package de.tipau.promille.ui.viewmodels

import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.DrinkCategory
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthTrendTest {

    private fun drinks(count: Int): List<Drink> = List(count) {
        Drink(name = "Bier", volumeML = 500.0, abv = 5.0, category = DrinkCategory.BEER)
    }

    @Test
    fun `running month compares only the elapsed days`() {
        val today = LocalDate.of(2026, 3, 10)
        val days = mapOf(
            LocalDate.of(2026, 2, 5) to drinks(3),
            LocalDate.of(2026, 2, 9) to drinks(2),
            // Past the 10th, so outside the comparison window.
            LocalDate.of(2026, 2, 20) to drinks(7)
        )
        val trend = previousMonthTrend(days, YearMonth.of(2026, 3), today)
        assertEquals(MonthTrend(previousTotalDrinks = 5, limitedToDays = 10), trend)
    }

    @Test
    fun `past month compares the full month`() {
        val today = LocalDate.of(2026, 3, 10)
        val days = mapOf(
            LocalDate.of(2026, 1, 5) to drinks(3),
            LocalDate.of(2026, 1, 28) to drinks(4),
            LocalDate.of(2026, 2, 20) to drinks(7)
        )
        val trend = previousMonthTrend(days, YearMonth.of(2026, 2), today)
        assertEquals(MonthTrend(previousTotalDrinks = 7, limitedToDays = null), trend)
    }

    @Test
    fun `no previous drinks means no trend`() {
        val days = mapOf(LocalDate.of(2026, 3, 2) to drinks(4))
        assertNull(previousMonthTrend(days, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 10)))
    }
}
