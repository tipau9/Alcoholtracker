package de.tipau.promille.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of SwiftUI's .background(.ultraThinMaterial): a real backdrop blur-through
 * with a subtle hair-thin border. Android has no public API for this (verified via javap
 * against the android-35 android.jar: only RenderEffect.createBlurEffect exists, which
 * blurs a node's *own* content, not what's behind it), so this draws the blur via the
 * "Haze" library instead - [hazeState] must be shared with a `Modifier.hazeSource` further
 * up the tree (the content this node should appear to float over).
 */
fun Modifier.appleGlass(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppColors.card,
    borderColor: Color = AppColors.border.copy(alpha = 0.60f)
): Modifier = this
    .clip(shape)
    .hazeEffect(
        state = hazeState,
        style = HazeStyle(
            backgroundColor = backgroundColor,
            tints = listOf(HazeTint(backgroundColor.copy(alpha = 0.55f))),
            blurRadius = 25.dp,
            noiseFactor = 0f
        )
    )
    .border(0.5.dp, borderColor, shape)
