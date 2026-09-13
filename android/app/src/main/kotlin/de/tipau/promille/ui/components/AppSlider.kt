package de.tipau.promille.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.tipau.promille.AppColors
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import io.github.alexzhirkevich.cupertino.CupertinoSliderDefaults
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

/**
 * Native iOS CupertinoSlider matching UISlider.
 * Exposes Apple standard thumb with authentic drop shadow, smooth drag physics,
 * continuous tick feedback, and color customization.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    activeColor: Color = AppColors.accent,
    inactiveColor: Color = AppColors.border,
    interactionSource: MutableInteractionSource? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    CupertinoSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        interactionSource = source,
        onValueChangeFinished = onValueChangeFinished,
        colors = CupertinoSliderDefaults.colors(
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            thumbColor = Color.White
        ),
        modifier = modifier.fillMaxWidth()
    )
}
