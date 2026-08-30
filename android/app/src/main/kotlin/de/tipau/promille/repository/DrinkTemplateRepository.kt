package de.tipau.promille.repository

import android.content.Context
import de.tipau.promille.data.DrinkCatalog
import de.tipau.promille.data.DrinkTemplateDao
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.network.CommunityDrinkRow
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.fetchCommunityDrinks
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

    /** Learns a product locally after a barcode confirm (manual entry or an
     *  edited OFF/community candidate), same as iOS inserting a new
     *  DrinkTemplate on every confirm. */
    suspend fun insertLocalTemplate(template: DrinkTemplateEntity) = dao.insertOrReplace(template)

    /**
     * Pulls approved community drinks down into the local template store so a
     * barcode scan can match one offline, without a live lookup, the next time
     * it's seen. Mirrors AlcoholtrackerApp.swift's launch sync. Best-effort:
     * an unconfigured backend or network failure must not affect startup, so
     * failures are swallowed here rather than propagated.
     */
    suspend fun syncCommunityDrinks(supabase: SupabaseService) {
        val rows = runCatching { supabase.fetchCommunityDrinks() }.getOrNull() ?: return
        val known = dao.getAllBarcodes().toSet()
        val fresh = rows.filter { it.barcode.isNotBlank() && it.barcode !in known }
        if (fresh.isEmpty()) return
        dao.insertAll(fresh.map { it.toTemplateEntity() })
    }

    /** Community row id is already a stable Supabase uuid - reuse it directly
     *  so re-syncing the same drink is idempotent by id, on top of the
     *  barcode pre-filter above. */
    private fun CommunityDrinkRow.toTemplateEntity(): DrinkTemplateEntity = DrinkTemplateEntity(
        id = id,
        name = name,
        categoryRaw = category,
        volume = volume,
        abv = abv,
        calories = calories,
        iconName = iconName,
        barcode = barcode
    )
}
