package de.tipau.promille.bac

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks for the domain layer that the golden vectors do not reach: the epoch
 * timeline, the 06:00 day boundary, learned pace and the hydration model.
 */
class DomainTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(berlin).toEpochSecond()

    private fun beer(at: Long, duration: Double = 20.0) = Drink(
        id = "d-$at",
        name = "Bier",
        volumeML = 500.0,
        abv = 5.0,
        calories = 210,
        iconName = "mug.fill",
        category = DrinkCategory.BEER,
        timestampEpochSeconds = at,
        drinkDurationMinutes = duration
    )

    private val profile = Profile(
        weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE
    )

    // MARK: logical day

    @Test
    fun `night out before six belongs to the previous day`() {
        val lateNight = epoch(2026, 6, 11, 1, 30)
        assertEquals(LocalDate.of(2026, 6, 10), LogicalDay.dateOf(lateNight, berlin))
        assertEquals(epoch(2026, 6, 10, 6), LogicalDay.startOf(lateNight, berlin))
    }

    @Test
    fun `the six hour mark starts a new logical day`() {
        val justBefore = epoch(2026, 6, 11, 5, 59)
        val justAfter = epoch(2026, 6, 11, 6, 0)
        assertEquals(LocalDate.of(2026, 6, 10), LogicalDay.dateOf(justBefore, berlin))
        assertEquals(LocalDate.of(2026, 6, 11), LogicalDay.dateOf(justAfter, berlin))
        assertTrue(!LogicalDay.sameLogicalDay(justBefore, justAfter, berlin))
    }

    // MARK: duration estimate and learned pace

    @Test
    fun `shots scale by count, not by a per-ml rate`() {
        assertEquals(1.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 20.0))
        assertEquals(4.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.SHOT, 160.0))
        assertEquals(20.0, DrinkDurationEstimator.baseEstimate(DrinkCategory.BEER, 500.0))
    }

    @Test
    fun `pace memory only shortens after a repeated pattern`() {
        val pace = DrinkPaceMemory.inMemory()
        val base = 20.0
        assertEquals(base, pace.adjustedEstimate(DrinkCategory.BEER, base))

        // Two early finishes are not enough: one accidental tap must not count.
        repeat(2) { pace.recordEarlyFinish(DrinkCategory.BEER, base, 10.0) }
        assertEquals(base, pace.adjustedEstimate(DrinkCategory.BEER, base))

        pace.recordEarlyFinish(DrinkCategory.BEER, base, 10.0)
        val learned = pace.adjustedEstimate(DrinkCategory.BEER, base)
        assertTrue(learned < base, "expected a shortened estimate, got $learned")
        assertTrue(learned >= base * DrinkPaceMemory.MINIMUM_LEARNED_RATIO)

        // A finish that is not early enough is ignored entirely.
        val other = DrinkPaceMemory.inMemory()
        repeat(5) { other.recordEarlyFinish(DrinkCategory.WINE, base, 19.0) }
        assertEquals(base, other.adjustedEstimate(DrinkCategory.WINE, base))
    }

    @Test
    fun `a stored duration wins over the estimate`() {
        val pace = DrinkPaceMemory.inMemory()
        val auto = beer(epoch(2026, 6, 10, 20), duration = 0.0)
        val fixed = beer(epoch(2026, 6, 10, 20), duration = 45.0)
        assertEquals(20.0, auto.effectiveDrinkDurationMinutes(pace))
        assertEquals(45.0, fixed.effectiveDrinkDurationMinutes(pace))
    }

    // MARK: projection on the epoch timeline

    @Test
    fun `projection input agrees with the raw engine`() {
        val start = epoch(2026, 6, 10, 20)
        val drinks = listOf(beer(start), beer(start + 3600), beer(start + 7200))
        val input = BacProjectionInput(
            drinks = drinks,
            profile = profile,
            stomachStatus = StomachStatus.LIGHT,
            conservative = false
        )
        val engine = BacCalculator.currentBac(
            drinks = drinks.mapIndexed { i, d ->
                DrinkInput(i * 60.0, d.volumeML, d.abv, d.category, d.drinkDurationMinutes)
            },
            profile = profile,
            atMinute = 150.0,
            stomach = StomachStatus.LIGHT
        )
        val viaInput = input.currentBac(start + 150 * 60)
        assertTrue(
            abs(engine - viaInput) < 1e-9,
            "epoch timeline drifted from the engine: $engine vs $viaInput"
        )
        assertTrue(viaInput > 0.3, "three beers should register, got $viaInput")
    }

    @Test
    fun `sober forecast from now shrinks as time passes`() {
        val start = epoch(2026, 6, 10, 20)
        val input = BacProjectionInput(
            drinks = listOf(beer(start), beer(start + 1800)),
            profile = profile,
            stomachStatus = StomachStatus.LIGHT,
            conservative = false
        )
        // Past the peak the remaining wait must only ever get shorter. This is the
        // path the golden vectors never take: they always forecast from the origin.
        val afterPeak = start + 120 * 60
        var previous = Double.MAX_VALUE
        for (step in 0..8) {
            val now = afterPeak + step * 600
            val remaining = input.hoursUntil(0.0, now)
            assertNotNull(remaining, "no sober time found at step $step")
            assertTrue(
                remaining <= previous + 1e-9,
                "sober forecast grew at step $step: $previous then $remaining"
            )
            previous = remaining
        }
        assertTrue(previous < 6.0, "two beers should clear well inside 6 h, got $previous")
    }

    @Test
    fun `stable key changes when a learned pace changes`() {
        val pace = DrinkPaceMemory.inMemory()
        val drinks = listOf(beer(epoch(2026, 6, 10, 20), duration = 0.0))
        fun key() = BacProjectionInput(
            drinks, profile, StomachStatus.LIGHT, conservative = false, pace = pace
        ).stableKey

        val before = key()
        repeat(3) { pace.recordEarlyFinish(DrinkCategory.BEER, 20.0, 10.0) }
        assertTrue(before != key(), "a learned pace must invalidate a cached projection")
    }

    // MARK: hydration

    @Test
    fun `compensation always exceeds the bare deficit`() {
        val drinks = listOf(beer(epoch(2026, 6, 10, 20)), beer(epoch(2026, 6, 10, 21)))
        val net = HydrationCalculator.sessionNetHydration(drinks)
        assertTrue(net > 0, "two beers are net positive on water, got $net")

        // A shot has almost no water and a lot of alcohol, so it goes negative.
        val shot = Drink(
            id = "s", name = "Wodka", volumeML = 40.0, abv = 40.0, calories = 90,
            iconName = "flame.fill", category = DrinkCategory.SHOT,
            timestampEpochSeconds = epoch(2026, 6, 10, 22), drinkDurationMinutes = 1.0
        )
        val shots = List(6) { shot.copy(id = "s$it") }
        val shortfall = HydrationCalculator.sessionNetHydration(shots)
        assertTrue(shortfall < 0, "six shots should leave a deficit, got $shortfall")
        assertTrue(
            HydrationCalculator.compensationWaterMl(shortfall) > -shortfall,
            "ADH pass-through means you must drink more than the raw shortfall"
        )
        assertEquals(HydrationStatus.NEEDS_LOTS, HydrationCalculator.status(shortfall, profile))
    }

    // MARK: hangover

    @Test
    fun `water can soften a hangover but never erase a high peak`() {
        val dry = HangoverPredictor.predict(
            peakBAC = 1.6, durationHours = 5.0, waterGlasses = 0.0, drinksCount = 8
        )
        val hydrated = HangoverPredictor.predict(
            peakBAC = 1.6, durationHours = 5.0, waterGlasses = 12.0, drinksCount = 8
        )
        assertTrue(hydrated < dry, "water should help: $dry then $hydrated")
        assertTrue(
            hydrated >= HangoverLevel.MILD,
            "a 1.6 permille peak must never read as harmless, got $hydrated"
        )

        // The floor bites harder further up: no amount of water gets below MODERATE.
        val drowned = HangoverPredictor.predict(
            peakBAC = 2.1, durationHours = 2.0, waterGlasses = 40.0, drinksCount = 6
        )
        assertTrue(drowned >= HangoverLevel.MODERATE, "peak floor failed, got $drowned")
    }

    @Test
    fun `three permille is flagged as a medical emergency`() {
        assertEquals(
            HangoverLevel.LETHAL,
            HangoverPredictor.predict(3.0, durationHours = 4.0, waterGlasses = 99.0, drinksCount = 20)
        )
        assertTrue(
            HangoverPredictor.predict(2.99, 4.0, 0.0, 20) != HangoverLevel.LETHAL,
            "the alarm must start exactly at 3.0, not below"
        )
    }

    @Test
    fun `soft drinks do not stretch the session`() {
        val start = epoch(2026, 6, 10, 20)
        val cola = Drink(
            id = "c", name = "Cola", volumeML = 330.0, abv = 0.0, calories = 140,
            iconName = "cup.and.saucer.fill", category = DrinkCategory.SOFT_DRINK,
            timestampEpochSeconds = start + 8 * 3600, drinkDurationMinutes = 10.0
        )
        val beers = listOf(beer(start), beer(start + 3600))
        val withCola = HangoverPredictor.predict(beers + cola, profile)
        val withoutCola = HangoverPredictor.predict(beers, profile)
        assertEquals(withoutCola, withCola, "a late cola must not lengthen the night")
    }

    @Test
    fun `a lighter body tips into a warning sooner`() {
        val light = profile.copy(weightKg = 50.0, heightCm = 160.0)
        val heavy = profile.copy(weightKg = 110.0, heightCm = 195.0)
        val deficit = -200.0
        assertTrue(
            HydrationCalculator.dehydrationFraction(deficit, light) >
                HydrationCalculator.dehydrationFraction(deficit, heavy)
        )
    }
}
