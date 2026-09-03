package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * 1:1 Mirror of iOS top scroll fade scrims.
 * Softens list content scrolling underneath sheet headers and navigation bars.
 */
@Composable
fun TopEdgeFadeScrim(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color = AppColors.background
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0f)
                    )
                )
            )
    )
}
