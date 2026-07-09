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
        // A peak this high is a medical emergency, not a hangover. 3‰ is very
        // dangerous, but the explicit "Lebensgefahr" alarm is reserved for the
        // range where coma/respiratory failure become a realistic acute concern.
        if peakBAC >= 4.0 { return .lethal }

        var score: Double = 0
        score += peakBAC * 2.0
        score += durationHours * 0.10
        score += Double(drinksCount) * 0.08

        // Hydration matters, but should not erase a high BAC completely. Cap the
        // benefit around 1.5 glasses per alcoholic drink so "good hydration"
        // noticeably lowers the forecast without making heavy sessions harmless.
        let usefulWater = min(waterGlasses, max(2.0, Double(drinksCount) * 1.5))
        score -= usefulWater * 0.35

        // Calibrated so that 1.5-2.0‰ is usually a hard/severe hangover signal,
        // not a medical death warning, and water can move it down by a tier.
        switch score {
        case ..<1.2:       return .none
        case 1.2..<2.0:    return .mild
        case 2.0..<3.0:    return .moderate
        case 3.0..<4.4:    return .strong
        default:           return .severe
        }
    }

    // Convenience variant. waterGlasses: real logged glasses from WaterLog;
    // nil falls back to the estimation heuristic (one glass per two drinks).
    static func predict(
        drinks: [Drink],
        profile: UserProfile,
        waterGlasses: Double? = nil,
        stomachStatus: StomachStatus? = nil,
        conservative: Bool? = nil,
        vomitTimes: [Date] = []
    ) -> HangoverLevel {
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
            stomachStatus: stomachStatus ?? profile.defaultStomachStatus,
            conservative: conservative ?? profile.conservativeForApp,
            vomitTimes: vomitTimes
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
