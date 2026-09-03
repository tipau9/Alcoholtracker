package de.tipau.promille.bac

import kotlin.math.max

// App-level domain types. The BAC engine itself speaks a minute-offset timeline
// (DrinkInput / MealInput) because that is what the golden vectors pin. These
// types carry real epoch timestamps and convert into that timeline, so screens
// never have to do offset arithmetic by hand.

/** Mirrors Models/Drink.swift. Times are epoch seconds, matching Supabase. */
data class Drink(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val volumeML: Double,
    val abv: Double,
    val calories: Int = 0,
    val iconName: String = "mug.fill",
    val category: DrinkCategory,
    val timestampEpochSeconds: Long = 0L,
    val templateId: String? = null,
    val mixerVolumeML: Double = 0.0,
    val mixerWaterContentPercent: Double = 0.0,
    /** 0 means "auto-estimate"; a positive value is an estimated or measured span. */
    val drinkDurationMinutes: Double = 0.0
) {
    val alcoholGrams: Double get() = volumeML * (abv / 100.0) * 0.789

    fun effectiveDrinkDurationMinutes(pace: DrinkPaceMemory): Double =
        if (drinkDurationMinutes > 0) drinkDurationMinutes
        else DrinkDurationEstimator.estimate(category, volumeML, pace)

    fun estimatedFinishedAtEpochSeconds(pace: DrinkPaceMemory): Long =
        timestampEpochSeconds + (effectiveDrinkDurationMinutes(pace) * 60).toLong()

    /** Mirrors Drink.finish(at:): a manual finish never records less than a minute. */
    fun finished(atEpochSeconds: Long): Drink =
        copy(drinkDurationMinutes = max(1.0, (atEpochSeconds - timestampEpochSeconds) / 60.0))
}

/** Mirrors MealEventValue in Models/SessionEvents.swift. */
data class MealEvent(
    val id: String,
    val timestampEpochSeconds: Long,
    val impact: MealImpact,
    val name: String = ""
)

/**
 * Everything a projection needs, in one bundle. Mirrors BACProjectionInput so
 * Home, Safety, the widget and the notification cannot disagree about stomach
 * mode, conservative mode or vomit events.
 */
data class BacProjectionInput(
    val drinks: List<Drink>,
    val profile: Profile,
    val stomachStatus: StomachStatus,
    val conservative: Boolean,
    val vomitEpochSeconds: List<Long> = emptyList(),
    val meals: List<MealEvent> = emptyList(),
    val pace: DrinkPaceMemory = DrinkPaceMemory.disabled()
) {
    /**
     * Origin of the engine timeline: the first drink. Everything else is a minute
     * offset from here, which keeps the engine identical to the vector-pinned form.
     */
    private val originEpochSeconds: Long
        get() = drinks.minOfOrNull { it.timestampEpochSeconds } ?: 0L

    private fun minutesFromOrigin(epochSeconds: Long): Double =
        (epochSeconds - originEpochSeconds) / 60.0

    private fun engineDrinks(): List<DrinkInput> = drinks.map {
        DrinkInput(
            offsetMinutes = minutesFromOrigin(it.timestampEpochSeconds),
            volumeML = it.volumeML,
            abv = it.abv,
            category = it.category,
            drinkDurationMinutes = it.effectiveDrinkDurationMinutes(pace)
        )
    }

    private fun engineMeals(): List<MealInput> = meals.map {
        MealInput(minutesFromOrigin(it.timestampEpochSeconds), it.impact)
    }

    private fun engineVomits(): List<Double> = vomitEpochSeconds.map { minutesFromOrigin(it) }

    fun currentBac(atEpochSeconds: Long): Double {
        if (drinks.isEmpty()) return 0.0
        return BacCalculator.currentBac(
            drinks = engineDrinks(),
            profile = profile,
            atMinute = minutesFromOrigin(atEpochSeconds),
            stomach = stomachStatus,
            conservative = conservative,
            vomitMinutes = engineVomits(),
            meals = engineMeals()
        )
    }

    /** Null when the target is never reached inside the search horizon. */
    fun hoursUntil(targetBac: Double, fromEpochSeconds: Long): Double? {
        if (drinks.isEmpty()) return null
        return BacCalculator.hoursUntilBac(
            targetBac = targetBac,
            drinks = engineDrinks(),
            profile = profile,
            fromMinute = minutesFromOrigin(fromEpochSeconds),
            stomach = stomachStatus,
            conservative = conservative,
            vomitMinutes = engineVomits(),
            meals = engineMeals()
        )
    }

    fun peakBac(intervalMinutes: Double = 10.0): Double {
        if (drinks.isEmpty()) return 0.0
        return BacCalculator.peakBac(
            drinks = engineDrinks(),
            profile = profile,
            intervalMinutes = intervalMinutes,
            stomach = stomachStatus,
            conservative = conservative,
            vomitMinutes = engineVomits(),
            meals = engineMeals()
        )
    }

    /** Curve points carry absolute epoch seconds so a chart can plot them directly. */
    fun curve(fromEpochSeconds: Long, hours: Double, intervalMinutes: Double): List<CurvePoint> {
        if (drinks.isEmpty()) {
            val stepSeconds = (intervalMinutes * 60).toLong().coerceAtLeast(60L)
            val totalSeconds = (hours * 3600).toLong()
            val points = mutableListOf<CurvePoint>()
            var t = fromEpochSeconds
            val end = fromEpochSeconds + totalSeconds
            while (t <= end) {
                points.add(CurvePoint(t, 0.0))
                t += stepSeconds
            }
            return points
        }
        val start = minutesFromOrigin(fromEpochSeconds)
        return BacCalculator.bacCurve(
            drinks = engineDrinks(),
            profile = profile,
            fromMinute = start,
            hours = hours,
            intervalMinutes = intervalMinutes,
            stomach = stomachStatus,
            conservative = conservative,
            vomitMinutes = engineVomits(),
            meals = engineMeals()
        ).map { (minute, bac) -> CurvePoint(originEpochSeconds + (minute * 60).toLong(), bac) }
    }

    /**
     * Cheap identity for cached projections. Mirrors BACProjectionInput.stableKey,
     * including the resolved drink duration so a learned-pace update invalidates
     * cached results for drinks stored with duration 0.
     */
    val stableKey: String
        get() = buildString {
            drinks.forEach {
                append(it.id).append(':')
                append(it.timestampEpochSeconds).append(':')
                append(it.volumeML).append(':')
                append(it.abv).append(':')
                append(it.effectiveDrinkDurationMinutes(pace)).append(':')
                append(it.category.raw).append('|')
            }
            append('#')
            vomitEpochSeconds.forEach { append(it).append('|') }
            append('#')
            meals.forEach {
                append(it.id).append(':').append(it.timestampEpochSeconds).append(':')
                append(it.impact.raw).append(':').append(it.name).append('|')
            }
            append('#').append(profile.bacProjectionKey)
            append('#').append(stomachStatus.raw)
            append('#').append(conservative)
        }
}

data class CurvePoint(val epochSeconds: Long, val bac: Double)

/** Mirrors DrinkTimingModel: drinking time and absorption time are separate spans. */
data class DrinkTiming(
    val drinkingStartedAt: Long,
    val drinkingFinishedAt: Long,
    val absorptionWindowMinutes: Double,
    val absorptionFinishedAt: Long
) {
    companion object {
        fun of(drink: Drink, stomach: StomachStatus, pace: DrinkPaceMemory): DrinkTiming {
            // Conservative projections keep the physical absorption duration; only
            // bioavailability and elimination assumptions become more cautious.
            val window = BacCalculator.absorptionWindowMinutes(
                category = drink.category,
                drinkDurationMinutes = drink.effectiveDrinkDurationMinutes(pace),
                gastricMinutes = stomach.absorptionMinutes
            )
            return DrinkTiming(
                drinkingStartedAt = drink.timestampEpochSeconds,
                drinkingFinishedAt = drink.estimatedFinishedAtEpochSeconds(pace),
                absorptionWindowMinutes = window,
                absorptionFinishedAt = drink.timestampEpochSeconds + (window * 60).toLong()
            )
        }
    }
}
