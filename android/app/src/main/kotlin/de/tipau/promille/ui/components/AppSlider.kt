package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * Slider with UISlider's thin track / small circular thumb, instead of
 * M3's default stadium-shaped thumb. Was duplicated inline in
 * SettingsSliderRow; every other raw Slider( in the app now goes through
 * this instead of re-inheriting the M3 default.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        interactionSource = source,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(0.5.dp, Color(0x22000000), CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveColor,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        },
        modifier = modifier.fillMaxWidth()
    )
}
