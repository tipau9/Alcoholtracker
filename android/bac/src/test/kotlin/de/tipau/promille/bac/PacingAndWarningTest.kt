package de.tipau.promille.bac

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Pacing Models and Warning Systems:
 * 1. DrinkDurationEstimator (heuristic duration per category, shot volume scaling, clamping [1.0, 180.0])
 * 2. DrinkPaceMemory (EMA smoothing alpha=0.35, floor 0.40, minimum 3 early finishes)
 * 3. PersonalInsights "Schneller Einstieg" discovery rule (<30 min median gap across >= 5 sessions)
 * 4. HangoverPredictor (HangoverLevel.LETHAL emergency alarm at peakBAC >= 3.0 permille, score tiers, water capping)
 */
class PacingAndWarningTest {

    private val eps = 1e-9
    private val berlinZone = ZoneId.of("Europe/Berlin")

    private val standardProfile = Profile(
        weightKg = 80.0,
        heightCm = 180.0,
        age = 30,
        gender = Gender.MALE,
        eliminationRate = 0.15
    )

    // MARK: - DrinkDurationEstimator

    @Test
    fun `DrinkDurationEstimator calculates shot duration based on count volume scaling`() {
        // volumeML / 40.0, clamped to [1.0, 180.0]
        assertEquals(1.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 20.0), eps)
        assertEquals(1.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 40.0), eps)
        assertEquals(2.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 80.0), eps)
        assertEquals(3.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 120.0), eps)
        assertEquals(4.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 160.0), eps)
        assertEquals(5.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 200.0), eps)

        // Upper clamp at 180 minutes
        assertEquals(180.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 8000.0), eps)
    }

    @Test
    fun `DrinkDurationEstimator applies correct rate per category`() {
        // BEER, CIDER, SPIRITS, LIQUEUR, OTHER -> 0.04 min/mL
        assertEquals(20.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.BEER, 500.0), eps)
        assertEquals(13.2, DrinkDurationEstimator.baseEstimate(DrinkCategory.BEER, 330.0), eps)
        assertEquals(10.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.CIDER, 250.0), eps)
        assertEquals(1.6, DrinkDurationEstimator.baseEstimate(DrinkCategory.SPIRITS, 40.0), eps)
        assertEquals(2.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.LIQUEUR, 50.0), eps)
        assertEquals(4.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.OTHER, 100.0), eps)

        // WINE, SPARKLING, COCKTAIL -> 0.05 min/mL
        assertEquals(10.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.WINE, 200.0), eps)
        assertEquals(5.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SPARKLING, 100.0), eps)
        assertEquals(15.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.COCKTAIL, 300.0), eps)

        // MIXED -> 0.045 min/mL
        assertEquals(13.5, DrinkDurationEstimator.baseEstimate(DrinkCategory.MIXED, 300.0), eps)

        // FORTIFIED -> 0.06 min/mL
        assertEquals(6.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.FORTIFIED, 100.0), eps)

        // Non-alcoholic categories (WATER, SOFT_DRINK, JUICE, COFFEE_TEA, MILK) -> 0.02 min/mL
        assertEquals(5.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.WATER, 250.0), eps)
        assertEquals(6.6, DrinkDurationEstimator.baseEstimate(DrinkCategory.SOFT_DRINK, 330.0), eps)
        assertEquals(4.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.JUICE, 200.0), eps)
        assertEquals(3.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.COFFEE_TEA, 150.0), eps)
        assertEquals(10.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.MILK, 500.0), eps)
    }

    @Test
    fun `DrinkDurationEstimator clamps duration strictly between 1_0 and 180_0 minutes`() {
        // Underflow: tiny volume below 1.0 min
        assertEquals(1.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.BEER, 10.0), eps) // 10 * 0.04 = 0.4 -> 1.0
        assertEquals(1.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.WATER, 20.0), eps) // 20 * 0.02 = 0.4 -> 1.0

        // Overflow: massive volume above 180.0 min
        assertEquals(180.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.BEER, 10000.0), eps) // 400 min -> 180.0
    }

    // MARK: - DrinkPaceMemory

    @Test
    fun `DrinkPaceMemory requires at least 3 early finishes before adapting estimate`() {
        val memory = DrinkPaceMemory.inMemory()
        val baseMinutes = 20.0

        // Initially returns baseEstimate
        assertEquals(baseMinutes, memory.adjustedEstimate(DrinkCategory.BEER, baseMinutes), eps)

        // 1st early finish (actual 10.0 min): ratio = 0.50
        memory.recordEarlyFinish(DrinkCategory.BEER, baseMinutes, 10.0)
        assertEquals(baseMinutes, memory.adjustedEstimate(DrinkCategory.BEER, baseMinutes), eps)

        // 2nd early finish (actual 10.0 min)
        memory.recordEarlyFinish(DrinkCategory.BEER, baseMinutes, 10.0)
        assertEquals(baseMinutes, memory.adjustedEstimate(DrinkCategory.BEER, baseMinutes), eps)

        // 3rd early finish (activation threshold reached!)
        memory.recordEarlyFinish(DrinkCategory.BEER, baseMinutes, 10.0)
        val adjusted = memory.adjustedEstimate(DrinkCategory.BEER, baseMinutes)

        // Should now be adjusted below baseMinutes (20.0 * 0.50 = 10.0)
        assertTrue(adjusted < baseMinutes)
        assertEquals(10.0, adjusted, 0.01)
    }

    @Test
    fun `DrinkPaceMemory ignores invalid or insufficiently early finishes`() {
        val memory = DrinkPaceMemory.inMemory()
        val base = 20.0

        // 1. baseEstimate <= 1 or actual < 1 -> ignored
        memory.recordEarlyFinish(DrinkCategory.SHOT, 1.0, 0.5)
        assertEquals(1.0, memory.adjustedEstimate(DrinkCategory.SHOT, 1.0), eps)

        // 2. actualMinutes > baseEstimate * 0.75 (e.g. 16 min out of 20 min base is > 15.0) -> ignored
        repeat(5) {
            memory.recordEarlyFinish(DrinkCategory.BEER, base, 16.0)
        }
        assertEquals(base, memory.adjustedEstimate(DrinkCategory.BEER, base), eps)

        // 3. baseEstimate - actualMinutes < 1.0 (e.g. base = 2.0, actual = 1.4 -> diff = 0.6 < 1.0) -> ignored
        repeat(5) {
            memory.recordEarlyFinish(DrinkCategory.LIQUEUR, 2.0, 1.4)
        }
        assertEquals(2.0, memory.adjustedEstimate(DrinkCategory.LIQUEUR, 2.0), eps)
    }

    @Test
    fun `DrinkPaceMemory clamps learned ratio to minimum 0_40 and applies EMA smoothing`() {
        val memory = DrinkPaceMemory.inMemory()
        val base = 100.0

        // 1st finish: extremely fast (actual = 10 min -> raw ratio = 0.10, clamped to 0.40)
        memory.recordEarlyFinish(DrinkCategory.WINE, base, 10.0)

        // 2nd finish: actual = 50 min -> ratio = 0.50.
        // EMA: 0.40 * (1 - 0.35) + 0.50 * 0.35 = 0.26 + 0.175 = 0.435
        memory.recordEarlyFinish(DrinkCategory.WINE, base, 50.0)

        // 3rd finish: actual = 60 min -> ratio = 0.60.
        // EMA: 0.435 * 0.65 + 0.60 * 0.35 = 0.28275 + 0.21 = 0.49275
        memory.recordEarlyFinish(DrinkCategory.WINE, base, 60.0)

        val adjusted = memory.adjustedEstimate(DrinkCategory.WINE, base)
        assertEquals(100.0 * 0.49275, adjusted, 0.01)

        // Forget category resets learned pace
        memory.forget(DrinkCategory.WINE)
        assertEquals(base, memory.adjustedEstimate(DrinkCategory.WINE, base), eps)
    }

    // MARK: - PersonalInsights "Schneller Einstieg" Discovery

    @Test
    fun `PersonalInsights generates Schneller Einstieg discovery when median gap is under 30 min across at least 5 sessions`() {
        // Create 5 sessions, each with 2 drinks spaced 15 minutes apart
        val drinks = mutableListOf<Drink>()
        val baseEpoch = 1750000000L // arbitrary fixed time

        for (sessionIdx in 0 until 5) {
            val sessionStart = baseEpoch + sessionIdx * 86400L // 1 day apart
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d1",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart,
                    drinkDurationMinutes = 20.0
                )
            )
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d2",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart + 15 * 60L, // 15 min gap (< 30 min)
                    drinkDurationMinutes = 20.0
                )
            )
        }

        val insights = PersonalInsights.build(
            drinks = drinks,
            profile = standardProfile,
            cutoffEpochSeconds = baseEpoch,
            nowEpochSeconds = baseEpoch + 6 * 86400L,
            zone = berlinZone
        )

        val schnellerEinstieg = insights.discoveries.find { it.title == "Schneller Einstieg" }
        assertNotNull(schnellerEinstieg, "Schneller Einstieg discovery should be generated")
        assertTrue(schnellerEinstieg.detail.contains("15 Minuten"))
        assertEquals("bolt.fill", schnellerEinstieg.icon)
    }

    @Test
    fun `PersonalInsights does not generate Schneller Einstieg when median gap is 30 min or higher`() {
        val drinks = mutableListOf<Drink>()
        val baseEpoch = 1750000000L

        for (sessionIdx in 0 until 5) {
            val sessionStart = baseEpoch + sessionIdx * 86400L
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d1",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart,
                    drinkDurationMinutes = 20.0
                )
            )
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d2",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart + 45 * 60L, // 45 min gap (>= 30 min)
                    drinkDurationMinutes = 20.0
                )
            )
        }

        val insights = PersonalInsights.build(
            drinks = drinks,
            profile = standardProfile,
            cutoffEpochSeconds = baseEpoch,
            nowEpochSeconds = baseEpoch + 6 * 86400L,
            zone = berlinZone
        )

        val schnellerEinstieg = insights.discoveries.find { it.title == "Schneller Einstieg" }
        assertNull(schnellerEinstieg, "Schneller Einstieg should NOT be generated when gap >= 30 min")
    }

    @Test
    fun `PersonalInsights does not generate Schneller Einstieg with fewer than 5 eligible sessions`() {
        val drinks = mutableListOf<Drink>()
        val baseEpoch = 1750000000L

        // Only 4 sessions
        for (sessionIdx in 0 until 4) {
            val sessionStart = baseEpoch + sessionIdx * 86400L
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d1",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart,
                    drinkDurationMinutes = 20.0
                )
            )
            drinks.add(
                Drink(
                    id = "s${sessionIdx}_d2",
                    name = "Bier",
                    volumeML = 500.0,
                    abv = 5.0,
                    category = DrinkCategory.BEER,
                    timestampEpochSeconds = sessionStart + 10 * 60L,
                    drinkDurationMinutes = 20.0
                )
            )
        }

        val insights = PersonalInsights.build(
            drinks = drinks,
            profile = standardProfile,
            cutoffEpochSeconds = baseEpoch,
            nowEpochSeconds = baseEpoch + 6 * 86400L,
            zone = berlinZone
        )

        val schnellerEinstieg = insights.discoveries.find { it.title == "Schneller Einstieg" }
        assertNull(schnellerEinstieg, "Schneller Einstieg should NOT be generated with < 5 sessions")
    }

    // MARK: - HangoverPredictor

    @Test
    fun `HangoverPredictor triggers LETHAL alarm at peakBAC 3_0 permille regardless of water or duration`() {
        // Exactly 3.0 permille
        val lethal1 = HangoverPredictor.predict(
            peakBAC = 3.00,
            durationHours = 6.0,
            waterGlasses = 100.0, // excessive hydration cannot erase lethal danger
            drinksCount = 15
        )
        assertEquals(HangoverLevel.LETHAL, lethal1)
        assertTrue(lethal1.isLethal)
        assertFalse(lethal1.isPositive)
        assertEquals("Lebensgefahr – tödlicher Bereich", lethal1.germanLabel)

        // Above 3.0 permille (e.g. 3.5 permille)
        val lethal2 = HangoverPredictor.predict(
            peakBAC = 3.50,
            durationHours = 2.0,
            waterGlasses = 0.0,
            drinksCount = 20
        )
        assertEquals(HangoverLevel.LETHAL, lethal2)
        assertTrue(lethal2.isLethal)

        // Just below 3.0 permille (2.99 permille) -> not LETHAL
        val nearLethal = HangoverPredictor.predict(
            peakBAC = 2.99,
            durationHours = 6.0,
            waterGlasses = 0.0,
            drinksCount = 15
        )
        assertTrue(nearLethal != HangoverLevel.LETHAL)
        assertFalse(nearLethal.isLethal)
    }

    @Test
    fun `HangoverPredictor severity scoring correctly maps across all level tiers`() {
        // NONE: score < 1.2, peak < 1.2
        val noneLevel = HangoverPredictor.predict(
            peakBAC = 0.3, durationHours = 2.0, waterGlasses = 2.0, drinksCount = 1
        )
        assertEquals(HangoverLevel.NONE, noneLevel)
        assertTrue(noneLevel.isPositive)
        assertFalse(noneLevel.isLethal)
        assertEquals("Kein Kater erwartet", noneLevel.germanLabel)

        // MILD: 1.2 <= score < 2.0
        val mildLevel = HangoverPredictor.predict(
            peakBAC = 0.7, durationHours = 3.0, waterGlasses = 1.0, drinksCount = 3
        )
        assertEquals(HangoverLevel.MILD, mildLevel)
        assertEquals("Leichtes Unbehagen möglich", mildLevel.germanLabel)

        // MODERATE: 2.0 <= score < 3.0
        val moderateLevel = HangoverPredictor.predict(
            peakBAC = 1.3, durationHours = 4.0, waterGlasses = 2.0, drinksCount = 5
        )
        assertEquals(HangoverLevel.MODERATE, moderateLevel)
        assertEquals("Spürbarer Kater morgen", moderateLevel.germanLabel)

        // STRONG: 3.0 <= score < 4.4
        val strongLevel = HangoverPredictor.predict(
            peakBAC = 1.8, durationHours = 5.0, waterGlasses = 1.0, drinksCount = 8
        )
        assertEquals(HangoverLevel.STRONG, strongLevel)
        assertEquals("Harter Tag morgen", strongLevel.germanLabel)

        // SEVERE: score >= 4.4 (with peakBAC < 3.0)
        val severeLevel = HangoverPredictor.predict(
            peakBAC = 2.5, durationHours = 8.0, waterGlasses = 0.0, drinksCount = 14
        )
        assertEquals(HangoverLevel.SEVERE, severeLevel)
        assertEquals("Sehr schwerer Kater morgen", severeLevel.germanLabel)
    }

    @Test
    fun `HangoverPredictor floors prevent high BAC from being reduced below minimum tier by water`() {
        // peakBAC >= 2.0 is floored at MODERATE even if flooded with water
        val highPeakDrowned = HangoverPredictor.predict(
            peakBAC = 2.2, durationHours = 1.0, waterGlasses = 50.0, drinksCount = 4
        )
        assertTrue(highPeakDrowned >= HangoverLevel.MODERATE)

        // peakBAC >= 1.2 is floored at MILD
        val midPeakDrowned = HangoverPredictor.predict(
            peakBAC = 1.4, durationHours = 1.0, waterGlasses = 50.0, drinksCount = 3
        )
        assertTrue(midPeakDrowned >= HangoverLevel.MILD)
    }
}
