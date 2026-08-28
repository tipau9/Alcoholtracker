package de.tipau.promille.sync

import android.content.Context
import de.tipau.promille.bac.WaterLog
import de.tipau.promille.bac.syncPlan
import de.tipau.promille.data.CustomMixDao
import de.tipau.promille.data.CustomMixEntity
import de.tipau.promille.data.DayNoteDao
import de.tipau.promille.data.DayNoteEntity
import de.tipau.promille.data.DrinkDao
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateDao
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.network.AccountBackup
import de.tipau.promille.network.BlobDates
import de.tipau.promille.network.MixBackup
import de.tipau.promille.network.ProfileBackup
import de.tipau.promille.network.RemoteDayNote
import de.tipau.promille.network.RemoteDrink
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.TemplateBackup
import de.tipau.promille.network.Timestamps
import de.tipau.promille.network.blobJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Port of HistorySyncService.swift.
 *
 * The model is a single account backup and restore, not a live multi device
 * merge: the local database is the source of truth and an ongoing sync deletes
 * server rows that are missing locally, so the most recently synced device wins
 * for deletions. Two situations never delete, because both look identical to
 * "the user deleted everything": a fresh device (local empty, backup not) and
 * the first sync after signing in. That decision lives in [syncPlan] in :bac and
 * is unit tested there.
 */
class HistorySyncService(
    context: Context,
    private val supabase: SupabaseService,
    private val drinkDao: DrinkDao,
    private val dayNoteDao: DayNoteDao,
    private val customMixDao: CustomMixDao,
    private val templateDao: DrinkTemplateDao,
    private val profiles: UserProfileRepository,
    private val waterLog: WaterLog
) {

    private val prefs = context.getSharedPreferences("history_sync", Context.MODE_PRIVATE)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncEpochSeconds = MutableStateFlow<Long?>(null)
    val lastSyncEpochSeconds: StateFlow<Long?> = _lastSyncEpochSeconds.asStateFlow()

    /**
     * @param merge unions local and remote instead of letting local deletions win.
     *   Passed true right after signing in; the first sync on a device forces it
     *   anyway.
     */
    suspend fun sync(merge: Boolean = false) {
        if (_isSyncing.value) return
        val userId = supabase.userId ?: return
        if (!supabase.isConfigured) return

        _isSyncing.value = true
        try {
            val lastUser = prefs.getString(KEY_LAST_USER, null)
            val accountSwitched = lastUser != null && lastUser != userId
            if (accountSwitched) {
                purgeLocalUserData()
                prefs.edit().remove(KEY_DID_INITIAL_SYNC).apply()
            }
            // Read AFTER the purge cleared the flag, so an account switch always merges.
            val firstSync = !prefs.getBoolean(KEY_DID_INITIAL_SYNC, false)
            val useMerge = merge || firstSync

            try {
                syncDrinks(useMerge)
                syncNotes(useMerge)
                syncSettings(forceProfileRestore = accountSwitched)
                // Only after all three succeeded. Setting it on a partial failure
                // would make the retry authoritative and delete the server rows
                // the failed half never uploaded.
                prefs.edit()
                    .putBoolean(KEY_DID_INITIAL_SYNC, true)
                    .putString(KEY_LAST_USER, userId)
                    .apply()
                _lastSyncEpochSeconds.value = System.currentTimeMillis() / 1000
            } catch (e: Exception) {
                // Transient (network or server). Retried on the next call.
            }
        } finally {
            _isSyncing.value = false
        }
    }

    // MARK: Drinks

    private suspend fun syncDrinks(merge: Boolean) {
        val local = drinkDao.getAllDrinksSortedOnce()
        val before = fingerprint(local.size, local.maxOfOrNull { it.timestampEpochSeconds })
        if (!merge && before == prefs.getString(KEY_DRINKS_PRINT, null)) return

        val remote = supabase.fetchDrinkHistory()
        if (local.isNotEmpty()) supabase.uploadDrinkHistory(local.map { it.toRow() })

        val byId = remote.associateBy { it.id.lowercase() }
        val plan = syncPlan(local.map { it.id }.toSet(), byId.keys.toList(), merge)
        for (id in plan.toImport) byId[id]?.let { drinkDao.insert(it.toEntity()) }
        if (plan.toDeleteRemotely.isNotEmpty()) supabase.deleteDrinkHistory(plan.toDeleteRemotely)

        // Re-fingerprint the FINAL state: importing just grew it.
        val after = drinkDao.getAllDrinksSortedOnce()
        prefs.edit()
            .putString(KEY_DRINKS_PRINT, fingerprint(after.size, after.maxOfOrNull { it.timestampEpochSeconds }))
            .apply()
    }

    // MARK: Day notes

    private suspend fun syncNotes(merge: Boolean) {
        val local = dayNoteDao.getAllOnce()
        val before = notesFingerprint(local)
        if (!merge && before == prefs.getString(KEY_NOTES_PRINT, null)) return

        val remote = supabase.fetchDayNotes()
        if (local.isNotEmpty()) supabase.uploadDayNotes(local.map { it.toRow() })

        val byDay = remote.associateBy { it.dayStart }
        val plan = syncPlan(local.map { it.day }.toSet(), byDay.keys.toList(), merge)
        for (day in plan.toImport) byDay[day]?.let { dayNoteDao.insertOrUpdate(it.toEntity()) }
        if (plan.toDeleteRemotely.isNotEmpty()) supabase.deleteDayNotes(plan.toDeleteRemotely)

        prefs.edit().putString(KEY_NOTES_PRINT, notesFingerprint(dayNoteDao.getAllOnce())).apply()
    }

    // MARK: Settings blob

    private suspend fun syncSettings(forceProfileRestore: Boolean) {
        val server = supabase.fetchUserBackup()?.let {
            runCatching { blobJson.decodeFromJsonElement<AccountBackup>(it) }.getOrNull()
        }

        val localProfile = profiles.getProfileOnce()
        val serverProfile = server?.profile
        if (serverProfile != null && serverProfile.hasCompletedOnboarding &&
            (forceProfileRestore || localProfile?.hasCompletedOnboarding != true)
        ) {
            profiles.update(serverProfile.applyTo(localProfile))
        }

        // Mixes and custom drinks are an additive union, so no creation is lost.
        importMixes(server?.customMixes)
        importTemplates(server?.customDrinks)
        server?.waterLog?.let { waterLog.merge(it) }

        // Always push the (possibly augmented) local state back.
        val document = blobJson.encodeToJsonElement(buildBackup()).jsonObject
        supabase.uploadUserBackup(document)
    }

    private suspend fun buildBackup(): AccountBackup = AccountBackup(
        profile = profiles.getProfileOnce()?.toBackup(),
        waterLog = waterLog.allEntries,
        customMixes = customMixDao.getAllOnce().map { it.toBackup() },
        customDrinks = templateDao.getAll().filter { it.isCustom }.map { it.toBackup() }
    )

    private suspend fun importMixes(remote: List<MixBackup>?) {
        if (remote.isNullOrEmpty()) return
        val existing = customMixDao.getAllOnce().map { it.id }.toSet()
        for (m in remote) {
            val id = m.id.lowercase()
            if (id in existing) continue
            customMixDao.insert(
                CustomMixEntity(
                    id = id,
                    name = m.name,
                    ingredientsJson = m.ingredients.toString(),
                    createdAt = BlobDates.parse(m.createdAt) ?: (System.currentTimeMillis() / 1000)
                )
            )
        }
    }

    private suspend fun importTemplates(remote: List<TemplateBackup>?) {
        if (remote.isNullOrEmpty()) return
        val existing = templateDao.getAll().map { it.id }.toSet()
        for (t in remote) {
            val id = t.id.lowercase()
            if (id in existing) continue
            templateDao.insertOrReplace(
                DrinkTemplateEntity(
                    id = id,
                    name = t.name,
                    categoryRaw = t.categoryRaw,
                    volume = t.volume,
                    abv = t.abv,
                    calories = t.calories,
                    iconName = t.iconName,
                    isCustom = true,
                    usageCount = t.usageCount,
                    barcode = t.barcode
                )
            )
        }
    }

    // MARK: Account switch

    /**
     * Everything the previous account owned. The UserProfile row deliberately
     * stays: body data is what the BAC engine needs, and wiping it would drop the
     * new user into a broken app before the restore has run.
     */
    private suspend fun purgeLocalUserData() {
        drinkDao.deleteAll()
        dayNoteDao.deleteAll()
        customMixDao.deleteAll()
        templateDao.deleteCustom()
        waterLog.clear()
        prefs.edit().remove(KEY_DRINKS_PRINT).remove(KEY_NOTES_PRINT).apply()
    }

    private companion object {
        const val KEY_DID_INITIAL_SYNC = "history.didInitialSync"
        const val KEY_LAST_USER = "history.lastSyncedUserId"
        const val KEY_DRINKS_PRINT = "history.drinksFingerprint"
        const val KEY_NOTES_PRINT = "history.notesFingerprint"

        /*
         * A cheap "did anything change" stamp that lets an unchanged table skip the
         * network entirely. Device local only, so the epoch it uses does not have
         * to match the iOS one.
         */
        fun fingerprint(count: Int, maxEpochSeconds: Long?): String = "$count:${maxEpochSeconds ?: 0}"

        fun notesFingerprint(notes: List<DayNoteEntity>): String {
            val maxDay = notes.maxOfOrNull { it.day } ?: ""
            val moodSum = notes.sumOf { it.moodRaw }
            return "${notes.size}:$maxDay:$moodSum"
        }
    }
}

// MARK: - Row mapping

private fun DrinkEntity.toRow(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("volume", volume)
    put("abv", abv)
    put("calories", calories)
    put("icon_name", iconName)
    put("category", categoryRaw)
    put("mixer_volume", mixerVolume)
    put("mixer_water_content", mixerWaterContent)
    put("drink_duration_minutes", drinkDurationMinutes)
    put("consumed_at", Timestamps.format(timestampEpochSeconds.toDouble()))
    templateID?.let { put("template_id", JsonPrimitive(it)) }
}

private fun RemoteDrink.toEntity(): DrinkEntity = DrinkEntity(
    id = id.lowercase(),
    templateID = templateID?.lowercase(),
    name = name,
    volume = volume,
    abv = abv,
    calories = calories,
    iconName = iconName,
    timestampEpochSeconds = consumedAtEpochSeconds,
    categoryRaw = category,
    mixerVolume = mixerVolume,
    mixerWaterContent = mixerWaterContent,
    drinkDurationMinutes = drinkDurationMinutes
)

private fun DayNoteEntity.toRow(): JsonObject = buildJsonObject {
    put("day_start", day)
    put("text", text)
    put("mood", moodRaw)
}

private fun RemoteDayNote.toEntity(): DayNoteEntity =
    DayNoteEntity(day = dayStart, text = text, moodRaw = mood)

private fun CustomMixEntity.toBackup(): MixBackup = MixBackup(
    id = id,
    name = name,
    ingredients = blobJson.parseToJsonElement(ingredientsJson) as? JsonArray ?: JsonArray(emptyList()),
    createdAt = BlobDates.format(createdAt)
)

private fun DrinkTemplateEntity.toBackup(): TemplateBackup = TemplateBackup(
    id = id,
    name = name,
    categoryRaw = categoryRaw,
    volume = volume,
    abv = abv,
    calories = calories,
    iconName = iconName,
    usageCount = usageCount,
    barcode = barcode
)

private fun UserProfileEntity.toBackup(): ProfileBackup = ProfileBackup(
    weight = weight,
    height = height,
    age = age,
    birthDate = BlobDates.format(birthDate),
    genderRaw = genderRaw,
    eliminationRate = eliminationRate,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    homeStyleRaw = homeStyleRaw,
    activeWidgetsRaw = activeWidgetsRaw,
    largeText = largeText,
    highContrast = highContrast,
    reducedMotion = reducedMotion,
    toleranceMode = toleranceMode,
    warningThreshold = warningThreshold,
    stomachStatusRaw = stomachStatusRaw,
    statusSkinRaw = statusSkinRaw,
    tipsyThreshold = tipsyThreshold,
    drunkThreshold = drunkThreshold,
    carefulThreshold = carefulThreshold,
    dangerThreshold = dangerThreshold,
    accentColorHex = accentColorHex,
    sipVolumeML = sipVolumeML,
    activeMedicationsRaw = activeMedicationsRaw,
    healthKitEnabled = healthKitEnabled,
    weeklyDrinkLimit = weeklyDrinkLimit,
    soberDaysGoal = soberDaysGoal,
    isProbationaryDriver = isProbationaryDriver,
    drunkModeAuto = drunkModeAuto,
    onboardingStepsCompleted = onboardingStepsCompletedRaw.split(",").filter { it.isNotBlank() },
    hasCompletedOnboarding = hasCompletedOnboarding
)

/**
 * The three Android only profile columns are not in the iOS backup, so they are
 * carried over from the existing row instead of being reset by a restore.
 */
private fun ProfileBackup.applyTo(existing: UserProfileEntity?): UserProfileEntity {
    val base = existing ?: UserProfileEntity(
        weight = weight, height = height, age = age, eliminationRate = eliminationRate
    )
    return base.copy(
        weight = weight,
        height = height,
        age = age,
        birthDate = BlobDates.parse(birthDate) ?: base.birthDate,
        genderRaw = genderRaw,
        eliminationRate = eliminationRate,
        emergencyContactName = emergencyContactName,
        emergencyContactPhone = emergencyContactPhone,
        homeStyleRaw = homeStyleRaw,
        activeWidgetsRaw = activeWidgetsRaw,
        largeText = largeText,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
        toleranceMode = toleranceMode,
        warningThreshold = warningThreshold,
        stomachStatusRaw = stomachStatusRaw,
        statusSkinRaw = statusSkinRaw,
        tipsyThreshold = tipsyThreshold,
        drunkThreshold = drunkThreshold,
        carefulThreshold = carefulThreshold,
        dangerThreshold = dangerThreshold,
        accentColorHex = accentColorHex,
        sipVolumeML = sipVolumeML,
        activeMedicationsRaw = activeMedicationsRaw,
        healthKitEnabled = healthKitEnabled,
        weeklyDrinkLimit = weeklyDrinkLimit,
        soberDaysGoal = soberDaysGoal,
        isProbationaryDriver = isProbationaryDriver,
        drunkModeAuto = drunkModeAuto,
        onboardingStepsCompletedRaw = onboardingStepsCompleted.joinToString(","),
        hasCompletedOnboarding = hasCompletedOnboarding
    )
}
