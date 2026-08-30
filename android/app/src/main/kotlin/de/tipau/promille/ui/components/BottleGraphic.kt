package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

/**
 * Port of BottleGraphic.swift.
 * Stylised bottle silhouette showing start level (dashed line) and current level (filled).
 */
@Composable
fun BottleGraphic(
    startLevel: Double,   // 0.0 = empty, 1.0 = full
    currentLevel: Double,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth
        val heightDp = maxHeight

        val accentColor = AppColors.accent
        val borderColor = AppColors.border

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1.0 (100%) maps to 76% of physical height (starts below the neck)
            val maxFillRatio = 0.76f
            val visualStartLevel = (startLevel.toFloat() * maxFillRatio).coerceIn(0f, maxFillRatio)
            val visualCurrentLevel = (currentLevel.toFloat() * maxFillRatio).coerceIn(0f, maxFillRatio)

            val bottlePath = createBottlePath(w, h)

            // 1. Consumed region (start -> current), lighter accent tint (0.25 opacity)
            if (visualStartLevel > visualCurrentLevel) {
                clipPath(bottlePath) {
                    val topY = h * (1f - visualStartLevel)
                    val bottomY = h * (1f - visualCurrentLevel)
                    clipRect(
                        left = 0f,
                        top = topY,
                        right = w,
                        bottom = bottomY
                    ) {
                        drawRect(color = accentColor.copy(alpha = 0.25f))
                    }
                }
            }

            // 2. Remaining liquid (current level down to bottom), darker accent tint (0.55 opacity)
            if (visualCurrentLevel > 0f) {
                clipPath(bottlePath) {
                    val topY = h * (1f - visualCurrentLevel)
                    clipRect(
                        left = 0f,
                        top = topY,
                        right = w,
                        bottom = h
                    ) {
                        drawRect(color = accentColor.copy(alpha = 0.55f))
                    }
                }
            }

            // 3. Bottle outline
            drawPath(
                path = bottlePath,
                color = borderColor,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4. Start-level marker line (dashed)
            val markerY = h * (1f - visualStartLevel)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
            drawLine(
                color = accentColor,
                start = Offset(0f, markerY),
                end = Offset(w, markerY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        // 5. "Start" label if startLevel < 0.95
        if (startLevel < 0.95) {
            val maxFillRatio = 0.76f
            val visualStartLevel = (startLevel.toFloat() * maxFillRatio).coerceIn(0f, maxFillRatio)
            val markerYDp = heightDp * (1f - visualStartLevel) - 10.dp
            val labelXDp = widthDp * 0.65f

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.offset(x = labelXDp, y = markerYDp)
                ) {
                    Text(
                        text = "Start",
                        color = accentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Builds the geometric bottle outline matching BottleShape in BottleGraphic.swift.
 */
private fun createBottlePath(w: Float, h: Float): Path {
    val path = Path()
    val cx = w / 2f
    val neckW = w * 0.28f
    val bodyR = 8f
    val neckTop = h * 0.04f
    val neckBot = h * 0.24f
    val shoulder = h * 0.32f

    path.moveTo(cx - neckW / 2f, neckTop)
    path.lineTo(cx - neckW / 2f, neckBot)
    path.cubicTo(
        cx - neckW / 2f, shoulder,
        bodyR, shoulder,
        bodyR, shoulder + 18f
    )
    path.lineTo(bodyR, h - bodyR)
    path.arcTo(
        rect = Rect(bodyR, h - 2f * bodyR, 3f * bodyR, h),
        startAngleDegrees = 180f,
        sweepAngleDegrees = -90f,
        forceMoveTo = false
    )
    path.lineTo(w - 2f * bodyR, h)
    path.arcTo(
        rect = Rect(w - 3f * bodyR, h - 2f * bodyR, w - bodyR, h),
        startAngleDegrees = 90f,
        sweepAngleDegrees = -90f,
        forceMoveTo = false
    )
    path.lineTo(w - bodyR, shoulder + 18f)
    path.cubicTo(
        w - bodyR, shoulder,
        cx + neckW / 2f, shoulder,
        cx + neckW / 2f, neckBot
    )
    path.lineTo(cx + neckW / 2f, neckTop)
    path.close()
    return path
}
