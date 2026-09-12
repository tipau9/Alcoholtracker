package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of SwiftUI's .background(.ultraThinMaterial) with subtle hair-thin border.
 * Real backdrop blur (blurring what's drawn *behind* this node) needs
 * RenderEffect.createBackdropBlurEffect, only in the API 35 android.jar; this project's
 * compileSdk is 34 (that platform isn't installed here), so it can't be called yet.
 * `createBlurEffect` was tried instead but blurs the node's *own* content, which turned
 * this modifier's own icon/text children (e.g. the tab bar) into unreadable blobs. Until
 * compileSdk moves to 35+, stay translucency-only rather than reintroduce that bug.
 */
fun Modifier.appleGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppColors.card.copy(alpha = 0.82f),
    borderColor: Color = AppColors.border.copy(alpha = 0.60f),
    blurRadius: Float = 25f
): Modifier = this
    .clip(shape)
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
