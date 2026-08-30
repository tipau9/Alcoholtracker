package de.tipau.promille.bac

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Port of the iOS `BACCalculator` (Alcoholtracker/Services/BACCalculator.swift).
 *
 * Widmark with a Watson-1980 distribution factor, a linear absorption ramp per
 * drink, and mixed-order elimination: zero-order down to [KM], first-order below
 * it. The whole session is integrated as ONE trajectory with a single beta on the
 * pooled total, not as per-drink curves that are summed.
 *
 * Every number this file produces is pinned by testdata/bac_vectors.json, which
 * is generated from the live Swift engine. Do not "improve" the math here without
 * regenerating those vectors, or the two apps will show different permille values.
 *
 * DISCLAIMER: Estimates only. No substitute for a certified breath test. Never
 * use to assess legal fitness to drive.
 */
object BacCalculator {

    /** Michaelis constant in permille. Kept in permille, not g/100 mL. */
    const val KM: Double = 0.10

    /** Below this a purely decaying curve snaps to true zero in finite time. */
    const val SOBER_FLOOR: Double = 0.005

    private const val ETHANOL_DENSITY = 0.789

    // MARK: - Single-drink helpers

    /**
     * Raw Widmark term for one drink: ignores the resorption deficit, the
     * absorption ramp and elimination, so it OVERSTATES what the body reaches.
     * Use [projectedPeak] for anything shown to the user.
     */
    fun bacContribution(
        volumeML: Double,
        abv: Double,
        weightKg: Double,
        distributionFactor: Double
    ): Double {
        val alcoholGrams = (volumeML * abv / 100.0) * ETHANOL_DENSITY
        return alcoholGrams / (max(weightKg, 30.0) * distributionFactor)
    }

    /**
     * Minutes over which a drink's alcohol enters the blood: the LONGER of the
     * gastric phase and the actual drinking duration, never their sum. Adding them
     * double-counts the spread and badly understates single-drink peaks.
     */
    fun absorptionWindowMinutes(
        category: DrinkCategory,
        drinkDurationMinutes: Double,
        gastricMinutes: Double
    ): Double {
        require(drinkDurationMinutes > 0) { "drinkDurationMinutes must be positive" }
        return max(1.0, max(drinkDurationMinutes, gastricMinutes * category.absorptionModifier))
    }

    /**
     * The realistic peak one drink reaches on its own: raw Widmark x resorption
     * deficit, minus the elimination that happens while it is being absorbed.
     * Closed form, because the single-drink curve rises linearly and peaks at the
     * end of its absorption window.
     */
    fun projectedPeak(
        drink: DrinkInput,
        profile: Profile,
        stomach: StomachStatus,
        conservative: Boolean = false
    ): Double {
        // Conservative mode drops the resorption deficit but keeps the physical
        // absorption window: treating absorption as instantaneous made live BAC
        // jump straight to the theoretical peak.
        val factor = if (conservative) 1.0 else stomach.peakFactor
        val rawPeak = bacContribution(
            volumeML = drink.volumeML,
            abv = drink.abv,
            weightKg = profile.validatedWeight,
            distributionFactor = profile.distributionFactor
        ) * factor
        val window = absorptionWindowMinutes(
            category = drink.category,
            drinkDurationMinutes = drink.drinkDurationMinutes,
            gastricMinutes = stomach.absorptionMinutes
        )
        val elimPerMin = profile.resolvedEliminationRate(conservative) / 60.0
        return max(0.0, rawPeak - elimPerMin * window)
    }

    // MARK: - Whole-session integration

    private class Segment(val start: Double, val end: Double, val amount: Double)

    private class Envelope(val segments: List<Segment>, val effEnd: Double)

    /**
     * Forward-integrates the session curve once and samples it at [sampleMinutes].
     *
     * All minute values (drinks, meals, vomits, samples) live on one caller-chosen
     * timeline; the engine rebases everything onto the first drink, so anything at
     * or before it reads 0.
     */
    fun sampledBac(
        drinks: List<DrinkInput>,
        profile: Profile,
        sampleMinutes: List<Double>,
        stomach: StomachStatus,
        conservative: Boolean = false,
        vomitMinutes: List<Double> = emptyList(),
        meals: List<MealInput> = emptyList()
    ): List<Double> {
        val n = sampleMinutes.size
        if (n == 0) return emptyList()
        val originMin = drinks.minOfOrNull { it.offsetMinutes } ?: return List(n) { 0.0 }

        val r = profile.distributionFactor
        val elimPerMin = profile.resolvedEliminationRate(conservative) / 60.0
        val factor = if (conservative) 1.0 else stomach.peakFactor
        val gastric = stomach.absorptionMinutes

        // A vomit expels alcohol still sitting in the stomach, so it truncates the
        // drink's absorption envelope. Alcohol already in the blood is untouched,
        // which is why the running bac is never reduced directly.
        val vomits = vomitMinutes.map { it - originMin }.sorted()
        val sortedMeals = meals.sortedBy { it.offsetMinutes }

        val envelopes = drinks.map { drink ->
            val start = drink.offsetMinutes - originMin
            val peak = bacContribution(
                volumeML = drink.volumeML,
                abv = drink.abv,
                weightKg = profile.validatedWeight,
                distributionFactor = r
            ) * factor
            var window = absorptionWindowMinutes(
                category = drink.category,
                drinkDurationMinutes = drink.drinkDurationMinutes,
                gastricMinutes = gastric
            )

            // A meal that predates the first sip and is still active affects the
            // full window.
            val initialMultiplier = sortedMeals
                .filter {
                    it.offsetMinutes <= drink.offsetMinutes &&
                        (drink.offsetMinutes - it.offsetMinutes) * 60.0 <= it.impact.activeDurationSeconds
                }
                .maxOfOrNull { it.impact.remainingAbsorptionMultiplier } ?: 1.0
            window *= initialMultiplier

            var segments = listOf(Segment(start, start + window, peak))
            for (meal in sortedMeals.filter { it.offsetMinutes > drink.offsetMinutes }) {
                val minute = meal.offsetMinutes - originMin
                val currentEnd = segments.maxOfOrNull { it.end } ?: continue
                if (minute >= currentEnd) continue

                val prefix = ArrayList<Segment>()
                for (segment in segments) {
                    if (segment.end <= minute) {
                        prefix.add(segment)
                    } else if (segment.start < minute) {
                        val fraction = (minute - segment.start) / max(segment.end - segment.start, 0.001)
                        prefix.add(
                            Segment(
                                start = segment.start,
                                end = minute,
                                amount = segment.amount * min(1.0, max(0.0, fraction))
                            )
                        )
                    }
                }
                val remaining = max(0.0, peak - prefix.sumOf { it.amount })
                val newEnd = minute +
                    max(1.0, currentEnd - minute) * meal.impact.remainingAbsorptionMultiplier
                if (remaining > 0) {
                    prefix.add(Segment(minute, newEnd, remaining))
                }
                segments = prefix
            }

            // Earliest vomit strictly inside the window cuts it short.
            var effEnd = segments.maxOfOrNull { it.end } ?: start
            for (tv in vomits) {
                if (tv > start && tv < effEnd) {
                    effEnd = tv
                    break
                }
            }
            Envelope(segments, effEnd)
        }

        // Alcohol entering the blood over [lo, hi], across all piecewise segments.
        fun absorbed(lo: Double, hi: Double): Double {
            if (hi <= lo) return 0.0
            var total = 0.0
            for (e in envelopes) {
                for (segment in e.segments) {
                    val l = max(lo, segment.start)
                    val h = min(hi, min(segment.end, e.effEnd))
                    if (h > l) {
                        total += segment.amount * (h - l) / max(segment.end - segment.start, 0.001)
                    }
                }
            }
            return total
        }

        // Mixed-order elimination: constant elimPerMin above KM, first-order below,
        // with the rate constant chosen so the two regimes meet continuously at KM.
        val firstOrderK = if (KM > 0) elimPerMin / KM else 0.0
        fun eliminate(c0: Double, dt: Double): Double {
            if (c0 <= 0) return 0.0
            if (dt <= 0) return c0
            return if (c0 >= KM) {
                val afterZero = c0 - elimPerMin * dt
                if (afterZero >= KM) {
                    afterZero
                } else {
                    val tToKm = (c0 - KM) / elimPerMin
                    KM * exp(-firstOrderK * (dt - tToKm))
                }
            } else {
                c0 * exp(-firstOrderK * dt)
            }
        }

        val targets = sampleMinutes
            .mapIndexed { idx, m -> idx to (m - originMin) }
            .sortedBy { it.second }

        val result = DoubleArray(n)
        var ti = 0
        while (ti < targets.size && targets[ti].second <= 0.0) {
            result[targets[ti].first] = 0.0
            ti++
        }

        val maxMinute = targets.lastOrNull()?.second ?: 0.0
        var bac = 0.0
        var t = 0.0
        while (ti < targets.size) {
            // Emit every sample inside the current whole minute via one partial
            // sub-step. Absorption is added before the step's elimination.
            while (ti < targets.size && targets[ti].second < t + 1.0) {
                val m = targets[ti].second
                val add = absorbed(t, m)
                var v = eliminate(bac + add, m - t)
                // Snap to zero only while nothing is absorbing, so a rising
                // sub-floor BAC is never killed mid-climb.
                if (add <= 0 && v < SOBER_FLOOR) v = 0.0
                result[targets[ti].first] = v
                ti++
            }
            if (ti >= targets.size || t >= maxMinute) break
            val add1 = absorbed(t, t + 1.0)
            var nb = eliminate(bac + add1, 1.0)
            if (add1 <= 0 && nb < SOBER_FLOOR) nb = 0.0
            bac = nb
            t += 1.0
        }
        return result.toList()
    }

    fun currentBac(
        drinks: List<DrinkInput>,
        profile: Profile,
        atMinute: Double,
        stomach: StomachStatus,
        conservative: Boolean = false,
        vomitMinutes: List<Double> = emptyList(),
        meals: List<MealInput> = emptyList()
    ): Double = sampledBac(
        drinks, profile, listOf(atMinute), stomach, conservative, vomitMinutes, meals
    ).firstOrNull() ?: 0.0

    /**
     * Hours from [fromMinute] until BAC reaches or drops below [targetBac].
     * 0.0 if already there, null if it does not happen inside [maxHours].
     *
     * Returns the LAST crossing: a later drink can create another rise after an
     * earlier dip, so the first crossing is unsafe for a sobriety forecast.
     */
    fun hoursUntilBac(
        targetBac: Double,
        drinks: List<DrinkInput>,
        profile: Profile,
        fromMinute: Double,
        stomach: StomachStatus,
        conservative: Boolean = false,
        vomitMinutes: List<Double> = emptyList(),
        meals: List<MealInput> = emptyList(),
        maxHours: Double = 72.0
    ): Double? {
        val stepMin = 2.0
        val steps = max(1, (max(0.0, maxHours) * 60.0 / stepMin).toInt())
        val minutes = (0..steps).map { fromMinute + it * stepMin }
        val bacs = sampledBac(drinks, profile, minutes, stomach, conservative, vomitMinutes, meals)
        if (bacs.isEmpty()) return null
        val lastAbove = bacs.indexOfLast { it > targetBac }
        if (lastAbove < 0) return 0.0
        if (lastAbove >= steps) return null
        val above = bacs[lastAbove]
        val below = bacs[lastAbove + 1]
        val frac = if (above > below) (above - targetBac) / (above - below) else 0.0
        return (lastAbove + frac) * stepMin / 60.0
    }

    /**
     * Highest BAC reached, sampled from the first drink until 6 hours after the
     * last one so a late peak is never cut off.
     */
    fun peakBac(
        drinks: List<DrinkInput>,
        profile: Profile,
        stomach: StomachStatus,
        conservative: Boolean = false,
        vomitMinutes: List<Double> = emptyList(),
        meals: List<MealInput> = emptyList(),
        intervalMinutes: Double = 10.0
    ): Double {
        val first = drinks.minOfOrNull { it.offsetMinutes } ?: return 0.0
        val last = drinks.maxOfOrNull { it.offsetMinutes } ?: return 0.0
        val hours = (last - first) / 60.0 + 6.0
        return bacCurve(
            drinks, profile, first, hours, intervalMinutes,
            stomach, conservative, vomitMinutes, meals
        ).maxOfOrNull { it.second } ?: 0.0
    }

    /** (minute, bac) pairs for charting. */
    fun bacCurve(
        drinks: List<DrinkInput>,
        profile: Profile,
        fromMinute: Double,
        hours: Double = 8.0,
        intervalMinutes: Double = 15.0,
        stomach: StomachStatus = StomachStatus.LIGHT,
        conservative: Boolean = false,
        vomitMinutes: List<Double> = emptyList(),
        meals: List<MealInput> = emptyList()
    ): List<Pair<Double, Double>> {
        val steps = ((hours * 60.0) / intervalMinutes).toInt()
        val minutes = (0..steps).map { fromMinute + it * intervalMinutes }
        val bacs = sampledBac(drinks, profile, minutes, stomach, conservative, vomitMinutes, meals)
        return minutes.zip(bacs)
    }

    /**
     * Closed-form tail from a single known peak, for extrapolating a snapshot
     * forward when the drink history is not at hand (widget, notification).
     */
    fun bacAtTime(peakBac: Double, hoursSincePeak: Double, beta: Double): Double {
        if (peakBac <= 0 || hoursSincePeak < 0 || beta <= 0) return 0.0
        val timeToKm = max(0.0, (peakBac - KM) / beta)
        return if (hoursSincePeak <= timeToKm) {
            max(0.0, peakBac - beta * hoursSincePeak)
        } else {
            max(0.0, KM * exp(-(beta / KM) * (hoursSincePeak - timeToKm)))
        }
    }

    fun hoursUntilThreshold(peakBac: Double, threshold: Double, beta: Double): Double {
        if (peakBac <= threshold || beta <= 0) return 0.0
        val timeToKm = max(0.0, (peakBac - KM) / beta)
        return if (threshold >= KM) {
            (peakBac - threshold) / beta
        } else {
            timeToKm + ln(KM / max(threshold, 0.001)) / (beta / KM)
        }
    }
}
