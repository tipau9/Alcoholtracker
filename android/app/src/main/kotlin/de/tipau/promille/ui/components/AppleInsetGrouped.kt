package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * Native Apple Inset Grouped List Section (.listStyle(.insetGrouped)).
 * Provides rounded 14dp card with specular light border, edge-to-edge row layout,
 * and Apple HIG header/footer typography.
 */
@Composable
fun AppleInsetGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = AppText.caption.copy(letterSpacing = 0.5.sp),
                color = AppColors.textDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(AppColors.card)
                .appleLichtkante(cornerRadius = cornerRadius),
            content = content
        )

        if (footer != null) {
            Text(
                text = footer,
                style = AppText.caption,
                color = AppColors.textDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Simulates Apple's dark-mode specular light edge (top-lit glass highlight).
 */
fun Modifier.appleLichtkante(
    cornerRadius: Dp = 14.dp,
    highlightAlpha: Float = 0.16f
): Modifier = this.then(
    Modifier.border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = highlightAlpha),
            0.15f to AppColors.border,
            1.0f to AppColors.border.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
)
