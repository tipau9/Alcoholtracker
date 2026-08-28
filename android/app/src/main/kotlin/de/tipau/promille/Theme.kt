package de.tipau.promille

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design tokens, mirrored 1:1 from Alcoholtracker/Theme/Colors.swift. Never
// hardcode a colour in a screen; add a token here instead.
object AppColors {
    val background = Color(0xFF0A0807)
    val card = Color(0xFF13100D)
    val border = Color(0xFF2A211C)

    val text = Color(0xFFF0E8D2)
    val textDim = Color(0xFFA89E89)
    val textMuted = Color(0xFF6E665B)

    val accent = Color(0xFFC9802F)

    val statusGreen = Color(0xFF6B9B6E)
    val statusYellow = Color(0xFFE5C158)
    val statusOrange = Color(0xFFE5A055)
    val statusRed = Color(0xFFE55050)
    val statusDarkRed = Color(0xFFB82828)
}

/** Same five bands as the iOS BACStatus, with the default thresholds. */
enum class BacStatus(val germanName: String, val color: Color) {
    SOBER("Nüchtern", AppColors.statusGreen),
    TIPSY("Leicht beschwipst", AppColors.statusYellow),
    DRUNK("Beschwipst", AppColors.statusOrange),
    CAREFUL("Fahruntüchtig", AppColors.statusRed),
    DANGER("Gefährlich", AppColors.statusDarkRed);

    companion object {
        fun of(bac: Double): BacStatus = when {
            bac < 0.01 -> SOBER
            bac < 0.30 -> TIPSY
            bac < 0.80 -> DRUNK
            bac < 1.50 -> CAREFUL
            else -> DANGER
        }
    }
}

@Composable
fun PromilleTheme(content: @Composable () -> Unit) {
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
