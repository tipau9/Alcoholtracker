package de.tipau.promille.repository

import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.data.DrinkDao
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DrinkRepository(
    private val drinkDao: DrinkDao,
    private val templateDao: DrinkTemplateDao
) {
    fun getSessionDrinks(sinceEpochSeconds: Long): Flow<List<DrinkEntity>> =
        drinkDao.getDrinksSince(sinceEpochSeconds)

    fun getDrinksForHistory(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<DrinkEntity>> =
        drinkDao.getDrinksBetween(startEpochSeconds, endEpochSeconds)

    fun getAllDrinksSorted(): Flow<List<DrinkEntity>> =
        drinkDao.getAllDrinksSorted()

    suspend fun getAllDrinksSortedOnce(): List<DrinkEntity> =
        drinkDao.getAllDrinksSortedOnce()

    suspend fun getDrinkCountWithAlcoholSince(sinceEpochSeconds: Long): Int =
        drinkDao.getDrinkCountWithAlcoholSince(sinceEpochSeconds)

    suspend fun addDrink(drink: DrinkEntity) {
        drinkDao.insert(drink)
        drink.templateID?.let { templateDao.incrementUsageCount(it) }
    }

    suspend fun updateDrink(drink: DrinkEntity) {
        drinkDao.update(drink)
    }

    suspend fun deleteDrink(drink: DrinkEntity) {
        drinkDao.delete(drink)
    }

    suspend fun deleteAll() {
        drinkDao.deleteAll()
    }

    companion object {
        /** Converts a Room entity to the :bac engine's Drink type. */
        fun toDomainDrink(entity: DrinkEntity): Drink = Drink(
            id = entity.id,
            name = entity.name,
            volumeML = entity.volume,
            abv = entity.abv,
            calories = entity.calories,
            iconName = entity.iconName,
            category = DrinkCategory.from(entity.categoryRaw),
            timestampEpochSeconds = entity.timestampEpochSeconds,
            templateId = entity.templateID,
            mixerVolumeML = entity.mixerVolume,
            mixerWaterContentPercent = entity.mixerWaterContent,
            drinkDurationMinutes = entity.drinkDurationMinutes
        )

        /** Converts a :bac Drink back to a Room entity. */
        fun toEntity(drink: Drink): DrinkEntity = DrinkEntity(
            id = drink.id,
            templateID = drink.templateId,
            name = drink.name,
            volume = drink.volumeML,
            abv = drink.abv,
            calories = drink.calories,
            iconName = drink.iconName,
            timestampEpochSeconds = drink.timestampEpochSeconds,
            categoryRaw = drink.category.raw,
            mixerVolume = drink.mixerVolumeML,
            mixerWaterContent = drink.mixerWaterContentPercent,
            drinkDurationMinutes = drink.drinkDurationMinutes
        )
    }
}
