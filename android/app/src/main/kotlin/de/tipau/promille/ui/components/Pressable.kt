package de.tipau.promille.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import de.tipau.promille.LocalReducedMotion

/**
 * 1:1 mirror of iOS PressableButtonStyle (Theme/Motion.swift:47).
 * Applies subtle scale-down (0.97) and opacity (0.85) feedback on touch press.
 * Bypasses scale animation when reducedMotion is enabled.
 */
@Composable
fun Modifier.pressableEffect(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    scale: Float = 0.97f,
    pressedAlpha: Float = 0.85f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current

    val currentScale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reducedMotion) scale else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressableScale"
    )

    val currentAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled && !reducedMotion) pressedAlpha else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressableAlpha"
    )

    return this.graphicsLayer {
        scaleX = currentScale
        scaleY = currentScale
        alpha = currentAlpha
    }
}

/**
 * Convenience modifier combining custom touch interaction with scale/opacity press feedback.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    scale: Float = 0.97f,
    pressedAlpha: Float = 0.85f,
    onClick: (() -> Unit)? = null
): Modifier {
    val modifierWithClick = if (onClick != null) {
        this.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    } else {
        this
    }

    return modifierWithClick.pressableEffect(
        interactionSource = interactionSource,
        enabled = enabled,
        scale = scale,
        pressedAlpha = pressedAlpha
    )
}

/**
 * 1:1 mirror of iOS pressableChip (Theme/Motion.swift:65).
 * Slightly stronger shrink (scale: 0.94) for small controls (chips, circular icon buttons).
 */
@Composable
fun Modifier.pressableChip(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = pressable(
    interactionSource = interactionSource,
    enabled = enabled,
    scale = 0.94f,
    pressedAlpha = 0.85f,
    onClick = onClick
)
