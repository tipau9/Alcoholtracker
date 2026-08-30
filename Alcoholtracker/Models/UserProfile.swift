import Foundation
import SwiftData

// Parses a stored enum-rawValue list. The current format is a plain
// comma-separated string ("a,b,c"), which avoids running a JSONDecoder on every
// SwiftUI read of activeWidgets / activeMedications. Older builds stored a JSON
// array ("[\"a\",\"b\"]"); that legacy shape is still decoded as a fallback on
// first read, and gets rewritten to CSV the next time the property is set.
private func _parseRawList(_ stored: String) -> [String] {
    let trimmed = stored.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return [] }
    if trimmed.hasPrefix("[") {
        // Legacy JSON array from an older app version.
        guard let data = trimmed.data(using: .utf8),
              let raws = try? JSONDecoder().decode([String].self, from: data)
        else { return [] }
        return raws
    }
    return trimmed.split(separator: ",").map(String.init)
}

// MARK: - Supporting Enums

enum Gender: String, Codable, CaseIterable {
    case male    = "male"
    case female  = "female"
    case diverse = "diverse"

    var localizedName: String {
        switch self {
        case .male:    return "Männlich"
        case .female:  return "Weiblich"
        case .diverse: return "Divers"
        }
    }
}

enum HomeStyle: String, Codable, CaseIterable {
    case minimal  = "minimal"
    case detailed = "detailed"

    var localizedName: String {
        switch self {
        case .minimal:  return "Minimal"
        case .detailed: return "Detailliert"
        }
    }
}

enum BodyDataValidation {
    static let weightRange: ClosedRange<Double> = 35...250
    static let heightRange: ClosedRange<Double> = 120...230
    static let ageRange: ClosedRange<Int> = 18...100

    static func weightError(_ value: Double) -> String? {
        guard value.isFinite, weightRange.contains(value) else {
            return "Gewicht muss zwischen 35 und 250 kg liegen."
        }
        return nil
    }

    static func heightError(_ value: Double) -> String? {
        guard value.isFinite, heightRange.contains(value) else {
            return "Größe muss zwischen 120 und 230 cm liegen."
        }
        return nil
    }

    static func ageError(_ value: Int) -> String? {
        guard ageRange.contains(value) else {
            return "Alter muss zwischen 18 und 100 Jahren liegen."
        }
        return nil
    }

    static func age(from birthDate: Date, now: Date = Date()) -> Int {
        Calendar.current.dateComponents([.year], from: birthDate, to: now).year ?? 0
    }

    static func birthDateRange(now: Date = Date()) -> ClosedRange<Date> {
        let calendar = Calendar.current
        // Normalize to calendar days so someone whose 18th or 100th birthday is
        // today remains selectable regardless of the current time of day.
        let today = calendar.startOfDay(for: now)
        let oldest = calendar.date(byAdding: .year, value: -ageRange.upperBound, to: today) ?? today
        let youngest = calendar.date(byAdding: .year, value: -ageRange.lowerBound, to: today) ?? today
        return oldest...youngest
    }
}

enum WidgetType: String, Codable, CaseIterable {
    // Info-Kacheln (2x2 grid on home screen)
    case timeToLimit      = "timeToLimit"
    case water            = "water"
    case calories         = "calories"
    case drinkCount       = "drinkCount"
    // Abschnitte (full-width sections)
    case bacCurve         = "bacCurve"
    case hydration        = "hydration"
    case stomachStatus    = "stomachStatus"
    case favStrip         = "favStrip"
    case drinkHistory     = "drinkHistory"
    case milestone        = "milestone"
    case dayStats         = "dayStats"
    case safetyActions    = "safetyActions"
    // Reserved for future phases
    case streak           = "streak"
    case crewStatus       = "crewStatus"
    case drinkingSpeed    = "drinkingSpeed"
    case hangover         = "hangover"

    // Widgets added after the initial widget system shipped. Profiles whose
    // stored list is empty (created before customisation existed) do NOT get
    // these automatically, so an app update never changes an untouched home
    // screen; fresh profiles receive every case via the initialiser.
    static let newerAdditions: Set<WidgetType> = [.milestone, .dayStats, .safetyActions]

    static var preWidgetDefault: [WidgetType] {
        allCases.filter { !newerAdditions.contains($0) }
    }

    static let explicitNoneRaw = "__none__"

    var localizedName: String {
        switch self {
        case .timeToLimit:   return "Bis 0,5 ‰"
        case .water:         return "Wasser"
        case .calories:      return "Kalorien"
        case .drinkCount:    return "Drinks heute"
        case .bacCurve:      return "BAC-Verlauf"
        case .hydration:     return "Wasser-Tracker"
        case .stomachStatus: return "Magen-Status"
        case .favStrip:      return "Schnell hinzufügen"
        case .drinkHistory:  return "Verlauf heute"
        case .milestone:     return "Nächster Meilenstein"
        case .dayStats:      return "Tages-Stats"
        case .safetyActions: return "Safety-Aktionen"
        case .streak:        return "Streak"
        case .crewStatus:    return "Freunde-Status"
        case .drinkingSpeed: return "Trinkgeschwindigkeit"
        case .hangover:      return "Kater-Prognose"
        }
    }

    var symbolName: String {
        switch self {
        case .timeToLimit:   return "car.fill"
        case .water:         return "drop.fill"
        case .calories:      return "flame.fill"
        case .drinkCount:    return "figure.walk"
        case .bacCurve:      return "chart.line.uptrend.xyaxis"
        case .hydration:     return "drop.circle.fill"
        case .stomachStatus: return "fork.knife"
        case .favStrip:      return "bolt.fill"
        case .drinkHistory:  return "clock.fill"
        case .milestone:     return "car.fill"
        case .dayStats:      return "chart.bar.fill"
        case .safetyActions: return "shield.fill"
        case .streak:        return "star.fill"
        case .crewStatus:    return "person.3.fill"
        case .drinkingSpeed: return "speedometer"
        case .hangover:      return "zzz"
        }
    }
}

// MARK: - UserProfile

@Model
final class UserProfile {
    var weight: Double           // kg
    var height: Double           // cm
    var age: Int
    var eliminationRate: Double  // promille/h, typically 0.10 to 0.20
    var emergencyContactName: String?
    var emergencyContactPhone: String?
    var largeText: Bool          // was useLargeText
    var highContrast: Bool       // was useHighContrast
    var reducedMotion: Bool      // was reduceAnimations
    var hasCompletedOnboarding: Bool
    var toleranceMode: Bool
    var warningThreshold: Double

    // Raw backing stores
    // Customizable BAC thresholds (sober is always 0.00)
    // Inline defaults required for SwiftData lightweight migration.
    var tipsyThreshold:   Double = 0.01  // start of "tipsy" range
    var drunkThreshold:   Double = 0.30
    var carefulThreshold: Double = 0.80
    var dangerThreshold:  Double = 1.50

    // FIX BUG1: birthDate replaces plain age int for accurate age computation
    var birthDate: Date = Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date()

    // FIX FEATURE10: user-chosen accent color hex
    var accentColorHex: String = "C9802F"

    // FEATURE2: volume counted as one sip in the sip counter (ml)
    var sipVolumeML: Double = 25

    var genderRaw: String
    var homeStyleRaw: String
    var activeWidgetsRaw: String // comma-separated WidgetType rawValues (legacy: JSON array)

    // Order of the full-width home sections (WidgetType rawValues plus the
    // pseudo id "grid" for the 2x2 tile block). Empty = built-in default order.
    // Inline default required for SwiftData lightweight migration.
    var homeSectionOrderRaw: String = ""
    var stomachStatusRaw: String
    var statusSkinRaw: String = "standard"

    // A4: Onboarding analytics (local only, no external tracking)
    // Inline default required for SwiftData lightweight migration.
    var onboardingStepsCompleted: [String] = []

    // B3: Active medication flags for alcohol interaction warnings
    // Inline default required for SwiftData lightweight migration.
    var activeMedicationsRaw: String = ""  // comma-separated MedicationFlag rawValues (legacy: JSON array)

    // B7: HealthKit export enabled
    var healthKitEnabled: Bool = false

    // Weekly drink limit & sober days goal
    var weeklyDrinkLimit: Int = 0
    var soberDaysGoal: Int = 4

    // Probezeit / Fahranfänger: gesetzliche Promillegrenze 0,0 statt 0,5.
    // Wird im Sicherheits-Tab gesetzt und steuert auch die "Fahrbereit"-Labels
    // der als Fahrer markierten Freunde.
    // Inline default required for SwiftData lightweight migration.
    var isProbationaryDriver: Bool = false

    // Drunk-Mode: when on, the home screen auto-switches to a stripped-down,
    // big-touch-target layout once the BAC crosses the "careful" threshold.
    // Inline default required for SwiftData lightweight migration.
    var drunkModeAuto: Bool = false

    // Konservativ rechnen (Worst-Case): safety figures assume full alcohol
    // bioavailability and the cautious base elimination rate, while retaining
    // physical absorption timing. The rest of the app keeps the realistic model.
    // Inline default required for SwiftData lightweight migration.
    var conservativeSafety: Bool = false

    // Konservativ in der ganzen App: when on, the worst-case model is applied
    // everywhere (home BAC, curves, add badges), not just the safety screens. This
    // implies the safety figures are conservative too. Off keeps the realistic
    // model app-wide (the safety screens still honour conservativeSafety on their own).
    // Inline default required for SwiftData lightweight migration.
    var conservativeEverywhere: Bool = false

    // Whether the safety readiness timers + forecast should use the worst-case model:
    // either the safety-only switch or the app-wide switch turns it on.
    var conservativeForSafety: Bool { conservativeSafety || conservativeEverywhere }

    // Whether the rest of the app (home, charts, badges) should use the worst-case
    // model. Only the app-wide switch does this.
    var conservativeForApp: Bool { conservativeEverywhere }

    // MARK: Computed wrappers

    var gender: Gender {
        get { Gender(rawValue: genderRaw) ?? .diverse }
        set { genderRaw = newValue.rawValue }
    }

    var homeStyle: HomeStyle {
        get { HomeStyle(rawValue: homeStyleRaw) ?? .detailed }
        set { homeStyleRaw = newValue.rawValue }
    }

    var defaultStomachStatus: StomachStatus {
        get { StomachStatus(rawValue: stomachStatusRaw) ?? .light }
        set { stomachStatusRaw = newValue.rawValue }
    }

    var statusSkin: StatusSkin {
        get { StatusSkin(rawValue: statusSkinRaw) ?? .standard }
        set { statusSkinRaw = newValue.rawValue }
    }

    var homeSectionOrder: [String] {
        get { homeSectionOrderRaw.isEmpty ? [] : homeSectionOrderRaw.components(separatedBy: ",") }
        set { homeSectionOrderRaw = newValue.joined(separator: ",") }
    }

    var activeWidgets: [WidgetType] {
        get {
            if activeWidgetsRaw == WidgetType.explicitNoneRaw { return [] }
            let stored = _parseRawList(activeWidgetsRaw)
            // An empty stored list means "all active" (the default for a fresh
            // profile, which is initialised with every case). A NON-empty list is
            // an explicit user choice and is now respected exactly, so turning a
            // widget off in the edit sheet actually sticks. Previously every
            // missing case was re-appended here, which silently resurrected any
            // widget the user had disabled. New widget types added in an update
            // stay reachable: the edit sheet lists them and a fresh profile gets
            // them via the all-cases initialiser.
            guard !stored.isEmpty else { return WidgetType.preWidgetDefault }
            return stored.compactMap { WidgetType(rawValue: $0) }
        }
        set {
            activeWidgetsRaw = newValue.isEmpty
                ? WidgetType.explicitNoneRaw
                : newValue.map(\.rawValue).joined(separator: ",")
        }
    }

    var activeMedications: [MedicationFlag] {
        get {
            _parseRawList(activeMedicationsRaw).compactMap { MedicationFlag(rawValue: $0) }
        }
        set {
            activeMedicationsRaw = newValue.map(\.rawValue).joined(separator: ",")
        }
    }

    // FIX BUG9: derived from birthDate for accuracy; falls back to stored age
    var currentAge: Int {
        let fromBirth = Calendar.current.dateComponents([.year], from: birthDate, to: Date()).year ?? 0
        return fromBirth > 0 ? fromBirth : age
    }

    var validatedWeight: Double {
        // Keep UI validation at 35 kg, but use the engine's 30 kg floor for old or
        // malformed stored profiles so the safety calculation never raises a real
        // low weight and thereby understates BAC.
        min(max(weight, 30), BodyDataValidation.weightRange.upperBound)
    }

    var validatedHeight: Double {
        min(max(height, BodyDataValidation.heightRange.lowerBound), BodyDataValidation.heightRange.upperBound)
    }

    var validatedAge: Int {
        min(max(currentAge, BodyDataValidation.ageRange.lowerBound), BodyDataValidation.ageRange.upperBound)
    }

    // FIX BUG1: when toleranceMode is active, enforce minimum elimination rate of 0.20
    // (regular drinkers metabolise at 0.17-0.25 vs 0.10-0.20 for occasional drinkers)
    var effectiveEliminationRate: Double {
        toleranceMode ? max(eliminationRate, 0.20) : eliminationRate
    }

    // Worst-case (conservative) safety math must NOT assume the faster metabolism of
    // tolerance mode: a higher elimination rate shortens the "nüchtern"/"fahrbereit"
    // times and errs optimistic, which is the opposite of what a worst-case readout
    // should do. Conservative callers therefore drop the tolerance floor and use the
    // user's base rate; everyone else keeps the tolerance-adjusted rate.
    func resolvedEliminationRate(conservative: Bool) -> Double {
        conservative ? eliminationRate : effectiveEliminationRate
    }

    // MARK: Driving limit
    // Legal BAC limit in ‰. 0,0 during the probationary period (Probezeit) or
    // for novice drivers, otherwise the German 0,5 ‰ limit.
    var drivingLimit: Double { isProbationaryDriver ? 0.0 : 0.5 }

    // "Fahrbereit" only when at or below the legal limit. In Probezeit the
    // driver must be essentially sober; the small epsilon absorbs rounding so a
    // residual 0,00x value does not block an otherwise sober driver.
    func mayDrive(at bac: Double) -> Bool {
        isProbationaryDriver ? bac <= 0.005 : bac < 0.5
    }

    // MARK: Widmark distribution factor (Watson 1980 formula)
    // More accurate than a flat gender lookup. Clamped to physiological range.
    //
    // Watson estimates TOTAL BODY WATER (litres). The Widmark r for BLOOD alcohol
    // (the legal Promille basis) is TBW divided by the blood-water fraction
    // (~0.806), NOT TBW/weight: TBW/weight yields the body-water concentration,
    // which overstates blood BAC by 1/0.806 (~24%). Dividing by 0.806 brings r in
    // line with the Watson-Widmark / German forensic values (men ~0.70, women
    // ~0.60). Clamp is the physiological blood-r range.
    var distributionFactor: Double {
        min(max((totalBodyWater / validatedWeight) / 0.806, 0.50), 0.90)
    }

    // MARK: Total body water (Watson 1980)
    // Estimated total body water in LITRES from age/height/weight/gender. Drives
    // both the Widmark distribution factor above and the exact dehydration model
    // (HydrationCalculator): a deficit of X ml is far more dehydrating for a small
    // person with little body water than for a large one, so the hydration status
    // is scaled against this rather than an absolute ml threshold.
    var totalBodyWater: Double {
        let a = Double(validatedAge)
        switch gender {
        case .male:
            return 2.447 - 0.09516 * a + 0.1074 * validatedHeight + 0.3362 * validatedWeight
        case .female:
            return -2.097 + 0.1069 * validatedHeight + 0.2466 * validatedWeight
        case .diverse:
            let m = 2.447 - 0.09516 * a + 0.1074 * validatedHeight + 0.3362 * validatedWeight
            let f = -2.097 + 0.1069 * validatedHeight + 0.2466 * validatedWeight
            return (m + f) / 2.0
        }
    }

    init(
        weight: Double = 70,
        height: Double = 175,
        age: Int = 25,
        gender: Gender = .diverse,
        eliminationRate: Double = 0.15,
        emergencyContactName: String? = nil,
        emergencyContactPhone: String? = nil,
        homeStyle: HomeStyle = .detailed,
        activeWidgets: [WidgetType] = WidgetType.allCases,
        largeText: Bool = false,
        highContrast: Bool = false,
        reducedMotion: Bool = false,
        hasCompletedOnboarding: Bool = false,
        toleranceMode: Bool = false,
        warningThreshold: Double = 0.5,
        defaultStomachStatus: StomachStatus = .light,
        statusSkin: StatusSkin = .standard,
        tipsyThreshold: Double   = 0.01,
        drunkThreshold: Double   = 0.30,
        carefulThreshold: Double = 0.80,
        dangerThreshold: Double  = 1.50
    ) {
        self.weight = weight
        self.height = height
        self.age = age
        self.genderRaw = gender.rawValue
        self.eliminationRate = eliminationRate
        self.emergencyContactName = emergencyContactName
        self.emergencyContactPhone = emergencyContactPhone
        self.homeStyleRaw = homeStyle.rawValue
        self.largeText = largeText
        self.highContrast = highContrast
        self.reducedMotion = reducedMotion
        self.hasCompletedOnboarding = hasCompletedOnboarding
        self.toleranceMode = toleranceMode
        self.warningThreshold = warningThreshold
        self.stomachStatusRaw = defaultStomachStatus.rawValue
        self.statusSkinRaw    = statusSkin.rawValue
        self.tipsyThreshold   = tipsyThreshold
        self.drunkThreshold   = drunkThreshold
        self.carefulThreshold = carefulThreshold
        self.dangerThreshold  = dangerThreshold

        self.activeWidgetsRaw = activeWidgets.map(\.rawValue).joined(separator: ",")
    }
}
