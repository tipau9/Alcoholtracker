package de.tipau.promille.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Apple Fitness Awards / Apple Wallet 3D Parallax Tilt Effect.
 * Tilts the surface in 3D perspective following touch drag gestures,
 * casts a dynamic specular light sheen across the surface,
 * and bounces back with authentic Apple spring physics on release.
 */
fun Modifier.appleParallax3D(
    maxAngle: Float = 14f,
    sheenAlpha: Float = 0.22f
): Modifier = this.composed {
    val coroutineScope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }

    this
        .graphicsLayer {
            rotationX = tiltY.value * maxAngle
            rotationY = tiltX.value * maxAngle
            cameraDistance = 16f * density
        }
        .drawWithContent {
            drawContent()
            if (tiltX.value != 0f || tiltY.value != 0f) {
                // Specular light sheen that moves across the card
                val sheenCenter = Offset(
                    x = size.width * (0.5f - tiltX.value * 0.4f),
                    y = size.height * (0.5f - tiltY.value * 0.4f)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheenAlpha),
                            Color.White.copy(alpha = sheenAlpha * 0.3f),
                            Color.Transparent
                        ),
                        center = sheenCenter,
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    coroutineScope.launch {
                        tiltX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                    coroutineScope.launch {
                        tiltY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        tiltX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                    coroutineScope.launch {
                        tiltY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val halfWidth = size.width / 2f
                    val halfHeight = size.height / 2f
                    val currentX = (change.position.x - halfWidth) / halfWidth.coerceAtLeast(1f)
                    val currentY = -(change.position.y - halfHeight) / halfHeight.coerceAtLeast(1f)
                    coroutineScope.launch {
                        tiltX.snapTo(currentX.coerceIn(-1f, 1f))
                        tiltY.snapTo(currentY.coerceIn(-1f, 1f))
                    }
                }
            )
        }
}
