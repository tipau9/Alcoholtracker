package de.tipau.promille.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of SwiftUI's .background(.ultraThinMaterial) with subtle hair-thin border.
 * On Android 12+ (API 31+), applies a hardware-accelerated RenderEffect blur with translucent tinting
 * and a 0.5dp specular highlight border.
 */
fun Modifier.appleGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppColors.card.copy(alpha = 0.82f),
    borderColor: Color = AppColors.border.copy(alpha = 0.60f),
    blurRadius: Float = 25f
): Modifier = this
    .clip(shape)
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect
                    .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
    )
    .background(
        Brush.verticalGradient(
            listOf(
                backgroundColor.copy(alpha = 0.90f),
                backgroundColor.copy(alpha = 0.76f)
            )
        ),
        shape
    )
    .border(0.5.dp, borderColor, shape)
