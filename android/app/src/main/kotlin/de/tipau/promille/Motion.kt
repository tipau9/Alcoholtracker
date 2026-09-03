package de.tipau.promille

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable

/**
 * 1:1 mirror of iOS Theme/Motion.swift animation curves:
 * - appSnappy = .smooth(duration: 0.22)
 * - appSpring = .smooth(duration: 0.38)
 * - appGentle = .easeInOut(duration: 0.35)
 *
 * All curves collapse to instant (0ms / snap) when reducedMotion is enabled.
 */
object AppMotion {
    @Composable
    fun <T> snappy(): AnimationSpec<T> =
        if (LocalReducedMotion.current) snap()
        else tween(durationMillis = 220, easing = FastOutSlowInEasing)

    @Composable
    fun <T> springMotion(): AnimationSpec<T> =
        if (LocalReducedMotion.current) snap()
        else tween(durationMillis = 380, easing = FastOutSlowInEasing)

    @Composable
    fun <T> gentle(): AnimationSpec<T> =
        if (LocalReducedMotion.current) snap()
        else tween(durationMillis = 350, easing = FastOutSlowInEasing)

    val bannerTopEnter = slideInVertically(initialOffsetY = { -it }) + fadeIn()
    val bannerTopExit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()

    val toastBottomEnter = slideInVertically(initialOffsetY = { it }) + fadeIn()
    val toastBottomExit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
}
