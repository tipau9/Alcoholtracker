package de.tipau.promille.bac

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Gastric Emptying Delay, Resorption Deficit by Stomach Status,
 * all 16 DrinkCategory Absorption Modifiers, Conservative Mode Bypass, and
 * Meal Impact Dynamics with Piecewise Window Stretching.
 */
class GastricAndMealModelTest {

    private val eps = 1e-9

    private val standardProfile = Profile(
        weightKg = 80.0,
        heightCm = 180.0,
        age = 30,
        gender = Gender.MALE,
        eliminationRate = 0.15
    )

    // MARK: - Stomach Status Profiles & Resorption Deficits

    @Test
    fun `stomach status profiles match physiological specifications`() {
        // EMPTY: 45 min absorption, 0.90 peak factor (10% deficit)
        assertEquals("empty", StomachStatus.EMPTY.raw)
        assertEquals(45.0, StomachStatus.EMPTY.absorptionMinutes, eps)
        assertEquals(0.90, StomachStatus.EMPTY.peakFactor, eps)
        assertEquals("Leer", StomachStatus.EMPTY.germanName)

        // LIGHT: 75 min absorption, 0.81 peak factor (19% deficit)
        assertEquals("light", StomachStatus.LIGHT.raw)
        assertEquals(75.0, StomachStatus.LIGHT.absorptionMinutes, eps)
        assertEquals(0.81, StomachStatus.LIGHT.peakFactor, eps)
        assertEquals("Leicht gefüllt", StomachStatus.LIGHT.germanName)

        // FULL: 90 min absorption, 0.75 peak factor (25% deficit)
        assertEquals("full", StomachStatus.FULL.raw)
        assertEquals(90.0, StomachStatus.FULL.absorptionMinutes, eps)
        assertEquals(0.75, StomachStatus.FULL.peakFactor, eps)
        assertEquals("Satt", StomachStatus.FULL.germanName)
    }

    @Test
    fun `StomachStatus from raw resolves known names and defaults to LIGHT`() {
        assertEquals(StomachStatus.EMPTY, StomachStatus.from("empty"))
        assertEquals(StomachStatus.LIGHT, StomachStatus.from("light"))
        assertEquals(StomachStatus.FULL, StomachStatus.from("full"))
        assertEquals(StomachStatus.LIGHT, StomachStatus.from("unknown_value"))
        assertEquals(StomachStatus.LIGHT, StomachStatus.from(""))
    }

    // MARK: - All 16 DrinkCategory Absorption Modifiers

    @Test
    fun `all 16 drink categories have correct absorption modifiers and raw names`() {
        val expectedCategories = mapOf(
            DrinkCategory.BEER to Pair("beer", 0.85),
            DrinkCategory.WINE to Pair("wine", 1.0),
            DrinkCategory.SPARKLING to Pair("sparkling", 0.85),
            DrinkCategory.SPIRITS to Pair("spirits", 1.0),
            DrinkCategory.LIQUEUR to Pair("liqueur", 1.0),
            DrinkCategory.COCKTAIL to Pair("cocktail", 1.0),
            DrinkCategory.MIXED to Pair("mixed", 1.0),
            DrinkCategory.SHOT to Pair("shot", 0.75),
            DrinkCategory.CIDER to Pair("cider", 0.85),
            DrinkCategory.FORTIFIED to Pair("fortified", 1.0),
            DrinkCategory.WATER to Pair("water", 1.0),
            DrinkCategory.SOFT_DRINK to Pair("softDrink", 1.0),
            DrinkCategory.JUICE to Pair("juice", 1.0),
            DrinkCategory.COFFEE_TEA to Pair("coffeeTea", 1.0),
            DrinkCategory.MILK to Pair("milk", 1.0),
            DrinkCategory.OTHER to Pair("other", 1.0)
        )

        assertEquals(16, DrinkCategory.entries.size)

        for ((cat, pair) in expectedCategories) {
            val (expectedRaw, expectedModifier) = pair
            assertEquals(expectedRaw, cat.raw, "Mismatch in raw name for $cat")
            assertEquals(expectedModifier, cat.absorptionModifier, eps, "Mismatch in modifier for $cat")
            assertEquals(cat, DrinkCategory.from(expectedRaw), "Mismatch in deserialization for $cat")
        }

        // Unknown fallback
        assertEquals(DrinkCategory.OTHER, DrinkCategory.from("non_existent_category"))
    }

    @Test
    fun `absorptionWindowMinutes correctly combines gastric time and category modifier`() {
        val gastric = 75.0 // LIGHT stomach

        // SHOT (modifier 0.75): gastric * modifier = 56.25 min
        // If drink duration is short (1 min), gastric dominates: 56.25 min
        val shotWindow = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.SHOT,
            drinkDurationMinutes = 1.0,
            gastricMinutes = gastric
        )
        assertEquals(56.25, shotWindow, eps)

        // BEER (modifier 0.85): gastric * modifier = 63.75 min
        // If drink duration is 20 min, gastric dominates: 63.75 min
        val beerWindow = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 20.0,
            gastricMinutes = gastric
        )
        assertEquals(63.75, beerWindow, eps)

        // WINE (modifier 1.0): gastric * modifier = 75.0 min
        val wineWindow = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.WINE,
            drinkDurationMinutes = 10.0,
            gastricMinutes = gastric
        )
        assertEquals(75.0, wineWindow, eps)

        // If drinking duration exceeds gastric phase, drinking duration dominates (never summed!)
        val longSipWindow = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 120.0,
            gastricMinutes = gastric
        )
        assertEquals(120.0, longSipWindow, eps)

        // Minimum clamp: even with 0 gastric minutes, window is at least 1.0
        val clampedWindow = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.SHOT,
            drinkDurationMinutes = 0.5,
            gastricMinutes = 0.0
        )
        assertEquals(1.0, clampedWindow, eps)
    }

    @Test
    fun `absorptionWindowMinutes requires strictly positive drinkDurationMinutes`() {
        assertFailsWith<IllegalArgumentException> {
            BacCalculator.absorptionWindowMinutes(DrinkCategory.BEER, 0.0, 45.0)
        }
        assertFailsWith<IllegalArgumentException> {
            BacCalculator.absorptionWindowMinutes(DrinkCategory.BEER, -10.0, 45.0)
        }
    }

    // MARK: - Conservative Mode Bypass

    @Test
    fun `conservative mode sets peakFactor to 1_0 disabling resorption deficit`() {
        val drink = DrinkInput(
            offsetMinutes = 0.0,
            volumeML = 500.0,
            abv = 5.0,
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 20.0
        )

        // Non-conservative projectedPeak with FULL stomach (factor = 0.75)
        val normalFullPeak = BacCalculator.projectedPeak(
            drink = drink,
            profile = standardProfile,
            stomach = StomachStatus.FULL,
            conservative = false
        )

        // Conservative projectedPeak with FULL stomach (factor forced to 1.0)
        val conservativeFullPeak = BacCalculator.projectedPeak(
            drink = drink,
            profile = standardProfile,
            stomach = StomachStatus.FULL,
            conservative = true
        )

        // Conservative peak must be strictly greater than normal peak with deficit
        assertTrue(
            conservativeFullPeak > normalFullPeak,
            "Conservative peak ($conservativeFullPeak) should exceed normal full peak ($normalFullPeak)"
        )

        // Verify across all stomach types that conservative peak is identical (since factor is 1.0 for all,
        // and window depends on stomach absorptionMinutes)
        val conservativeEmptyPeak = BacCalculator.projectedPeak(
            drink = drink,
            profile = standardProfile,
            stomach = StomachStatus.EMPTY,
            conservative = true
        )

        val rawBacContribution = BacCalculator.bacContribution(
            volumeML = 500.0,
            abv = 5.0,
            weightKg = standardProfile.validatedWeight,
            distributionFactor = standardProfile.distributionFactor
        )

        // In conservative mode, factor = 1.0, so rawPeak = rawBacContribution * 1.0
        val window = BacCalculator.absorptionWindowMinutes(
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 20.0,
            gastricMinutes = StomachStatus.EMPTY.absorptionMinutes
        )
        val elimPerMin = standardProfile.resolvedEliminationRate(conservative = true) / 60.0
        val expectedConservativePeak = rawBacContribution - elimPerMin * window

        assertEquals(expectedConservativePeak, conservativeEmptyPeak, eps)
    }

    // MARK: - Meal Impact Levels

    @Test
    fun `meal impact levels match multiplier and active duration specifications`() {
        // SNACK: 1.15 multiplier, 2h (7200s)
        assertEquals("snack", MealImpact.SNACK.raw)
        assertEquals(1.15, MealImpact.SNACK.remainingAbsorptionMultiplier, eps)
        assertEquals(7200.0, MealImpact.SNACK.activeDurationSeconds, eps)
        assertEquals("Snack", MealImpact.SNACK.germanName)

        // LIGHT_MEAL: 1.35 multiplier, 3h (10800s)
        assertEquals("lightMeal", MealImpact.LIGHT_MEAL.raw)
        assertEquals(1.35, MealImpact.LIGHT_MEAL.remainingAbsorptionMultiplier, eps)
        assertEquals(10800.0, MealImpact.LIGHT_MEAL.activeDurationSeconds, eps)
        assertEquals("Leichte Mahlzeit", MealImpact.LIGHT_MEAL.germanName)

        // FULL_MEAL: 1.65 multiplier, 4h (14400s)
        assertEquals("fullMeal", MealImpact.FULL_MEAL.raw)
        assertEquals(1.65, MealImpact.FULL_MEAL.remainingAbsorptionMultiplier, eps)
        assertEquals(14400.0, MealImpact.FULL_MEAL.activeDurationSeconds, eps)
        assertEquals("Volle Mahlzeit", MealImpact.FULL_MEAL.germanName)
    }

    @Test
    fun `MealImpact from raw deserialization resolves known names and defaults to LIGHT_MEAL`() {
        assertEquals(MealImpact.SNACK, MealImpact.from("snack"))
        assertEquals(MealImpact.LIGHT_MEAL, MealImpact.from("lightMeal"))
        assertEquals(MealImpact.FULL_MEAL, MealImpact.from("fullMeal"))
        assertEquals(MealImpact.LIGHT_MEAL, MealImpact.from("unknown_meal"))
        assertEquals(MealImpact.LIGHT_MEAL, MealImpact.from(""))
    }

    // MARK: - Meal Impact Dynamics & Piecewise Window Expansion

    @Test
    fun `prior active meal stretches the entire initial absorption window`() {
        val drink = DrinkInput(
            offsetMinutes = 60.0, // Drink at 60 min
            volumeML = 500.0,
            abv = 5.0,
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 20.0
        )

        // Base window without meal (EMPTY stomach = 45m * 0.85 = 38.25m)
        val baseWindow = BacCalculator.absorptionWindowMinutes(
            DrinkCategory.BEER, 20.0, StomachStatus.EMPTY.absorptionMinutes
        )
        assertEquals(38.25, baseWindow, eps)

        // Meal eaten at t = 0 min (1 hour before drink, inside 4h active duration of FULL_MEAL)
        val priorMeal = MealInput(offsetMinutes = 0.0, impact = MealImpact.FULL_MEAL)

        // Sample at the end of the un-mealed window (t = 60 + 38.25 = 98.25 min)
        // and at the end of the mealed window (t = 60 + 38.25 * 1.65 = 123.1125 min)
        val bacWithoutMeal = BacCalculator.currentBac(
            drinks = listOf(drink),
            profile = standardProfile,
            atMinute = 98.25,
            stomach = StomachStatus.EMPTY,
            meals = emptyList()
        )

        val bacWithMeal = BacCalculator.currentBac(
            drinks = listOf(drink),
            profile = standardProfile,
            atMinute = 98.25,
            stomach = StomachStatus.EMPTY,
            meals = listOf(priorMeal)
        )

        // At t = 98.25 min, the meal has slowed absorption down so BAC with meal is lower
        assertTrue(
            bacWithMeal < bacWithoutMeal,
            "Prior meal should delay alcohol absorption resulting in lower BAC during early phase: $bacWithMeal vs $bacWithoutMeal"
        )
    }

    @Test
    fun `expired prior meal does not stretch the absorption window`() {
        val drink = DrinkInput(
            offsetMinutes = 300.0, // Drink at 300 min (5 hours)
            volumeML = 500.0,
            abv = 5.0,
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 20.0
        )

        // SNACK active duration is 2 hours (120 min). Meal at t = 0 is 300 min old -> expired!
        val expiredMeal = MealInput(offsetMinutes = 0.0, impact = MealImpact.SNACK)

        val bacWithoutMeal = BacCalculator.currentBac(
            drinks = listOf(drink),
            profile = standardProfile,
            atMinute = 330.0,
            stomach = StomachStatus.EMPTY,
            meals = emptyList()
        )

        val bacWithExpiredMeal = BacCalculator.currentBac(
            drinks = listOf(drink),
            profile = standardProfile,
            atMinute = 330.0,
            stomach = StomachStatus.EMPTY,
            meals = listOf(expiredMeal)
        )

        // Should be identical since meal has expired
        assertEquals(bacWithoutMeal, bacWithExpiredMeal, 1e-6)
    }

    @Test
    fun `mid-drink meal splits envelope into prefix and stretched remainder with mass conservation`() {
        val drink = DrinkInput(
            offsetMinutes = 0.0,
            volumeML = 500.0,
            abv = 5.0,
            category = DrinkCategory.BEER,
            drinkDurationMinutes = 60.0 // 60 min drinking duration
        )

        // Base window is 60.0 min (since drink duration 60.0 > 45 * 0.85 = 38.25)
        // Mid-drink meal at t = 20 min (LIGHT_MEAL multiplier = 1.35)
        val midMeal = MealInput(offsetMinutes = 20.0, impact = MealImpact.LIGHT_MEAL)

        val samples = listOf(0.0, 10.0, 20.0, 40.0, 60.0, 80.0, 120.0)
        val bacWithout = BacCalculator.sampledBac(
            drinks = listOf(drink),
            profile = standardProfile,
            sampleMinutes = samples,
            stomach = StomachStatus.EMPTY,
            meals = emptyList()
        )
        val bacWith = BacCalculator.sampledBac(
            drinks = listOf(drink),
            profile = standardProfile,
            sampleMinutes = samples,
            stomach = StomachStatus.EMPTY,
            meals = listOf(midMeal)
        )

        // Before meal arrives at t=20, trajectories must be identical
        assertEquals(bacWithout[0], bacWith[0], 1e-6) // t = 0
        assertEquals(bacWithout[1], bacWith[1], 1e-6) // t = 10
        assertEquals(bacWithout[2], bacWith[2], 1e-6) // t = 20

        // After meal at t=40, absorption of remainder is slowed down, so bacWith < bacWithout
        assertTrue(
            bacWith[3] < bacWithout[3],
            "At t=40, meal should have slowed down remaining absorption: with=${bacWith[3]} vs without=${bacWithout[3]}"
        )
    }

    @Test
    fun `meal logged after absorption window has completed has zero effect`() {
        val drink = DrinkInput(
            offsetMinutes = 0.0,
            volumeML = 40.0,
            abv = 40.0,
            category = DrinkCategory.SHOT,
            drinkDurationMinutes = 1.0 // Window = max(1.0, 45 * 0.75 = 33.75) = 33.75 min
        )

        // Meal logged at t = 60.0 min (well after window ended at 33.75 min)
        val postMeal = MealInput(offsetMinutes = 60.0, impact = MealImpact.FULL_MEAL)

        val samples = listOf(0.0, 30.0, 50.0, 70.0, 100.0)
        val withoutMeal = BacCalculator.sampledBac(
            drinks = listOf(drink),
            profile = standardProfile,
            sampleMinutes = samples,
            stomach = StomachStatus.EMPTY,
            meals = emptyList()
        )
        val withPostMeal = BacCalculator.sampledBac(
            drinks = listOf(drink),
            profile = standardProfile,
            sampleMinutes = samples,
            stomach = StomachStatus.EMPTY,
            meals = listOf(postMeal)
        )

        for (i in samples.indices) {
            assertEquals(withoutMeal[i], withPostMeal[i], 1e-6, "Mismatch at sample index $i")
        }
    }
}
