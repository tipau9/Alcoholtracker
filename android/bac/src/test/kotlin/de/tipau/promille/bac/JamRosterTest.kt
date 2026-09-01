package de.tipau.promille.bac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The roster decides whose permille a jam member sees, so the merge is pinned. */
class JamRosterTest {

    private fun p(
        id: String,
        userID: String? = null,
        bac: Double? = null,
        updated: Long = 0
    ) = JamParticipant(
        id = id,
        userID = userID,
        displayName = "Test",
        currentBAC = bac,
        lastUpdatedEpochSeconds = updated
    )

    @Test
    fun `same user id replaces even when the row id changed`() {
        val old = listOf(p("row-a", userID = "u1", bac = 0.4, updated = 100))
        val merged = old.upserting(p("row-b", userID = "u1", bac = 0.9, updated = 200))
        assertEquals(1, merged.size, "one row per user, not a duplicate")
        assertEquals(0.9, merged[0].currentBAC)
    }

    @Test
    fun `privacy labels split shared from hidden and ignore local preferences`() {
        val (shared, hidden) = JamSettings(
            shareBAC = false,
            shareDrinkCount = false,
            shareLocation = true,
            allowWaves = false
        ).privacyLabels()
        assertEquals(listOf("Status", "Drinks", "SOS-Status", "Fotos"), shared)
        assertEquals(listOf("Promille-Wert", "Anzahl Drinks"), hidden)
    }

    @Test
    fun `a stale broadcast never clobbers a newer status`() {
        val current = listOf(p("row-a", userID = "u1", bac = 0.9, updated = 200))
        val merged = current.upserting(p("row-a", userID = "u1", bac = 0.1, updated = 100))
        assertEquals(0.9, merged[0].currentBAC, "late arrival after reconnect must lose")
    }

    @Test
    fun `an equal timestamp still applies so a retry is not dropped`() {
        val current = listOf(p("row-a", userID = "u1", bac = 0.1, updated = 200))
        val merged = current.upserting(p("row-a", userID = "u1", bac = 0.5, updated = 200))
        assertEquals(0.5, merged[0].currentBAC)
    }

    @Test
    fun `an anonymous proximity peer matches on the row id`() {
        val current = listOf(p("row-a", bac = 0.2, updated = 100))
        val merged = current.upserting(p("row-a", bac = 0.7, updated = 300))
        assertEquals(1, merged.size)
        assertEquals(0.7, merged[0].currentBAC)
    }

    @Test
    fun `merging two rosters is order independent`() {
        val a = listOf(p("row-a", userID = "u1", bac = 0.9, updated = 200), p("row-b", userID = "u2"))
        val b = listOf(p("row-a", userID = "u1", bac = 0.1, updated = 100), p("row-c", userID = "u3"))

        val left = a.merging(b).sortedBy { it.userID }
        val right = b.merging(a).sortedBy { it.userID }

        assertEquals(3, left.size, "union of both sides")
        assertEquals(left.map { it.userID }, right.map { it.userID })
        assertEquals(left.map { it.currentBAC }, right.map { it.currentBAC })
        assertTrue(left.first { it.userID == "u1" }.currentBAC == 0.9, "newest wins either way")
    }
}

/**
 * Host election runs on every remaining client with no server involved. If two
 * clients disagree the jam splits in two, so the order has to be total.
 */
class JamHostElectionTest {

    private fun p(
        id: String,
        bac: Double? = null,
        joined: Long = 0,
        userID: String? = id
    ) = JamParticipant(
        id = id, userID = userID, displayName = id,
        currentBAC = bac, joinedAtEpochSeconds = joined
    )

    @Test
    fun `the soberest member wins`() {
        val elected = electJamHost(listOf(p("a", 0.8), p("b", 0.2), p("c", 1.4)))
        assertEquals("b", elected?.id)
    }

    @Test
    fun `a hidden value loses to any known one`() {
        val elected = electJamHost(listOf(p("a", null), p("b", 1.4)))
        assertEquals("b", elected?.id, "a known drinker beats an unknown")
    }

    @Test
    fun `equal values fall back to the longest jam history, then the id`() {
        assertEquals("b", electJamHost(listOf(p("a", 0.5, joined = 200), p("b", 0.5, joined = 100)))?.id)
        assertEquals("a", electJamHost(listOf(p("b", 0.5), p("a", 0.5)))?.id)
    }

    @Test
    fun `the order of the input never changes the winner`() {
        val members = listOf(p("a", 0.5, 100), p("b", null), p("c", 0.5, 90), p("d", 1.2))
        val forwards = electJamHost(members)?.id
        val backwards = electJamHost(members.reversed())?.id
        assertEquals(forwards, backwards)
        assertEquals("c", forwards)
    }

    @Test
    fun `the leaving host is excluded and an empty jam elects nobody`() {
        assertEquals("b", electJamHost(listOf(p("a", 0.1), p("b", 0.9)), excludingUserID = "a")?.id)
        assertNull(electJamHost(emptyList()))
    }
}

/**
 * The roster poll runs every 12 seconds. A wrong filter here either drops a
 * member who is present or resurrects one who just left.
 */
class JamActiveRosterTest {

    private val now = 1_700_000_000L

    private fun p(id: String, updated: Long = 1_700_000_000L, userID: String? = null) =
        JamParticipant(
            id = id, userID = userID, displayName = id,
            lastUpdatedEpochSeconds = updated
        )

    private val me = JamParticipant(id = "me", userID = "u-me", displayName = "Ich")

    @Test
    fun `our own row comes from the local copy, never from the server`() {
        val roster = activeJamRoster(
            serverRows = listOf(p("me", userID = "u-me"), p("a")),
            me = me, myUserID = "u-me", tombstonedIDs = emptySet(), nowEpochSeconds = now
        )
        assertEquals(listOf("a", "me"), roster.map { it.id })
        assertEquals(1, roster.count { it.id == "me" }, "no duplicate self")
    }

    @Test
    fun `a phone that stopped reporting drops out after two minutes`() {
        val roster = activeJamRoster(
            serverRows = listOf(p("fresh", now - 119), p("gone", now - 121)),
            me = me, myUserID = "u-me", tombstonedIDs = emptySet(), nowEpochSeconds = now
        )
        assertEquals(listOf("fresh", "me"), roster.map { it.id })
    }

    @Test
    fun `a server row keeps the privacy toggles only bluetooth carries`() {
        val known = p("a", userID = "u-a").copy(sharedSettings = JamSettings(shareBAC = false))
        val roster = activeJamRoster(
            serverRows = listOf(p("a", userID = "u-a")),
            me = me, myUserID = "u-me", tombstonedIDs = emptySet(), nowEpochSeconds = now,
            existingParticipants = listOf(known)
        )
        assertEquals(false, roster.first { it.id == "a" }.sharedSettings?.shareBAC)
    }

    @Test
    fun `a member who just left is not re-added by the next poll`() {
        val roster = activeJamRoster(
            serverRows = listOf(p("left")),
            me = me, myUserID = "u-me", tombstonedIDs = setOf("left"), nowEpochSeconds = now
        )
        assertEquals(listOf("me"), roster.map { it.id })
    }

    @Test
    fun `a vanished own row reads as a kick, a present one does not`() {
        assertTrue(wasKickedFromJam(listOf(p("a")), "me", "u-me"))
        assertFalse(wasKickedFromJam(listOf(p("a"), p("x", userID = "u-me")), "me", "u-me"))
        assertFalse(wasKickedFromJam(listOf(p("me")), "me", null))
    }

    @Test
    fun `a proximity peer with no server row survives the poll`() {
        val peer = p("bt").copy(connectionType = JamConnectionType.PROXIMITY)
        val roster = activeJamRoster(
            serverRows = emptyList(),
            me = me, myUserID = "u-me", tombstonedIDs = emptySet(), nowEpochSeconds = now,
            existingParticipants = listOf(peer)
        )
        assertEquals(listOf("bt", "me"), roster.map { it.id })
    }

    @Test
    fun `a proximity peer we just kicked is not resurrected from the local copy`() {
        val peer = p("bt").copy(connectionType = JamConnectionType.PROXIMITY)
        val roster = activeJamRoster(
            serverRows = emptyList(),
            me = me, myUserID = "u-me", tombstonedIDs = setOf("bt"), nowEpochSeconds = now,
            existingParticipants = listOf(peer)
        )
        assertEquals(listOf("me"), roster.map { it.id })
    }
}

/** The water leaderboard merges two sources, one of which can race our own submit. */
class JamWaterMergeTest {

    @Test
    fun `the server wins for everyone else`() {
        val merged = mergeWaterScores(
            local = listOf(WaterScore("a", "Anna", 900)),
            server = listOf(WaterScore("a", "Anna", 1200)),
            myParticipantID = "me"
        )
        assertEquals(1200, merged.single().ms, "a host reset has to reach every client")
    }

    @Test
    fun `our own better time survives a poll that races the submit`() {
        val merged = mergeWaterScores(
            local = listOf(WaterScore("me", "Ich", 800)),
            server = listOf(WaterScore("me", "Ich", 1500)),
            myParticipantID = "me"
        )
        assertEquals(800, merged.single().ms)
    }

    @Test
    fun `our own time appears even before the server has it`() {
        val merged = mergeWaterScores(
            local = listOf(WaterScore("me", "Ich", 800)),
            server = emptyList(),
            myParticipantID = "me"
        )
        assertEquals(listOf("me"), merged.map { it.participantID })
    }

    @Test
    fun `the board is ordered by time, fastest first`() {
        val merged = mergeWaterScores(
            local = emptyList(),
            server = listOf(WaterScore("a", "A", 1200), WaterScore("b", "B", 700)),
            myParticipantID = "me"
        )
        assertEquals(listOf("b", "a"), merged.map { it.participantID })
    }
}

/** Every arcade round is judged on these numbers, on both platforms. */
class JamArcadeScoringTest {

    @Test
    fun `perfect second scores the distance from the target, either side`() {
        assertEquals(120.0, perfectSecondErrorMs(5.12, 5.0), 1e-9)
        assertEquals(120.0, perfectSecondErrorMs(4.88, 5.0), 1e-9, "early and late are equally wrong")
        assertEquals(0.0, perfectSecondErrorMs(5.0, 5.0), 1e-9)
    }

    @Test
    fun `tapping before the signal is a false start, not a fast time`() {
        val early = reactionResult(tapAtEpochSeconds = 99.9, signalAtEpochSeconds = 100.0)
        assertTrue(early.falseStart)
        assertEquals(0.0, early.milliseconds, 1e-9, "a negative time must never rank first")

        val ok = reactionResult(tapAtEpochSeconds = 100.25, signalAtEpochSeconds = 100.0)
        assertFalse(ok.falseStart)
        assertEquals(250.0, ok.milliseconds, 1e-9)
    }

    @Test
    fun `a steady phone beats a wobbly one, and no sensor scores worst`() {
        val steady = BalanceAccumulator().apply { repeat(30) { record(0.0, 0.0) } }
        val wobbly = BalanceAccumulator().apply { repeat(30) { record(0.2, 0.2) } }
        assertEquals(0.0, steady.score(), 1e-9)
        assertTrue(wobbly.score() > steady.score())
        assertEquals(100.0, BalanceAccumulator().score(), "a phone that never measured must not win")
    }

    @Test
    fun `a tilt past the normalizer is clamped rather than scoring unboundedly`() {
        val hard = BalanceAccumulator().apply { record(9.0, 9.0) }
        val justPast = BalanceAccumulator().apply { record(0.45, 0.45) }
        assertEquals(justPast.score(), hard.score(), 1e-9)
    }

    @Test
    fun `results order best first and a false start always loses`() {
        fun r(id: String, value: Double, dq: Boolean = false, at: Double = 0.0) =
            JamArcadeResultPayload(
                jamID = "j", roundID = "r", participantID = id, participantName = id,
                value = value, disqualified = dq, submittedAtEpochSeconds = at
            )
        val ordered = orderedArcadeResults(
            listOf(r("dq", 0.0, dq = true), r("slow", 400.0), r("fast", 120.0))
        )
        assertEquals(listOf("fast", "slow", "dq"), ordered.map { it.participantID })
    }

}
