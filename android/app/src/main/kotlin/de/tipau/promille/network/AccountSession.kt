package de.tipau.promille.network

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Same four fields the iOS Keychain session carries. expiresAt is epoch seconds. */
@Serializable
data class AccountSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAt: Double
)

/**
 * iOS keeps this in the Keychain. The Android counterpart with the same threat
 * model is EncryptedSharedPreferences, but it pulls in a Tink dependency for a
 * token that the OS already sandboxes per app. Plain private SharedPreferences
 * matches the rest of this app's local state; a rooted device defeats both.
 * ponytail: swap to EncryptedSharedPreferences if the app ever stores more than
 * a refreshable session token here.
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AccountSession? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString(AccountSession.serializer(), raw) }.getOrNull()
    }

    fun save(session: AccountSession) {
        prefs.edit().putString(KEY, json.encodeToString(AccountSession.serializer(), session)).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "session" }
}
