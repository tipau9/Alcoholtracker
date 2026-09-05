package de.tipau.promille.ui.viewmodels

import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.data.CrewMemberDao
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.data.DrinkDao
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateDao
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.data.UserProfileDao
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.CrewRepository
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.service.AchievementService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testServiceScope = CoroutineScope(dispatcher)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = testServiceScope.cancel()

    private class FakeDrinkDao : DrinkDao {
        val drinksFlow = MutableStateFlow<List<DrinkEntity>>(emptyList())
        override fun getDrinksSince(timestampEpochSeconds: Long): Flow<List<DrinkEntity>> = drinksFlow
        override fun getDrinksBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<DrinkEntity>> = drinksFlow
        override fun getAllDrinksSorted(): Flow<List<DrinkEntity>> = drinksFlow
        override suspend fun getAllDrinksSortedOnce(): List<DrinkEntity> = drinksFlow.value
        override suspend fun getDrinkCountWithAlcoholSince(sinceEpochSeconds: Long): Int = drinksFlow.value.count { it.abv > 0 }
        override suspend fun insert(drink: DrinkEntity) { drinksFlow.value = drinksFlow.value + drink }
        override suspend fun update(drink: DrinkEntity) {}
        override suspend fun delete(drink: DrinkEntity) {}
        override suspend fun deleteAll() { drinksFlow.value = emptyList() }
    }

    private class FakeDrinkTemplateDao : DrinkTemplateDao {
        override suspend fun getAll(): List<DrinkTemplateEntity> = emptyList()
        override fun getTopFavorites(limit: Int): Flow<List<DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override fun searchByName(query: String): Flow<List<DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override fun getByCategory(category: String): Flow<List<DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): DrinkTemplateEntity? = null
        override suspend fun incrementUsageCount(id: String) {}
        override suspend fun insertAll(templates: List<DrinkTemplateEntity>) {}
        override suspend fun insertOrReplace(template: DrinkTemplateEntity) {}
        override suspend fun update(template: DrinkTemplateEntity) {}
        override suspend fun count(): Int = 0
        override suspend fun getAllBarcodes(): List<String> = emptyList()
        override suspend fun deleteCustom() {}
    }

    private class FakeUserProfileDao : UserProfileDao {
        val profileFlow = MutableStateFlow<UserProfileEntity?>(
            UserProfileEntity(id = 1, weight = 75.0, height = 180.0, age = 25, eliminationRate = 0.15, genderRaw = "male")
        )
        override fun getProfile(): Flow<UserProfileEntity?> = profileFlow
        override suspend fun getProfileOnce(): UserProfileEntity? = profileFlow.value
        override suspend fun insertOrUpdate(profile: UserProfileEntity) { profileFlow.value = profile }
    }

    private class FakeCrewMemberDao : CrewMemberDao {
        val crewFlow = MutableStateFlow<List<CrewMemberEntity>>(emptyList())
        override fun getAll(): Flow<List<CrewMemberEntity>> = crewFlow
        override suspend fun getAllOnce(): List<CrewMemberEntity> = crewFlow.value
        override suspend fun countNonSelf(): Int = crewFlow.value.count { !it.isSelf }
        override suspend fun insertOrUpdate(member: CrewMemberEntity) {}
        override suspend fun update(member: CrewMemberEntity) {}
        override suspend fun applyServerUpdate(
            id: String,
            currentBac: Double,
            lastDrinkTimestamp: Long?,
            isProbationaryDriver: Boolean,
            sosActive: Boolean,
            highAlertFired: Boolean,
            isMutual: Boolean
        ) {}
        override suspend fun delete(member: CrewMemberEntity) {}
        override suspend fun deleteAll() {}
    }

    @Test
    fun `unlockedCount is 0 with empty drink history`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeDrinkTemplateDao()
        val profileDao = FakeUserProfileDao()
        val crewDao = FakeCrewMemberDao()

        val drinkRepo = DrinkRepository(drinkDao, templateDao)
        val profileRepo = UserProfileRepository(profileDao, testServiceScope)
        val crewRepo = CrewRepository(crewDao)

        val achievementService = AchievementService(drinkRepo, profileRepo, crewRepo, testServiceScope)
        val viewModel = SettingsViewModel(profileRepo, achievementService)

        assertEquals(0, viewModel.unlockedCount.value, "empty history must yield 0 unlocked achievements")
    }

    @Test
    fun `unlockedCount is greater than 0 with at least one drink`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeDrinkTemplateDao()
        val profileDao = FakeUserProfileDao()
        val crewDao = FakeCrewMemberDao()

        val drinkRepo = DrinkRepository(drinkDao, templateDao)
        val profileRepo = UserProfileRepository(profileDao, testServiceScope)
        val crewRepo = CrewRepository(crewDao)

        val achievementService = AchievementService(drinkRepo, profileRepo, crewRepo, testServiceScope)
        val viewModel = SettingsViewModel(profileRepo, achievementService)

        // Add a beer (triggers first_beer and first_drink)
        drinkDao.insert(
            DrinkEntity(
                id = "d1",
                name = "Helles",
                volume = 500.0,
                abv = 5.0,
                calories = 200,
                iconName = "mug.fill",
                categoryRaw = DrinkCategory.BEER.raw,
                timestampEpochSeconds = System.currentTimeMillis() / 1000
            )
        )

        assertTrue(viewModel.unlockedCount.value > 0, "adding a drink must unlock achievements (>0)")
    }
}
