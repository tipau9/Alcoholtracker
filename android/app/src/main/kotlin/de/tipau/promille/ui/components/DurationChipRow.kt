package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.appSpec
import de.tipau.promille.AppColors
import kotlin.math.roundToInt

/**
 * Port of DurationChipRow.swift.
 *
 * Picks how long a drink is consumed over ("verzögerter Start" / sipping).
 * 0 means "auto-estimate" and a positive value stretches the BAC absorption window.
 */
@Composable
fun DurationChipRow(
    durationMinutes: Double,
    onDurationMinutesChange: (Double) -> Unit,
    estimatedMinutes: Double? = null,
    modifier: Modifier = Modifier
) {
    // (label, minutes). 0 = auto-estimate from category + volume.
    val options = listOf(
        "Auto" to 0.0,
        "30 min" to 30.0,
        "1 Std" to 60.0,
        "2 Std" to 120.0,
        "3 Std" to 180.0
    )

    val isCustom = durationMinutes > 0 && options.drop(1).none { it.second.toInt() == durationMinutes.toInt() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Preset duration chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = durationMinutes.toInt() == option.second.toInt()
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.accent else AppColors.card,
                    animationSpec = appSpec(tween(150)),
                    label = "durationChipBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) AppColors.background else AppColors.textDim,
                    animationSpec = appSpec(tween(150)),
                    label = "durationChipText"
                )
                val borderColor = if (isSelected) AppColors.accent else AppColors.border

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .border(0.5.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable { onDurationMinutesChange(option.second) }
                        .padding(vertical = if (option.second == 0.0 && estimatedMinutes != null) 6.dp else 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            // iOS: .appCaption, no weight override (was
                            // 12sp Medium here).
                            text = option.first,
                            color = textColor,
                            style = de.tipau.promille.AppText.caption,
                            textAlign = TextAlign.Center
                        )
                        if (option.second == 0.0 && estimatedMinutes != null) {
                            Text(
                                text = formatDuration(estimatedMinutes),
                                color = textColor,
                                style = de.tipau.promille.AppText.micro,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Custom duration button and stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val customBgColor by animateColorAsState(
                targetValue = if (isCustom) AppColors.accent else AppColors.card,
                animationSpec = appSpec(tween(150)),
                label = "customDurationBg"
            )
            val customTextColor by animateColorAsState(
                targetValue = if (isCustom) AppColors.background else AppColors.textDim,
                animationSpec = appSpec(tween(150)),
                label = "customDurationText"
            )
            val customBorderColor = if (isCustom) AppColors.accent else AppColors.border

            // "Eigene Dauer" button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(customBgColor)
                    .border(0.5.dp, customBorderColor, RoundedCornerShape(10.dp))
                    .clickable {
                        if (durationMinutes <= 0) {
                            onDurationMinutesChange(estimatedMinutes ?: 30.0)
                        }
                    }
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Sliders,
                        contentDescription = null,
                        tint = customTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        // iOS: .appCaptionBold (13sp SemiBold) - was 12sp Bold.
                        text = "Eigene Dauer",
                        color = customTextColor,
                        style = de.tipau.promille.AppText.captionBold
                    )
                }
            }

            // Stepper controls when duration > 0
            if (durationMinutes > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        // iOS: .appCaptionBold - was 12sp Bold.
                        text = formatDuration(durationMinutes),
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.captionBold
                    )

                    // Minus button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.background)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(6.dp))
                            .clickable {
                                val next = maxOf(1.0, durationMinutes - 5.0)
                                onDurationMinutesChange(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-",
                            color = AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Plus button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.background)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(6.dp))
                            .clickable {
                                val next = minOf(240.0, durationMinutes + 5.0)
                                onDurationMinutesChange(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(minutes: Double): String {
    val rounded = maxOf(1, minutes.roundToInt())
    if (rounded < 60) return "$rounded min"
    val hours = rounded / 60
    val mins = rounded % 60
    return if (mins == 0) "$hours Std" else "${hours}h ${mins}m"
}
