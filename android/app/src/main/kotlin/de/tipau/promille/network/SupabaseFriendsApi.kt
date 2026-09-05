package de.tipau.promille.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Friends and the follow model. The profile reads go through the SECURITY
 * DEFINER functions from supabase/profiles_security.sql: profiles itself is
 * self-only, so a plain select on someone else returns nothing. Both functions
 * demand an exact code or id and therefore cannot be enumerated.
 */

/** Uppercase A-Z and 0-9 only, matching the Swift sanitizeCode. */
fun sanitizeFriendCode(code: String): String =
    code.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

private fun isUuid(value: String): Boolean =
    runCatching { java.util.UUID.fromString(value) }.isSuccess

private fun jsonStrings(values: List<String>): JsonArray =
    JsonArray(values.map { JsonPrimitive(it) })

suspend fun SupabaseService.lookupFriend(code: String): FriendProfile {
    if (!isConfigured) throw SupabaseError.NotConfigured
    val clean = sanitizeFriendCode(code)
    if (clean.isEmpty()) throw SupabaseError.FriendNotFound
    val data = transport.restRPC("friend_profiles_by_codes", buildJsonObject {
        put("p_codes", jsonStrings(listOf(clean)))
    })
    return decodeList<FriendProfile>(data).firstOrNull() ?: throw SupabaseError.FriendNotFound
}

/**
 * The RPC also returns friends who stopped sharing (with the BAC nulled out);
 * only the actively sharing ones reach the Crew screen.
 */
suspend fun SupabaseService.fetchFriendsBAC(codes: List<String>): List<FriendProfile> {
    if (!isConfigured || codes.isEmpty()) return emptyList()
    val cleaned = codes.map(::sanitizeFriendCode).filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return emptyList()
    return cleaned.chunked(50).flatMap { batch ->
        decodeList<FriendProfile>(
            transport.restRPC("friend_profiles_by_codes", buildJsonObject {
                put("p_codes", jsonStrings(batch))
            })
        )
    }
}

/** Friend code of one user id, used to verify friends-only jam access. */
suspend fun SupabaseService.friendCode(userID: String): String? {
    if (userID.isEmpty() || !isUuid(userID)) return null
    val data = transport.restRPC("friend_profiles_by_ids", buildJsonObject {
        put("p_ids", jsonStrings(listOf(userID)))
    })
    return decodeList<FriendProfile>(data).firstOrNull()?.friendCode
}

/** Profiles for a set of user ids (mutual friends display). */
suspend fun SupabaseService.fetchProfiles(ids: List<String>): List<FriendProfile> {
    val valid = ids.filter(::isUuid)
    if (valid.isEmpty()) return emptyList()
    return valid.chunked(50).flatMap { batch ->
        decodeList<FriendProfile>(
            transport.restRPC("friend_profiles_by_ids", buildJsonObject {
                put("p_ids", jsonStrings(batch))
            })
        )
    }
}

suspend fun SupabaseService.addFriendship(friendID: String) {
    val me = requireUserId()
    if (!isUuid(friendID) || friendID == me) return
    transport.restPOST(
        "/rest/v1/friendships",
        buildJsonObject {
            put("follower_id", me)
            put("friend_id", friendID)
        },
        ignoreDuplicates = true
    )
}

suspend fun SupabaseService.removeFriendship(friendID: String) {
    val me = requireUserId()
    if (!isUuid(friendID)) return
    transport.restDELETE("/rest/v1/friendships?follower_id=eq.$me&friend_id=eq.$friendID")
}

/** User ids this person follows (their friend list). */
suspend fun SupabaseService.fetchFriendIDs(userID: String): List<String> {
    if (!isUuid(userID)) return emptyList()
    val raw = transport.restGET("/rest/v1/friendships?follower_id=eq.$userID&select=friend_id")
    val rows = supabaseJson.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
    return rows.mapNotNull {
        (it as? JsonObject)?.get("friend_id")?.let { v -> (v as? JsonPrimitive)?.content }
    }
}
