package de.tipau.promille.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.BacStatus
import de.tipau.promille.ui.viewmodels.BacTrend
import java.util.Locale

@Composable
fun BACDisplaySection(
    bac: Double,
    status: BacStatus,
    trend: BacTrend = BacTrend.STABLE,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Pulsating Glow (when BAC > 0.01)
            if (bac > 0.01) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(status.color.copy(alpha = pulseAlpha), Color.Transparent),
                                radius = 340f
                            )
                        )
                )
            }

            // 2. Circular Outer Ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(AppColors.card.copy(alpha = 0.6f))
                    .border(1.5.dp, status.color.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f", bac),
                            color = if (bac > 0.01) status.color else AppColors.text,
                            fontSize = 62.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default
                        )
                        if (bac > 0.01) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = trend.symbol,
                                color = when (trend) {
                                    BacTrend.RISING -> AppColors.statusOrange
                                    BacTrend.FALLING -> AppColors.statusGreen
                                    BacTrend.STABLE -> AppColors.textDim
                                },
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                        }
                    }
                    Text(
                        text = "‰ Promille",
                        color = AppColors.textDim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 3. Status Pill
        Box(
            modifier = Modifier
                .background(status.color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .border(1.dp, status.color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text(
                text = status.germanName,
                color = status.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
