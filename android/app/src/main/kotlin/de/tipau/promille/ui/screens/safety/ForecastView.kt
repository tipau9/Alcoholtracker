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
                imageVector = AppIcons.History,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Vorausschau",
                color = AppColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (profile.conservativeForSafety) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.statusOrange)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "WORST-CASE",
                        color = AppColors.background,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
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
                        Text(
                            text = "WANN MUSST DU FIT SEIN?",
                            color = AppColors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = String.format(Locale.GERMAN, "%.1f h ab jetzt (%s Uhr)", targetHoursAhead, targetTimeStr),
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
                Slider(
                    value = targetHoursAhead,
                    onValueChange = { targetHoursAhead = it },
                    valueRange = 0.5f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.accent,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = AppColors.border
                    )
                )
            }

            // Target BAC Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GRENZWERT",
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
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
                            Text(
                                text = label,
                                color = if (isSelected) AppColors.background else AppColors.textDim,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ziel-BAC bereits überschritten",
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$allowedDrinks",
                                color = if (allowedDrinks > 0) AppColors.accent else AppColors.textDim,
                                fontSize = 44.sp,
                                fontFamily = AppSerif,
                                fontWeight = FontWeight.Light
                            )
                            Text(
                                text = "noch möglich",
                                color = AppColors.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.GERMAN,
                                "Standarddrinks · je ~%.2f ‰ Budget (konservativ)",
                                singleBeerBac
                            ),
                            color = AppColors.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
