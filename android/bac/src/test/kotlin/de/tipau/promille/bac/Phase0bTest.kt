package de.tipau.promille.bac

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks for the catalogs and rule engines ported in Phase 0b: status bands,
 * mixers, the water log, achievement rules and the insight discoveries. Each
 * test pins the one thing that would silently drift away from iOS.
 */
class Phase0bTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(berlin).toEpochSecond()

    private fun drink(
        at: Long,
        name: String = "Bier",
        category: DrinkCategory = DrinkCategory.BEER,
        abv: Double = 5.0,
        volumeML: Double = 500.0
    ) = Drink(
        id = "$name-$at",
        name = name,
        volumeML = volumeML,
        abv = abv,
        calories = 210,
        iconName = "mug.fill",
        category = category,
        timestampEpochSeconds = at,
        drinkDurationMinutes = 20.0
    )

    private val profile = Profile(
        weightKg = 80.0, heightCm = 180.0, age = 30, gender = Gender.MALE
    )

    // MARK: status bands and skins

    @Test
    fun `status bands follow the user thresholds, not the defaults`() {
        assertEquals(BacStatus.SOBER, BacStatus.of(0.005))
        assertEquals(BacStatus.DRUNK, BacStatus.of(0.79))
        assertEquals(BacStatus.CAREFUL, BacStatus.of(0.80))
        assertEquals(BacStatus.DANGER, BacStatus.of(1.5))

        // A user who lowered the bands must see the stricter status at the same BAC.
        val strict = profile.copy(carefulThreshold = 0.4, dangerThreshold = 0.9)
        assertEquals(BacStatus.CAREFUL, BacStatus.of(0.5, strict))
        assertEquals(BacStatus.DRUNK, BacStatus.of(0.5, null))
    }

    @Test
    fun `every skin names all five bands distinctly`() {
        assertEquals(10, StatusSkin.entries.size)
        for (skin in StatusSkin.entries) {
            val labels = BacStatus.entries.map { skin.label(it) }
            assertEquals(5, labels.size)
            assertEquals(5, labels.toSet().size, "$skin reuses a label across bands")
            assertTrue(labels.none { it.isBlank() }, "$skin has an empty label")
        }
        assertEquals("Nüchtern", StatusSkin.STANDARD.label(BacStatus.SOBER))
        assertEquals("Felipe", StatusSkin.NORMAL.label(BacStatus.DANGER))
    }

    // MARK: mixers

    @Test
    fun `mixer catalog is complete and grouped in enum order`() {
        assertEquals(55, MixerDatabase.ALL.size, "the iOS catalog has 55 mixers")
        assertEquals(55, MixerDatabase.ALL.map { it.name }.toSet().size, "duplicate mixer name")

        val grouped = MixerDatabase.grouped()
        val order = grouped.map { it.first }
        assertEquals(order.sortedBy { it.ordinal }, order, "categories must follow enum order")
        for ((_, items) in grouped) {
            assertEquals(items.sortedBy { it.name }, items, "mixers inside a group are sorted")
        }
        assertEquals(MixerDatabase.ALL.size, grouped.sumOf { it.second.size })

        // Search is case-insensitive on the name, and empty means everything.
        assertTrue(MixerDatabase.search("cola").isNotEmpty())
        assertEquals(MixerDatabase.search("COLA"), MixerDatabase.search("cola"))
        assertEquals(MixerDatabase.ALL.size, MixerDatabase.search("   ").size)
    }

    // MARK: water log

    @Test
    fun `an unlogged day is not the same as zero glasses`() {
        val log = WaterLog.inMemory()
        val day = LocalDate.of(2026, 6, 10)
        assertNull(log.loggedGlasses(day), "never logged must stay null so callers can estimate")

        val now = epoch(2026, 6, 10, 20)
        log.addGlassToday(now, berlin)
        log.addGlassToday(now, berlin)
        assertEquals(2, log.loggedGlasses(day))

        repeat(5) { log.removeGlassToday(now, berlin) }
        assertEquals(0, log.loggedGlasses(day), "removing must floor at zero, not go negative")

        // A merge from the server keeps the higher count: a stale device must never
        // erase glasses logged elsewhere.
        log.merge(mapOf(WaterLog.key(day) to 4))
        assertEquals(4, log.loggedGlasses(day))
        log.merge(mapOf(WaterLog.key(day) to 1))
        assertEquals(4, log.loggedGlasses(day))
    }

    // MARK: achievements

    @Test
    fun `variety needs three categories inside one logical day`() {
        val night = epoch(2026, 6, 10, 22)
        val sameNight = listOf(
            drink(night, "Bier", DrinkCategory.BEER, abv = 4.8),
            drink(night + 3600, "Wein", DrinkCategory.WINE, abv = 12.0, volumeML = 200.0),
            // 01:00 still belongs to the same logical day.
            drink(epoch(2026, 6, 11, 1), "Wodka", DrinkCategory.SHOT, abv = 40.0, volumeML = 20.0)
        )
        val spread = listOf(
            sameNight[0],
            drink(night + 3 * 86400, "Wein", DrinkCategory.WINE, abv = 12.0, volumeML = 200.0),
            drink(night + 6 * 86400, "Wodka", DrinkCategory.SHOT, abv = 40.0, volumeML = 20.0)
        )
        val now = epoch(2026, 6, 20, 12)
        fun earned(drinks: List<Drink>, id: String) = AchievementCatalog.isEarned(
            id, drinks, false, 0, 0, 0,
            AchievementCatalog.EvalContext(drinks, profile, now, now - 60 * 86400, berlin)
        )
        assertTrue(earned(sameNight, "session_variety"))
        assertFalse(earned(spread, "session_variety"), "three nights are not one session")
        assertTrue(earned(spread, "categories_3"), "three categories overall still counts")
        assertTrue(earned(sameNight, "abv_spectrum"), "5, 12 and 40 percent span the spectrum")
        assertTrue(earned(sameNight, "night_owl"), "the 01:00 shot is a night owl")
        assertFalse(earned(sameNight, "categories_all"))
    }

    @Test
    fun `the sober streak counts the days since the last drink`() {
        val now = epoch(2026, 6, 20, 12)
        val drinks = listOf(drink(now - 10 * 86400))
        assertEquals(
            10,
            AchievementCatalog.soberStreak(drinks, now, now - 60 * 86400, berlin)
        )
        val cache = AchievementCatalog.EvalContext(drinks, profile, now, now - 60 * 86400, berlin)
        assertTrue(AchievementCatalog.isEarned("sober_7", drinks, false, 0, 0, 0, cache))
        assertFalse(AchievementCatalog.isEarned("sober_14", drinks, false, 0, 0, 0, cache))

        // Without any alcohol history there is nothing to be proud of yet.
        val empty = AchievementCatalog.soberStreak(emptyList(), now, now - 60 * 86400, berlin)
        assertEquals(0, empty)
    }

    // MARK: personal insights

    @Test
    fun `a night across midnight is one drinking day`() {
        val drinks = listOf(
            drink(epoch(2026, 6, 10, 23)),
            drink(epoch(2026, 6, 11, 1))
        )
        val insights = PersonalInsights.build(
            drinks = drinks, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 11, 12), zone = berlin
        )
        assertEquals(2, insights.totalDrinks)
        assertEquals(1, insights.drinkingDays, "23:00 and 01:00 are the same night")
        assertEquals(LocalDate.of(2026, 6, 10), insights.highestPeakDay)
        assertTrue(insights.averageSessionMinutes >= 120, "the session spans both hours")
    }

    @Test
    fun `the typical start averages around midnight, not around noon`() {
        val drinks = listOf(
            drink(epoch(2026, 6, 10, 23)),
            drink(epoch(2026, 6, 13, 1))
        )
        val insights = PersonalInsights.build(
            drinks = drinks, profile = null, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 14, 12), zone = berlin
        )
        assertEquals(2, insights.drinkingDays)
        // 23:00 and 01:00 average to midnight on a circular clock. A naive mean
        // would land at 12:00 and claim the user starts at lunchtime.
        assertEquals(0, insights.typicalStartMinutesAfterMidnight)
    }

    @Test
    fun `the alcohol free streak stops at the last drinking day`() {
        val now = epoch(2026, 6, 20, 12)
        val insights = PersonalInsights.build(
            drinks = listOf(drink(now - 5 * 86400)), profile = profile,
            cutoffEpochSeconds = null, nowEpochSeconds = now, zone = berlin
        )
        assertEquals(5, insights.currentAlcoholFreeStreak)
        assertEquals(5, insights.alcoholFreeDays)
        assertEquals(1, insights.drinkingDays)
    }

    @Test
    fun `a favourite only counts above a forty percent share`() {
        // Six nights, ten drinks, six of them the same beer.
        val base = epoch(2026, 6, 1, 20)
        val many = (0 until 6).map { drink(base + it * 86400L, "Helles") } +
            (0 until 4).map {
                drink(base + it * 86400L + 3600, "Wein", DrinkCategory.WINE, abv = 12.0)
            }
        val insights = PersonalInsights.build(
            drinks = many, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), zone = berlin
        )
        val favourite = insights.discoveries.firstOrNull { it.title == "Dein klarer Favorit" }
        assertNotNull(favourite)
        assertTrue(favourite.detail.contains("Helles"))
        assertEquals("Helles", insights.topDrinks.first().name)
        assertEquals("Bier", insights.topDrinks.first().subtitle)

        // Drop the share below 40 percent and the claim must disappear.
        val balanced = many.take(4) + many.drop(6)
        val quiet = PersonalInsights.build(
            drinks = balanced, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), zone = berlin
        )
        assertTrue(quiet.discoveries.none { it.title == "Dein klarer Favorit" })
    }

    @Test
    fun `the mood discovery needs three rated nights on each side`() {
        val base = epoch(2026, 6, 1, 20)
        val drinks = mutableListOf<Drink>()
        val notes = mutableListOf<DayNote>()
        for (day in 0 until 6) {
            val start = base + day * 86400L
            // Good nights are single drinks, bad nights are four.
            val count = if (day < 3) 1 else 4
            repeat(count) { drinks.add(drink(start + it * 1800L, "Bier $day$it")) }
            notes.add(
                DayNote(
                    day = LogicalDay.dateOf(start, berlin),
                    mood = if (day < 3) DayMood.HAPPY else DayMood.REGRET
                )
            )
        }
        val insights = PersonalInsights.build(
            drinks = drinks, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), notes = notes, zone = berlin
        )
        val mood = insights.discoveries.firstOrNull { it.title == "Morgenstimmung und Drinkzahl" }
        assertNotNull(mood)
        assertTrue(mood.detail.contains("3,0"), "German decimal comma expected: ${mood.detail}")

        // Five notes are below the threshold, so nothing is claimed.
        val tooFew = PersonalInsights.build(
            drinks = drinks, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), notes = notes.take(5), zone = berlin
        )
        assertTrue(tooFew.discoveries.none { it.title == "Morgenstimmung und Drinkzahl" })
    }

    @Test
    fun `water and mood only compare days that were actually logged`() {
        val base = epoch(2026, 6, 1, 20)
        val log = WaterLog.inMemory()
        val drinks = mutableListOf<Drink>()
        val notes = mutableListOf<DayNote>()
        for (day in 0 until 6) {
            val start = base + day * 86400L
            val logicalDay = LogicalDay.dateOf(start, berlin)
            repeat(4) { drinks.add(drink(start + it * 1800L, "Bier $day$it")) }
            val hydrated = day < 3
            // Two glasses per four drinks clears the one-per-two bar, one does not.
            log.merge(mapOf(WaterLog.key(logicalDay) to if (hydrated) 2 else 1))
            notes.add(
                DayNote(
                    day = logicalDay,
                    mood = if (hydrated) DayMood.HAPPY else DayMood.TERRIBLE
                )
            )
        }
        val insights = PersonalInsights.build(
            drinks = drinks, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), notes = notes,
            waterLog = log, zone = berlin
        )
        assertNotNull(insights.discoveries.firstOrNull { it.title == "Wasser und Morgenstimmung" })

        // Same nights, no water history: the comparison must not be invented.
        val blind = PersonalInsights.build(
            drinks = drinks, profile = profile, cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 20, 12), notes = notes, zone = berlin
        )
        assertTrue(blind.discoveries.none { it.title == "Wasser und Morgenstimmung" })
    }

    @Test
    fun `breathalyzer bias is reported in permille with a comma`() {
        val now = epoch(2026, 6, 20, 12)
        val readings = (0 until 5).map {
            BreathalyzerReading(now - it * 3600L, measuredBAC = 0.60, estimatedBAC = 0.45)
        }
        val insights = PersonalInsights.build(
            drinks = listOf(drink(now - 7200)), profile = profile,
            cutoffEpochSeconds = null, nowEpochSeconds = now,
            breathalyzerReadings = readings, zone = berlin
        )
        val measurement = insights.discoveries.firstOrNull { it.title == "Messung und Schätzung" }
        assertNotNull(measurement)
        assertTrue(measurement.detail.contains("0,15 ‰"), measurement.detail)
        assertTrue(measurement.detail.contains("über"), measurement.detail)
    }

    @Test
    fun `every catalog entry is complete and unique`() {
        val all = AchievementCatalog.ALL
        assertEquals(49, all.size, "an added or dropped achievement needs a matching isEarned branch")
        assertEquals(all.size, all.map { it.id }.toSet().size, "duplicate achievement id")
        for (a in all) {
            assertTrue(a.title.isNotBlank(), "${a.id} has no title")
            assertTrue(a.subtitle.isNotBlank(), "${a.id} has no subtitle")
            assertTrue(a.icon.isNotBlank(), "${a.id} has no icon")
        }
    }

    @Test
    fun `a night of cola produces no insights at all`() {
        val at = epoch(2026, 6, 10, 20)
        val cola = drink(at, "Cola", DrinkCategory.SOFT_DRINK, abv = 0.0, volumeML = 330.0)
        val water = drink(at + 3600, "Wasser", DrinkCategory.WATER, abv = 0.0, volumeML = 500.0)
        val insights = PersonalInsights.build(
            drinks = listOf(cola, water),
            profile = profile,
            cutoffEpochSeconds = null,
            nowEpochSeconds = epoch(2026, 6, 11, 12),
            zone = berlin
        )
        assertEquals(PersonalInsights.empty, insights)
    }
}

/**
 * The sign-up gate. A false accept sends a confirmation mail nobody receives and
 * the account is unrecoverable; a false reject blocks a valid address outright.
 */
class EmailValidationTest {

    @Test
    fun `ordinary addresses pass`() {
        assertTrue(isValidEmail("name@beispiel.de"))
        assertTrue(isValidEmail("max.mustermann+bar@sub.beispiel.co.uk"))
    }

    @Test
    fun `the shapes AuthGate rejects stay rejected`() {
        assertFalse(isValidEmail(""), "empty")
        assertFalse(isValidEmail("beispiel.de"), "no at sign")
        assertFalse(isValidEmail("a@b@c.de"), "two at signs")
        assertFalse(isValidEmail("@beispiel.de"), "empty local part")
        assertFalse(isValidEmail("name@beispiel"), "no dot in the domain")
        assertFalse(isValidEmail("name@.beispiel.de"), "domain starts with a dot")
        assertFalse(isValidEmail("name@beispiel.de."), "domain ends with a dot")
        assertFalse(isValidEmail("name@beispiel..de"), "empty domain label")
    }
}

/**
 * The friend list decays a published value. Without it a friend who closed the
 * app at 1.2 is shown at 1.2 all night, and careScore drives who the app tells
 * you to check on.
 */
class CrewMathTest {

    private val now = 1_700_000_000L

    @Test
    fun `a published value decays at the flat rate`() {
        assertEquals(1.2, CrewMath.estimatedBac(1.5, now - 2 * 3600, now), 1e-9)
        assertEquals(0.0, CrewMath.estimatedBac(0.2, now - 10 * 3600, now), 1e-9)
    }

    @Test
    fun `without a server timestamp the value is passed through unchanged`() {
        assertEquals(0.7, CrewMath.estimatedBac(0.7, null, now), 1e-9)
        assertNull(CrewMath.updatedMinutesAgo(null, now))
    }

    @Test
    fun `careScore crosses 40 exactly where the attention list starts`() {
        // Band 2 (drunk) is 40, which is the threshold Home and Crew filter on.
        assertEquals(40, CrewMath.careScore(0.5, now, now))
        assertEquals(20, CrewMath.careScore(0.2, now, now))
        // Band 3 plus the fresh-and-high bonus.
        assertEquals(70, CrewMath.careScore(1.2, now, now))
        // Same value, but published half an hour ago: no bonus, and decayed.
        assertEquals(60, CrewMath.careScore(1.2, now - 1800, now))
    }
}
