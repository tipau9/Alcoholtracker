package de.tipau.promille.service

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import de.tipau.promille.bac.Jam
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamArcadeResultPayload
import de.tipau.promille.bac.JamArcadeRoundPayload
import de.tipau.promille.bac.JamConnectionType
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamRoulettePayload
import de.tipau.promille.bac.JamSettings
import de.tipau.promille.bac.JamVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

/**
 * Largest jpeg that still fits a Nearby BYTES payload once base64 has inflated
 * it by 4/3 and the JSON envelope has been wrapped around it:
 * 22 KB -> ~29.3 KB encoded, under the 32768 byte ceiling with room for the
 * message id and the sender name.
 */
const val MAX_PHOTO_BYTES = 22_000

/**
 * Port of iOS MultipeerService. MultipeerConnectivity has no Android
 * equivalent, so this rides Nearby Connections (P2P_CLUSTER - many-to-many,
 * the same shape as Multipeer's star/mesh) instead; everything above the
 * transport (envelope mesh relay with messageID/ttl, the callback surface,
 * the jam-gated connection handshake) mirrors MultipeerService.swift 1:1.
 *
 * Requesting the various Bluetooth/Wi-Fi runtime permissions is a
 * Compose-level concern, same split as LocationService; every entry point
 * here just no-ops behind a try/catch if a permission is missing rather than
 * crashing.
 *
 * Nearby has no MultipeerConnectivity-style discoveryInfo dictionary, so jam
 * metadata rides the endpoint name as a delimited string instead
 * ("jamID<SEP>code<SEP>hostName" when advertising, "deviceName<SEP>jamID"
 * when requesting a connection - the latter is the analog of MC's `context`
 * data, checked against [activeJamID] before a host accepts).
 */
class MultipeerService(private val context: Context) {

    enum class ControlAction(val raw: String) { LEAVE("leave"), KICK("kick"), TRANSFER_HOST("transferHost") }

    data class JamControl(
        val action: ControlAction,
        val jamID: String,
        val participantID: String,
        val userID: String? = null,
        val newHostName: String? = null
    )

    data class JamStatusBroadcast(val jamID: String, val participant: JamParticipant)

    data class JamPhotoPayload(val senderName: String, val jpeg: ByteArray, val senderBAC: Double? = null)

    enum class WaterKind { RESULT, RESET }
    data class WaterPayload(
        val jamID: String,
        val kind: WaterKind,
        val participantID: String,
        val name: String,
        val milliseconds: Int
    )

    var onStatusReceived: ((JamStatusBroadcast) -> Unit)? = null
    var onJamFound: ((Jam) -> Unit)? = null
    var onPhotoReceived: ((JamPhotoPayload) -> Unit)? = null
    var onControlReceived: ((JamControl) -> Unit)? = null
    var onRouletteReceived: ((JamRoulettePayload) -> Unit)? = null
    var onWaterReceived: ((WaterPayload) -> Unit)? = null
    var onArcadeRoundReceived: ((JamArcadeRoundPayload) -> Unit)? = null
    var onArcadeResultReceived: ((JamArcadeResultPayload) -> Unit)? = null

    /** Gates both accepting inbound connections and auto-connecting on discovery. */
    var activeJamID: String? = null

    private val _discoveredJams = MutableStateFlow<List<Jam>>(emptyList())
    val discoveredJams: StateFlow<List<Jam>> = _discoveredJams

    val hasConnectedPeers: Boolean get() = connectedEndpoints.isNotEmpty()

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val myDeviceName: String = Build.MODEL ?: "Gerät"

    private val connectedEndpoints = mutableSetOf<String>()

    // endpointId -> the jamID it advertised, so a later connectToJam() call
    // (the explicit analog of iOS auto-inviting inside foundPeer) knows which
    // endpoint to dial without a fresh discovery event.
    private val discoveredEndpoints = mutableMapOf<String, String>()

    private var advertising = false
    private var discovering = false

    // Bounded recently-seen mesh message ids, so a message is forwarded and
    // delivered at most once. LinkedHashSet keeps both O(1) membership and
    // insertion order for the eviction below.
    private val seenIDs = LinkedHashSet<String>()
    private fun hasSeen(id: String) = id in seenIDs
    private fun rememberSeen(id: String) {
        if (!seenIDs.add(id)) return
        if (seenIDs.size > MAX_SEEN) seenIDs.remove(seenIDs.first())
    }

    private val json = Json { ignoreUnknownKeys = true }

    // MARK: Host mode

    fun startAdvertisingJam(jam: Jam) {
        activeJamID = jam.id
        stopAdvertising()
        val name = listOf(jam.id, jam.code, jam.hostName).joinToString(SEP)
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        runCatching {
            client.startAdvertising(name, SERVICE_ID, connectionLifecycleCallback, options)
            advertising = true
        }
        startBrowsing()
    }

    fun resetAndStartAdvertising(jam: Jam) {
        stopAll()
        startAdvertisingJam(jam)
    }

    // MARK: Browser mode (joining)

    // Resets discoveredJams/discoveredEndpoints, so calling this while a
    // discovery session is already live drops whatever it had found so far.
    fun startBrowsing() {
        _discoveredJams.value = emptyList()
        discoveredEndpoints.clear()
        stopBrowsing()
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        runCatching {
            client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            discovering = true
        }
    }

    fun stopBrowsing() {
        if (discovering) runCatching { client.stopDiscovery() }
        discovering = false
    }

    fun stopAdvertising() {
        if (advertising) runCatching { client.stopAdvertising() }
        advertising = false
    }

    fun stopAll() {
        stopAdvertising()
        stopBrowsing()
        runCatching { client.stopAllEndpoints() }
        connectedEndpoints.clear()
        discoveredEndpoints.clear()
        _discoveredJams.value = emptyList()
        activeJamID = null
    }

    /**
     * Connects to an already-discovered jam. Nearby fires onEndpointFound once
     * per discovery, not on every poll like MultipeerConnectivity effectively
     * does while browsing, so picking a jam the user saw a moment ago needs an
     * explicit dial instead of relying on a fresh callback.
     */
    fun connectToJam(jamID: String) {
        activeJamID = jamID
        val endpointId = discoveredEndpoints.entries.firstOrNull { it.value == jamID }?.key ?: return
        val name = listOf(myDeviceName, jamID).joinToString(SEP)
        runCatching { client.requestConnection(name, endpointId, connectionLifecycleCallback) }
    }

    // MARK: Data send

    fun broadcastParticipant(participant: JamParticipant, jamID: String) =
        send(WireEnvelope(kind = "status", status = WireStatus(jamID, WireParticipant.from(participant))))

    /** The host relays it on, so it reaches every member of the mesh. */
    fun broadcastControl(control: JamControl) =
        send(WireEnvelope(kind = "control", control = WireControlDto.from(control)))

    fun broadcastRoulette(payload: JamRoulettePayload) =
        send(WireEnvelope(kind = "roulette", roulette = WireRoulette.from(payload)))

    fun broadcastWater(payload: WaterPayload) =
        send(WireEnvelope(kind = "water", water = WireWaterDto.from(payload)))

    fun broadcastArcadeRound(payload: JamArcadeRoundPayload) =
        send(WireEnvelope(kind = "arcadeRound", arcadeRound = WireArcadeRound.from(payload)))

    fun broadcastArcadeResult(payload: JamArcadeResultPayload) =
        send(WireEnvelope(kind = "arcadeResult", arcadeResult = WireArcadeResult.from(payload)))

    /**
     * jpeg must already be compressed/resized by the caller, and it must fit
     * MAX_PHOTO_BYTES - Nearby refuses a BYTES payload over 32 KB
     * (Connections.MAX_BYTES_DATA_SIZE) and sendPayload swallows the refusal
     * in runCatching, so anything larger vanishes without a trace. iOS gets to
     * send 200 KB because MultipeerConnectivity streams; matching that here
     * means moving photos to Payload.fromStream, which needs its own
     * reassembly and dedup path on the receiving side.
     */
    fun broadcastPhoto(jpeg: ByteArray, senderName: String, senderBAC: Double?) {
        if (jpeg.isEmpty() || jpeg.size > MAX_PHOTO_BYTES) return
        val b64 = Base64.getEncoder().encodeToString(jpeg)
        send(WireEnvelope(kind = "photo", photo = WirePhotoDto(senderName, b64, senderBAC)))
    }

    private fun send(envelope: WireEnvelope) {
        if (connectedEndpoints.isEmpty()) return
        val out = envelope.copy(messageID = UUID.randomUUID().toString())
        rememberSeen(out.messageID!!)
        val bytes = json.encodeToString(out).toByteArray(Charsets.UTF_8)
        runCatching { client.sendPayload(connectedEndpoints.toList(), Payload.fromBytes(bytes)) }
    }

    private fun forward(envelope: WireEnvelope, exceptEndpointId: String) {
        val others = connectedEndpoints.filter { it != exceptEndpointId }
        if (others.isEmpty()) return
        val bytes = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        runCatching { client.sendPayload(others, Payload.fromBytes(bytes)) }
    }

    // MARK: Nearby callbacks

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val myJam = activeJamID
            val theirJamID = info.endpointName.split(SEP).getOrNull(1)
            if (myJam != null && theirJamID == myJam) {
                runCatching { client.acceptConnection(endpointId, payloadCallback) }
            } else {
                runCatching { client.rejectConnection(endpointId) }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoints.add(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val parts = info.endpointName.split(SEP)
            if (parts.size < 3) return
            val jamID = parts[0]
            val code = parts[1]
            val hostName = parts[2]
            discoveredEndpoints[endpointId] = jamID

            // Connect only to peers of the jam we are actually in; while just
            // browsing the lobby no connection is made.
            if (activeJamID == jamID) {
                val name = listOf(myDeviceName, jamID).joinToString(SEP)
                runCatching { client.requestConnection(name, endpointId, connectionLifecycleCallback) }
            }
            if (_discoveredJams.value.any { it.id == jamID }) return
            val placeholder = Jam(
                id = jamID,
                code = code,
                hostUserID = "",
                hostName = hostName,
                createdAtEpochSeconds = System.currentTimeMillis() / 1000,
                visibility = JamVisibility.PROXIMITY_AND_CODE,
                settings = JamSettings(),
                participants = emptyList()
            )
            _discoveredJams.value = _discoveredJams.value + placeholder
            onJamFound?.invoke(placeholder)
        }

        override fun onEndpointLost(endpointId: String) {
            val jamID = discoveredEndpoints.remove(endpointId) ?: return
            _discoveredJams.value = _discoveredJams.value.filterNot { it.id == jamID }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val envelope = runCatching {
                json.decodeFromString<WireEnvelope>(String(bytes, Charsets.UTF_8))
            }.getOrNull() ?: return
            val mid = envelope.messageID ?: return
            if (hasSeen(mid)) return
            rememberSeen(mid)
            // Mesh forward with one less hop to every OTHER connected peer, so a
            // message can travel A -> B -> C across a chain, then deliver locally.
            if (envelope.ttl > 1) forward(envelope.copy(ttl = envelope.ttl - 1), endpointId)
            deliver(envelope)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun deliver(envelope: WireEnvelope) {
        when (envelope.kind) {
            "status" -> envelope.status?.let {
                onStatusReceived?.invoke(JamStatusBroadcast(it.jamID, it.participant.toParticipant()))
            }
            "control" -> envelope.control?.let { onControlReceived?.invoke(it.toControl()) }
            "roulette" -> envelope.roulette?.let { onRouletteReceived?.invoke(it.toPayload()) }
            "water" -> envelope.water?.let { onWaterReceived?.invoke(it.toPayload()) }
            "arcadeRound" -> envelope.arcadeRound?.toPayload()?.let { onArcadeRoundReceived?.invoke(it) }
            "arcadeResult" -> envelope.arcadeResult?.let { onArcadeResultReceived?.invoke(it.toPayload()) }
            "photo" -> envelope.photo?.let { dto ->
                runCatching { Base64.getDecoder().decode(dto.jpegBase64) }.getOrNull()?.let { jpeg ->
                    onPhotoReceived?.invoke(JamPhotoPayload(dto.senderName, jpeg, dto.senderBAC))
                }
            }
        }
    }

    private companion object {
        const val SERVICE_ID = "de.tipau.promille.JAM"
        const val SEP = ""
        const val MAX_SEEN = 512
    }
}

// ---- Wire types -------------------------------------------------------
//
// Flat, kind-tagged envelope rather than iOS's enum payload: kotlinx.serialization
// polymorphism needs a registered subclass module, and a flat envelope with one
// populated field per kind is the same data with none of that setup.
//
// WireParticipant carries the sender's privacy toggles as WireSettings, matching
// iOS, which puts a whole JamParticipant in its status broadcast. The values are
// already nulled per those toggles before broadcasting (see
// JamService.makeMyParticipant); the flags themselves are what the participant
// privacy detail renders, and only the Bluetooth channel has them - the server
// roster leaves sharedSettings null.

@Serializable
private data class WireEnvelope(
    val kind: String,
    val messageID: String? = null,
    val ttl: Int = 5,
    val status: WireStatus? = null,
    val control: WireControlDto? = null,
    val roulette: WireRoulette? = null,
    val water: WireWaterDto? = null,
    val arcadeRound: WireArcadeRound? = null,
    val arcadeResult: WireArcadeResult? = null,
    val photo: WirePhotoDto? = null
)

@Serializable
private data class WireStatus(val jamID: String, val participant: WireParticipant)

@Serializable
private data class WireParticipant(
    val id: String,
    val userID: String? = null,
    val displayName: String,
    val avatar: String = "",
    val joinedAtEpochSeconds: Long = 0,
    val connectionType: String = "Code",
    val currentBAC: Double? = null,
    val currentStatus: String? = null,
    val hasSOSActive: Boolean = false,
    val lastUpdatedEpochSeconds: Long = 0,
    val sharedSettings: WireSettings? = null
) {
    fun toParticipant() = JamParticipant(
        id = id, userID = userID, displayName = displayName, avatar = avatar,
        joinedAtEpochSeconds = joinedAtEpochSeconds,
        connectionType = JamConnectionType.from(connectionType),
        currentBAC = currentBAC, currentStatus = currentStatus, hasSOSActive = hasSOSActive,
        lastUpdatedEpochSeconds = lastUpdatedEpochSeconds,
        sharedSettings = sharedSettings?.toSettings()
    )

    companion object {
        fun from(p: JamParticipant) = WireParticipant(
            p.id, p.userID, p.displayName, p.avatar, p.joinedAtEpochSeconds,
            p.connectionType.raw, p.currentBAC, p.currentStatus, p.hasSOSActive,
            p.lastUpdatedEpochSeconds, p.sharedSettings?.let { WireSettings.from(it) }
        )
    }
}

// Only the six shared-value flags: the rest of JamSettings is local preference
// (location, waves, auto accept) and stays out of the broadcast.
@Serializable
private data class WireSettings(
    val shareBAC: Boolean = true,
    val shareStatus: Boolean = true,
    val shareDrinks: Boolean = true,
    val shareDrinkCount: Boolean = true,
    val shareSOSStatus: Boolean = true,
    val sharePhotos: Boolean = true
) {
    fun toSettings() = JamSettings(
        shareBAC = shareBAC, shareStatus = shareStatus, shareDrinks = shareDrinks,
        shareDrinkCount = shareDrinkCount, shareSOSStatus = shareSOSStatus,
        sharePhotos = sharePhotos
    )

    companion object {
        fun from(s: JamSettings) = WireSettings(
            s.shareBAC, s.shareStatus, s.shareDrinks, s.shareDrinkCount,
            s.shareSOSStatus, s.sharePhotos
        )
    }
}

@Serializable
private data class WireControlDto(
    val action: String,
    val jamID: String,
    val participantID: String,
    val userID: String? = null,
    val newHostName: String? = null
) {
    fun toControl() = MultipeerService.JamControl(
        action = MultipeerService.ControlAction.entries.firstOrNull { it.raw == action }
            ?: MultipeerService.ControlAction.LEAVE,
        jamID = jamID, participantID = participantID, userID = userID, newHostName = newHostName
    )

    companion object {
        fun from(c: MultipeerService.JamControl) =
            WireControlDto(c.action.raw, c.jamID, c.participantID, c.userID, c.newHostName)
    }
}

@Serializable
private data class WireRoulette(
    val id: String,
    val jamID: String,
    val participants: List<String>,
    val winnerIndex: Int,
    val starterName: String,
    val starterID: String? = null
) {
    fun toPayload() = JamRoulettePayload(id, jamID, participants, winnerIndex, starterName, starterID)

    companion object {
        fun from(p: JamRoulettePayload) = WireRoulette(p.id, p.jamID, p.participants, p.winnerIndex, p.starterName, p.starterID)
    }
}

@Serializable
private data class WireArcadeRound(
    val id: String,
    val jamID: String,
    val game: String,
    val starterID: String,
    val starterName: String,
    val startAtEpochSeconds: Double,
    val signalAtEpochSeconds: Double? = null,
    val durationSeconds: Double
) {
    /** Null for a round whose game type this build does not know. */
    fun toPayload(): JamArcadeRoundPayload? {
        val g = JamArcadeGame.from(game) ?: return null
        return JamArcadeRoundPayload(id, jamID, g, starterID, starterName, startAtEpochSeconds, signalAtEpochSeconds, durationSeconds)
    }

    companion object {
        fun from(p: JamArcadeRoundPayload) = WireArcadeRound(
            p.id, p.jamID, p.game.raw, p.starterID, p.starterName,
            p.startAtEpochSeconds, p.signalAtEpochSeconds, p.durationSeconds
        )
    }
}

@Serializable
private data class WireArcadeResult(
    val jamID: String,
    val roundID: String,
    val participantID: String,
    val participantName: String,
    val value: Double,
    val disqualified: Boolean,
    val submittedAtEpochSeconds: Double
) {
    fun toPayload() = JamArcadeResultPayload(jamID, roundID, participantID, participantName, value, disqualified, submittedAtEpochSeconds)

    companion object {
        fun from(p: JamArcadeResultPayload) = WireArcadeResult(
            p.jamID, p.roundID, p.participantID, p.participantName, p.value, p.disqualified, p.submittedAtEpochSeconds
        )
    }
}

@Serializable
private data class WireWaterDto(
    val jamID: String,
    val kind: String,
    val participantID: String,
    val name: String,
    val milliseconds: Int
) {
    fun toPayload() = MultipeerService.WaterPayload(
        jamID,
        if (kind == "reset") MultipeerService.WaterKind.RESET else MultipeerService.WaterKind.RESULT,
        participantID, name, milliseconds
    )

    companion object {
        fun from(p: MultipeerService.WaterPayload) = WireWaterDto(
            p.jamID,
            if (p.kind == MultipeerService.WaterKind.RESET) "reset" else "result",
            p.participantID, p.name, p.milliseconds
        )
    }
}

@Serializable
private data class WirePhotoDto(val senderName: String, val jpegBase64: String, val senderBAC: Double? = null)
