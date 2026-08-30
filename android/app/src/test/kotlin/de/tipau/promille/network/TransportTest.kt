package de.tipau.promille.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transport decisions that would fail silently: which error a status maps
 * to, and whether a failed refresh is allowed to sign the user out.
 */
class TransportTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun transport(
        session: AccountSession? = null,
        nowSeconds: Double = 1_000_000.0,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): Pair<SupabaseTransport, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            handler(request)
        }
        val t = SupabaseTransport(
            store = null, engine = engine, now = { nowSeconds },
            projectURL = "https://test.supabase.co", anonKey = "anon-key"
        )
        session?.let { t.applySession(it) }
        return t to seen
    }

    @Test
    fun `a 400 that says invalid is a credential rejection, other 400s are not`() {
        assertTrue(
            Responses.errorFor(400, """{"msg":"Invalid login credentials"}""")
                is SupabaseError.InvalidCredentials
        )
        val other = Responses.errorFor(400, """{"message":"column missing"}""")
        assertTrue(other is SupabaseError.ServerError && other.serverMessage == "column missing")
        assertNull(Responses.errorFor(204, ""))
    }

    @Test
    fun `a failed refresh only clears the session on a definitive rejection`() {
        // A dead spot must never sign the user out.
        assertEquals(false, Responses.refreshFailureClearsSession(
            SupabaseError.NetworkError(java.io.IOException("kein Netz"))
        ))
        assertEquals(false, Responses.refreshFailureClearsSession(
            SupabaseError.ServerError(503, "upstream down")
        ))
        assertEquals(true, Responses.refreshFailureClearsSession(
            SupabaseError.ServerError(401, "refresh token expired")
        ))
        assertEquals(true, Responses.refreshFailureClearsSession(SupabaseError.NotSignedIn))
    }

    @Test
    fun `a signup without tokens means the confirmation mail is pending`() {
        assertFailsWith<SupabaseError.EmailConfirmationRequired> {
            Responses.sessionFrom("""{"user":{"id":"abc"}}""", 0.0)
        }
        val s = Responses.sessionFrom(
            """{"access_token":"at","refresh_token":"rt","expires_in":3600,"user":{"id":"uid"}}""",
            1_000.0
        )
        assertEquals("uid", s.userId)
        assertEquals(4_600.0, s.expiresAt)
    }

    @Test
    fun `every request carries the anon key and the bearer token`() = runTest {
        val session = AccountSession("access", "refresh", "uid", 9_999_999.0)
        val (t, seen) = transport(session) { respond("[]", HttpStatusCode.OK, jsonHeaders) }
        t.restGET("/rest/v1/profiles?select=*")
        val req = seen.single()
        assertEquals("anon-key", req.headers["apikey"])
        assertEquals("Bearer access", req.headers["Authorization"])
        assertEquals("https://test.supabase.co/rest/v1/profiles?select=*", req.url.toString())
    }

    @Test
    fun `an upsert asks postgrest to merge duplicates`() = runTest {
        val session = AccountSession("access", "refresh", "uid", 9_999_999.0)
        val (t, seen) = transport(session) { respond("", HttpStatusCode.Created) }
        t.upsert("/rest/v1/day_notes", listOf(JsonObject(mapOf("day" to jsonString("2026-08-28")))))
        assertEquals("return=minimal,resolution=merge-duplicates", seen.single().headers["Prefer"])
    }

    @Test
    fun `an expiring token is refreshed before the call goes out`() = runTest {
        // Expires in 60s, inside the 120s margin, so the refresh must fire first.
        val session = AccountSession("old", "refresh", "uid", 1_000_060.0)
        val (t, seen) = transport(session) { request ->
            if (request.url.encodedPath.contains("/auth/v1/token")) {
                respond(
                    """{"access_token":"fresh","refresh_token":"rt2","expires_in":3600,"user":{"id":"uid"}}""",
                    HttpStatusCode.OK, jsonHeaders
                )
            } else {
                respond("[]", HttpStatusCode.OK, jsonHeaders)
            }
        }
        t.restGET("/rest/v1/profiles")
        assertEquals(2, seen.size, "refresh then the actual call")
        assertEquals("Bearer fresh", seen[1].headers["Authorization"])
        assertEquals("fresh", assertNotNull(t.session).accessToken)
    }

    @Test
    fun `a refresh that fails on a network error keeps the session`() = runTest {
        val session = AccountSession("old", "refresh", "uid", 1_000_060.0)
        val (t, _) = transport(session) { respond("", HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<SupabaseError.ServerError> { t.restGET("/rest/v1/profiles") }
        assertEquals("old", assertNotNull(t.session).accessToken, "an outage must not sign the user out")
    }

    @Test
    fun `a refresh the server rejects signs the user out`() = runTest {
        val session = AccountSession("old", "refresh", "uid", 1_000_060.0)
        val (t, _) = transport(session) {
            respond("""{"msg":"refresh token expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        assertFailsWith<SupabaseError.NotSignedIn> { t.restGET("/rest/v1/profiles") }
        assertNull(t.session)
    }

    @Test
    fun `an unconfigured build never fires a request`() = runTest {
        val engine = MockEngine { error("must not be called") }
        val t = SupabaseTransport(store = null, engine = engine, projectURL = "", anonKey = "")
        assertFailsWith<SupabaseError.NotConfigured> { t.publicGET("/rest/v1/community_drinks") }
    }
}
