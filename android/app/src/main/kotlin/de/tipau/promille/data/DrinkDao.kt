package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkDao {
    @Query("SELECT * FROM drink WHERE timestamp >= :sinceEpochSeconds ORDER BY timestamp ASC")
    fun getDrinksSince(sinceEpochSeconds: Long): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drink WHERE timestamp >= :startEpochSeconds AND timestamp < :endEpochSeconds ORDER BY timestamp ASC")
    fun getDrinksBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<DrinkEntity>>

    @Query("SELECT COUNT(*) FROM drink WHERE timestamp >= :sinceEpochSeconds AND abv > 0")
    suspend fun getDrinkCountWithAlcoholSince(sinceEpochSeconds: Long): Int

    @Query("SELECT * FROM drink ORDER BY timestamp ASC")
    fun getAllDrinksSorted(): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drink ORDER BY timestamp ASC")
    suspend fun getAllDrinksSortedOnce(): List<DrinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(drink: DrinkEntity)

    @Update
    suspend fun update(drink: DrinkEntity)

    @Delete
    suspend fun delete(drink: DrinkEntity)

    @Query("DELETE FROM drink")
    suspend fun deleteAll()
}
