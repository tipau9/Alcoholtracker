import Foundation

// MARK: - MixerCategory

enum MixerCategory: String, Codable, CaseIterable {
    case soda    = "soda"
    case bitter  = "bitter"
    case energy  = "energy"
    case juice   = "juice"
    case water   = "water"
    case cream   = "cream"
    case tea     = "tea"
    case other   = "other"

    var localizedName: String {
        switch self {
        case .soda:   return "Softdrink"
        case .bitter: return "Bitter"
        case .energy: return "Energy"
        case .juice:  return "Saft"
        case .water:  return "Wasser"
        case .cream:  return "Cremig"
        case .tea:    return "Tee"
        case .other:  return "Sonstiges"
        }
    }

    var symbolName: String {
        switch self {
        case .soda:   return "cup.and.saucer.fill"
        case .bitter: return "drop.fill"
        case .energy: return "bolt.fill"
        case .juice:  return "leaf.fill"
        case .water:  return "drop"
        case .cream:  return "drop.circle.fill"
        case .tea:    return "cup.and.saucer"
        case .other:  return "star.fill"
        }
    }
}

// MARK: - Mixer

struct Mixer: Identifiable, Hashable {
    var id: String { name }
    let name: String
    let category: MixerCategory
    let caloriesPer100ml: Int
    let waterContentPercent: Double
    let icon: String
}
