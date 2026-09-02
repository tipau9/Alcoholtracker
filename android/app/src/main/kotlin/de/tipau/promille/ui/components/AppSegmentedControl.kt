package de.tipau.promille.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import de.tipau.promille.LocalReducedMotion

/**
 * 1:1 mirror of standard iOS Segmented Control (Picker.pickerStyle(.segmented), AdminView.swift:260).
 * Features 10dp outer rounded rect, 3dp inset, sliding pill indicator with .pressable feedback.
 */
@Composable
fun <T> AppSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: @Composable (T) -> String = { it.toString() }
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val reducedMotion = LocalReducedMotion.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / items.size.coerceAtLeast(1)

        val animatedOffset by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = if (reducedMotion) tween(durationMillis = 0) else tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "segmentedIndicatorOffset"
        )

        // Sliding Highlight Pill
        Box(
            modifier = Modifier
                .offset(x = segmentWidth * animatedOffset)
                .width(segmentWidth)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        )

        // Row of items
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .pressable(
                            scale = 0.96f,
                            onClick = { onItemSelected(item) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = if (isSelected) AppText.captionBold else AppText.caption,
                        color = if (isSelected) AppColors.text else AppColors.textDim,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
