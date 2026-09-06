package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionEventDao {
    // Vomit Events
    @Query("SELECT * FROM vomit_event WHERE timestamp >= :sinceEpochSeconds ORDER BY timestamp ASC")
    fun getVomitEventsSince(sinceEpochSeconds: Long): Flow<List<VomitEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVomitEvent(event: VomitEventEntity)

    @Delete
    suspend fun deleteVomitEvent(event: VomitEventEntity)

    @Query("DELETE FROM vomit_event")
    suspend fun deleteAllVomitEvents()

    // Meal Events
    @Query("SELECT * FROM meal_event WHERE timestamp >= :sinceEpochSeconds ORDER BY timestamp ASC")
    fun getMealEventsSince(sinceEpochSeconds: Long): Flow<List<MealEventEntity>>

    @Query("SELECT * FROM meal_event WHERE timestamp >= :startEpochSeconds AND timestamp < :endEpochSeconds ORDER BY timestamp ASC")
    fun getMealEventsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<MealEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealEvent(event: MealEventEntity)

    @Delete
    suspend fun deleteMealEvent(event: MealEventEntity)

    @Query("DELETE FROM meal_event")
    suspend fun deleteAllMealEvents()

    // Breathalyzer Readings
    @Query("SELECT * FROM breathalyzer_reading WHERE timestamp >= :sinceEpochSeconds ORDER BY timestamp ASC")
    fun getBreathalyzerReadingsSince(sinceEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>>

    @Query("SELECT * FROM breathalyzer_reading WHERE timestamp >= :startEpochSeconds AND timestamp < :endEpochSeconds ORDER BY timestamp ASC")
    fun getBreathalyzerReadingsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreathalyzerReading(reading: BreathalyzerReadingEntity)

    @Delete
    suspend fun deleteBreathalyzerReading(reading: BreathalyzerReadingEntity)

    @Query("DELETE FROM breathalyzer_reading")
    suspend fun deleteAllBreathalyzerReadings()
}
