package de.tipau.promille.bac

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins this Kotlin engine to the numbers the shipping Swift engine produces.
 *
 * The fixture is generated on a macOS runner by the `bac-vectors` CI job (see
 * testdata/README.md). A failure here means the two apps would show different
 * permille values. Fix the port. Never widen the tolerance: every double in the
 * file is rounded to 9 places, so a disagreement in the 4th decimal is an
 * algorithm difference, not float noise.
 */
class GoldenVectorTest {

    private val root = JsonParser.parseReader(vectorFile().reader()).asJsonObject
    private val tolerance = root["tolerance"].asDouble
    private val vectors = root["vectors"].asJsonArray

    private fun vectorFile(): File {
        val path = System.getProperty("bac.vectors")
            ?: fail("bac.vectors system property not set; see bac/build.gradle.kts")
        val file = File(path)
        assertTrue(file.isFile, "golden vectors not found at $path")
        return file
    }

    /** A forEach over an empty list is a green test that proves nothing. */
    @Test
    fun fixtureIsPresentAndComplete() {
        assertEquals(1, root["schema"].asInt, "unknown fixture schema")
        assertEquals(EXPECTED_VECTOR_COUNT, vectors.size(), "vector count changed")
        assertEquals(0.1, root["constants"].asJsonObject["km"].asDouble, "km drifted")
        assertEquals(1e-6, tolerance, "tolerance drifted")
    }

    @Test
    fun derivedChainMatches() {
        forEachVector { id, v ->
            val profile = profileOf(v)
            val d = v["derived"].asJsonObject
            near("$id validatedWeight", d["validatedWeight"].asDouble, profile.validatedWeight)
            near("$id validatedHeight", d["validatedHeight"].asDouble, profile.validatedHeight)
            assertEquals(d["validatedAge"].asInt, profile.validatedAge, "$id validatedAge")
            near("$id totalBodyWaterL", d["totalBodyWaterL"].asDouble, profile.totalBodyWaterL)
            near("$id distributionFactor", d["distributionFactor"].asDouble, profile.distributionFactor)
            near(
                "$id effectiveEliminationRate",
                d["effectiveEliminationRate"].asDouble,
                profile.effectiveEliminationRate
            )
            near(
                "$id resolvedEliminationRate",
                d["resolvedEliminationRate"].asDouble,
                profile.resolvedEliminationRate(v["input"].asJsonObject["conservative"].asBoolean)
            )
            near("$id drivingLimit", d["drivingLimit"].asDouble, profile.drivingLimit)
        }
    }

    @Test
    fun perDrinkTermsMatch() {
        forEachVector { id, v ->
            val profile = profileOf(v)
            val input = v["input"].asJsonObject
            val stomach = StomachStatus.from(input["stomachStatus"].asString)
            input["drinks"].asJsonArray.forEachIndexed { i, element ->
                val spec = element.asJsonObject
                val drink = drinkOf(spec)
                near(
                    "$id drink $i rawContribution",
                    spec["rawContribution"].asDouble,
                    BacCalculator.bacContribution(
                        drink.volumeML, drink.abv,
                        profile.validatedWeight, profile.distributionFactor
                    )
                )
                near(
                    "$id drink $i absorptionWindowMinutes",
                    spec["absorptionWindowMinutes"].asDouble,
                    BacCalculator.absorptionWindowMinutes(
                        drink.category, drink.drinkDurationMinutes, stomach.absorptionMinutes
                    )
                )
            }
        }
    }

    @Test
    fun peaksAndForecastsMatch() {
        forEachVector { id, v ->
            val c = caseOf(v)
            val expected = v["expected"].asJsonObject
            near(
                "$id projectedPeakFirstDrink",
                expected["projectedPeakFirstDrink"].asDouble,
                BacCalculator.projectedPeak(c.drinks.first(), c.profile, c.stomach, c.conservative)
            )
            // Compared before the samples: the 15-minute grid can miss a peak
            // entirely, so this is what localises a truncation bug.
            near(
                "$id sessionPeak",
                expected["sessionPeak"].asDouble,
                BacCalculator.peakBac(
                    c.drinks, c.profile, c.stomach, c.conservative,
                    c.vomits, c.meals, intervalMinutes = 1.0
                )
            )
            nearOrNull(
                "$id hoursUntilSober", expected["hoursUntilSober"],
                BacCalculator.hoursUntilBac(
                    0.0, c.drinks, c.profile, 0.0, c.stomach,
                    c.conservative, c.vomits, c.meals
                )
            )
            nearOrNull(
                "$id hoursUntilDrivingLimit", expected["hoursUntilDrivingLimit"],
                BacCalculator.hoursUntilBac(
                    c.profile.drivingLimit, c.drinks, c.profile, 0.0, c.stomach,
                    c.conservative, c.vomits, c.meals
                )
            )
        }
    }

    @Test
    fun curveSamplesMatch() {
        forEachVector { id, v ->
            val c = caseOf(v)
            val samples = v["expected"].asJsonObject["samples"].asJsonArray
            assertTrue(samples.size() > 0, "$id has no samples")
            val minutes = samples.map { it.asJsonObject["minute"].asDouble }
            val actual = BacCalculator.sampledBac(
                c.drinks, c.profile, minutes, c.stomach, c.conservative, c.vomits, c.meals
            )
            assertTrue(actual.any { it > 0 }, "$id curve never rises above zero")
            samples.forEachIndexed { i, element ->
                near(
                    "$id sample at ${minutes[i]} min",
                    element.asJsonObject["bac"].asDouble,
                    actual[i]
                )
            }
        }
    }

    // MARK: - Helpers

    private class Case(
        val profile: Profile,
        val drinks: List<DrinkInput>,
        val stomach: StomachStatus,
        val conservative: Boolean,
        val vomits: List<Double>,
        val meals: List<MealInput>
    )

    private fun forEachVector(body: (String, JsonObject) -> Unit) {
        assertEquals(EXPECTED_VECTOR_COUNT, vectors.size(), "vector count changed")
        vectors.forEach { element ->
            val v = element.asJsonObject
            body(v["id"].asString, v)
        }
    }

    private fun profileOf(v: JsonObject): Profile {
        val p = v["input"].asJsonObject["profile"].asJsonObject
        return Profile(
            weightKg = p["weightKg"].asDouble,
            heightCm = p["heightCm"].asDouble,
            age = p["age"].asInt,
            gender = Gender.from(p["gender"].asString),
            eliminationRate = p["eliminationRate"].asDouble,
            toleranceMode = p["toleranceMode"].asBoolean,
            isProbationaryDriver = p["isProbationaryDriver"]?.asBoolean ?: false
        )
    }

    private fun drinkOf(spec: JsonObject) = DrinkInput(
        offsetMinutes = spec["offsetMinutes"].asDouble,
        volumeML = spec["volumeML"].asDouble,
        abv = spec["abv"].asDouble,
        category = DrinkCategory.from(spec["category"].asString),
        drinkDurationMinutes = spec["drinkDurationMinutes"].asDouble
    )

    private fun caseOf(v: JsonObject): Case {
        val input = v["input"].asJsonObject
        return Case(
            profile = profileOf(v),
            drinks = input["drinks"].asJsonArray.map { drinkOf(it.asJsonObject) },
            stomach = StomachStatus.from(input["stomachStatus"].asString),
            conservative = input["conservative"].asBoolean,
            vomits = input["vomitOffsetMinutes"].asJsonArray.map { it.asDouble },
            meals = input["meals"].asJsonArray.map {
                val m = it.asJsonObject
                MealInput(m["offsetMinutes"].asDouble, MealImpact.from(m["impact"].asString))
            }
        )
    }

    private fun near(what: String, expected: Double, actual: Double) {
        if (abs(expected - actual) > tolerance) {
            fail("$what: expected $expected, got $actual (delta ${abs(expected - actual)})")
        }
    }

    private fun nearOrNull(what: String, expected: com.google.gson.JsonElement, actual: Double?) {
        if (expected.isJsonNull) {
            assertEquals(null, actual, "$what: expected null")
        } else {
            if (actual == null) fail("$what: expected ${expected.asDouble}, got null")
            near(what, expected.asDouble, actual)
        }
    }

    private companion object {
        const val EXPECTED_VECTOR_COUNT = 24
    }
}
