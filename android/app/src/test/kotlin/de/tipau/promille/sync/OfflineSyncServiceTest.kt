package de.tipau.promille.sync

import de.tipau.promille.data.PendingSyncDao
import de.tipau.promille.data.PendingSyncOperationEntity
import de.tipau.promille.network.AccountSession
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.SupabaseTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.utils.EmptyContent
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The queue is what stands between a bar with bad signal and a lost permille, and
 * every branch in it is a silent failure mode: a dropped operation, a burned
 * retry, or a stale value published as if it were current.
 */
class OfflineSyncServiceTest {

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

    private fun service(
        dao: PendingSyncDao,
        now: () -> Long = { 1_000_000 },
        respondWith: HttpStatusCode = HttpStatusCode.OK
    ): Pair<OfflineSyncService, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("[]", respondWith, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val transport = SupabaseTransport(
            store = null, engine = engine, now = { 1_000_000.0 },
            projectURL = "https://test.supabase.co", anonKey = "anon-key"
        )
        transport.applySession(
            AccountSession("token", "refresh", "user-1", expiresAt = 9_999_999.0)
        )
        return OfflineSyncService(dao, SupabaseService(transport), now) to seen
    }

    private fun HttpRequestData.bodyText(): String =
        (body as? TextContent)?.text ?: if (body === EmptyContent) "" else body.toString()

    @Test
    fun `a second permille replaces the first instead of queueing behind it`() = runTest {
        val dao = FakeDao()
        val (sync, _) = service(dao)
        sync.enqueueBACPublish(0.4, 0.15)
        sync.enqueueBACPublish(0.9, 0.15)
        assertEquals(1, dao.rows.size, "only the newest permille is worth publishing")
        assertTrue(dao.rows.single().payload.contains("0.9"))
    }

    @Test
    fun `a queued permille is eliminated down to now before it is published`() = runTest {
        val dao = FakeDao()
        var clock = 1_000_000L
        val (sync, seen) = service(dao, now = { clock })
        sync.enqueueBACPublish(0.5, 0.15)
        clock += 3600 // one hour offline
        sync.syncAll()

        val body = seen.single { it.url.encodedPath.contains("profiles") }.bodyText()
        assertTrue(body.contains("0.35"), "0.5 minus one hour at 0.15 should publish as 0.35, got $body")
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a server outage stops the drain instead of burning a retry on every row`() = runTest {
        val dao = FakeDao()
        val (sync, seen) = service(dao, respondWith = HttpStatusCode.InternalServerError)
        sync.enqueueBACPublish(0.4, 0.15)
        sync.enqueueUpdateSharing(true)
        sync.syncAll()

        assertEquals(1, seen.size, "the second operation must not be attempted during an outage")
        assertEquals(2, dao.rows.size, "nothing is lost")
        assertEquals(1, dao.rows.first { it.operationType == "publishBAC" }.retryCount)
        assertEquals(0, dao.rows.first { it.operationType == "updateSharing" }.retryCount)
    }

    @Test
    fun `an operation that failed six times is given up on`() = runTest {
        val dao = FakeDao()
        dao.rows += PendingSyncOperationEntity(
            id = "op", operationType = "updateSharing", payload = """{"sharing":true}""",
            createdAt = 1, retryCount = 5
        )
        val (sync, _) = service(dao, respondWith = HttpStatusCode.InternalServerError)
        sync.syncAll()
        assertTrue(dao.rows.isEmpty(), "past the retry limit the operation is dropped")
    }

    @Test
    fun `a corrupt payload is dropped at once rather than retried forever`() = runTest {
        val dao = FakeDao()
        dao.rows += PendingSyncOperationEntity(
            id = "bad", operationType = "publishBAC", payload = "{", createdAt = 1
        )
        dao.rows += PendingSyncOperationEntity(
            id = "good", operationType = "updateSharing", payload = """{"sharing":false}""",
            createdAt = 2
        )
        val (sync, seen) = service(dao)
        sync.syncAll()

        assertTrue(dao.rows.isEmpty(), "the corrupt row must not block the one behind it")
        assertEquals(1, seen.size, "the good operation still ran")
    }
}
