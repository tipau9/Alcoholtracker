package de.tipau.promille.ui.viewmodels
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.color
import de.tipau.promille.bac.*
import de.tipau.promille.data.*
import de.tipau.promille.repository.*
import de.tipau.promille.platform.WeatherService
import de.tipau.promille.service.LocationService
import de.tipau.promille.service.NotificationService
import de.tipau.promille.sync.BACPublisher
import de.tipau.promille.sync.JamService
import de.tipau.promille.ui.screens.home.HomeWidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

enum class BacTrend(val symbol: String, val text: String) {
    RISING("↑", "steigend"),
    STABLE("■", "stabil"),
    FALLING("↓", "fallend")
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

    // Weather-driven hydration heat term (extra sweat loss on a warm night).
    // Mirrors iOS HomeView.weatherSweatML: session span capped at 6h, only
    // above a 24C "warm" threshold. Silent - only starts if location
    // permission is already granted; requesting it is a Compose-level concern
    // (Safety/Trends screens), not this ViewModel's job.
    private val weatherService = WeatherService()
    private val currentTempC = MutableStateFlow<Double?>(null)
    private var lastWeatherFetchAtMs = 0L

    val extraSweatML: StateFlow<Double> = combine(drinks, currentTempC, ticker) { list, temp, nowSeconds ->
        if (temp == null || temp < 24.0) return@combine 0.0
        val first = list.minOfOrNull { it.timestampEpochSeconds } ?: return@combine 0.0
        val hours = ((nowSeconds - first) / 3600.0).coerceIn(0.0, 6.0)
        HydrationCalculator.heatSweatLossMl(temp, hours)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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

    // The band edges are user-adjustable in Settings, so the status has to be
    // derived against the profile. Reading it off the fixed defaults made those
    // sliders decorative.
    val bacStatus: StateFlow<BacStatus> = combine(currentBAC, profileEntity) { bac, profile ->
        BacStatus.of(bac, profile?.let { UserProfileRepository.toProfile(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BacStatus.SOBER)

    val statusSkin: StateFlow<StatusSkin> = profileEntity.map { profile ->
        StatusSkin.from(profile?.statusSkinRaw ?: "standard")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusSkin.STANDARD)

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

    val pacingWarning: StateFlow<String?> = combine(rawDrinks, ticker) { list, now ->
        val halfHourAgo = now - 30 * 60
        val recentDrinks = list.filter { it.timestampEpochSeconds >= halfHourAgo && it.abv > 0 }
        if (recentDrinks.size >= 2) {
            "${recentDrinks.size} Drinks in 30 Minuten. Zeit für ein Glas Wasser!"
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Undo state
    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    // Active sip drink counter state
    private val _activeSipDrink = MutableStateFlow<DrinkTemplateEntity?>(null)
    val activeSipDrink: StateFlow<DrinkTemplateEntity?> = _activeSipDrink.asStateFlow()

    private val _sipCount = MutableStateFlow(0)
    val sipCount: StateFlow<Int> = _sipCount.asStateFlow()

    private var sipCounterStartTime: Long? = null

    val currentSipVolume: StateFlow<Double> = combine(activeSipDrink, profileEntity) { drink, profile ->
        val base = profile?.sipVolumeML ?: 25.0
        if (drink == null) return@combine base
        when {
            drink.abv > 20.0 -> maxOf(5.0, base * 0.3) // ~7.5 ml for spirits
            drink.abv > 10.0 -> maxOf(10.0, base * 0.6) // ~15 ml for wine  
            else -> base
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 25.0)

    val sipTotalML: StateFlow<Double> = combine(sipCount, currentSipVolume) { count, vol ->
        count.toDouble() * vol
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val sipPromille: StateFlow<Double> = combine(
        activeSipDrink, sipTotalML, profileEntity, stomachStatus
    ) { drink, totalMl, profileEnt, stomach ->
        if (drink == null || profileEnt == null || totalMl <= 0) return@combine 0.0
        val profile = UserProfileRepository.toProfile(profileEnt)
        val input = DrinkInput(
            offsetMinutes = 0.0,
            volumeML = totalMl,
            abv = drink.abv,
            category = DrinkCategory.from(drink.categoryRaw),
            drinkDurationMinutes = 15.0
        )
        BacCalculator.projectedPeak(
            drink = input,
            profile = profile,
            stomach = stomach,
            conservative = profile.conservativeForApp
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        // Always listens for a coordinate - permission may be granted later
        // from RidePickerSheet/SafetyScreen via onLocationPermissionGranted(),
        // not only at construction time.
        viewModelScope.launch {
            LocationService.coordinate.collect { coord ->
                if (coord == null) return@collect
                val now = System.currentTimeMillis()
                if (now - lastWeatherFetchAtMs < 30 * 60 * 1000) return@collect
                lastWeatherFetchAtMs = now
                currentTempC.value = weatherService.fetchCurrentTemperature(coord.latitude, coord.longitude)
            }
        }
        // Silent refresh for pinging: only if location was already granted
        // elsewhere (Safety/Trends), never prompts from here.
        applicationContext?.let { ctx ->
            if (LocationService.hasPermission(ctx)) LocationService.requestLocation(ctx)
        }

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
                // Deliberately the plain German name, never the skinned label:
                // this string is rendered on other members' devices, and the
                // Emoji skin would put emoji into an app that has none.
                jamService.myCurrentStatus.value =
                    BacStatus.of(bac, profileEntity.value?.let { UserProfileRepository.toProfile(it) }).germanName
            }
        }

        // Automatically reschedule sobriety/drive notifications and widget/statusbar updates
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

            viewModelScope.launch {
                combine(currentBAC, bacStatus, bacTrend, soberInHours) { current, status, trend, soberHours ->
                    // Reads the same token the status pill uses. The hardcoded hex
                    // list that stood here painted DANGER in statusRed, so the widget
                    // showed the careful colour at the worst band.
                    val color = status.color.toArgb()
                    de.tipau.promille.widget.PromilleAppWidgetProvider.updateAllWidgets(
                        context = ctx,
                        bac = current,
                        statusText = status.germanName,
                        statusColor = color
                    )

                    val soberTimeStr = soberHours?.let {
                        val totalMin = (it * 60).toInt()
                        val time = java.time.LocalTime.now().plusMinutes(totalMin.toLong())
                        time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.GERMAN))
                    }

                    NotificationService.updateLiveNotification(
                        context = ctx,
                        bac = current,
                        statusText = status.germanName,
                        trendSymbol = trend.symbol,
                        soberTimeStr = soberTimeStr
                    )
                }.collect()
            }
        }
    }

    private fun haptics(): de.tipau.promille.ui.components.HapticManager? =
        applicationContext?.let { de.tipau.promille.ui.components.HapticManager.from(it) }

    // CRUD & Event Actions
    fun addDrink(entity: DrinkEntity) {
        haptics()?.light()
        viewModelScope.launch {
            drinkRepository.addDrink(entity)
            _undoAction.value = UndoAction(label = "${entity.name} hinzugefügt", addedDrink = entity)
        }
    }

    fun duplicateDrink(drink: Drink) {
        haptics()?.success()
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
        haptics()?.light()
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val durationMinutes = kotlin.math.max(1.0, (now - drink.timestampEpochSeconds) / 60.0)
            val updated = DrinkRepository.toEntity(drink).copy(drinkDurationMinutes = durationMinutes)
            drinkRepository.updateDrink(updated)
        }
    }

    fun updateDrink(drink: Drink, volume: Double, timestampSeconds: Long, durationMinutes: Double) {
        haptics()?.light()
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
        haptics()?.medium()
        viewModelScope.launch {
            val entity = DrinkRepository.toEntity(drink)
            drinkRepository.deleteDrink(entity)
            _undoAction.value = UndoAction(label = "${drink.name} gelöscht", deletedDrink = entity)
        }
    }

    fun performUndo() {
        val action = _undoAction.value ?: return
        haptics()?.light()
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
    fun startSipCounter(template: DrinkTemplateEntity) {
        haptics()?.light()
        _activeSipDrink.value = template
        _sipCount.value = 0
        sipCounterStartTime = System.currentTimeMillis() / 1000
    }

    fun addSip() {
        if (_activeSipDrink.value == null) return
        haptics()?.medium()
        _sipCount.value += 1
    }

    fun removeSip() {
        haptics()?.light()
        _sipCount.value = maxOf(0, _sipCount.value - 1)
    }

    fun commitSips() {
        val template = _activeSipDrink.value ?: return
        val count = _sipCount.value
        if (count <= 0) return
        haptics()?.success()
        val ml = count.toDouble() * currentSipVolume.value
        val scaledCalories = if (template.volume > 0) {
            (template.calories.toDouble() / template.volume * ml).toInt()
        } else 0
        val drink = DrinkEntity(
            id = UUID.randomUUID().toString(),
            templateID = template.id,
            name = "${template.name} ($count Schlucke)",
            volume = ml,
            abv = template.abv,
            calories = scaledCalories,
            iconName = template.iconName,
            categoryRaw = template.categoryRaw,
            timestampEpochSeconds = System.currentTimeMillis() / 1000
        )
        // Use measured drinking duration if available
        val start = sipCounterStartTime
        val withDuration = if (start != null) {
            val dur = maxOf(1.0, (System.currentTimeMillis() / 1000 - start) / 60.0)
            drink.copy(drinkDurationMinutes = dur)
        } else drink
        addDrink(withDuration)
        cancelSipCounter()
    }

    fun cancelSipCounter() {
        _activeSipDrink.value = null
        _sipCount.value = 0
        sipCounterStartTime = null
    }

    // Session Events
    fun logVomit() {
        haptics()?.success()
        viewModelScope.launch { sessionEventRepository.logVomit() }
    }

    fun removeLastVomit() {
        haptics()?.light()
        viewModelScope.launch {
            val last = rawVomits.value.lastOrNull() ?: return@launch
            sessionEventRepository.deleteVomitEvent(last)
        }
    }

    fun logMeal(impact: MealImpact, name: String = "") {
        haptics()?.medium()
        viewModelScope.launch { sessionEventRepository.logMeal(impact, name) }
    }

    fun logBreathalyzerReading(measuredBac: Double, note: String = "") {
        haptics()?.success()
        viewModelScope.launch {
            val est = currentBAC.value
            sessionEventRepository.logBreathalyzerReading(measuredBac, est, "manual", note)
        }
    }

    fun resetSession() {
        haptics()?.warning()
        viewModelScope.launch {
            drinkRepository.deleteAll()
            sessionEventRepository.clearAll()
            _undoAction.value = null
        }
    }

    fun updateHomeCustomization(homeStyle: String, warningThreshold: Double, activeWidgetsRaw: String) {
        userProfileRepository.updateDebounced { entity ->
            entity.copy(
                homeStyleRaw = homeStyle,
                warningThreshold = warningThreshold,
                activeWidgetsRaw = activeWidgetsRaw
            )
        }
    }

    /** Back to the detailed layout from inside the minimal one, other home settings kept. */
    fun setHomeStyle(homeStyle: String) {
        userProfileRepository.updateDebounced { it.copy(homeStyleRaw = homeStyle) }
    }

    /**
     * Leaving drunk mode also drops the oversized type it turned on, mirroring the
     * onExit closure in HomeView.swift. Without this the normal layout stays blown
     * up after the user asked for it back.
     */
    fun disableLargeText() {
        userProfileRepository.updateDebounced { it.copy(largeText = false) }
    }

    fun toggleWidget(widgetRaw: String) {
        val type = HomeWidgetType.entries.firstOrNull { it.raw == widgetRaw } ?: return
        updateWidgets { if (type in it) it - type else it + type }
    }

    /**
     * The four info tiles are one section in edit mode, as on iOS: tapping it
     * clears all four, or restores all four (HomeView.swift:845-851).
     */
    fun toggleGridTiles() {
        val grid = HomeWidgetType.gridTypes
        updateWidgets { active ->
            if (grid.any { it in active }) active - grid.toSet() else active + grid
        }
    }

    // This used to keep its own hardcoded copy of the widget list and its own
    // split/join, which meant the "__none__" sentinel could only ever be half
    // applied. One pair of functions now, HomeWidgetType's.
    private fun updateWidgets(transform: (Set<HomeWidgetType>) -> Set<HomeWidgetType>) {
        val currentProfile = profileEntity.value ?: return
        val raw = currentProfile.activeWidgetsRaw
        val next = transform(HomeWidgetType.parseActiveWidgets(raw))
        val serialized = HomeWidgetType.serialize(next, HomeWidgetType.foreignTokens(raw))
        userProfileRepository.updateDebounced { it.copy(activeWidgetsRaw = serialized) }
    }
}
