package de.tipau.promille.bac

/*
 * Jam session model. Dependency free so the roster merge stays unit testable:
 * it is the part that decides whose permille a jam member sees.
 */

enum class JamVisibility(val raw: String) {
    PROXIMITY_AND_CODE("Proximity + Code"),
    FRIENDS_ONLY("Nur Freunde"),
    CODE_ONLY("Nur per Code"),
    PROXIMITY_ONLY("Nur in der Nähe");

    val description: String
        get() = when (this) {
            PROXIMITY_AND_CODE -> "Sichtbar für alle in der Nähe und per Code"
            FRIENDS_ONLY -> "Nur deine Freunde können beitreten"
            CODE_ONLY -> "Niemand wird automatisch sehen, nur mit Code"
            PROXIMITY_ONLY -> "Funktioniert offline, nur per Bluetooth"
        }

    /** Proximity-only jams never touch the server. */
    val usesServer: Boolean get() = this != PROXIMITY_ONLY

    val usesProximity: Boolean
        get() = this == PROXIMITY_AND_CODE || this == PROXIMITY_ONLY

    companion object {
        fun from(raw: String): JamVisibility =
            entries.firstOrNull { it.raw == raw } ?: PROXIMITY_AND_CODE
    }
}

enum class JamConnectionType(val raw: String) {
    PROXIMITY("Proximity"),
    FRIEND("Freund"),
    CODE("Code");

    val label: String
        get() = when (this) {
            PROXIMITY -> "In der Nähe"
            FRIEND -> "Freund"
            CODE -> "Code"
        }

    companion object {
        fun from(raw: String?): JamConnectionType =
            entries.firstOrNull { it.raw == raw } ?: CODE
    }
}

data class JamSettings(
    val shareBAC: Boolean = true,
    val shareStatus: Boolean = true,
    val shareDrinks: Boolean = true,
    val shareDrinkCount: Boolean = true,
    val shareSOSStatus: Boolean = true,
    val sharePhotos: Boolean = true,
    val shareLocation: Boolean = false,
    val allowWaves: Boolean = true,
    val autoAcceptFriends: Boolean = true
)

/**
 * Splits the toggles into the values a member shares and the ones they hide,
 * mirroring ParticipantPrivacySheet (JamPrivacySheets.swift:75-97). Only the six
 * roster visible values: location, waves and auto accept are local preferences,
 * never values another member sees.
 */
fun JamSettings.privacyLabels(): Pair<List<String>, List<String>> {
    val flags = listOf(
        shareBAC to "Promille-Wert",
        shareStatus to "Status",
        shareDrinks to "Drinks",
        shareDrinkCount to "Anzahl Drinks",
        shareSOSStatus to "SOS-Status",
        sharePhotos to "Fotos"
    )
    return flags.filter { it.first }.map { it.second } to
        flags.filterNot { it.first }.map { it.second }
}

data class JamParticipant(
    val id: String,
    val userID: String? = null,
    val displayName: String,
    val avatar: String = "",
    val joinedAtEpochSeconds: Long = 0,
    val connectionType: JamConnectionType = JamConnectionType.CODE,
    val currentBAC: Double? = null,
    val currentStatus: String? = null,
    val hasSOSActive: Boolean = false,
    val lastUpdatedEpochSeconds: Long = 0,
    val sharedSettings: JamSettings? = null
)

data class Jam(
    val id: String,
    val code: String,
    val hostUserID: String,
    val hostName: String,
    val createdAtEpochSeconds: Long = 0,
    val visibility: JamVisibility = JamVisibility.PROXIMITY_AND_CODE,
    val settings: JamSettings = JamSettings(),
    val participants: List<JamParticipant> = emptyList()
)

/**
 * Last writer wins upsert: an incoming row only replaces an existing one when it
 * is at least as fresh. That makes the roster an LWW map, so a stale broadcast
 * arriving late after an offline reconnect cannot clobber a newer status and
 * merges come out the same no matter which side merges into which.
 */
fun List<JamParticipant>.upserting(participant: JamParticipant): List<JamParticipant> {
    val index = participant.userID
        ?.let { uid -> indexOfFirst { it.userID == uid }.takeIf { it >= 0 } }
        ?: indexOfFirst { it.id == participant.id }.takeIf { it >= 0 }

    if (index == null) return this + participant
    if (participant.lastUpdatedEpochSeconds < this[index].lastUpdatedEpochSeconds) return this
    return toMutableList().also { it[index] = participant }
}

/** Conflict free union of two rosters, used when two sides reconnect. */
fun List<JamParticipant>.merging(other: List<JamParticipant>): List<JamParticipant> =
    other.fold(this) { acc, p -> acc.upserting(p) }

/**
 * Deterministic host election, mirroring JamService.electHost.
 *
 * Every remaining client runs this and has to agree on the same new host with no
 * server round trip, so the order is total and never depends on iteration order:
 * lowest shared permille first, then the longest jam history, then a stable id
 * order. Someone hiding their value is treated as least suitable, so a member
 * known to be sober wins over one who might not be.
 */
fun electJamHost(
    participants: List<JamParticipant>,
    excludingUserID: String? = null
): JamParticipant? = participants
    .filter { excludingUserID == null || it.userID != excludingUserID }
    .minWithOrNull(
        compareBy<JamParticipant> { it.currentBAC ?: Double.MAX_VALUE }
            .thenBy { it.joinedAtEpochSeconds }
            .thenBy { it.id }
    )

/**
 * The roster the UI shows, from the rows the server returned.
 *
 * A row older than [STALE_PARTICIPANT_SECONDS] is someone whose phone stopped
 * reporting, and a tombstoned id is someone who just left or was kicked: the
 * poll would otherwise re-add them for the two minutes their row lingers. Our
 * own row is dropped from the server list and [me] appended instead, because the
 * local copy is always the fresher one.
 */
const val STALE_PARTICIPANT_SECONDS = 120L

fun activeJamRoster(
    serverRows: List<JamParticipant>,
    me: JamParticipant,
    myUserID: String?,
    tombstonedIDs: Set<String>,
    nowEpochSeconds: Long,
    existingParticipants: List<JamParticipant> = emptyList()
): List<JamParticipant> {
    val others = serverRows.filter { p ->
        p.id != me.id &&
            (myUserID == null || p.userID != myUserID) &&
            p.id !in tombstonedIDs &&
            nowEpochSeconds - p.lastUpdatedEpochSeconds <= STALE_PARTICIPANT_SECONDS
    }

    // Keep multipeer-only peers (proximity peers with no server row, e.g. a
    // discovered nearby jam that never had one, or an anonymous host):
    // rebuilding purely from server rows made them flicker in and out every
    // poll cycle. They persist regardless of age; only leave/kick drops them.
    val otherIDs = others.map { it.id }.toSet()
    val otherUserIDs = others.mapNotNull { it.userID }.toSet()
    val keptProximityPeers = existingParticipants.filter { p ->
        p.id != me.id &&
            p.connectionType == JamConnectionType.PROXIMITY &&
            p.id !in tombstonedIDs &&
            p.id !in otherIDs &&
            (p.userID == null || p.userID !in otherUserIDs)
    }
    return others + keptProximityPeers + me
}

/** True when our own row vanished from the server, which means the host kicked us. */
fun wasKickedFromJam(
    serverRows: List<JamParticipant>,
    myParticipantID: String,
    myUserID: String?
): Boolean = serverRows.none {
    it.id == myParticipantID || (myUserID != null && it.userID == myUserID)
}

/* Mini games. Shared over both transports, so every payload carries a stable id
 * that lets a draw arriving twice be shown once. */

data class WaterScore(val participantID: String, val name: String, val ms: Int)

enum class JamArcadeGame(val raw: String) {
    PERFECT_SECOND("perfectSecond"),
    BALANCE_BATTLE("balanceBattle"),
    REACTION_ROYALE("reactionRoyale");

    val title: String
        get() = when (this) {
            PERFECT_SECOND -> "Perfect Second"
            BALANCE_BATTLE -> "Balance Battle"
            REACTION_ROYALE -> "Reaction Royale"
        }

    val subtitle: String
        get() = when (this) {
            PERFECT_SECOND -> "Triff genau 5,000 Sekunden"
            BALANCE_BATTLE -> "Halte dein Handy möglichst ruhig"
            REACTION_ROYALE -> "Reagiere schnell, aber nicht zu früh"
        }

    companion object {
        fun from(raw: String?): JamArcadeGame? = entries.firstOrNull { it.raw == raw }
    }
}

data class JamRoulettePayload(
    val id: String,
    val jamID: String,
    val participants: List<String>,
    val winnerIndex: Int,
    val starterName: String,
    /** Null only for payloads produced by older app versions. */
    val starterID: String?
)

data class JamArcadeRoundPayload(
    val id: String,
    val jamID: String,
    val game: JamArcadeGame,
    val starterID: String,
    val starterName: String,
    val startAtEpochSeconds: Double,
    val signalAtEpochSeconds: Double?,
    val durationSeconds: Double
)

data class JamArcadeResultPayload(
    val jamID: String,
    val roundID: String,
    val participantID: String,
    val participantName: String,
    val value: Double,
    val disqualified: Boolean,
    val submittedAtEpochSeconds: Double
)

/**
 * The server is authoritative for everyone who has a row there, so a host reset
 * reaches every client. Our own time is the exception: a poll racing our own
 * submit would otherwise flash the stale, worse value back on screen, so for our
 * own entry the better (lower) time wins.
 */
fun mergeWaterScores(
    local: List<WaterScore>,
    server: List<WaterScore>,
    myParticipantID: String
): List<WaterScore> {
    val merged = server.associateBy { it.participantID }.toMutableMap()
    val mine = local.firstOrNull { it.participantID == myParticipantID }
    if (mine != null) {
        val onServer = merged[myParticipantID]
        if (onServer == null || mine.ms < onServer.ms) merged[myParticipantID] = mine
    }
    return merged.values.sortedWith(compareBy({ it.ms }, { it.participantID }))
}

/* Arcade scoring. Kept here so every number a round is judged on is the same
 * one iOS computes, and so it can be checked without a phone in hand. */

/** Perfect Second: how far off the target you stopped, in milliseconds. */
fun perfectSecondErrorMs(elapsedSeconds: Double, targetSeconds: Double): Double =
    kotlin.math.abs(elapsedSeconds - targetSeconds) * 1000.0

/**
 * Reaction Royale. Tapping before the signal is a false start, not a very good
 * time, so it is reported as disqualified rather than as a negative value.
 */
data class ReactionResult(val milliseconds: Double, val falseStart: Boolean)

fun reactionResult(tapAtEpochSeconds: Double, signalAtEpochSeconds: Double): ReactionResult =
    if (tapAtEpochSeconds < signalAtEpochSeconds) {
        ReactionResult(0.0, falseStart = true)
    } else {
        ReactionResult((tapAtEpochSeconds - signalAtEpochSeconds) * 1000.0, falseStart = false)
    }

/**
 * Balance Battle. Accumulates how far the phone tilted, sampled while the round
 * runs; the score is the mean deviation, so lower is steadier. A round with no
 * samples at all (no sensor) scores the worst possible value rather than a
 * perfect zero, which would otherwise hand the win to a phone that never moved
 * because it never measured.
 */
class BalanceAccumulator(private val tiltNormalizer: Double = 0.45) {

    private var sum = 0.0
    private var samples = 0

    /** Roll and pitch in radians, as the platform reports them. */
    fun record(rollRadians: Double, pitchRadians: Double) {
        val r = (rollRadians / tiltNormalizer).coerceIn(-1.0, 1.0)
        val p = (pitchRadians / tiltNormalizer).coerceIn(-1.0, 1.0)
        sum += kotlin.math.sqrt(r * r + p * p)
        samples += 1
    }

    fun score(): Double = if (samples > 0) sum / samples * 100.0 else 100.0

    fun reset() {
        sum = 0.0
        samples = 0
    }
}

/** Best first. A false start always loses, and an exact tie goes to whoever was quicker to submit. */
fun orderedArcadeResults(results: List<JamArcadeResultPayload>): List<JamArcadeResultPayload> =
    results.sortedWith(
        compareBy<JamArcadeResultPayload> { it.disqualified }
            .thenBy { it.value }
            .thenBy { it.submittedAtEpochSeconds }
    )
