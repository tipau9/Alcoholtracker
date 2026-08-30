import Foundation
import SwiftData

enum MealImpact: String, Codable, CaseIterable, Identifiable, Hashable {
    case snack
    case lightMeal
    case fullMeal

    var id: String { rawValue }

    var title: String {
        switch self {
        case .snack: return "Snack"
        case .lightMeal: return "Leichte Mahlzeit"
        case .fullMeal: return "Volle Mahlzeit"
        }
    }

    var icon: String {
        switch self {
        case .snack: return "takeoutbag.and.cup.and.straw.fill"
        case .lightMeal: return "fork.knife"
        case .fullMeal: return "birthday.cake.fill"
        }
    }

    /// Stretches only the absorption time that remains when the meal is logged.
    /// The total alcohol dose is conserved; food never removes alcohol already
    /// absorbed into the blood and therefore cannot make live BAC jump down.
    var remainingAbsorptionMultiplier: Double {
        switch self {
        case .snack: return 1.15
        case .lightMeal: return 1.35
        case .fullMeal: return 1.65
        }
    }

    var activeDuration: TimeInterval {
        switch self {
        case .snack: return 2 * 3600
        case .lightMeal: return 3 * 3600
        case .fullMeal: return 4 * 3600
        }
    }
}

struct MealEventValue: Codable, Hashable {
    let id: UUID
    let timestamp: Date
    let impact: MealImpact
    let name: String
}

@Model
final class MealEvent {
    @Attribute(.unique) var id: UUID
    #Index<MealEvent>([\.timestamp])
    var timestamp: Date
    var impactRaw: String
    var name: String

    var impact: MealImpact {
        get { MealImpact(rawValue: impactRaw) ?? .lightMeal }
        set { impactRaw = newValue.rawValue }
    }

    var value: MealEventValue {
        MealEventValue(id: id, timestamp: timestamp, impact: impact, name: name)
    }

    init(timestamp: Date = Date(), impact: MealImpact, name: String = "") {
        id = UUID()
        self.timestamp = timestamp
        impactRaw = impact.rawValue
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

enum BreathalyzerSource: String, Codable, CaseIterable, Identifiable {
    case manual
    case bluetooth

    var id: String { rawValue }
    var title: String { self == .manual ? "Manuell" : "Bluetooth-Gerät" }
}

@Model
final class BreathalyzerReading {
    @Attribute(.unique) var id: UUID
    #Index<BreathalyzerReading>([\.timestamp])
    var timestamp: Date
    var measuredBAC: Double
    var estimatedBAC: Double
    var sourceRaw: String
    var note: String

    var source: BreathalyzerSource {
        get { BreathalyzerSource(rawValue: sourceRaw) ?? .manual }
        set { sourceRaw = newValue.rawValue }
    }

    init(
        timestamp: Date = Date(),
        measuredBAC: Double,
        estimatedBAC: Double,
        source: BreathalyzerSource = .manual,
        note: String = ""
    ) {
        id = UUID()
        self.timestamp = timestamp
        self.measuredBAC = min(5, max(0, measuredBAC))
        self.estimatedBAC = min(5, max(0, estimatedBAC))
        sourceRaw = source.rawValue
        self.note = note.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
