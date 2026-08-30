package de.tipau.promille.sync

import de.tipau.promille.data.PendingSyncDao
import de.tipau.promille.data.PendingSyncOperationEntity
import de.tipau.promille.network.AccountSession
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.SupabaseTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The throttle stands between the 30s ticker and a PATCH per tick. Getting it
 * wrong is either a hammered server or friends looking at a stale permille.
 */
class BACPublisherTest {

    private class FakeDao : PendingSyncDao {
        val rows = mutableListOf<PendingSyncOperationEntity>()
        override suspend fun getPending() = rows.sortedBy { it.createdAt }
        override suspend fun insert(op: PendingSyncOperationEntity) {
            rows.removeAll { it.id == op.id }
            rows += op
        }
        override suspend fun update(op: PendingSyncOperationEntity) {
            val i = rows.indexOfFirst { it.id == op.id }
            if (i >= 0) rows[i] = op
        }
        override suspend fun delete(op: PendingSyncOperationEntity) {
            rows.removeAll { it.id == op.id }
        }
        override suspend fun deleteByType(operationType: String) {
            rows.removeAll { it.operationType == operationType }
        }
        override suspend fun count() = rows.size
    }

    private class Fixture(
        status: HttpStatusCode = HttpStatusCode.OK,
        signedIn: Boolean = true
    ) {
        var clock = 1_000_000L
        val dao = FakeDao()
        var requests = 0
        private val engine = MockEngine {
            requests++
            respond("[]", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        private val transport = SupabaseTransport(
            store = null, engine = engine, now = { 1_000_000.0 },
            projectURL = "https://test.supabase.co", anonKey = "anon-key"
        ).also {
            if (signedIn) {
                it.applySession(AccountSession("token", "refresh", "user-1", expiresAt = 9_999_999.0))
            }
        }
        private val supabase = SupabaseService(transport)
        val offline = OfflineSyncService(dao, supabase) { clock }
        val publisher = BACPublisher(supabase, offline) { clock }
    }

    @Test
    fun `the first value always publishes, then the ticker is throttled out`() = runTest {
        val f = Fixture()
        assertTrue(f.publisher.publish(0.40, 0.15))
        // Same permille 30 seconds later: no move, no sober crossing, not due.
        f.clock += 30
        assertFalse(f.publisher.publish(0.40, 0.15))
        assertEquals(1, f.requests)
    }

    @Test
    fun `a big move publishes at once instead of waiting out the interval`() = runTest {
        val f = Fixture()
        f.publisher.publish(0.40, 0.15)
        f.clock += 30
        // 0.45 would be 0.049999... in binary and miss the threshold. The Swift
        // side computes the identical double, so this stays a shared quirk
        // rather than a divergence, and the next tick publishes anyway.
        assertTrue(f.publisher.publish(0.46, 0.15))
        assertEquals(2, f.requests)
    }

    @Test
    fun `going sober publishes even though the move is tiny`() = runTest {
        val f = Fixture()
        f.publisher.publish(0.02, 0.15)
        f.clock += 30
        assertTrue(f.publisher.publish(0.0, 0.15), "friends must see the sober transition")
    }

    @Test
    fun `two minutes of a flat permille is due again`() = runTest {
        val f = Fixture()
        f.publisher.publish(0.40, 0.15)
        f.clock += 120
        assertTrue(f.publisher.publish(0.40, 0.15))
    }

    @Test
    fun `a failed publish lands in the offline queue rather than vanishing`() = runTest {
        val f = Fixture(status = HttpStatusCode.InternalServerError)
        assertTrue(f.publisher.publish(0.40, 0.15))
        assertEquals(1, f.dao.rows.size, "the queue has no other producer")
        assertEquals("publishBAC", f.dao.rows.single().operationType)
    }

    @Test
    fun `signed out publishes nothing and queues nothing`() = runTest {
        val f = Fixture(signedIn = false)
        assertFalse(f.publisher.publish(0.40, 0.15))
        assertEquals(0, f.requests)
        assertTrue(f.dao.rows.isEmpty())
    }
}
