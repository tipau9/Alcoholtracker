package de.tipau.promille.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Moderation dashboard. Every call is a SECURITY DEFINER RPC that checks the
 * caller's role server-side, so the client never decides who is an admin: it
 * only asks, and a non-admin simply gets an error back.
 */

@Serializable
data class AdminMetric(val metric: String = "", val value: Int = 0)

@Serializable
data class AdminQueueItem(
    @SerialName("item_type") val itemType: String = "",
    val id: String,
    val title: String = "",
    val subtitle: String = "",
    val status: String = "",
    @SerialName("confirmed_count") val confirmedCount: Int = 0,
    @SerialName("created_at") val createdAtRaw: String? = null,
    val payload: JsonElement = JsonNull
)

@Serializable
data class AdminReport(
    val id: String,
    @SerialName("item_type") val itemType: String = "",
    @SerialName("item_id") val itemID: String? = null,
    val reason: String = "",
    val status: String = "",
    val details: JsonElement = JsonNull,
    @SerialName("created_at") val createdAtRaw: String? = null
)

@Serializable
data class AdminFeatureFlag(
    val key: String,
    val enabled: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
    val value: JsonElement = JsonNull,
    val description: String = "",
    @SerialName("updated_at") val updatedAtRaw: String? = null
)

@Serializable
data class PublicFeatureFlag(val key: String, val value: JsonElement = JsonNull)

@Serializable
data class AdminAuditEntry(
    val id: String,
    @SerialName("actor_id") val actorID: String? = null,
    val action: String = "",
    @SerialName("item_type") val itemType: String? = null,
    @SerialName("item_id") val itemID: String? = null,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAtRaw: String? = null
)

@Serializable
data class AdminUserRole(
    @SerialName("user_id") val userID: String,
    val role: String = "",
    @SerialName("created_at") val createdAtRaw: String? = null
)

@Serializable
data class AdminBlockedVoter(
    val voter: String,
    val reason: String = "",
    @SerialName("created_at") val createdAtRaw: String? = null
)

private val EMPTY = JsonObject(emptyMap())

private fun nullable(value: String?): JsonElement =
    if (value == null) JsonNull else JsonPrimitive(value)

suspend fun SupabaseService.refreshAdminStatus() {
    setAdminFlag(
        if (!isConfigured || userId == null) false
        else transport.restRPC("is_admin", EMPTY).trim().toBooleanStrictOrNull() ?: false
    )
}

suspend fun SupabaseService.fetchAdminMetrics(): List<AdminMetric> =
    decodeList(transport.restRPC("admin_metrics", EMPTY))

suspend fun SupabaseService.fetchAdminQueue(): List<AdminQueueItem> =
    decodeList(transport.restRPC("admin_moderation_queue", EMPTY))

suspend fun SupabaseService.fetchAdminContent(
    status: String? = "approved",
    search: String = "",
    limit: Int = 200,
    offset: Int = 0
): List<AdminQueueItem> = decodeList(
    transport.restRPC("admin_content_list", buildJsonObject {
        put("p_status", nullable(status))
        put("p_search", search)
        put("p_limit", limit)
        put("p_offset", offset)
    })
)

suspend fun SupabaseService.setAdminModerationStatus(
    itemType: String,
    id: String,
    status: String,
    reason: String? = null
) {
    transport.restRPC("admin_set_moderation_status", buildJsonObject {
        put("p_item_type", itemType)
        put("p_id", id.lowercase())
        put("p_status", status)
        put("p_reason", nullable(reason))
    })
}

suspend fun SupabaseService.updateAdminDrink(
    id: String,
    name: String,
    category: String,
    volume: Double,
    abv: Double,
    calories: Int,
    iconName: String?
) {
    transport.restRPC("admin_update_drink", buildJsonObject {
        put("p_id", id.lowercase())
        put("p_name", name)
        put("p_category", category)
        put("p_volume", volume)
        put("p_abv", abv)
        put("p_calories", calories)
        put("p_icon_name", nullable(iconName))
    })
}

suspend fun SupabaseService.updateAdminMix(
    id: String,
    name: String,
    ingredients: JsonElement,
    totalVolume: Double,
    totalABV: Double,
    calories: Int
) {
    transport.restRPC("admin_update_mix", buildJsonObject {
        put("p_id", id.lowercase())
        put("p_name", name)
        put("p_ingredients", ingredients)
        put("p_total_volume", totalVolume)
        put("p_total_abv", totalABV)
        put("p_calories", calories)
    })
}

suspend fun SupabaseService.fetchAdminReports(): List<AdminReport> =
    decodeList(transport.restRPC("admin_reports_list", EMPTY))

suspend fun SupabaseService.resolveAdminReport(id: String, status: String, note: String? = null) {
    transport.restRPC("admin_resolve_report", buildJsonObject {
        put("p_id", id.lowercase())
        put("p_status", status)
        put("p_note", nullable(note))
    })
}

suspend fun SupabaseService.submitReport(
    itemType: String,
    itemID: String? = null,
    reason: String,
    details: JsonObject = EMPTY
) {
    transport.restRPC("submit_report", buildJsonObject {
        put("p_item_type", itemType)
        put("p_reason", reason)
        put("p_item_id", nullable(itemID?.lowercase()))
        put("p_details", details)
    })
}

suspend fun SupabaseService.fetchAdminFeatureFlags(): List<AdminFeatureFlag> =
    decodeList(transport.restRPC("admin_feature_flags_list", EMPTY))

/**
 * The value column is jsonb. A blank input means "no payload", valid JSON is
 * passed through, and anything else is wrapped as {"value": ...} rather than
 * rejected, which is what the dashboard form expects.
 */
suspend fun SupabaseService.setAdminFeatureFlag(
    key: String,
    enabled: Boolean,
    isPublic: Boolean,
    value: String,
    description: String
) {
    val jsonValue: JsonElement = when {
        value.isBlank() -> EMPTY
        else -> runCatching { supabaseJson.parseToJsonElement(value) }
            .getOrElse { buildJsonObject { put("value", value) } }
    }
    transport.restRPC("admin_set_feature_flag", buildJsonObject {
        put("p_key", key)
        put("p_enabled", enabled)
        put("p_value", jsonValue)
        put("p_description", description)
        put("p_is_public", isPublic)
    })
}

suspend fun SupabaseService.fetchPublicFeatureFlags(): List<PublicFeatureFlag> {
    if (!isConfigured) throw SupabaseError.NotConfigured
    return decodeList(transport.publicRPC("public_feature_flags", EMPTY))
}

suspend fun SupabaseService.fetchAdminAuditLog(): List<AdminAuditEntry> =
    decodeList(transport.restRPC("admin_audit_log_list", EMPTY))

suspend fun SupabaseService.fetchAdminUsers(): List<AdminUserRole> =
    decodeList(transport.restRPC("admin_users_list", EMPTY))

suspend fun SupabaseService.setAdminUserRole(userID: String, role: String) {
    transport.restRPC("admin_set_user_role", buildJsonObject {
        put("p_user_id", userID)
        put("p_role", role)
    })
}

suspend fun SupabaseService.fetchAdminBlockedVoters(): List<AdminBlockedVoter> =
    decodeList(transport.restRPC("admin_blocked_voters_list", EMPTY))

suspend fun SupabaseService.setAdminVoterBlock(voter: String, blocked: Boolean, reason: String) {
    transport.restRPC("admin_set_voter_block", buildJsonObject {
        put("p_voter", voter)
        put("p_blocked", blocked)
        put("p_reason", reason)
    })
}
