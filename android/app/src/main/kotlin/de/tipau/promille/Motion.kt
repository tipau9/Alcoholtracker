package de.tipau.promille

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

object AppMotion {
    @Composable
    fun <T> snappy(): AnimationSpec<T> =
        if (AppTheme.shared.reducedMotion) snap()
        else tween(durationMillis = 220, easing = FastOutSlowInEasing)

    @Composable
    fun <T> springMotion(): AnimationSpec<T> =
        if (AppTheme.shared.reducedMotion) snap()
        else spring(dampingRatio = 0.85f, stiffness = 400f)

    @Composable
    fun <T> gentle(): AnimationSpec<T> =
        if (AppTheme.shared.reducedMotion) snap()
        else tween(durationMillis = 350, easing = FastOutSlowInEasing)

    val bannerTopEnter = slideInVertically(initialOffsetY = { -it }) + fadeIn()
    val bannerTopExit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()

    val toastBottomEnter = slideInVertically(initialOffsetY = { it }) + fadeIn()
    val toastBottomExit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
}

fun Modifier.pressable(
    scale: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentScale = if (AppTheme.shared.reducedMotion) 1f else if (isPressed) scale else 1f

    this
        .scale(currentScale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

fun Modifier.pressableChip(
    onClick: () -> Unit
): Modifier = pressable(scale = 0.94f, onClick = onClick)

