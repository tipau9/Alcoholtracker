package de.tipau.promille.bac

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Watson (1980) total body water formula, physiological parameter clamping,
 * Widmark blood distribution factor r, tolerance mode elimination rate floor, and
 * probationary driving zero-tolerance rules.
 */
class WatsonProfileTest {

    private val eps = 1e-9

    // MARK: - Watson (1980) Total Body Water Formulas

    @Test
    fun `male total body water follows Watson 1980 formula exactly`() {
        val age = 35
        val height = 180.0
        val weight = 80.0
        val profile = Profile(
            weightKg = weight,
            heightCm = height,
            age = age,
            gender = Gender.MALE
        )

        // Expected Male TBW = 2.447 - 0.09516 * age + 0.1074 * height + 0.3362 * weight
        val expectedMaleTbw = 2.447 - (0.09516 * 35.0) + (0.1074 * 180.0) + (0.3362 * 80.0)
        assertEquals(expectedMaleTbw, profile.totalBodyWaterL, eps)
        // Verify numerical value: 2.447 - 3.3306 + 19.332 + 26.896 = 45.3444 L
        assertTrue(abs(profile.totalBodyWaterL - 45.3444) < 1e-4)
    }

    @Test
    fun `female total body water follows Watson 1980 formula without age term`() {
        val height = 165.0
        val weight = 60.0
        val profile = Profile(
            weightKg = weight,
            heightCm = height,
            age = 28,
            gender = Gender.FEMALE
        )

        // Expected Female TBW = -2.097 + 0.1069 * height + 0.2466 * weight
        val expectedFemaleTbw = -2.097 + (0.1069 * 165.0) + (0.2466 * 60.0)
        assertEquals(expectedFemaleTbw, profile.totalBodyWaterL, eps)
        // Verify numerical value: -2.097 + 17.6385 + 14.796 = 30.3375 L
        assertTrue(abs(profile.totalBodyWaterL - 30.3375) < 1e-4)
    }

    @Test
    fun `diverse total body water is arithmetic mean of male and female equations`() {
        val age = 40
        val height = 175.0
        val weight = 70.0
        val profileDiverse = Profile(
            weightKg = weight,
            heightCm = height,
            age = age,
            gender = Gender.DIVERSE
        )

        val maleTbw = 2.447 - (0.09516 * 40.0) + (0.1074 * 175.0) + (0.3362 * 70.0)
        val femaleTbw = -2.097 + (0.1069 * 175.0) + (0.2466 * 70.0)
        val expectedDiverse = (maleTbw + femaleTbw) / 2.0

        assertEquals(expectedDiverse, profileDiverse.totalBodyWaterL, eps)
    }

    // MARK: - Parameter Clamping Boundaries

    @Test
    fun `weight is clamped to boundary range 30 to 250 kg`() {
        // Below minimum 30.0 kg
        val underWeight = Profile(weightKg = 20.0, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(30.0, underWeight.validatedWeight)

        val nearUnderWeight = Profile(weightKg = 29.99, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(30.0, nearUnderWeight.validatedWeight)

        // Exact lower bound
        val exactMinWeight = Profile(weightKg = 30.0, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(30.0, exactMinWeight.validatedWeight)

        // Nominal weight
        val normalWeight = Profile(weightKg = 75.5, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(75.5, normalWeight.validatedWeight)

        // Exact upper bound
        val exactMaxWeight = Profile(weightKg = 250.0, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(250.0, exactMaxWeight.validatedWeight)

        // Above maximum 250.0 kg
        val overWeight = Profile(weightKg = 320.0, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(250.0, overWeight.validatedWeight)

        val nearOverWeight = Profile(weightKg = 250.01, heightCm = 170.0, age = 25, gender = Gender.MALE)
        assertEquals(250.0, nearOverWeight.validatedWeight)
    }

    @Test
    fun `height is clamped to boundary range 120 to 230 cm`() {
        // Below minimum 120.0 cm
        val underHeight = Profile(weightKg = 70.0, heightCm = 95.0, age = 25, gender = Gender.FEMALE)
        assertEquals(120.0, underHeight.validatedHeight)

        val nearUnderHeight = Profile(weightKg = 70.0, heightCm = 119.9, age = 25, gender = Gender.FEMALE)
        assertEquals(120.0, nearUnderHeight.validatedHeight)

        // Exact bounds
        val exactMinHeight = Profile(weightKg = 70.0, heightCm = 120.0, age = 25, gender = Gender.FEMALE)
        assertEquals(120.0, exactMinHeight.validatedHeight)

        val exactMaxHeight = Profile(weightKg = 70.0, heightCm = 230.0, age = 25, gender = Gender.FEMALE)
        assertEquals(230.0, exactMaxHeight.validatedHeight)

        // Above maximum 230.0 cm
        val overHeight = Profile(weightKg = 70.0, heightCm = 245.0, age = 25, gender = Gender.FEMALE)
        assertEquals(230.0, overHeight.validatedHeight)

        val nearOverHeight = Profile(weightKg = 70.0, heightCm = 230.1, age = 25, gender = Gender.FEMALE)
        assertEquals(230.0, nearOverHeight.validatedHeight)
    }

    @Test
    fun `age is clamped to boundary range 18 to 100 years`() {
        // Below minimum 18 years
        val underAge = Profile(weightKg = 70.0, heightCm = 175.0, age = 15, gender = Gender.MALE)
        assertEquals(18, underAge.validatedAge)

        val nearUnderAge = Profile(weightKg = 70.0, heightCm = 175.0, age = 17, gender = Gender.MALE)
        assertEquals(18, nearUnderAge.validatedAge)

        // Exact bounds
        val exactMinAge = Profile(weightKg = 70.0, heightCm = 175.0, age = 18, gender = Gender.MALE)
        assertEquals(18, exactMinAge.validatedAge)

        val exactMaxAge = Profile(weightKg = 70.0, heightCm = 175.0, age = 100, gender = Gender.MALE)
        assertEquals(100, exactMaxAge.validatedAge)

        // Above maximum 100 years
        val overAge = Profile(weightKg = 70.0, heightCm = 175.0, age = 105, gender = Gender.MALE)
        assertEquals(100, overAge.validatedAge)
    }

    // MARK: - Widmark Blood Distribution Factor r

    @Test
    fun `distribution factor r is total body water divided by weight and blood water factor 0_806`() {
        val profile = Profile(weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE)
        val tbw = profile.totalBodyWaterL
        val expectedR = (tbw / 80.0) / 0.806
        assertEquals(expectedR, profile.distributionFactor, eps)
        // Check reasonable human range
        assertTrue(profile.distributionFactor in 0.50..0.90)
    }

    @Test
    fun `distribution factor r clamps to minimum 0_50 and maximum 0_90`() {
        // Construct extreme body composition yielding low TBW / weight ratio
        // Heavy weight (250 kg) with minimum height (120 cm) and max age (100)
        val heavyLowWaterProfile = Profile(
            weightKg = 250.0,
            heightCm = 120.0,
            age = 100,
            gender = Gender.FEMALE
        )
        // Raw r = (TBW / 250) / 0.806 -> should be clamped to 0.50 if below 0.50
        val rawLowR = (heavyLowWaterProfile.totalBodyWaterL / 250.0) / 0.806
        if (rawLowR < 0.50) {
            assertEquals(0.50, heavyLowWaterProfile.distributionFactor, eps)
        } else {
            assertTrue(heavyLowWaterProfile.distributionFactor >= 0.50)
        }

        // Construct extreme body composition yielding high TBW / weight ratio
        // Very tall (230 cm) with minimum weight (30 kg) and young age (18)
        val tallLightProfile = Profile(
            weightKg = 30.0,
            heightCm = 230.0,
            age = 18,
            gender = Gender.MALE
        )
        val rawHighR = (tallLightProfile.totalBodyWaterL / 30.0) / 0.806
        if (rawHighR > 0.90) {
            assertEquals(0.90, tallLightProfile.distributionFactor, eps)
        } else {
            assertTrue(tallLightProfile.distributionFactor <= 0.90)
        }
    }

    // MARK: - Tolerance Mode & Elimination Rates

    @Test
    fun `tolerance mode enforces elimination rate floor of 0_20 permille per hour`() {
        // Standard non-tolerant profile with low rate
        val lowRateProfile = Profile(
            weightKg = 75.0, heightCm = 175.0, age = 30, gender = Gender.MALE,
            eliminationRate = 0.12,
            toleranceMode = false
        )
        assertEquals(0.12, lowRateProfile.effectiveEliminationRate, eps)
        assertEquals(0.12, lowRateProfile.resolvedEliminationRate(conservative = false), eps)
        assertEquals(0.12, lowRateProfile.resolvedEliminationRate(conservative = true), eps)

        // Tolerance mode active with low base rate -> elevated to 0.20
        val tolerantLowRate = lowRateProfile.copy(toleranceMode = true)
        assertEquals(0.20, tolerantLowRate.effectiveEliminationRate, eps)
        assertEquals(0.20, tolerantLowRate.resolvedEliminationRate(conservative = false), eps)
        // Conservative mode must NOT assume tolerance rate increase: returns raw elimination rate
        assertEquals(0.12, tolerantLowRate.resolvedEliminationRate(conservative = true), eps)

        // Tolerance mode active with higher base rate (0.25) -> retains 0.25
        val tolerantHighRate = Profile(
            weightKg = 75.0, heightCm = 175.0, age = 30, gender = Gender.MALE,
            eliminationRate = 0.25,
            toleranceMode = true
        )
        assertEquals(0.25, tolerantHighRate.effectiveEliminationRate, eps)
        assertEquals(0.25, tolerantHighRate.resolvedEliminationRate(conservative = false), eps)
        assertEquals(0.25, tolerantHighRate.resolvedEliminationRate(conservative = true), eps)
    }

    // MARK: - Driving Limits & Probationary Driver Rules

    @Test
    fun `regular driver has 0_5 permille limit and mayDrive strictly below 0_5`() {
        val driver = Profile(
            weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE,
            isProbationaryDriver = false
        )
        assertEquals(0.5, driver.drivingLimit, eps)

        assertTrue(driver.mayDrive(0.0))
        assertTrue(driver.mayDrive(0.005))
        assertTrue(driver.mayDrive(0.10))
        assertTrue(driver.mayDrive(0.499))
        assertFalse(driver.mayDrive(0.500))
        assertFalse(driver.mayDrive(0.501))
        assertFalse(driver.mayDrive(1.20))
    }

    @Test
    fun `probationary driver has zero tolerance limit and mayDrive up to 0_005 floor`() {
        val probationaryDriver = Profile(
            weightKg = 80.0, heightCm = 180.0, age = 19, gender = Gender.MALE,
            isProbationaryDriver = true
        )
        assertEquals(0.0, probationaryDriver.drivingLimit, eps)

        assertTrue(probationaryDriver.mayDrive(0.0))
        assertTrue(probationaryDriver.mayDrive(0.0049))
        assertTrue(probationaryDriver.mayDrive(0.0050))
        assertFalse(probationaryDriver.mayDrive(0.0051))
        assertFalse(probationaryDriver.mayDrive(0.01))
        assertFalse(probationaryDriver.mayDrive(0.10))
        assertFalse(probationaryDriver.mayDrive(0.50))
    }

    // MARK: - Gender Serialization & Projection Key

    @Test
    fun `Gender from raw deserialization resolves known values and defaults to DIVERSE`() {
        assertEquals(Gender.MALE, Gender.from("male"))
        assertEquals(Gender.FEMALE, Gender.from("female"))
        assertEquals(Gender.DIVERSE, Gender.from("diverse"))
        assertEquals(Gender.DIVERSE, Gender.from("unknown"))
        assertEquals(Gender.DIVERSE, Gender.from(""))
    }

    @Test
    fun `conservative switches evaluate correctly for safety and app`() {
        val normal = Profile(weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE)
        assertFalse(normal.conservativeForSafety)
        assertFalse(normal.conservativeForApp)

        val safetyOnly = normal.copy(conservativeSafety = true)
        assertTrue(safetyOnly.conservativeForSafety)
        assertFalse(safetyOnly.conservativeForApp)

        val everywhere = normal.copy(conservativeEverywhere = true)
        assertTrue(everywhere.conservativeForSafety)
        assertTrue(everywhere.conservativeForApp)
    }

    @Test
    fun `bacProjectionKey reflects all profile configuration changes`() {
        val p1 = Profile(weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE)
        val p2 = p1.copy(weightKg = 85.0)
        val p3 = p1.copy(toleranceMode = true)
        val p4 = p1.copy(isProbationaryDriver = true)

        assertTrue(p1.bacProjectionKey != p2.bacProjectionKey)
        assertTrue(p1.bacProjectionKey != p3.bacProjectionKey)
        assertTrue(p1.bacProjectionKey != p4.bacProjectionKey)
        assertTrue(p1.bacProjectionKey.contains("80.0"))
        assertTrue(p1.bacProjectionKey.contains("male"))
    }
}
