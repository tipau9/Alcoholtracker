package de.tipau.promille.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.bac.*
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DayStats(
    val date: LocalDate,
    val drinks: List<Drink>,
) {
    val drinkCount: Int get() = drinks.size
    val hadAlcohol: Boolean get() = drinks.any { it.abv > 0.01 }
    val totalCalories: Int get() = drinks.sumOf { it.calories }
    val totalAlcoholGrams: Double get() = drinks.sumOf { it.alcoholGrams }

    fun peakBAC(profile: Profile? = null): Double {
        if (drinks.isEmpty()) return 0.0
        val p = profile ?: Profile(weightKg = 75.0, heightCm = 175.0, age = 25, gender = Gender.MALE)
        val input = BacProjectionInput(
            drinks = drinks,
            profile = p,
            stomachStatus = p.defaultStomachStatus,
            conservative = p.conservativeForApp
        )
        val timestamps = drinks.map { it.timestampEpochSeconds }
        val minT = timestamps.minOrNull() ?: 0L
        val maxT = (timestamps.maxOrNull() ?: 0L) + 8 * 3600
        var peak = 0.0
        var t = minT
        while (t <= maxT) {
            val b = input.currentBac(t)
            if (b > peak) peak = b
            t += 600
        }
        return peak
    }

    fun bacStatus(profile: Profile? = null): BacStatus {
        val peak = peakBAC(profile)
        return BacStatus.of(peak, profile)
    }
}

data class MonthStats(
    val drinkDays: Int,
    val totalDrinks: Int,
    val totalCalories: Int,
    val days: Map<LocalDate, DayStats>,
    val trend: MonthTrend? = null
)

data class MonthTrend(
    val previousTotalDrinks: Int,
    // Set while the visible month is still running: comparing a half month
    // against a full one would always read "weniger als im Vormonat".
    val limitedToDays: Int?
)

// iOS HistoryViewModel.previousMonthComparison. Keys are logical days already,
// so a 02:00 drink counts towards the night before.
internal fun previousMonthTrend(
    daysByDate: Map<LocalDate, List<Drink>>,
    month: YearMonth,
    today: LocalDate
): MonthTrend? {
    val limit = if (month == YearMonth.from(today)) today.dayOfMonth else null
    val previous = month.minusMonths(1)
    val total = daysByDate
        .filter { (date, _) ->
            YearMonth.from(date) == previous && (limit == null || date.dayOfMonth <= limit)
        }
        .values.sumOf { it.size }
    return if (total > 0) MonthTrend(total, limit) else null
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val drinkRepository: DrinkRepository,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val visibleMonth = MutableStateFlow(YearMonth.now())

    val profileEntity: StateFlow<UserProfileEntity?> = (userProfileRepository?.profile ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val statusSkin: StateFlow<StatusSkin> = profileEntity.map {
        StatusSkin.from(it?.statusSkinRaw ?: "standard")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusSkin.STANDARD)

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
                days = dayStatsMap,
                // The window already spans the previous month, so no second query.
                trend = previousMonthTrend(grouped, month, LocalDate.now())
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthStats(0, 0, 0, emptyMap()))

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun goToCurrentMonth() {
        visibleMonth.value = YearMonth.now()
    }

    fun deleteDrink(drink: de.tipau.promille.bac.Drink) {
        viewModelScope.launch {
            drinkRepository.deleteDrink(
                de.tipau.promille.data.DrinkEntity(
                    id = drink.id,
                    name = drink.name,
                    volume = drink.volumeML,
                    abv = drink.abv,
                    calories = drink.calories,
                    iconName = drink.iconName,
                    categoryRaw = drink.category.name.lowercase(),
                    timestampEpochSeconds = drink.timestampEpochSeconds,
                    templateID = drink.templateId,
                    mixerVolume = drink.mixerVolumeML,
                    mixerWaterContent = drink.mixerWaterContentPercent
                )
            )
        }
    }
}

