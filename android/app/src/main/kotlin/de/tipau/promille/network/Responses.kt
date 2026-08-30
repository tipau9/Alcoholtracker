package de.tipau.promille.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * The two decisions the transport makes that are worth getting exactly right,
 * pulled out of the HTTP path so a plain JVM test can drive them: how a non-2xx
 * response becomes an error, and whether a failed refresh may delete the session.
 */
object Responses {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseObject(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** Same extraction order as the Swift perform(): msg, then message, then raw body. */
    fun serverMessage(body: String): String {
        val obj = parseObject(body)
        val msg = obj?.get("msg")?.jsonPrimitive?.contentOrNullSafe()
            ?: obj?.get("message")?.jsonPrimitive?.contentOrNullSafe()
        return msg ?: body.ifBlank { "Unbekannter Fehler" }
    }

    /** null for 2xx. A 400 whose message says "invalid" is a credential rejection. */
    fun errorFor(status: Int, body: String): SupabaseError? {
        if (status in 200..299) return null
        val message = serverMessage(body)
        if (status == 400 && message.lowercase().contains("invalid")) {
            return SupabaseError.InvalidCredentials
        }
        return SupabaseError.ServerError(status, message)
    }

    /**
     * A failed token refresh may only sign the user out on a definitive auth
     * rejection. Network trouble and server outages must keep the stored
     * session, otherwise one dead spot in a bar logs the user out for good.
     */
    fun refreshFailureClearsSession(error: SupabaseError): Boolean = when (error) {
        is SupabaseError.NetworkError, is SupabaseError.DecodingError -> false
        is SupabaseError.ServerError -> error.status < 500
        else -> true
    }

    /**
     * GoTrue answers a signup with the user object and no tokens when email
     * confirmation is on, so that case is detected before the full decode.
     */
    fun sessionFrom(body: String, nowEpochSeconds: Double): AccountSession {
        val obj = parseObject(body) ?: throw SupabaseError.DecodingError(
            IllegalArgumentException("keine JSON Antwort")
        )
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNullSafe()
            ?: throw SupabaseError.EmailConfirmationRequired
        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNullSafe()
            ?: throw SupabaseError.DecodingError(IllegalArgumentException("kein refresh_token"))
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.contentOrNullSafe()?.toDoubleOrNull()
            ?: throw SupabaseError.DecodingError(IllegalArgumentException("kein expires_in"))
        val userId = (obj["user"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNullSafe()
            ?: throw SupabaseError.DecodingError(IllegalArgumentException("keine user id"))
        return AccountSession(access, refresh, userId, nowEpochSeconds + expiresIn)
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content.ifBlank { null }
