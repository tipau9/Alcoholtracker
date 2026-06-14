import Foundation
import SwiftData

private let _mixDecoder = JSONDecoder()
private let _mixEncoder = JSONEncoder()

// MARK: - MixIngredient

struct MixIngredient: Codable, Identifiable, Hashable {
    var id: UUID = UUID()
    var name: String
    var abv: Double    // ABV % (was alcoholPercent)
    var volume: Double // ml

    var alcoholGrams: Double {
        volume * (abv / 100.0) * 0.789
    }
}

// MARK: - CustomMix

@Model
final class CustomMix {
    var id: UUID
    var name: String
    var ingredientsData: Data  // JSON-encoded [MixIngredient]
    var createdAt: Date

    var ingredients: [MixIngredient] {
        get { (try? _mixDecoder.decode([MixIngredient].self, from: ingredientsData)) ?? [] }
        set { ingredientsData = (try? _mixEncoder.encode(newValue)) ?? Data() }
    }

    var totalVolume: Double {
        ingredients.reduce(0) { $0 + $1.volume }
    }

    var totalAbv: Double {
        guard totalVolume > 0 else { return 0 }
        let pureAlcohol = ingredients.reduce(0.0) { $0 + $1.volume * ($1.abv / 100.0) }
        return (pureAlcohol / totalVolume) * 100.0
    }

    var totalAlcoholGrams: Double {
        ingredients.reduce(0) { $0 + $1.alcoholGrams }
    }

    var estimatedCalories: Int {
        Int(totalAlcoholGrams * 7)
    }

    init(name: String, ingredients: [MixIngredient] = []) {
        self.id = UUID()
        self.name = name
        self.ingredientsData = (try? _mixEncoder.encode(ingredients)) ?? Data()
        self.createdAt = Date()
    }

    func asTemplate() -> DrinkTemplate {
        DrinkTemplate(
            name: name,
            category: .cocktail,
            volume: totalVolume,
            abv: totalAbv,
            calories: estimatedCalories,
            iconName: "wineglass",
            isCustom: true
        )
    }

    func asDrink(timestamp: Date = Date()) -> Drink {
        Drink(
            name: name,
            volume: totalVolume,
            abv: totalAbv,
            calories: estimatedCalories,
            iconName: "wineglass",
            category: .cocktail,
            timestamp: timestamp
        )
    }
}
