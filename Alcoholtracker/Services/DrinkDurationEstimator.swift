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
            // Treat every 40 ml as roughly one shot/minute. A large logged
            // amount must not retain the one-minute estimate of a single shot.
            return max(1, min(180, volumeML / 40))
        case .spirits:
            // Keep the familiar 200 ml ~= 8 min estimate, but scale larger
            // pours instead of pinning everything above 100 ml to 8 minutes.
            minutesPerML = 0.04
            maxMinutes = 180
        case .liqueur:
            minutesPerML = 0.04
            maxMinutes = 180
        case .beer, .cider:
            minutesPerML = 0.04 // 500 ml ~ 20 min
            maxMinutes = 180
        case .wine, .sparkling:
            minutesPerML = 0.05 // 200 ml ~ 10 min
            maxMinutes = 180
        case .cocktail:
            minutesPerML = 0.05
            maxMinutes = 180
        case .mixed:
            minutesPerML = 0.045
            maxMinutes = 180
        case .fortified:
            minutesPerML = 0.06
            maxMinutes = 180
        case .water, .softDrink, .juice, .coffeeTea, .milk:
            minutesPerML = 0.02
            maxMinutes = 180
        case .other:
            minutesPerML = 0.04
            maxMinutes = 180
        }

        return max(1, min(maxMinutes, volumeML * minutesPerML))
    }
}
