package de.tipau.promille.repository

import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.MealImpact
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.data.BreathalyzerReadingEntity
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.data.DayNoteEntity
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.data.FakeCrewMemberDao
import de.tipau.promille.data.FakeDayNoteDao
import de.tipau.promille.data.FakeDrinkDao
import de.tipau.promille.data.FakeDrinkTemplateDao
import de.tipau.promille.data.FakePhotoMemoryDao
import de.tipau.promille.data.FakeSessionEventDao
import de.tipau.promille.data.FakeUserProfileDao
import de.tipau.promille.data.MealEventEntity
import de.tipau.promille.data.PhotoMemoryEntity
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.data.VomitEventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTests {

    // ==========================================
    // DrinkRepository Tests
    // ==========================================

    @Test
    fun `drinkRepository - addDrink with template ID increments template usageCount`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeDrinkTemplateDao()
        val repository = DrinkRepository(drinkDao, templateDao)

        val template = DrinkTemplateEntity(
            id = "t1",
            name = "Helles",
            categoryRaw = "beer",
            volume = 500.0,
            abv = 5.0,
            calories = 200,
            iconName = "beer",
            usageCount = 3
        )
        templateDao.insertOrReplace(template)

        val drink = DrinkEntity(
            id = "d1",
            templateID = "t1",
            name = "Helles",
            volume = 500.0,
            abv = 5.0,
            calories = 200,
            iconName = "beer",
            timestampEpochSeconds = 1000L,
            categoryRaw = "beer"
        )

        repository.addDrink(drink)

        // Drink must be inserted
        val storedDrinks = repository.getAllDrinksSortedOnce()
        assertEquals(1, storedDrinks.size)
        assertEquals("d1", storedDrinks.first().id)

        // Template usage count must be incremented from 3 to 4
        val updatedTemplate = templateDao.getById("t1")
        assertNotNull(updatedTemplate)
        assertEquals(4, updatedTemplate.usageCount)
    }

    @Test
    fun `drinkRepository - addDrink without template ID does not call incrementUsageCount`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeDrinkTemplateDao()
        val repository = DrinkRepository(drinkDao, templateDao)

        val template = DrinkTemplateEntity(
            id = "t1",
            name = "Helles",
            categoryRaw = "beer",
            volume = 500.0,
            abv = 5.0,
            calories = 200,
            iconName = "beer",
            usageCount = 3
        )
        templateDao.insertOrReplace(template)

        val drink = DrinkEntity(
            id = "d1",
            templateID = null,
            name = "Custom Cocktail",
            volume = 300.0,
            abv = 15.0,
            calories = 250,
            iconName = "cocktail",
            timestampEpochSeconds = 1000L,
            categoryRaw = "cocktail"
        )

        repository.addDrink(drink)

        val storedDrinks = repository.getAllDrinksSortedOnce()
        assertEquals(1, storedDrinks.size)

        // Template usage count remains unchanged
        assertEquals(3, templateDao.getById("t1")?.usageCount)
    }

    @Test
    fun `drinkRepository - update, delete, deleteAll and query delegation`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeDrinkTemplateDao()
        val repository = DrinkRepository(drinkDao, templateDao)

        val d1 = DrinkEntity("d1", null, "Beer", 500.0, 5.0, 200, "beer", 100L, "beer")
        val d2 = DrinkEntity("d2", null, "Wine", 200.0, 12.0, 150, "wine", 200L, "wine")
        val d3 = DrinkEntity("d3", null, "Water", 500.0, 0.0, 0, "water", 300L, "nonAlcoholic")

        repository.addDrink(d1)
        repository.addDrink(d2)
        repository.addDrink(d3)

        assertEquals(3, repository.getAllDrinksSortedOnce().size)
        assertEquals(3, repository.getAllDrinksSorted().first().size)
        assertEquals(2, repository.getSessionDrinks(150L).first().size)
        assertEquals(2, repository.getDrinksForHistory(100L, 250L).first().size)
        assertEquals(2, repository.getDrinkCountWithAlcoholSince(50L))

        // Update
        val updatedD1 = d1.copy(volume = 1000.0)
        repository.updateDrink(updatedD1)
        assertEquals(1000.0, repository.getAllDrinksSortedOnce().first { it.id == "d1" }.volume)

        // Delete
        repository.deleteDrink(updatedD1)
        assertEquals(2, repository.getAllDrinksSortedOnce().size)

        // Delete all
        repository.deleteAll()
        assertTrue(repository.getAllDrinksSortedOnce().isEmpty())
    }

    @Test
    fun `drinkRepository - bidirectional entity and domain mappers`() {
        val domainDrink = de.tipau.promille.bac.Drink(
            id = "test-uuid",
            name = "Gin Tonic",
            volumeML = 300.0,
            abv = 12.5,
            calories = 180,
            iconName = "cocktail",
            category = DrinkCategory.COCKTAIL,
            timestampEpochSeconds = 1700000000L,
            templateId = "template-123",
            mixerVolumeML = 150.0,
            mixerWaterContentPercent = 90.0,
            drinkDurationMinutes = 15.0
        )

        val entity = DrinkRepository.toEntity(domainDrink)
        assertEquals("test-uuid", entity.id)
        assertEquals("template-123", entity.templateID)
        assertEquals("Gin Tonic", entity.name)
        assertEquals(300.0, entity.volume)
        assertEquals(12.5, entity.abv)
        assertEquals(180, entity.calories)
        assertEquals("cocktail", entity.iconName)
        assertEquals(1700000000L, entity.timestampEpochSeconds)
        assertEquals("cocktail", entity.categoryRaw)
        assertEquals(150.0, entity.mixerVolume)
        assertEquals(90.0, entity.mixerWaterContent)
        assertEquals(15.0, entity.drinkDurationMinutes)

        val restoredDomain = DrinkRepository.toDomainDrink(entity)
        assertEquals(domainDrink, restoredDomain)
    }

    // ==========================================
    // UserProfileRepository Tests
    // ==========================================

    @Test
    fun `userProfileRepository - debounced save updates pending immediately and persists after delay`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val dao = FakeUserProfileDao()

        val initial = UserProfileEntity(
            id = 1,
            weight = 75.0,
            height = 180.0,
            age = 30,
            eliminationRate = 0.15
        )
        dao.insertOrUpdate(initial)

        val repository = UserProfileRepository(dao, scope = testScope)
        testScope.advanceUntilIdle()

        assertEquals(75.0, repository.getProfileOnce()?.weight)

        // Trigger debounced update
        repository.updateDebounced { it.copy(weight = 80.0) }

        // Immediately, repository should show pending value 80.0
        assertEquals(80.0, repository.getProfileOnce()?.weight)
        // But DAO before debounce delay should still hold 75.0
        assertEquals(75.0, dao.getProfileOnce()?.weight)

        // Advance 200ms (less than 300ms debounce window)
        testScope.advanceTimeBy(200)
        assertEquals(75.0, dao.getProfileOnce()?.weight)

        // Advance past debounce delay
        testScope.advanceTimeBy(150)
        testScope.advanceUntilIdle()

        // Now DAO must have persisted 80.0
        assertEquals(80.0, dao.getProfileOnce()?.weight)
        assertEquals(80.0, repository.getProfileOnce()?.weight)
    }

    @Test
    fun `userProfileRepository - rapid debounced updates are coalesced`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val dao = FakeUserProfileDao()

        val initial = UserProfileEntity(id = 1, weight = 70.0, height = 175.0, age = 25, eliminationRate = 0.15)
        dao.insertOrUpdate(initial)

        val repository = UserProfileRepository(dao, scope = testScope)
        testScope.advanceUntilIdle()

        repository.updateDebounced { it.copy(weight = 71.0) }
        testScope.advanceTimeBy(100)
        repository.updateDebounced { it.copy(weight = 72.0) }
        testScope.advanceTimeBy(100)
        repository.updateDebounced { it.copy(weight = 73.0) }

        // Before 300ms from last call
        assertEquals(70.0, dao.getProfileOnce()?.weight)

        // Advance past debounce window
        testScope.advanceTimeBy(350)
        testScope.advanceUntilIdle()

        assertEquals(73.0, dao.getProfileOnce()?.weight)
    }

    @Test
    fun `userProfileRepository - immediate update cancels pending debounce and writes to DAO`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val dao = FakeUserProfileDao()

        val initial = UserProfileEntity(id = 1, weight = 70.0, height = 175.0, age = 25, eliminationRate = 0.15)
        dao.insertOrUpdate(initial)

        val repository = UserProfileRepository(dao, scope = testScope)
        testScope.advanceUntilIdle()

        // Debounced update to 75.0
        repository.updateDebounced { it.copy(weight = 75.0) }

        // Immediate write to 85.0
        val direct = initial.copy(weight = 85.0)
        repository.update(direct)

        assertEquals(85.0, dao.getProfileOnce()?.weight)
        assertEquals(85.0, repository.getProfileOnce()?.weight)

        // Advance time to ensure the cancelled debounced job does not overwrite
        testScope.advanceTimeBy(500)
        testScope.advanceUntilIdle()
        assertEquals(85.0, dao.getProfileOnce()?.weight)
    }

    @Test
    fun `userProfileRepository - getOrCreate creates default entity if none exists`() = runTest {
        val dao = FakeUserProfileDao()
        val repository = UserProfileRepository(dao)

        assertNull(dao.getProfileOnce())

        val created = repository.getOrCreate()
        assertEquals(70.0, created.weight)
        assertEquals(175.0, created.height)
        assertEquals(25, created.age)
        assertEquals(0.15, created.eliminationRate)

        // Second call returns existing
        val existing = repository.getOrCreate()
        assertEquals(created, existing)
    }

    @Test
    fun `userProfileRepository - toProfile domain mapping`() {
        val entity = UserProfileEntity(
            id = 1,
            weight = 82.5,
            height = 185.0,
            age = 32,
            genderRaw = "female",
            eliminationRate = 0.18,
            toleranceMode = true,
            isProbationaryDriver = true,
            conservativeSafety = true,
            conservativeEverywhere = false,
            stomachStatusRaw = "full",
            warningThreshold = 0.45,
            tipsyThreshold = 0.02,
            drunkThreshold = 0.35,
            carefulThreshold = 0.75,
            dangerThreshold = 1.40
        )

        val profile = UserProfileRepository.toProfile(entity)
        assertEquals(82.5, profile.weightKg)
        assertEquals(185.0, profile.heightCm)
        assertEquals(32, profile.age)
        assertEquals(Gender.FEMALE, profile.gender)
        assertEquals(0.18, profile.eliminationRate)
        assertTrue(profile.toleranceMode)
        assertTrue(profile.isProbationaryDriver)
        assertTrue(profile.conservativeSafety)
        assertFalse(profile.conservativeEverywhere)
        assertEquals(StomachStatus.FULL, profile.defaultStomachStatus)
        assertEquals(0.45, profile.warningThreshold)
        assertEquals(0.02, profile.tipsyThreshold)
        assertEquals(0.35, profile.drunkThreshold)
        assertEquals(0.75, profile.carefulThreshold)
        assertEquals(1.40, profile.dangerThreshold)
    }

    // ==========================================
    // DayNoteRepository Tests
    // ==========================================

    @Test
    fun `dayNoteRepository - getOrCreate returns existing or creates new`() = runTest {
        val dao = FakeDayNoteDao()
        val repo = DayNoteRepository(dao)

        val newNote = repo.getOrCreate("2026-08-28")
        assertEquals("2026-08-28", newNote.day)
        assertEquals("", newNote.text)
        assertEquals(0, newNote.moodRaw)

        // Modify note
        dao.insertOrUpdate(newNote.copy(text = "Had fun", moodRaw = 5))

        val existing = repo.getOrCreate("2026-08-28")
        assertEquals("Had fun", existing.text)
        assertEquals(5, existing.moodRaw)
    }

    @Test
    fun `dayNoteRepository - saveNote updates text and mood preserving day`() = runTest {
        val dao = FakeDayNoteDao()
        val repo = DayNoteRepository(dao)

        // Save note on non-existent day
        repo.saveNote("2026-08-25", "First entry", 4)
        assertEquals("First entry", repo.getNoteForDay("2026-08-25")?.text)
        assertEquals(4, repo.getNoteForDay("2026-08-25")?.moodRaw)

        // Update existing note
        repo.saveNote("2026-08-25", "Updated entry", 5)
        assertEquals("Updated entry", repo.getNoteForDay("2026-08-25")?.text)
        assertEquals(5, repo.getNoteForDay("2026-08-25")?.moodRaw)
    }

    @Test
    fun `dayNoteRepository - getNotesBetween and update`() = runTest {
        val dao = FakeDayNoteDao()
        val repo = DayNoteRepository(dao)

        repo.saveNote("2026-08-20", "Day 20", 3)
        repo.saveNote("2026-08-21", "Day 21", 4)
        repo.saveNote("2026-08-22", "Day 22", 5)

        val between = repo.getNotesBetween("2026-08-20", "2026-08-21").first()
        assertEquals(2, between.size)
        assertEquals(listOf("2026-08-20", "2026-08-21"), between.map { it.day })

        repo.update(DayNoteEntity("2026-08-20", "Rewritten", 1))
        assertEquals("Rewritten", repo.getNoteForDay("2026-08-20")?.text)
    }

    // ==========================================
    // SessionEventRepository Tests
    // ==========================================

    @Test
    fun `sessionEventRepository - logVomit, logMeal, logBreathalyzer assign UUID and timestamp`() = runTest {
        val dao = FakeSessionEventDao()
        val repo = SessionEventRepository(dao)

        repo.logVomit()
        val vomits = repo.getVomitEventsSince(0L).first()
        assertEquals(1, vomits.size)
        assertTrue(vomits.first().id.isNotBlank())
        assertTrue(vomits.first().timestamp > 0)

        repo.logMeal(MealImpact.FULL_MEAL, "Burger")
        val meals = repo.getMealEventsSince(0L).first()
        assertEquals(1, meals.size)
        assertEquals("fullMeal", meals.first().impactRaw)
        assertEquals("Burger", meals.first().name)
        assertTrue(meals.first().id.isNotBlank())

        repo.logBreathalyzerReading(0.55, 0.50, "device", "Dräger test")
        val readings = repo.getBreathalyzerReadingsSince(0L).first()
        assertEquals(1, readings.size)
        assertEquals(0.55, readings.first().measuredBAC)
        assertEquals(0.50, readings.first().estimatedBAC)
        assertEquals("device", readings.first().sourceRaw)
        assertEquals("Dräger test", readings.first().note)

        // Delete single events
        repo.deleteVomitEvent(vomits.first())
        assertTrue(repo.getVomitEventsSince(0L).first().isEmpty())

        repo.deleteMealEvent(meals.first())
        assertTrue(repo.getMealEventsSince(0L).first().isEmpty())

        repo.deleteBreathalyzerReading(readings.first())
        assertTrue(repo.getBreathalyzerReadingsSince(0L).first().isEmpty())
    }

    @Test
    fun `sessionEventRepository - clearAll wipes all event tables`() = runTest {
        val dao = FakeSessionEventDao()
        val repo = SessionEventRepository(dao)

        repo.logVomit()
        repo.logMeal(MealImpact.SNACK, "Pretzels")
        repo.logBreathalyzerReading(0.2, 0.22)

        assertEquals(1, repo.getVomitEventsSince(0L).first().size)
        assertEquals(1, repo.getMealEventsSince(0L).first().size)
        assertEquals(1, repo.getBreathalyzerReadingsSince(0L).first().size)

        repo.clearAll()

        assertTrue(repo.getVomitEventsSince(0L).first().isEmpty())
        assertTrue(repo.getMealEventsSince(0L).first().isEmpty())
        assertTrue(repo.getBreathalyzerReadingsSince(0L).first().isEmpty())
    }

    @Test
    fun `sessionEventRepository - toDomainMealEvent mapper`() {
        val entity = MealEventEntity(
            id = "meal-1",
            timestamp = 1700000000L,
            impactRaw = "fullMeal",
            name = "Steak"
        )
        val domain = SessionEventRepository.toDomainMealEvent(entity)
        assertEquals("meal-1", domain.id)
        assertEquals(1700000000L, domain.timestampEpochSeconds)
        assertEquals(MealImpact.FULL_MEAL, domain.impact)
        assertEquals("Steak", domain.name)
    }

    // ==========================================
    // CrewRepository Tests
    // ==========================================

    @Test
    fun `crewRepository - members flow, countNonSelf and CRUD operations`() = runTest {
        val dao = FakeCrewMemberDao()
        val repo = CrewRepository(dao)

        val self = CrewMemberEntity("m1", "Me", "M", isSelf = true, joinedAt = 100L)
        val buddy = CrewMemberEntity("m2", "Buddy", "B", isSelf = false, joinedAt = 200L)

        repo.insertOrUpdate(self)
        repo.insertOrUpdate(buddy)

        val members = repo.members.first()
        assertEquals(2, members.size)
        assertEquals(listOf("Buddy", "Me"), members.map { it.name })

        assertEquals(1, repo.countNonSelf())

        val updatedBuddy = buddy.copy(currentBAC = 0.4)
        repo.update(updatedBuddy)
        assertEquals(0.4, repo.members.first().first { it.id == "m2" }.currentBAC)

        repo.delete(self)
        assertEquals(1, repo.members.first().size)

        repo.deleteAll()
        assertTrue(repo.members.first().isEmpty())
    }

    // ==========================================
    // PhotoMemoryRepository Tests
    // ==========================================

    @Test
    fun `photoMemoryRepository - addMemory auto-assigns ID and timestamp, deleteMemory`() = runTest {
        val dao = FakePhotoMemoryDao()
        val repo = PhotoMemoryRepository(dao)

        val added = repo.addMemory("photo.jpg", 0.65, "Party time")
        assertEquals("photo.jpg", added.filename)
        assertEquals(0.65, added.bacAtTime)
        assertEquals("Party time", added.caption)
        assertTrue(added.id.isNotBlank())
        assertTrue(added.timestamp > 0)

        val memories = repo.memories.first()
        assertEquals(1, memories.size)
        assertEquals(added.id, memories.first().id)

        repo.deleteMemory(added)
        assertTrue(repo.memories.first().isEmpty())
    }

    // ==========================================
    // DrinkTemplateRepository Tests
    // ==========================================

    @Test
    fun `drinkTemplateRepository - queries delegation`() = runTest {
        val dao = FakeDrinkTemplateDao()
        val repo = DrinkTemplateRepository(dao)

        val t1 = DrinkTemplateEntity("t1", "IPA", "beer", 330.0, 6.5, 180, "beer", usageCount = 10)
        val t2 = DrinkTemplateEntity("t2", "Stout", "beer", 500.0, 5.0, 220, "beer", usageCount = 20)
        val t3 = DrinkTemplateEntity("t3", "Chardonnay", "wine", 200.0, 13.0, 160, "wine", usageCount = 5)

        dao.insertOrReplace(t1)
        dao.insertOrReplace(t2)
        dao.insertOrReplace(t3)

        assertEquals(3, repo.getAll().size)
        assertEquals("t1", repo.getById("t1")?.id)
        assertEquals(listOf("Stout", "IPA"), repo.getTopFavorites(2).first().map { it.name })
        assertEquals(listOf("Stout"), repo.search("stout").first().map { it.name })
        assertEquals(1, repo.getByCategory("wine").first().size)
    }
}
