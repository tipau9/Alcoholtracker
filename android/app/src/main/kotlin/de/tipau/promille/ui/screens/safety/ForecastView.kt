package de.tipau.promille.ui.screens.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacCalculator
import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.Profile
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ForecastView(
    drinks: List<Drink>,
    profile: Profile,
    modifier: Modifier = Modifier
) {
    var targetHoursAhead by remember { mutableStateOf(3f) }
    var targetBac by remember { mutableStateOf(profile.drivingLimit) }

    val now = remember { System.currentTimeMillis() / 1000 }
    val targetEpochSeconds = now + (targetHoursAhead * 3600).toLong()

    val targetTimeStr = remember(targetEpochSeconds) {
        val time = LocalTime.now().plusHours(targetHoursAhead.toLong())
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
        )
    }

    val allowedDrinks = (allowedAdditionalBac / singleBeerBac.coerceAtLeast(0.01)).toInt()

    PromilleCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(text = "TRINK-PROGNOSE")
                Text(
                    text = "Ziel: $targetTimeStr Uhr",
                    color = AppColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Target Hours Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("In wie vielen Stunden?", color = AppColors.textDim, fontSize = 13.sp)
                    Text("in ${targetHoursAhead.toInt()}h ($targetTimeStr Uhr)", color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = targetHoursAhead,
                    onValueChange = { targetHoursAhead = it },
                    valueRange = 1f..8f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.accent,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = AppColors.border
                    )
                )
            }

            // Target BAC Selector (0,0 ‰ vs 0,5 ‰)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.0 to "0,0 ‰ (Nüchtern)", 0.3 to "0,3 ‰ (Sicher)", 0.5 to "0,5 ‰ (Fahrtauglich)").forEach { (limit, label) ->
                    val isSelected = kotlin.math.abs(targetBac - limit) < 0.05
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AppColors.accent.copy(alpha = 0.15f) else AppColors.background)
                            .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(10.dp))
                            .clickable { targetBac = limit }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AppColors.accent else AppColors.textDim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Forecast Result Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.background)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (projectedBacAtTarget >= targetBac) {
                        Text(
                            text = "⚠️ Limit wird um $targetTimeStr Uhr überschritten (${String.format(Locale.GERMANY, "%.2f ‰", projectedBacAtTarget)} erwartet)",
                            color = AppColors.statusOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Trinke bis dahin keinen Alkohol mehr, um dein Ziel zu erreichen.",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Noch ca. $allowedDrinks Drinks (z.B. 0,33L Bier) möglich",
                            color = AppColors.statusGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Prognostizierter Pegel um $targetTimeStr Uhr ohne weitere Drinks: ${String.format(Locale.GERMANY, "%.2f ‰", projectedBacAtTarget)}",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
