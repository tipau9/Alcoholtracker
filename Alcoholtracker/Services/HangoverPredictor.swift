import Foundation
import SwiftUI

// MARK: - HangoverLevel

enum HangoverLevel {
    case none
    case mild
    case moderate
    case strong
    case severe
    case lethal   // medically dangerous peak BAC, not just a hangover

    var label: String {
        switch self {
        case .none:     return "Kein Kater erwartet"
        case .mild:     return "Leichtes Unbehagen möglich"
        case .moderate: return "Spürbarer Kater morgen"
        case .strong:   return "Harter Tag morgen"
        case .severe:   return "Sehr schwerer Kater morgen"
        case .lethal:   return "Lebensgefahr – tödlicher Bereich"
        }
    }

    var symbolName: String {
        switch self {
        case .none:     return "checkmark.circle.fill"
        case .mild:     return "circle.dotted"
        case .moderate: return "exclamationmark.circle"
        case .strong:   return "exclamationmark.triangle"
        case .severe:   return "xmark.octagon.fill"
        case .lethal:   return "cross.case.fill"
        }
    }

    // Tint for the hangover card/widget. severe/lethal must read as a real alarm,
    // not the generic orange used for milder levels.
    var color: Color {
        switch self {
        case .none:     return .statusGreen
        case .mild:     return .statusYellow
        case .moderate: return .statusOrange
        case .strong:   return .statusRed
        case .severe:   return .statusRed
        case .lethal:   return .statusDarkRed
        }
    }

    var isPositive: Bool { self == .none }
    // Worst tier: a genuine medical danger, shown with an extra warning line.
    var isLethal: Bool { self == .lethal }
}

// MARK: - HangoverPredictor

enum HangoverPredictor {

    // peakBAC: highest promille value reached during the session
    // durationHours: hours from first to last drink
    // waterGlasses: number of water glasses drunk (estimated from drinks count heuristic if unknown)
    // drinksCount: total drinks consumed
    static func predict(
        peakBAC: Double,
        durationHours: Double,
        waterGlasses: Double,
        drinksCount: Int
    ) -> HangoverLevel {
        // A peak this high is a medical emergency, not a hangover. ~3‰ already
        // risks coma; ~4‰ and above is frequently fatal. Flag it directly,
        // independent of the hangover score.
        if peakBAC >= 3.0 { return .lethal }

        var score: Double = 0
        score += peakBAC * 3.0
        score += durationHours * 0.15
        score -= waterGlasses * 0.2
        score += Double(drinksCount) * 0.05

        // Calibrated so that a short session peaking at ~0.5‰ yields at most .mild.
        // Old thresholds were too aggressive (0.5‰ × 3.0 = 1.5 → .strong directly).
        switch score {
        case ..<1.5:       return .none
        case 1.5..<2.5:    return .mild
        case 2.5..<3.5:    return .moderate
        case 3.5..<5.0:    return .strong
        default:           return .severe
        }
    }

    // Convenience variant. waterGlasses: real logged glasses from WaterLog;
    // nil falls back to the estimation heuristic (one glass per two drinks).
    static func predict(drinks: [Drink], profile: UserProfile, waterGlasses: Double? = nil) -> HangoverLevel {
        // Only alcoholic drinks drive a hangover: a Cola or water logged late
        // must not stretch the session duration or inflate the drink count (and
        // thus the severity). Without this, a fixed drinking day could read
        // worse than its actual alcohol warrants.
        let alcoholic = drinks.filter { $0.abv > 0 }.sorted { $0.timestamp < $1.timestamp }
        guard let firstDrink = alcoholic.first?.timestamp,
              let lastDrink = alcoholic.last?.timestamp else { return .none }

        let duration = lastDrink.timeIntervalSince(firstDrink) / 3600

        // Tatsächlichen Spitzen-BAC durch Abtasten der Kurve (inkl. Abbau) ermitteln
        let curve = BACCalculator.bacCurve(
            drinks: alcoholic,
            profile: profile,
            from: firstDrink,
            hours: duration + 6.0,
            intervalMinutes: 15,
            stomachStatus: profile.defaultStomachStatus
        )
        let peakBAC = curve.map { $0.bac }.max() ?? 0.0

        // Real logged water when available; otherwise rough heuristic
        // (one glass of water per two alcoholic drinks).
        let water = waterGlasses ?? Double(alcoholic.count) / 2.0

        return predict(
            peakBAC: peakBAC,
            durationHours: duration,
            waterGlasses: water,
            drinksCount: alcoholic.count
        )
    }
}
