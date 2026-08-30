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
import de.tipau.promille.data.UserProfileEntity

// Design tokens, mirrored 1:1 from Alcoholtracker/Theme/Colors.swift.
object AppColors {
    internal var highContrast by mutableStateOf(false)
    internal var accentColorHex by mutableStateOf("C9802F")

    val background: Color get() = if (highContrast) Color.Black else Color(0xFF0A0807)
    val card: Color get() = if (highContrast) Color(0xFF111111) else Color(0xFF13100D)
    val border: Color get() = if (highContrast) Color(0xFF5A4A3E) else Color(0xFF2A211C)

    val text: Color get() = if (highContrast) Color.White else Color(0xFFF0E8D2)
    val textDim: Color get() = if (highContrast) Color(0xFFD8D0C0) else Color(0xFFA89E89)
    val textMuted: Color get() = if (highContrast) Color(0xFFB0A898) else Color(0xFF6E665B)

    val accent: Color get() {
        if (highContrast) return Color(0xFFFFB04D)
        val clean = accentColorHex.removePrefix("#").trim()
        val value = clean.toLongOrNull(16) ?: 0xC9802FL
        val argb = if (clean.length <= 6) 0xFF000000L or value else value
        return Color(argb.toInt())
    }

    val statusGreen = Color(0xFF6B9B6E)
    val statusYellow = Color(0xFFE5C158)
    val statusOrange = Color(0xFFE5A055)
    val statusRed = Color(0xFFE55050)
    val statusDarkRed = Color(0xFFB82828)
}

/**
 * Singleton bridging UserProfile accessibility flags and accent color into UI.
 * Mirrors Alcoholtracker/Services/AppTheme.swift 1:1.
 */
class AppTheme private constructor() {
    companion object {
        val shared = AppTheme()
    }

    var highContrast: Boolean by mutableStateOf(false)
    var reducedMotion: Boolean by mutableStateOf(false)
    var largeText: Boolean by mutableStateOf(false)
    var accentColorHex: String by mutableStateOf("C9802F")

    val accentColor: Color get() = AppColors.accent

    fun sync(profile: UserProfileEntity?) {
        if (profile == null) return
        highContrast = profile.highContrast
        reducedMotion = profile.reducedMotion
        largeText = profile.largeText
        accentColorHex = profile.accentColorHex
        AppColors.highContrast = profile.highContrast
        AppColors.accentColorHex = profile.accentColorHex
    }
}

val de.tipau.promille.bac.BacStatus.color: Color
    get() = when (this) {
        de.tipau.promille.bac.BacStatus.SOBER -> AppColors.statusGreen
        de.tipau.promille.bac.BacStatus.TIPSY -> AppColors.statusYellow
        de.tipau.promille.bac.BacStatus.DRUNK -> AppColors.statusOrange
        de.tipau.promille.bac.BacStatus.CAREFUL -> AppColors.statusRed
        de.tipau.promille.bac.BacStatus.DANGER -> AppColors.statusDarkRed
    }

val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun <T> appSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

const val LARGE_TEXT_SCALE = 1.3f

@Composable
fun fixedSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

@Composable
fun PromilleTheme(
    highContrast: Boolean = false,
    largeText: Boolean = false,
    reducedMotion: Boolean = false,
    accentColorHex: String = "C9802F",
    content: @Composable () -> Unit
) {
    SideEffect {
        AppColors.highContrast = highContrast
        AppColors.accentColorHex = accentColorHex
    }

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
            typography = appTypography(),
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
