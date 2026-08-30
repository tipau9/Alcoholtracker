package de.tipau.promille.invariant

import androidx.compose.ui.graphics.Color
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Requirement R4: UI & Quality Invariant Compliance
 *
 * Enforces exact theme color definitions, BacStatus threshold boundaries and
 * color mappings, and typography invariants (New York hero numbers, tabular
 * figures on live counters).
 */
class ThemeAndTypographyInvariantTest {

    private fun resolveAppDir(): File {
        val current = File(".").canonicalFile
        return if (current.name == "app") current else File(current, "app")
    }

    private fun Color.hexString(): String {
        val a = (alpha * 255.0f + 0.5f).toInt() and 0xFF
        val r = (red * 255.0f + 0.5f).toInt() and 0xFF
        val g = (green * 255.0f + 0.5f).toInt() and 0xFF
        val b = (blue * 255.0f + 0.5f).toInt() and 0xFF
        return String.format("0x%02X%02X%02X%02X", a, r, g, b)
    }

    @Test
    fun `AppColors tokens match exact RGBA hexadecimal definitions and full opacity`() {
        val expectedHexTokens = mapOf(
            "background" to (AppColors.background to "0xFF0A0807"),
            "card" to (AppColors.card to "0xFF13100D"),
            "border" to (AppColors.border to "0xFF2A211C"),
            "text" to (AppColors.text to "0xFFF0E8D2"),
            "textDim" to (AppColors.textDim to "0xFFA89E89"),
            "textMuted" to (AppColors.textMuted to "0xFF6E665B"),
            "accent" to (AppColors.accent to "0xFFC9802F"),
            "statusGreen" to (AppColors.statusGreen to "0xFF6B9B6E"),
            "statusYellow" to (AppColors.statusYellow to "0xFFE5C158"),
            "statusOrange" to (AppColors.statusOrange to "0xFFE5A055"),
            "statusRed" to (AppColors.statusRed to "0xFFE55050"),
            "statusDarkRed" to (AppColors.statusDarkRed to "0xFFB82828")
        )

        for ((name, pair) in expectedHexTokens) {
            val (color, expectedHex) = pair
            assertEquals(
                expectedHex,
                color.hexString(),
                "Color token '$name' hex mismatch: expected $expectedHex but got ${color.hexString()}"
            )
            assertEquals(
                1.0f,
                color.alpha,
                "Color token '$name' must be fully opaque (alpha = 1.0f)"
            )
        }
    }

    @Test
    fun `high contrast swaps the four tokens the iOS palette overrides`() {
        try {
            AppColors.highContrast = true
            assertEquals(Color.Black, AppColors.background)
            assertEquals(Color(0xFF111111), AppColors.card)
            assertEquals(Color.White, AppColors.text)
            assertEquals(Color(0xFFFFB04D), AppColors.accent)
        } finally {
            AppColors.highContrast = false
        }
        assertEquals("0xFF0A0807", AppColors.background.hexString())
    }

    @Test
    fun `high contrast also lifts border, textDim and textMuted since Compose has no global contrast filter`() {
        // iOS covers these with a blanket .contrast(1.6) on the whole tree; Android
        // has no equivalent, so these three need their own high-contrast value or
        // they'd stay unreadably dim against the black high-contrast background.
        val normalDim = AppColors.textDim
        val normalMuted = AppColors.textMuted
        val normalBorder = AppColors.border
        try {
            AppColors.highContrast = true
            assertNotEquals(normalDim, AppColors.textDim)
            assertNotEquals(normalMuted, AppColors.textMuted)
            assertNotEquals(normalBorder, AppColors.border)
        } finally {
            AppColors.highContrast = false
        }
        assertEquals(normalDim, AppColors.textDim)
        assertEquals(normalMuted, AppColors.textMuted)
        assertEquals(normalBorder, AppColors.border)
    }

    @Test
    fun `BacStatus enum defines 5 standard bands with correct German names and color associations`() {
        assertEquals(5, BacStatus.entries.size, "BacStatus must define exactly 5 status bands")

        assertEquals("Nüchtern", BacStatus.SOBER.germanName)
        assertEquals(AppColors.statusGreen, BacStatus.SOBER.color)

        assertEquals("Leicht beschwipst", BacStatus.TIPSY.germanName)
        assertEquals(AppColors.statusYellow, BacStatus.TIPSY.color)

        assertEquals("Beschwipst", BacStatus.DRUNK.germanName)
        assertEquals(AppColors.statusOrange, BacStatus.DRUNK.color)

        assertEquals("Fahruntüchtig", BacStatus.CAREFUL.germanName)
        assertEquals(AppColors.statusRed, BacStatus.CAREFUL.color)

        assertEquals("Gefährlich", BacStatus.DANGER.germanName)
        assertEquals(AppColors.statusDarkRed, BacStatus.DANGER.color)
    }

    @Test
    fun `BacStatus of correctly classifies BAC values and boundary thresholds`() {
        // SOBER (< 0.01)
        assertEquals(BacStatus.SOBER, BacStatus.of(0.00))
        assertEquals(BacStatus.SOBER, BacStatus.of(0.005))
        assertEquals(BacStatus.SOBER, BacStatus.of(0.00999))

        // TIPSY (0.01 ..< 0.30)
        assertEquals(BacStatus.TIPSY, BacStatus.of(0.01))
        assertEquals(BacStatus.TIPSY, BacStatus.of(0.15))
        assertEquals(BacStatus.TIPSY, BacStatus.of(0.2999))

        // DRUNK (0.30 ..< 0.80)
        assertEquals(BacStatus.DRUNK, BacStatus.of(0.30))
        assertEquals(BacStatus.DRUNK, BacStatus.of(0.50))
        assertEquals(BacStatus.DRUNK, BacStatus.of(0.7999))

        // CAREFUL (0.80 ..< 1.50)
        assertEquals(BacStatus.CAREFUL, BacStatus.of(0.80))
        assertEquals(BacStatus.CAREFUL, BacStatus.of(1.20))
        assertEquals(BacStatus.CAREFUL, BacStatus.of(1.4999))

        // DANGER (>= 1.50)
        assertEquals(BacStatus.DANGER, BacStatus.of(1.50))
        assertEquals(BacStatus.DANGER, BacStatus.of(2.00))
        assertEquals(BacStatus.DANGER, BacStatus.of(3.50))
        assertEquals(BacStatus.DANGER, BacStatus.of(10.00))
    }

    @Test
    fun `typography invariants enforce AppSerif for hero numbers and tabular figures for technical counters`() {
        val uiSrc = File(resolveAppDir(), "src/main/kotlin/de/tipau/promille/ui")
        assertTrue(uiSrc.exists(), "UI source directory missing: ${uiSrc.absolutePath}")

        val uiFiles = uiSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(uiFiles.isNotEmpty(), "Found no UI Kotlin source files to scan")

        // 1. Hero number displays must use the New York family, not the raw M3 Serif
        val heroDisplayFiles = listOf(
            "BACDisplaySection.kt",
            "FullScreenBacChart.kt",
            "TrendsView.kt",
            "HomeCards.kt",
            "SessionScreen.kt",
            "ForecastView.kt",
            "QuickAddSheet.kt",
            "BottleModeSheet.kt"
        )

        for (fileName in heroDisplayFiles) {
            val file = uiFiles.find { it.name == fileName }
            assertTrue(file != null, "Expected UI file $fileName not found")
            val content = file.readText()
            assertTrue(
                content.contains("AppSerif"),
                "Hero number screen $fileName must use AppSerif for prominent BAC / amount readouts"
            )
        }

        // 2. Technical counters, stopwatches and live arcade scores must use tabular
        // figures. iOS does this with .monospacedDigit(), never a monospaced face.
        val tabularFiles = listOf(
            "FullScreenBacChart.kt",
            "WaterContestSheet.kt",
            "JamView.kt"
        )

        for (fileName in tabularFiles) {
            val file = uiFiles.find { it.name == fileName }
            assertTrue(file != null, "Expected UI file $fileName not found")
            val content = file.readText()
            assertTrue(
                content.contains("TabularFigures"),
                "Technical counter screen $fileName must use TabularFigures for tabular alignment"
            )
        }

        // 3. No raw M3 families anywhere in the UI. Serif and Monospace resolve to
        // Noto Serif and Droid Sans Mono on Android, which is not what iOS renders.
        val forbidden = listOf("FontFamily.Cursive", "FontFamily.Serif", "FontFamily.Monospace")
        for (file in uiFiles) {
            val content = file.readText()
            for (family in forbidden) {
                assertTrue(
                    !content.contains(family),
                    "Forbidden $family found in ${file.name}; use AppSerif / AppSans + TabularFigures"
                )
            }
        }
    }
}
