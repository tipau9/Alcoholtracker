package de.tipau.promille.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import de.tipau.promille.LocalReducedMotion

/**
 * Controller coordinating the iOS Card Stack presentation effect across the app.
 * When a modal sheet opens, the underlying screen scales down to 0.93x with 24dp
 * rounded corners, perfectly recreating the iOS modal card stack hierarchy.
 */
class CardStackController {
    var activeSheetCount by mutableIntStateOf(0)
        private set

    val isSheetActive: Boolean
        get() = activeSheetCount > 0

    fun pushSheet() {
        activeSheetCount++
    }

    fun popSheet() {
        if (activeSheetCount > 0) {
            activeSheetCount--
        }
    }
}

val LocalCardStackController = compositionLocalOf { CardStackController() }

/**
 * Declares that a sheet or modal is currently active in the composition,
 * triggering the card-stack scale-down on the parent content.
 */
@Composable
fun CardStackSheetEffect(active: Boolean) {
    val controller = LocalCardStackController.current
    DisposableEffect(active) {
        if (active) {
            controller.pushSheet()
        }
        onDispose {
            if (active) {
                controller.popSheet()
            }
        }
    }
}

/**
 * Container wrapping the root navigation hierarchy.
 * Scales down smoothly to 0.93x and rounds its corners when [isSheetOpen] is true.
 */
@Composable
fun CardStackContainer(
    isSheetOpen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current

    val scale by animateFloatAsState(
        targetValue = if (isSheetOpen && !reducedMotion) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardStackScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isSheetOpen && !reducedMotion) 24.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardStackCorners"
    )

    val dimAlpha by animateFloatAsState(
        targetValue = if (isSheetOpen) 0.30f else 0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardStackDim"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    clip = cornerRadius > 0.dp
                    shape = RoundedCornerShape(cornerRadius)
                }
        ) {
            content()
            if (dimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha))
                )
            }
        }
    }
}
