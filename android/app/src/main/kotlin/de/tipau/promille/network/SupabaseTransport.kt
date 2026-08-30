package de.tipau.promille.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The HTTP half of SupabaseService.swift: raw PostgREST plus GoTrue, no SDK.
 * Bodies stay untyped JsonObject the way the Swift side passes [String: Any],
 * because the write surface is 80 odd endpoints and none of them would gain
 * anything from a generated request model.
 *
 * Every request carries the anon key in `apikey`; the bearer token on top of it
 * decides which role Postgres runs the statement as.
 */
class SupabaseTransport(
    private val store: SessionStore?,
    engine: HttpClientEngine = OkHttp.create(),
    private val now: () -> Double = { System.currentTimeMillis() / 1000.0 },
    // Injected rather than read straight off SupabaseConfig so a JVM test can
    // drive the transport without a populated local.properties.
    private val projectURL: String = SupabaseConfig.projectURL,
    private val anonKey: String = SupabaseConfig.anonKey
) {

    private val client = HttpClient(engine)
    private val refreshLock = Mutex()

    @Volatile
    var session: AccountSession? = store?.load()
        private set

    val isSignedIn: Boolean get() = session != null
    val isConfigured: Boolean get() = projectURL.startsWith("https://") && anonKey.isNotBlank()

    fun applySession(s: AccountSession) {
        session = s
        store?.save(s)
    }

    fun clearSession() {
        session = null
        store?.clear()
    }

    // MARK: authenticated verbs

    suspend fun restGET(path: String): String =
        perform(path, HttpMethod.Get, bearer = requireToken(), accept = true)

    suspend fun restRPC(function: String, body: JsonObject): String =
        perform(
            "/rest/v1/rpc/$function", HttpMethod.Post, bearer = requireToken(),
            body = body, accept = true
        )

    suspend fun restPATCH(path: String, body: JsonObject) {
        perform(path, HttpMethod.Patch, bearer = requireToken(), body = body, prefer = MINIMAL)
    }

    suspend fun restPOST(path: String, body: JsonElement, ignoreDuplicates: Boolean = false) {
        perform(
            path, HttpMethod.Post, bearer = requireToken(), body = body,
            prefer = if (ignoreDuplicates) IGNORE_DUPLICATES else MINIMAL
        )
    }

    suspend fun restDELETE(path: String) {
        perform(path, HttpMethod.Delete, bearer = requireToken(), prefer = MINIMAL)
    }

    /** Upsert helper: same merge-duplicates resolution the Swift upsert() uses. */
    suspend fun upsert(path: String, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        perform(
            path, HttpMethod.Post, bearer = requireToken(),
            body = JsonArrayOf(rows), prefer = MERGE_DUPLICATES
        )
    }

    // MARK: anon verbs

    suspend fun authPOST(path: String, body: JsonObject): String =
        perform(path, HttpMethod.Post, bearer = null, body = body)

    suspend fun publicGET(path: String): String =
        perform(path, HttpMethod.Get, bearer = anonKey, accept = true)

    suspend fun publicPOST(path: String, body: JsonElement, ignoreDuplicates: Boolean = false) {
        perform(
            path, HttpMethod.Post, bearer = anonKey, body = body,
            prefer = if (ignoreDuplicates) IGNORE_DUPLICATES else MINIMAL
        )
    }

    suspend fun publicRPC(function: String, body: JsonObject): String =
        perform(
            "/rest/v1/rpc/$function", HttpMethod.Post,
            bearer = anonKey, body = body, accept = true
        )

    /**
     * Community writes prefer the signed-in token so the server keys the crowd
     * vote on a real account id, which is far harder to sybil than the request
     * IP that anon callers fall back to.
     */
    suspend fun communityPOST(path: String, body: JsonElement) {
        perform(
            path, HttpMethod.Post,
            bearer = session?.accessToken ?: anonKey,
            body = body, prefer = MINIMAL
        )
    }

    // MARK: internals

    private fun requireToken(): String =
        session?.accessToken ?: throw SupabaseError.NotSignedIn

    private suspend fun perform(
        path: String,
        method: HttpMethod,
        bearer: String?,
        body: JsonElement? = null,
        prefer: String? = null,
        accept: Boolean = false
    ): String {
        if (!isConfigured) throw SupabaseError.NotConfigured
        if (bearer != null && bearer == session?.accessToken) refreshIfNeeded()

        val response = try {
            client.request(projectURL + path) {
                this.method = method
                header("apikey", anonKey)
                // The refresh above may have swapped the token, so read it late.
                val token = if (bearer == null) null
                else if (bearer == anonKey) bearer
                else session?.accessToken ?: bearer
                if (token != null) header("Authorization", "Bearer $token")
                if (accept) header("Accept", "application/json")
                if (prefer != null) header("Prefer", prefer)
                if (body != null) {
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            }
        } catch (e: SupabaseError) {
            throw e
        } catch (e: Throwable) {
            throw SupabaseError.NetworkError(e)
        }

        val text = response.bodyAsText()
        Responses.errorFor(response.status.value, text)?.let { throw it }
        return text
    }

    /**
     * Refreshes two minutes before expiry. A failure only clears the stored
     * session when the server definitively rejected it, see
     * Responses.refreshFailureClearsSession.
     */
    private suspend fun refreshIfNeeded() {
        val current = session ?: return
        if (now() < current.expiresAt - REFRESH_MARGIN_SECONDS) return

        refreshLock.withLock {
            val s = session ?: return
            if (now() < s.expiresAt - REFRESH_MARGIN_SECONDS) return

            val body = JsonObject(mapOf("refresh_token" to jsonString(s.refreshToken)))
            try {
                val data = authPOST("/auth/v1/token?grant_type=refresh_token", body)
                applySession(Responses.sessionFrom(data, now()))
            } catch (e: SupabaseError) {
                if (Responses.refreshFailureClearsSession(e)) {
                    clearSession()
                    throw SupabaseError.NotSignedIn
                }
                throw e
            } catch (e: Throwable) {
                throw SupabaseError.NetworkError(e)
            }
        }
    }

    private companion object {
        const val REFRESH_MARGIN_SECONDS = 120.0
        const val MINIMAL = "return=minimal"
        const val IGNORE_DUPLICATES = "return=minimal,resolution=ignore-duplicates"
        const val MERGE_DUPLICATES = "return=minimal,resolution=merge-duplicates"
    }
}

internal fun jsonString(value: String): JsonElement =
    kotlinx.serialization.json.JsonPrimitive(value)

@Suppress("FunctionName")
internal fun JsonArrayOf(rows: List<JsonObject>): JsonElement =
    kotlinx.serialization.json.JsonArray(rows)

internal val supabaseJson = Json { ignoreUnknownKeys = true }
