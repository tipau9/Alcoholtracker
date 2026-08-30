package de.tipau.promille.data

import de.tipau.promille.bac.BacProjectionInput
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.StomachStatus
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only checks for the parts of the data layer that can silently corrupt a
 * permille number: the generated catalog and the Room <-> :bac mapping. DAO
 * behaviour needs a device and is not covered here.
 */
class DataLayerTest {

    // AGP runs unit tests with the module directory as the working directory,
    // so the packaged asset is readable without an Android Context.
    private val catalog = DrinkCatalog.parse(
        File("src/main/assets/drink_catalog.json").readText()
    )

    @Test
    fun `the generated catalog carries every entry and the seed version`() {
        assertEquals(6, catalog.version, "the seed gate compares against DrinkDatabase.catalogVersion")
        assertEquals(591, catalog.drinks.size, "rerun android/tools/extract_drink_catalog.py")
        assertEquals(
            catalog.drinks.size, catalog.drinks.map { it.name }.toSet().size,
            "seedIfNeeded skips duplicates by name, so a repeat would silently vanish"
        )
    }

    @Test
    fun `every catalog category is a known raw value`() {
        for (entry in catalog.drinks) {
            val category = DrinkCategory.from(entry.category)
            assertTrue(
                category.raw == entry.category,
                "${entry.name} has category '${entry.category}', which fell back to ${category.raw}"
            )
            assertTrue(entry.volumeML > 0, "${entry.name} has no volume")
            assertTrue(entry.abv in 0.0..100.0, "${entry.name} has abv ${entry.abv}")
        }
    }

    @Test
    fun `a catalog entry becomes a template and then a drink unchanged`() {
        val entry = catalog.drinks.first { it.name == "Augustiner Helle" }
        val template = with(DrinkCatalog) { entry.toEntity() }
        assertEquals(entry.volumeML, template.volume)
        assertEquals(entry.abv, template.abv)
        assertEquals(entry.category, template.categoryRaw)
        assertEquals(template.id, normalizeId(template.id), "ids are stored lowercase")
    }

    @Test
    fun `a drink survives the round trip through a Room row`() {
        val original = de.tipau.promille.bac.Drink(
            id = "8B0A5C2E-1F4D-4A21-9C77-0E3B6D5A1122",
            name = "Augustiner Helle",
            volumeML = 500.0,
            abv = 5.2,
            calories = 210,
            iconName = "mug.fill",
            category = DrinkCategory.BEER,
            timestampEpochSeconds = 1_781_200_800L,
            templateId = "C4E2A9D0-7B31-4F58-A6E1-2D93F5B0C874",
            mixerVolumeML = 200.0,
            mixerWaterContentPercent = 90.0,
            drinkDurationMinutes = 18.0
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original.copy(id = original.id.lowercase(),
            templateId = original.templateId?.lowercase()), restored)
        assertEquals(original.alcoholGrams, restored.alcoholGrams)
    }

    @Test
    fun `a stored profile drives the engine exactly like a hand built one`() {
        val row = defaultProfileEntity(nowEpochSeconds = 1_781_200_800L).copy(
            weight = 82.0, height = 184.0, age = 31, genderRaw = "male",
            eliminationRate = 0.17, stomachStatusRaw = "full", conservativeSafety = true
        )
        val profile = row.toDomain()
        assertEquals(Gender.MALE, profile.gender)
        assertEquals(StomachStatus.FULL, profile.defaultStomachStatus)
        assertTrue(profile.conservativeForSafety)

        val drinks = listOf(
            catalog.drinks.first { it.name == "Augustiner Helle" }
                .let { with(DrinkCatalog) { it.toEntity() } }
                .let { template ->
                    DrinkEntity(
                        id = newId(), name = template.name, volume = template.volume,
                        abv = template.abv, calories = template.calories,
                        iconName = template.iconName, categoryRaw = template.categoryRaw,
                        timestampEpochSeconds = 1_781_200_800L, drinkDurationMinutes = 20.0
                    )
                }
        )
        val input = BacProjectionInput(
            drinks = drinks.map { it.toDomain() },
            profile = profile,
            stomachStatus = profile.defaultStomachStatus,
            conservative = profile.conservativeForSafety
        )
        val bac = input.currentBac(1_781_200_800L + 60 * 60)
        assertTrue(bac > 0.0, "one half litre of Helles must register, got $bac")
        assertTrue(bac < 1.0, "one beer must not read as near drunk, got $bac")

        // Writing the domain values back must not disturb the UI-only columns.
        val written = row.applying(profile)
        assertEquals(row.accentColorHex, written.accentColorHex)
        assertEquals(row.birthDate, written.birthDate)
        assertTrue(abs(written.eliminationRate - 0.17) < 1e-12)
    }
}
