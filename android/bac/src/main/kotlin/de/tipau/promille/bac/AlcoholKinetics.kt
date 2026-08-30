package de.tipau.promille.bac

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Michaelis-Menten (mixed-order) elimination model.
 * Holford NH. "Clinical pharmacokinetics of ethanol." Clin Pharmacokinet. 1987;13(5):273-92.
 *
 * Used to extrapolate a single snapshot BAC forward in time. The in-app BAC engine
 * (BacCalculator) integrates the full drink history with zero-order elimination;
 * this is the closed-form tail model for when only a peak/current value is known.
 */
object AlcoholKinetics {
    /**
     * BAC threshold (in ‰, i.e. g per kg) below which elimination crosses from
     * zero-order into first-order kinetics. This is NOT g/100 mL: the whole engine
     * works in Promille, and 0.10 ‰ matches the physiological Michaelis constant
     * for ethanol (~0.08 to 0.18 ‰). Rescaling it to g/100 mL (which would make it
     * 1.0 ‰) would break every tail/sober time, so keep it in ‰.
     */
    const val KM: Double = 0.10

    /**
     * BAC at time t hours after peak, using mixed-order kinetics.
     */
    fun bacAtTime(peakBAC: Double, hoursSincePeak: Double, beta: Double): Double {
        if (peakBAC <= 0 || hoursSincePeak < 0 || beta <= 0) return 0.0

        val timeToKm = max(0.0, (peakBAC - KM) / beta)

        return if (hoursSincePeak <= timeToKm) {
            max(0.0, peakBAC - beta * hoursSincePeak)
        } else {
            val timeInFirstOrder = hoursSincePeak - timeToKm
            val k = beta / KM
            max(0.0, KM * exp(-k * timeInFirstOrder))
        }
    }

    /**
     * Hours from peak until BAC drops to threshold.
     */
    fun hoursUntilThreshold(peakBAC: Double, threshold: Double, beta: Double): Double {
        if (peakBAC <= threshold || beta <= 0) return 0.0
        val timeToKm = max(0.0, (peakBAC - KM) / beta)

        return if (threshold >= KM) {
            (peakBAC - threshold) / beta
        } else {
            val k = beta / KM
            timeToKm + ln(KM / max(threshold, 0.001)) / k
        }
    }
}
