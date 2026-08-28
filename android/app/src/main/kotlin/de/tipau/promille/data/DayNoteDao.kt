package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_note WHERE day = :day")
    suspend fun getNoteForDay(day: String): DayNoteEntity?

    @Query("SELECT * FROM day_note WHERE day >= :startDay AND day <= :endDay ORDER BY day ASC")
    fun getNotesBetween(startDay: String, endDay: String): Flow<List<DayNoteEntity>>

    /** One shot fetch for the backup sync, which needs a snapshot, not a stream. */
    @Query("SELECT * FROM day_note ORDER BY day ASC")
    suspend fun getAllOnce(): List<DayNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: DayNoteEntity)

    @Delete
    suspend fun delete(note: DayNoteEntity)

    @Query("DELETE FROM day_note")
    suspend fun deleteAll()
}
