import ActivityKit
import Combine
import Foundation
import SwiftData
import UIKit
import WidgetKit

// MARK: - SessionViewModel

@MainActor
@Observable
final class SessionViewModel {

    // MARK: Published state

    var currentBAC: Double = 0
    var bacStatus: BACStatus = .sober
    var drinks: [Drink] = []
    // Logged "Übergeben" events in the current session. Each truncates the
    // absorption of drinks still in the stomach (see BACCalculator / VomitEvent).
    var vomitEvents: [VomitEvent] = []
    var mealEvents: [MealEvent] = []
    var breathalyzerReadings: [BreathalyzerReading] = []
    var currentWeekDrinkCount: Int = 0

    // Timestamps handed to the BAC engine so still-absorbing drinks are cut short.
    var vomitTimes: [Date] { vomitEvents.map(\.timestamp) }
    var mealEventValues: [MealEventValue] { mealEvents.map(\.value) }
    var latestBreathalyzerReading: BreathalyzerReading? { breathalyzerReadings.last }

    // Worst-case model applied app-wide (home BAC, curves, badges) when the user
    // enabled "Konservativ in ganzer App". The safety screens have their own gate.
    private var conservative: Bool { profile?.conservativeForApp ?? false }
    var stomachStatus: StomachStatus = .light {
        didSet {
            guard !isConfiguring, stomachStatus != oldValue else { return }
            profile?.defaultStomachStatus = stomachStatus
            try? modelContext?.save()
            recalculate()
        }
    }

    // MARK: Bottle mode state (in-memory, not persisted)

    var lastBottleLevels: [UUID: Double] = [:]  // templateID -> last currentLevel

    // MARK: HealthKit BAC throttle (in-memory)

    // recalculate() fires every 30s while BAC > 0, so writing a HealthKit BAC
    // sample on each tick would flood Health with ~120 samples/hour for the whole
    // session plus its sober tail. Sample at most every 5 minutes, or sooner when
    // the value moved enough to be worth a point on the Health graph.
    private var lastHealthKitBACLog: Date?
    private var lastHealthKitBAC: Double = 0

    // MARK: Deferred recalc state (in-memory)

    // The shared BAC curve written for the widget is a function of the drinks,
    // not the wall clock, so plain 30s timer ticks only need an occasional
    // refresh to slide its 12h window. Tracks the last write so the timer can
    // throttle it; any state change forces an immediate recompute.
    private var lastSharedCurveWrite: Date?
    // The most recent deferred recalc burst; cancelled when superseded so a
    // pile-up of queued ticks does not each redo the heavy work.
    private var deferredRecalcTask: Task<Void, Never>?

    // MARK: Undo state (in-memory)

    // Value snapshot so a deleted @Model drink can be recreated for undo.
    struct DrinkSnapshot {
        let name: String
        let volume: Double
        let abv: Double
        let calories: Int
        let iconName: String
        let categoryRaw: String
        let timestamp: Date
        let templateID: UUID?
        let mixerVolume: Double
        let mixerWaterContent: Double
        let drinkDurationMinutes: Double

        init(_ d: Drink) {
            name = d.name; volume = d.volume; abv = d.abv; calories = d.calories
            iconName = d.iconName; categoryRaw = d.categoryRaw; timestamp = d.timestamp
            templateID = d.templateID; mixerVolume = d.mixerVolume
            mixerWaterContent = d.mixerWaterContent; drinkDurationMinutes = d.drinkDurationMinutes
        }

        func makeDrink() -> Drink {
            let drink = Drink(
                name: name, volume: volume, abv: abv, calories: calories,
                iconName: iconName, category: DrinkCategory(rawValue: categoryRaw) ?? .other,
                timestamp: timestamp, templateID: templateID,
                mixerVolume: mixerVolume, mixerWaterContent: mixerWaterContent
            )
            drink.drinkDurationMinutes = drinkDurationMinutes
            return drink
        }
    }

    enum UndoAction {
        case added(Drink)
        case removed([DrinkSnapshot])
        case reset([DrinkSnapshot])
    }

    private(set) var undoAction: UndoAction? = nil
    // Bumped on every new undoable action so the view can restart its hide timer.
    private(set) var undoVersion: Int = 0

    var undoLabel: String? {
        switch undoAction {
        case .added(let drink): return "\(drink.name) hinzugefügt"
        case .removed(let snaps): return snaps.count == 1 ? "\(snaps.first!.name) gelöscht" : "\(snaps.count) Drinks gelöscht"
        case .reset(let snaps): return "Sitzung gelöscht (\(snaps.count) Drinks)"
        case nil:               return nil
        }
    }

    func performUndo() {
        switch undoAction {
        case .added(let drink):
            undoAction = nil
            removeDrink(drink)
        case .removed(let snapshots), .reset(let snapshots):
            undoAction = nil
            guard let context = modelContext else { return }
            var restored: [Drink] = []
            for snap in snapshots {
                let drink = snap.makeDrink()
                context.insert(drink)
                restored.append(drink)
            }
            try? context.save()
            // Re-log into HealthKit since the delete/reset removed the samples.
            if profile?.healthKitEnabled == true, let hk = healthKit {
                Task {
                    for d in restored { await hk.logDrink(d) }
                }
            }
            loadTodaysDrinks()
            pushBACToWidget()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case nil:
            break
        }
    }

    func clearUndo() {
        undoAction = nil
    }

    // MARK: Sip counter state (in-memory, not persisted)

    var activeSipDrink: DrinkTemplate? = nil
    var sipCount: Int = 0
    private var sipCounterStartTime: Date? = nil

    var currentSipVolume: Double {
        guard let p = profile else { return 25.0 }
        let base = p.sipVolumeML
        guard let drink = activeSipDrink else { return base }
        if drink.abv > 20.0 {
            return max(5.0, base * 0.3) // ~7.5 ml for spirits
        } else if drink.abv > 10.0 {
            return max(10.0, base * 0.6) // ~15 ml for wine
        } else {
            return base
        }
    }

    var sipTotalML: Double { Double(sipCount) * currentSipVolume }

    // Realistic peak this many sips reaches, matching the "+x ‰" badges shown when
    // adding any other drink (QuickAdd/AmountInput/Mix all use projectedPeak). The
    // old raw-Widmark term ignored the resorption deficit and absorption-window
    // elimination, so the live sip readout overstated what actually landed on the
    // home screen once the sips were committed through the full model.
    var sipPromille: Double {
        guard let drink = activeSipDrink, let p = profile, sipTotalML > 0 else { return 0 }
        return BACCalculator.projectedPeak(
            volume: sipTotalML, abv: drink.abv, category: drink.category,
            profile: p, stomachStatus: stomachStatus, conservative: conservative
        )
    }

    // MARK: Computed

    var totalCalories: Int {
        drinks.reduce(0) { $0 + $1.calories }
    }

    // Legal BAC limit in ‰ for this user: 0,0 during the Probezeit / for novice
    // drivers, otherwise the German 0,5 ‰ limit. Used by the "Bis ... ‰" widget
    // so a probationary driver is not told they are fahrbereit at 0,4 ‰.
    var drivingLimit: Double { profile?.drivingLimit ?? 0.5 }

    var timeUntilSober: TimeInterval {
        guard let input = bacProjectionInput, currentBAC > 0 else { return 0 }
        // Use the same forward-scan engine as the Safety "Nüchtern" timer rather than
        // a linear currentBAC / rate line, so alcohol still being absorbed and the
        // Michaelis-Menten low-end tail are accounted for and every screen agrees.
        if let hours = input.hoursUntil(0.0) {
            return hours * 3600
        }
        return 72 * 3600   // target not reached within the scan horizon
    }

    // MARK: - Insights & Warnings

    var pacingWarning: String? {
        let halfHourAgo = Date().addingTimeInterval(-30 * 60)
        // Finde alle alkoholischen Drinks der letzten 30 Minuten
        let recentDrinks = drinks.filter { $0.timestamp >= halfHourAgo && $0.abv > 0 }
        if recentDrinks.count >= 2 {
            return "\(recentDrinks.count) Drinks in 30 Minuten. Zeit für ein Glas Wasser!"
        }
        return nil
    }

    var estimatedSoberTime: Date? {
        guard currentBAC > 0 else { return nil }
        // timeUntilSober is already a TimeInterval in seconds.
        return Date().addingTimeInterval(timeUntilSober)
    }

    func isDrunkModeActive(profile: UserProfile?, dismissed: Bool) -> Bool {
        guard let profile, profile.drunkModeAuto, !dismissed else { return false }
        return currentBAC >= profile.carefulThreshold
    }

    var drunkModeSoberCountdown: String? {
        guard currentBAC > 0.01 else { return nil }
        guard let hours = hoursUntil(0.0) else { return "> 72 h bis nüchtern" }
        guard hours > 0 else { return nil }
        let totalMinutes = Int((hours * 60).rounded())
        let h = totalMinutes / 60
        let m = totalMinutes % 60
        if h > 0 { return "noch ca. \(h) h \(m) min bis nüchtern" }
        return "noch ca. \(m) min bis nüchtern"
    }

    var weeklyLimitWarning: String? {
        guard let limit = profile?.weeklyDrinkLimit, limit > 0 else { return nil }
        if currentWeekDrinkCount >= limit {
            return "Wochenlimit erreicht (\(currentWeekDrinkCount)/\(limit) Drinks)"
        } else if currentWeekDrinkCount >= limit - 2 {
            return "Wochenlimit fast erreicht (\(currentWeekDrinkCount)/\(limit) Drinks)"
        }
        return nil
    }

    // MARK: Dependencies

    private var profile: UserProfile?
    private var modelContext: ModelContext?
    private var timer: AnyCancellable?
    var healthKit: HealthKitService?
    private var isConfiguring = false
    private var lastCurrentBACInputKey = ""
    private var lastCurrentBACUpdateAt: Date?
    // The last value signature loaded from SwiftData. Object identity is not enough
    // here: History can edit an existing @Model in place without changing its id.
    private var lastLoadedProjectionKey = ""

    private var bacProjectionInput: BACProjectionInput? {
        guard let profile else { return nil }
        return BACProjectionInput(
            drinks: drinks,
            profile: profile,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes,
            mealEvents: mealEventValues
        )
    }

    // MARK: Setup

    func configure(profile: UserProfile, context: ModelContext) {
        isConfiguring = true
        self.profile = profile
        self.modelContext = context
        stomachStatus = profile.defaultStomachStatus
        isConfiguring = false
        consumePendingWidgetDrinks()
        loadTodaysDrinks()
        startTimer()
    }

    // Drinks added via the Lock Screen Widget or Watch are written to UserDefaults.
    // Pick them up and persist to SwiftData the next time the app is active.
    private func consumePendingWidgetDrinks() {
        guard let context = modelContext else { return }
        let pending = SharedStateStore.readPendingDrinks()
        guard !pending.isEmpty else { return }
        for p in pending {
            let drink = Drink(
                name: p.name,
                volume: p.volume,
                abv: p.abv,
                calories: p.calories,
                iconName: p.iconName,
                category: DrinkCategory(rawValue: p.categoryRaw) ?? .other,
                timestamp: p.timestamp
            )
            context.insert(drink)
        }
        do {
            try context.save()
            SharedStateStore.clearPendingDrinks()
        } catch {
            context.rollback()
        }
    }

    // MARK: Drink management

    func addDrink(_ drink: Drink) {
        guard let context = modelContext else { return }
        context.insert(drink)
        do {
            try context.save()
        } catch {
            context.rollback()
            return
        }
        // Increment template usage count after the drink is committed to disk.
        if let tid = drink.templateID {
            let pred = #Predicate<DrinkTemplate> { $0.id == tid }
            if let t = try? context.fetch(FetchDescriptor<DrinkTemplate>(predicate: pred)).first {
                t.usageCount += 1
                try? context.save()
            }
        }
        drinks.append(drink)
        undoAction = .added(drink)
        undoVersion += 1
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if profile?.healthKitEnabled == true {
            Task { await healthKit?.logDrink(drink) }
        }
    }

    // Logs another drink identical to an existing one, stamped at the current
    // time. Used by the swipe-right-to-duplicate gesture on the home list.
    func duplicateDrink(_ drink: Drink) {
        let copy = Drink(
            name: drink.name,
            volume: drink.volume,
            abv: drink.abv,
            calories: drink.calories,
            iconName: drink.iconName,
            category: drink.category,
            timestamp: Date(),
            templateID: drink.templateID,
            mixerVolume: drink.mixerVolume,
            mixerWaterContent: drink.mixerWaterContent
        )
        copy.drinkDurationMinutes = drink.drinkDurationMinutes
        addDrink(copy)
    }

    func removeDrink(_ drink: Drink, recordUndo: Bool = false) {
        // Snapshot before mutation so an accidental swipe-delete can be undone.
        let snapshot = recordUndo ? DrinkSnapshot(drink) : nil
        // The pending undo would resurrect or re-delete a drink that no longer matches.
        if case .added(let pending) = undoAction, pending.id == drink.id {
            undoAction = nil
        }
        drinks.removeAll { $0.id == drink.id }
        if profile?.healthKitEnabled == true {
            Task { await healthKit?.removeDrink(drink) }
        }
        modelContext?.delete(drink)
        try? modelContext?.save()
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        if let snapshot {
            undoAction = .removed([snapshot])
            undoVersion += 1
        }
    }

    func updateDrink(_ drink: Drink, volume: Double, timestamp: Date, durationMinutes: Double) {
        guard volume > 0, drink.volume > 0 else { return }
        // Scale calories from original volume to avoid int-rounding accumulation across edits.
        let originalVolume = drink.volume
        let originalCalories = drink.calories
        drink.calories = Int((Double(originalCalories) / originalVolume * volume).rounded())
        drink.volume = volume
        drink.timestamp = timestamp
        drink.drinkDurationMinutes = durationMinutes
        try? modelContext?.save()
        // force: true -- the drink ID is unchanged after a volume/timestamp edit, so
        // the normal idempotency guard would skip recalculate(). Force bypasses it.
        loadTodaysDrinks(force: true)
        pushBACToWidget()
    }

    func finishDrinkNow(_ drink: Drink) {
        let baseEstimate = DrinkDurationEstimator.baseEstimate(
            category: drink.category,
            volumeML: drink.volume
        )
        let actualMinutes = max(1, Date().timeIntervalSince(drink.timestamp) / 60)
        if drink.drinkDurationMinutes == 0 {
            DrinkPaceMemory.recordEarlyFinish(
                category: drink.category,
                baseEstimate: baseEstimate,
                actualMinutes: actualMinutes
            )
        }
        drink.finish()
        try? modelContext?.save()
        loadTodaysDrinks(force: true)
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func resetSession() {
        // Keep value snapshots so the deletion can be undone from the snackbar.
        let snapshots = drinks.map { DrinkSnapshot($0) }
        // Mirror the deletion into HealthKit (id+timestamp pairs captured before
        // the models are deleted; the async task must not touch dead models).
        if profile?.healthKitEnabled == true, let hk = healthKit {
            let drinkInfo = drinks.map { (id: $0.id, timestamp: $0.timestamp) }
            Task {
                for info in drinkInfo { await hk.removeDrink(id: info.id, timestamp: info.timestamp) }
            }
        }
        drinks.forEach { modelContext?.delete($0) }
        drinks = []
        vomitEvents.forEach { modelContext?.delete($0) }
        vomitEvents = []
        mealEvents.forEach { modelContext?.delete($0) }
        mealEvents = []
        breathalyzerReadings.forEach { modelContext?.delete($0) }
        breathalyzerReadings = []
        try? modelContext?.save()
        if !snapshots.isEmpty {
            undoAction = .reset(snapshots)
            undoVersion += 1
        }
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }

    // MARK: Bottle mode

    func addBottleDrink(template: DrinkTemplate, bottleSize: Double, startLevel: Double, currentLevel: Double) {
        let consumedML = (startLevel - currentLevel) * bottleSize
        guard consumedML > 0 else { return }
        let scaledCalories = template.volume > 0
            ? Int(Double(template.calories) / template.volume * consumedML)
            : 0
        let drink = Drink(
            name: template.name,
            volume: consumedML,
            abv: template.abv,
            calories: scaledCalories,
            iconName: template.iconName,
            category: template.category,
            timestamp: Date(),
            templateID: template.id
        )
        lastBottleLevels[template.id] = currentLevel
        addDrink(drink)
    }

    // MARK: Sip counter

    func startSipCounter(for drink: DrinkTemplate) {
        activeSipDrink = drink
        sipCount = 0
        sipCounterStartTime = Date()
    }

    func addSip() {
        guard activeSipDrink != nil else { return }
        sipCount += 1
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func removeSip() {
        sipCount = max(0, sipCount - 1)
    }

    func commitSips() {
        guard let template = activeSipDrink, sipCount > 0, profile != nil else { return }
        let ml = Double(sipCount) * currentSipVolume
        let scaledCalories = template.volume > 0
            ? Int(Double(template.calories) / template.volume * ml)
            : 0
        let drink = Drink(
            name: "\(template.name) (\(sipCount) Schlucke)",
            volume: ml,
            abv: template.abv,
            calories: scaledCalories,
            iconName: template.iconName,
            category: template.category,
            timestamp: Date(),
            templateID: template.id
        )
        // Use the measured drinking duration if available
        if let start = sipCounterStartTime {
            drink.drinkDurationMinutes = max(1, Date().timeIntervalSince(start) / 60.0)
        }
        addDrink(drink)
        cancelSipCounter()
    }

    func cancelSipCounter() {
        activeSipDrink = nil
        sipCount = 0
        sipCounterStartTime = nil
    }

    func loadTodaysDrinks(force: Bool = false) {
        guard let context = modelContext else { return }
        
        let now = Date()

        // 1. Der logische Tag beginnt erst um 06:00 Uhr morgens (verhindert 0:00 Uhr Reset)
        let logicalStart = Calendar.current.logicalDayStart(for: now)

        // 2. Lade Drinks der letzten 48 Stunden für die Rolling-Session
        let lookback = now.addingTimeInterval(-48 * 3600)
        let descriptor = FetchDescriptor<Drink>(
            predicate: #Predicate { $0.timestamp >= lookback },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        let recentDrinks = (try? context.fetch(descriptor)) ?? []
        let vomitDescriptor = FetchDescriptor<VomitEvent>(
            predicate: #Predicate { $0.timestamp >= lookback },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        let recentVomitEvents = (try? context.fetch(vomitDescriptor)) ?? []
        let mealDescriptor = FetchDescriptor<MealEvent>(
            predicate: #Predicate { $0.timestamp >= lookback },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        let recentMealEvents = (try? context.fetch(mealDescriptor)) ?? []
        let breathDescriptor = FetchDescriptor<BreathalyzerReading>(
            predicate: #Predicate { $0.timestamp >= lookback },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        let recentBreathReadings = (try? context.fetch(breathDescriptor)) ?? []
        
        guard let p = profile else {
            self.drinks = recentDrinks.filter { $0.timestamp >= logicalStart }
            self.mealEvents = recentMealEvents.filter { $0.timestamp >= logicalStart }
            self.breathalyzerReadings = recentBreathReadings.filter { $0.timestamp >= logicalStart }
            recalculate()
            return
        }
        
        // 3. Rolling Session: Wenn um 06:00 Uhr noch Restalkohol vorhanden war,
        // wird die Sitzung rückwirkend bis zum ersten Drink dieser ununterbrochenen Phase verlängert!
        let drinksBefore6AM = recentDrinks.filter { $0.timestamp <= logicalStart }
        let vomitTimesBefore6AM = recentVomitEvents
            .map(\.timestamp)
            .filter { $0 <= logicalStart }
        let mealsBefore6AM = recentMealEvents
            .filter { $0.timestamp <= logicalStart }
            .map(\.value)
        let bacAt6AM = BACProjectionInput(
            drinks: drinksBefore6AM,
            profile: p,
            stomachStatus: p.defaultStomachStatus,
            conservative: p.conservativeForApp,
            vomitTimes: vomitTimesBefore6AM,
            mealEvents: mealsBefore6AM
        ).currentBAC(at: logicalStart)
        
        var sessionStart = logicalStart
        
        if bacAt6AM > 0.001 {
            var blockStart = logicalStart
            for i in (0..<drinksBefore6AM.count).reversed() {
                let d = drinksBefore6AM[i]
                blockStart = d.timestamp
                
                // Promillewert genau 1 Minute VOR diesem Drink prüfen
                let beforeTime = d.timestamp.addingTimeInterval(-60)
                let pastDrinks = Array(drinksBefore6AM[0..<i])
                let vomitTimesBeforeDrink = vomitTimesBefore6AM.filter { $0 <= beforeTime }
                let mealsBeforeDrink = mealsBefore6AM.filter { $0.timestamp <= beforeTime }
                let bacBefore = BACProjectionInput(
                    drinks: pastDrinks,
                    profile: p,
                    stomachStatus: p.defaultStomachStatus,
                    conservative: p.conservativeForApp,
                    vomitTimes: vomitTimesBeforeDrink,
                    mealEvents: mealsBeforeDrink
                ).currentBAC(at: beforeTime)
                
                // Wenn wir vor diesem Drink nüchtern waren, ist hier der Start der Session
                if bacBefore <= 0.001 {
                    break
                }
            }
            sessionStart = blockStart
        }
        
        let sessionDrinks = recentDrinks.filter { $0.timestamp >= sessionStart }
        let sessionVomitEvents = recentVomitEvents.filter { $0.timestamp >= sessionStart }
        let sessionMealEvents = recentMealEvents.filter { $0.timestamp >= sessionStart }
        let sessionBreathReadings = recentBreathReadings.filter { $0.timestamp >= sessionStart }
        // The @Query in HomeView re-fires this right after a local addDrink/removeDrink,
        // which already recalculated and rescheduled. Skip the expensive BAC recompute +
        // notification reschedule only when every BAC-relevant input is unchanged;
        // an in-place SwiftData edit retains object identity but changes this key.
        let sessionKey = BACProjectionInput(
            drinks: sessionDrinks,
            profile: p,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: sessionVomitEvents.map(\.timestamp),
            mealEvents: sessionMealEvents.map(\.value)
        ).stableKey
        let unchanged = !force && sessionKey == lastLoadedProjectionKey
        self.drinks = sessionDrinks
        breathalyzerReadings = sessionBreathReadings
        if !unchanged {
            vomitEvents = sessionVomitEvents
            mealEvents = sessionMealEvents
            recalculate()
            rescheduleNotifications()
            // This path handles external model mutations such as History edits
            // and Settings/profile changes. The App Group snapshot is current after
            // recalculate(), but WidgetKit also needs an explicit timeline reload.
            pushBACToWidget()
        }
        
        // Aktuelle Woche berechnen (für das Wochenlimit)
        var cal = Calendar.current
        cal.firstWeekday = 2 // Start am Montag
        if let weekStart = cal.date(from: cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)) {
            let weekDescriptor = FetchDescriptor<Drink>(predicate: #Predicate { $0.timestamp >= weekStart })
            let weekDrinks = (try? context.fetch(weekDescriptor)) ?? []
            self.currentWeekDrinkCount = weekDrinks.filter { $0.abv > 0 }.count
        }
    }

    // MARK: Taktisches Übergeben

    private func loadVomitEvents(since start: Date) {
        guard let context = modelContext else { vomitEvents = []; return }
        let descriptor = FetchDescriptor<VomitEvent>(
            predicate: #Predicate { $0.timestamp >= start },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        vomitEvents = (try? context.fetch(descriptor)) ?? []
    }

    // Logs an "Übergeben" at the current time. Only alcohol still in the stomach
    // (not yet resorbed) is removed; the BAC already in the blood is unchanged, so
    // the displayed value does not jump down, it just stops rising from the drinks
    // that were still being absorbed.
    func logVomit() {
        guard let context = modelContext else { return }
        let event = VomitEvent(timestamp: Date())
        context.insert(event)
        try? context.save()
        vomitEvents.append(event)
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    // Removes the most recently logged vomit (e.g. a mistaken tap).
    func removeLastVomit() {
        guard let event = vomitEvents.last else { return }
        vomitEvents.removeLast()
        modelContext?.delete(event)
        try? modelContext?.save()
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    // MARK: Meals & breathalyser readings

    func logMeal(impact: MealImpact, name: String = "", at timestamp: Date = Date()) {
        guard let context = modelContext else { return }
        let event = MealEvent(timestamp: timestamp, impact: impact, name: name)
        context.insert(event)
        try? context.save()
        mealEvents.append(event)
        mealEvents.sort { $0.timestamp < $1.timestamp }
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func removeLastMeal() {
        guard let event = mealEvents.last else { return }
        mealEvents.removeLast()
        modelContext?.delete(event)
        try? modelContext?.save()
        recalculate()
        pushBACToWidget()
        rescheduleNotifications()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func logBreathalyzerReading(
        measuredBAC: Double,
        note: String = "",
        at timestamp: Date = Date(),
        source: BreathalyzerSource = .manual
    ) {
        guard let context = modelContext else { return }
        let estimated = bacProjectionInput?.currentBAC(at: timestamp) ?? currentBAC
        let reading = BreathalyzerReading(
            timestamp: timestamp,
            measuredBAC: measuredBAC,
            estimatedBAC: estimated,
            source: source,
            note: note
        )
        context.insert(reading)
        try? context.save()
        breathalyzerReadings.append(reading)
        breathalyzerReadings.sort { $0.timestamp < $1.timestamp }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    func removeBreathalyzerReading(_ reading: BreathalyzerReading) {
        breathalyzerReadings.removeAll { $0.id == reading.id }
        modelContext?.delete(reading)
        try? modelContext?.save()
    }

    // MARK: Projections

    func hoursUntil(_ target: Double) -> Double? {
        bacProjectionInput?.hoursUntil(target)
    }

    var hangoverForecast: HangoverLevel {
        guard let input = bacProjectionInput else { return .none }
        // Gate on the session peak, not the live BAC: the forecast would
        // otherwise vanish while sobering down, exactly when it matters.
        let peak = input.peakBAC()
        guard peak > 0.3 else { return .none }
        let water = WaterLog.loggedGlasses(
            forDay: Calendar.current.logicalDay(for: Date())
        ).map(Double.init)
        return HangoverPredictor.predict(
            drinks: drinks,
            profile: input.profile,
            waterGlasses: water,
            stomachStatus: input.stomachStatus,
            conservative: input.conservative,
            vomitTimes: input.vomitTimes,
            mealEvents: input.mealEvents
        )
    }

    var bacCurve: [BACCalculator.BACPoint] {
        cachedCurve(hours: 8, intervalMinutes: 15)
    }

    var bacCurve24h: [BACCalculator.BACPoint] {
        cachedCurve(hours: 24, intervalMinutes: 30)
    }

    // MARK: Curve cache
    //
    // HomeView reads bacCurve/bacCurve24h on every body evaluation and each
    // raw call integrates the whole drink list. Results are memoized per
    // (generation, minute): recalculate() bumps the generation on every state
    // change and 30s tick, and the 60s time bucket bounds staleness even if a
    // mutation path ever skips recalculate(). At 15/30 min curve resolution a
    // <= 60s stale start point is invisible.

    private struct CurveCacheKey: Hashable {
        let hours: Int
        let intervalMinutes: Int
        let bucket: Int
        let inputKey: String

        init(hours: Double, intervalMinutes: Double, bucket: Int, inputKey: String) {
            self.hours = Int((hours * 100).rounded())
            self.intervalMinutes = Int((intervalMinutes * 100).rounded())
            self.bucket = bucket
            self.inputKey = inputKey
        }
    }

    @ObservationIgnored private var curveCache: [CurveCacheKey: (generation: Int, points: [BACCalculator.BACPoint])] = [:]
    @ObservationIgnored private var curveGeneration = 0

    private func cachedCurve(hours: Double, intervalMinutes: Double) -> [BACCalculator.BACPoint] {
        guard let input = bacProjectionInput else { return [] }
        // Touch the observed inputs even on a cache hit so views that only
        // read the curve still re-render when drinks or settings change.
        _ = (drinks.count, stomachStatus, conservative)

        let bucket = Int(Date().timeIntervalSinceReferenceDate / 60)
        let key = CurveCacheKey(hours: hours, intervalMinutes: intervalMinutes, bucket: bucket, inputKey: input.stableKey)
        if let hit = curveCache[key], hit.generation == curveGeneration {
            return hit.points
        }
        let points = input.curve(hours: hours, intervalMinutes: intervalMinutes)
        curveCache[key] = (curveGeneration, points)
        return points
    }

    func projectedBAC(hours: Double = 8) -> [(Date, Double)] {
        guard let input = bacProjectionInput else { return [] }
        let now = Date()
        return input.curve(from: now, hours: hours, intervalMinutes: (hours * 60) / 30)
            .map { ($0.date, $0.bac) }
    }

    // MARK: Private

    // refreshCurve: force a fresh shared BAC curve (widget data). Every state
    // change passes true; only the 30s timer tick passes false so a quiescent
    // session does not recompute the 12h curve twice a minute for hours.
    private func recalculate(refreshCurve: Bool = true) {
        curveGeneration &+= 1
        guard let input = bacProjectionInput else {
            currentBAC = 0
            bacStatus  = .sober
            lastLoadedProjectionKey = ""
            return
        }
        let profile = input.profile
        let inputKey = input.stableKey
        lastLoadedProjectionKey = inputKey
        let now = Date()
        let computedBAC = input.currentBAC(at: now)
        currentBAC = filteredCurrentBAC(computedBAC, inputKey: inputKey, at: now)
        bacStatus  = BACStatus(bac: currentBAC, profile: profile)
        UserDefaults.widgetShared.set(currentBAC, forKey: UserDefaults.keyCurrentBAC)
        let resolvedEliminationRate = profile.resolvedEliminationRate(conservative: conservative)
        UserDefaults.widgetShared.set(resolvedEliminationRate, forKey: UserDefaults.keyEliminationRate)
        UserDefaults.widgetShared.set(Date(), forKey: UserDefaults.keyLastUpdated)
        UserDefaults.widgetShared.set(profile.warningThreshold, forKey: UserDefaults.keyWarningThreshold)
        UserDefaults.widgetShared.set(profile.drivingLimit, forKey: UserDefaults.keyDrivingLimit)
        let perDrink = BACCalculator.bacContribution(
            volume: 330, abv: 5.0,
            weight: profile.validatedWeight,
            distributionFactor: profile.distributionFactor
        )
        UserDefaults.widgetShared.set(perDrink, forKey: UserDefaults.keyPerDrinkBAC)

        // Heavy work (curve, shared session, Live Activity) deferred so the UI
        // can update (sheet dismiss, haptic) before the compute burst runs.
        let drinksCopy   = drinks
        let bacAtCall    = currentBAC
        let stomachCopy  = stomachStatus
        let vomitCopy    = vomitTimes
        let mealCopy     = mealEventValues
        let conservativeCopy = conservative
        let elimRate     = resolvedEliminationRate
        let drinkCount   = drinks.count
        let skin         = profile.statusSkin
        let forceCurve   = refreshCurve

        deferredRecalcTask?.cancel()
        deferredRecalcTask = Task {
            // A superseded tick that never started its work bails immediately.
            guard !Task.isCancelled else { return }

            // Curve + status config are shape/threshold data, not live BAC, so
            // recompute them only on a forced refresh or once the window slides
            // (>= 5 min). The live-BAC writes below always run per tick.
            let curveStale = lastSharedCurveWrite.map { Date().timeIntervalSince($0) >= 300 } ?? true
            if forceCurve || curveStale {
                let curvePoints = BACProjectionInput(
                    drinks: drinksCopy,
                    profile: profile,
                    stomachStatus: stomachCopy,
                    conservative: conservativeCopy,
                    vomitTimes: vomitCopy,
                    mealEvents: mealCopy
                ).curve(hours: 12, intervalMinutes: 15)
                    .map { SharedBACPoint(date: $0.date, bac: $0.bac) }
                guard !Task.isCancelled else { return }
                SharedStateStore.writeBACCurve(curvePoints)
                SharedStateStore.writeStatusConfig(SharedStatusConfig(
                    tipsyThreshold: profile.tipsyThreshold,
                    drunkThreshold: profile.drunkThreshold,
                    carefulThreshold: profile.carefulThreshold,
                    dangerThreshold: profile.dangerThreshold,
                    labels: [
                        skin.label(for: .sober),
                        skin.label(for: .tipsy),
                        skin.label(for: .drunk),
                        skin.label(for: .careful),
                        skin.label(for: .danger),
                    ]
                ))
                lastSharedCurveWrite = Date()
            }

            guard !Task.isCancelled else { return }
            writeSharedSession(profile: profile, eliminationRate: elimRate)
            // Precompute the sober / driving-limit crossing times with the full BAC
            // engine so the Live Activity uses the Probezeit-aware legal limit and
            // reflects the still-rising absorption phase, instead of a linear
            // extrapolation off the warning threshold.
            let safetyInput = BACProjectionInput(
                drinks: drinksCopy,
                profile: profile,
                stomachStatus: stomachCopy,
                conservative: conservativeCopy,
                vomitTimes: vomitCopy,
                mealEvents: mealCopy
            )
            let soberAt = safetyInput.hoursUntil(profile.tipsyThreshold, from: now)
                .map { now.addingTimeInterval($0 * 3600) }
            let driveReadyAt = safetyInput.hoursUntil(profile.drivingLimit, from: now)
                .map { now.addingTimeInterval($0 * 3600) }
            LiveActivityService.shared.syncActivity(
                bac: bacAtCall,
                eliminationRate: elimRate,
                drinkCount: drinkCount,
                soberThreshold: profile.tipsyThreshold,
                warningThreshold: profile.warningThreshold,
                drivingLimit: profile.drivingLimit,
                soberAt: soberAt,
                driveReadyAt: driveReadyAt
            )
            if profile.healthKitEnabled, shouldLogHealthKitBAC(bacAtCall) {
                await healthKit?.logBAC(bacAtCall)
            }
        }
    }

    private func writeSharedSession(profile: UserProfile, eliminationRate: Double) {
        let sharedDrinks = drinks.map { d in
            SharedDrink(id: d.id, name: d.name, volume: d.volume, abv: d.abv,
                        timestamp: d.timestamp, iconName: d.iconName,
                        categoryRaw: d.category.rawValue, calories: d.calories)
        }

        var favorites: [SharedDrinkTemplate] = []
        if let context = modelContext {
            var descriptor = FetchDescriptor<DrinkTemplate>(
                sortBy: [SortDescriptor(\.usageCount, order: .reverse)]
            )
            descriptor.fetchLimit = 6
            if let templates = try? context.fetch(descriptor) {
                favorites = templates.map {
                    SharedDrinkTemplate(id: $0.id, name: $0.name, volume: $0.volume, abv: $0.abv, icon: $0.iconName)
                }
            }
        }

        let session = SharedSessionData(
            currentBAC: currentBAC,
            eliminationRate: eliminationRate,
            lastUpdated: Date(),
            drinks: sharedDrinks,
            favoriteDrinks: favorites,
            statusLabel: bacStatus.localizedName
        )
        SharedStateStore.writeSession(session)
    }

    // Arms the HealthKit BAC throttle: returns true (and records the write) when
    // a sample is due: on the first sample, once 5 minutes have elapsed, or when
    // the BAC moved by at least 0.1 permille since the last logged sample.
    private func shouldLogHealthKitBAC(_ bac: Double) -> Bool {
        let now = Date()
        if let last = lastHealthKitBACLog,
           now.timeIntervalSince(last) < 300,
           abs(bac - lastHealthKitBAC) < 0.1 {
            return false
        }
        lastHealthKitBACLog = now
        lastHealthKitBAC = bac
        return true
    }

    private func startTimer() {
        timer?.cancel()
        timer = Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self else { return }
                // While sober with no drinks the BAC stays 0 tick-to-tick, so the
                // deferred curve / shared-session / Live-Activity burst would be pure
                // waste. Drink changes still call recalculate() directly, so nothing
                // can go stale. This skips ~2880 idle recompute bursts per sober day.
                guard !self.drinks.isEmpty || self.currentBAC > 0 else { return }
                self.recalculate(refreshCurve: false)
            }
    }

    private func filteredCurrentBAC(_ computedBAC: Double, inputKey: String, at now: Date) -> Double {
        defer {
            lastCurrentBACInputKey = inputKey
            lastCurrentBACUpdateAt = now
        }

        guard lastCurrentBACInputKey == inputKey,
              let lastUpdate = lastCurrentBACUpdateAt,
              computedBAC > currentBAC
        else { return computedBAC }

        // If a settings/UI rebuild recomputes the same session instantly, do not
        // let the displayed live BAC jump to a future curve peak for one frame.
        // Real drink edits/additions change inputKey and bypass this guard.
        let elapsed = max(0, now.timeIntervalSince(lastUpdate))
        let maxPlausibleRise = max(0.05, elapsed * 0.02)
        guard computedBAC > currentBAC + maxPlausibleRise else { return computedBAC }
        return currentBAC
    }

    private func pushBACToWidget() {
        Task.detached(priority: .utility) {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    // Reschedule the sober/drive-ready local notifications. Called on drink
    // changes, not from the 30s timer, to avoid permanent rescheduling churn.
    func rescheduleNotifications() {
        guard let profile else { return }
        let drinksSnapshot = drinks
        let vomitSnapshot = vomitTimes
        let mealSnapshot = mealEventValues
        Task {
            await NotificationService.reschedule(
                drinks: drinksSnapshot,
                profile: profile,
                stomachStatus: stomachStatus,
                vomitTimes: vomitSnapshot,
                mealEvents: mealSnapshot
            )
        }
    }
}
