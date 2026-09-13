package de.tipau.promille.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 1:1 mirror of iOS UINavigationController.interactivePopGestureRecognizer.
 * Detects swipe gesture from the left screen edge (x <= 36.dp).
 * Interactively drags the screen to the right with parallax scrim dimming and Apple spring release.
 */
@Composable
fun AppleEdgeSwipeContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val haptics = rememberHapticManager()
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edgeThresholdPx = with(density) { 36.dp.toPx() }
    val dismissThreshold = screenWidth * 0.38f

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x <= edgeThresholdPx) {
                        var isDragging = false
                        var accumulatedX = 0f

                        horizontalDrag(down.id) { change ->
                            val deltaX = change.positionChange().x
                            accumulatedX = (accumulatedX + deltaX).coerceAtLeast(0f)
                            if (accumulatedX > 10f) {
                                isDragging = true
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(accumulatedX)
                                }
                            }
                        }

                        if (isDragging) {
                            if (accumulatedX > dismissThreshold) {
                                coroutineScope.launch {
                                    haptics.selection()
                                    offsetX.animateTo(
                                        screenWidth,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                    )
                                    onDismiss()
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Scrim background
        val progress = (offsetX.value / screenWidth).coerceIn(0f, 1f)
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - progress) * 0.35f))
            )
        }

        // Draggable screen with drop shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (progress > 0f) {
                        Modifier.shadow(16.dp)
                    } else Modifier
                )
        ) {
            content()
        }
    }
}
