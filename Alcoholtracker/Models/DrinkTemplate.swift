import Foundation
import SwiftData

// MARK: - DrinkCategory

enum DrinkCategory: String, Codable, CaseIterable {
    case beer      = "beer"
    case wine      = "wine"
    case sparkling = "sparkling"
    case spirits   = "spirits"
    case liqueur   = "liqueur"
    case cocktail  = "cocktail"
    case mixed     = "mixed"
    case shot      = "shot"
    case cider     = "cider"
    case fortified = "fortified"
    case other     = "other"

    var localizedName: String {
        switch self {
        case .beer:      return "Bier"
        case .wine:      return "Wein"
        case .sparkling: return "Sekt und Schaumwein"
        case .spirits:   return "Spirituose"
        case .liqueur:   return "Likör"
        case .cocktail:  return "Cocktail"
        case .mixed:     return "Mischgetränk"
        case .shot:      return "Shot"
        case .cider:     return "Cider"
        case .fortified: return "Likörwein"
        case .other:     return "Sonstiges"
        }
    }

    var symbolName: String {
        switch self {
        case .beer:      return "mug.fill"
        case .cider:     return "mug.fill"
        case .wine:      return "wineglass.fill"
        case .sparkling: return "sparkles"
        case .fortified: return "wineglass.fill"
        case .spirits:   return "flame.fill"
        case .liqueur:   return "wineglass.fill"
        case .shot:      return "flame.fill"
        case .cocktail:  return "wineglass.fill"
        case .mixed:     return "cylinder.fill"
        case .other:     return "cup.and.saucer"
        }
    }

    // Standard bottle sizes shown in the bottle-mode slider UI.
    var commonBottleSizes: [(label: String, volumeML: Double)] {
        switch self {
        case .spirits, .liqueur:
            return [("Klein (0,2 L)", 200), ("Standard (0,5 L)", 500),
                    ("Flasche (0,7 L)", 700), ("Liter (1,0 L)", 1000)]
        case .beer, .cider:
            return [("Flasche (0,33 L)", 330), ("Flasche (0,5 L)", 500),
                    ("Sixpack (6x0,33 L)", 1980), ("Kasten (20x0,5 L)", 10000)]
        case .wine, .sparkling, .fortified:
            return [("Halbe (0,375 L)", 375), ("Flasche (0,75 L)", 750),
                    ("Magnum (1,5 L)", 1500)]
        default:
            return [("Klein (0,33 L)", 330), ("Standard (0,5 L)", 500),
                    ("Gross (0,7 L)", 700), ("Liter (1,0 L)", 1000)]
        }
    }

    // Scales stomachStatus.absorptionMinutes. CO2 accelerates gastric emptying;
    // shots reach peak faster due to small volume and rapid transit.
    var absorptionModifier: Double {
        switch self {
        case .shot:                         return 0.75
        case .beer, .cider, .sparkling:     return 0.85
        default:                            return 1.0
        }
    }
}

// MARK: - BACStatus

enum BACStatus: Equatable, Hashable, CaseIterable {
    case sober    // < 0.01
    case tipsy    // 0.01 to < 0.3
    case drunk    // 0.3 to < 0.8
    case careful  // 0.8 to < 1.5
    case danger   // >= 1.5

    init(bac: Double) {
        switch bac {
        case ..<0.01:  self = .sober
        case ..<0.3:   self = .tipsy
        case ..<0.8:   self = .drunk
        case ..<1.5:   self = .careful
        default:       self = .danger
        }
    }

    // Uses custom per-user thresholds when available.
    init(bac: Double, profile: UserProfile?) {
        guard let p = profile else {
            self.init(bac: bac)
            return
        }
        switch bac {
        case ..<p.tipsyThreshold:   self = .sober
        case ..<p.drunkThreshold:   self = .tipsy
        case ..<p.carefulThreshold: self = .drunk
        case ..<p.dangerThreshold:  self = .careful
        default:                    self = .danger
        }
    }

    var localizedName: String {
        switch self {
        case .sober:   return "Nüchtern"
        case .tipsy:   return "Leicht beschwipst"
        case .drunk:   return "Beschwipst"
        case .careful: return "Aufpassen"
        case .danger:  return "Fahruntauglich"
        }
    }

    func label(for skin: StatusSkin) -> String { skin.label(for: self) }

    // Used for sorting: higher = more critical
    var level: Int {
        switch self {
        case .sober:   return 0
        case .tipsy:   return 1
        case .drunk:   return 2
        case .careful: return 3
        case .danger:  return 4
        }
    }
}

// MARK: - StomachStatus

enum StomachStatus: String, Codable, CaseIterable {
    case empty = "empty"
    case light = "light"
    case full  = "full"

    var localizedName: String {
        switch self {
        case .empty: return "Leer"
        case .light: return "Leicht gefüllt"
        case .full:  return "Satt"
        }
    }

    var symbolName: String {
        switch self {
        case .empty: return "circle"
        case .light: return "circle.lefthalf.filled"
        case .full:  return "circle.fill"
        }
    }

    var absorptionMinutes: Double {
        switch self {
        case .empty: return 45.0   // fasted: 30-60 min to peak (Widmark)
        case .light: return 75.0   // mixed meal: 60-90 min to peak
        case .full:  return 120.0  // high-calorie meal: 90-180 min to peak
        }
    }

    // Peak-BAC scaling = (forensic resorption deficit) × (food/absorption-completeness).
    // A ~10% first-pass loss in the gut wall and liver applies to the whole dose even
    // when fasted, so the empty-stomach factor is 0.90 (forensic minimum) rather than
    // 1.00. Food in the stomach lowers the peak further on top of that, preserving the
    // empty > light > full gradient: 1.00→0.90, 0.90→0.81, 0.75→0.68.
    var peakFactor: Double {
        switch self {
        case .empty: return 0.90
        case .light: return 0.81
        case .full:  return 0.68
        }
    }
}

// MARK: - DrinkTemplate

@Model
final class DrinkTemplate {
    // PERFORMANCE: indexed; name and category indexed via #Index for search and filtering
    @Attribute(.unique) var id: UUID
    #Index<DrinkTemplate>([\.name], [\.categoryRaw])
    var name: String
    var categoryRaw: String
    var volume: Double       // ml
    var abv: Double          // ABV %
    var calories: Int
    var iconName: String     // SF Symbol
    var isCustom: Bool
    var usageCount: Int
    var barcode: String = "" // EAN/UPC barcode; empty = no barcode

    var category: DrinkCategory {
        get { DrinkCategory(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    init(
        name: String,
        category: DrinkCategory,
        volume: Double,
        abv: Double,
        calories: Int,
        iconName: String? = nil,
        isCustom: Bool = false
    ) {
        self.id = UUID()
        self.name = name
        self.categoryRaw = category.rawValue
        self.volume = volume
        self.abv = abv
        self.calories = calories
        self.iconName = iconName ?? category.symbolName
        self.isCustom = isCustom
        self.usageCount = 0
    }
}
