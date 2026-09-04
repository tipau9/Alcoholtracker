package de.tipau.promille.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacCalculator
import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.Profile
import de.tipau.promille.ui.components.AppIcons
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import de.tipau.promille.AppSerif

@Composable
fun ForecastView(
    drinks: List<Drink>,
    profile: Profile,
    modifier: Modifier = Modifier
) {
    var targetHoursAhead by remember { mutableStateOf(3f) }
    var targetBac by remember(profile.isProbationaryDriver) {
        mutableStateOf(if (profile.isProbationaryDriver) 0.0 else profile.drivingLimit)
    }

    val now = remember { System.currentTimeMillis() / 1000 }
    val targetEpochSeconds = now + (targetHoursAhead * 3600).toLong()

    val targetTimeStr = remember(targetHoursAhead) {
        val time = LocalTime.now().plusMinutes((targetHoursAhead * 60).toLong())
        time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
    }

    val projection = remember(drinks, profile) {
        BacProjectionInput(
            drinks = drinks,
            profile = profile,
            stomachStatus = profile.defaultStomachStatus,
            conservative = profile.conservativeForSafety
        )
    }

    val projectedBacAtTarget = remember(projection, targetEpochSeconds) {
        projection.currentBac(targetEpochSeconds)
    }

    val allowedAdditionalBac = (targetBac - projectedBacAtTarget).coerceAtLeast(0.0)

    val singleBeerBac = remember(profile) {
        BacCalculator.bacContribution(
            volumeML = 330.0,
            abv = 5.0,
            weightKg = profile.weightKg,
            distributionFactor = profile.distributionFactor
        ).coerceAtLeast(0.01)
    }

    val allowedDrinks = (allowedAdditionalBac / singleBeerBac).toInt()
    val isAlreadyOverLimit = projectedBacAtTarget >= targetBac

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
                painter = AppIcons.History,
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
            // Target Time Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // iOS: .appMicro.tracking(1) (ForecastView.swift:144).
                        Text(
                            text = "WANN MUSST DU FIT SEIN?",
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.micro,
                            letterSpacing = 1.sp
                        )
                        // iOS: .appCaption (ForecastView.swift:148).
                        Text(
                            text = String.format(Locale.GERMANY, "%.1f h ab jetzt (%s Uhr)", targetHoursAhead, targetTimeStr),
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                }
                de.tipau.promille.ui.components.AppSlider(
                    value = targetHoursAhead,
                    onValueChange = { targetHoursAhead = it },
                    valueRange = 0.5f..12f
                )
            }

            // Target BAC Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // iOS: .appMicro.tracking(1) (ForecastView.swift:174).
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
                                .clickable { targetBac = limit }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // iOS: .appCaption (ForecastView.swift:183).
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
                            painter = AppIcons.Close,
                            contentDescription = null,
                            tint = AppColors.statusRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // iOS: .appBodyBold (ForecastView.swift:210).
                            Text(
                                text = "Besser nichts mehr trinken",
                                color = AppColors.statusRed,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            // iOS: .appCaption (ForecastView.swift:213).
                            Text(
                                text = "Ziel-BAC bereits überschritten",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // iOS: .system(size: 52, weight: .light, design: .serif) (ForecastView.swift:220)
                            Text(
                                text = "$allowedDrinks",
                                color = if (allowedDrinks > 0) AppColors.accent else AppColors.textDim,
                                fontSize = 52.sp,
                                fontFamily = AppSerif,
                                fontWeight = FontWeight.Light
                            )
                            // iOS: .appCaption (ForecastView.swift:224).
                            Text(
                                text = "noch möglich",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        // iOS: .appMicro (ForecastView.swift:228).
                        Text(
                            text = String.format(
                                Locale.GERMANY,
                                "Standarddrinks · je ~%.2f ‰ Budget (konservativ)",
                                singleBeerBac
                            ),
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }
            }
        }
    }
}
