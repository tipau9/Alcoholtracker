package de.tipau.promille.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.BacStatus
import de.tipau.promille.repository.DayNoteRepository
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.DayStats
import de.tipau.promille.ui.viewmodels.HistoryViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEKDAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    dayNoteRepository: DayNoteRepository,
    drinkRepository: DrinkRepository? = null,
    userProfileRepository: UserProfileRepository? = null,
    modifier: Modifier = Modifier
) {
    val visibleMonth by viewModel.visibleMonth.collectAsState()
    val monthStats by viewModel.monthStats.collectAsState()

    var selectedDayStats by remember { mutableStateOf<DayStats?>(null) }
    var showTrends by remember { mutableStateOf(false) }

    val allDrinks by (drinkRepository?.getAllDrinksSorted() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    val profileEntity by (userProfileRepository?.profile ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsState(initial = null)

    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
    }

    if (showTrends) {
        TrendsView(
            drinks = allDrinks.map { DrinkRepository.toDomainDrink(it) },
            profile = profileEntity?.let { UserProfileRepository.toProfile(it) },
            onDismiss = { showTrends = false }
        )
    }

    if (selectedDayStats != null) {
        DayDetailSheet(
            dayStats = selectedDayStats!!,
            dayNoteRepository = dayNoteRepository,
            onDismiss = { selectedDayStats = null }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Selector Header & Trends Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, CircleShape)
                            .clickable { viewModel.previousMonth() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", color = AppColors.text, fontSize = 20.sp)
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = visibleMonth.format(monthFormatter),
                        color = AppColors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, CircleShape)
                            .clickable { viewModel.nextMonth() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("›", color = AppColors.text, fontSize = 20.sp)
                    }
                }

                Button(
                    onClick = { showTrends = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.card, contentColor = AppColors.accent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("📊 Trends", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Calendar Weekdays Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                WEEKDAY_LABELS.forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }

        // Calendar Month Grid
        item {
            val daysInMonth = visibleMonth.lengthOfMonth()
            val firstDayOfMonth = visibleMonth.atDay(1).dayOfWeek.value // 1 (Mo) - 7 (So)
            val totalCells = ((firstDayOfMonth - 1) + daysInMonth + 6) / 7 * 7

            PromilleCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - (firstDayOfMonth - 1) + 1

                                if (dayNum in 1..daysInMonth) {
                                    val date = visibleMonth.atDay(dayNum)
                                    val stats = monthStats.days[date]
                                    val hadAlcohol = stats?.hadAlcohol == true

                                    val cellColor = if (hadAlcohol) {
                                        if (stats.drinkCount >= 4) AppColors.statusOrange
                                        else AppColors.statusYellow
                                    } else {
                                        Color.Transparent
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (hadAlcohol) cellColor.copy(alpha = 0.2f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (hadAlcohol) cellColor.copy(alpha = 0.5f)
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                selectedDayStats = stats ?: DayStats(date = date, drinks = emptyList())
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (hadAlcohol) cellColor else AppColors.text,
                                                fontSize = 13.sp,
                                                fontWeight = if (hadAlcohol) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (hadAlcohol) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(cellColor)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.size(36.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Monthly Summary Hero Card
        item {
            PromilleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Trinktage", color = AppColors.textDim, fontSize = 12.sp)
                        Text("${monthStats.drinkDays}", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Tage", color = AppColors.textMuted, fontSize = 11.sp)
                    }

                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(AppColors.border))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Getränke", color = AppColors.textDim, fontSize = 12.sp)
                        Text("${monthStats.totalDrinks}", color = AppColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("gesamt", color = AppColors.textMuted, fontSize = 11.sp)
                    }

                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(AppColors.border))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kalorien", color = AppColors.textDim, fontSize = 12.sp)
                        Text("${monthStats.totalCalories}", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("kcal", color = AppColors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // List of days with logged drinks
        item {
            SectionLabel("Tagesdetails (${monthStats.days.size} Tage)")
        }

        val sortedDays = monthStats.days.values.sortedByDescending { it.date }

        if (sortedDays.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine Einträge in diesem Monat",
                        color = AppColors.textMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(sortedDays, key = { it.date.toString() }) { day ->
                val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
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
                            Text(
                                text = day.date.format(dayFormatter),
                                color = AppColors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${day.drinkCount} Getränke · ${String.format(Locale.GERMANY, "%.1f g", day.totalAlcoholGrams)} Alkohol · ${day.totalCalories} kcal",
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                        Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
