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

    // MARK: Per-drink

    static func waterIn(drink: Drink) -> Double {
        drink.volume * (1.0 - drink.abv / 100.0)
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
