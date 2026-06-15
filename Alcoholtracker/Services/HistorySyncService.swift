import Foundation
import SwiftData

// MARK: - HistorySyncService
//
// Backs up the on-device drinking history (drinks + day notes) to the signed-in
// Supabase account and restores it on a fresh install. The local SwiftData store
// is the source of truth: on each sync the local set is pushed (upsert) and, in
// the ongoing `.authoritative` mode, server rows that no longer exist locally are
// deleted so deletions propagate.
//
// Two safety exceptions never delete anything:
//   * Fresh device (empty local store, non-empty backup) -> rows are restored.
//   * First sync right after sign-in (`merge: true`) -> the account backup and
//     the device's current history are unioned, so signing into an existing
//     account never wipes either side.
//
// This is a single-account backup/restore model (the most recently synced device
// wins for deletions), not a live multi-device CRDT merge.

@MainActor
@Observable
final class HistorySyncService {

    private(set) var isSyncing = false
    private(set) var lastSyncDate: Date?

    private let modelContext: ModelContext
    private let supabase: SupabaseService

    // yyyy-MM-dd in the device's calendar; round-trips DayNote.dayStart (a local
    // startOfDay) to the Postgres `date` column and back.
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    init(modelContext: ModelContext, supabase: SupabaseService) {
        self.modelContext = modelContext
        self.supabase = supabase
    }

    /// Push local history to the account and reconcile. Pass `merge: true` for the
    /// first sync after a sign-in so the account backup is unioned with the device
    /// instead of one overwriting the other. Best-effort: any failure leaves local
    /// data intact and is retried on the next launch / foreground.
    func sync(merge: Bool = false) async {
        guard !isSyncing, let userId = supabase.session?.userId, supabase.isConfigured else { return }
        isSyncing = true
        defer { isSyncing = false }

        // A different account on this device must never inherit or upload the
        // previous user's local data. Purge it so the new account starts from
        // its own backup (or fresh), and restore the new account's settings.
        let lastUser = UserDefaults.standard.string(forKey: Self.lastUserKey)
        let accountSwitched = lastUser != nil && lastUser != userId
        if accountSwitched {
            purgeLocalUserData()
            UserDefaults.standard.removeObject(forKey: Self.didInitialSyncKey)
        }

        // The very first successful sync on this install is always a union, so
        // upgrading an already-signed-in device never clobbers a backup another
        // device made. Authoritative (delete-propagating) syncs start afterwards.
        let firstSync = !UserDefaults.standard.bool(forKey: Self.didInitialSyncKey)
        let useMerge = merge || firstSync

        do {
            try await syncDrinks(merge: useMerge)
            try await syncNotes(merge: useMerge)
            try await syncSettings(forceProfileRestore: accountSwitched)
            UserDefaults.standard.set(true, forKey: Self.didInitialSyncKey)
            UserDefaults.standard.set(userId, forKey: Self.lastUserKey)
            lastSyncDate = Date()
        } catch {
            // Transient (network/server); retried next time.
        }
    }

    // Deletes the signed-out user's on-device data before another account's
    // sync runs. The UserProfile is intentionally left in place (it is restored
    // from the new account's backup in syncSettings, or kept if that account has
    // none) to avoid wiping settings out from under the live UI.
    private func purgeLocalUserData() {
        for d in (try? modelContext.fetch(FetchDescriptor<Drink>())) ?? [] { modelContext.delete(d) }
        for n in (try? modelContext.fetch(FetchDescriptor<DayNote>())) ?? [] { modelContext.delete(n) }
        for m in (try? modelContext.fetch(FetchDescriptor<CustomMix>())) ?? [] { modelContext.delete(m) }
        for t in ((try? modelContext.fetch(FetchDescriptor<DrinkTemplate>())) ?? []).filter(\.isCustom) {
            modelContext.delete(t)
        }
        try? modelContext.save()
        WaterLog.clear()
    }

    private static let didInitialSyncKey = "history.didInitialSync"
    private static let lastUserKey = "history.lastSyncedUserId"

    // MARK: Drinks

    private func syncDrinks(merge: Bool) async throws {
        let local = (try? modelContext.fetch(FetchDescriptor<Drink>())) ?? []
        let remote = try await supabase.fetchDrinkHistory()
        let localIDs = Set(local.map(\.id))

        if !local.isEmpty {
            try await supabase.uploadDrinkHistory(local.map(drinkRow))
        }

        // Import server rows we don't have locally when merging or restoring.
        if merge || local.isEmpty {
            var imported = false
            for r in remote {
                guard let id = UUID(uuidString: r.id), !localIDs.contains(id) else { continue }
                insertDrink(from: r, id: id)
                imported = true
            }
            if imported { try? modelContext.save() }
        }

        // Ongoing authoritative mode: propagate local deletions to the server.
        if !merge, !local.isEmpty {
            let stale = remote.compactMap { UUID(uuidString: $0.id) }.filter { !localIDs.contains($0) }
            try await supabase.deleteDrinkHistory(ids: stale)
        }
    }

    private func drinkRow(_ d: Drink) -> [String: Any] {
        var row: [String: Any] = [
            "id": d.id.uuidString,
            "name": d.name,
            "volume": d.volume,
            "abv": d.abv,
            "calories": d.calories,
            "icon_name": d.iconName,
            "category": d.categoryRaw,
            "mixer_volume": d.mixerVolume,
            "mixer_water_content": d.mixerWaterContent,
            "drink_duration_minutes": d.drinkDurationMinutes,
            "consumed_at": Self.isoFormatter.string(from: d.timestamp),
        ]
        if let tid = d.templateID { row["template_id"] = tid.uuidString }
        return row
    }

    private func insertDrink(from r: SupabaseService.RemoteDrink, id: UUID) {
        let drink = Drink(
            name: r.name,
            volume: r.volume,
            abv: r.abv,
            calories: r.calories,
            iconName: r.iconName,
            category: DrinkCategory(rawValue: r.category) ?? .other,
            timestamp: r.consumedAt,
            templateID: r.templateID.flatMap { UUID(uuidString: $0) },
            mixerVolume: r.mixerVolume,
            mixerWaterContent: r.mixerWaterContent
        )
        drink.id = id                              // preserve server identity for future upserts
        drink.drinkDurationMinutes = r.drinkDurationMinutes
        modelContext.insert(drink)
    }

    // MARK: Day notes

    private func syncNotes(merge: Bool) async throws {
        let local = (try? modelContext.fetch(FetchDescriptor<DayNote>())) ?? []
        let remote = try await supabase.fetchDayNotes()
        let localDays = Set(local.map { Self.dayFormatter.string(from: $0.dayStart) })

        if !local.isEmpty {
            try await supabase.uploadDayNotes(local.map(noteRow))
        }

        if merge || local.isEmpty {
            var imported = false
            for r in remote where !localDays.contains(r.dayStart) {
                guard let day = Self.dayFormatter.date(from: r.dayStart) else { continue }
                let note = DayNote(dayStart: day, text: r.text, mood: DayMood(rawValue: r.mood) ?? .neutral)
                modelContext.insert(note)
                imported = true
            }
            if imported { try? modelContext.save() }
        }

        if !merge, !local.isEmpty {
            let stale = remote.map(\.dayStart).filter { !localDays.contains($0) }
            try await supabase.deleteDayNotes(days: stale)
        }
    }

    private func noteRow(_ n: DayNote) -> [String: Any] {
        [
            "day_start": Self.dayFormatter.string(from: n.dayStart),
            "text": n.text,
            "mood": n.moodRaw,
        ]
    }

    // MARK: Settings, water log, custom mixes & drinks (single JSON document)

    private static let blobEncoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        return e
    }()

    private static let blobDecoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    private func syncSettings(forceProfileRestore: Bool = false) async throws {
        let profile = (try? modelContext.fetch(FetchDescriptor<UserProfile>()))?.first
        let serverData = try await supabase.fetchUserBackup()
        let server = serverData.flatMap { try? Self.blobDecoder.decode(AccountBackup.self, from: $0) }

        var changed = false

        // 1. Profile / settings: restore from the account when this device hasn't
        //    been set up yet (fresh install) or a different account just signed in
        //    (forceProfileRestore). Otherwise an onboarded device keeps its own
        //    settings and pushes them as the backup.
        if let sp = server?.profile, sp.hasCompletedOnboarding,
           forceProfileRestore || !(profile?.hasCompletedOnboarding ?? false) {
            applyProfile(sp, into: profile)
            changed = true
        }

        // 2. Custom mixes & drinks: additive union so no creation is ever lost.
        if let server {
            if importMixes(server.customMixes) { changed = true }
            if importDrinks(server.customDrinks) { changed = true }
        }

        // 3. Water log: merge (higher count per day wins).
        if let water = server?.waterLog { WaterLog.merge(water) }

        if changed { try? modelContext.save() }

        // 4. Push the (possibly augmented) local state back as the backup.
        let backup = buildBackup(profile: (try? modelContext.fetch(FetchDescriptor<UserProfile>()))?.first)
        let object = try JSONSerialization.jsonObject(with: Self.blobEncoder.encode(backup))
        try await supabase.uploadUserBackup(object)
    }

    private func buildBackup(profile: UserProfile?) -> AccountBackup {
        let mixes = (try? modelContext.fetch(FetchDescriptor<CustomMix>())) ?? []
        let templates = ((try? modelContext.fetch(FetchDescriptor<DrinkTemplate>())) ?? [])
            .filter(\.isCustom)
        return AccountBackup(
            profile: profile.map { ProfileBackup($0) },
            waterLog: WaterLog.allEntries,
            customMixes: mixes.map {
                MixBackup(id: $0.id, name: $0.name, ingredients: $0.ingredients, createdAt: $0.createdAt)
            },
            customDrinks: templates.map { TemplateBackup($0) }
        )
    }

    private func applyProfile(_ b: ProfileBackup, into existing: UserProfile?) {
        let p: UserProfile
        if let existing {
            p = existing
        } else {
            p = UserProfile()
            modelContext.insert(p)
        }
        p.weight = b.weight
        p.height = b.height
        p.age = b.age
        p.birthDate = b.birthDate
        p.genderRaw = b.genderRaw
        p.eliminationRate = b.eliminationRate
        p.emergencyContactName = b.emergencyContactName
        p.emergencyContactPhone = b.emergencyContactPhone
        p.homeStyleRaw = b.homeStyleRaw
        p.activeWidgetsRaw = b.activeWidgetsRaw
        p.largeText = b.largeText
        p.highContrast = b.highContrast
        p.reducedMotion = b.reducedMotion
        p.toleranceMode = b.toleranceMode
        p.warningThreshold = b.warningThreshold
        p.stomachStatusRaw = b.stomachStatusRaw
        p.statusSkinRaw = b.statusSkinRaw
        p.tipsyThreshold = b.tipsyThreshold
        p.drunkThreshold = b.drunkThreshold
        p.carefulThreshold = b.carefulThreshold
        p.dangerThreshold = b.dangerThreshold
        p.accentColorHex = b.accentColorHex
        p.sipVolumeML = b.sipVolumeML
        p.activeMedicationsRaw = b.activeMedicationsRaw
        p.healthKitEnabled = b.healthKitEnabled
        p.weeklyDrinkLimit = b.weeklyDrinkLimit
        p.soberDaysGoal = b.soberDaysGoal
        p.isProbationaryDriver = b.isProbationaryDriver
        p.drunkModeAuto = b.drunkModeAuto
        p.onboardingStepsCompleted = b.onboardingStepsCompleted
        p.hasCompletedOnboarding = b.hasCompletedOnboarding
    }

    private func importMixes(_ remote: [MixBackup]?) -> Bool {
        guard let remote, !remote.isEmpty else { return false }
        let existing = Set(((try? modelContext.fetch(FetchDescriptor<CustomMix>())) ?? []).map(\.id))
        var imported = false
        for m in remote where !existing.contains(m.id) {
            let mix = CustomMix(name: m.name, ingredients: m.ingredients)
            mix.id = m.id
            mix.createdAt = m.createdAt
            modelContext.insert(mix)
            imported = true
        }
        return imported
    }

    private func importDrinks(_ remote: [TemplateBackup]?) -> Bool {
        guard let remote, !remote.isEmpty else { return false }
        let existing = Set(((try? modelContext.fetch(FetchDescriptor<DrinkTemplate>())) ?? []).map(\.id))
        var imported = false
        for t in remote where !existing.contains(t.id) {
            let template = DrinkTemplate(
                name: t.name,
                category: DrinkCategory(rawValue: t.categoryRaw) ?? .other,
                volume: t.volume,
                abv: t.abv,
                calories: t.calories,
                iconName: t.iconName,
                isCustom: true
            )
            template.id = t.id
            template.usageCount = t.usageCount
            template.barcode = t.barcode
            modelContext.insert(template)
            imported = true
        }
        return imported
    }
}

// MARK: - Backup document (encoded as the user_backup.data JSON blob)

struct AccountBackup: Codable {
    var profile: ProfileBackup?
    var waterLog: [String: Int]?
    var customMixes: [MixBackup]?
    var customDrinks: [TemplateBackup]?
}

struct ProfileBackup: Codable {
    var weight: Double
    var height: Double
    var age: Int
    var birthDate: Date
    var genderRaw: String
    var eliminationRate: Double
    var emergencyContactName: String?
    var emergencyContactPhone: String?
    var homeStyleRaw: String
    var activeWidgetsRaw: String
    var largeText: Bool
    var highContrast: Bool
    var reducedMotion: Bool
    var toleranceMode: Bool
    var warningThreshold: Double
    var stomachStatusRaw: String
    var statusSkinRaw: String
    var tipsyThreshold: Double
    var drunkThreshold: Double
    var carefulThreshold: Double
    var dangerThreshold: Double
    var accentColorHex: String
    var sipVolumeML: Double
    var activeMedicationsRaw: String
    var healthKitEnabled: Bool
    var weeklyDrinkLimit: Int
    var soberDaysGoal: Int
    var isProbationaryDriver: Bool
    var drunkModeAuto: Bool
    var onboardingStepsCompleted: [String]
    var hasCompletedOnboarding: Bool

    init(_ p: UserProfile) {
        weight = p.weight
        height = p.height
        age = p.age
        birthDate = p.birthDate
        genderRaw = p.genderRaw
        eliminationRate = p.eliminationRate
        emergencyContactName = p.emergencyContactName
        emergencyContactPhone = p.emergencyContactPhone
        homeStyleRaw = p.homeStyleRaw
        activeWidgetsRaw = p.activeWidgetsRaw
        largeText = p.largeText
        highContrast = p.highContrast
        reducedMotion = p.reducedMotion
        toleranceMode = p.toleranceMode
        warningThreshold = p.warningThreshold
        stomachStatusRaw = p.stomachStatusRaw
        statusSkinRaw = p.statusSkinRaw
        tipsyThreshold = p.tipsyThreshold
        drunkThreshold = p.drunkThreshold
        carefulThreshold = p.carefulThreshold
        dangerThreshold = p.dangerThreshold
        accentColorHex = p.accentColorHex
        sipVolumeML = p.sipVolumeML
        activeMedicationsRaw = p.activeMedicationsRaw
        healthKitEnabled = p.healthKitEnabled
        weeklyDrinkLimit = p.weeklyDrinkLimit
        soberDaysGoal = p.soberDaysGoal
        isProbationaryDriver = p.isProbationaryDriver
        drunkModeAuto = p.drunkModeAuto
        onboardingStepsCompleted = p.onboardingStepsCompleted
        hasCompletedOnboarding = p.hasCompletedOnboarding
    }

    // Tolerant decoding: every field falls back to the UserProfile default when
    // absent, so adding a new profile field in a later app version never makes an
    // older backup fail to decode (which would silently drop the whole restore).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func d<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T {
            (try? c.decodeIfPresent(T.self, forKey: key)) ?? nil ?? fallback
        }
        weight = d(.weight, 70)
        height = d(.height, 175)
        age = d(.age, 25)
        birthDate = d(.birthDate, Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date())
        genderRaw = d(.genderRaw, "diverse")
        eliminationRate = d(.eliminationRate, 0.15)
        emergencyContactName = (try? c.decodeIfPresent(String.self, forKey: .emergencyContactName)) ?? nil
        emergencyContactPhone = (try? c.decodeIfPresent(String.self, forKey: .emergencyContactPhone)) ?? nil
        homeStyleRaw = d(.homeStyleRaw, "detailed")
        activeWidgetsRaw = d(.activeWidgetsRaw, "")
        largeText = d(.largeText, false)
        highContrast = d(.highContrast, false)
        reducedMotion = d(.reducedMotion, false)
        toleranceMode = d(.toleranceMode, false)
        warningThreshold = d(.warningThreshold, 0.5)
        stomachStatusRaw = d(.stomachStatusRaw, "light")
        statusSkinRaw = d(.statusSkinRaw, "standard")
        tipsyThreshold = d(.tipsyThreshold, 0.01)
        drunkThreshold = d(.drunkThreshold, 0.30)
        carefulThreshold = d(.carefulThreshold, 0.80)
        dangerThreshold = d(.dangerThreshold, 1.50)
        accentColorHex = d(.accentColorHex, "C9802F")
        sipVolumeML = d(.sipVolumeML, 25)
        activeMedicationsRaw = d(.activeMedicationsRaw, "")
        healthKitEnabled = d(.healthKitEnabled, false)
        weeklyDrinkLimit = d(.weeklyDrinkLimit, 0)
        soberDaysGoal = d(.soberDaysGoal, 4)
        isProbationaryDriver = d(.isProbationaryDriver, false)
        drunkModeAuto = d(.drunkModeAuto, false)
        onboardingStepsCompleted = d(.onboardingStepsCompleted, [String]())
        hasCompletedOnboarding = d(.hasCompletedOnboarding, false)
    }
}

struct MixBackup: Codable {
    var id: UUID
    var name: String
    var ingredients: [MixIngredient]
    var createdAt: Date
}

struct TemplateBackup: Codable {
    var id: UUID
    var name: String
    var categoryRaw: String
    var volume: Double
    var abv: Double
    var calories: Int
    var iconName: String
    var usageCount: Int
    var barcode: String

    init(_ t: DrinkTemplate) {
        id = t.id
        name = t.name
        categoryRaw = t.categoryRaw
        volume = t.volume
        abv = t.abv
        calories = t.calories
        iconName = t.iconName
        usageCount = t.usageCount
        barcode = t.barcode
    }
}
