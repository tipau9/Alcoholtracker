package de.tipau.promille.bac

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Net hydration effect of a drinking session. Mirrors HydrationCalculator.swift.
 *
 * Model: each gram of alcohol inhibits ADH, causing ~10 ml of diuresis above
 * baseline. The non-alcoholic fraction of the drink counts as water in, so a
 * mixed drink with a high-water mixer scores better than a neat pour.
 *
 * Estimates only. Individual physiology varies widely.
 */
object HydrationCalculator {

    /** ml of extra urine per gram of alcohol. */
    const val DIURESIS_PER_ALCOHOL_GRAM = 10.0

    /**
     * While alcohol still suppresses ADH, part of any water drunk passes straight
     * through. Closing a deficit therefore needs MORE than the raw shortfall:
     * required = deficit / retention. 0.80 reflects the ~20% pass-through measured
     * during active intoxication.
     */
    const val RETENTION_WHILE_DRINKING = 0.80

    /** Extra sweat loss per degree above comfort per hour out. */
    const val SWEAT_ML_PER_DEGREE_HOUR = 12.0

    fun waterIn(drink: Drink): Double = drink.volumeML * (1.0 - drink.abv / 100.0)

    fun diuresisLoss(drink: Drink): Double = drink.alcoholGrams * DIURESIS_PER_ALCOHOL_GRAM

    fun netHydration(drink: Drink): Double = waterIn(drink) - diuresisLoss(drink)

    /** Water provided specifically by the mixer, a sub-component of waterIn. */
    fun mixerWaterContribution(drink: Drink): Double =
        drink.mixerVolumeML * (drink.mixerWaterContentPercent / 100.0)

    fun sessionWaterIn(drinks: List<Drink>): Double = drinks.sumOf { waterIn(it) }

    fun sessionDiuresisLoss(drinks: List<Drink>): Double = drinks.sumOf { diuresisLoss(it) }

    fun sessionNetHydration(drinks: List<Drink>): Double = drinks.sumOf { netHydration(it) }

    fun sessionMixerWaterContribution(drinks: List<Drink>): Double =
        drinks.sumOf { mixerWaterContribution(it) }

    /** Extra water (ml) that would bring net hydration back to zero. */
    fun recommendedExtraWaterMl(drinks: List<Drink>): Int =
        max(0, (-sessionNetHydration(drinks)).roundToInt())

    fun recommendedGlasses(drinks: List<Drink>, glassML: Double = 250.0): Int {
        val ml = recommendedExtraWaterMl(drinks).toDouble()
        return if (ml <= 0) 0 else ceil(ml / glassML).toInt()
    }

    /**
     * The deficit as a fraction of total body water. A given ml shortfall
     * dehydrates a small body more than a large one, so this is the correct basis
     * for severity instead of an absolute ml threshold.
     */
    fun dehydrationFraction(netML: Double, profile: Profile): Double {
        val tbwML = max(profile.totalBodyWaterL * 1000.0, 1.0)
        return max(0.0, -netML) / tbwML
    }

    /** Legacy absolute thresholds, used where no profile is at hand. */
    fun status(netML: Double): HydrationStatus = when {
        netML >= 0 -> HydrationStatus.OK
        netML >= -150 -> HydrationStatus.NEEDS_LITTLE
        netML >= -300 -> HydrationStatus.NEEDS_MORE
        else -> HydrationStatus.NEEDS_LOTS
    }

    /**
     * TBW-relative status. Calibrated so an average adult (~42 L TBW) lands on the
     * same boundaries as the absolute thresholds, while a lighter person tips into
     * a warning sooner and a heavier one later.
     */
    fun status(netML: Double, profile: Profile): HydrationStatus {
        if (netML >= 0) return HydrationStatus.OK
        return when {
            dehydrationFraction(netML, profile) < 0.0036 -> HydrationStatus.NEEDS_LITTLE
            dehydrationFraction(netML, profile) < 0.0072 -> HydrationStatus.NEEDS_MORE
            else -> HydrationStatus.NEEDS_LOTS
        }
    }

    /**
     * Water (ml) that actually closes the deficit, grossing the raw shortfall up by
     * the ADH pass-through. Always at least the bare deficit.
     */
    fun compensationWaterMl(netML: Double): Int =
        if (netML >= 0) 0 else (-netML / RETENTION_WHILE_DRINKING).roundToInt()

    fun compensationWaterMl(drinks: List<Drink>, extraNetML: Double = 0.0): Int =
        compensationWaterMl(sessionNetHydration(drinks) + extraNetML)

    fun compensationGlasses(
        drinks: List<Drink>,
        extraNetML: Double = 0.0,
        glassML: Double = 250.0
    ): Int {
        val ml = compensationWaterMl(drinks, extraNetML).toDouble()
        return if (ml <= 0) 0 else ceil(ml / glassML).toInt()
    }

    fun dynamicWaterTargetMl(
        drinks: List<Drink>,
        profile: Profile? = null,
        extraSweatML: Double = 0.0,
        vomitCount: Int = 0
    ): Int {
        val alcoholGrams = drinks.filter { it.abv > 0 }.sumOf { it.alcoholGrams }
        val vomitLoss = vomitCount * 300.0
        val netTarget = compensationWaterMl(
            sessionNetHydration(drinks) - extraSweatML - vomitLoss
        )
        val bodyScale = profile?.let { min(1.25, max(0.85, it.weightKg / 75.0)) } ?: 1.0
        val supportTarget = (alcoholGrams * 4.0 * bodyScale + extraSweatML + vomitLoss).roundToInt()
        return max(netTarget, supportTarget)
    }

    /**
     * Extra sweat loss (ml) on a warm night, on top of the alcohol diuresis.
     * Deliberately conservative so weather nudges the recommendation rather than
     * dominating it.
     */
    fun heatSweatLossMl(tempC: Double, hours: Double, comfortC: Double = 22.0): Double =
        max(0.0, tempC - comfortC) * max(0.0, hours) * SWEAT_ML_PER_DEGREE_HOUR
}

enum class HydrationStatus(val germanLabel: String) {
    OK("Gut hydriert"),
    NEEDS_LITTLE("Glas Wasser?"),
    NEEDS_MORE("Trink Wasser"),
    NEEDS_LOTS("Dringend trinken")
}
