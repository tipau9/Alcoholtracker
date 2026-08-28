package de.tipau.promille.di

import android.content.Context
import de.tipau.promille.bac.WaterLog
import de.tipau.promille.data.AppDatabase
import de.tipau.promille.data.WaterLogStore
import de.tipau.promille.network.SessionStore
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.refreshAdminStatus
import de.tipau.promille.network.SupabaseTransport
import de.tipau.promille.sync.BACPublisher
import de.tipau.promille.sync.ConnectivityWatcher
import de.tipau.promille.sync.FriendSyncService
import de.tipau.promille.sync.JamService
import de.tipau.promille.sync.HistorySyncService
import de.tipau.promille.sync.OfflineSyncService
import de.tipau.promille.repository.CrewRepository
import de.tipau.promille.repository.DayNoteRepository
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.repository.SessionEventRepository
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manual dependency injection container. Holds the single database instance,
 * all DAOs and all repositories. Initialised once in [PromilleApplication].
 *
 * Hilt/Koin would work too, but a manual container keeps the Gradle config
 * minimal and the compile times short for a project this size.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    // DAOs
    private val drinkDao = database.drinkDao()
    private val drinkTemplateDao = database.drinkTemplateDao()
    private val userProfileDao = database.userProfileDao()
    private val sessionEventDao = database.sessionEventDao()
    private val crewMemberDao = database.crewMemberDao()
    private val dayNoteDao = database.dayNoteDao()
    private val customMixDao = database.customMixDao()
    private val photoMemoryDao = database.photoMemoryDao()
    private val pendingSyncDao = database.pendingSyncDao()

    // Repositories
    val userProfileRepository = UserProfileRepository(userProfileDao)
    val drinkRepository = DrinkRepository(drinkDao, drinkTemplateDao)
    val drinkTemplateRepository = DrinkTemplateRepository(drinkTemplateDao)
    val sessionEventRepository = SessionEventRepository(sessionEventDao)
    val dayNoteRepository = DayNoteRepository(dayNoteDao)
    val crewRepository = CrewRepository(crewMemberDao)

    // Network
    val supabase = SupabaseService(SupabaseTransport(SessionStore(context)))

    val waterLog = WaterLog(WaterLogStore(context))

    val offlineSync = OfflineSyncService(pendingSyncDao, supabase)

    val historySync = HistorySyncService(
        context = context,
        supabase = supabase,
        drinkDao = drinkDao,
        dayNoteDao = dayNoteDao,
        customMixDao = customMixDao,
        templateDao = drinkTemplateDao,
        profiles = userProfileRepository,
        waterLog = waterLog
    )

    val bacPublisher = BACPublisher(supabase, offlineSync)

    val friendSync = FriendSyncService(context, supabase, crewMemberDao)

    /** Lives as long as the process, so it is never cancelled on purpose. */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Its polling has to outlive the jam screen, not the composition. */
    val jamService = JamService(supabase, syncScope)

    private val connectivity = ConnectivityWatcher(context, syncScope) {
        offlineSync.syncAll()
        historySync.sync()
    }

    init {
        // Crew toggles sos_active on the profile; the jam roster carries the
        // same flag, so the two must not drift apart.
        syncScope.launch {
            supabase.myProfile.collect { jamService.mySOSActive.value = it?.sosActive == true }
        }
        connectivity.start()
        syncScope.launch {
            offlineSync.refreshPendingCount()
            // The connectivity callback only fires on a change, so without this
            // an app that starts with working Wi-Fi would never sync at all.
            offlineSync.syncAll()
            // A restored session leaves myProfile empty until this runs, and the
            // Settings account section keys off it.
            runCatching { supabase.syncMyProfile() }
            // Only signIn and signUp refresh this, so a restored session would
            // leave an admin without their console until the next sign-in.
            runCatching { supabase.refreshAdminStatus() }
            historySync.sync()
        }
    }

    /**
     * First sync after sign-in: unions the account backup with whatever history
     * this device already has. Runs on the process scope on purpose, so
     * dismissing the auth sheet does not cancel a half-finished merge.
     */
    fun syncAfterSignIn() {
        syncScope.launch { historySync.sync(merge = true) }
    }

    // Services
    val achievementService = de.tipau.promille.service.AchievementService(
        drinkRepository = drinkRepository,
        userProfileRepository = userProfileRepository,
        crewRepository = crewRepository
    )
}
