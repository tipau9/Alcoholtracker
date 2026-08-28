package de.tipau.promille.sync

import de.tipau.promille.network.SupabaseService
import kotlin.math.abs

/**
 * Port of HomeView.publishBACThrottled. Publishes the permille to the backend at
 * most once every 2 minutes, plus immediately on a >= 0.05 move or a sober
 * transition, so friends see meaningful changes without a PATCH on every ticker
 * tick. A failed call is queued through the offline sync service instead of being
 * silently dropped: this is the only producer the queue has.
 */
class BACPublisher(
    private val supabase: SupabaseService,
    private val offlineSync: OfflineSyncService,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    private var lastPublishedBAC = -1.0
    private var lastPublishAt = Long.MIN_VALUE / 2

    /** True when the value was due, whether the network call itself succeeded. */
    suspend fun publish(bac: Double, eliminationRate: Double): Boolean {
        if (!supabase.isSignedIn.value) return false
        val crossedSober = (bac <= 0.001) != (lastPublishedBAC <= 0.001)
        val movedEnough = abs(bac - lastPublishedBAC) >= 0.05
        val intervalDue = now() - lastPublishAt >= PUBLISH_INTERVAL_SECONDS
        if (!crossedSober && !movedEnough && !intervalDue) return false

        lastPublishedBAC = bac
        lastPublishAt = now()
        try {
            supabase.publishBAC(bac)
        } catch (e: Exception) {
            offlineSync.enqueueBACPublish(bac, eliminationRate)
        }
        return true
    }

    private companion object {
        const val PUBLISH_INTERVAL_SECONDS = 120L
    }
}
