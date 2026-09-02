package de.tipau.promille.ui.screens.home

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.*
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.ui.components.DrinkIconView
import de.tipau.promille.ui.components.DurationChipRow
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1:1 Port of DrinkEditSheet.swift.
 * Full drink editing sheet with volume validation, mix details, timestamp edit,
 * live BAC impact preview, duration chip row, and delete confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkEditSheet(
    drink: Drink,
    profileEntity: UserProfileEntity? = null,
    onDismiss: () -> Unit,
    onSave: (volume: Double, timestampSeconds: Long, durationMinutes: Double) -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onFinishNow: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var volumeText by remember { mutableStateOf(drink.volumeML.roundToInt().toString()) }
    var durationMinutes by remember { mutableStateOf(drink.drinkDurationMinutes.coerceAtLeast(0.0)) }
    var timestampSeconds by remember { mutableStateOf(drink.timestampEpochSeconds) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val volume = volumeText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val isValid = volume > 0.0 && volume <= 3000.0

    val profile = remember(profileEntity) {
        profileEntity?.let { UserProfileRepository.toProfile(it) } ?: Profile.DEFAULT
    }

    val autoDurationMinutes = remember(drink.category, volume) {
        DrinkDurationEstimator.estimate(drink.category, volume)
    }

    val effectiveDurationMinutes = remember(durationMinutes, autoDurationMinutes) {
        if (durationMinutes > 0.0) durationMinutes else autoDurationMinutes
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }

    val startTimeStr = remember(timestampSeconds) {
        timeFormatter.format(Instant.ofEpochSecond(timestampSeconds))
    }

    val estimatedEndTimeStr = remember(timestampSeconds, effectiveDurationMinutes) {
        val endSec = timestampSeconds + (effectiveDurationMinutes * 60).toLong()
        timeFormatter.format(Instant.ofEpochSecond(endSec))
    }

    val absorptionEndTimeStr = remember(drink.category, volume, durationMinutes, timestampSeconds, profile) {
        val windowMin = BacCalculator.absorptionWindowMinutes(
            category = drink.category,
            drinkDurationMinutes = if (effectiveDurationMinutes > 0.0) effectiveDurationMinutes else 15.0,
            gastricMinutes = profile.defaultStomachStatus.absorptionMinutes
        )
        val endSec = timestampSeconds + (windowMin * 60).toLong()
        timeFormatter.format(Instant.ofEpochSecond(endSec))
    }

    val bacContribution = remember(volume, drink.abv, drink.category, durationMinutes, profile) {
        if (!isValid) null
        else {
            val input = DrinkInput(
                offsetMinutes = 0.0,
                volumeML = volume,
                abv = drink.abv,
                category = drink.category,
                drinkDurationMinutes = if (effectiveDurationMinutes > 0.0) effectiveDurationMinutes else 15.0
            )
            BacCalculator.projectedPeak(
                drink = input,
                profile = profile,
                stomach = profile.defaultStomachStatus,
                conservative = profile.conservativeForApp
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            DrinkIconView(
                                drink = drink,
                                tint = AppColors.accent,
                                size = 20.dp
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                // iOS: .appBodyBold (SemiBold, not Bold).
                                text = drink.name,
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            Text(
                                text = "${String.format(Locale.GERMANY, "%.1f", drink.abv)} % Alk.",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                            if (drink.mixerVolumeML > 0.0) {
                                val spiritML = (drink.volumeML - drink.mixerVolumeML).roundToInt().coerceAtLeast(0)
                                val mixerML = drink.mixerVolumeML.roundToInt()
                                Text(
                                    text = "Spirituose $spiritML ml, Mixer $mixerML ml",
                                    color = AppColors.textMuted,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }
                        }
                    }
                    de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
                }

                // Volume Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "MENGE")
                    de.tipau.promille.ui.components.AppTextField(
                        value = volumeText,
                        onValueChange = { volumeText = it },
                        placeholder = "ml",
                        trailingIcon = { Text("ml", color = AppColors.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Mix details card if cocktail/mixed
                if (drink.mixerVolumeML > 0.0) {
                    val spiritML = (drink.volumeML - drink.mixerVolumeML).roundToInt().coerceAtLeast(0)
                    val mixerML = drink.mixerVolumeML.roundToInt()
                    val spiritPct = if (drink.volumeML > 0) ((drink.volumeML - drink.mixerVolumeML) / drink.volumeML * 100).roundToInt() else 0

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(text = "MIX-DETAILS")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // iOS DESStat: value .appCaptionBold (SemiBold,
                            // not Bold), label .appMicro.
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("$spiritML ml", color = AppColors.accent, style = de.tipau.promille.AppText.captionBold)
                                Text("Spirituose", color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
                            }
                            Box(modifier = Modifier.width(0.5.dp).height(32.dp).background(AppColors.border))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("$mixerML ml", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                                Text("Mixer", color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
                            }
                            Box(modifier = Modifier.width(0.5.dp).height(32.dp).background(AppColors.border))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("$spiritPct %", color = AppColors.accent, style = de.tipau.promille.AppText.captionBold)
                                Text("Spi.-Anteil", color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
                            }
                        }
                    }
                }

                // Duration Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "TRINKDAUER")
                    DurationChipRow(
                        durationMinutes = durationMinutes,
                        estimatedMinutes = autoDurationMinutes,
                        onDurationMinutesChange = { durationMinutes = it }
                    )
                    Text(
                        text = "Start $startTimeStr · fertig ca. $estimatedEndTimeStr",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                    Text(
                        text = "Aufnahme ca. bis $absorptionEndTimeStr",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                }

                // Timestamp Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "UHRZEIT")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .clickable {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = timestampSeconds * 1000
                                }
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val zdt = Instant.ofEpochSecond(timestampSeconds)
                                            .atZone(ZoneId.systemDefault())
                                            .withHour(hourOfDay)
                                            .withMinute(minute)
                                        timestampSeconds = zdt.toEpochSecond()
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // iOS: .appBody / .appBodyBold (SemiBold, not Bold).
                        Text("Startzeit", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
                        Text(startTimeStr, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                    }
                }

                // Estimated BAC Contribution Preview
                if (bacContribution != null && bacContribution > 0.0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Geschätzte Wirkung", color = AppColors.textDim, style = de.tipau.promille.AppText.caption, modifier = Modifier.weight(1f))
                        Text(
                            // iOS: .appCaptionBold (SemiBold, not Bold).
                            text = "+${String.format(Locale.GERMANY, "%.2f", bacContribution)} ‰",
                            color = AppColors.statusOrange,
                            style = de.tipau.promille.AppText.captionBold
                        )
                    }
                }

                // Save Button
                PrimaryButton(
                    text = "Speichern",
                    enabled = isValid,
                    onClick = {
                        onSave(volume, timestampSeconds, durationMinutes)
                        onDismiss()
                    }
                )

                // Delete Button
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.statusRed.copy(alpha = 0.10f),
                        contentColor = AppColors.statusRed
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, AppColors.statusRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                        // iOS: .appBodyBold (17sp SemiBold) - was 16sp Bold.
                        Text("Drink entfernen", style = de.tipau.promille.AppText.bodyBold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Drink wirklich entfernen?", color = AppColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Dieser Eintrag wird dauerhaft aus deiner Session und der Historie gelöscht.", color = AppColors.textDim) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    }
                ) {
                    Text("Entfernen", color = AppColors.statusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            },
            containerColor = AppColors.card,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

