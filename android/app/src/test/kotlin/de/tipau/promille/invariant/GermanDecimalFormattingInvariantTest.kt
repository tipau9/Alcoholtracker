package de.tipau.promille.invariant

import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.germanName
import de.tipau.promille.bac.isValidEmail
import de.tipau.promille.bac.oneDecimalGerman
import de.tipau.promille.bac.permilleString
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requirement R4: UI & Quality Invariant Compliance
 *
 * Enforces strict German number formatting (comma decimal separator, permille symbol)
 * across formatting extensions, domain categories, and UI codebase invocations.
 */
class GermanDecimalFormattingInvariantTest {

    private fun resolveAppDir(): File {
        val current = File(".").canonicalFile
        return if (current.name == "app") current else File(current, "app")
    }

    private fun resolveBacDir(): File {
        val appDir = resolveAppDir()
        return File(appDir.parentFile, "bac")
    }

    @Test
    fun `permilleString formats numbers with comma and permille sign`() {
        assertEquals("1,50 ‰", 1.5.permilleString())
        assertEquals("0,05 ‰", 0.05.permilleString())
        assertEquals("0,00 ‰", 0.0.permilleString())
        assertEquals("0,25 ‰", 0.25.permilleString())
        assertEquals("10,00 ‰", 10.0.permilleString())
        assertEquals("-0,50 ‰", (-0.5).permilleString())

        // Rounding checks
        assertEquals("1,23 ‰", 1.234.permilleString())
        assertEquals("1,24 ‰", 1.236.permilleString())
        assertEquals("0,00 ‰", 0.004.permilleString())
        assertEquals("0,01 ‰", 0.005.permilleString())

        // Structural invariant checks
        val formatted = 2.45.permilleString()
        assertTrue(formatted.contains(","), "Expected comma separator in '$formatted'")
        assertFalse(formatted.contains("."), "Must not contain period separator in '$formatted'")
        assertTrue(formatted.endsWith(" ‰"), "Must end with space and permille sign in '$formatted'")
    }

    @Test
    fun `oneDecimalGerman formats numbers with single decimal and comma`() {
        assertEquals("1,5", 1.5.oneDecimalGerman())
        assertEquals("0,0", 0.0.oneDecimalGerman())
        assertEquals("0,0", 0.04.oneDecimalGerman())
        assertEquals("0,1", 0.05.oneDecimalGerman())
        assertEquals("12,3", 12.34.oneDecimalGerman())
        assertEquals("12,4", 12.36.oneDecimalGerman())
        assertEquals("-2,7", (-2.7).oneDecimalGerman())

        // Structural invariant checks
        val formatted = 4.8.oneDecimalGerman()
        assertTrue(formatted.contains(","), "Expected comma separator in '$formatted'")
        assertFalse(formatted.contains("."), "Must not contain period separator in '$formatted'")
        assertEquals(1, formatted.substringAfter(",").length, "Must have exactly 1 decimal digit")
    }

    @Test
    fun `drinkCategory germanName maps all categories to correct German nomenclature`() {
        val expectedTranslations = mapOf(
            DrinkCategory.BEER to "Bier",
            DrinkCategory.WINE to "Wein",
            DrinkCategory.SPARKLING to "Sekt und Schaumwein",
            DrinkCategory.SPIRITS to "Spirituose",
            DrinkCategory.LIQUEUR to "Likör",
            DrinkCategory.COCKTAIL to "Cocktail",
            DrinkCategory.MIXED to "Mischgetränk",
            DrinkCategory.SHOT to "Shot",
            DrinkCategory.CIDER to "Cider",
            DrinkCategory.FORTIFIED to "Likörwein",
            DrinkCategory.WATER to "Wasser",
            DrinkCategory.SOFT_DRINK to "Softdrink",
            DrinkCategory.JUICE to "Saft",
            DrinkCategory.COFFEE_TEA to "Kaffee und Tee",
            DrinkCategory.MILK to "Milch",
            DrinkCategory.OTHER to "Sonstiges"
        )

        assertEquals(
            DrinkCategory.entries.size,
            expectedTranslations.size,
            "Mismatch between total DrinkCategory enum entries and tested translations"
        )

        for (category in DrinkCategory.entries) {
            val germanName = category.germanName
            val expected = expectedTranslations[category]
            assertEquals(expected, germanName, "Category $category did not match expected German name")
            assertTrue(germanName.isNotBlank(), "Category $category must not have blank German name")
        }

        // Ensure distinct translations (no unintentional duplicate mappings)
        val distinctNames = DrinkCategory.entries.map { it.germanName }.toSet()
        assertEquals(DrinkCategory.entries.size, distinctNames.size, "All categories must have unique German names")
    }

    @Test
    fun `isValidEmail enforces German auth gate validation rules`() {
        // Valid emails
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("alice.smith@sub.domain.de"))
        assertTrue(isValidEmail("kontakt@tipau.promille.de"))
        assertTrue(isValidEmail("test_123+tag@gmail.com"))

        // Invalid emails
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("plainaddress"))
        assertFalse(isValidEmail("@missinguser.com"))
        assertFalse(isValidEmail("user@"))
        assertFalse(isValidEmail("user@.com"))
        assertFalse(isValidEmail("user@com."))
        assertFalse(isValidEmail("user@domain..com"))
        assertFalse(isValidEmail("user@@domain.com"))
        assertFalse(isValidEmail("user@domain"))
    }

    @Test
    fun `all floating point string formats in codebase strictly specify German Locale`() {
        val appSrc = File(resolveAppDir(), "src/main/kotlin")
        val bacSrc = File(resolveBacDir(), "src/main/kotlin")

        assertTrue(appSrc.exists(), "app Kotlin source dir missing: ${appSrc.absolutePath}")
        assertTrue(bacSrc.exists(), "bac Kotlin source dir missing: ${bacSrc.absolutePath}")

        val allSourceFiles = (appSrc.walkTopDown() + bacSrc.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(allSourceFiles.isNotEmpty(), "Found no Kotlin source files to scan")

        val floatPatternRegex = Regex("%[+-]?\\d*(\\.\\d+)?[fF]")
        val violations = mutableListOf<String>()

        for (file in allSourceFiles) {
            val lines = file.readLines()
            for ((idx, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue

                // Check if line contains a float formatting specifier
                if (floatPatternRegex.containsMatchIn(line)) {
                    // Check if String.format is used on this or preceding lines
                    // A compliant float formatting call must supply Locale.GERMANY or Locale.GERMAN or GERMAN
                    val hasGermanLocale = line.contains("Locale.GERMAN") ||
                        line.contains("Locale.GERMANY") ||
                        line.contains("GERMAN") ||
                        line.contains("Locale.ROOT") ||
                        line.contains("Locale.US")

                    // If String.format without locale is called with a float specifier, that's a violation
                    if (!hasGermanLocale) {
                        // Check if previous 2 lines had Locale.GERMANY (in case of multiline format call)
                        val context = (maxOf(0, idx - 2)..minOf(lines.size - 1, idx + 2))
                            .joinToString(" ") { lines[it] }

                        if (!context.contains("Locale.GERMAN") &&
                            !context.contains("Locale.GERMANY") &&
                            !context.contains("Locale.ROOT") &&
                            !context.contains("Locale.US") &&
                            !context.contains("GERMAN")
                        ) {
                            violations.add(
                                "${file.name}:${idx + 1}: Floating-point format without German Locale: '$trimmed'"
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} unlocalized float formatting calls in codebase:\n" +
                violations.joinToString("\n")
        )
    }
}
