package de.tipau.promille.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.LogicalDay
import de.tipau.promille.repository.DrinkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

data class DayStats(
    val date: LocalDate,
    val drinks: List<Drink>,
) {
    val drinkCount: Int get() = drinks.size
    val hadAlcohol: Boolean get() = drinks.any { it.abv > 0.01 }
    val totalCalories: Int get() = drinks.sumOf { it.calories }
    val totalAlcoholGrams: Double get() = drinks.sumOf { it.alcoholGrams }
}

data class MonthStats(
    val drinkDays: Int,
    val totalDrinks: Int,
    val totalCalories: Int,
    val days: Map<LocalDate, DayStats>
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val drinkRepository: DrinkRepository
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val visibleMonth = MutableStateFlow(YearMonth.now())

    val monthStats: StateFlow<MonthStats> = visibleMonth.flatMapLatest { month ->
        val windowStart = month.minusMonths(1).atDay(1)
            .atStartOfDay(zone).toEpochSecond()
        val windowEnd = month.plusMonths(2).atDay(1)
            .atStartOfDay(zone).toEpochSecond()

        drinkRepository.getDrinksForHistory(windowStart, windowEnd).map { entities ->
            val drinks = entities.map { DrinkRepository.toDomainDrink(it) }
            val grouped = drinks.groupBy { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }

            // Filter to the visible month
            val monthDays = grouped.filter { (date, _) ->
                YearMonth.from(date) == month
            }

            val dayStatsMap = monthDays.mapValues { (date, dayDrinks) ->
                DayStats(date = date, drinks = dayDrinks)
            }

            MonthStats(
                drinkDays = dayStatsMap.count { it.value.hadAlcohol },
                totalDrinks = dayStatsMap.values.sumOf { it.drinkCount },
                totalCalories = dayStatsMap.values.sumOf { it.totalCalories },
                days = dayStatsMap
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthStats(0, 0, 0, emptyMap()))

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }
}
