package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.appSpec
import de.tipau.promille.AppColors
import kotlin.math.abs

/**
 * Port of MixRatioSlider.swift.
 * Visual spirit/mixer ratio bar. spiritFraction is clamped to 0.10..0.75.
 */
@Composable
fun MixRatioSlider(
    spiritFraction: Double,
    onSpiritFractionChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val minFraction = 0.10
    val maxFraction = 0.75

    val ratioPresets = listOf(
        "Leicht" to 0.10,
        "Standard" to 0.25,
        "Stark" to 0.33,
        "Doppelt" to 0.50
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Visual Slider Bar
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            val animatedFraction by animateFloatAsState(
                targetValue = spiritFraction.toFloat(),
                animationSpec = appSpec(spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )),
                label = "spiritFractionAnim"
            )

            val thumbOffsetXDp = with(LocalDensity.current) {
                ((totalWidthPx * animatedFraction) - 13.dp.toPx()).coerceAtLeast(0f).toDp()
            }
            val spiritWidthDp = with(LocalDensity.current) {
                (totalWidthPx * animatedFraction).coerceAtLeast(0f).toDp()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
                    .pointerInput(totalWidthPx) {
                        detectTapGestures { offset ->
                            if (totalWidthPx > 0) {
                                val raw = (offset.x / totalWidthPx).toDouble()
                                onSpiritFractionChange(raw.coerceIn(minFraction, maxFraction))
                            }
                        }
                    }
                    .pointerInput(totalWidthPx) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            if (totalWidthPx > 0) {
                                val raw = (change.position.x / totalWidthPx).toDouble()
                                onSpiritFractionChange(raw.coerceIn(minFraction, maxFraction))
                            }
                        }
                    }
            ) {
                // Spirit segment (accent fill from left)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(spiritWidthDp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.accent)
                )

                // Divider thumb
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffsetXDp)
                        .size(26.dp)
                        .align(Alignment.CenterStart)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppColors.text)
                )
            }
        }

        // Percentage indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spirit side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent)
                )
                Text(
                    text = "Spirituose",
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
                Text(
                    text = "${(spiritFraction * 100).toInt()}%",
                    color = AppColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mixer side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "${((1 - spiritFraction) * 100).toInt()}%",
                    color = AppColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mixer",
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, CircleShape)
                )
            }
        }

        // Preset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratioPresets.forEach { preset ->
                val isActive = abs(spiritFraction - preset.second) < 0.03
                val presetBgColor by animateColorAsState(
                    targetValue = if (isActive) AppColors.accent.copy(alpha = 0.15f) else AppColors.card,
                    animationSpec = appSpec(tween(150)),
                    label = "presetBg"
                )
                val presetTextColor by animateColorAsState(
                    targetValue = if (isActive) AppColors.accent else AppColors.textDim,
                    animationSpec = appSpec(tween(150)),
                    label = "presetText"
                )
                val presetBorderColor = if (isActive) AppColors.accent else AppColors.border
                val presetBorderWidth = if (isActive) 1.dp else 0.5.dp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(presetBgColor)
                        .border(presetBorderWidth, presetBorderColor, CircleShape)
                        .clickable { onSpiritFractionChange(preset.second) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.first,
                        color = presetTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
