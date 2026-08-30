package de.tipau.promille.sync

import de.tipau.promille.data.PendingSyncDao
import de.tipau.promille.data.PendingSyncOperationEntity
import de.tipau.promille.data.newId
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.leaveJam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 * Port of OfflineSyncQueue.swift. Peak usage is a bar with bad signal, so a write
 * that fails is queued in Room and replayed instead of being lost.
 */
class OfflineSyncService(
    private val dao: PendingSyncDao,
    private val supabase: SupabaseService,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    @Serializable
    private data class PublishBACPayload(
        val bac: Double,
        val recordedAt: Long,
        val eliminationRate: Double
    )

    @Serializable
    private data class LeaveJamPayload(val jamID: String)

    @Serializable
    private data class UpdateSharingPayload(val sharing: Boolean)

    /**
     * Only the newest permille matters, so an older queued value is obsolete and
     * is dropped rather than replayed on top of the newer one.
     */
    suspend fun enqueueBACPublish(bac: Double, eliminationRate: Double) {
        dao.deleteByType(TYPE_PUBLISH_BAC)
        enqueue(
            TYPE_PUBLISH_BAC,
            json.encodeToString(
                PublishBACPayload.serializer(),
                PublishBACPayload(bac, nowSeconds(), eliminationRate)
            )
        )
    }

    suspend fun enqueueLeaveJam(jamID: String) =
        enqueue(TYPE_LEAVE_JAM, json.encodeToString(LeaveJamPayload.serializer(), LeaveJamPayload(jamID)))

    suspend fun enqueueUpdateSharing(sharing: Boolean) = enqueue(
        TYPE_UPDATE_SHARING,
        json.encodeToString(UpdateSharingPayload.serializer(), UpdateSharingPayload(sharing))
    )

    private suspend fun enqueue(type: String, payload: String) {
        dao.insert(
            PendingSyncOperationEntity(
                id = newId(),
                operationType = type,
                payload = payload,
                createdAt = nowSeconds()
            )
        )
        refreshPendingCount()
    }

    suspend fun refreshPendingCount() {
        _pendingCount.value = dao.count()
    }

    suspend fun syncAll() {
        if (_isSyncing.value || !supabase.isSignedIn.value) return
        _isSyncing.value = true
        try {
            for (op in dao.getPending()) {
                try {
                    execute(op)
                    dao.delete(op)
                } catch (e: SerializationException) {
                    // A corrupt payload can never succeed. Dropping it beats
                    // blocking the whole queue on it forever.
                    dao.delete(op)
                } catch (e: Exception) {
                    val next = op.copy(retryCount = op.retryCount + 1)
                    if (next.retryCount > MAX_RETRIES) dao.delete(op) else dao.update(next)
                    // Transient (network or server): the remaining operations would
                    // fail too. Stop here so one outage does not burn a retry on the
                    // whole queue; the network callback retriggers syncAll.
                    break
                }
            }
            refreshPendingCount()
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun execute(op: PendingSyncOperationEntity) {
        when (op.operationType) {
            TYPE_PUBLISH_BAC -> {
                val p = json.decodeFromString(PublishBACPayload.serializer(), op.payload)
                // The queued value is a permille reading from some minutes ago. It
                // has been eliminating ever since, so publishing it raw would show
                // friends a stale, too high number.
                val elapsedHours = max(0.0, (nowSeconds() - p.recordedAt) / 3600.0)
                val rate = if (p.eliminationRate > 0) p.eliminationRate else DEFAULT_RATE
                supabase.publishBAC(max(0.0, p.bac - rate * elapsedHours))
            }
            TYPE_LEAVE_JAM ->
                supabase.leaveJam(json.decodeFromString(LeaveJamPayload.serializer(), op.payload).jamID)
            TYPE_UPDATE_SHARING ->
                supabase.updateSharing(
                    json.decodeFromString(UpdateSharingPayload.serializer(), op.payload).sharing
                )
        }
    }

    private companion object {
        const val TYPE_PUBLISH_BAC = "publishBAC"
        const val TYPE_LEAVE_JAM = "leaveJam"
        const val TYPE_UPDATE_SHARING = "updateSharing"
        const val MAX_RETRIES = 5
        const val DEFAULT_RATE = 0.15
    }
}
