package de.tipau.promille.ui.screens.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.R
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.HangoverLevel
import de.tipau.promille.bac.HangoverPredictor
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.color
import de.tipau.promille.data.DayNoteEntity
import de.tipau.promille.repository.DayNoteRepository
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.StatusPill
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.DayStats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DayMoodOption(val raw: Int, val emoji: String, val label: String, val iconRes: Int) {
    NEUTRAL(0, "😐", "Kein Urteil", R.drawable.ic_mood_neutral),
    HAPPY(1, "😄", "Guter Abend", R.drawable.ic_mood_happy),
    PROUD(2, "💪", "Gut gemacht", R.drawable.ic_mood_proud),
    REGRET(3, "😬", "Lieber nicht", R.drawable.ic_mood_regret),
    TERRIBLE(4, "🤢", "War zu viel", R.drawable.ic_mood_terrible)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    dayStats: DayStats,
    dayNoteRepository: DayNoteRepository,
    profile: Profile? = null,
    statusSkin: StatusSkin = StatusSkin.STANDARD,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dateString = dayStats.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val isToday = dayStats.date == LocalDate.now()

    val dateTitle = remember(dayStats.date) {
        dayStats.date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))
    }
    val subtitle = remember(dayStats.date, isToday) {
        if (isToday) "Heute" else dayStats.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
    }

    var noteText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(DayMoodOption.NEUTRAL) }

    LaunchedEffect(dateString) {
        val existing = dayNoteRepository.getNoteForDay(dateString)
        if (existing != null) {
            noteText = existing.text
            selectedMood = DayMoodOption.entries.find { it.raw == existing.moodRaw } ?: DayMoodOption.NEUTRAL
        }
    }

    fun saveNote() {
        val trimmed = noteText.trim()
        coroutineScope.launch {
            dayNoteRepository.saveNote(
                day = dateString,
                text = trimmed,
                moodRaw = selectedMood.raw
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            saveNote()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp, top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Row (Matches iOS DayDetailSheet.swift 1:1)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            // iOS: .appHeadline (DayDetailSheet.swift:118) -
                            // sans, not the AppSerif/Bold this used to be.
                            text = dateTitle,
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.headline
                        )
                        Text(
                            text = subtitle,
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .clickable {
                                saveNote()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                            tint = AppColors.textDim,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            item {
                HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)
            }

            // Drinks or Empty State
            if (dayStats.drinks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Moon,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Kein Alkohol an diesem Tag.",
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                }
            } else {
                // Summary Stats Card (Matches iOS 1:1)
                item {
                    val peakBAC = remember(dayStats, profile) { dayStats.peakBAC(profile) }
                    val bacStatus = remember(dayStats, profile) { dayStats.bacStatus(profile) }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("ZUSAMMENFASSUNG")
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
                                // Spitzen-BAC
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Drop,
                                        contentDescription = null,
                                        tint = bacStatus.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        // iOS DDSStat: value is .appBodyBold
                                        // (SemiBold, not the Bold this had).
                                        text = String.format(Locale.GERMANY, "%.2f ‰", peakBAC),
                                        color = bacStatus.color,
                                        style = de.tipau.promille.AppText.bodyBold
                                    )
                                    Text(
                                        text = "Spitzen-BAC",
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .height(40.dp)
                                        .background(AppColors.border)
                                )

                                // Drinks
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Drink,
                                        contentDescription = null,
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${dayStats.drinkCount}",
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.bodyBold
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
                                        .height(40.dp)
                                        .background(AppColors.border)
                                )

                                // Kalorien
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Fire,
                                        contentDescription = null,
                                        tint = AppColors.statusOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${dayStats.totalCalories}",
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.bodyBold
                                    )
                                    Text(
                                        text = "Kalorien",
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }
                            }
                        }

                        StatusPill(status = bacStatus, skin = statusSkin)
                    }
                }

                // Hangover Forecast Card matching iOS DayDetailSheet.swift:237-282
                item {
                    val level = remember(dayStats, profile) {
                        if (dayStats.drinks.isEmpty()) {
                            HangoverLevel.NONE
                        } else if (profile != null) {
                            HangoverPredictor.predict(
                                drinks = dayStats.drinks,
                                profile = profile,
                                conservative = profile.conservativeForApp
                            )
                        } else {
                            val peak = dayStats.peakBAC(null)
                            val timestamps = dayStats.drinks.map { it.timestampEpochSeconds }
                            val durationHours = if (timestamps.size > 1) {
                                (timestamps.maxOrNull()!! - timestamps.minOrNull()!!) / 3600.0
                            } else 0.5
                            HangoverPredictor.predict(
                                peakBAC = peak,
                                durationHours = durationHours,
                                waterGlasses = dayStats.drinks.size / 2.0,
                                drinksCount = dayStats.drinks.size
                            )
                        }
                    }

                    val levelColor = when (level) {
                        HangoverLevel.NONE -> AppColors.statusGreen
                        HangoverLevel.MILD -> AppColors.statusYellow
                        HangoverLevel.MODERATE -> AppColors.statusOrange
                        HangoverLevel.STRONG -> AppColors.statusRed
                        HangoverLevel.SEVERE -> AppColors.statusRed
                        HangoverLevel.LETHAL -> AppColors.statusDarkRed
                    }

                    val iconVector = when (level) {
                        HangoverLevel.NONE -> AppIcons.Check
                        HangoverLevel.MILD -> AppIcons.Moon
                        HangoverLevel.MODERATE, HangoverLevel.STRONG -> AppIcons.Info
                        HangoverLevel.SEVERE, HangoverLevel.LETHAL -> AppIcons.Car
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (level.isLethal) levelColor.copy(alpha = 0.10f) else AppColors.card)
                            .border(
                                if (level.isLethal) 1.dp else 0.5.dp,
                                if (level.isLethal) levelColor.copy(alpha = 0.5f) else AppColors.border,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(levelColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = levelColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Kater-Prognose",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro
                                )
                                Text(
                                    // iOS: .appCaptionBold (SemiBold, not Bold).
                                    text = level.germanLabel,
                                    color = if (level.isLethal) levelColor else AppColors.text,
                                    style = de.tipau.promille.AppText.captionBold
                                )
                                if (level.isLethal) {
                                    Text(
                                        text = "Solche Werte sind lebensgefährlich. Im Zweifel Notruf 112.",
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }
                            }
                        }
                    }
                }

                // Drink list
                item {
                    SectionLabel("GETRÄNKE")
                }
                items(dayStats.drinks) { drink ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(AppColors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Drink,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    // iOS: .appBody, no weight override - was
                                    // 15sp Medium here, neither matches.
                                    text = drink.name,
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.body
                                )
                                Text(
                                    text = String.format(
                                        Locale.GERMANY,
                                        "%.0f ml · %.1f %%",
                                        drink.volumeML,
                                        drink.abv
                                    ),
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }
                        }
                    }
                }
            }

            // Section NOTIZ (Matches iOS DayDetailSheet.swift:353-406 1:1)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("NOTIZ")

                    // 5 Mood evaluation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DayMoodOption.entries.forEach { mood ->
                            val isSelected = selectedMood == mood
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) AppColors.accent.copy(alpha = 0.12f) else AppColors.card)
                                    .border(
                                        if (isSelected) 1.5.dp else 0.5.dp,
                                        if (isSelected) AppColors.accent else AppColors.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedMood = mood
                                        saveNote()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = mood.iconRes),
                                        contentDescription = mood.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = mood.label,
                                        color = if (isSelected) AppColors.accent else AppColors.textMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    // Text field
                    de.tipau.promille.ui.components.AppTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            saveNote()
                        },
                        placeholder = "Kurze Notiz zum Abend...",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
}
