import Foundation

// MARK: - DrinkDurationEstimator

// Estimates how many minutes a drink takes based on beverage type and volume.
// BACCalculator uses this as the default drinking window when the user has not
// entered a custom estimate.
enum DrinkDurationEstimator {
    static func estimate(category: DrinkCategory, volumeML: Double) -> Double {
        let minutesPerML: Double
        switch category {
        case .shot:
            return 1
        case .spirits:
            minutesPerML = 0.35
        case .liqueur:
            minutesPerML = 0.30
        case .beer, .cider:
            minutesPerML = 0.16   // 500 ml ~ 80 min
        case .wine, .sparkling:
            minutesPerML = 0.18   // 200 ml ~ 36 min
        case .cocktail:
            minutesPerML = 0.12
        case .mixed:
            minutesPerML = 0.14
        case .fortified:
            minutesPerML = 0.15
        case .water, .softDrink, .juice, .coffeeTea, .milk:
            minutesPerML = 0.04
        case .other:
            minutesPerML = 0.08
        }
        return max(1, min(120, volumeML * minutesPerML))
    }
}
