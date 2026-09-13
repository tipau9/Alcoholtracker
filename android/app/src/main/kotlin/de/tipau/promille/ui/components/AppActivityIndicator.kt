package de.tipau.promille.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.tipau.promille.AppColors
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

/**
 * 1:1 mirror of iOS UIActivityIndicatorView (spinning tick marks with fading opacity).
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppColors.textDim
) {
    CupertinoActivityIndicator(
        modifier = modifier,
        color = color
    )
}
