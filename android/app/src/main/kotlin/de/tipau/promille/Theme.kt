package de.tipau.promille

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// Design tokens, mirrored 1:1 from Alcoholtracker/Theme/Colors.swift. Never
// hardcode a colour in a screen; add a token here instead.
object AppColors {
    /**
     * Set from PromilleTheme when UserProfile.highContrast flips. Snapshot state,
     * so every existing AppColors.x read recomposes on its own and no screen has
     * to thread a CompositionLocal through.
     */
    internal var highContrast by mutableStateOf(false)

    // The overrides are exactly the four in the highContrast enum of Colors.swift,
    // plus border/textDim/textMuted lifted separately: iOS covers those with a
    // blanket .contrast(1.6) on the whole tree, which Compose has no modifier
    // for, so they need their own high-contrast value instead (textMuted on
    // pure black was ~3.3:1, under WCAG AA, and background going to black only
    // made that worse).
    val background: Color get() = if (highContrast) Color.Black else Color(0xFF0A0807)
    val card: Color get() = if (highContrast) Color(0xFF111111) else Color(0xFF13100D)
    val border: Color get() = if (highContrast) Color(0xFF5A4A3E) else Color(0xFF2A211C)

    val text: Color get() = if (highContrast) Color.White else Color(0xFFF0E8D2)
    val textDim: Color get() = if (highContrast) Color(0xFFD8D0C0) else Color(0xFFA89E89)
    val textMuted: Color get() = if (highContrast) Color(0xFFB0A898) else Color(0xFF6E665B)

    val accent: Color get() = if (highContrast) Color(0xFFFFB04D) else Color(0xFFC9802F)

    val statusGreen = Color(0xFF6B9B6E)
    val statusYellow = Color(0xFFE5C158)
    val statusOrange = Color(0xFFE5A055)
    val statusRed = Color(0xFFE55050)
    val statusDarkRed = Color(0xFFB82828)
}

/**
 * Band colour for the shared BacStatus. The band itself lives in :bac, which has
 * no Compose dependency, so only the colour is attached here. There used to be a
 * second BacStatus enum in this file with fixed thresholds; it shadowed the one
 * that reads the user's thresholds and applies the status skin, which is why the
 * skin picker changed nothing.
 */
val de.tipau.promille.bac.BacStatus.color: Color
    get() = when (this) {
        de.tipau.promille.bac.BacStatus.SOBER -> AppColors.statusGreen
        de.tipau.promille.bac.BacStatus.TIPSY -> AppColors.statusYellow
        de.tipau.promille.bac.BacStatus.DRUNK -> AppColors.statusOrange
        de.tipau.promille.bac.BacStatus.CAREFUL -> AppColors.statusRed
        de.tipau.promille.bac.BacStatus.DANGER -> AppColors.statusDarkRed
    }

/**
 * UserProfile.reducedMotion. iOS kills animations globally with
 * transaction { disablesAnimations = true } (ContentView.swift:96-98); Compose
 * has no equivalent, because MotionDurationScale is fixed per recomposer and the
 * system animator scale needs WRITE_SETTINGS. So the animating call sites read
 * this and fall back to snap(), which is what .linear(duration: 0.01) is in
 * Motion.swift.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Wraps an animation spec so it collapses to snap() under reducedMotion, which
 * is what every token in Motion.swift does with .linear(duration: 0.01).
 */
@Composable
fun <T> appSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

/**
 * Multiplier applied to every sp when UserProfile.largeText is on.
 *
 * iOS uses dynamicTypeSize(.accessibility2) (Typography.swift:73), roughly 1.9x
 * for body text, and gets away with it because its type is semantic and its rows
 * size themselves. Here the flag is a plain sp multiplier over literal sizes in
 * fixed dp containers, so the ceiling is set by the containers, not by AX2.
 */
const val LARGE_TEXT_SCALE = 1.3f

/**
 * Size for the serif hero numbers, which stay fixed no matter the text scale,
 * mirroring the fixed-point bacDisplay fonts in Typography.swift:12-15. Dp
 * divides the font scale back out, so LARGE_TEXT_SCALE cancels here.
 */
@Composable
fun fixedSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

@Composable
fun PromilleTheme(
    highContrast: Boolean = false,
    largeText: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    SideEffect { AppColors.highContrast = highContrast }

    val density = LocalDensity.current
    val scaled = if (largeText) {
        Density(density.density, density.fontScale * LARGE_TEXT_SCALE)
    } else {
        density
    }

    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = AppColors.accent,
                onPrimary = AppColors.background,
                background = AppColors.background,
                onBackground = AppColors.text,
                surface = AppColors.card,
                onSurface = AppColors.text,
                outline = AppColors.border
            ),
            content = content
        )
    }
}
