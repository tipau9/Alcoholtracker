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

enum WidgetType: String, Codable, CaseIterable {
    // Info-Kacheln (2x2 grid on home screen)
    case timeToLimit      = "timeToLimit"
    case water            = "water"
    case calories         = "calories"
    case drinkCount       = "drinkCount"
    // Abschnitte (full-width sections)
    case bacCurve         = "bacCurve"
    case stomachStatus    = "stomachStatus"
    case favStrip         = "favStrip"
    case drinkHistory     = "drinkHistory"
    // Reserved for future phases
    case streak           = "streak"
    case crewStatus       = "crewStatus"
    case drinkingSpeed    = "drinkingSpeed"
    case hangover         = "hangover"

    var localizedName: String {
        switch self {
        case .timeToLimit:   return "Bis 0,5 ‰"
        case .water:         return "Wasser"
        case .calories:      return "Kalorien"
        case .drinkCount:    return "Drinks heute"
        case .bacCurve:      return "BAC-Verlauf"
        case .stomachStatus: return "Magen-Status"
        case .favStrip:      return "Schnell hinzufügen"
        case .drinkHistory:  return "Verlauf heute"
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
        case .stomachStatus: return "fork.knife"
        case .favStrip:      return "bolt.fill"
        case .drinkHistory:  return "clock.fill"
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

    // Konservativ rechnen (Worst-Case): when on, the safety-critical figures
    // (the Sicherheit timers and the Vorausschau) drop the resorption deficit
    // and the absorption ramp, so they show the highest BAC the body could
    // plausibly reach (ADAC-style) instead of the realistic peak. The rest of
    // the app (Home, charts) keeps the realistic model.
    // Inline default required for SwiftData lightweight migration.
    var conservativeSafety: Bool = false

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

    var activeWidgets: [WidgetType] {
        get {
            let stored = _parseRawList(activeWidgetsRaw)
            // An empty stored list means "all active" (the default for a fresh
            // profile, which is initialised with every case). A NON-empty list is
            // an explicit user choice and is now respected exactly, so turning a
            // widget off in the edit sheet actually sticks. Previously every
            // missing case was re-appended here, which silently resurrected any
            // widget the user had disabled. New widget types added in an update
            // stay reachable: the edit sheet lists them and a fresh profile gets
            // them via the all-cases initialiser.
            guard !stored.isEmpty else { return WidgetType.allCases }
            return stored.compactMap { WidgetType(rawValue: $0) }
        }
        set {
            activeWidgetsRaw = newValue.map(\.rawValue).joined(separator: ",")
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

    // FIX BUG1: when toleranceMode is active, enforce minimum elimination rate of 0.20
    // (regular drinkers metabolise at 0.17-0.25 vs 0.10-0.20 for occasional drinkers)
    var effectiveEliminationRate: Double {
        toleranceMode ? max(eliminationRate, 0.20) : eliminationRate
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
        min(max((totalBodyWater / weight) / 0.806, 0.50), 0.90)
    }

    // MARK: Total body water (Watson 1980)
    // Estimated total body water in LITRES from age/height/weight/gender. Drives
    // both the Widmark distribution factor above and the exact dehydration model
    // (HydrationCalculator): a deficit of X ml is far more dehydrating for a small
    // person with little body water than for a large one, so the hydration status
    // is scaled against this rather than an absolute ml threshold.
    var totalBodyWater: Double {
        let a = Double(currentAge)
        switch gender {
        case .male:
            return 2.447 - 0.09516 * a + 0.1074 * height + 0.3362 * weight
        case .female:
            return -2.097 + 0.1069 * height + 0.2466 * weight
        case .diverse:
            let m = 2.447 - 0.09516 * a + 0.1074 * height + 0.3362 * weight
            let f = -2.097 + 0.1069 * height + 0.2466 * weight
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
