package de.tipau.promille.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.BacStatus
import de.tipau.promille.bac.*
import de.tipau.promille.data.*
import de.tipau.promille.repository.*
import de.tipau.promille.service.NotificationService
import de.tipau.promille.sync.BACPublisher
import de.tipau.promille.sync.JamService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

enum class BacTrend(val symbol: String, val text: String) {
    RISING("▲", "steigend"),
    STABLE("■", "stabil"),
    FALLING("▼", "fallend")
}

data class UndoAction(
    val label: String,
    val deletedDrink: DrinkEntity? = null,
    val addedDrink: DrinkEntity? = null
)

class SessionViewModel(
    private val drinkRepository: DrinkRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val bacPublisher: BACPublisher,
    private val jamService: JamService,
    private val applicationContext: Context? = null
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val pace = DrinkPaceMemory.disabled()

    init {
        // Automatically reschedule sobriety/drive notifications whenever projection or profile changes
        applicationContext?.let { ctx ->
            viewModelScope.launch {
                combine(projection, profileEntity) { proj, prof ->
                    if (proj != null && prof != null) {
                        NotificationService.reschedule(
                            context = ctx,
                            input = proj,
                            tipsyThreshold = prof.tipsyThreshold,
                            warningThreshold = prof.warningThreshold
                        )
                    } else {
                        NotificationService.cancelAll(ctx)
                    }
                }.collect()
            }
        }
    }

    // 48h rolling window
    private val lookbackSeconds: Long
        get() = System.currentTimeMillis() / 1000 - 48 * 3600

    val rawDrinks: StateFlow<List<DrinkEntity>> =
        drinkRepository.getSessionDrinks(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawVomits: StateFlow<List<VomitEventEntity>> =
        sessionEventRepository.getVomitEventsSince(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawMeals: StateFlow<List<MealEventEntity>> =
        sessionEventRepository.getMealEventsSince(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawBreathalyzer: StateFlow<List<BreathalyzerReadingEntity>> =
        sessionEventRepository.getBreathalyzerReadingsSince(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileEntity: StateFlow<UserProfileEntity?> =
        userProfileRepository.profile
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stomachStatus = MutableStateFlow(StomachStatus.LIGHT)

    val drinks: StateFlow<List<Drink>> = rawDrinks.map { entities ->
        entities.map { DrinkRepository.toDomainDrink(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Periodic recalculation ticker
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis() / 1000)
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis() / 1000)

    val projection: StateFlow<BacProjectionInput?> = combine(
        drinks, profileEntity, stomachStatus, rawVomits, rawMeals, ticker
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val drinkList = values[0] as List<Drink>
        val profile = values[1] as? UserProfileEntity ?: return@combine null
        val stomach = values[2] as StomachStatus
        @Suppress("UNCHECKED_CAST")
        val vomits = values[3] as List<VomitEventEntity>
        @Suppress("UNCHECKED_CAST")
        val meals = values[4] as List<MealEventEntity>

        val bacProfile = UserProfileRepository.toProfile(profile)
        BacProjectionInput(
            drinks = drinkList,
            profile = bacProfile,
            stomachStatus = stomach,
            conservative = bacProfile.conservativeForApp,
            vomitEpochSeconds = vomits.map { it.timestamp },
            meals = meals.map { SessionEventRepository.toDomainMealEvent(it) },
            pace = pace
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentBAC: StateFlow<Double> = projection.map { proj ->
        proj?.currentBac(System.currentTimeMillis() / 1000) ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val bacStatus: StateFlow<BacStatus> = currentBAC.map { bac ->
        BacStatus.of(bac)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BacStatus.SOBER)

    val soberInHours: StateFlow<Double?> = projection.map { proj ->
        proj?.hoursUntil(0.0, System.currentTimeMillis() / 1000)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val driveableInHours: StateFlow<Double?> = combine(projection, profileEntity) { proj, profile ->
        if (proj == null || profile == null) null
        else {
            val limit = UserProfileRepository.toProfile(profile).drivingLimit
            proj.hoursUntil(limit, System.currentTimeMillis() / 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalCalories: StateFlow<Int> = drinks.map { list ->
        list.sumOf { it.calories }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalAlcoholGrams: StateFlow<Double> = drinks.map { list ->
        list.sumOf { it.alcoholGrams }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Hydration metrics
    val recommendedWaterMl: StateFlow<Int> = drinks.map { list ->
        HydrationCalculator.recommendedExtraWaterMl(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hydrationGlasses: StateFlow<Int> = drinks.map { list ->
        HydrationCalculator.recommendedGlasses(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Curve projections for 8h and 24h
    val bacCurve: StateFlow<List<CurvePoint>> = combine(projection, ticker) { proj, now ->
        proj?.curve(now - 3600, 8.0, 5.0) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bacCurve24h: StateFlow<List<CurvePoint>> = combine(projection, ticker) { proj, now ->
        proj?.curve(now - 3600 * 3, 24.0, 10.0) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Trend calculation (comparing with 5 minutes ago)
    val bacTrend: StateFlow<BacTrend> = combine(projection, ticker) { proj, now ->
        if (proj == null) return@combine BacTrend.STABLE
        val curr = proj.currentBac(now)
        if (curr <= 0.01) return@combine BacTrend.STABLE
        val fiveMinsAgo = proj.currentBac(now - 300)
        when {
            curr > fiveMinsAgo + 0.005 -> BacTrend.RISING
            curr < fiveMinsAgo - 0.005 -> BacTrend.FALLING
            else -> BacTrend.STABLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BacTrend.STABLE)

    /**
     * Highest value reached today, the curve peak up to now or the current value
     * if that is higher. Derived from the 24h curve, which is a full integration,
     * so it is a flow rather than something the card recomputes while drawing.
     */
    val maxToday: StateFlow<Double> = combine(bacCurve24h, currentBAC) { curve, current ->
        val dayStart = LogicalDay.startOf(System.currentTimeMillis() / 1000, zone)
        val now = System.currentTimeMillis() / 1000
        val peak = curve
            .filter { it.epochSeconds in dayStart..now }
            .maxOfOrNull { it.bac } ?: 0.0
        maxOf(peak, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Null when nothing has been logged at all. */
    val minutesSinceLastDrink: StateFlow<Int?> = combine(rawDrinks, ticker) { list, now ->
        list.maxOfOrNull { it.timestampEpochSeconds }?.let { maxOf(0, ((now - it) / 60).toInt()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Drinks with alcohol since Monday, for the weekly limit card. Counted from
     * the calendar week, not the logical day, which is what iOS does too.
     */
    val currentWeekDrinkCount: StateFlow<Int> = combine(rawDrinks, ticker) { _, _ ->
        // On the ticker too: a phone left open across Sunday night would keep
        // showing last week's count against this week's limit otherwise.
        drinkRepository.getDrinkCountWithAlcoholSince(startOfWeekSeconds())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun startOfWeekSeconds(): Long =
        java.time.LocalDate.now(zone)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toEpochSecond()

    /** Hours until the projection drops below [target], null when beyond the horizon. */
    fun hoursUntil(target: Double): Double? =
        projection.value?.hoursUntil(target, System.currentTimeMillis() / 1000)

    val pacingWarning: StateFlow<String?> = combine(drinks, currentBAC) { list, bac ->
        if (list.size >= 3 && bac > 0.5) {
            val recentMinutes = 60
            val recentThreshold = System.currentTimeMillis() / 1000 - recentMinutes * 60
            val drinksLastHour = list.count { it.timestampEpochSeconds >= recentThreshold }
            if (drinksLastHour >= 3) {
                "Du trinkst gerade schnell ($drinksLastHour Drinks in der letzten Stunde). Gönn dir ein Glas Wasser!"
            } else null
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Undo state
    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    // Active sip drink counter state
    private val _activeSipDrink = MutableStateFlow<Drink?>(null)
    val activeSipDrink: StateFlow<Drink?> = _activeSipDrink.asStateFlow()

    private val _sipCount = MutableStateFlow(0)
    val sipCount: StateFlow<Int> = _sipCount.asStateFlow()

    init {
        viewModelScope.launch {
            profileEntity.filterNotNull().first().let {
                stomachStatus.value = StomachStatus.from(it.stomachStatusRaw)
            }
        }
        viewModelScope.launch {
            // Mirrors HomeView's onChange(of: session.currentBAC). The publisher
            // throttles, so collecting every ticker tick is not a PATCH per tick.
            currentBAC.collect { bac ->
                bacPublisher.publish(bac, profileEntity.value?.eliminationRate ?: 0.15)
                // The jam roster reads this. Left unfed, everyone in the jam
                // shows as 0,00 and the ghost host election would always pick
                // whoever is on Android as the soberest member.
                jamService.myCurrentBAC.value = bac
                jamService.myCurrentStatus.value = BacStatus.of(bac).germanName
            }
        }
    }

    // CRUD & Event Actions
    fun addDrink(entity: DrinkEntity) {
        viewModelScope.launch {
            drinkRepository.addDrink(entity)
            _undoAction.value = UndoAction(label = "${entity.name} hinzugefügt", addedDrink = entity)
        }
    }

    fun duplicateDrink(drink: Drink) {
        val copy = DrinkEntity(
            id = UUID.randomUUID().toString(),
            templateID = drink.templateId,
            name = drink.name,
            volume = drink.volumeML,
            abv = drink.abv,
            calories = drink.calories,
            iconName = drink.iconName,
            categoryRaw = drink.category.raw,
            timestampEpochSeconds = System.currentTimeMillis() / 1000
        )
        addDrink(copy)
    }

    fun finishDrinkNow(drink: Drink) {
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val durationMinutes = kotlin.math.max(1.0, (now - drink.timestampEpochSeconds) / 60.0)
            val updated = DrinkRepository.toEntity(drink).copy(drinkDurationMinutes = durationMinutes)
            drinkRepository.updateDrink(updated)
        }
    }

    fun updateDrink(drink: Drink, volume: Double, timestampSeconds: Long, durationMinutes: Double) {
        viewModelScope.launch {
            val factor = if (drink.volumeML > 0) volume / drink.volumeML else 1.0
            val updatedCalories = (drink.calories * factor).toInt()
            val updated = DrinkRepository.toEntity(drink).copy(
                volume = volume,
                calories = updatedCalories,
                timestampEpochSeconds = timestampSeconds,
                drinkDurationMinutes = durationMinutes
            )
            drinkRepository.updateDrink(updated)
        }
    }

    fun removeDrink(drink: Drink) {
        viewModelScope.launch {
            val entity = DrinkRepository.toEntity(drink)
            drinkRepository.deleteDrink(entity)
            _undoAction.value = UndoAction(label = "${drink.name} gelöscht", deletedDrink = entity)
        }
    }

    fun performUndo() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            if (action.deletedDrink != null) {
                drinkRepository.addDrink(action.deletedDrink)
            } else if (action.addedDrink != null) {
                drinkRepository.deleteDrink(action.addedDrink)
            }
            _undoAction.value = null
        }
    }

    fun clearUndo() {
        _undoAction.value = null
    }

    // Sip Counter Actions
    fun startSipCounter(drink: Drink) {
        _activeSipDrink.value = drink
        _sipCount.value = 0
    }

    fun recordSip(sipVolumeML: Double = 25.0) {
        _sipCount.value += 1
    }

    fun finishSipCounter() {
        _activeSipDrink.value = null
        _sipCount.value = 0
    }

    // Session Events
    fun logVomit() {
        viewModelScope.launch { sessionEventRepository.logVomit() }
    }

    fun removeLastVomit() {
        viewModelScope.launch {
            val last = rawVomits.value.lastOrNull() ?: return@launch
            sessionEventRepository.deleteVomitEvent(last)
        }
    }

    fun logMeal(impact: MealImpact, name: String = "") {
        viewModelScope.launch { sessionEventRepository.logMeal(impact, name) }
    }

    fun logBreathalyzerReading(measuredBac: Double, note: String = "") {
        viewModelScope.launch {
            val est = currentBAC.value
            sessionEventRepository.logBreathalyzerReading(measuredBac, est, "manual", note)
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            drinkRepository.deleteAll()
            sessionEventRepository.clearAll()
            _undoAction.value = null
        }
    }
}
