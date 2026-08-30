package de.tipau.promille.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaoTests {

    // ==========================================
    // DrinkDao Tests
    // ==========================================

    @Test
    fun `drinkDao - insert, update, delete and sorting by timestamp`() = runTest {
        val dao = FakeDrinkDao()

        val drink1 = DrinkEntity(
            id = "d1",
            name = "Beer",
            volume = 500.0,
            abv = 5.0,
            calories = 200,
            iconName = "beer",
            timestampEpochSeconds = 1000L,
            categoryRaw = "beer"
        )
        val drink2 = DrinkEntity(
            id = "d2",
            name = "Wine",
            volume = 200.0,
            abv = 12.0,
            calories = 150,
            iconName = "wine",
            timestampEpochSeconds = 500L,
            categoryRaw = "wine"
        )
        val drink3 = DrinkEntity(
            id = "d3",
            name = "Shot",
            volume = 40.0,
            abv = 40.0,
            calories = 90,
            iconName = "shot",
            timestampEpochSeconds = 1500L,
            categoryRaw = "spirits"
        )

        dao.insert(drink1)
        dao.insert(drink2)
        dao.insert(drink3)

        // Verify sorted once by timestamp ASC: drink2 (500), drink1 (1000), drink3 (1500)
        val sortedOnce = dao.getAllDrinksSortedOnce()
        assertEquals(3, sortedOnce.size)
        assertEquals(listOf("d2", "d1", "d3"), sortedOnce.map { it.id })

        // Verify Flow emits sorted
        val sortedFlow = dao.getAllDrinksSorted().first()
        assertEquals(listOf("d2", "d1", "d3"), sortedFlow.map { it.id })

        // Update drink1
        val updatedDrink1 = drink1.copy(name = "Big Beer", volume = 1000.0)
        dao.update(updatedDrink1)
        val afterUpdate = dao.getAllDrinksSortedOnce().first { it.id == "d1" }
        assertEquals("Big Beer", afterUpdate.name)
        assertEquals(1000.0, afterUpdate.volume)

        // Delete drink2
        dao.delete(drink2)
        assertEquals(listOf("d1", "d3"), dao.getAllDrinksSortedOnce().map { it.id })

        // Delete all
        dao.deleteAll()
        assertTrue(dao.getAllDrinksSortedOnce().isEmpty())
        assertTrue(dao.getAllDrinksSorted().first().isEmpty())
    }

    @Test
    fun `drinkDao - getDrinksSince and getDrinksBetween filtering`() = runTest {
        val dao = FakeDrinkDao()

        val d1 = DrinkEntity("d1", null, "D1", 500.0, 5.0, 200, "beer", 100L, "beer")
        val d2 = DrinkEntity("d2", null, "D2", 500.0, 5.0, 200, "beer", 200L, "beer")
        val d3 = DrinkEntity("d3", null, "D3", 500.0, 5.0, 200, "beer", 300L, "beer")
        val d4 = DrinkEntity("d4", null, "D4", 500.0, 5.0, 200, "beer", 400L, "beer")

        dao.insert(d1)
        dao.insert(d2)
        dao.insert(d3)
        dao.insert(d4)

        val since200 = dao.getDrinksSince(200L).first()
        assertEquals(listOf("d2", "d3", "d4"), since200.map { it.id })

        val between100and350 = dao.getDrinksBetween(100L, 350L).first()
        assertEquals(listOf("d1", "d2", "d3"), between100and350.map { it.id })

        val between200and400 = dao.getDrinksBetween(200L, 400L).first()
        assertEquals(listOf("d2", "d3"), between200and400.map { it.id })
    }

    @Test
    fun `drinkDao - getDrinkCountWithAlcoholSince only counts alcoholic drinks`() = runTest {
        val dao = FakeDrinkDao()

        val alc1 = DrinkEntity("a1", null, "Beer", 500.0, 5.0, 200, "beer", 200L, "beer")
        val nonAlc = DrinkEntity("na", null, "Water", 500.0, 0.0, 0, "water", 250L, "nonAlcoholic")
        val alc2 = DrinkEntity("a2", null, "Shot", 40.0, 40.0, 90, "shot", 300L, "spirits")
        val oldAlc = DrinkEntity("old", null, "Old Beer", 500.0, 5.0, 200, "beer", 50L, "beer")

        dao.insert(alc1)
        dao.insert(nonAlc)
        dao.insert(alc2)
        dao.insert(oldAlc)

        val countSince100 = dao.getDrinkCountWithAlcoholSince(100L)
        assertEquals(2, countSince100, "Should count only a1 and a2 (timestamp >= 100 and abv > 0)")

        val countSince250 = dao.getDrinkCountWithAlcoholSince(250L)
        assertEquals(1, countSince250, "Should count only a2, non-alc at 250 is ignored")
    }

    @Test
    fun `drinkDao - insert onConflict REPLACE overwrites entity`() = runTest {
        val dao = FakeDrinkDao()
        val original = DrinkEntity("d1", null, "Original", 500.0, 5.0, 200, "beer", 100L, "beer")
        val updated = DrinkEntity("d1", null, "Replaced", 400.0, 6.0, 180, "beer", 150L, "beer")

        dao.insert(original)
        assertEquals("Original", dao.getAllDrinksSortedOnce().first().name)

        dao.insert(updated)
        val result = dao.getAllDrinksSortedOnce()
        assertEquals(1, result.size)
        assertEquals("Replaced", result.first().name)
        assertEquals(400.0, result.first().volume)
    }

    // ==========================================
    // DrinkTemplateDao Tests
    // ==========================================

    @Test
    fun `drinkTemplateDao - getAll sorts by name ASC`() = runTest {
        val dao = FakeDrinkTemplateDao()
        dao.insertOrReplace(DrinkTemplateEntity("3", "Zebra", "cocktail", 300.0, 10.0, 200, "cocktail"))
        dao.insertOrReplace(DrinkTemplateEntity("1", "Apple Cider", "cider", 500.0, 4.5, 210, "cider"))
        dao.insertOrReplace(DrinkTemplateEntity("2", "Beer", "beer", 500.0, 5.0, 200, "beer"))

        val all = dao.getAll()
        assertEquals(listOf("Apple Cider", "Beer", "Zebra"), all.map { it.name })
    }

    @Test
    fun `drinkTemplateDao - getTopFavorites orders by usageCount DESC and limits`() = runTest {
        val dao = FakeDrinkTemplateDao()
        dao.insertOrReplace(DrinkTemplateEntity("1", "Drink A", "beer", 500.0, 5.0, 200, "beer", usageCount = 10))
        dao.insertOrReplace(DrinkTemplateEntity("2", "Drink B", "beer", 500.0, 5.0, 200, "beer", usageCount = 50))
        dao.insertOrReplace(DrinkTemplateEntity("3", "Drink C", "beer", 500.0, 5.0, 200, "beer", usageCount = 30))
        dao.insertOrReplace(DrinkTemplateEntity("4", "Drink D", "beer", 500.0, 5.0, 200, "beer", usageCount = 5))

        val top2 = dao.getTopFavorites(limit = 2).first()
        assertEquals(listOf("Drink B", "Drink C"), top2.map { it.name })

        val top6 = dao.getTopFavorites(limit = 6).first()
        assertEquals(listOf("Drink B", "Drink C", "Drink A", "Drink D"), top6.map { it.name })
    }

    @Test
    fun `drinkTemplateDao - searchByName case-insensitive and ranked by usageCount DESC`() = runTest {
        val dao = FakeDrinkTemplateDao()
        dao.insertOrReplace(DrinkTemplateEntity("1", "Augustiner Helles", "beer", 500.0, 5.2, 210, "beer", usageCount = 5))
        dao.insertOrReplace(DrinkTemplateEntity("2", "Tegernseer Hell", "beer", 500.0, 4.8, 200, "beer", usageCount = 20))
        dao.insertOrReplace(DrinkTemplateEntity("3", "Paulaner Weißbier", "beer", 500.0, 5.5, 230, "beer", usageCount = 12))

        val results = dao.searchByName("hell").first()
        assertEquals(2, results.size)
        assertEquals("Tegernseer Hell", results[0].name)
        assertEquals("Augustiner Helles", results[1].name)
    }

    @Test
    fun `drinkTemplateDao - getByCategory filters and orders by usageCount DESC`() = runTest {
        val dao = FakeDrinkTemplateDao()
        dao.insertOrReplace(DrinkTemplateEntity("1", "Merlot", "wine", 200.0, 13.0, 160, "wine", usageCount = 2))
        dao.insertOrReplace(DrinkTemplateEntity("2", "Riesling", "wine", 200.0, 11.5, 140, "wine", usageCount = 8))
        dao.insertOrReplace(DrinkTemplateEntity("3", "Pils", "beer", 500.0, 4.9, 210, "beer", usageCount = 15))

        val wines = dao.getByCategory("wine").first()
        assertEquals(listOf("Riesling", "Merlot"), wines.map { it.name })
    }

    @Test
    fun `drinkTemplateDao - incrementUsageCount and getById`() = runTest {
        val dao = FakeDrinkTemplateDao()
        val template = DrinkTemplateEntity("t1", "Gin Tonic", "longdrink", 300.0, 12.0, 180, "cocktail", usageCount = 0)
        dao.insertOrReplace(template)

        assertEquals(0, dao.getById("t1")?.usageCount)

        dao.incrementUsageCount("t1")
        assertEquals(1, dao.getById("t1")?.usageCount)

        dao.incrementUsageCount("t1")
        assertEquals(2, dao.getById("t1")?.usageCount)

        // Non-existent ID does nothing
        dao.incrementUsageCount("unknown")
        assertNull(dao.getById("unknown"))
    }

    @Test
    fun `drinkTemplateDao - insertAll with IGNORE conflict strategy`() = runTest {
        val dao = FakeDrinkTemplateDao()
        val t1 = DrinkTemplateEntity("1", "Original T1", "beer", 500.0, 5.0, 200, "beer")
        dao.insertOrReplace(t1)

        val batch = listOf(
            DrinkTemplateEntity("1", "Ignored Overwrite T1", "beer", 330.0, 4.0, 150, "beer"),
            DrinkTemplateEntity("2", "New T2", "wine", 200.0, 12.0, 160, "wine")
        )
        dao.insertAll(batch)

        assertEquals(2, dao.count())
        assertEquals("Original T1", dao.getById("1")?.name)
        assertEquals("New T2", dao.getById("2")?.name)
    }

    @Test
    fun `drinkTemplateDao - deleteCustom deletes only custom templates`() = runTest {
        val dao = FakeDrinkTemplateDao()
        val seeded = DrinkTemplateEntity("s1", "Seeded Beer", "beer", 500.0, 5.0, 200, "beer", isCustom = false)
        val custom1 = DrinkTemplateEntity("c1", "My Mix", "cocktail", 300.0, 10.0, 200, "cocktail", isCustom = true)
        val custom2 = DrinkTemplateEntity("c2", "My Shot", "spirits", 40.0, 30.0, 70, "shot", isCustom = true)

        dao.insertOrReplace(seeded)
        dao.insertOrReplace(custom1)
        dao.insertOrReplace(custom2)
        assertEquals(3, dao.count())

        dao.deleteCustom()
        assertEquals(1, dao.count())
        assertEquals("s1", dao.getAll().first().id)
        assertNull(dao.getById("c1"))
        assertNull(dao.getById("c2"))
    }

    // ==========================================
    // UserProfileDao Tests
    // ==========================================

    @Test
    fun `userProfileDao - insert, getProfileOnce and reactive Flow emission`() = runTest {
        val dao = FakeUserProfileDao()
        assertNull(dao.getProfileOnce())
        assertNull(dao.getProfile().first())

        val p1 = UserProfileEntity(
            id = 1,
            weight = 80.0,
            height = 180.0,
            age = 28,
            eliminationRate = 0.15,
            genderRaw = "male"
        )
        dao.insertOrUpdate(p1)

        assertEquals(p1, dao.getProfileOnce())
        assertEquals(p1, dao.getProfile().first())

        val p2 = p1.copy(weight = 82.5, toleranceMode = true)
        dao.insertOrUpdate(p2)

        assertEquals(82.5, dao.getProfileOnce()?.weight)
        assertTrue(dao.getProfileOnce()?.toleranceMode == true)
        assertEquals(82.5, dao.getProfile().first()?.weight)
    }

    // ==========================================
    // DayNoteDao Tests
    // ==========================================

    @Test
    fun `dayNoteDao - CRUD, range query and sorting by day`() = runTest {
        val dao = FakeDayNoteDao()

        val n1 = DayNoteEntity(day = "2026-08-26", text = "Day 1 note", moodRaw = 3)
        val n2 = DayNoteEntity(day = "2026-08-28", text = "Day 3 note", moodRaw = 5)
        val n3 = DayNoteEntity(day = "2026-08-27", text = "Day 2 note", moodRaw = 4)

        dao.insertOrUpdate(n1)
        dao.insertOrUpdate(n2)
        dao.insertOrUpdate(n3)

        assertEquals(n1, dao.getNoteForDay("2026-08-26"))
        assertNull(dao.getNoteForDay("2026-08-29"))

        val allOnce = dao.getAllOnce()
        assertEquals(listOf("2026-08-26", "2026-08-27", "2026-08-28"), allOnce.map { it.day })

        val between = dao.getNotesBetween("2026-08-26", "2026-08-27").first()
        assertEquals(listOf("2026-08-26", "2026-08-27"), between.map { it.day })

        // Update note
        val updatedN1 = n1.copy(text = "Updated text", moodRaw = 2)
        dao.insertOrUpdate(updatedN1)
        assertEquals("Updated text", dao.getNoteForDay("2026-08-26")?.text)

        // Delete note
        dao.delete(updatedN1)
        assertNull(dao.getNoteForDay("2026-08-26"))

        // Delete all
        dao.deleteAll()
        assertTrue(dao.getAllOnce().isEmpty())
    }

    // ==========================================
    // SessionEventDao Tests
    // ==========================================

    @Test
    fun `sessionEventDao - vomit, meal, breathalyzer event operations and since filters`() = runTest {
        val dao = FakeSessionEventDao()

        // Vomit events
        val v1 = VomitEventEntity("v1", 100L)
        val v2 = VomitEventEntity("v2", 200L)
        dao.insertVomitEvent(v1)
        dao.insertVomitEvent(v2)
        assertEquals(listOf("v1", "v2"), dao.getVomitEventsSince(50L).first().map { it.id })
        assertEquals(listOf("v2"), dao.getVomitEventsSince(150L).first().map { it.id })

        dao.deleteVomitEvent(v1)
        assertEquals(listOf("v2"), dao.getVomitEventsSince(50L).first().map { it.id })
        dao.deleteAllVomitEvents()
        assertTrue(dao.getVomitEventsSince(0L).first().isEmpty())

        // Meal events
        val m1 = MealEventEntity("m1", 300L, "snack", "Chips")
        val m2 = MealEventEntity("m2", 400L, "heavy", "Pizza")
        dao.insertMealEvent(m1)
        dao.insertMealEvent(m2)
        assertEquals(listOf("m1", "m2"), dao.getMealEventsSince(200L).first().map { it.id })
        assertEquals("Pizza", dao.getMealEventsSince(350L).first().first().name)

        dao.deleteMealEvent(m1)
        assertEquals(listOf("m2"), dao.getMealEventsSince(200L).first().map { it.id })
        dao.deleteAllMealEvents()
        assertTrue(dao.getMealEventsSince(0L).first().isEmpty())

        // Breathalyzer readings
        val b1 = BreathalyzerReadingEntity("b1", 500L, 0.45, 0.48, "manual", "First check")
        val b2 = BreathalyzerReadingEntity("b2", 600L, 0.30, 0.32, "bluetooth", "Second check")
        dao.insertBreathalyzerReading(b1)
        dao.insertBreathalyzerReading(b2)
        assertEquals(listOf("b1", "b2"), dao.getBreathalyzerReadingsSince(400L).first().map { it.id })
        assertEquals(0.30, dao.getBreathalyzerReadingsSince(550L).first().first().measuredBAC)

        dao.deleteBreathalyzerReading(b1)
        assertEquals(listOf("b2"), dao.getBreathalyzerReadingsSince(400L).first().map { it.id })
        dao.deleteAllBreathalyzerReadings()
        assertTrue(dao.getBreathalyzerReadingsSince(0L).first().isEmpty())
    }

    // ==========================================
    // CrewMemberDao Tests
    // ==========================================

    @Test
    fun `crewMemberDao - CRUD, countNonSelf and partial server update`() = runTest {
        val dao = FakeCrewMemberDao()

        val self = CrewMemberEntity(
            id = "self_id",
            name = "Myself",
            avatarInitial = "M",
            isSelf = true,
            joinedAt = 1000L
        )
        val friend1 = CrewMemberEntity(
            id = "f1",
            name = "Bob",
            avatarInitial = "B",
            currentBAC = 0.0,
            isSelf = false,
            joinedAt = 1100L,
            friendCode = "BOB123",
            isHome = true
        )
        val friend2 = CrewMemberEntity(
            id = "f2",
            name = "Alice",
            avatarInitial = "A",
            currentBAC = 0.5,
            isSelf = false,
            joinedAt = 1200L,
            friendCode = "ALI456"
        )

        dao.insertOrUpdate(self)
        dao.insertOrUpdate(friend1)
        dao.insertOrUpdate(friend2)

        // Sorted by name ASC: Alice, Bob, Myself
        val all = dao.getAllOnce()
        assertEquals(listOf("Alice", "Bob", "Myself"), all.map { it.name })
        assertEquals(listOf("Alice", "Bob", "Myself"), dao.getAll().first().map { it.name })

        // countNonSelf
        assertEquals(2, dao.countNonSelf())

        // applyServerUpdate: updates currentBAC, lastDrinkTimestamp, isProbationaryDriver, sosActive, highAlertFired
        // while preserving name, avatarInitial, friendCode, isHome, joinedAt
        dao.applyServerUpdate(
            id = "f1",
            currentBac = 0.85,
            lastDrinkTimestamp = 1500L,
            isProbationaryDriver = true,
            sosActive = true,
            highAlertFired = true
        )

        val updatedF1 = dao.getAllOnce().first { it.id == "f1" }
        assertEquals(0.85, updatedF1.currentBAC)
        assertEquals(1500L, updatedF1.lastDrinkTimestamp)
        assertTrue(updatedF1.isProbationaryDriver)
        assertTrue(updatedF1.sosActive)
        assertTrue(updatedF1.highAlertFired)
        // Preserved properties
        assertEquals("Bob", updatedF1.name)
        assertEquals("B", updatedF1.avatarInitial)
        assertEquals("BOB123", updatedF1.friendCode)
        assertTrue(updatedF1.isHome)
        assertFalse(updatedF1.isSelf)

        // Delete and deleteAll
        dao.delete(friend2)
        assertEquals(2, dao.getAllOnce().size)
        dao.deleteAll()
        assertTrue(dao.getAllOnce().isEmpty())
    }

    // ==========================================
    // PhotoMemoryDao Tests
    // ==========================================

    @Test
    fun `photoMemoryDao - insert, count, delete and sorting by timestamp DESC`() = runTest {
        val dao = FakePhotoMemoryDao()

        val p1 = PhotoMemoryEntity("p1", 1000L, "photo1.jpg", "First", 0.2)
        val p2 = PhotoMemoryEntity("p2", 3000L, "photo2.jpg", "Third", 0.6)
        val p3 = PhotoMemoryEntity("p3", 2000L, "photo3.jpg", "Second", 0.4)

        dao.insert(p1)
        dao.insert(p2)
        dao.insert(p3)

        assertEquals(3, dao.count())

        // Sorted by timestamp DESC: p2 (3000), p3 (2000), p1 (1000)
        val all = dao.getAll().first()
        assertEquals(listOf("p2", "p3", "p1"), all.map { it.id })

        dao.delete(p3)
        assertEquals(2, dao.count())
        assertEquals(listOf("p2", "p1"), dao.getAll().first().map { it.id })

        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    // ==========================================
    // CustomMixDao Tests
    // ==========================================

    @Test
    fun `customMixDao - insert, getAll sorted by createdAt DESC, delete and deleteAll`() = runTest {
        val dao = FakeCustomMixDao()

        val mix1 = CustomMixEntity("m1", "Mix One", "[]", 100L)
        val mix2 = CustomMixEntity("m2", "Mix Two", "[]", 300L)
        val mix3 = CustomMixEntity("m3", "Mix Three", "[]", 200L)

        dao.insert(mix1)
        dao.insert(mix2)
        dao.insert(mix3)

        val allOnce = dao.getAllOnce()
        assertEquals(listOf("m2", "m3", "m1"), allOnce.map { it.id })

        val allFlow = dao.getAll().first()
        assertEquals(listOf("m2", "m3", "m1"), allFlow.map { it.id })

        dao.delete(mix2)
        assertEquals(listOf("m3", "m1"), dao.getAllOnce().map { it.id })

        dao.deleteAll()
        assertTrue(dao.getAllOnce().isEmpty())
    }

    // ==========================================
    // PendingSyncDao Tests
    // ==========================================

    @Test
    fun `pendingSyncDao - insert, getPending sorted by createdAt ASC, deleteByType, update, count`() = runTest {
        val dao = FakePendingSyncDao()

        val op1 = PendingSyncOperationEntity("op1", "DRINK_INSERT", "{}", 200L, 0)
        val op2 = PendingSyncOperationEntity("op2", "PROFILE_UPDATE", "{}", 100L, 0)
        val op3 = PendingSyncOperationEntity("op3", "DRINK_INSERT", "{}", 300L, 0)

        dao.insert(op1)
        dao.insert(op2)
        dao.insert(op3)

        assertEquals(3, dao.count())

        // Sorted by createdAt ASC: op2 (100), op1 (200), op3 (300)
        val pending = dao.getPending()
        assertEquals(listOf("op2", "op1", "op3"), pending.map { it.id })

        // Update retry count
        dao.update(op2.copy(retryCount = 3))
        val updatedOp2 = dao.getPending().first { it.id == "op2" }
        assertEquals(3, updatedOp2.retryCount)

        // Delete by type DRINK_INSERT removes op1 and op3
        dao.deleteByType("DRINK_INSERT")
        assertEquals(1, dao.count())
        assertEquals(listOf("op2"), dao.getPending().map { it.id })

        // Delete single
        dao.delete(updatedOp2)
        assertEquals(0, dao.count())
    }
}
