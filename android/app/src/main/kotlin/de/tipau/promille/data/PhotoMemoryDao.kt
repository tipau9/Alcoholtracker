package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoMemoryDao {
    @Query("SELECT * FROM photo_memory ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PhotoMemoryEntity>>

    @Query("SELECT COUNT(*) FROM photo_memory")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: PhotoMemoryEntity)

    @Delete
    suspend fun delete(memory: PhotoMemoryEntity)

    @Query("DELETE FROM photo_memory")
    suspend fun deleteAll()
}
