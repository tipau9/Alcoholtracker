import Foundation
import SwiftUI

// MARK: - HangoverLevel

enum HangoverLevel: Int, Comparable {
    case none
    case mild
    case moderate
    case strong
    case severe
    case lethal   // medically dangerous peak BAC, not just a hangover

    // Ascending severity order (declared low to high) drives the peak-BAC floor
    // in the predictor, so `max(level, .moderate)` reads as "at least moderate".
    static func < (lhs: HangoverLevel, rhs: HangoverLevel) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

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
        // A peak this high is a medical emergency, not a hangover. Respiratory
        // depression and aspiration become a realistic acute risk from ~3‰ upward
        // (earlier in less tolerant drinkers), so the explicit "Lebensgefahr" alarm
        // starts there rather than only at 4‰, which understated the 3-4‰ range.
        if peakBAC >= 3.0 { return .lethal }

        var score: Double = 0
        score += peakBAC * 2.0
        score += durationHours * 0.10
        score += Double(drinksCount) * 0.08

        // Hydration eases symptoms but must not erase a high BAC. Cap the benefit
        // both per amount (~1.5 glasses per alcoholic drink) AND in total (1.5
        // score points), so "good hydration" lowers the forecast by roughly a tier
        // without ever making a heavy night read as harmless.
        let usefulWater = min(waterGlasses, max(2.0, Double(drinksCount) * 1.5))
        score -= min(usefulWater * 0.35, 1.5)

        // Calibrated so that 1.5-2.0‰ is usually a hard/severe hangover signal,
        // not a medical death warning, and water can move it down by a tier.
        let scored: HangoverLevel
        switch score {
        case ..<1.2:       scored = .none
        case 1.2..<2.0:    scored = .mild
        case 2.0..<3.0:    scored = .moderate
        case 3.0..<4.4:    scored = .strong
        default:           scored = .severe
        }

        // Floor by the raw peak: a genuinely high blood-alcohol peak is toxic and
        // dehydrating on its own, so neither water nor a short session may downgrade
        // it below a matching minimum tier.
        if peakBAC >= 2.0 { return max(scored, .moderate) }
        if peakBAC >= 1.2 { return max(scored, .mild) }
        return scored
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
        let curve = BACProjectionInput(
            drinks: alcoholic,
            profile: profile,
            stomachStatus: stomachStatus ?? profile.defaultStomachStatus,
            // Danger classification should use the same worst-case peak the Safety
            // tab shows, not the app-wide realistic one, so the Kater/Lebensgefahr
            // tier is never systematically below what the user sees under Sicherheit.
            conservative: conservative ?? profile.conservativeForSafety,
            vomitTimes: vomitTimes
        ).curve(from: firstDrink, hours: duration + 6.0, intervalMinutes: 15)
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
