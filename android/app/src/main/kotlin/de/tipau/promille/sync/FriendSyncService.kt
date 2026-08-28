package de.tipau.promille.sync

import android.content.Context
import de.tipau.promille.bac.CrewMath
import de.tipau.promille.bac.permilleString
import de.tipau.promille.data.CrewMemberDao
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.network.FriendProfile
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.fetchFriendsBAC
import de.tipau.promille.service.NotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a single friend update wants the app to tell the user about. */
enum class FriendAlert { SOS, HIGH_BAC }

/**
 * Applies one server row to one stored friend. Pure so the alert edges can be
 * tested without a database or a notification manager: both are edge triggered
 * and a mistake means either a missed SOS or a banner every 60 seconds.
 */
fun applyFriendUpdate(
    member: CrewMemberEntity,
    remote: FriendProfile,
    dangerLimit: Double,
    nowEpochSeconds: Long
): Pair<CrewMemberEntity, List<FriendAlert>> {
    val alerts = mutableListOf<FriendAlert>()
    val updatedAt = remote.bacUpdatedAt?.toLong()

    // Rising edge only: the friend has to have been quiet before.
    if (remote.sosActive && !member.sosActive) alerts += FriendAlert.SOS

    val estimated = CrewMath.estimatedBac(remote.currentBac ?: 0.0, updatedAt, nowEpochSeconds)
    var highFired = member.highAlertFired
    if (member.alertWhenHigh) {
        if (estimated >= dangerLimit && !highFired) {
            highFired = true
            alerts += FriendAlert.HIGH_BAC
        } else if (estimated < dangerLimit && highFired) {
            // Reset, so the next episode can alert again.
            highFired = false
        }
    }

    val next = member.copy(
        currentBAC = remote.currentBac ?: 0.0,
        lastDrinkTimestamp = updatedAt,
        isProbationaryDriver = remote.isProbationary,
        sosActive = remote.sosActive,
        highAlertFired = highFired
    )
    return next to alerts
}

/**
 * Port of CrewView.syncFriendsBAC. Polls every friend's published permille and
 * writes it back into Room, because the crew list is the only place a friend's
 * value ever comes from: there is no push channel on either platform.
 */
class FriendSyncService(
    private val context: Context,
    private val supabase: SupabaseService,
    private val dao: CrewMemberDao,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    suspend fun sync(dangerLimit: Double) {
        if (_isSyncing.value || !supabase.isSignedIn.value) return
        val members = dao.getAllOnce()
        val codes = members.mapNotNull { it.friendCode }.filter { it.isNotEmpty() }
        if (codes.isEmpty()) return

        _isSyncing.value = true
        try {
            val fresh = runCatching { supabase.fetchFriendsBAC(codes) }.getOrNull() ?: return
            val now = nowSeconds()
            for (remote in fresh) {
                val member = members.firstOrNull {
                    it.friendCode?.equals(remote.friendCode, ignoreCase = true) == true
                } ?: continue
                val (next, alerts) = applyFriendUpdate(member, remote, dangerLimit, now)
                dao.applyServerUpdate(
                    id = next.id,
                    currentBac = next.currentBAC,
                    lastDrinkTimestamp = next.lastDrinkTimestamp,
                    isProbationaryDriver = next.isProbationaryDriver,
                    sosActive = next.sosActive,
                    highAlertFired = next.highAlertFired
                )
                for (alert in alerts) notify(alert, next, now)
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private fun notify(alert: FriendAlert, member: CrewMemberEntity, now: Long) {
        when (alert) {
            FriendAlert.SOS -> NotificationService.notifyNow(
                context,
                id = "promille.friend.sos.${member.id}",
                title = "SOS von ${member.name}",
                body = "${member.name} braucht Hilfe. Tippe, um das Profil zu öffnen."
            )
            FriendAlert.HIGH_BAC -> {
                val estimated = CrewMath.estimatedBac(
                    member.currentBAC, member.lastDrinkTimestamp, now
                )
                NotificationService.notifyNow(
                    context,
                    id = "promille.friend.high.${member.id}",
                    title = "${member.name} trinkt viel",
                    body = "${member.name} liegt rechnerisch bei ${estimated.permilleString()}. Vielleicht mal nachfragen."
                )
            }
        }
    }
}
