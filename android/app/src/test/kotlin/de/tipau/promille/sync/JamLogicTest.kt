package de.tipau.promille.sync

import de.tipau.promille.bac.*
import org.junit.Assert.*
import org.junit.Test

class JamLogicTest {
    @Test
    fun testRosterUpsertLWW() {
        val p1 = JamParticipant(id = "p1", displayName = "Alice", currentBAC = 0.2, lastUpdatedEpochSeconds = 100)
        val p1Newer = p1.copy(currentBAC = 0.4, lastUpdatedEpochSeconds = 110)
        val p1Stale = p1.copy(currentBAC = 0.1, lastUpdatedEpochSeconds = 90)

        var list = listOf(p1)
        list = list.upserting(p1Newer)
        assertEquals(0.4, list.first().currentBAC ?: 0.0, 0.001)

        list = list.upserting(p1Stale)
        assertEquals(0.4, list.first().currentBAC ?: 0.0, 0.001)
    }

    @Test
    fun testElectJamHost() {
        val p1 = JamParticipant(id = "p1", displayName = "Bob", currentBAC = 0.5, joinedAtEpochSeconds = 10)
        val p2 = JamParticipant(id = "p2", displayName = "Alice", currentBAC = 0.1, joinedAtEpochSeconds = 20)
        val p3 = JamParticipant(id = "p3", displayName = "Charlie", currentBAC = null, joinedAtEpochSeconds = 5)

        val elected = electJamHost(listOf(p1, p2, p3))
        assertEquals("p2", elected?.id)
    }
}

