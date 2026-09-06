package de.tipau.promille.repository

import de.tipau.promille.bac.MealEvent
import de.tipau.promille.bac.MealImpact
import de.tipau.promille.data.BreathalyzerReadingEntity
import de.tipau.promille.data.MealEventEntity
import de.tipau.promille.data.SessionEventDao
import de.tipau.promille.data.VomitEventEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionEventRepository(private val dao: SessionEventDao) {

    fun getVomitEventsSince(sinceEpochSeconds: Long): Flow<List<VomitEventEntity>> =
        dao.getVomitEventsSince(sinceEpochSeconds)

    fun getMealEventsSince(sinceEpochSeconds: Long): Flow<List<MealEventEntity>> =
        dao.getMealEventsSince(sinceEpochSeconds)

    fun getMealEventsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<MealEventEntity>> =
        dao.getMealEventsBetween(startEpochSeconds, endEpochSeconds)

    fun getBreathalyzerReadingsSince(sinceEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>> =
        dao.getBreathalyzerReadingsSince(sinceEpochSeconds)

    fun getBreathalyzerReadingsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>> =
        dao.getBreathalyzerReadingsBetween(startEpochSeconds, endEpochSeconds)

    suspend fun logVomit() {
        val now = System.currentTimeMillis() / 1000
        dao.insertVomitEvent(
            VomitEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = now
            )
        )
    }

    suspend fun logMeal(impact: MealImpact, name: String = "") {
        val now = System.currentTimeMillis() / 1000
        dao.insertMealEvent(
            MealEventEntity(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                impactRaw = impact.raw,
                name = name
            )
        )
    }

    suspend fun logBreathalyzerReading(
        measuredBAC: Double,
        estimatedBAC: Double,
        source: String = "manual",
        note: String = ""
    ) {
        val now = System.currentTimeMillis() / 1000
        dao.insertBreathalyzerReading(
            BreathalyzerReadingEntity(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                measuredBAC = measuredBAC,
                estimatedBAC = estimatedBAC,
                sourceRaw = source,
                note = note
            )
        )
    }

    suspend fun deleteVomitEvent(event: VomitEventEntity) = dao.deleteVomitEvent(event)
    suspend fun deleteMealEvent(event: MealEventEntity) = dao.deleteMealEvent(event)
    suspend fun deleteBreathalyzerReading(reading: BreathalyzerReadingEntity) = dao.deleteBreathalyzerReading(reading)

    suspend fun clearAll() {
        dao.deleteAllVomitEvents()
        dao.deleteAllMealEvents()
        dao.deleteAllBreathalyzerReadings()
    }

    companion object {
        fun toDomainMealEvent(entity: MealEventEntity): MealEvent = MealEvent(
            id = entity.id,
            timestampEpochSeconds = entity.timestamp,
            impact = MealImpact.from(entity.impactRaw),
            name = entity.name
        )
    }
}
