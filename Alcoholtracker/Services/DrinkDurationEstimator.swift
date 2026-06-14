import Foundation

// MARK: - DrinkDurationEstimator
// Estimates how many minutes it takes to drink a given beverage.
// Used by BACCalculator to spread absorption over the actual drinking window
// instead of modelling all alcohol as a single instantaneous bolus.

enum DrinkDurationEstimator {

    static func estimate(category: DrinkCategory, volumeML: Double) -> Double {
        let minutesPerML: Double
        switch category {
        case .shot:
            return 1
        case .spirits:
            minutesPerML = 0.50  // sipped neat, slow
        case .liqueur:
            minutesPerML = 0.40
        case .beer, .cider:
            minutesPerML = 0.06  // 500 ml ~ 30 min
        case .wine, .sparkling:
            minutesPerML = 0.12  // 200 ml ~ 24 min
        case .cocktail:
            minutesPerML = 0.10
        case .mixed:
            minutesPerML = 0.08
        case .fortified:
            minutesPerML = 0.15  // sherry / port, sipped slowly
        case .other:
            minutesPerML = 0.08
        }
        return max(1, min(120, volumeML * minutesPerML))
    }
}
