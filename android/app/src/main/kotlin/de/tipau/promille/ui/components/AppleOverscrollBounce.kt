package de.tipau.promille.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Apple iOS Rubber-Band Overscroll & Bounce Physics.
 *
 * Replaces Android's default Material 12 stretch effect with authentic
 * iOS UIScrollView logarithmic rubber-banding, kinetic bounce absorption,
 * and interruptible critically-damped spring snapback.
 *
 * Formula: f(x) = (x * d * c) / (d + c * |x|) where c = 0.55 (Apple coefficient).
 */
fun Modifier.appleOverscrollBounce(
    enabled: Boolean = true,
    rubberBandFactor: Float = 0.55f,
    springStiffness: Float = 380f,
    springDamping: Float = 0.82f
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val offsetAnim = remember { Animatable(0f) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }

    fun appleRubberBand(rawOffset: Float, dimension: Float): Float {
        val absVal = abs(rawOffset)
        val rubberBanded = (absVal * dimension * rubberBandFactor) / (dimension + rubberBandFactor * absVal)
        return sign(rawOffset) * rubberBanded
    }

    val nestedScrollConnection = remember(screenHeightPx, springStiffness, springDamping) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    // Interrupt running snapback animation on new user touch
                    if (offsetAnim.isRunning) {
                        scope.launch { offsetAnim.stop() }
                    }

                    // If currently in overscroll, consume drag toward 0 before scrolling content
                    val currentOffset = offsetAnim.value
                    if (currentOffset != 0f) {
                        if ((currentOffset > 0f && available.y < 0f) || (currentOffset < 0f && available.y > 0f)) {
                            rawDragOffset += available.y
                            if ((currentOffset > 0f && rawDragOffset <= 0f) || (currentOffset < 0f && rawDragOffset >= 0f)) {
                                rawDragOffset = 0f
                                scope.launch { offsetAnim.snapTo(0f) }
                                return Offset(0f, available.y)
                            } else {
                                val newBanded = appleRubberBand(rawDragOffset, screenHeightPx)
                                scope.launch { offsetAnim.snapTo(newBanded) }
                                return Offset(0f, available.y)
                            }
                        }
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When dragging reaches boundary, absorb unconsumed delta into rubber-band
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    rawDragOffset += available.y
                    val newBanded = appleRubberBand(rawDragOffset, screenHeightPx)
                    scope.launch { offsetAnim.snapTo(newBanded) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetAnim.value != 0f) {
                    rawDragOffset = 0f
                    scope.launch {
                        offsetAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = springDamping,
                                stiffness = springStiffness
                            )
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y != 0f) {
                    rawDragOffset = 0f
                    // Kinetic bounce: absorb remaining velocity into edge bounce
                    val impulse = (available.y * 0.04f).coerceIn(-140f, 140f)
                    if (abs(impulse) > 6f) {
                        scope.launch {
                            offsetAnim.animateTo(
                                targetValue = impulse,
                                animationSpec = spring(
                                    dampingRatio = 0.92f,
                                    stiffness = Spring.StiffnessHigh
                                )
                            )
                            offsetAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = springDamping,
                                    stiffness = springStiffness
                                )
                            )
                        }
                        return available
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(nestedScrollConnection)
        .graphicsLayer {
            translationY = offsetAnim.value
        }
}

/**
 * Container that disables Android Material 12 stretch glow and enables
 * pure iOS spring bounce overscroll for all child scrollables.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleBounceScrollContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.appleOverscrollBounce()
        ) {
            content()
        }
    }
}
