package de.tipau.promille.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Port of SupabaseService.swift: raw PostgREST plus GoTrue, no SDK, polling
 * only. The HTTP mechanics live in SupabaseTransport; this layer is the endpoint
 * surface and the small amount of state the screens observe.
 *
 * Every server write degrades gracefully when a column or table is missing, so a
 * feature "doing nothing" means missing Supabase schema, not a client crash.
 */
class SupabaseService(internal val transport: SupabaseTransport) {

    private val _myProfile = MutableStateFlow<FriendProfile?>(null)
    val myProfile: StateFlow<FriendProfile?> = _myProfile.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isSignedIn = MutableStateFlow(transport.isSignedIn)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    internal val friendCodeToHostIDCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    val isConfigured: Boolean get() = transport.isConfigured
    val userId: String? get() = transport.session?.userId

    internal fun requireUserId(): String =
        transport.session?.userId ?: throw SupabaseError.NotSignedIn

    /** Set by refreshAdminStatus in SupabaseAdminApi, which owns the is_admin RPC. */
    internal fun setAdminFlag(value: Boolean) {
        _isAdmin.value = value
    }

    // MARK: Auth

    suspend fun signUp(email: String, password: String, displayName: String) {
        if (!isConfigured) throw SupabaseError.NotConfigured
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", buildJsonObject { put("display_name", displayName) })
        }
        val data = transport.authPOST("/auth/v1/signup", body)
        transport.applySession(Responses.sessionFrom(data, nowSeconds()))
        _isSignedIn.value = true
        syncMyProfile()
        runCatching { refreshAdminStatus() }
    }

    suspend fun signIn(email: String, password: String) {
        if (!isConfigured) throw SupabaseError.NotConfigured
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val data = transport.authPOST("/auth/v1/token?grant_type=password", body)
        transport.applySession(Responses.sessionFrom(data, nowSeconds()))
        _isSignedIn.value = true
        syncMyProfile()
        runCatching { refreshAdminStatus() }
    }

    suspend fun signOut() {
        if (transport.session != null) {
            runCatching { transport.restPOST("/auth/v1/logout", JsonObject(emptyMap())) }
        }
        clearLocalState()
    }

    /** Needs the SECURITY DEFINER delete_user() function from the Supabase SQL. */
    suspend fun deleteAccount() {
        requireUserId()
        transport.restPOST("/rest/v1/rpc/delete_user", JsonObject(emptyMap()))
        clearLocalState()
    }

    private fun clearLocalState() {
        transport.clearSession()
        _myProfile.value = null
        _isAdmin.value = false
        _isSignedIn.value = false
    }

    // MARK: Profile

    suspend fun syncMyProfile() {
        val id = requireUserId()
        val rows = decodeList<FriendProfile>(
            transport.restGET("/rest/v1/profiles?id=eq.$id&select=*")
        )
        _myProfile.value = rows.firstOrNull()
    }

    suspend fun publishBAC(bac: Double) {
        val id = requireUserId()
        val stamp = Timestamps.format(nowSeconds())
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("current_bac", bac)
            put("bac_updated_at", stamp)
        })
        _myProfile.value = _myProfile.value?.copy(currentBac = bac, bacUpdatedAtRaw = stamp)
    }

    suspend fun updateDisplayName(name: String) {
        val id = requireUserId()
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("display_name", name)
        })
        _myProfile.value = _myProfile.value?.copy(displayName = name)
    }

    suspend fun updateSharing(sharing: Boolean) {
        val id = requireUserId()
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("is_sharing", sharing)
        })
        _myProfile.value = _myProfile.value?.copy(isSharing = sharing)
    }

    /** Needs profiles.is_probationary; without the column the PATCH just no-ops. */
    suspend fun updateProbation(on: Boolean) {
        val id = requireUserId()
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("is_probationary", on)
        })
        _myProfile.value = _myProfile.value?.copy(isProbationary = on)
    }

    /** Needs profiles.sos_active and profiles.sos_updated_at. */
    suspend fun setSOS(active: Boolean) {
        val id = requireUserId()
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("sos_active", active)
            put("sos_updated_at", Timestamps.format(nowSeconds()))
        })
        _myProfile.value = _myProfile.value?.copy(sosActive = active)
    }

    suspend fun publishAchievements(ids: List<String>) {
        val id = requireUserId()
        transport.restPATCH("/rest/v1/profiles?id=eq.$id", buildJsonObject {
            put("achievements", JsonArray(ids.map { JsonPrimitive(it) }))
        })
        _myProfile.value = _myProfile.value?.copy(achievements = ids)
    }

    // MARK: Account history sync
    //
    // RLS scopes every row to auth.uid(), so the GETs need no user_id filter.

    suspend fun fetchDrinkHistory(): List<RemoteDrink> =
        decodeList(transport.restGET("/rest/v1/drink_history?select=*"))

    suspend fun uploadDrinkHistory(rows: List<JsonObject>) {
        val id = requireUserId()
        transport.upsert("/rest/v1/drink_history", rows.map { it.withUser(id) })
    }

    suspend fun deleteDrinkHistory(ids: List<String>) {
        if (ids.isEmpty()) return
        transport.restDELETE("/rest/v1/drink_history?id=in.(${ids.joinToString(",")})")
    }

    suspend fun fetchDayNotes(): List<RemoteDayNote> =
        decodeList(transport.restGET("/rest/v1/day_notes?select=*"))

    suspend fun uploadDayNotes(rows: List<JsonObject>) {
        val id = requireUserId()
        transport.upsert("/rest/v1/day_notes", rows.map { it.withUser(id) })
    }

    suspend fun deleteDayNotes(days: List<String>) {
        if (days.isEmpty()) return
        transport.restDELETE("/rest/v1/day_notes?day_start=in.(${days.joinToString(",")})")
    }

    /** One JSON document per user: settings, water log, custom mixes, drinks. */
    suspend fun fetchUserBackup(): JsonObject? {
        val raw = transport.restGET("/rest/v1/user_backup?select=data")
        val arr = supabaseJson.parseToJsonElement(raw) as? JsonArray ?: return null
        return (arr.firstOrNull() as? JsonObject)?.get("data")?.jsonObject
    }

    suspend fun uploadUserBackup(document: JsonObject) {
        val id = requireUserId()
        transport.upsert("/rest/v1/user_backup", listOf(buildJsonObject {
            put("user_id", id)
            put("data", document)
        }))
    }
}

internal fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0

internal fun JsonObject.withUser(userId: String): JsonObject =
    JsonObject(this + ("user_id" to JsonPrimitive(userId)))

internal inline fun <reified T> decodeList(raw: String): List<T> =
    runCatching { supabaseJson.decodeFromString<List<T>>(raw) }.getOrDefault(emptyList())
