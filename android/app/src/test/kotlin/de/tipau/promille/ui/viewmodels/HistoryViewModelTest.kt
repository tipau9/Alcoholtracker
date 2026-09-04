package de.tipau.promille.ui.viewmodels

import de.tipau.promille.data.DrinkDao
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateDao
import de.tipau.promille.repository.DrinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.systemDefault()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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

    private class FakeTemplateDao : DrinkTemplateDao {
        override suspend fun getAll(): List<de.tipau.promille.data.DrinkTemplateEntity> = emptyList()
        override fun getTopFavorites(limit: Int): Flow<List<de.tipau.promille.data.DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override fun searchByName(query: String): Flow<List<de.tipau.promille.data.DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override fun getByCategory(category: String): Flow<List<de.tipau.promille.data.DrinkTemplateEntity>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): de.tipau.promille.data.DrinkTemplateEntity? = null
        override suspend fun incrementUsageCount(id: String) {}
        override suspend fun insertAll(templates: List<de.tipau.promille.data.DrinkTemplateEntity>) {}
        override suspend fun insertOrReplace(template: de.tipau.promille.data.DrinkTemplateEntity) {}
        override suspend fun update(template: de.tipau.promille.data.DrinkTemplateEntity) {}
        override suspend fun count(): Int = 0
        override suspend fun getAllBarcodes(): List<String> = emptyList()
        override suspend fun deleteCustom() {}
    }

    @Test
    fun `monthTrend returns null when previous month had no drinks`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeTemplateDao()
        val repo = DrinkRepository(drinkDao, templateDao)
        val vm = HistoryViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthTrend.collect {} }

        assertNull(vm.monthTrend.value, "monthTrend must be null when no drinks exist in previous month")
    }

    @Test
    fun `monthTrend calculates previous month drinks with running month limit`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeTemplateDao()
        val repo = DrinkRepository(drinkDao, templateDao)
        val vm = HistoryViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthTrend.collect {} }

        val now = YearMonth.now()
        val prev = now.minusMonths(1)
        val day1Prev = prev.atDay(1).atStartOfDay(zone).toEpochSecond() + 3600 * 12 // noon on 1st of prev month

        drinkDao.drinksFlow.value = listOf(
            DrinkEntity(
                id = "d1",
                name = "Bier",
                volume = 500.0,
                abv = 0.05,
                calories = 215,
                iconName = "beer",
                categoryRaw = "beer",
                timestampEpochSeconds = day1Prev
            )
        )

        val trend = vm.monthTrend.value
        assertNotNull(trend, "monthTrend must not be null when previous month had drinks")
        assertEquals(1, trend.previousTotalDrinks)
        assertEquals(LocalDate.now().dayOfMonth, trend.limitedToDays, "Running month must limit comparison to today's day of month")
    }

    @Test
    fun `monthTrend has no limit for historical months`() = runTest {
        val drinkDao = FakeDrinkDao()
        val templateDao = FakeTemplateDao()
        val repo = DrinkRepository(drinkDao, templateDao)
        val vm = HistoryViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.monthTrend.collect {} }

        // Go back 2 months so visible month is definitely not current
        vm.previousMonth()
        vm.previousMonth()

        val historicalMonth = vm.visibleMonth.value
        val prevOfHistorical = historicalMonth.minusMonths(1)
        val drinkTime = prevOfHistorical.atDay(15).atStartOfDay(zone).toEpochSecond() + 3600 * 12

        drinkDao.drinksFlow.value = listOf(
            DrinkEntity(
                id = "d1",
                name = "Bier",
                volume = 500.0,
                abv = 0.05,
                calories = 215,
                iconName = "beer",
                categoryRaw = "beer",
                timestampEpochSeconds = drinkTime
            )
        )

        val trend = vm.monthTrend.value
        assertNotNull(trend)
        assertEquals(1, trend.previousTotalDrinks)
        assertNull(trend.limitedToDays, "Historical months must compare full month without day limit")
    }
}
