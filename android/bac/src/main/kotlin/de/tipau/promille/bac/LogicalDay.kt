package de.tipau.promille.bac

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// The app day starts at 06:00, not midnight: drinks logged between 00:00 and
// 05:59 belong to the previous evening. Single source of truth for session,
// history, achievements, safety and hydration. Mirrors CalendarLogicalDay.swift.
object LogicalDay {

    const val START_HOUR: Int = 6

    /** The calendar date that owns this timestamp. 01:30 on June 11 returns June 10. */
    fun dateOf(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val local = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return if (local.hour < START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** 06:00 of the logical day that owns this timestamp, as epoch seconds. */
    fun startOf(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateOf(epochSeconds, zone).atTime(LocalTime.of(START_HOUR, 0)).atZone(zone).toEpochSecond()

    fun sameLogicalDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        dateOf(a, zone) == dateOf(b, zone)

    /**
     * Where the "today" session actually starts. Normally 06:00, but extended
     * back to the first drink of an unbroken pre-06:00 block as long as that
     * block's residual BAC is still present NOW - not just at 06:00, or a
     * sober afternoon would keep showing the whole night. Mirrors
     * SessionViewModel.swift:528-613 (loadTodaysDrinks).
     */
    fun sessionStart(
        drinks: List<Drink>,
        profile: Profile,
        stomachStatus: StomachStatus,
        conservative: Boolean,
        vomitEpochSeconds: List<Long>,
        meals: List<MealEvent>,
        nowEpochSeconds: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val logicalStart = startOf(nowEpochSeconds, zone)
        val drinksBefore = drinks.filter { it.timestampEpochSeconds <= logicalStart }
        if (drinksBefore.isEmpty()) return logicalStart
        val vomitsBefore = vomitEpochSeconds.filter { it <= logicalStart }
        val mealsBefore = meals.filter { it.timestampEpochSeconds <= logicalStart }

        fun bacAt(atEpoch: Long, upTo: List<Drink>, vomits: List<Long>, mealList: List<MealEvent>): Double {
            if (upTo.isEmpty()) return 0.0
            return BacProjectionInput(
                drinks = upTo,
                profile = profile,
                stomachStatus = stomachStatus,
                conservative = conservative,
                vomitEpochSeconds = vomits,
                meals = mealList
            ).currentBac(atEpoch)
        }

        val bacAt6 = bacAt(logicalStart, drinksBefore, vomitsBefore, mealsBefore)
        val residualNow = bacAt(nowEpochSeconds, drinksBefore, vomitsBefore, mealsBefore)
        if (bacAt6 <= 0.001 || residualNow <= 0.001) return logicalStart

        var blockStart = logicalStart
        for (i in drinksBefore.indices.reversed()) {
            val d = drinksBefore[i]
            blockStart = d.timestampEpochSeconds
            val beforeTime = d.timestampEpochSeconds - 60
            val pastDrinks = drinksBefore.subList(0, i)
            val vomitsBeforeDrink = vomitsBefore.filter { it <= beforeTime }
            val mealsBeforeDrink = mealsBefore.filter { it.timestampEpochSeconds <= beforeTime }
            val bacBefore = bacAt(beforeTime, pastDrinks, vomitsBeforeDrink, mealsBeforeDrink)
            if (bacBefore <= 0.001) break
        }
        return blockStart
    }
}
