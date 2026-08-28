package de.tipau.promille.bac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Getting this wrong deletes the user's drink history, so all four cells are pinned. */
class SyncPlanTest {

    private val remote = listOf("a", "b", "c")

    @Test
    fun `ongoing sync deletes remotely what is gone locally`() {
        val plan = syncPlan(localIds = setOf("a"), remoteIds = remote, merge = false)
        assertEquals(listOf("b", "c"), plan.toDeleteRemotely)
        assertTrue(plan.toImport.isEmpty())
    }

    @Test
    fun `merge imports instead of deleting`() {
        val plan = syncPlan(localIds = setOf("a"), remoteIds = remote, merge = true)
        assertEquals(listOf("b", "c"), plan.toImport)
        assertTrue(plan.toDeleteRemotely.isEmpty())
    }

    @Test
    fun `an empty local store is a fresh device, so it restores and never deletes`() {
        val plan = syncPlan(localIds = emptySet(), remoteIds = remote, merge = false)
        assertEquals(remote, plan.toImport)
        assertTrue(plan.toDeleteRemotely.isEmpty(), "empty local must never wipe the backup")
    }

    @Test
    fun `a local store matching the server changes nothing`() {
        val plan = syncPlan(localIds = setOf("a", "b", "c"), remoteIds = remote, merge = false)
        assertTrue(plan.toImport.isEmpty())
        assertTrue(plan.toDeleteRemotely.isEmpty())
    }

    @Test
    fun `import and delete are never both populated`() {
        for (merge in listOf(true, false)) {
            for (local in listOf(emptySet(), setOf("a"), setOf("a", "b", "c"), setOf("z"))) {
                val plan = syncPlan(local, remote, merge)
                assertTrue(
                    plan.toImport.isEmpty() || plan.toDeleteRemotely.isEmpty(),
                    "merge=$merge local=$local produced both directions at once"
                )
            }
        }
    }
}
