package de.tipau.promille.ui.viewmodels

import de.tipau.promille.network.AccountSession
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.SupabaseTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The console loads eight endpoints in one pass. If one dead RPC could blank the
 * other seven, an admin would read seven empty sections as "nothing to do"
 * rather than as a failed load, and moderate nothing while a queue piles up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    // Unconfined so viewModelScope.launch runs inline: the loads go out over a
    // MockEngine, which does not park on the test scheduler.
    private val dispatcher = UnconfinedTestDispatcher()

    // Set and left set: resetting it mid-run pulls the dispatcher out from
    // under a ktor continuation that has not resumed yet, and the whole class
    // then fails with a missing Main dispatcher.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    /** [failing] names the RPCs that answer 500; everything else answers with one row. */
    private fun viewModel(failing: Set<String> = emptySet()): AdminViewModel {
        val engine = MockEngine { request ->
            val rpc = request.url.encodedPath.substringAfterLast('/')
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (rpc in failing) {
                respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, json)
            } else {
                respond(bodyFor(rpc), HttpStatusCode.OK, json)
            }
        }
        val transport = SupabaseTransport(
            store = null, engine = engine, now = { 1_000_000.0 },
            projectURL = "https://test.supabase.co", anonKey = "anon-key"
        )
        transport.applySession(
            AccountSession("token", "refresh", "user-1", expiresAt = 9_999_999.0)
        )
        return AdminViewModel(SupabaseService(transport))
    }

    private fun bodyFor(rpc: String): String = when (rpc) {
        "admin_metrics" -> """[{"metric":"pending_drinks","value":3}]"""
        "admin_moderation_queue" ->
            """[{"item_type":"drink","id":"q1","title":"Testbier","subtitle":"5,0 %","status":"pending"}]"""
        "admin_reports_list" -> """[{"id":"r1","item_type":"drink","reason":"Spam","status":"open"}]"""
        "admin_feature_flags_list" -> """[{"key":"community","enabled":true}]"""
        "admin_users_list" -> """[{"user_id":"u1","role":"admin"}]"""
        "admin_blocked_voters_list" -> """[{"voter":"device-1","reason":"Spam"}]"""
        "admin_audit_log_list" -> """[{"id":"a1","action":"approve"}]"""
        "admin_content_list" ->
            """[{"item_type":"drink","id":"c1","title":"Pils","subtitle":"","status":"approved"}]"""
        else -> "[]"
    }

    @Test
    fun `a healthy load fills every section`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.reloadAll()?.join()

        assertEquals(1, viewModel.metrics.value.size)
        assertEquals("q1", viewModel.queue.value.single().id)
        assertEquals(1, viewModel.reports.value.size)
        assertEquals(1, viewModel.flags.value.size)
        assertEquals(1, viewModel.adminUsers.value.size)
        assertEquals(1, viewModel.blockedVoters.value.size)
        assertEquals(1, viewModel.audit.value.size)
        assertEquals(1, viewModel.catalog.value.size)
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `one dead RPC does not blank the other seven sections`() = runTest(dispatcher) {
        // The first call in the sequence, which is the one that used to abort it.
        val viewModel = viewModel(failing = setOf("admin_metrics"))
        viewModel.reloadAll()?.join()

        assertTrue(viewModel.metrics.value.isEmpty())
        assertEquals("q1", viewModel.queue.value.single().id, "the queue still has to load")
        assertEquals(1, viewModel.reports.value.size)
        assertEquals(1, viewModel.audit.value.size)
        assertNotNull(viewModel.error.value, "and the failure is still reported")
    }

    @Test
    fun `the loading flag clears even when everything fails`() = runTest(dispatcher) {
        val all = setOf(
            "admin_metrics", "admin_moderation_queue", "admin_reports_list",
            "admin_feature_flags_list", "admin_users_list", "admin_blocked_voters_list",
            "admin_audit_log_list", "admin_content_list"
        )
        val viewModel = viewModel(failing = all)
        viewModel.reloadAll()?.join()

        assertTrue(viewModel.queue.value.isEmpty())
        assertNotNull(viewModel.error.value)
        assertEquals(false, viewModel.isLoading.value, "a stuck spinner hides the retry button")
    }

    @Test
    fun `select all toggles the whole queue and back`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.reloadAll()?.join()

        viewModel.toggleSelectAll()
        assertEquals(setOf("q1"), viewModel.selection.value)
        viewModel.toggleSelectAll()
        assertTrue(viewModel.selection.value.isEmpty())
    }
}
