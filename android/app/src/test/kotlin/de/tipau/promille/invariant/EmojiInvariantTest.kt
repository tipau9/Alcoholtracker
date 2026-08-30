package de.tipau.promille.invariant

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requirement R4: UI & Quality Invariant Compliance
 *
 * Enforces the strict zero-emoji policy across all app assets, XML resources,
 * and source code, with explicit assertions for approved domain exceptions.
 */
class EmojiInvariantTest {

    object EmojiDetector {
        fun isEmojiCodePoint(codePoint: Int): Boolean {
            return when (codePoint) {
                in 0x1F600..0x1F64F -> true // Emoticons (😄, 😬, etc.)
                in 0x1F300..0x1F5FF -> true // Misc Symbols and Pictographs (🍻, etc.)
                in 0x1F680..0x1F6FF -> true // Transport and Map Symbols
                in 0x1F700..0x1F77F -> true // Alchemical Symbols
                in 0x1F780..0x1F7FF -> true // Geometric Shapes Extended (🟢, 🟡, 🟠, 🔴, etc.)
                in 0x1F800..0x1F8FF -> true // Supplemental Arrows-C
                in 0x1F900..0x1F9FF -> true // Supplemental Symbols and Pictographs (🤢, 💪, etc.)
                in 0x1FA00..0x1FA6F -> true // Chess Symbols
                in 0x1FA70..0x1FAFF -> true // Symbols and Pictographs Extended-A
                in 0x1F1E6..0x1F1FF -> true // Regional Indicator Symbols (flags)
                in 0x2600..0x26FF -> true   // Misc Symbols (☕, ⚡, ⚠️, etc.)
                in 0x2700..0x27BF -> true   // Dingbats (✨, ❌, etc.)
                0x1F6AB -> true            // Prohibited sign 🚫
                in 0x2B05..0x2B07 -> true   // Misc Symbols and Arrows, emoji arrows
                in 0x2B1B..0x2B1C -> true   // Black and white large squares
                0x2B50 -> true             // Star ⭐
                0x2B55 -> true             // Heavy large circle ⭕
                else -> false
            }
        }

        data class EmojiOccurrence(
            val codePoint: Int,
            val charString: String,
            val line: Int,
            val column: Int,
            val lineSnippet: String
        )

        fun findEmojis(text: String): List<EmojiOccurrence> {
            val occurrences = mutableListOf<EmojiOccurrence>()
            val lines = text.lines()
            for ((lineIndex, line) in lines.withIndex()) {
                var i = 0
                while (i < line.length) {
                    val codePoint = line.codePointAt(i)
                    if (isEmojiCodePoint(codePoint)) {
                        occurrences.add(
                            EmojiOccurrence(
                                codePoint = codePoint,
                                charString = String(Character.toChars(codePoint)),
                                line = lineIndex + 1,
                                column = i + 1,
                                lineSnippet = line.trim()
                            )
                        )
                    }
                    i += Character.charCount(codePoint)
                }
            }
            return occurrences
        }
    }

    private fun resolveAppDir(): File {
        val current = File(".").canonicalFile
        return if (current.name == "app") current else File(current, "app")
    }

    private fun resolveBacDir(): File {
        val appDir = resolveAppDir()
        return File(appDir.parentFile, "bac")
    }

    @Test
    fun `emoji detector accurately identifies positive emoji samples`() {
        val positiveSamples = listOf(
            "😄", "😀", "😐", "😬", "🤢", "💪", "👍", "👋",
            "🍻", "🍺", "🍷", "🍕", "🟢", "🟡", "🟠", "🔴",
            "🚫", "⚠️", "⭐", "❤️", "☕", "🎉", "🔥"
        )
        for (sample in positiveSamples) {
            val occurrences = EmojiDetector.findEmojis(sample)
            assertTrue(
                occurrences.isNotEmpty(),
                "Expected emoji in sample: '$sample', but none detected"
            )
        }

        val mixedSentence = "Guten Abend! 😄 Wir trinken 🍻 und haben Spass."
        val found = EmojiDetector.findEmojis(mixedSentence)
        assertEquals(2, found.size, "Expected exactly 2 emojis in mixed sentence")
        assertEquals("😄", found[0].charString)
        assertEquals("🍻", found[1].charString)
    }

    @Test
    fun `emoji detector does not trigger false positives on German characters and typography`() {
        val negativeSamples = listOf(
            "0,25 ‰ Promille",
            "1,50 ‰",
            "Äpfel, Öle, Übermut und Straße",
            "äöüÄÖÜß",
            "Preis: 12,50 € / $15.00",
            "100% Vol. · 500 ml",
            "Titel — Untertitel – Bereich",
            "Punkt · Aufzählung • Element",
            "x <= y && a >= b || c != d",
            "10 ± 2 ms",
            "Grad: 20°C",
            "alpha = 0xFF0A0807L"
        )
        for (sample in negativeSamples) {
            val occurrences = EmojiDetector.findEmojis(sample)
            assertTrue(
                occurrences.isEmpty(),
                "False positive detected in sample '$sample': ${occurrences.map { it.charString }}"
            )
        }
    }

    @Test
    fun `zero emojis in German strings_xml`() {
        val stringsXml = File(resolveAppDir(), "src/main/res/values/strings.xml")
        assertTrue(stringsXml.exists(), "strings.xml not found at ${stringsXml.absolutePath}")

        val content = stringsXml.readText()
        val occurrences = EmojiDetector.findEmojis(content)
        assertTrue(
            occurrences.isEmpty(),
            "Found ${occurrences.size} unexpected emojis in ${stringsXml.name}: " +
                occurrences.joinToString { "${it.charString} (line ${it.line}:${it.column})" }
        )
    }

    @Test
    fun `zero emojis in English strings_xml`() {
        val stringsXmlEn = File(resolveAppDir(), "src/main/res/values-en/strings.xml")
        assertTrue(stringsXmlEn.exists(), "values-en/strings.xml not found at ${stringsXmlEn.absolutePath}")

        val content = stringsXmlEn.readText()
        val occurrences = EmojiDetector.findEmojis(content)
        assertTrue(
            occurrences.isEmpty(),
            "Found ${occurrences.size} unexpected emojis in ${stringsXmlEn.name}: " +
                occurrences.joinToString { "${it.charString} (line ${it.line}:${it.column})" }
        )
    }

    @Test
    fun `zero emojis in drink catalog asset`() {
        val catalogJson = File(resolveAppDir(), "src/main/assets/drink_catalog.json")
        assertTrue(catalogJson.exists(), "drink_catalog.json not found at ${catalogJson.absolutePath}")

        val content = catalogJson.readText()
        val occurrences = EmojiDetector.findEmojis(content)
        assertTrue(
            occurrences.isEmpty(),
            "Found ${occurrences.size} unexpected emojis in drink_catalog.json: " +
                occurrences.joinToString { "${it.charString} (line ${it.line}:${it.column})" }
        )
    }

    @Test
    fun `zero emojis in Kotlin source code except approved exceptions`() {
        val appSrc = File(resolveAppDir(), "src/main/kotlin")
        val bacSrc = File(resolveBacDir(), "src/main/kotlin")

        assertTrue(appSrc.exists(), "app Kotlin source dir missing: ${appSrc.absolutePath}")
        assertTrue(bacSrc.exists(), "bac Kotlin source dir missing: ${bacSrc.absolutePath}")

        val allSourceFiles = (appSrc.walkTopDown() + bacSrc.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(allSourceFiles.isNotEmpty(), "Found no Kotlin source files to scan")

        // Explicit set of files permitted to contain specific emojis
        val approvedExceptions = mapOf(
            "MorningMoodPrompt.kt" to setOf("😄", "💪", "😬", "🤢"),
            "DayDetailSheet.kt" to setOf("😐", "😄", "💪", "😬", "🤢"),
            "PersonalInsights.kt" to setOf("😐", "😄", "💪", "😬", "🤢"),
            "RoundRouletteSheet.kt" to setOf("🍻"),
            "StatusSkin.kt" to setOf("🟢", "🟡", "🟠", "🔴", "🚫")
        )

        val violations = mutableListOf<String>()

        for (file in allSourceFiles) {
            val content = file.readText()
            val occurrences = EmojiDetector.findEmojis(content)
            if (occurrences.isEmpty()) continue

            val allowedEmojis = approvedExceptions[file.name]
            if (allowedEmojis == null) {
                // File is not permitted to contain any emojis
                for (occ in occurrences) {
                    violations.add(
                        "${file.name}:${occ.line}:${occ.column} contains unauthorized emoji '${occ.charString}': '${occ.lineSnippet}'"
                    )
                }
            } else {
                // File is permitted, but only with specific approved emojis
                for (occ in occurrences) {
                    if (occ.charString !in allowedEmojis) {
                        violations.add(
                            "${file.name}:${occ.line}:${occ.column} contains unapproved emoji '${occ.charString}' (allowed: $allowedEmojis): '${occ.lineSnippet}'"
                        )
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found ${violations.size} emoji violations in source code:\n" + violations.joinToString("\n")
        )
    }

    @Test
    fun `approved exception files contain exactly their expected domain emojis`() {
        val morningMoodPromptFile = File(resolveAppDir(), "src/main/kotlin/de/tipau/promille/ui/components/MorningMoodPrompt.kt")
        assertTrue(morningMoodPromptFile.exists())
        val morningEmojis = EmojiDetector.findEmojis(morningMoodPromptFile.readText()).map { it.charString }.toSet()
        assertEquals(setOf("😄", "💪", "😬", "🤢"), morningEmojis)

        val dayDetailSheetFile = File(resolveAppDir(), "src/main/kotlin/de/tipau/promille/ui/screens/history/DayDetailSheet.kt")
        assertTrue(dayDetailSheetFile.exists())
        val dayDetailEmojis = EmojiDetector.findEmojis(dayDetailSheetFile.readText()).map { it.charString }.toSet()
        assertEquals(setOf("😐", "😄", "💪", "😬", "🤢"), dayDetailEmojis)

        val personalInsightsFile = File(resolveBacDir(), "src/main/kotlin/de/tipau/promille/bac/PersonalInsights.kt")
        assertTrue(personalInsightsFile.exists())
        val insightsEmojis = EmojiDetector.findEmojis(personalInsightsFile.readText()).map { it.charString }.toSet()
        assertEquals(setOf("😐", "😄", "💪", "😬", "🤢"), insightsEmojis)

        val rouletteFile = File(resolveAppDir(), "src/main/kotlin/de/tipau/promille/ui/screens/jam/RoundRouletteSheet.kt")
        assertTrue(rouletteFile.exists())
        val rouletteEmojis = EmojiDetector.findEmojis(rouletteFile.readText()).map { it.charString }.toSet()
        assertEquals(setOf("🍻"), rouletteEmojis)

        val statusSkinFile = File(resolveBacDir(), "src/main/kotlin/de/tipau/promille/bac/StatusSkin.kt")
        assertTrue(statusSkinFile.exists())
        val statusSkinEmojis = EmojiDetector.findEmojis(statusSkinFile.readText()).map { it.charString }.toSet()
        assertEquals(setOf("🟢", "🟡", "🟠", "🔴", "🚫"), statusSkinEmojis)
    }
}
