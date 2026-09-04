package de.tipau.promille.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.color
import de.tipau.promille.repository.DayNoteRepository
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.DayStats
import de.tipau.promille.ui.viewmodels.HistoryViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import de.tipau.promille.AppSerif

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    dayNoteRepository: DayNoteRepository,
    drinkRepository: DrinkRepository? = null,
    userProfileRepository: UserProfileRepository? = null,
    supabase: de.tipau.promille.network.SupabaseService? = null,
    modifier: Modifier = Modifier
) {
    val visibleMonth by viewModel.visibleMonth.collectAsState()
    val monthStats by viewModel.monthStats.collectAsState()
    val statusSkin by viewModel.statusSkin.collectAsState()

    var selectedDayStats by remember { mutableStateOf<DayStats?>(null) }
    var showTrends by remember { mutableStateOf(false) }

    val allDrinks by (drinkRepository?.getAllDrinksSorted() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    val allNotes by dayNoteRepository.getNotesBetween("0000-01-01", "9999-12-31")
        .collectAsState(initial = emptyList())
    val profileEntity by (userProfileRepository?.profile ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)
    val profile = remember(profileEntity) {
        profileEntity?.let { UserProfileRepository.toProfile(it) }
    }

    val currentMonth = remember { YearMonth.now() }
    val isCurrentMonth = visibleMonth >= currentMonth

    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
    }

    if (showTrends) {
        TrendsView(
            drinks = allDrinks.map { DrinkRepository.toDomainDrink(it) },
            notes = allNotes.map {
                de.tipau.promille.bac.DayNote(
                    day = LocalDate.parse(it.day),
                    text = it.text,
                    mood = de.tipau.promille.bac.DayMood.from(it.moodRaw)
                )
            },
            profile = profile,
            supabase = supabase,
            onDismiss = { showTrends = false }
        )
    }

    if (selectedDayStats != null) {
        DayDetailSheet(
            dayStats = selectedDayStats!!,
            dayNoteRepository = dayNoteRepository,
            profile = profile,
            statusSkin = statusSkin,
            onDeleteDrink = { viewModel.deleteDrink(it) },
            onDismiss = { selectedDayStats = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // HVTopBar (matches iOS HistoryView.swift 1:1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // iOS: .appHeadline (HistoryView.swift:145).
                Text(
                    text = "Verlauf",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.headline
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.12f))
                        .clickable { showTrends = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = AppIcons.Chart,
                        contentDescription = "Trends",
                        tint = AppColors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isCurrentMonth) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.accent.copy(alpha = 0.12f))
                            .clickable { viewModel.goToCurrentMonth() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // iOS: .appMicro (HistoryView.swift:166).
                        Text(
                            text = "Heute",
                            color = AppColors.accent,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable { viewModel.previousMonth() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = AppColors.textDim, fontSize = 18.sp)
                }

                // iOS: .appCaptionBold (HistoryView.swift:189).
                Text(
                    text = visibleMonth.format(monthFormatter),
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.captionBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 90.dp)
                        .padding(horizontal = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable(enabled = !isCurrentMonth) { viewModel.nextMonth() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "›",
                        color = if (!isCurrentMonth) AppColors.textDim else AppColors.border,
                        fontSize = 18.sp
                    )
                }
            }
        }

        HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Weekday Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    WEEKDAY_LABELS.forEach { label ->
                        Text(
                            text = label,
                            color = AppColors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Calendar Grid
            item {
                val daysInMonth = visibleMonth.lengthOfMonth()
                val firstDayOfMonth = visibleMonth.atDay(1).dayOfWeek.value // 1 (Mo) .. 7 (So)
                val totalCells = ((firstDayOfMonth - 1) + daysInMonth + 6) / 7 * 7
                val today = LocalDate.now()

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (row in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - (firstDayOfMonth - 1) + 1

                                if (dayNum in 1..daysInMonth) {
                                    val date = visibleMonth.atDay(dayNum)
                                    val isToday = date == today
                                    val isFuture = date > today
                                    val stats = monthStats.days[date]
                                    val hadAlcohol = stats?.hadAlcohol == true
                                    val dayStatus = stats?.bacStatus(profile) ?: BacStatus.SOBER

                                    val cellColor = if (!isFuture && hadAlcohol) {
                                        dayStatus.color.copy(alpha = 0.30f)
                                    } else {
                                        AppColors.card
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cellColor)
                                            .border(
                                                if (isToday) 1.5.dp else 0.5.dp,
                                                if (isToday) AppColors.accent else AppColors.border.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable(enabled = !isFuture) {
                                                selectedDayStats = stats ?: DayStats(date = date, drinks = emptyList())
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                color = when {
                                                    isFuture -> AppColors.textMuted.copy(alpha = 0.4f)
                                                    isToday -> AppColors.accent
                                                    else -> AppColors.text
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (hadAlcohol && !isFuture) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(AppColors.text.copy(alpha = 0.4f))
                                                )
                                            } else {
                                                Spacer(Modifier.size(4.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }

            // HVMonthSummary (matches iOS HistoryView.swift 1:1)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(text = "MONATS-ÜBERSICHT")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tile 1: Tage
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = AppIcons.Calendar,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                // iOS HVSummaryTile: value is .appBodyBold.monospacedDigit(), label is .appMicro
                                Text(
                                    text = "${monthStats.drinkDays}",
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                                )
                                Text(
                                    text = "Tage",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(0.5.dp)
                                    .height(36.dp)
                                    .background(AppColors.border)
                            )

                            // Tile 2: Drinks
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = AppIcons.Drink,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${monthStats.totalDrinks}",
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                                )
                                Text(
                                    text = "Drinks",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(0.5.dp)
                                    .height(36.dp)
                                    .background(AppColors.border)
                            )

                            // Tile 3: Kalorien
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = AppIcons.Fire,
                                    contentDescription = null,
                                    tint = AppColors.statusOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${monthStats.totalCalories}",
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                                )
                                Text(
                                    text = "Kalorien",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }
                        }
                    }
                }
            }

            // Vormonats-Trend (iOS HistoryView.swift:344-389). Only shown once
            // there is something to compare against.
            monthStats.trend?.let { trend ->
                item {
                    val diff = monthStats.totalDrinks - trend.previousTotalDrinks
                    val pct = (kotlin.math.abs(diff).toDouble() /
                        trend.previousTotalDrinks * 100).roundToInt()
                    val tint = when {
                        diff < 0 -> AppColors.statusGreen
                        diff > 0 -> AppColors.statusOrange
                        else -> AppColors.textDim
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = if (diff == 0) AppIcons.Equal else AppIcons.ArrowDown,
                                contentDescription = null,
                                tint = tint,
                                // No arrow-up asset; the down one flipped is the same glyph.
                                modifier = Modifier
                                    .size(12.dp)
                                    .then(if (diff > 0) Modifier.rotate(180f) else Modifier)
                            )
                            Text(
                                text = when {
                                    diff < 0 -> "$pct% weniger Drinks als im Vormonat"
                                    diff > 0 -> "$pct% mehr Drinks als im Vormonat"
                                    else -> "Gleich viele Drinks wie im Vormonat"
                                },
                                color = tint,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                        trend.limitedToDays?.let { days ->
                            Text(
                                text = "Vergleich: jeweils die ersten $days Tage",
                                color = AppColors.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Legend: 1:1 match with iOS HistoryView.swift:108-129, a 3-column
            // LazyVGrid at spacing 8. One flat Row cannot hold five German band
            // names at 11sp, which is why "Gefährlich" used to fall off screen.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val bands = listOf(
                        BacStatus.SOBER, BacStatus.TIPSY, BacStatus.DRUNK,
                        BacStatus.CAREFUL, BacStatus.DANGER
                    )
                    bands.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { st ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(st.color.copy(alpha = 0.30f))
                                            .border(0.5.dp, st.color, CircleShape)
                                    )
                                    // iOS: .appMicro (HistoryView.swift:122).
                                    Text(
                                        text = st.label(statusSkin),
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.micro,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // List of days with logged drinks
            val sortedDays = monthStats.days.values.sortedByDescending { it.date }
            if (sortedDays.isNotEmpty()) {
                item {
                    SectionLabel("Tagesdetails (${sortedDays.size} Tage)")
                }
                items(sortedDays, key = { it.date.toString() }) { day ->
                    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
                    val status = day.bacStatus(profile)
                    val statusLabel = status.label(statusSkin)

                    PromilleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDayStats = day }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = day.date.format(dayFormatter),
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.bodyBold
                                    )
                                    if (day.hadAlcohol) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(status.color.copy(alpha = 0.15f))
                                                .border(0.5.dp, status.color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = status.color,
                                                style = de.tipau.promille.AppText.micro
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${day.drinkCount} Getränke · ${String.format(Locale.GERMAN, "%.1f g", day.totalAlcoholGrams)} Alkohol · ${day.totalCalories} kcal",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.caption
                                )
                            }
                            Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}
