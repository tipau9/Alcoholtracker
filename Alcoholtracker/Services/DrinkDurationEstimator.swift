import Foundation

// MARK: - DrinkDurationEstimator

// Estimates how many minutes a drink takes based on beverage type and volume.
// BACCalculator uses this as the default drinking window when the user has not
// entered a custom estimate.
enum DrinkDurationEstimator {
    static func estimate(category: DrinkCategory, volumeML: Double) -> Double {
        return DrinkPaceMemory.adjustedEstimate(
            category: category,
            baseMinutes: baseEstimate(category: category, volumeML: volumeML)
        )
    }

    static func baseEstimate(category: DrinkCategory, volumeML: Double) -> Double {
        let minutesPerML: Double
        let maxMinutes: Double

        switch category {
        case .shot:
            return 1
        case .spirits:
            minutesPerML = 0.08
            maxMinutes = 8
        case .liqueur:
            minutesPerML = 0.07
            maxMinutes = 8
        case .beer, .cider:
            minutesPerML = 0.04 // 500 ml ~ 20 min
            maxMinutes = 20
        case .wine, .sparkling:
            minutesPerML = 0.05 // 200 ml ~ 10 min
            maxMinutes = 12
        case .cocktail:
            minutesPerML = 0.05
            maxMinutes = 15
        case .mixed:
            minutesPerML = 0.045
            maxMinutes = 15
        case .fortified:
            minutesPerML = 0.06
            maxMinutes = 10
        case .water, .softDrink, .juice, .coffeeTea, .milk:
            minutesPerML = 0.02
            maxMinutes = 10
        case .other:
            minutesPerML = 0.04
            maxMinutes = 12
        }

        return max(1, min(maxMinutes, volumeML * minutesPerML))
    }
}
