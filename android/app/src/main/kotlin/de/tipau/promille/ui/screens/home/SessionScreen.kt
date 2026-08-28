package de.tipau.promille.ui.screens.home

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

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    templateRepository: DrinkTemplateRepository,
    container: de.tipau.promille.di.AppContainer? = null,
    modifier: Modifier = Modifier
) {
    val bac by viewModel.currentBAC.collectAsState()
    val status by viewModel.bacStatus.collectAsState()
    val trend by viewModel.bacTrend.collectAsState()
    val soberIn by viewModel.soberInHours.collectAsState()
    val driveableIn by viewModel.driveableInHours.collectAsState()
    val drinks by viewModel.drinks.collectAsState()
    val stomachStatus by viewModel.stomachStatus.collectAsState()
    val totalCalories by viewModel.totalCalories.collectAsState()
    val totalAlcoholGrams by viewModel.totalAlcoholGrams.collectAsState()
    val recommendedWater by viewModel.recommendedWaterMl.collectAsState()
    val waterGlasses by viewModel.hydrationGlasses.collectAsState()
    val curvePoints by viewModel.bacCurve.collectAsState()
    val pacingWarning by viewModel.pacingWarning.collectAsState()
    val undoAction by viewModel.undoAction.collectAsState()
    val profile by viewModel.profileEntity.collectAsState()
    val maxToday by viewModel.maxToday.collectAsState()
    val minutesSinceLast by viewModel.minutesSinceLastDrink.collectAsState()
    val weekCount by viewModel.currentWeekDrinkCount.collectAsState()

    val scope = rememberCoroutineScope()
    val crewMembers by (container?.crewRepository?.members
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val myProfile by (container?.supabase?.myProfile
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

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
    var amountTemplate by remember { mutableStateOf<DrinkTemplateEntity?>(null) }

    // Achievement toast. The service exposes the unlocked set, not an event, so
    // the newly added id is the difference against what was already on screen.
    val unlockedIds by (container?.achievementService?.unlockedIds
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())).collectAsState()
    var seenAchievements by remember { mutableStateOf<Set<String>?>(null) }
    var unlockToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(unlockedIds) {
        val seen = seenAchievements
        if (seen == null) {
            // First emission is the existing state, not a fresh unlock.
            seenAchievements = unlockedIds
            return@LaunchedEffect
        }
        val fresh = (unlockedIds - seen).firstOrNull()
        seenAchievements = unlockedIds
        if (fresh != null) {
            unlockToast = de.tipau.promille.bac.AchievementCatalog.ALL
                .firstOrNull { it.id == fresh }?.title ?: fresh
            delay(3000)
            unlockToast = null
        }
    }

    amountTemplate?.let { template ->
        de.tipau.promille.ui.screens.quickadd.AmountInputSheet(
            template = template,
            onDismiss = { amountTemplate = null },
            onDrinkAdded = {
                viewModel.addDrink(it)
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
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = AppColors.card,
            title = { Text("Sitzung zurücksetzen?", color = AppColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Alle heutigen Getränke und Ereignisse werden gelöscht.", color = AppColors.textDim) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetSession()
                        showResetDialog = false
                    }
                ) {
                    Text("Zurücksetzen", color = AppColors.statusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    if (showQuickAdd) {
        QuickAddSheet(
            templateRepository = templateRepository,
            onDismiss = { showQuickAdd = false },
            onDrinkAdded = { drink ->
                viewModel.addDrink(drink)
            }
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

    Box(modifier = modifier.fillMaxSize().background(AppColors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "AKTUELL")
                    if (drinks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                                .border(1.dp, AppColors.border, CircleShape)
                                .clickable { showResetDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↺", color = AppColors.textDim, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Pacing Warning
            if (pacingWarning != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.statusOrange.copy(alpha = 0.15f))
                            .border(1.dp, AppColors.statusOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = pacingWarning!!,
                                color = AppColors.statusOrange,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (needsAttention.isNotEmpty()) {
                item {
                    CrewAlertBanner(names = needsAttention, onClick = { /* Crew tab */ })
                }
            }

            // 1. BAC Dial Hero
            item {
                BACDisplaySection(
                    bac = bac,
                    status = status,
                    trend = trend
                )
            }

            // 2. Interactive 24h/8h BAC Curve Chart
            item {
                BACCurveChartView(
                    points = curvePoints,
                    drinks = drinks,
                    warningThreshold = 0.5
                )
            }

            // 3. 2x2 Info Tiles Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Fahrtauglich
                    InfoTile(
                        icon = "🚗",
                        title = "Fahrtauglich",
                        value = driveableIn?.let { formatHours(it) } ?: "jetzt",
                        accentColor = if (driveableIn != null && driveableIn!! > 0) AppColors.statusRed else AppColors.statusGreen,
                        modifier = Modifier.weight(1f)
                    )
                    // Nüchtern
                    InfoTile(
                        icon = "⏱",
                        title = "Nüchtern in",
                        value = soberIn?.let { formatHours(it) } ?: "0 min",
                        accentColor = if (bac > 0.01) AppColors.statusOrange else AppColors.statusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kalorien
                    InfoTile(
                        icon = "🔥",
                        title = "Kalorien",
                        value = "$totalCalories kcal",
                        accentColor = AppColors.accent,
                        modifier = Modifier.weight(1f)
                    )
                    // Getränke
                    InfoTile(
                        icon = "🍺",
                        title = "Getränke",
                        value = "${drinks.size} gesamt",
                        accentColor = AppColors.accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Hydration Widget
            if (drinks.isNotEmpty()) {
                item {
                    PromilleCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💧", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wasserbedarf", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (recommendedWater > 0) "Noch ca. $waterGlasses Gläser ($recommendedWater ml) trinken" else "Ausgeglichen!",
                                    color = AppColors.textDim,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. Stomach Status Picker
            item {
                SectionLabel("Magen-Status")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StomachStatus.entries.forEach { s ->
                        val isSelected = stomachStatus == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                                .border(
                                    1.dp,
                                    if (isSelected) AppColors.accent else AppColors.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.stomachStatus.value = s }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.germanName,
                                color = if (isSelected) AppColors.accent else AppColors.textDim,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Event Logging Cards (Essen, Pusten, Übergeben)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .clickable { showMealSheet = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🍽️", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("Essen", color = AppColors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .clickable { showBreathalyzerDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💨", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("Pusten", color = AppColors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .clickable { showVomitDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🤮", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (vomits.isNotEmpty()) "${vomits.size}× Kotzen" else "Kotzen",
                                color = if (vomits.isNotEmpty()) AppColors.statusOrange else AppColors.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            item {
                FavouritesStrip(
                    templates = favorites,
                    onAdd = { template ->
                        viewModel.addDrink(
                            DrinkEntity(
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
                        )
                    },
                    // The stored size is not always the one in front of you.
                    onLongPress = { amountTemplate = it }
                )
            }

            item {
                // Keyed on the ticker: hoursUntil reads projection.value and is
                // not a flow, so without this the countdown freezes until some
                // other observed state happens to change.
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

            item {
                DayStatsCard(
                    drinkCount = drinks.size,
                    maxToday = maxToday,
                    minutesSinceLastDrink = minutesSinceLast
                )
            }

            profile?.weeklyDrinkLimit?.takeIf { it > 0 }?.let { limit ->
                item { WeeklyLimitCard(used = weekCount, limit = limit) }
            }

            if (container != null) {
                item {
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

            // 7. Drink History Section
            item {
                SectionLabel("Getränke dieser Session (${drinks.size})")
            }

            if (drinks.isEmpty()) {
                item {
                    EmptyDrinkHint(onAdd = { showQuickAdd = true })
                }
            } else {
                items(drinks.reversed(), key = { it.id }) { drink ->
                    val timeStr = timeFormatter.format(Instant.ofEpochSecond(drink.timestampEpochSeconds))
                    PromilleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingDrink = drink }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = drink.name,
                                    color = AppColors.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$timeStr Uhr · ${String.format(Locale.GERMANY, "%.0f ml · %.1f%% · %d kcal", drink.volumeML, drink.abv, drink.calories)}",
                                    color = AppColors.textDim,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "✎",
                                color = AppColors.textMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button (FAB) for QuickAdd
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            PromilleFAB(
                text = "Drink hinzufügen",
                onClick = { showQuickAdd = true }
            )
        }

        unlockToast?.let { title ->
            AchievementToast(
                title = title,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            )
        }

        // Floating Undo Snackbar
        AnimatedVisibility(
            visible = undoAction != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp, start = 20.dp, end = 20.dp)
        ) {
            if (undoAction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = undoAction!!.label,
                            color = AppColors.text,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Rückgängig",
                            color = AppColors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { viewModel.performUndo() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(
    icon: String,
    title: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    PromilleCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = AppColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
