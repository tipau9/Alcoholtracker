import Foundation
import SwiftUI

// MARK: - HydrationCalculator
//
// Estimates net hydration effect of a drinking session.
//
// Model: Each gram of alcohol inhibits ADH, causing ~10 ml of additional
// diuresis above baseline. The non-alcoholic fraction of the drink
// (volume * (1 - abv/100)) counts as water in. Mixed drinks with a
// high-water-content mixer (e.g. tonic water) benefit from better
// waterIn because their blended ABV is low.
//
// mixerVolume and mixerWaterContent on Drink are used for the
// per-drink mixer breakdown, not for altering the core math.
//
// DISCLAIMER: Estimates only. Individual physiology varies widely.

enum HydrationCalculator {

    // MARK: Constants

    static let diuresisPerAlcoholGram = 10.0  // ml extra urine per gram of alcohol

    // While alcohol is still suppressing antidiuretic hormone (ADH), a fraction of
    // any water you drink is passed straight through rather than retained. To
    // actually close a deficit you therefore have to drink MORE than the raw
    // shortfall: required intake = deficit / retentionWhileDrinking. ~0.80 reflects
    // the ~20% pass-through measured during active intoxication.
    static let retentionWhileDrinking = 0.80

    // MARK: Per-drink

    // ABV above which the liquid is a concentrated spirit: its small non-alcohol
    // fraction is not meaningfully hydrating, so a neat shot must not be credited
    // as water intake. Only an explicit mixer's water counts for such drinks;
    // diluted drinks (beer, wine, long drinks) keep the full non-alcohol volume.
    static let neatSpiritABV = 22.0

    static func waterIn(drink: Drink) -> Double {
        if drink.abv > neatSpiritABV {
            return mixerWaterContribution(drink: drink)
        }
        return drink.volume * (1.0 - drink.abv / 100.0)
    }

    static func diuresisLoss(drink: Drink) -> Double {
        drink.alcoholGrams * diuresisPerAlcoholGram
    }

    static func netHydration(drink: Drink) -> Double {
        waterIn(drink: drink) - diuresisLoss(drink: drink)
    }

    // Water provided specifically by the mixer (sub-component of waterIn).
    static func mixerWaterContribution(drink: Drink) -> Double {
        drink.mixerVolume * (drink.mixerWaterContent / 100.0)
    }

    // MARK: Session totals

    static func sessionWaterIn(drinks: [Drink]) -> Double {
        drinks.reduce(0) { $0 + waterIn(drink: $1) }
    }

    static func sessionDiuresisLoss(drinks: [Drink]) -> Double {
        drinks.reduce(0) { $0 + diuresisLoss(drink: $1) }
    }

    static func sessionNetHydration(drinks: [Drink]) -> Double {
        drinks.reduce(0) { $0 + netHydration(drink: $1) }
    }

    static func sessionMixerWaterContribution(drinks: [Drink]) -> Double {
        drinks.reduce(0) { $0 + mixerWaterContribution(drink: $1) }
    }

    // MARK: Recommendation

    // Extra water (ml) to drink to bring net hydration to zero.
    static func recommendedExtraWaterMl(drinks: [Drink]) -> Int {
        let net = sessionNetHydration(drinks: drinks)
        return max(0, Int((-net).rounded()))
    }

    static func recommendedWater(for drinks: [Drink]) -> Double {
        Double(recommendedExtraWaterMl(drinks: drinks))
    }

    static func recommendedGlasses(for drinks: [Drink], glassML: Double = 250) -> Int {
        let ml = recommendedWater(for: drinks)
        guard ml > 0 else { return 0 }
        return Int(ceil(ml / glassML))
    }

    static func hydrationStatus(for drinks: [Drink]) -> HydrationStatus {
        hydrationStatus(netML: sessionNetHydration(drinks: drinks))
    }

    // Status from an arbitrary net value, e.g. including logged water glasses.
    static func hydrationStatus(netML: Double) -> HydrationStatus {
        switch netML {
        case 0...:          return .ok
        case -150..<0:      return .needsLittle
        case -300 ..< -150: return .needsMore
        default:            return .needsLots
        }
    }

    // MARK: Exact, body-water-aware compensation

    // The water deficit as a fraction of the person's total body water. A given ml
    // shortfall dehydrates a small body more than a large one, so this is the
    // correct basis for severity instead of an absolute ml threshold.
    static func dehydrationFraction(netML: Double, profile: UserProfile) -> Double {
        let tbwML = max(profile.totalBodyWater * 1000.0, 1)
        return max(0, -netML) / tbwML
    }

    // TBW-relative status. Thresholds are in % of body water and are calibrated so
    // an average adult (~42 L TBW) lands on the same boundaries as the legacy
    // absolute thresholds (~150 / 300 ml), while a lighter person tips into a
    // warning sooner and a heavier one later.
    static func hydrationStatus(netML: Double, profile: UserProfile) -> HydrationStatus {
        if netML >= 0 { return .ok }
        switch dehydrationFraction(netML: netML, profile: profile) {
        case ..<0.0036: return .needsLittle   // < ~0,15 L for a 42 L body
        case ..<0.0072: return .needsMore      // < ~0,30 L
        default:        return .needsLots
        }
    }

    // Exact water (ml) needed to actually close the deficit, grossing the raw
    // shortfall up by the ADH pass-through (retentionWhileDrinking). This is what
    // the user should drink, and it is always >= the bare deficit.
    static func compensationWaterMl(netML: Double) -> Int {
        guard netML < 0 else { return 0 }
        return Int((-netML / retentionWhileDrinking).rounded())
    }

    static func compensationWaterMl(for drinks: [Drink], extraNetML: Double = 0) -> Int {
        compensationWaterMl(netML: sessionNetHydration(drinks: drinks) + extraNetML)
    }

    // Glasses to drink to close the deficit, based on the exact compensation above.
    static func compensationGlasses(for drinks: [Drink], extraNetML: Double = 0, glassML: Double = 250) -> Int {
        let ml = Double(compensationWaterMl(for: drinks, extraNetML: extraNetML))
        guard ml > 0 else { return 0 }
        return Int(ceil(ml / glassML))
    }

    // MARK: Weather-driven sweat loss (Wetter-Korrelation)

    // Extra sweat water loss (ml) on a warm night, on top of the alcohol diuresis.
    // Above a comfort temperature the body sheds roughly this much per °C per hour
    // spent out. Conservative so weather nudges the recommendation rather than
    // dominating it. Pass the session duration in hours.
    static let sweatMlPerDegreeHour = 12.0

    static func heatSweatLossMl(tempC: Double, hours: Double, comfortC: Double = 22) -> Double {
        let over = max(0, tempC - comfortC)
        let h    = max(0, hours)
        return over * h * sweatMlPerDegreeHour
    }
}

// MARK: - HydrationStatus

enum HydrationStatus {
    case ok
    case needsLittle
    case needsMore
    case needsLots

    var label: String {
        switch self {
        case .ok:          return "Gut hydriert"
        case .needsLittle: return "Glas Wasser?"
        case .needsMore:   return "Trink Wasser"
        case .needsLots:   return "Dringend trinken"
        }
    }

    var color: Color {
        switch self {
        case .ok:          return .statusGreen
        case .needsLittle: return .statusYellow
        case .needsMore:   return .statusOrange
        case .needsLots:   return .statusRed
        }
    }
}
