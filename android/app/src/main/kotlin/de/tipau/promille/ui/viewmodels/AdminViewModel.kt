package de.tipau.promille.ui.viewmodels
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.network.AdminAuditEntry
import de.tipau.promille.network.AdminBlockedVoter
import de.tipau.promille.network.AdminFeatureFlag
import de.tipau.promille.network.AdminMetric
import de.tipau.promille.network.AdminQueueItem
import de.tipau.promille.network.AdminReport
import de.tipau.promille.network.AdminUserRole
import de.tipau.promille.network.SupabaseService
import kotlinx.serialization.json.JsonNull
import de.tipau.promille.network.fetchAdminAuditLog
import de.tipau.promille.network.fetchAdminBlockedVoters
import de.tipau.promille.network.fetchAdminContent
import de.tipau.promille.network.fetchAdminFeatureFlags
import de.tipau.promille.network.fetchAdminMetrics
import de.tipau.promille.network.fetchAdminQueue
import de.tipau.promille.network.fetchAdminReports
import de.tipau.promille.network.fetchAdminUsers
import de.tipau.promille.network.resolveAdminReport
import de.tipau.promille.network.setAdminFeatureFlag
import de.tipau.promille.network.setAdminModerationStatus
import de.tipau.promille.network.setAdminUserRole
import de.tipau.promille.network.setAdminVoterBlock
import de.tipau.promille.network.updateAdminDrink
import de.tipau.promille.network.updateAdminMix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class AdminSection(val title: String) {
    MODERATION("Queue"),
    CATALOG("Katalog"),
    REPORTS("Reports"),
    FLAGS("Flags"),
    SECURITY("Security"),
    AUDIT("Audit"),
    DEBUG("Debug")
}

/**
 * Backs the admin console. Every list here comes from a SECURITY DEFINER RPC
 * that checks the caller's role server side, so a non-admin gets an error rather
 * than data: the client side gate is convenience, not the actual protection.
 */
class AdminViewModel(private val supabase: SupabaseService) : ViewModel() {

    private val _metrics = MutableStateFlow<List<AdminMetric>>(emptyList())
    val metrics: StateFlow<List<AdminMetric>> = _metrics.asStateFlow()

    private val _queue = MutableStateFlow<List<AdminQueueItem>>(emptyList())
    val queue: StateFlow<List<AdminQueueItem>> = _queue.asStateFlow()

    private val _catalog = MutableStateFlow<List<AdminQueueItem>>(emptyList())
    val catalog: StateFlow<List<AdminQueueItem>> = _catalog.asStateFlow()

    private val _reports = MutableStateFlow<List<AdminReport>>(emptyList())
    val reports: StateFlow<List<AdminReport>> = _reports.asStateFlow()

    private val _flags = MutableStateFlow<List<AdminFeatureFlag>>(emptyList())
    val flags: StateFlow<List<AdminFeatureFlag>> = _flags.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<AdminUserRole>>(emptyList())
    val adminUsers: StateFlow<List<AdminUserRole>> = _adminUsers.asStateFlow()

    private val _blockedVoters = MutableStateFlow<List<AdminBlockedVoter>>(emptyList())
    val blockedVoters: StateFlow<List<AdminBlockedVoter>> = _blockedVoters.asStateFlow()

    private val _audit = MutableStateFlow<List<AdminAuditEntry>>(emptyList())
    val audit: StateFlow<List<AdminAuditEntry>> = _audit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val catalogSearch = MutableStateFlow("")

    /** Ids ticked in the moderation queue for a bulk approve, reject or block. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelection(id: String) {
        _selection.value = _selection.value.let { if (id in it) it - id else it + id }
    }

    fun toggleSelectAll() {
        val all = _queue.value.map { it.id }.toSet()
        _selection.value = if (_selection.value.size == all.size) emptySet() else all
    }

    /** Returns the job so a caller, or a test, can wait for the pass to finish. */
    fun reloadAll(): Job? {
        if (!supabase.isSignedIn.value) return null
        _isLoading.value = true
        return viewModelScope.launch {
            // Isolated per call on purpose. One RPC that is not deployed, or one
            // transient 500, must not blank the other six lists: seven empty
            // sections read as "nothing to moderate", not as "the load failed".
            var firstFailure: String? = null
            suspend fun <T> load(target: MutableStateFlow<List<T>>, fetch: suspend () -> List<T>) {
                try {
                    target.value = fetch()
                } catch (e: Exception) {
                    if (firstFailure == null) {
                        firstFailure = e.message ?: "Adminabfrage fehlgeschlagen."
                    }
                }
            }

            load(_metrics) { supabase.fetchAdminMetrics() }
            load(_queue) { supabase.fetchAdminQueue() }
            load(_reports) { supabase.fetchAdminReports() }
            load(_flags) { supabase.fetchAdminFeatureFlags() }
            load(_adminUsers) { supabase.fetchAdminUsers() }
            load(_blockedVoters) { supabase.fetchAdminBlockedVoters() }
            load(_audit) { supabase.fetchAdminAuditLog() }
            load(_catalog) { supabase.fetchAdminContent(search = catalogSearch.value) }

            _error.value = firstFailure
            _isLoading.value = false
        }
    }

    fun searchCatalog(term: String) {
        catalogSearch.value = term
        viewModelScope.launch {
            runCatching { supabase.fetchAdminContent(search = term) }
                .onSuccess { _catalog.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    // MARK: Moderation

    fun setModerationStatus(item: AdminQueueItem, status: String) = act {
        supabase.setAdminModerationStatus(
            itemType = item.itemType,
            id = item.id,
            status = status,
            reason = if (status == "approved") "Approved in app" else "Rejected in app"
        )
        reloadModeration()
    }

    fun bulkSetModerationStatus(status: String) = act {
        val ids = _selection.value
        // Iterating rather than one call: the RPC takes a single item, and a
        // partial failure should still leave the successful ones decided.
        for (item in _queue.value.filter { it.id in ids }) {
            runCatching {
                supabase.setAdminModerationStatus(
                    itemType = item.itemType,
                    id = item.id,
                    status = status,
                    reason = "Bulk $status in app"
                )
            }
        }
        _selection.value = emptySet()
        reloadModeration()
    }

    fun blockVoter(voter: String, reason: String = "Blocked from moderation queue") = act {
        supabase.setAdminVoterBlock(voter, blocked = true, reason = reason)
        _blockedVoters.value = supabase.fetchAdminBlockedVoters()
        reloadModeration()
    }

    fun unblockVoter(voter: String) = act {
        supabase.setAdminVoterBlock(voter, blocked = false, reason = "Unblocked in app")
        _blockedVoters.value = supabase.fetchAdminBlockedVoters()
    }

    private suspend fun reloadModeration() {
        _queue.value = supabase.fetchAdminQueue()
        _metrics.value = supabase.fetchAdminMetrics()
    }

    // MARK: Reports, flags, roles

    fun resolveReport(id: String, status: String) = act {
        supabase.resolveAdminReport(id, status)
        _reports.value = supabase.fetchAdminReports()
        _metrics.value = supabase.fetchAdminMetrics()
    }

    fun setFlag(flag: AdminFeatureFlag, enabled: Boolean) = act {
        // The RPC overwrites the whole row, so the existing payload has to be
        // sent back verbatim or flipping the switch would erase it. A null
        // payload goes back as blank, which the API turns into an empty object.
        supabase.setAdminFeatureFlag(
            key = flag.key,
            enabled = enabled,
            isPublic = flag.isPublic,
            value = if (flag.value is JsonNull) "" else flag.value.toString(),
            description = flag.description
        )
        _flags.value = supabase.fetchAdminFeatureFlags()
    }

    fun setUserRole(userID: String, role: String) = act {
        supabase.setAdminUserRole(userID, role)
        _adminUsers.value = supabase.fetchAdminUsers()
    }

    // The 5 admin editor sheets (AdminEditors.kt) need to keep their dialog open
    // and show an inline error on failure, only dismissing on success - the
    // fire-and-forget act() pattern above can't do that (it swallows the
    // exception into the top-level error banner and returns immediately). These
    // are genuine suspend functions the dialog awaits directly.

    /** Full create/edit path - unlike [setFlag] this can also change the key,
     * value and description, not just flip the switch. */
    suspend fun saveFlag(key: String, enabled: Boolean, isPublic: Boolean, value: String, description: String) {
        supabase.setAdminFeatureFlag(key, enabled, isPublic, value, description)
        _flags.value = supabase.fetchAdminFeatureFlags()
    }

    suspend fun updateDrink(id: String, name: String, category: String, volume: Double, abv: Double, calories: Int, iconName: String?) {
        supabase.updateAdminDrink(id, name, category, volume, abv, calories, iconName)
        reloadModeration()
        _catalog.value = supabase.fetchAdminContent(search = catalogSearch.value)
    }

    suspend fun updateMix(id: String, name: String, ingredients: kotlinx.serialization.json.JsonElement, totalVolume: Double, totalABV: Double, calories: Int) {
        supabase.updateAdminMix(id, name, ingredients, totalVolume, totalABV, calories)
        reloadModeration()
        _catalog.value = supabase.fetchAdminContent(search = catalogSearch.value)
    }

    suspend fun setRole(userID: String, role: String) {
        supabase.setAdminUserRole(userID, role)
        _adminUsers.value = supabase.fetchAdminUsers()
    }

    suspend fun blockVoterAwait(voter: String, reason: String) {
        supabase.setAdminVoterBlock(voter, blocked = true, reason = reason)
        _blockedVoters.value = supabase.fetchAdminBlockedVoters()
        reloadModeration()
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Aktion fehlgeschlagen."
            }
            _isLoading.value = false
        }
    }

    companion object {
        /** The metric keys the RPC returns, in the order iOS shows them. */
        fun metricLabel(key: String): String = when (key) {
            "pending_drinks" -> "Offene Drinks"
            "pending_mixes" -> "Offene Mixes"
            "open_reports" -> "Reports"
            "approved_drinks" -> "Verifizierte Drinks"
            "approved_mixes" -> "Verifizierte Mixes"
            "blocked_voters" -> "Blockierte Voter"
            else -> key
        }
    }
}
