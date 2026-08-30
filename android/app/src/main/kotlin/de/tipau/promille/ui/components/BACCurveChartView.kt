package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.CurvePoint
import de.tipau.promille.bac.Drink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun BACCurveChartView(
    points: List<CurvePoint>,
    drinks: List<Drink>,
    warningThreshold: Double = 0.5,
    modifier: Modifier = Modifier,
    onFullScreenTap: (() -> Unit)? = null
) {
    var showFullDay by remember { mutableStateOf(false) }
    var scrubbedPoint by remember { mutableStateOf<CurvePoint?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }

    PromilleCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with 8h/24h toggle and full-screen button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(text = "VERLAUF")
                    if (scrubbedPoint != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${timeFormatter.format(Instant.ofEpochSecond(scrubbedPoint!!.epochSeconds))}: ${String.format(Locale.GERMANY, "%.2f ‰", scrubbedPoint!!.bac)}",
                            color = AppColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.background)
                            .border(1.dp, AppColors.border, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (!showFullDay) AppColors.accent else Color.Transparent)
                                .clickable { showFullDay = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "8h",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!showFullDay) AppColors.background else AppColors.textDim
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (showFullDay) AppColors.accent else Color.Transparent)
                                .clickable { showFullDay = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "24h",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showFullDay) AppColors.background else AppColors.textDim
                            )
                        }
                    }

                    if (onFullScreenTap != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Vollbild-Kurve öffnen", onClick = onFullScreenTap),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.background)
                                    .border(0.5.dp, AppColors.border, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = de.tipau.promille.ui.components.AppIcons.Expand,
                                    contentDescription = "Vollbild-Kurve",
                                    tint = AppColors.textDim,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Canvas Chart
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Noch keine Verlaufskurve", color = AppColors.textMuted, fontSize = 13.sp)
                }
            } else {
                val nowEpoch = remember { System.currentTimeMillis() / 1000 }
                val startTime = points.first().epochSeconds
                val endTime = points.last().epochSeconds
                val maxBac = max(1.0, (points.maxOfOrNull { it.bac } ?: 0.0) + 0.2)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(points) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val targetEpoch = startTime + ((endTime - startTime) * fraction).toLong()
                                    scrubbedPoint = points.minByOrNull { kotlin.math.abs(it.epochSeconds - targetEpoch) }
                                }
                            )
                        }
                        .pointerInput(points) {
                            detectDragGestures(
                                onDragEnd = { scrubbedPoint = null },
                                onDragCancel = { scrubbedPoint = null },
                                onDrag = { change, _ ->
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val targetEpoch = startTime + ((endTime - startTime) * fraction).toLong()
                                    scrubbedPoint = points.minByOrNull { kotlin.math.abs(it.epochSeconds - targetEpoch) }
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height - 24.dp.toPx() // Reserve space for x-axis labels

                    // 1. Grid Lines & Y-Labels
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val yBac = (maxBac / gridSteps) * i
                        val yPos = h - (yBac / maxBac * h).toFloat()
                        drawLine(
                            color = AppColors.border.copy(alpha = 0.5f),
                            start = Offset(0f, yPos),
                            end = Offset(w, yPos),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format(Locale.GERMANY, "%.1f", yBac),
                            topLeft = Offset(4.dp.toPx(), yPos - 12.dp.toPx()),
                            style = TextStyle(color = AppColors.textDim, fontSize = 9.sp)
                        )
                    }

                    // 2. Warning Threshold Line (e.g. 0.5 ‰)
                    if (warningThreshold <= maxBac) {
                        val threshY = h - (warningThreshold / maxBac * h).toFloat()
                        drawLine(
                            color = AppColors.statusOrange.copy(alpha = 0.6f),
                            start = Offset(0f, threshY),
                            end = Offset(w, threshY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${String.format(Locale.GERMANY, "%.1f", warningThreshold)} ‰ Limit",
                            topLeft = Offset(w - 60.dp.toPx(), threshY - 14.dp.toPx()),
                            style = TextStyle(color = AppColors.statusOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }

                    // 3. Current Time ("Jetzt") Vertical Line
                    val currentNow = System.currentTimeMillis() / 1000
                    if (currentNow in startTime..endTime) {
                        val nowX = ((currentNow - startTime).toFloat() / (endTime - startTime)) * w
                        drawLine(
                            color = AppColors.accent.copy(alpha = 0.5f),
                            start = Offset(nowX, 0f),
                            end = Offset(nowX, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "Jetzt",
                            topLeft = Offset(nowX + 4.dp.toPx(), 4.dp.toPx()),
                            style = TextStyle(color = AppColors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }

                    // 4. Build Curve Path & Area Path
                    val curvePath = Path()
                    val areaPath = Path()

                    points.forEachIndexed { index, p ->
                        val x = ((p.epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                        val y = h - (p.bac / maxBac * h).toFloat()

                        if (index == 0) {
                            curvePath.moveTo(x, y)
                            areaPath.moveTo(x, h)
                            areaPath.lineTo(x, y)
                        } else {
                            val prev = points[index - 1]
                            val prevX = ((prev.epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                            val prevY = h - (prev.bac / maxBac * h).toFloat()
                            val midX = (prevX + x) / 2f
                            curvePath.cubicTo(midX, prevY, midX, y, x, y)
                            areaPath.cubicTo(midX, prevY, midX, y, x, y)
                        }
                    }

                    val lastX = ((points.last().epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                    areaPath.lineTo(lastX, h)
                    areaPath.close()

                    // Draw Gradient Area
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(AppColors.accent.copy(alpha = 0.25f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Curve Stroke
                    drawPath(
                        path = curvePath,
                        color = AppColors.accent,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 5. Drink Point Dots
                    drinks.forEach { drink ->
                        if (drink.timestampEpochSeconds in startTime..endTime) {
                            val drinkX = ((drink.timestampEpochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                            val nearest = points.minByOrNull { kotlin.math.abs(it.epochSeconds - drink.timestampEpochSeconds) }
                            if (nearest != null) {
                                val drinkY = h - (nearest.bac / maxBac * h).toFloat()
                                drawCircle(
                                    color = AppColors.background,
                                    radius = 5.dp.toPx(),
                                    center = Offset(drinkX, drinkY)
                                )
                                drawCircle(
                                    color = AppColors.accent,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(drinkX, drinkY)
                                )
                            }
                        }
                    }

                    // 6. X-Axis Time Labels
                    val labelCount = if (showFullDay) 6 else 4
                    for (i in 0..labelCount) {
                        val t = startTime + ((endTime - startTime) / labelCount) * i
                        val x = (i.toFloat() / labelCount) * w
                        val label = timeFormatter.format(Instant.ofEpochSecond(t))
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(max(0f, x - 14.dp.toPx()), h + 4.dp.toPx()),
                            style = TextStyle(color = AppColors.textDim, fontSize = 9.sp)
                        )
                    }

                    // 7. Scrubbed Marker
                    if (scrubbedPoint != null) {
                        val sx = ((scrubbedPoint!!.epochSeconds - startTime).toFloat() / (endTime - startTime)) * w
                        val sy = h - (scrubbedPoint!!.bac / maxBac * h).toFloat()

                        drawLine(
                            color = AppColors.textDim,
                            start = Offset(sx, 0f),
                            end = Offset(sx, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawCircle(
                            color = AppColors.text,
                            radius = 6.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        drawCircle(
                            color = AppColors.accent,
                            radius = 4.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }
    }
}
