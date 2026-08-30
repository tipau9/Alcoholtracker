package de.tipau.promille.repository

import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.data.UserProfileDao
import de.tipau.promille.data.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class UserProfileRepository(
    private val dao: UserProfileDao,
    /** Outlives any ViewModel, so a debounced save is never cancelled by a screen going away. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    /*
     * Port of SaveDebouncer.swift. A settings slider fires per drag pixel, and on
     * iOS that only mutates the SwiftData object in memory while the save is
     * debounced. Room has no in-memory object, so the pending value is held here
     * and overlaid on the stored row: the UI moves at once, the write lands once.
     */
    private val pending = MutableStateFlow<UserProfileEntity?>(null)

    /*
     * Written from inside the combine below, which runs once per collector on
     * that collector's dispatcher, and read from the main thread. Volatile so a
     * slider drag never reads a stale base row and writes a lost setting back.
     */
    @Volatile
    private var lastStored: UserProfileEntity? = null
    private var saveJob: Job? = null

    init {
        // Without this, updateDebounced would no-op until something collects
        // `profile`, which is not true while a settings screen is being built.
        scope.launch { lastStored = dao.getProfileOnce() }
    }

    val profile: Flow<UserProfileEntity?> = combine(dao.getProfile(), pending) { stored, p ->
        lastStored = stored
        // The write landed, so drop the overlay and let the store speak again.
        if (p != null && p == stored) pending.value = null
        p ?: stored
    }

    suspend fun getProfileOnce(): UserProfileEntity? = pending.value ?: dao.getProfileOnce()

    /**
     * Read, modify and write in one step so two edits in the same frame cannot
     * race each other into a lost setting. Call from the main thread.
     */
    fun updateDebounced(block: (UserProfileEntity) -> UserProfileEntity) {
        val next = block(pending.value ?: lastStored ?: return)
        pending.value = next
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            dao.insertOrUpdate(next)
        }
    }

    suspend fun getOrCreate(): UserProfileEntity {
        return dao.getProfileOnce() ?: UserProfileEntity(
            weight = 70.0,
            height = 175.0,
            age = 25,
            eliminationRate = 0.15
        ).also { dao.insertOrUpdate(it) }
    }

    /** Immediate write. Drops any pending debounced edit, which it supersedes. */
    suspend fun update(profile: UserProfileEntity) {
        saveJob?.cancel()
        pending.value = null
        dao.insertOrUpdate(profile)
    }

    companion object {

        /** Same 300 ms window SaveDebouncer.swift uses. */
        private const val SAVE_DEBOUNCE_MS = 300L

        /** Converts a Room entity to the :bac engine's Profile type. */
        fun toProfile(entity: UserProfileEntity): Profile = Profile(
            weightKg = entity.weight,
            heightCm = entity.height,
            age = entity.age,
            gender = Gender.from(entity.genderRaw),
            eliminationRate = entity.eliminationRate,
            toleranceMode = entity.toleranceMode,
            isProbationaryDriver = entity.isProbationaryDriver,
            conservativeSafety = entity.conservativeSafety,
            conservativeEverywhere = entity.conservativeEverywhere,
            defaultStomachStatus = StomachStatus.from(entity.stomachStatusRaw),
            warningThreshold = entity.warningThreshold,
            tipsyThreshold = entity.tipsyThreshold,
            drunkThreshold = entity.drunkThreshold,
            carefulThreshold = entity.carefulThreshold,
            dangerThreshold = entity.dangerThreshold
        )
    }
}
