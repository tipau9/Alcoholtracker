package de.tipau.promille.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.tipau.promille.AppColors
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import io.github.alexzhirkevich.cupertino.CupertinoSwitchDefaults
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

/**
 * Native iOS CupertinoSwitch from alexzhirkevich:cupertino.
 * Features realistic thumb drag gestures, spring physics, stretching on drag,
 * iOS-faithful track proportions, and tactile audio-haptic feedback.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    activeColor: Color = AppColors.accent,
    inactiveColor: Color = AppColors.border
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHapticManager()

    CupertinoSwitch(
        checked = checked,
        onCheckedChange = { newValue: Boolean ->
            haptics.selection()
            AppAudio.playClick(context)
            onCheckedChange?.invoke(newValue)
        },
        modifier = modifier,
        colors = CupertinoSwitchDefaults.colors(
            checkedTrackColor = activeColor,
            uncheckedTrackColor = inactiveColor,
            thumbColor = Color.White
        ),
        enabled = onCheckedChange != null
    )
}
