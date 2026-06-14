import Foundation

// MARK: - AlcoholKinetics
// Michaelis-Menten (mixed-order) elimination model.
// Holford NH. "Clinical pharmacokinetics of ethanol." Clin Pharmacokinet. 1987;13(5):273-92.
//
// Used by the Live Activity service and App Intents to extrapolate a single
// snapshot BAC forward in time. The in-app BAC engine (BACCalculator) integrates
// the full drink history with zero-order elimination; this is the closed-form
// tail model for when only a peak/current value is known.

// nonisolated: pure math, called from AppIntents outside the main actor.
nonisolated enum AlcoholKinetics {
    // BAC threshold below which first-order kinetics dominate (g/100 mL).
    static let km: Double = 0.10

    // BAC at time t hours after peak, using mixed-order kinetics.
    static func bacAtTime(peakBAC: Double, hoursSincePeak: Double, beta: Double) -> Double {
        guard peakBAC > 0, hoursSincePeak >= 0, beta > 0 else { return 0 }

        let timeToKm = max(0, (peakBAC - km) / beta)

        if hoursSincePeak <= timeToKm {
            return max(0, peakBAC - beta * hoursSincePeak)
        } else {
            let timeInFirstOrder = hoursSincePeak - timeToKm
            let k = beta / km
            return max(0, km * exp(-k * timeInFirstOrder))
        }
    }

    // Hours from peak until BAC drops to threshold.
    static func hoursUntilThreshold(peakBAC: Double, threshold: Double, beta: Double) -> Double {
        guard peakBAC > threshold, beta > 0 else { return 0 }
        let timeToKm = max(0, (peakBAC - km) / beta)

        if threshold >= km {
            return (peakBAC - threshold) / beta
        } else {
            let k = beta / km
            return timeToKm + log(km / max(threshold, 0.001)) / k
        }
    }
}
