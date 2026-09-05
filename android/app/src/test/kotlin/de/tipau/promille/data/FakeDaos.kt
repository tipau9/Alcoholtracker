package de.tipau.promille.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDrinkDao : DrinkDao {
    private val drinks = MutableStateFlow<Map<String, DrinkEntity>>(emptyMap())

    override fun getDrinksSince(sinceEpochSeconds: Long): Flow<List<DrinkEntity>> =
        drinks.map { map ->
            map.values
                .filter { it.timestampEpochSeconds >= sinceEpochSeconds }
                .sortedBy { it.timestampEpochSeconds }
        }

    override fun getDrinksBetween(
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): Flow<List<DrinkEntity>> =
        drinks.map { map ->
            map.values
                .filter { it.timestampEpochSeconds >= startEpochSeconds && it.timestampEpochSeconds < endEpochSeconds }
                .sortedBy { it.timestampEpochSeconds }
        }

    override suspend fun getDrinkCountWithAlcoholSince(sinceEpochSeconds: Long): Int =
        drinks.value.values.count { it.timestampEpochSeconds >= sinceEpochSeconds && it.abv > 0.0 }

    override fun getAllDrinksSorted(): Flow<List<DrinkEntity>> =
        drinks.map { map -> map.values.sortedBy { it.timestampEpochSeconds } }

    override suspend fun getAllDrinksSortedOnce(): List<DrinkEntity> =
        drinks.value.values.sortedBy { it.timestampEpochSeconds }

    override suspend fun insert(drink: DrinkEntity) {
        drinks.update { it + (drink.id to drink) }
    }

    override suspend fun update(drink: DrinkEntity) {
        drinks.update { it + (drink.id to drink) }
    }

    override suspend fun delete(drink: DrinkEntity) {
        drinks.update { it - drink.id }
    }

    override suspend fun deleteAll() {
        drinks.value = emptyMap()
    }
}

class FakeDrinkTemplateDao : DrinkTemplateDao {
    private val templates = MutableStateFlow<Map<String, DrinkTemplateEntity>>(emptyMap())

    override suspend fun getAll(): List<DrinkTemplateEntity> =
        templates.value.values.sortedBy { it.name }

    override fun getTopFavorites(limit: Int): Flow<List<DrinkTemplateEntity>> =
        templates.map { map ->
            map.values
                .sortedWith(compareByDescending<DrinkTemplateEntity> { it.usageCount }.thenBy { it.name })
                .take(limit)
        }

    override fun searchByName(query: String): Flow<List<DrinkTemplateEntity>> =
        templates.map { map ->
            map.values
                .filter { it.name.contains(query, ignoreCase = true) }
                .sortedWith(compareByDescending<DrinkTemplateEntity> { it.usageCount }.thenBy { it.name })
        }

    override fun getByCategory(category: String): Flow<List<DrinkTemplateEntity>> =
        templates.map { map ->
            map.values
                .filter { it.categoryRaw == category }
                .sortedWith(compareByDescending<DrinkTemplateEntity> { it.usageCount }.thenBy { it.name })
        }

    override suspend fun getById(id: String): DrinkTemplateEntity? =
        templates.value[id]

    override suspend fun incrementUsageCount(id: String) {
        templates.update { map ->
            val existing = map[id] ?: return@update map
            map + (id to existing.copy(usageCount = existing.usageCount + 1))
        }
    }

    override suspend fun insertAll(templates: List<DrinkTemplateEntity>) {
        this.templates.update { current ->
            val next = current.toMutableMap()
            for (t in templates) {
                if (!next.containsKey(t.id)) {
                    next[t.id] = t
                }
            }
            next
        }
    }

    override suspend fun insertOrReplace(template: DrinkTemplateEntity) {
        templates.update { it + (template.id to template) }
    }

    override suspend fun update(template: DrinkTemplateEntity) {
        templates.update { it + (template.id to template) }
    }

    override suspend fun count(): Int = templates.value.size

    override suspend fun getAllBarcodes(): List<String> =
        templates.value.values.map { it.barcode }.filter { it.isNotEmpty() }

    override suspend fun deleteCustom() {
        templates.update { map -> map.filterValues { !it.isCustom } }
    }
}

class FakeUserProfileDao : UserProfileDao {
    private val profileFlow = MutableStateFlow<UserProfileEntity?>(null)

    override fun getProfile(): Flow<UserProfileEntity?> = profileFlow

    override suspend fun getProfileOnce(): UserProfileEntity? = profileFlow.value

    override suspend fun insertOrUpdate(profile: UserProfileEntity) {
        profileFlow.value = profile
    }
}

class FakeDayNoteDao : DayNoteDao {
    private val notes = MutableStateFlow<Map<String, DayNoteEntity>>(emptyMap())

    override suspend fun getNoteForDay(day: String): DayNoteEntity? =
        notes.value[day]

    override fun getNotesBetween(startDay: String, endDay: String): Flow<List<DayNoteEntity>> =
        notes.map { map ->
            map.values
                .filter { it.day in startDay..endDay }
                .sortedBy { it.day }
        }

    override suspend fun getAllOnce(): List<DayNoteEntity> =
        notes.value.values.sortedBy { it.day }

    override suspend fun insertOrUpdate(note: DayNoteEntity) {
        notes.update { it + (note.day to note) }
    }

    override suspend fun delete(note: DayNoteEntity) {
        notes.update { it - note.day }
    }

    override suspend fun deleteAll() {
        notes.value = emptyMap()
    }
}

class FakeSessionEventDao : SessionEventDao {
    private val vomits = MutableStateFlow<Map<String, VomitEventEntity>>(emptyMap())
    private val meals = MutableStateFlow<Map<String, MealEventEntity>>(emptyMap())
    private val breathalyzers = MutableStateFlow<Map<String, BreathalyzerReadingEntity>>(emptyMap())

    override fun getVomitEventsSince(sinceEpochSeconds: Long): Flow<List<VomitEventEntity>> =
        vomits.map { map ->
            map.values
                .filter { it.timestamp >= sinceEpochSeconds }
                .sortedBy { it.timestamp }
        }

    override suspend fun insertVomitEvent(event: VomitEventEntity) {
        vomits.update { it + (event.id to event) }
    }

    override suspend fun deleteVomitEvent(event: VomitEventEntity) {
        vomits.update { it - event.id }
    }

    override suspend fun deleteAllVomitEvents() {
        vomits.value = emptyMap()
    }

    override fun getMealEventsSince(sinceEpochSeconds: Long): Flow<List<MealEventEntity>> =
        meals.map { map ->
            map.values
                .filter { it.timestamp >= sinceEpochSeconds }
                .sortedBy { it.timestamp }
        }

    override suspend fun insertMealEvent(event: MealEventEntity) {
        meals.update { it + (event.id to event) }
    }

    override suspend fun deleteMealEvent(event: MealEventEntity) {
        meals.update { it - event.id }
    }

    override suspend fun deleteAllMealEvents() {
        meals.value = emptyMap()
    }

    override fun getMealEventsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<MealEventEntity>> =
        meals.map { map ->
            map.values
                .filter { it.timestamp in startEpochSeconds..endEpochSeconds }
                .sortedBy { it.timestamp }
        }

    override fun getBreathalyzerReadingsSince(sinceEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>> =
        breathalyzers.map { map ->
            map.values
                .filter { it.timestamp >= sinceEpochSeconds }
                .sortedBy { it.timestamp }
        }

    override fun getBreathalyzerReadingsBetween(startEpochSeconds: Long, endEpochSeconds: Long): Flow<List<BreathalyzerReadingEntity>> =
        breathalyzers.map { map ->
            map.values
                .filter { it.timestamp in startEpochSeconds..endEpochSeconds }
                .sortedBy { it.timestamp }
        }

    override suspend fun insertBreathalyzerReading(reading: BreathalyzerReadingEntity) {
        breathalyzers.update { it + (reading.id to reading) }
    }

    override suspend fun deleteBreathalyzerReading(reading: BreathalyzerReadingEntity) {
        breathalyzers.update { it - reading.id }
    }

    override suspend fun deleteAllBreathalyzerReadings() {
        breathalyzers.value = emptyMap()
    }
}

class FakeCrewMemberDao : CrewMemberDao {
    private val members = MutableStateFlow<Map<String, CrewMemberEntity>>(emptyMap())

    override fun getAll(): Flow<List<CrewMemberEntity>> =
        members.map { map -> map.values.sortedBy { it.name } }

    override suspend fun getAllOnce(): List<CrewMemberEntity> =
        members.value.values.sortedBy { it.name }

    override suspend fun countNonSelf(): Int =
        members.value.values.count { !it.isSelf }

    override suspend fun insertOrUpdate(member: CrewMemberEntity) {
        members.update { it + (member.id to member) }
    }

    override suspend fun update(member: CrewMemberEntity) {
        members.update { it + (member.id to member) }
    }

    override suspend fun applyServerUpdate(
        id: String,
        currentBac: Double,
        lastDrinkTimestamp: Long?,
        isProbationaryDriver: Boolean,
        sosActive: Boolean,
        highAlertFired: Boolean,
        isMutual: Boolean
    ) {
        members.update { map ->
            val existing = map[id] ?: return@update map
            map + (id to existing.copy(
                currentBAC = currentBac,
                lastDrinkTimestamp = lastDrinkTimestamp,
                isProbationaryDriver = isProbationaryDriver,
                sosActive = sosActive,
                highAlertFired = highAlertFired,
                isMutual = isMutual
            ))
        }
    }

    override suspend fun delete(member: CrewMemberEntity) {
        members.update { it - member.id }
    }

    override suspend fun deleteAll() {
        members.value = emptyMap()
    }
}

class FakePhotoMemoryDao : PhotoMemoryDao {
    private val photos = MutableStateFlow<Map<String, PhotoMemoryEntity>>(emptyMap())

    override fun getAll(): Flow<List<PhotoMemoryEntity>> =
        photos.map { map -> map.values.sortedByDescending { it.timestamp } }

    override suspend fun count(): Int = photos.value.size

    override suspend fun insert(memory: PhotoMemoryEntity) {
        photos.update { it + (memory.id to memory) }
    }

    override suspend fun delete(memory: PhotoMemoryEntity) {
        photos.update { it - memory.id }
    }

    override suspend fun deleteAll() {
        photos.value = emptyMap()
    }
}

class FakeCustomMixDao : CustomMixDao {
    private val mixes = MutableStateFlow<Map<String, CustomMixEntity>>(emptyMap())

    override fun getAll(): Flow<List<CustomMixEntity>> =
        mixes.map { map -> map.values.sortedByDescending { it.createdAt } }

    override suspend fun getAllOnce(): List<CustomMixEntity> =
        mixes.value.values.sortedByDescending { it.createdAt }

    override suspend fun insert(mix: CustomMixEntity) {
        mixes.update { it + (mix.id to mix) }
    }

    override suspend fun delete(mix: CustomMixEntity) {
        mixes.update { it - mix.id }
    }

    override suspend fun deleteAll() {
        mixes.value = emptyMap()
    }
}

class FakePendingSyncDao : PendingSyncDao {
    private val ops = MutableStateFlow<Map<String, PendingSyncOperationEntity>>(emptyMap())

    override suspend fun getPending(): List<PendingSyncOperationEntity> =
        ops.value.values.sortedBy { it.createdAt }

    override suspend fun insert(op: PendingSyncOperationEntity) {
        ops.update { it + (op.id to op) }
    }

    override suspend fun delete(op: PendingSyncOperationEntity) {
        ops.update { it - op.id }
    }

    override suspend fun deleteByType(operationType: String) {
        ops.update { map -> map.filterValues { it.operationType != operationType } }
    }

    override suspend fun update(op: PendingSyncOperationEntity) {
        ops.update { it + (op.id to op) }
    }

    override suspend fun count(): Int = ops.value.size
}
