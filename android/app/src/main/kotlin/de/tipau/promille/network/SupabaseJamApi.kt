package de.tipau.promille.network

import de.tipau.promille.bac.Jam
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamArcadeResultPayload
import de.tipau.promille.bac.JamArcadeRoundPayload
import de.tipau.promille.bac.JamConnectionType
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamRoulettePayload
import de.tipau.promille.bac.JamSettings
import de.tipau.promille.bac.JamVisibility
import de.tipau.promille.bac.WaterScore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/*
 * The online half of a jam. Discovery, join and the mini games all go through
 * SECURITY DEFINER RPCs that verify membership server-side, because jams and
 * jam_participants are not directly selectable: the exact code or an existing
 * membership is what authorises a read, so nothing can be enumerated.
 * See supabase/jams_security.sql and supabase/jam_games.sql.
 */

@Serializable
internal data class JamSettingsRow(
    val shareBAC: Boolean = true,
    val shareStatus: Boolean = true,
    val shareDrinks: Boolean = true,
    val shareDrinkCount: Boolean = true,
    val shareSOSStatus: Boolean = true,
    val sharePhotos: Boolean = true,
    val shareLocation: Boolean = false,
    val allowWaves: Boolean = true,
    val autoAcceptFriends: Boolean = true
) {
    fun toSettings() = JamSettings(
        shareBAC, shareStatus, shareDrinks, shareDrinkCount, shareSOSStatus,
        sharePhotos, shareLocation, allowWaves, autoAcceptFriends
    )

    companion object {
        fun from(s: JamSettings) = JamSettingsRow(
            s.shareBAC, s.shareStatus, s.shareDrinks, s.shareDrinkCount, s.shareSOSStatus,
            s.sharePhotos, s.shareLocation, s.allowWaves, s.autoAcceptFriends
        )
    }
}

@Serializable
internal data class JamRow(
    val id: String,
    val code: String = "",
    @SerialName("host_user_id") val hostUserID: String? = null,
    @SerialName("host_name") val hostName: String? = null,
    val visibility: String? = null,
    val settings: JamSettingsRow? = null,
    @SerialName("created_at") val createdAtRaw: String? = null
) {
    fun toJam() = Jam(
        id = id.lowercase(),
        code = code,
        hostUserID = hostUserID ?: "",
        hostName = hostName ?: "Anonym",
        createdAtEpochSeconds = (Timestamps.parse(createdAtRaw) ?: 0.0).toLong(),
        // An unknown visibility must not widen access, so it falls back to code only.
        visibility = visibility?.let { raw ->
            JamVisibility.entries.firstOrNull { it.raw == raw }
        } ?: JamVisibility.CODE_ONLY,
        settings = settings?.toSettings() ?: JamSettings(),
        participants = emptyList()
    )
}

@Serializable
internal data class JamParticipantRow(
    val id: String,
    @SerialName("user_id") val userID: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("connection_type") val connectionType: String? = null,
    @SerialName("current_bac") val currentBAC: Double? = null,
    @SerialName("current_status") val currentStatus: String? = null,
    @SerialName("has_sos_active") val hasSOSActive: Boolean? = null,
    @SerialName("joined_at") val joinedAtRaw: String? = null,
    @SerialName("last_updated") val lastUpdatedRaw: String? = null
) {
    fun toParticipant(nowEpochSeconds: Long): JamParticipant {
        val name = displayName ?: "Anonym"
        return JamParticipant(
            id = id.lowercase(),
            userID = userID,
            displayName = name,
            avatar = name.take(1).uppercase(),
            joinedAtEpochSeconds = (Timestamps.parse(joinedAtRaw) ?: 0.0).toLong()
                .takeIf { it > 0 } ?: nowEpochSeconds,
            connectionType = JamConnectionType.from(connectionType),
            currentBAC = currentBAC,
            currentStatus = currentStatus,
            hasSOSActive = hasSOSActive ?: false,
            lastUpdatedEpochSeconds = (Timestamps.parse(lastUpdatedRaw) ?: 0.0).toLong()
                .takeIf { it > 0 } ?: nowEpochSeconds,
            sharedSettings = null
        )
    }
}

@Serializable
internal data class JamWaterScoreRow(
    @SerialName("participant_id") val participantID: String,
    val name: String? = null,
    val ms: Int = 0
) {
    fun toScore() = WaterScore(participantID.lowercase(), name ?: "Anonym", ms)
}

@Serializable
internal data class JamRouletteRow(
    @SerialName("draw_id") val drawID: String,
    val participants: List<String> = emptyList(),
    @SerialName("winner_index") val winnerIndex: Int = 0,
    @SerialName("starter_name") val starterName: String? = null,
    @SerialName("starter_id") val starterID: String? = null
) {
    fun toPayload(jamID: String) = JamRoulettePayload(
        id = drawID.lowercase(),
        jamID = jamID,
        participants = participants,
        winnerIndex = winnerIndex,
        starterName = starterName ?: "Jemand",
        starterID = starterID?.lowercase()
    )
}

@Serializable
internal data class JamArcadeRoundRow(
    @SerialName("round_id") val roundID: String,
    @SerialName("game_type") val gameType: String = "",
    @SerialName("starter_id") val starterID: String = "",
    @SerialName("starter_name") val starterName: String? = null,
    @SerialName("start_at") val startAtRaw: String? = null,
    @SerialName("signal_at") val signalAtRaw: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0
) {
    /** Null for a row whose game type this build does not know. */
    fun toPayload(jamID: String): JamArcadeRoundPayload? {
        val game = JamArcadeGame.from(gameType) ?: return null
        return JamArcadeRoundPayload(
            id = roundID.lowercase(),
            jamID = jamID,
            game = game,
            starterID = starterID.lowercase(),
            starterName = starterName ?: "Jemand",
            startAtEpochSeconds = Timestamps.parse(startAtRaw) ?: 0.0,
            signalAtEpochSeconds = Timestamps.parse(signalAtRaw),
            durationSeconds = durationSeconds
        )
    }
}

@Serializable
internal data class JamArcadeResultRow(
    @SerialName("participant_id") val participantID: String,
    @SerialName("participant_name") val participantName: String? = null,
    val value: Double = 0.0,
    val disqualified: Boolean = false,
    @SerialName("submitted_at") val submittedAtRaw: String? = null
) {
    fun toPayload(jamID: String, roundID: String) = JamArcadeResultPayload(
        jamID = jamID,
        roundID = roundID,
        participantID = participantID.lowercase(),
        participantName = participantName ?: "Anonym",
        value = value,
        disqualified = disqualified,
        submittedAtEpochSeconds = Timestamps.parse(submittedAtRaw) ?: 0.0
    )
}

private fun nullableDouble(value: Double?): JsonElement =
    if (value == null) JsonNull else JsonPrimitive(value)

private fun nullableString(value: String?): JsonElement =
    if (value == null) JsonNull else JsonPrimitive(value)

// MARK: Jam lifecycle

suspend fun SupabaseService.publishJam(jam: Jam) {
    val me = requireUserId()
    transport.restPOST("/rest/v1/jams", buildJsonObject {
        put("id", jam.id)
        put("code", jam.code)
        put("host_user_id", me)
        put("host_name", jam.hostName)
        put("visibility", jam.visibility.raw)
        put("settings", supabaseJson.encodeToJsonElement(JamSettingsRow.from(jam.settings)))
    })
    val mine = jam.participants.firstOrNull { it.userID == me }
    try {
        transport.restPOST(
            "/rest/v1/jam_participants?on_conflict=jam_id,user_id",
            buildJsonObject {
                put("id", mine?.id ?: java.util.UUID.randomUUID().toString())
                put("jam_id", jam.id)
                put("user_id", me)
                put("display_name", myProfile.value?.displayName ?: jam.hostName)
                put("connection_type", JamConnectionType.CODE.raw)
                put("has_sos_active", false)
                put("last_updated", Timestamps.format(nowSeconds()))
                put("current_bac", nullableDouble(mine?.currentBAC))
            },
            ignoreDuplicates = true
        )
    } catch (e: Exception) {
        val msg = e.message.orEmpty()
        if (msg.contains("409") || msg.contains("jam_participants_one_row_per_user")) {
            runCatching {
                transport.restPATCH(
                    "/rest/v1/jam_participants?jam_id=eq.${jam.id}&user_id=eq.$me",
                    buildJsonObject {
                        mine?.id?.let { put("id", it) }
                        put("display_name", myProfile.value?.displayName ?: jam.hostName)
                        put("connection_type", JamConnectionType.CODE.raw)
                        put("last_updated", Timestamps.format(nowSeconds()))
                        put("current_bac", nullableDouble(mine?.currentBAC))
                    }
                )
            }
        } else {
            throw e
        }
    }
}

suspend fun SupabaseService.findJamByCode(code: String): Jam? {
    val clean = sanitizeFriendCode(code)
    if (clean.isEmpty()) return null
    val rows = decodeList<JamRow>(
        transport.restRPC("jam_by_code", buildJsonObject { put("p_code", clean) })
    )
    return rows.firstOrNull()?.toJam()
}

suspend fun SupabaseService.joinJam(
    jamID: String,
    participantID: String,
    initialBAC: Double?,
    connectionType: String = JamConnectionType.CODE.raw
) {
    val me = requireUserId()
    try {
        transport.restPOST(
            "/rest/v1/jam_participants?on_conflict=jam_id,user_id",
            buildJsonObject {
                put("id", participantID)
                put("jam_id", jamID)
                put("user_id", me)
                put("display_name", myProfile.value?.displayName ?: "Anonym")
                put("connection_type", connectionType)
                put("has_sos_active", false)
                put("last_updated", Timestamps.format(nowSeconds()))
                put("current_bac", nullableDouble(initialBAC))
            },
            ignoreDuplicates = true
        )
    } catch (e: Exception) {
        val msg = e.message.orEmpty()
        if (msg.contains("409") || msg.contains("jam_participants_one_row_per_user")) {
            runCatching {
                transport.restPATCH(
                    "/rest/v1/jam_participants?jam_id=eq.$jamID&user_id=eq.$me",
                    buildJsonObject {
                        put("id", participantID)
                        put("display_name", myProfile.value?.displayName ?: "Anonym")
                        put("connection_type", connectionType)
                        put("last_updated", Timestamps.format(nowSeconds()))
                        put("current_bac", nullableDouble(initialBAC))
                    }
                )
            }
        } else {
            throw e
        }
    }
}

suspend fun SupabaseService.leaveJam(jamID: String) {
    val me = requireUserId()
    transport.restDELETE("/rest/v1/jam_participants?jam_id=eq.$jamID&user_id=eq.$me")
}

/** Host only. The database cascade removes the participants. */
suspend fun SupabaseService.deleteJam(jamID: String) {
    transport.restDELETE("/rest/v1/jams?id=eq.$jamID")
}

/** Host initiated kick, allowed by the jam_participants_delete_self_or_host policy. */
suspend fun SupabaseService.removeParticipant(jamID: String, participantID: String) {
    transport.restDELETE("/rest/v1/jam_participants?jam_id=eq.$jamID&id=eq.$participantID")
}

/**
 * Hands the host role to another user. Needs the jams_update_host RLS policy;
 * without it the PATCH is rejected and the caller keeps the local host change.
 */
suspend fun SupabaseService.updateJamHost(jamID: String, hostUserID: String, hostName: String) {
    transport.restPATCH("/rest/v1/jams?id=eq.$jamID", buildJsonObject {
        put("host_user_id", hostUserID)
        put("host_name", hostName)
    })
}

suspend fun SupabaseService.updateMyJamStatus(
    jamID: String,
    bac: Double?,
    status: String?,
    sosActive: Boolean
) {
    val me = requireUserId()
    transport.restPATCH(
        "/rest/v1/jam_participants?jam_id=eq.$jamID&user_id=eq.$me",
        buildJsonObject {
            put("has_sos_active", sosActive)
            put("last_updated", Timestamps.format(nowSeconds()))
            put("current_bac", nullableDouble(bac))
            put("current_status", nullableString(status))
        }
    )
}

suspend fun SupabaseService.fetchJamParticipants(jamID: String): List<JamParticipant> {
    val now = nowSeconds().toLong()
    return decodeList<JamParticipantRow>(
        transport.restRPC("jam_participants_for_member", buildJsonObject {
            put("p_jam_id", jamID)
        })
    ).map { it.toParticipant(now) }
}

/**
 * Only jams hosted by one of the locally stored friends. The codes resolve to
 * host ids first: without that filter every signed-in user would see every
 * friends-only jam worldwide. friend_jams already leaves out the caller's own.
 */
suspend fun SupabaseService.fetchFriendJams(friendCodes: List<String>): List<Jam> {
    if (userId == null) return emptyList()
    val cleaned = friendCodes.map(::sanitizeFriendCode).filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return emptyList()

    val hostIDs = mutableListOf<String>()
    val missingCodes = mutableListOf<String>()

    for (code in cleaned) {
        val cached = friendCodeToHostIDCache[code]
        if (cached != null) {
            hostIDs.add(cached)
        } else {
            missingCodes.add(code)
        }
    }

    if (missingCodes.isNotEmpty()) {
        val fetched = decodeList<FriendProfile>(
            transport.restRPC("friend_profiles_by_codes", buildJsonObject {
                put("p_codes", JsonArray(missingCodes.map { JsonPrimitive(it) }))
            })
        )
        for (p in fetched) {
            val code = sanitizeFriendCode(p.friendCode)
            if (code.isNotEmpty()) {
                friendCodeToHostIDCache[code] = p.id
            }
            hostIDs.add(p.id)
        }
    }

    val validHostIDs = hostIDs.filter { runCatching { java.util.UUID.fromString(it) }.isSuccess }
    if (validHostIDs.isEmpty()) return emptyList()

    return decodeList<JamRow>(
        transport.restRPC("friend_jams", buildJsonObject {
            put("p_host_ids", JsonArray(validHostIDs.map { JsonPrimitive(it) }))
        })
    ).map { it.toJam() }
}

// MARK: Invitations

@Serializable
data class PendingJamInvite(
    val id: String = "",
    @SerialName("inviter_code") val inviterCode: String = "",
    @SerialName("jam_id") val jamID: String = "",
    @SerialName("jam_code") val jamCode: String = "",
    @SerialName("host_name") val hostName: String = "",
    @SerialName("created_at") val createdAtRaw: String? = null
)

/** Fire and forget: a failed invite must not break hosting the jam. */
suspend fun SupabaseService.sendJamInvitation(
    inviteeCode: String,
    jamID: String,
    jamCode: String,
    hostName: String
) {
    if (!isConfigured || userId == null) return
    val clean = sanitizeFriendCode(inviteeCode)
    if (clean.isEmpty()) return
    runCatching {
        transport.restRPC("send_jam_invitation", buildJsonObject {
            put("p_invitee_code", clean)
            put("p_jam_id", jamID.lowercase())
            put("p_jam_code", jamCode)
            put("p_host_name", hostName)
        })
    }
}

suspend fun SupabaseService.fetchMyJamInvitations(): List<PendingJamInvite> {
    if (!isConfigured || userId == null) return emptyList()
    return decodeList(transport.restRPC("my_jam_invitations", JsonObject(emptyMap())))
}

suspend fun SupabaseService.markInvitationSeen(id: String) {
    if (!isConfigured || userId == null) return
    runCatching {
        transport.restRPC("mark_invitation_seen", buildJsonObject { put("p_id", id.lowercase()) })
    }
}

// MARK: Mini games
//
// The participant id is derived server-side from the caller's membership, so it
// is never sent from here: a client supplied id would be spoofable.

suspend fun SupabaseService.submitJamWaterTime(jamID: String, name: String, ms: Int) {
    transport.restRPC("jam_submit_water", buildJsonObject {
        put("p_jam_id", jamID)
        put("p_name", name)
        put("p_ms", ms)
    })
}

suspend fun SupabaseService.resetJamWater(jamID: String) {
    transport.restRPC("jam_reset_water", buildJsonObject { put("p_jam_id", jamID) })
}

suspend fun SupabaseService.fetchJamWaterScores(jamID: String): List<WaterScore> =
    decodeList<JamWaterScoreRow>(
        transport.restRPC("jam_water_board", buildJsonObject { put("p_jam_id", jamID) })
    ).map { it.toScore() }

suspend fun SupabaseService.setJamRoulette(payload: JamRoulettePayload) {
    val starter = payload.starterID ?: return
    transport.restRPC("jam_set_roulette", buildJsonObject {
        put("p_jam_id", payload.jamID)
        put("p_draw_id", payload.id)
        put("p_participants", JsonArray(payload.participants.map { JsonPrimitive(it) }))
        put("p_winner_index", payload.winnerIndex)
        put("p_starter_name", payload.starterName)
        put("p_starter_id", starter)
    })
}

suspend fun SupabaseService.fetchJamRoulette(jamID: String): JamRoulettePayload? =
    decodeList<JamRouletteRow>(
        transport.restRPC("jam_roulette", buildJsonObject { put("p_jam_id", jamID) })
    ).firstOrNull()?.toPayload(jamID)

suspend fun SupabaseService.setJamArcadeRound(round: JamArcadeRoundPayload) {
    transport.restRPC("jam_set_arcade_round", buildJsonObject {
        put("p_jam_id", round.jamID)
        put("p_round_id", round.id)
        put("p_game_type", round.game.raw)
        put("p_starter_id", round.starterID)
        put("p_starter_name", round.starterName)
        put("p_start_at", Timestamps.format(round.startAtEpochSeconds))
        put("p_signal_at", nullableString(round.signalAtEpochSeconds?.let(Timestamps::format)))
        put("p_duration_seconds", round.durationSeconds)
    })
}

suspend fun SupabaseService.fetchJamArcadeRound(jamID: String): JamArcadeRoundPayload? =
    decodeList<JamArcadeRoundRow>(
        transport.restRPC("jam_arcade_round", buildJsonObject { put("p_jam_id", jamID) })
    ).firstNotNullOfOrNull { it.toPayload(jamID) }

suspend fun SupabaseService.submitJamArcadeResult(result: JamArcadeResultPayload) {
    transport.restRPC("jam_submit_arcade_result", buildJsonObject {
        put("p_jam_id", result.jamID)
        put("p_round_id", result.roundID)
        put("p_name", result.participantName)
        put("p_value", result.value)
        put("p_disqualified", result.disqualified)
    })
}

suspend fun SupabaseService.fetchJamArcadeResults(
    jamID: String,
    roundID: String
): List<JamArcadeResultPayload> = decodeList<JamArcadeResultRow>(
    transport.restRPC("jam_arcade_board", buildJsonObject {
        put("p_jam_id", jamID)
        put("p_round_id", roundID)
    })
).map { it.toPayload(jamID, roundID) }
