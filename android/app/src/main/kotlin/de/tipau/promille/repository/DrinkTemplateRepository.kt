package de.tipau.promille.repository

import android.content.Context
import de.tipau.promille.data.DrinkCatalog
import de.tipau.promille.data.DrinkTemplateDao
import de.tipau.promille.data.DrinkTemplateEntity
import kotlinx.coroutines.flow.Flow

class DrinkTemplateRepository(private val dao: DrinkTemplateDao) {

    fun getTopFavorites(limit: Int = 6): Flow<List<DrinkTemplateEntity>> =
        dao.getTopFavorites(limit)

    fun search(query: String): Flow<List<DrinkTemplateEntity>> =
        dao.searchByName(query)

    fun getByCategory(categoryRaw: String): Flow<List<DrinkTemplateEntity>> =
        dao.getByCategory(categoryRaw)

    suspend fun getById(id: String): DrinkTemplateEntity? = dao.getById(id)

    suspend fun getAll(): List<DrinkTemplateEntity> = dao.getAll()

    /** Seeds assets/drink_catalog.json, version-gated exactly like seedIfNeeded. */
    suspend fun seedCatalog(context: Context) = DrinkCatalog.seedIfNeeded(context, dao)
}
