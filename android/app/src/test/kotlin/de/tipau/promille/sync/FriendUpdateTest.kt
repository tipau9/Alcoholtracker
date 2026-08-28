package de.tipau.promille.sync

import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.network.FriendProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Both alerts are edge triggered and the sync runs once a minute. Getting an
 * edge wrong is either a missed SOS or the same banner sixty times an hour.
 */
class FriendUpdateTest {

    private val now = 1_700_000_000L

    private fun member(
        sos: Boolean = false,
        alertWhenHigh: Boolean = true,
        highFired: Boolean = false
    ) = CrewMemberEntity(
        id = "m1", name = "Anna", avatarInitial = "A", joinedAt = 0,
        friendCode = "ABC123", sosActive = sos,
        alertWhenHigh = alertWhenHigh, highAlertFired = highFired
    )

    private fun remote(
        bac: Double = 0.0,
        sos: Boolean = false,
        updatedAt: Long? = null
    ) = FriendProfile(
        id = "u1", displayName = "Anna", friendCode = "ABC123",
        currentBac = bac,
        bacUpdatedAtRaw = updatedAt?.let { de.tipau.promille.network.Timestamps.format(it.toDouble()) },
        sosActive = sos
    )

    @Test
    fun `SOS fires on the rising edge and stays quiet while it is still on`() {
        val (first, alerts) = applyFriendUpdate(member(), remote(sos = true), 1.5, now)
        assertEquals(listOf(FriendAlert.SOS), alerts)
        assertTrue(first.sosActive)

        val (_, again) = applyFriendUpdate(first, remote(sos = true), 1.5, now)
        assertTrue(again.isEmpty(), "a standing SOS must not re-notify every minute")
    }

    @Test
    fun `a high value alerts once, then only again after dropping below`() {
        val (high, alerts) = applyFriendUpdate(
            member(), remote(bac = 1.8, updatedAt = now), 1.5, now
        )
        assertEquals(listOf(FriendAlert.HIGH_BAC), alerts)
        assertTrue(high.highAlertFired)

        val (still, none) = applyFriendUpdate(high, remote(bac = 1.9, updatedAt = now), 1.5, now)
        assertTrue(none.isEmpty(), "still high is not a new episode")

        val (sober, reset) = applyFriendUpdate(still, remote(bac = 0.2, updatedAt = now), 1.5, now)
        assertTrue(reset.isEmpty())
        assertFalse(sober.highAlertFired, "the guard resets so the next episode can alert")

        val (_, second) = applyFriendUpdate(sober, remote(bac = 1.8, updatedAt = now), 1.5, now)
        assertEquals(listOf(FriendAlert.HIGH_BAC), second)
    }

    @Test
    fun `a friend switched off never alerts on their permille`() {
        val (_, alerts) = applyFriendUpdate(
            member(alertWhenHigh = false), remote(bac = 2.5, updatedAt = now), 1.5, now
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `the threshold is checked against the decayed value, not the published one`() {
        // 1.8 published four hours ago is 1.2 by now, which is under the limit.
        val (_, alerts) = applyFriendUpdate(
            member(), remote(bac = 1.8, updatedAt = now - 4 * 3600), 1.5, now
        )
        assertTrue(alerts.isEmpty(), "a stale value must not fire a live alert")
    }
}
