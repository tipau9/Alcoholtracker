package de.tipau.promille.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

private val TrackWidth = 46.dp
private val TrackHeight = 26.dp
private val KnobSize = 22.dp
private val KnobPadding = 2.dp

/**
 * Capsule Switch matching UISwitch's proportions. M3's Switch only exposes
 * thumbContent/colors in this Compose BOM, not track geometry, so this is
 * hand-rolled rather than a parameterized wrapper.
 *
 * The knob stays white in both states, same as iOS - only the track tints.
 * (Two of the three call sites this replaces used to recolor the thumb too,
 * which isn't how UISwitch actually looks.)
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    activeColor: Color = AppColors.accent,
    inactiveColor: Color = AppColors.border
) {
    val offset by animateDpAsState(
        targetValue = if (checked) TrackWidth - KnobSize - KnobPadding else KnobPadding,
        animationSpec = tween(150),
        label = "switchKnob"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else inactiveColor,
        animationSpec = tween(150),
        label = "switchTrack"
    )
    val haptics = rememberHapticManager()
    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .toggleable(
                value = checked,
                onValueChange = {
                    haptics.selection()
                    onCheckedChange?.invoke(it)
                },
                role = Role.Switch,
                enabled = onCheckedChange != null
            )
            .background(trackColor, RoundedCornerShape(50)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(KnobSize)
                .background(Color.White, CircleShape)
        )
    }
}
