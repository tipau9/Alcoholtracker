package de.tipau.promille.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import kotlin.math.max

/**
 * Canvas bar chart backing all five iOS SwiftCharts BarMark uses in TrendsView
 * (Uhrzeiten, Wochentage, Wochenverlauf, Kategorien, Stadt-Uhrzeiten). No
 * charting library is a dependency here, so this follows the same
 * Canvas + rememberTextMeasurer pattern as BACCurveChartView.
 *
 * [labelEvery] thins out x-axis labels in vertical mode (e.g. every 4th hour),
 * matching iOS's explicit AxisMarks(values: [0, 4, 8, ...]).
 */
@Composable
fun InsightsBarChart(
    data: List<Pair<String, Int>>,
    barColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    horizontal: Boolean = false,
    labelEvery: Int = 1
) {
    val textMeasurer = rememberTextMeasurer()
    if (data.isEmpty()) return
    val maximum = max(1, data.maxOf { it.second })

    if (horizontal) {
        val rowHeight = 26.dp
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(rowHeight * data.size)
        ) {
            val labelColW = 92.dp.toPx()
            val countColW = 28.dp.toPx()
            val barAreaW = size.width - labelColW - countColW
            val rowH = rowHeight.toPx()
            data.forEachIndexed { i, (label, count) ->
                val y = i * rowH
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(0f, y + rowH / 2 - 6.dp.toPx()),
                    style = TextStyle(color = AppColors.text, fontSize = 11.sp),
                    maxLines = 1
                )
                val barW = barAreaW * (count.toFloat() / maximum)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(labelColW, y + rowH / 2 - 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(max(2f, barW), 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$count",
                    topLeft = Offset(labelColW + barAreaW + 4.dp.toPx(), y + rowH / 2 - 6.dp.toPx()),
                    style = TextStyle(color = AppColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    } else {
        Canvas(modifier = modifier.fillMaxWidth().height(height)) {
            val labelReserve = 18.dp.toPx()
            val w = size.width
            val h = size.height - labelReserve
            val n = data.size
            val slot = w / n
            val barWidth = slot * 0.6f
            data.forEachIndexed { i, (label, count) ->
                val barH = h * (count.toFloat() / maximum)
                val x = i * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, h - barH),
                    size = androidx.compose.ui.geometry.Size(barWidth, max(1f, barH)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                if (labelEvery <= 1 || i % labelEvery == 0 || i == n - 1) {
                    val style = TextStyle(color = AppColors.textDim, fontSize = 9.sp)
                    val measured = textMeasurer.measure(label, style)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(x + barWidth / 2 - measured.size.width / 2f, h + 3.dp.toPx())
                    )
                }
            }
        }
    }
}
