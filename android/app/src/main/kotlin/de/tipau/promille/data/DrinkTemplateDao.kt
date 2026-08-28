package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkTemplateDao {
    @Query("SELECT * FROM drink_template ORDER BY name ASC")
    suspend fun getAll(): List<DrinkTemplateEntity>

    @Query("SELECT * FROM drink_template ORDER BY usageCount DESC LIMIT :limit")
    fun getTopFavorites(limit: Int = 6): Flow<List<DrinkTemplateEntity>>

    @Query("SELECT * FROM drink_template WHERE name LIKE '%' || :query || '%' ORDER BY usageCount DESC, name ASC")
    fun searchByName(query: String): Flow<List<DrinkTemplateEntity>>

    @Query("SELECT * FROM drink_template WHERE categoryRaw = :category ORDER BY usageCount DESC, name ASC")
    fun getByCategory(category: String): Flow<List<DrinkTemplateEntity>>

    @Query("SELECT * FROM drink_template WHERE id = :id")
    suspend fun getById(id: String): DrinkTemplateEntity?

    @Query("UPDATE drink_template SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsageCount(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<DrinkTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(template: DrinkTemplateEntity)

    @Update
    suspend fun update(template: DrinkTemplateEntity)

    @Query("SELECT COUNT(*) FROM drink_template")
    suspend fun count(): Int

    /**
     * Only the user's own templates. The seeded catalog is not user data, so an
     * account switch must not wipe it.
     */
    @Query("DELETE FROM drink_template WHERE isCustom = 1")
    suspend fun deleteCustom()
}
