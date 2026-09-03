package de.tipau.promille.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.tipau.promille.AppColors
import de.tipau.promille.AppSerif
import de.tipau.promille.bac.BacCalculator
import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.Profile
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.TimeWheelPicker
import de.tipau.promille.ui.components.rememberHapticManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ForecastView(
    drinks: List<Drink>,
    profile: Profile,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticManager()

    var targetDateTime by remember {
        mutableStateOf(LocalDateTime.now().plusHours(3))
    }
    var showTimeDialog by remember { mutableStateOf(false) }

    var targetBac by remember(profile.isProbationaryDriver) {
        mutableStateOf(if (profile.isProbationaryDriver) 0.0 else profile.drivingLimit)
    }

    val now = remember { System.currentTimeMillis() / 1000 }

    val targetEpochSeconds = remember(targetDateTime) {
        targetDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
    }

    val targetHoursAhead = remember(targetDateTime) {
        val sec = ChronoUnit.SECONDS.between(LocalDateTime.now(), targetDateTime)
        (sec / 3600.0).toFloat().coerceAtLeast(0.1f)
    }

    val targetTimeStr = remember(targetDateTime) {
        val isToday = targetDateTime.toLocalDate() == LocalDate.now()
        val isTomorrow = targetDateTime.toLocalDate() == LocalDate.now().plusDays(1)
        val dayPrefix = when {
            isToday -> "Heute"
            isTomorrow -> "Morgen"
            else -> targetDateTime.format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN))
        }
        val timeStr = targetDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
        "$dayPrefix, $timeStr Uhr"
    }

    val projection = remember(drinks, profile) {
        BacProjectionInput(
            drinks = drinks,
            profile = profile,
            stomachStatus = profile.defaultStomachStatus,
            conservative = profile.conservativeForSafety
        )
    }

    val elimRate = profile.resolvedEliminationRate(profile.conservativeForSafety)
    val hoursUntilTarget = targetHoursAhead.toDouble()

    val projectedBacAtTarget = remember(projection, targetEpochSeconds) {
        projection.currentBac(targetEpochSeconds)
    }

    val singleBeerBac = remember(profile) {
        BacCalculator.bacContribution(
            volumeML = 330.0,
            abv = 5.0,
            weightKg = profile.weightKg,
            distributionFactor = profile.distributionFactor
        ).coerceAtLeast(0.01)
    }

    val currentBacNow = remember(projection, now) {
        projection.currentBac(now)
    }

    val hoursToClearCurrent = remember(projection, now, currentBacNow, elimRate) {
        if (currentBacNow > 0.005) {
            projection.hoursUntil(0.005, now) ?: (currentBacNow / elimRate)
        } else 0.0
    }

    val isAlreadyOverLimit = projectedBacAtTarget >= targetBac + 0.005

    val (allowedDrinks, eliminationCapacity) = remember(
        targetBac, projectedBacAtTarget, hoursUntilTarget, hoursToClearCurrent, elimRate, singleBeerBac, isAlreadyOverLimit
    ) {
        if (isAlreadyOverLimit) {
            0 to 0.0
        } else if (hoursToClearCurrent >= hoursUntilTarget) {
            // Existing drinks occupy liver past the target time; only direct headroom at target time fits
            val roomAtTarget = (targetBac - projectedBacAtTarget).coerceAtLeast(0.0)
            val count = (roomAtTarget / singleBeerBac).toInt()
            count to 0.0
        } else {
            // Existing drinks clear before target; free hours are available to eliminate new drinks
            val freeHours = (hoursUntilTarget - hoursToClearCurrent).coerceAtLeast(0.0)
            // Leave a 45 min absorption/lag buffer for new drinks
            val effectiveElimHours = (freeHours - 0.75).coerceAtLeast(0.0)
            val elimCap = effectiveElimHours * elimRate
            val roomAtTarget = (targetBac - projectedBacAtTarget).coerceAtLeast(0.0)
            val totalAllowed = elimCap + roomAtTarget
            val count = (totalAllowed / singleBeerBac).toInt().coerceAtLeast(0)
            count to elimCap
        }
    }

    val planningTargets = remember(profile.isProbationaryDriver, profile.drivingLimit) {
        if (profile.isProbationaryDriver || profile.drivingLimit <= 0.0) {
            listOf(0.0 to "Unter 0,0 ‰")
        } else {
            listOf(
                0.0 to "Nüchtern",
                profile.drivingLimit to String.format(Locale.GERMAN, "Unter %.1f ‰", profile.drivingLimit)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppIcons.History,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            // iOS: .appCaptionBold (ForecastView.swift:122).
            Text(
                text = "Vorausschau",
                color = AppColors.accent,
                style = de.tipau.promille.AppText.captionBold
            )
            Spacer(Modifier.weight(1f))
            if (profile.conservativeForSafety) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.statusOrange)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    // iOS: .appMicro (ForecastView.swift:127).
                    Text(
                        text = "WORST-CASE",
                        color = AppColors.background,
                        style = de.tipau.promille.AppText.micro,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.border.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Time Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WANN MUSST DU FIT SEIN?",
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.micro,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = String.format(Locale.GERMANY, "%s (in %.1f h)", targetTimeStr, targetHoursAhead),
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.captionBold
                        )
                    }

                    // Custom Time Picker Pill Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.accent.copy(alpha = 0.12f))
                            .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.selection()
                                showTimeDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Uhrzeit",
                                color = AppColors.accent,
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                    }
                }

                // Quick Preset Chips (Horizontally Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = remember {
                        val n = LocalDateTime.now()
                        listOf(
                            "+3h" to n.plusHours(3),
                            "+6h" to n.plusHours(6),
                            "+8h" to n.plusHours(8),
                            "Morgen 07:00" to LocalDate.now().plusDays(1).atTime(7, 0),
                            "Morgen 08:30" to LocalDate.now().plusDays(1).atTime(8, 30),
                            "Morgen 10:00" to LocalDate.now().plusDays(1).atTime(10, 0),
                            "Morgen 12:00" to LocalDate.now().plusDays(1).atTime(12, 0)
                        )
                    }

                    presets.forEach { (label, presetTime) ->
                        val isPresetActive = ChronoUnit.MINUTES.between(targetDateTime, presetTime) in -10..10
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isPresetActive) AppColors.accent else AppColors.background)
                                .border(
                                    0.5.dp,
                                    if (isPresetActive) AppColors.accent else AppColors.border,
                                    CircleShape
                                )
                                .clickable {
                                    haptics.selection()
                                    targetDateTime = presetTime
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isPresetActive) AppColors.background else AppColors.textDim,
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                    }
                }

                // Smooth Fluid Slider across up to 24 hours
                de.tipau.promille.ui.components.AppSlider(
                    value = targetHoursAhead.coerceIn(0.5f, 24f),
                    onValueChange = {
                        targetDateTime = LocalDateTime.now().plusMinutes((it * 60).toLong())
                    },
                    valueRange = 0.5f..24f
                )
            }

            // Target BAC Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GRENZWERT",
                    color = AppColors.textMuted,
                    style = de.tipau.promille.AppText.micro,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    planningTargets.forEach { (limit, label) ->
                        val isSelected = kotlin.math.abs(targetBac - limit) < 0.05
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) AppColors.accent else AppColors.background)
                                .border(
                                    0.5.dp,
                                    if (isSelected) AppColors.accent else AppColors.border,
                                    CircleShape
                                )
                                .clickable {
                                    haptics.selection()
                                    targetBac = limit
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AppColors.background else AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                    }
                }
            }

            // Result Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.background)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                if (isAlreadyOverLimit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = null,
                            tint = AppColors.statusRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Besser nichts mehr trinken",
                                color = AppColors.statusRed,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            Text(
                                text = String.format(Locale.GERMANY, "Ziel-BAC (%.2f ‰) bis %s bereits überschritten", projectedBacAtTarget, targetTimeStr),
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$allowedDrinks",
                                color = if (allowedDrinks > 0) AppColors.accent else AppColors.textDim,
                                fontSize = 52.sp,
                                fontFamily = AppSerif,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = "noch möglich",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.GERMANY,
                                "Standarddrinks · je ~%.2f ‰ Budget (konservativ)",
                                singleBeerBac
                            ),
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.micro
                        )
                        if (eliminationCapacity > 0.05) {
                            Text(
                                text = String.format(
                                    Locale.GERMANY,
                                    "Abbaukapazität bis dahin: ~%.2f ‰ (Ziel: %.1f ‰)",
                                    eliminationCapacity,
                                    targetBac
                                ),
                                color = AppColors.accent.copy(alpha = 0.9f),
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                    }
                }
            }
        }
    }

    // Custom Target Time Selection Dialog
    if (showTimeDialog) {
        var selectedDayOffset by remember {
            mutableStateOf(
                ChronoUnit.DAYS.between(LocalDate.now(), targetDateTime.toLocalDate()).toInt().coerceIn(0, 2)
            )
        }
        var selectedHour by remember { mutableStateOf(targetDateTime.hour) }
        var selectedMinute by remember { mutableStateOf(targetDateTime.minute) }

        Dialog(
            onDismissRequest = { showTimeDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wann musst du fit sein?",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline
                    )

                    // Day selection: Heute / Morgen / Übermorgen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Heute",
                            1 to "Morgen",
                            2 to "Übermorgen"
                        ).forEach { (offset, dayLabel) ->
                            val isSel = selectedDayOffset == offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) AppColors.accent else AppColors.background)
                                    .border(
                                        0.5.dp,
                                        if (isSel) AppColors.accent else AppColors.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.selection()
                                        selectedDayOffset = offset
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayLabel,
                                    color = if (isSel) AppColors.background else AppColors.textDim,
                                    style = de.tipau.promille.AppText.captionBold
                                )
                            }
                        }
                    }

                    // Smooth Interactive Time Wheel Picker
                    TimeWheelPicker(
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        }
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.background)
                                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                                .clickable { showTimeDialog = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Abbrechen",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.accent)
                                .clickable {
                                    val newDate = LocalDate.now().plusDays(selectedDayOffset.toLong())
                                    val candidate = newDate.atTime(selectedHour, selectedMinute)
                                    targetDateTime = if (candidate.isBefore(LocalDateTime.now())) {
                                        // If selected time in the past for today, bump to tomorrow
                                        candidate.plusDays(1)
                                    } else {
                                        candidate
                                    }
                                    haptics.success()
                                    showTimeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Fertig",
                                color = AppColors.background,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                        }
                    }
                }
            }
        }
    }
}
