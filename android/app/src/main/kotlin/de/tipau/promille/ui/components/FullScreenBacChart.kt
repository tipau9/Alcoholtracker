package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.CurvePoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import de.tipau.promille.AppSans
import de.tipau.promille.AppSerif
import de.tipau.promille.TabularFigures
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.style.ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.chart.decoration.Decoration
import com.patrykandpatrick.vico.core.chart.decoration.ThresholdLine
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.DashedShape
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.extension.half
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter
import com.patrykandpatrick.vico.core.marker.MarkerVisibilityChangeListener
import kotlin.math.max

/** ThresholdLine only marks y-axis ranges, so the vertical "Jetzt" line needs its own
 * x-based [Decoration] mapping a data x-value to the chart's pixel bounds by hand. */
private class NowLineDecoration(
    private val xValue: Float,
    private val lineComponent: ShapeComponent,
    private val thicknessDp: Float = 1f
) : Decoration {
    override fun onDrawAboveChart(
        context: ChartDrawContext,
        bounds: android.graphics.RectF
    ): Unit = with(context) {
        val chartValues = chartValuesProvider.getChartValues()
        val xRange = chartValues.maxX - chartValues.minX
        if (xRange <= 0f || xValue < chartValues.minX || xValue > chartValues.maxX) return@with
        val x = bounds.left + (xValue - chartValues.minX) / xRange * bounds.width()
        val half = thicknessDp.pixels.half
        lineComponent.draw(context = context, left = x - half, top = bounds.top, right = x + half, bottom = bounds.bottom)
    }
}

/**
 * 1:1 Port of FullScreenBACChart.swift.
 * Full-screen interactive BAC chart for 24-hour visualization with real-time scrubbing.
 * Grid/gradient/scrub-bubble rendering is vico (compose-m3); header, HUD and legend are
 * custom to keep the app's own chrome. x is minutes-since-start, not epoch seconds -
 * ChartEntry.x is a Float and epoch seconds overflow its exact-integer range.
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

            // Interactive Chart (vico)
            val startTime = effectivePoints.first().epochSeconds
            val endTime = effectivePoints.last().epochSeconds

            val entryProducer = remember(effectivePoints, startTime) {
                ChartEntryModelProducer(
                    effectivePoints.map { pt ->
                        FloatEntry(x = (pt.epochSeconds - startTime) / 60f, y = pt.bac.toFloat())
                    }
                )
            }

            val style = remember {
                ChartStyle.fromColors(
                    axisLabelColor = AppColors.textDim,
                    axisGuidelineColor = AppColors.border.copy(alpha = 0.3f),
                    axisLineColor = AppColors.border.copy(alpha = 0.3f),
                    entityColors = listOf(AppColors.accent),
                    elevationOverlayColor = AppColors.accent
                )
            }

            val markerLabel = textComponent(
                color = AppColors.background,
                textSize = 13.sp,
                background = shapeComponent(shape = Shapes.pillShape, color = AppColors.accent),
                padding = dimensionsOf(horizontal = 10.dp, vertical = 6.dp)
            )
            val markerIndicator = shapeComponent(shape = Shapes.pillShape, color = AppColors.accent)
            val markerGuideline = lineComponent(color = AppColors.textDim.copy(alpha = 0.35f), thickness = 1.dp)
            val marker = remember(markerLabel, markerIndicator, markerGuideline) {
                object : MarkerComponent(markerLabel, markerIndicator, markerGuideline) {
                    init {
                        indicatorSizeDp = 8f
                        labelFormatter = MarkerLabelFormatter { markedEntries, _ ->
                            markedEntries.firstOrNull()?.entry?.y?.let {
                                String.format(Locale.GERMANY, "%.2f", it)
                            } ?: ""
                        }
                    }
                }
            }
            val markerListener = remember(startTime) {
                object : MarkerVisibilityChangeListener {
                    override fun onMarkerShown(marker: Marker, markerEntryModels: List<Marker.EntryModel>) {
                        markerEntryModels.firstOrNull()?.entry?.let { entry ->
                            haptics.selection()
                            selectedPoint = CurvePoint(startTime + (entry.x * 60).toLong(), entry.y.toDouble())
                        }
                    }
                    override fun onMarkerMoved(marker: Marker, markerEntryModels: List<Marker.EntryModel>) {
                        markerEntryModels.firstOrNull()?.entry?.let { entry ->
                            haptics.selection()
                            selectedPoint = CurvePoint(startTime + (entry.x * 60).toLong(), entry.y.toDouble())
                        }
                    }
                    override fun onMarkerHidden(marker: Marker) {
                        // Keep the last scrubbed point visible after drag-release, like the old Canvas chart did.
                    }
                }
            }

            val thresholdLabel = textComponent(
                color = AppColors.statusRed,
                textSize = 11.sp,
                padding = dimensionsOf(horizontal = 4.dp, vertical = 2.dp)
            )
            val thresholdLineComponent = shapeComponent(
                shape = DashedShape(Shapes.rectShape, 8f, 4f),
                color = AppColors.statusRed.copy(alpha = 0.55f)
            )
            val thresholdLine = remember(drivingLimit, thresholdLineComponent, thresholdLabel) {
                if (drivingLimit > 0) {
                    ThresholdLine(
                        thresholdValue = drivingLimit.toFloat(),
                        thresholdLabel = "${String.format(Locale.GERMANY, "%.1f", drivingLimit)} Promille",
                        lineComponent = thresholdLineComponent,
                        labelComponent = thresholdLabel
                    )
                } else null
            }
            val nowLineComponent = shapeComponent(
                shape = DashedShape(Shapes.rectShape, 3f, 3f),
                color = AppColors.textDim.copy(alpha = 0.5f)
            )
            val nowLine = remember(startTime, endTime, nowLineComponent) {
                val nowEpoch = System.currentTimeMillis() / 1000
                if (nowEpoch in startTime..endTime) {
                    NowLineDecoration(xValue = (nowEpoch - startTime) / 60f, lineComponent = nowLineComponent)
                } else null
            }

            val maxBacFromPoints = effectivePoints.maxOfOrNull { it.bac } ?: 0.0
            val maxBac = max(maxBacFromPoints * 1.2, max(drivingLimit * 1.3, 0.8))

            ProvideChartStyle(style) {
                Chart(
                    chart = lineChart(
                        lines = listOf(
                            lineSpec(
                                lineColor = AppColors.accent,
                                pointConnector = DefaultPointConnector(cubicStrength = 0f)
                            )
                        ),
                        decorations = listOfNotNull<Decoration>(thresholdLine, nowLine),
                        axisValuesOverrider = AxisValuesOverrider.fixed(minY = 0f, maxY = maxBac.toFloat())
                    ),
                    chartModelProducer = entryProducer,
                    startAxis = rememberStartAxis(
                        valueFormatter = { value, _ -> String.format(Locale.GERMANY, "%.1f", value) }
                    ),
                    bottomAxis = rememberBottomAxis(
                        guideline = null,
                        valueFormatter = { value, _ ->
                            timeFormatter.format(Instant.ofEpochSecond(startTime + (value * 60).toLong()))
                        }
                    ),
                    marker = marker,
                    markerVisibilityChangeListener = markerListener,
                    runInitialAnimation = false,
                    chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                    isZoomEnabled = false,
                    horizontalLayout = HorizontalLayout.FullWidth(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = if (selectedPoint == null) 12.dp else 4.dp)
                )
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
