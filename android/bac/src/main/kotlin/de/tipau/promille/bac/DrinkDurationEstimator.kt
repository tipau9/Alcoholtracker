package de.tipau.promille.bac

import kotlin.math.max
import kotlin.math.min

// How many minutes a drink takes to consume, by beverage type and volume. The
// BAC engine uses this as the drinking window when the user has not entered a
// custom estimate. Mirrors Services/DrinkDurationEstimator.swift.
object DrinkDurationEstimator {

    private const val MAX_MINUTES = 180.0

    fun estimate(category: DrinkCategory, volumeML: Double, pace: DrinkPaceMemory): Double =
        pace.adjustedEstimate(category, baseEstimate(category, volumeML))

    fun baseEstimate(category: DrinkCategory, volumeML: Double): Double {
        // Shots scale by count, not by a per-ml rate: a large logged amount is
        // several shots and must not keep the one-minute estimate of a single one.
        if (category == DrinkCategory.SHOT) return max(1.0, min(MAX_MINUTES, volumeML / 40.0))

        val minutesPerML = when (category) {
            DrinkCategory.SPIRITS, DrinkCategory.LIQUEUR -> 0.04
            DrinkCategory.BEER, DrinkCategory.CIDER -> 0.04      // 500 ml ~ 20 min
            DrinkCategory.WINE, DrinkCategory.SPARKLING -> 0.05  // 200 ml ~ 10 min
            DrinkCategory.COCKTAIL -> 0.05
            DrinkCategory.MIXED -> 0.045
            DrinkCategory.FORTIFIED -> 0.06
            DrinkCategory.WATER, DrinkCategory.SOFT_DRINK, DrinkCategory.JUICE,
            DrinkCategory.COFFEE_TEA, DrinkCategory.MILK -> 0.02
            else -> 0.04
        }
        return max(1.0, min(MAX_MINUTES, volumeML * minutesPerML))
    }
}
