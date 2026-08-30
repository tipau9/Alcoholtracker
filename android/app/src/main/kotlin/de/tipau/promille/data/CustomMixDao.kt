package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomMixDao {
    @Query("SELECT * FROM custom_mix ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CustomMixEntity>>

    @Query("SELECT * FROM custom_mix ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CustomMixEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mix: CustomMixEntity)

    @Delete
    suspend fun delete(mix: CustomMixEntity)

    @Query("DELETE FROM custom_mix")
    suspend fun deleteAll()
}
