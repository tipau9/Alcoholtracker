package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import dev.chrisbanes.haze.HazeState

/**
 * 1:1 mirror of iOS UINavigationBar(prefersLargeTitles = true).
 * Features a large bold title that seamlessly collapses into a centered frosted-glass top bar as the user scrolls down.
 */
@Composable
fun CollapsibleLargeTitleHeader(
    title: String,
    scrollOffset: Float,
    collapseThresholdPx: Float = 120f,
    hazeState: HazeState? = null,
    includeStatusBarPadding: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val progress = (scrollOffset / collapseThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (includeStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .height(52.dp)
            .then(
                if (hazeState != null && progress > 0.05f) {
                    Modifier.appleGlass(
                        hazeState = hazeState,
                        shape = RectangleShape,
                        backgroundColor = AppColors.background.copy(alpha = 0.85f * progress)
                    )
                } else {
                    Modifier.background(AppColors.background.copy(alpha = progress * 0.95f))
                }
            )
    ) {
        // Centered Small Title (fades in as progress -> 1)
        Text(
            text = title,
            style = AppText.bodyBold,
            color = AppColors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(progress)
        )

        // Trailing Actions
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )

        // Hairline bottom divider
        if (progress > 0.8f) {
            HorizontalDivider(
                color = AppColors.border.copy(alpha = (progress - 0.8f) * 5f),
                thickness = 0.5.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Large Title content item placed inside the scrollable Column or LazyColumn.
 */
@Composable
fun LargeTitleItem(
    title: String,
    scrollOffset: Float,
    collapseThresholdPx: Float = 120f,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val progress = (scrollOffset / collapseThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
            .alpha(alpha),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppText.largeTitle,
            color = AppColors.text
        )
        trailingContent?.invoke()
    }
}
