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
            // Sipped neat, but not absurdly slowly: at 0.50 a 200 ml pour implied a
            // 100 min drinking window, which (being longer than gastric emptying)
            // stretched absorption and subtracted ~0.25 permille of elimination,
            // pushing the shown peak well below reality. 0.35 keeps a realistic sip
            // pace while letting gastric emptying gate absorption for normal pours.
            minutesPerML = 0.35
        case .liqueur:
            minutesPerML = 0.30
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
