package de.tipau.promille.repository

import de.tipau.promille.data.DayNoteDao
import de.tipau.promille.data.DayNoteEntity
import kotlinx.coroutines.flow.Flow

class DayNoteRepository(private val dao: DayNoteDao) {

    suspend fun getNoteForDay(day: String): DayNoteEntity? =
        dao.getNoteForDay(day)

    fun getNotesBetween(startDay: String, endDay: String): Flow<List<DayNoteEntity>> =
        dao.getNotesBetween(startDay, endDay)

    suspend fun getOrCreate(day: String): DayNoteEntity {
        return dao.getNoteForDay(day) ?: DayNoteEntity(day = day).also {
            dao.insertOrUpdate(it)
        }
    }

    suspend fun update(note: DayNoteEntity) {
        dao.insertOrUpdate(note)
    }
}
