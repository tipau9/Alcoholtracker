package de.tipau.promille.bac

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers weeklyDrinkCounts/categoryTrends, ported from HistoryViewModel.swift for TrendsView. */
class PersonalInsightsTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun epoch(y: Int, m: Int, d: Int, h: Int = 20): Long =
        LocalDateTime.of(y, m, d, h, 0).atZone(berlin).toEpochSecond()

    private fun beer(at: Long) = Drink(
        id = "d-$at", name = "Bier", volumeML = 500.0, abv = 5.0, calories = 210,
        iconName = "mug.fill", category = DrinkCategory.BEER,
        timestampEpochSeconds = at, drinkDurationMinutes = 20.0
    )

    private fun wine(at: Long) = beer(at).copy(id = "w-$at", category = DrinkCategory.WINE)

    @Test
    fun `weeklyDrinkCounts buckets by Monday-start week, oldest first`() {
        // Tuesday June 9 2026: current week starts Mon June 8. Two drinks this
        // week, one in the previous week (June 1), one three weeks back.
        val now = epoch(2026, 6, 9, 12)
        val drinks = listOf(
            beer(epoch(2026, 6, 8)), beer(epoch(2026, 6, 9, 8)),
            beer(epoch(2026, 6, 1)),
            beer(epoch(2026, 5, 19))
        )
        val weeks = weeklyDrinkCounts(drinks, weeksBack = 4, nowEpochSeconds = now, zone = berlin)
        assertEquals(4, weeks.size)
        assertEquals(1, weeks[0].count, "oldest bucket (3 weeks back) should hold the May 19 drink")
        assertEquals(0, weeks[1].count)
        assertEquals(1, weeks[2].count, "previous week should hold the June 1 drink")
        assertEquals(2, weeks[3].count, "current week should hold both June 8/9 drinks")
    }

    @Test
    fun `categoryTrends counts alcoholic drinks within the window, sorted by count`() {
        val now = epoch(2026, 6, 20)
        val drinks = listOf(
            beer(now - 3600), beer(now - 7200), wine(now - 10800),
            // Outside the 7-day window.
            beer(now - 10 * 86400)
        )
        val trends = categoryTrends(drinks, days = 7, nowEpochSeconds = now)
        assertEquals(listOf(CategoryTrend(DrinkCategory.BEER.germanName, 2), CategoryTrend(DrinkCategory.WINE.germanName, 1)), trends)
    }
}
