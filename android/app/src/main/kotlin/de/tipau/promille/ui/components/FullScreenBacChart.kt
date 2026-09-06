package de.tipau.promille.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.CurvePoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import de.tipau.promille.AppSans
import de.tipau.promille.AppSerif
import de.tipau.promille.TabularFigures

/**
 * 1:1 Port of FullScreenBACChart.swift.
 * Full-screen interactive BAC chart for 24-hour visualization with real-time scrubbing.
 */
@Composable
fun FullScreenBacChart(
    points: List<CurvePoint>,
    drivingLimit: Double = 0.5,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHapticManager()
    var selectedPoint by remember { mutableStateOf<CurvePoint?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val effectivePoints = remember(points) {
        if (points.isNotEmpty()) {
            points
        } else {
            val now = System.currentTimeMillis() / 1000
            val durationHours = 24L
            val startOffset = 3L * 3600L
            val start = now - startOffset
            val step = 600L
            val count = (durationHours * 3600L / step).toInt()
            List(count + 1) { i -> CurvePoint(start + i * step, 0.0) }
        }
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }
    val hourFormatter = remember {
        DateTimeFormatter.ofPattern("HH", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "BAC-Verlauf",
                        color = AppColors.text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = AppSerif
                    )
                    Text(
                        // iOS: .appCaption - was 12sp.
                        text = "24-Stunden-Ansicht",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }

                AppIconCloseButton(onDismiss = onDismiss)
            }

            // Selected Point Overlay (Scrubbing HUD)
            val activePt = selectedPoint ?: effectivePoints.lastOrNull()
            if (activePt != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f", activePt.bac),
                            color = AppColors.accent,
                            fontSize = fixedSp(42f),
                            fontWeight = FontWeight.Light,
                            fontFamily = AppSerif
                        )
                        Text(
                            text = "‰",
                            color = AppColors.accent.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = timeFormatter.format(Instant.ofEpochSecond(activePt.epochSeconds)) + " Uhr",
                        color = AppColors.textDim,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppSans,
                        style = TabularFigures,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Interactive Chart Canvas (always rendered via effectivePoints)
            val startTime = effectivePoints.first().epochSeconds
            val endTime = effectivePoints.last().epochSeconds
            val maxBacFromPoints = effectivePoints.maxOfOrNull { it.bac } ?: 0.0
            val maxBac = max(maxBacFromPoints * 1.2, max(drivingLimit * 1.3, 0.8))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = if (selectedPoint == null) 12.dp else 4.dp)
                    .pointerInput(effectivePoints) {
                        detectTapGestures(
                            onPress = { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetEpoch = startTime + ((endTime - startTime) * fraction).toLong()
                                val closest = effectivePoints.minByOrNull { abs(it.epochSeconds - targetEpoch) }
                                if (closest != null && closest.epochSeconds != selectedPoint?.epochSeconds) {
                                    haptics.selection()
                                    selectedPoint = closest
                                }
                            }
                        )
                    }
                    .pointerInput(effectivePoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetEpoch = startTime + ((endTime - startTime) * fraction).toLong()
                                val closest = effectivePoints.minByOrNull { abs(it.epochSeconds - targetEpoch) }
                                if (closest != null && closest.epochSeconds != selectedPoint?.epochSeconds) {
                                    haptics.selection()
                                    selectedPoint = closest
                                }
                            },
                            onDragEnd = { /* keep point visible after drag for inspection */ },
                            onDragCancel = { /* keep point visible */ },
                            onDrag = { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                val targetEpoch = startTime + ((endTime - startTime) * fraction).toLong()
                                val closest = effectivePoints.minByOrNull { abs(it.epochSeconds - targetEpoch) }
                                if (closest != null && closest.epochSeconds != selectedPoint?.epochSeconds) {
                                    haptics.selection()
                                    selectedPoint = closest
                                }
                            }
                        )
                    }
            ) {
                    val w = size.width
                    val bottomPadding = 24.dp.toPx()
                    val h = size.height - bottomPadding

                    // 1. Grid Lines and Y-Axis Labels
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val yBac = (maxBac / ySteps) * i
                        val yPos = h - (yBac / maxBac * h).toFloat()
                        drawLine(
                            color = AppColors.border.copy(alpha = 0.3f),
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = 0.5.dp.toPx()
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format(Locale.GERMANY, "%.1f", yBac),
                            topLeft = Offset(4.dp.toPx(), yPos - 12.dp.toPx()),
                            style = TextStyle(color = AppColors.textDim, fontSize = 10.sp)
                        )
                    }

                    // 2. Driving Limit Line
                    if (drivingLimit > 0 && drivingLimit <= maxBac) {
                        val threshY = h - (drivingLimit / maxBac * h).toFloat()
                        drawLine(
                            color = AppColors.statusRed.copy(alpha = 0.55f),
                            start = Offset(0f, threshY),
                            end = Offset(w, threshY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${String.format(Locale.GERMANY, "%.1f", drivingLimit)} Promille",
                            topLeft = Offset(8.dp.toPx(), threshY - 14.dp.toPx()),
                            style = TextStyle(color = AppColors.statusRed.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        )
                    }

                    // 3. Current Time "Jetzt" Line
                    val currentNow = System.currentTimeMillis() / 1000
                    if (currentNow in startTime..endTime && endTime > startTime) {
                        val nowX = ((currentNow - startTime).toFloat() / (endTime - startTime)) * w
                        drawLine(
                            color = AppColors.textDim.copy(alpha = 0.4f),
                            start = Offset(nowX, 0f),
                            end = Offset(nowX, h),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
                        )
                    }

                    // 4. Smooth Curve Area and Line
                    val curvePath = Path()
                    val areaPath = Path()

                    effectivePoints.forEachIndexed { index, pt ->
                        val x = if (endTime > startTime) {
                            ((pt.epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                        } else 0f
                        val y = (h - (pt.bac / maxBac * h).toFloat()).coerceIn(0f, h)

                        if (index == 0) {
                            curvePath.moveTo(x, y)
                            areaPath.moveTo(x, h)
                            areaPath.lineTo(x, y)
                        } else {
                            curvePath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }
                    }
                    areaPath.lineTo(w, h)
                    areaPath.close()

                    // Draw Area Gradient
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AppColors.accent.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Line
                    drawPath(
                        path = curvePath,
                        color = AppColors.accent,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 5. Selected Point Indicator
                    if (selectedPoint != null && endTime > startTime) {
                        val pt = selectedPoint!!
                        val selX = ((pt.epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                        val selY = (h - (pt.bac / maxBac * h).toFloat()).coerceIn(0f, h)

                        // Vertical guide line
                        drawLine(
                            color = AppColors.accent.copy(alpha = 0.3f),
                            start = Offset(selX, 0f),
                            end = Offset(selX, h),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Glowing point
                        drawCircle(
                            color = AppColors.accent.copy(alpha = 0.3f),
                            radius = 10.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                        drawCircle(
                            color = AppColors.accent,
                            radius = 5.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                    }

                    // 6. X-Axis Time Labels (every 3 hours)
                    val stepSeconds = 3 * 3600L
                    var tick = ((startTime / stepSeconds) + 1) * stepSeconds
                    while (tick < endTime) {
                        val tickX = ((tick - startTime).toFloat() / (endTime - startTime)) * w
                        val label = hourFormatter.format(Instant.ofEpochSecond(tick))
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(tickX - 8.dp.toPx(), h + 6.dp.toPx()),
                            style = TextStyle(color = AppColors.textDim, fontSize = 10.sp)
                        )
                        tick += stepSeconds
                    }
                }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Verlauf
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
                    Text("Verlauf", color = AppColors.textDim, fontSize = 11.sp)
                }

                // Driving Limit
                if (drivingLimit > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AppColors.statusRed)
                        )
                        Text(
                            text = "${String.format(Locale.GERMANY, "%.1f", drivingLimit)} Promille",
                            color = AppColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                }

                // Jetzt
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(1.dp)
                            .background(AppColors.textDim.copy(alpha = 0.5f))
                    )
                    Text("Jetzt", color = AppColors.textDim, fontSize = 11.sp)
                }
            }
        }
    }
}
