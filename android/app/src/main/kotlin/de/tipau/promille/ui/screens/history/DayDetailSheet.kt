package de.tipau.promille.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import de.tipau.promille.data.DayNoteEntity
import de.tipau.promille.repository.DayNoteRepository
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.DayStats
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DayMoodOption(val raw: Int, val emoji: String, val label: String) {
    NEUTRAL(0, "😐", "Kein Urteil"),
    HAPPY(1, "😄", "Guter Abend"),
    PROUD(2, "💪", "Gut gemacht"),
    REGRET(3, "😬", "Lieber nicht"),
    TERRIBLE(4, "🤢", "War zu viel")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    dayStats: DayStats,
    dayNoteRepository: DayNoteRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dateString = dayStats.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val displayDate = dayStats.date.format(
        DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
    )

    var noteText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(DayMoodOption.NEUTRAL) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(dateString) {
        val existing = dayNoteRepository.getNoteForDay(dateString)
        if (existing != null) {
            noteText = existing.text
            selectedMood = DayMoodOption.entries.find { it.raw == existing.moodRaw } ?: DayMoodOption.NEUTRAL
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = displayDate,
                            color = AppColors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${dayStats.drinkCount} Getränke erfasst",
                            color = AppColors.textDim,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = AppColors.textDim, fontSize = 14.sp)
                    }
                }
            }

            // Overview Stats Card
            item {
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Alkohol", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                String.format(Locale.GERMANY, "%.1f g", dayStats.totalAlcoholGrams),
                                color = AppColors.accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kalorien", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                "${dayStats.totalCalories} kcal",
                                color = AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Status", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                if (dayStats.hadAlcohol) "Alkohol" else "Nüchtern",
                                color = if (dayStats.hadAlcohol) AppColors.statusYellow else AppColors.statusGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Stimmungsauswahl
            item {
                SectionLabel("Stimmung am Tag danach")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayMoodOption.entries.forEach { option ->
                        val isSelected = selectedMood == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                                .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(12.dp))
                                .clickable { selectedMood = option }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option.emoji, fontSize = 20.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = option.label,
                                    color = if (isSelected) AppColors.accent else AppColors.textDim,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Notizfeld
            item {
                SectionLabel("Persönliche Notiz")
                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        noteText = it
                        isSaved = false
                    },
                    placeholder = { Text("Wie war der Abend? Wer war dabei?", color = AppColors.textMuted, fontSize = 13.sp) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border,
                        cursorColor = AppColors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = if (isSaved) "✓ Gespeichert" else "Notiz speichern",
                    onClick = {
                        coroutineScope.launch {
                            dayNoteRepository.update(
                                DayNoteEntity(
                                    day = dateString,
                                    text = noteText.trim(),
                                    moodRaw = selectedMood.raw
                                )
                            )
                            isSaved = true
                        }
                    }
                )
            }

            // Drinks List of this day
            item {
                SectionLabel("Getränkeliste")
            }

            if (dayStats.drinks.isEmpty()) {
                item {
                    Text("Keine Getränke erfasst", color = AppColors.textMuted, fontSize = 13.sp)
                }
            } else {
                items(dayStats.drinks) { drink ->
                    PromilleCard {
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
                                    text = String.format(
                                        Locale.GERMANY,
                                        "%.0f ml · %.1f%% · %d kcal · %.1f g Alkohol",
                                        drink.volumeML,
                                        drink.abv,
                                        drink.calories,
                                        drink.alcoholGrams
                                    ),
                                    color = AppColors.textDim,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
