package de.tipau.promille.ui.screens.home
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*


import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.LocalReducedMotion
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.ui.components.*
import de.tipau.promille.ui.screens.quickadd.QuickAddSheet
import de.tipau.promille.ui.viewmodels.SessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import de.tipau.promille.bac.LogicalDay
import de.tipau.promille.data.toDomain
import de.tipau.promille.network.pingCityDrink
import de.tipau.promille.service.AppUpdateService
import de.tipau.promille.service.UpdateCheckResult
import de.tipau.promille.AppSerif

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    templateRepository: DrinkTemplateRepository,
    container: de.tipau.promille.di.AppContainer? = null,
    onOpenCrew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bac by viewModel.currentBAC.collectAsState()
    val status by viewModel.bacStatus.collectAsState()
    val skin by viewModel.statusSkin.collectAsState()
    val reducedMotion = LocalReducedMotion.current
    val trend by viewModel.bacTrend.collectAsState()
    val soberIn by viewModel.soberInHours.collectAsState()
    val driveableIn by viewModel.driveableInHours.collectAsState()
    val drinks by viewModel.drinks.collectAsState()
    val extraSweatML by viewModel.extraSweatML.collectAsState()
    val stomachStatus by viewModel.stomachStatus.collectAsState()
    val totalCalories by viewModel.totalCalories.collectAsState()
    val totalAlcoholGrams by viewModel.totalAlcoholGrams.collectAsState()
    val recommendedWater by viewModel.recommendedWaterMl.collectAsState()
    val waterGlasses by viewModel.hydrationGlasses.collectAsState()
    var loggedGlasses by remember { mutableStateOf(container?.waterLog?.glassesToday(System.currentTimeMillis() / 1000) ?: 0) }
    val curvePoints by viewModel.bacCurve.collectAsState()
    val curvePoints24h by viewModel.bacCurve24h.collectAsState()
    val pacingWarning by viewModel.pacingWarning.collectAsState()
    val undoAction by viewModel.undoAction.collectAsState()
    val profile by viewModel.profileEntity.collectAsState()
    val maxToday by viewModel.maxToday.collectAsState()
    val minutesSinceLast by viewModel.minutesSinceLastDrink.collectAsState()
    val weekCount by viewModel.currentWeekDrinkCount.collectAsState()
    val activeSipDrink by viewModel.activeSipDrink.collectAsState()
    val sipCount by viewModel.sipCount.collectAsState()
    val sipTotalML by viewModel.sipTotalML.collectAsState()
    val sipPromille by viewModel.sipPromille.collectAsState()
    val currentSipVolume by viewModel.currentSipVolume.collectAsState()

    val scope = rememberCoroutineScope()
    val crewMembers by (container?.crewRepository?.members
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val myProfile by (container?.supabase?.myProfile
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    // Anonymous city-trends ping, gated by the device-local opt-in (matches
    // HomeView.swift's pingCityTrend, called after every confirmed drink log).
    val pingCity by de.tipau.promille.service.LocationService.currentCity.collectAsState()
    fun pingCityTrend(drink: DrinkEntity) {
        if (profile?.shareAnonymousCityInsights != true) return
        val city = pingCity ?: return
        val supabaseService = container?.supabase ?: return
        val sessionStart = drinks.minOfOrNull { it.timestampEpochSeconds } ?: drink.timestampEpochSeconds
        val sessionMinutes = ((System.currentTimeMillis() / 1000) - sessionStart).toInt() / 60
        val effectiveMinutes = drink.toDomain()
            .effectiveDrinkDurationMinutes(de.tipau.promille.bac.DrinkPaceMemory.disabled())
        val localHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        scope.launch {
            supabaseService.pingCityDrink(
                city = city,
                drinkName = drink.name,
                category = drink.categoryRaw,
                currentBAC = bac,
                sessionDurationMinutes = sessionMinutes,
                drinkDurationMinutes = Math.round(effectiveMinutes).toInt(),
                localHour = localHour
            )
        }
    }

    // Running ticker so careScore re-evaluates as time decays
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }

    // Same rule as the Crew screen: the decayed value, not the published one.
    val needsAttention = remember(crewMembers, nowSeconds) {
        crewMembers.filter {
            !it.isHome && de.tipau.promille.bac.CrewMath.careScore(
                it.currentBAC, it.lastDrinkTimestamp, nowSeconds
            ) >= 40
        }.map { it.name }
    }

    val favorites by templateRepository.getTopFavorites(4).collectAsState(initial = emptyList())

    var showQuickAdd by remember { mutableStateOf(false) }
    var showRidePicker by remember { mutableStateOf(false) }
    var showFullScreenChart by remember { mutableStateOf(false) }
    var showHomeEditSheet by remember { mutableStateOf(false) }
    var isWidgetEditMode by remember { mutableStateOf(false) }
    var showMorningMoodPrompt by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var amountTemplate by remember { mutableStateOf<DrinkTemplateEntity?>(null) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val updateResult = AppUpdateService.checkForUpdate()
        if (updateResult is UpdateCheckResult.UpdateAvailable) {
            val prefs = context.getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)
            val dismissedTag = prefs.getString("dismissed_update_tag", "")
            if (dismissedTag != updateResult.newVersion) {
                availableUpdate = updateResult
            }
        }
    }

    LaunchedEffect(drinks) {
        val dayNoteRepo = container?.dayNoteRepository
        if (dayNoteRepo != null) {
            val now = Instant.now()
            val todayLogical = LogicalDay.dateOf(now.epochSecond)
            val yesterdayLogical = todayLogical.minus(java.time.Period.ofDays(1))
            val yesterdayIso = yesterdayLogical.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val moodPromptDismissKey = "moodPromptDismissed_$yesterdayIso"

            val prefs = context.getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)
            val isDismissed = prefs.getBoolean(moodPromptDismissKey, false)

            val existing = dayNoteRepo.getNoteForDay(yesterdayIso)
            val hadAlcoholYesterday = drinks.any {
                it.abv > 0.01 && LogicalDay.dateOf(it.timestampEpochSeconds) == yesterdayLogical
            }
            if (!isDismissed && hadAlcoholYesterday && (existing == null || existing.moodRaw == 0)) {
                showMorningMoodPrompt = true
            }
        }
    }

    val activeWidgets = remember(profile?.activeWidgetsRaw) {
        HomeWidgetType.parseActiveWidgets(profile?.activeWidgetsRaw ?: "")
    }

    if (showHomeEditSheet) {
        HomeEditSheet(
            profile = profile,
            onDismiss = { showHomeEditSheet = false },
            onSave = { homeStyle, warningThreshold, activeWidgetsRaw ->
                viewModel.updateHomeCustomization(homeStyle, warningThreshold, activeWidgetsRaw)
            }
        )
    }

    val haptics = de.tipau.promille.ui.components.rememberHapticManager()

    // Achievement toast (HomeView.swift:200-217, 2702-2744).
    val unlockedIds by (container?.achievementService?.unlockedIds
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())).collectAsState()
    var seenAchievements by remember { mutableStateOf<Set<String>?>(null) }
    var unlockedAchievementToast by remember { mutableStateOf<Pair<de.tipau.promille.bac.Achievement, Int>?>(null) }
    LaunchedEffect(unlockedIds) {
        val seen = seenAchievements
        if (seen == null) {
            // First emission is the existing state, not a fresh unlock.
            seenAchievements = unlockedIds
            return@LaunchedEffect
        }
        val freshIds = (unlockedIds - seen).toList()
        seenAchievements = unlockedIds
        if (freshIds.isNotEmpty()) {
            val firstAchievement = de.tipau.promille.bac.AchievementCatalog.ALL
                .firstOrNull { it.id == freshIds.first() }
            if (firstAchievement != null) {
                haptics.success()
                unlockedAchievementToast = firstAchievement to freshIds.size
                delay(4000)
                unlockedAchievementToast = null
            }
        }
    }

    amountTemplate?.let { template ->
        de.tipau.promille.ui.screens.quickadd.AmountInputSheet(
            template = template,
            onDismiss = { amountTemplate = null },
            onDrinkAdded = {
                viewModel.addDrink(it)
                pingCityTrend(it)
                amountTemplate = null
            }
        )
    }

    if (showRidePicker) {
        de.tipau.promille.ui.screens.safety.RidePickerSheet(
            emergencyContactPhone = profile?.emergencyContactPhone,
            onDismiss = { showRidePicker = false }
        )
    }

    if (showFullScreenChart) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullScreenChart = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            de.tipau.promille.ui.components.FullScreenBacChart(
                points = if (curvePoints24h.isNotEmpty()) curvePoints24h else curvePoints,
                drivingLimit = profile?.let { de.tipau.promille.repository.UserProfileRepository.toProfile(it).drivingLimit } ?: 0.5,
                onDismiss = { showFullScreenChart = false }
            )
        }
    }
    var showResetDialog by remember { mutableStateOf(false) }
    var showMealSheet by remember { mutableStateOf(false) }
    var showBreathalyzerDialog by remember { mutableStateOf(false) }
    var showVomitDialog by remember { mutableStateOf(false) }
    var editingDrink by remember { mutableStateOf<Drink?>(null) }

    val vomits by viewModel.rawVomits.collectAsState()

    if (showMealSheet) {
        MealLoggingSheet(
            onDismiss = { showMealSheet = false },
            onLogMeal = { impact, name -> viewModel.logMeal(impact, name) }
        )
    }

    if (showBreathalyzerDialog) {
        BreathalyzerDialog(
            currentEstimatedBAC = bac,
            onDismiss = { showBreathalyzerDialog = false },
            onSaveReading = { measured, note -> viewModel.logBreathalyzerReading(measured, note) }
        )
    }

    if (showVomitDialog) {
        VomitConfirmDialog(
            vomitCountToday = vomits.size,
            onDismiss = { showVomitDialog = false },
            onConfirmVomit = { viewModel.logVomit() },
            onUndoLastVomit = { viewModel.removeLastVomit() }
        )
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }

    // Auto-hide undo snackbar after 5 seconds
    LaunchedEffect(undoAction) {
        if (undoAction != null) {
            delay(5000)
            viewModel.clearUndo()
        }
    }

    if (showResetDialog) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = "Sitzung zurücksetzen?",
            text = "Alle heutigen Getränke und Ereignisse werden gelöscht.",
            confirmText = "Zurücksetzen",
            isDestructive = true,
            onConfirm = {
                viewModel.resetSession()
                showResetDialog = false
            },
            dismissText = "Abbrechen"
        )
    }

    if (showQuickAdd) {
        QuickAddSheet(
            templateRepository = templateRepository,
            onDismiss = { showQuickAdd = false },
            onDrinkAdded = { drink ->
                viewModel.addDrink(drink)
                pingCityTrend(drink)
            },
            onStartSipCounter = { template ->
                viewModel.startSipCounter(template)
            },
            supabase = container?.supabase,
            customMixDao = container?.customMixDao
        )
    }

    if (editingDrink != null) {
        DrinkEditSheet(
            drink = editingDrink!!,
            onDismiss = { editingDrink = null },
            onSave = { vol, ts, dur ->
                viewModel.updateDrink(editingDrink!!, vol, ts, dur)
            },
            onDuplicate = { viewModel.duplicateDrink(editingDrink!!) },
            onFinishNow = { viewModel.finishDrinkNow(editingDrink!!) },
            onDelete = { viewModel.removeDrink(editingDrink!!) }
        )
    }

    // Drunk mode is opt-in per profile and dismissable for the rest of the session,
    // so the auto trigger cannot trap someone in the simplified layout.
    var drunkModeDismissed by remember { mutableStateOf(false) }
    val isDrunkMode = profile?.drunkModeAuto == true &&
        !drunkModeDismissed &&
        bac >= (profile?.carefulThreshold ?: 0.80)

    Box(modifier = modifier.fillMaxSize().background(AppColors.background)) {
        if (isDrunkMode) {
            DrunkHomeView(
                viewModel = viewModel,
                profile = profile,
                waterLog = container?.waterLog,
                sosActive = myProfile?.sosActive == true,
                onAddDrink = { showQuickAdd = true },
                onCallRide = { showRidePicker = true },
                onToggleSOS = {
                    val next = myProfile?.sosActive != true
                    scope.launch { runCatching { container?.supabase?.setSOS(next) } }
                },
                onExit = {
                    if (profile?.largeText == true) viewModel.disableLargeText()
                    drunkModeDismissed = true
                }
            )
        } else if ((profile?.homeStyleRaw ?: "detailed") == "minimal") {
            MinimalHomeView(
                bac = bac,
                status = status,
                skin = skin,
                onAddDrink = { showQuickAdd = true },
                onExitToDetailed = { viewModel.setHomeStyle("detailed") }
            )
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Bar
            item {
                if (isWidgetEditMode) {
                    EditModeTopBar(
                        onDone = { isWidgetEditMode = false }
                    )
                } else {
                    HomeTopBar(
                        onResetClick = { showResetDialog = true },
                        onEdit = { isWidgetEditMode = true }
                    )
                }
            }

            // Pacing Warning (HomeView.swift:529-534, 2748-2779)
            if (pacingWarning != null && !isWidgetEditMode) {
                item {
                    de.tipau.promille.ui.components.PacingHintBanner(message = pacingWarning!!)
                }
            }

            if (needsAttention.isNotEmpty() && !isWidgetEditMode) {
                item {
                    // iOS's banner is inert (HomeView.swift:494 passes no
                    // closure) with a decorative chevron. This one has a real
                    // ripple, so the chevron has to lead somewhere.
                    CrewAlertBanner(names = needsAttention, onClick = onOpenCrew)
                }
            }

            // 1. Central BAC Display Section
            item {
                BACDisplaySection(
                    bac = bac,
                    status = status,
                    skin = skin,
                    trend = trend,
                    isEditMode = isWidgetEditMode,
                    onLongClick = { isWidgetEditMode = true }
                )
            }

            // Action Prompt (Empty State)
            if (drinks.isEmpty() && !isWidgetEditMode) {
                item {
                    EmptyDrinkHint(onAdd = { showQuickAdd = true })
                }
            }

            // 2. Stomach Status Selector (MAGEN: Leer / Leicht gefüllt / Satt)
            item {
                EditableWidgetContainer(
                    isEditMode = isWidgetEditMode,
                    isActive = activeWidgets.contains(HomeWidgetType.STOMACH_STATUS),
                    onToggleActive = { viewModel.toggleWidget("stomachStatus") }
                ) {
                    StomachStatusPicker(
                        currentStatus = stomachStatus,
                        onStatusSelected = { viewModel.stomachStatus.value = it }
                    )
                }
            }

            // 3. Event Logging Cards (Essen protokollieren & Breathalyser Messung)
            if (drinks.isNotEmpty() && !isWidgetEditMode) {
                item {
                    MealActionCard(
                        lastMealSubtitle = null,
                        onClick = { showMealSheet = true }
                    )
                }
                item {
                    BreathalyzerCard(
                        currentBAC = bac,
                        latestReadingText = null,
                        onClick = { showBreathalyzerDialog = true }
                    )
                }
            }

            // 4. Übergeben
            if (drinks.isNotEmpty() && !isWidgetEditMode) {
                item {
                    VomitActionCard(
                        vomitCount = vomits.size,
                        onLogClick = { showVomitDialog = true },
                        onUndoClick = { viewModel.removeLastVomit() }
                    )
                }
            }

            // 5. BAC Verlauf (Timeline Chart)
            item {
                EditableWidgetContainer(
                    isEditMode = isWidgetEditMode,
                    isActive = activeWidgets.contains(HomeWidgetType.BAC_CURVE),
                    onToggleActive = { viewModel.toggleWidget("bacCurve") }
                ) {
                    BACCurveChartView(
                        points = curvePoints,
                        drinks = drinks,
                        warningThreshold = 0.5,
                        onFullScreenTap = { showFullScreenChart = true }
                    )
                }
            }

            // 6. Unter 0,5% / Nächster Meilenstein
            if (drinks.isNotEmpty() || isWidgetEditMode) {
                item {
                    EditableWidgetContainer(
                        isEditMode = isWidgetEditMode,
                        isActive = activeWidgets.contains(HomeWidgetType.MILESTONE),
                        onToggleActive = { viewModel.toggleWidget("milestone") }
                    ) {
                        val hoursUntilTarget = remember(nowSeconds, profile) {
                            viewModel.hoursUntil(
                                if (profile?.isProbationaryDriver == true) {
                                    profile?.tipsyThreshold ?: 0.01
                                } else {
                                    0.5
                                }
                            )
                        }
                        MilestoneCard(
                            hoursUntilTarget = hoursUntilTarget,
                            isProbationaryDriver = profile?.isProbationaryDriver == true
                        )
                    }
                }
            }

            // 7. Hydration Section
            item {
                EditableWidgetContainer(
                    isEditMode = isWidgetEditMode,
                    isActive = activeWidgets.contains(HomeWidgetType.WATER) || activeWidgets.contains(HomeWidgetType.HYDRATION),
                    onToggleActive = { viewModel.toggleWidget("hydration") }
                ) {
                    if (container?.waterLog != null) {
                        HydrationWidget(
                            drinks = drinks,
                            profile = profile?.let { de.tipau.promille.repository.UserProfileRepository.toProfile(it) },
                            extraSweatML = extraSweatML,
                            vomitCount = vomits.size,
                            waterLog = container.waterLog
                        )
                    } else {
                        HydrationCard(
                            drinksCount = drinks.size,
                            waterGlasses = loggedGlasses,
                            recommendedWaterMl = recommendedWater,
                            onAddGlass = {
                                loggedGlasses++
                            },
                            onRemoveGlass = {
                                loggedGlasses = (loggedGlasses - 1).coerceAtLeast(0)
                            }
                        )
                    }
                }
            }

            // 8. 2x2 Info Tiles Grid (Unter Grenzwert / Wasser / Kalorien / Drinks heute)
            val showTime = activeWidgets.contains(HomeWidgetType.TIME_TO_LIMIT)
            val showWater = activeWidgets.contains(HomeWidgetType.WATER)
            val showCalories = activeWidgets.contains(HomeWidgetType.CALORIES)
            val showCount = activeWidgets.contains(HomeWidgetType.DRINK_COUNT)

            item {
                EditableWidgetContainer(
                    isEditMode = isWidgetEditMode,
                    isActive = showTime || showWater || showCalories || showCount,
                    onToggleActive = { viewModel.toggleGridTiles() }
                ) {
                    // Each tile follows its own switch, as iOS's HomeWidgetGrid
                    // does. The four used to render unconditionally, so turning
                    // Wasser, Kalorien or Drinks heute off in the edit sheet
                    // changed nothing on screen. Building the list and chunking
                    // it by two is what iOS's two-column LazyVGrid does with an
                    // odd number of tiles; the trailing Spacer keeps the last
                    // one at half width instead of letting it stretch.
                    val drivingLimit = profile
                        ?.let { de.tipau.promille.repository.UserProfileRepository.toProfile(it).drivingLimit } ?: 0.5
                    val overLimit = bac > drivingLimit + 0.005
                    val tiles = buildList<@Composable (Modifier) -> Unit> {
                        if (showTime) add { m ->
                            val driveText = driveableIn?.let { if (it <= 0) "Nüchtern" else "in ${formatHours(it)}" } ?: "Nüchtern"
                            InfoWidget(
                                icon = AppIcons.Car,
                                label = if (profile?.isProbationaryDriver == true) "Bis 0,0 ‰" else "Bis 0,5 ‰",
                                value = driveText,
                                iconColor = if (overLimit) AppColors.statusOrange else AppColors.accent,
                                isHighlighted = overLimit,
                                modifier = m
                            )
                        }
                        if (showWater) add { m ->
                            val waterText = if (recommendedWater <= 0) {
                                "Ausreichend"
                            } else {
                                "$waterGlasses ${if (waterGlasses == 1) "Glas" else "Gläser"}"
                            }
                            InfoWidget(
                                icon = AppIcons.Water,
                                label = "Wasser",
                                value = waterText,
                                iconColor = AppColors.accent,
                                modifier = m
                            )
                        }
                        if (showCalories) add { m ->
                            InfoWidget(
                                icon = AppIcons.Fire,
                                label = "Kalorien",
                                value = "$totalCalories kcal",
                                iconColor = AppColors.statusOrange,
                                modifier = m
                            )
                        }
                        if (showCount) add { m ->
                            InfoWidget(
                                icon = AppIcons.Person,
                                label = "Drinks heute",
                                value = "${drinks.size}",
                                iconColor = AppColors.statusGreen,
                                modifier = m
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { tile -> tile(Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Kater-Prognose Card
            item {
                HangoverForecastCard(currentBAC = bac)
            }

            // Weekly Limit Card
            if (!isWidgetEditMode) {
                profile?.weeklyDrinkLimit?.takeIf { it > 0 }?.let { limit ->
                    item { WeeklyLimitCard(used = weekCount, limit = limit) }
                }
            }

            // 9. Day Stats (Drinks, Maximum, Letzter Drink)
            item {
                EditableWidgetContainer(
                    isEditMode = isWidgetEditMode,
                    isActive = activeWidgets.contains(HomeWidgetType.DAY_STATS),
                    onToggleActive = { viewModel.toggleWidget("dayStats") }
                ) {
                    DayStatsCard(
                        drinkCount = drinks.size,
                        maxToday = maxToday,
                        minutesSinceLastDrink = minutesSinceLast
                    )
                }
            }

            // 10. Schnell hinzufügen (Favourites Strip)
            if (favorites.isNotEmpty() || isWidgetEditMode) {
                item {
                    EditableWidgetContainer(
                        isEditMode = isWidgetEditMode,
                        isActive = activeWidgets.contains(HomeWidgetType.FAV_STRIP),
                        onToggleActive = { viewModel.toggleWidget("favStrip") }
                    ) {
                        FavouritesStrip(
                            templates = favorites,
                            onAdd = { template ->
                                val quickDrink = DrinkEntity(
                                    id = UUID.randomUUID().toString(),
                                    templateID = template.id,
                                    name = template.name,
                                    volume = template.volume,
                                    abv = template.abv,
                                    calories = template.calories,
                                    iconName = template.iconName,
                                    categoryRaw = template.categoryRaw,
                                    timestampEpochSeconds = System.currentTimeMillis() / 1000
                                )
                                viewModel.addDrink(quickDrink)
                                pingCityTrend(quickDrink)
                            },
                            onLongPress = { amountTemplate = it }
                        )
                    }
                }
            }

            // 11. Heute mit der Drinks Auflistung
            if (drinks.isNotEmpty() || isWidgetEditMode) {
                item {
                    EditableWidgetContainer(
                        isEditMode = isWidgetEditMode,
                        isActive = activeWidgets.contains(HomeWidgetType.DRINK_HISTORY),
                        onToggleActive = { viewModel.toggleWidget("drinkHistory") }
                    ) {
                        DrinkHistorySection(
                            drinks = drinks,
                            stomachStatus = stomachStatus,
                            onEdit = { editingDrink = it },
                            onFinish = { viewModel.finishDrinkNow(it) },
                            onDuplicate = { viewModel.duplicateDrink(it) },
                            onDelete = { viewModel.removeDrink(it) }
                        )
                    }
                }
            }

            // 12. Sicher nach Hause (Safety Actions)
            if (container != null) {
                item {
                    EditableWidgetContainer(
                        isEditMode = isWidgetEditMode,
                        isActive = activeWidgets.contains(HomeWidgetType.SAFETY_ACTIONS),
                        onToggleActive = { viewModel.toggleWidget("safetyActions") }
                    ) {
                        SafetyActionsCard(
                            sosActive = myProfile?.sosActive == true,
                            onCallRide = { showRidePicker = true },
                            onToggleSOS = {
                                val next = myProfile?.sosActive != true
                                scope.launch { runCatching { container.supabase.setSOS(next) } }
                            }
                        )
                    }
                }
            }

            // 10. Disclaimer Footer Text matching iOS
            item {
                Text(
                    text = "Widmark-Schätzwert. Müdigkeit, Medikamente und individuelle Faktoren können stark abweichen. Kein Ersatz für einen Atemtest. Im Zweifel nicht fahren.",
                    color = AppColors.textMuted,
                    style = de.tipau.promille.AppText.micro,
                    lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }

        // FAB placement mirrors HomeView.swift:591-594: trailing 24, bottom 32.
        if (!isWidgetEditMode && activeSipDrink == null) {
            PromilleFAB(
                text = "Drink hinzufügen",
                onClick = { showQuickAdd = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 32.dp)
            )
        }
        }

        // Morning mood prompt sits above the layout branch, not inside the detailed
        // list: the minimal and drunk layouts have no list, and losing the prompt
        // there would silently stop the day note from ever getting a mood.
        if (showMorningMoodPrompt) {
            de.tipau.promille.ui.components.MorningMoodPrompt(
                onSelect = { mood ->
                    showMorningMoodPrompt = false
                    val now = Instant.now()
                    val todayLogical = LogicalDay.dateOf(now.epochSecond)
                    val yesterdayLogical = todayLogical.minus(java.time.Period.ofDays(1))
                    val yesterdayIso = yesterdayLogical.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val moodPromptDismissKey = "moodPromptDismissed_$yesterdayIso"

                    val prefs = context.getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(moodPromptDismissKey, true).apply()

                    scope.launch {
                        container?.dayNoteRepository?.saveNote(yesterdayIso, "", mood.raw)
                    }
                },
                onDismiss = {
                    showMorningMoodPrompt = false
                    val now = Instant.now()
                    val todayLogical = LogicalDay.dateOf(now.epochSecond)
                    val yesterdayLogical = todayLogical.minus(java.time.Period.ofDays(1))
                    val yesterdayIso = yesterdayLogical.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val moodPromptDismissKey = "moodPromptDismissed_$yesterdayIso"

                    val prefs = context.getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(moodPromptDismissKey, true).apply()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            )
        }

        if (availableUpdate != null && !showMorningMoodPrompt) {
            val update = availableUpdate!!
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                    .clickable { showUpdateSheet = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.ArrowDown, null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Android-only (AppUpdateService has no iOS source);
                    // appBodyBold/appMicro match this sweep's banner pairing.
                    Text(
                        text = "Update verfügbar: v${update.newVersion}",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.bodyBold
                    )
                    Text(
                        text = "Tippen für Details & Download",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.micro
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AppColors.background)
                        .clickable {
                            val prefs = context.getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("dismissed_update_tag", update.newVersion).apply()
                            availableUpdate = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                }
            }
        }

        if (showUpdateSheet) {
            de.tipau.promille.ui.components.AppUpdateSheet(
                onDismiss = {
                    showUpdateSheet = false
                    availableUpdate = null
                }
            )
        }

        // Floating Achievement Unlock Toast (bottom-rising toast matching iOS HomeView.swift:200-217)
        AnimatedVisibility(
            visible = unlockedAchievementToast != null && activeSipDrink == null,
            enter = if (reducedMotion) fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = if (reducedMotion) fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (undoAction != null) 148.dp else 84.dp)
        ) {
            unlockedAchievementToast?.let { (achievement, count) ->
                de.tipau.promille.ui.components.AchievementUnlockToast(
                    achievement = achievement,
                    count = count,
                    onDismiss = { unlockedAchievementToast = null }
                )
            }
        }

        // Floating Sip Counter Overlay (replaces bottom area while counting)
        AnimatedVisibility(
            visible = activeSipDrink != null,
            enter = if (reducedMotion) fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = if (reducedMotion) fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            activeSipDrink?.let { drink ->
                SipCounterView(
                    drinkName = drink.name,
                    drinkAbv = drink.abv,
                    sipCount = sipCount,
                    sipTotalML = sipTotalML.toInt(),
                    sipPromille = sipPromille,
                    currentSipVolume = currentSipVolume.toInt(),
                    onAddSip = { viewModel.addSip() },
                    onRemoveSip = { viewModel.removeSip() },
                    onCommit = { viewModel.commitSips() },
                    onCancel = { viewModel.cancelSipCounter() }
                )
            }
        }

        // Floating Undo Snackbar
        AnimatedVisibility(
            visible = undoAction != null && activeSipDrink == null,
            // Motion.swift:37, appToastBottom: the slide collapses to a plain
            // opacity change when the user asked for less motion.
            enter = if (reducedMotion) fadeIn() else slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = if (reducedMotion) fadeOut() else slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
        ) {
            if (undoAction != null) {
                UndoSnackbar(
                    label = undoAction!!.label,
                    onUndo = { viewModel.performUndo() }
                )
            }
        }
    }
}

@Composable
private fun InfoTile(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color = AppColors.text,
    modifier: Modifier = Modifier
) {
    PromilleCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AppColors.card, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AppColors.text
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppSerif,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatHours(hours: Double): String {
    val totalMinutes = (hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}
