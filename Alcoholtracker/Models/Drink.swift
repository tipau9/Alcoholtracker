import Foundation
import SwiftData

@Model
final class Drink {
    // PERFORMANCE: indexed via @Attribute(.unique); timestamp indexed via #Index below
    @Attribute(.unique) var id: UUID
    #Index<Drink>([\.timestamp])
    var templateID: UUID?
    var name: String
    var volume: Double        // ml
    var abv: Double           // ABV %
    var calories: Int
    var iconName: String      // SF Symbol
    var timestamp: Date
    var categoryRaw: String
    var mixerVolume: Double = 0         // ml of non-alcoholic mixer (0 for unmixed drinks)
    var mixerWaterContent: Double = 0   // % water in mixer, 0-100 (0 if no mixer)
    // 0 = auto-estimate via DrinkDurationEstimator; positive value = actual measured minutes
    var drinkDurationMinutes: Double = 0

    var category: DrinkCategory {
        get { DrinkCategory(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    var alcoholGrams: Double {
        volume * (abv / 100.0) * 0.789
    }

    init(
        name: String,
        volume: Double,
        abv: Double,
        calories: Int,
        iconName: String,
        category: DrinkCategory = .other,
        timestamp: Date = Date(),
        templateID: UUID? = nil,
        mixerVolume: Double = 0,
        mixerWaterContent: Double = 0
    ) {
        self.id = UUID()
        self.name = name
        self.volume = volume
        self.abv = abv
        self.calories = calories
        self.iconName = iconName
        self.categoryRaw = category.rawValue
        self.timestamp = timestamp
        self.templateID = templateID
        self.mixerVolume = mixerVolume
        self.mixerWaterContent = mixerWaterContent
    }

    static func from(template: DrinkTemplate, volume: Double? = nil, timestamp: Date = Date()) -> Drink {
        let actualVolume = volume ?? template.volume
        let scaledCalories: Int
        if let v = volume, template.volume > 0 {
            scaledCalories = Int(Double(template.calories) * v / template.volume)
        } else {
            scaledCalories = template.calories
        }
        return Drink(
            name: template.name,
            volume: actualVolume,
            abv: template.abv,
            calories: scaledCalories,
            iconName: template.iconName,
            category: template.category,
            timestamp: timestamp,
            templateID: template.id
        )
    }
}
