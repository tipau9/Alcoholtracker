package de.tipau.promille.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.LocalReducedMotion
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.color
import de.tipau.promille.ui.viewmodels.BacTrend
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import java.util.Locale
import de.tipau.promille.AppSerif

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BACDisplaySection(
    bac: Double,
    status: BacStatus,
    skin: StatusSkin = StatusSkin.STANDARD,
    trend: BacTrend = BacTrend.STABLE,
    isEditMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // HomeView.swift:1701-1771. The glow circle is always drawn; reducedMotion
    // and a sober reading only freeze it at scale 1.0 / opacity 0.82 rather
    // than removing it, so the ring never sits on bare background.
    val reducedMotion = LocalReducedMotion.current
    val animate = bac > 0.01 && !reducedMotion
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // RadialGradient(startRadius: 60, endRadius: 140): flat colour out to 60,
    // falloff only over the outer 80. A plain radialGradient has no start
    // radius, which is why the Android glow used to read as a hard disc.
    val glowRadius = with(LocalDensity.current) { 140.dp.toPx() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // 1. Pulsating glow
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(if (animate) pulseScale else 1f)
                    .alpha(if (animate) pulseAlpha else 0.82f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0f to status.color.copy(alpha = 0.10f),
                            (60f / 140f) to status.color.copy(alpha = 0.10f),
                            1f to Color.Transparent,
                            radius = glowRadius
                        )
                    )
            )

            // 2. Circular outer ring. strokeBorder only, no fill.
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .border(1.dp, status.color.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f", bac),
                            color = AppColors.text,
                            fontSize = fixedSp(96f),
                            fontWeight = FontWeight.Light,
                            fontFamily = AppSerif,
                            // .monospacedDigit(): tabular figures, not a mono face.
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.alignByBaseline()
                        )
                        if (bac > 0.01) {
                            Text(
                                text = trend.symbol,
                                color = when (trend) {
                                    BacTrend.RISING -> AppColors.statusOrange
                                    BacTrend.FALLING -> AppColors.statusGreen
                                    BacTrend.STABLE -> AppColors.textDim
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }
                    Text(
                        text = "‰",
                        color = AppColors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                }
            }

            // Edit mode overlay matching iOS
            if (isEditMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "Immer sichtbar",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        StatusPill(status = status, skin = skin)
    }
}
