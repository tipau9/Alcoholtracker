package de.tipau.promille.sync

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
import de.tipau.promille.bac.activeJamRoster
import de.tipau.promille.bac.electJamHost
import de.tipau.promille.bac.mergeWaterScores
import de.tipau.promille.bac.upserting
import de.tipau.promille.bac.wasKickedFromJam
import de.tipau.promille.network.PendingJamInvite
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.deleteJam
import de.tipau.promille.network.fetchFriendJams
import de.tipau.promille.network.fetchJamArcadeResults
import de.tipau.promille.network.fetchJamArcadeRound
import de.tipau.promille.network.fetchJamParticipants
import de.tipau.promille.network.fetchJamRoulette
import de.tipau.promille.network.fetchJamWaterScores
import de.tipau.promille.network.fetchMyJamInvitations
import de.tipau.promille.network.findJamByCode
import de.tipau.promille.network.friendCode
import de.tipau.promille.network.joinJam
import de.tipau.promille.network.leaveJam
import de.tipau.promille.network.markInvitationSeen
import de.tipau.promille.network.publishJam
import de.tipau.promille.network.removeParticipant
import de.tipau.promille.network.resetJamWater
import de.tipau.promille.network.sanitizeFriendCode
import de.tipau.promille.network.sendJamInvitation
import de.tipau.promille.network.setJamArcadeRound
import de.tipau.promille.network.setJamRoulette
import de.tipau.promille.network.submitJamArcadeResult
import de.tipau.promille.network.submitJamWaterTime
import de.tipau.promille.network.updateJamHost
import de.tipau.promille.network.updateMyJamStatus
import android.graphics.Bitmap
import de.tipau.promille.service.MAX_PHOTO_BYTES
import de.tipau.promille.service.MultipeerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.random.Random

class JamException(message: String) : Exception(message)

/**
 * Port of JamService.swift, orchestrating both transports exactly as iOS does:
 * Supabase for [JamVisibility.usesServer] jams, [MultipeerService] (the Nearby
 * Connections port of MultipeerConnectivity) for [JamVisibility.usesProximity]
 * ones. A [JamVisibility.PROXIMITY_AND_CODE] jam runs both at once; a
 * [JamVisibility.PROXIMITY_ONLY] one never touches the server.
 *
 * [multipeer] is nullable so tests and any call site that has no Context handy
 * still compile; passing null just means proximity jams behave as before this
 * port (create throws, since a session with no transport at all is worse than
 * saying so).
 */
class JamService(
    private val supabase: SupabaseService,
    private val scope: CoroutineScope,
    private val multipeer: MultipeerService? = null,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    private val _currentJam = MutableStateFlow<Jam?>(null)
    val currentJam: StateFlow<Jam?> = _currentJam.asStateFlow()

    private val _availableJamsFromFriends = MutableStateFlow<List<Jam>>(emptyList())
    val availableJamsFromFriends: StateFlow<List<Jam>> = _availableJamsFromFriends.asStateFlow()

    private val _invitations = MutableStateFlow<List<PendingJamInvite>>(emptyList())
    val invitations: StateFlow<List<PendingJamInvite>> = _invitations.asStateFlow()

    private val _waterScores = MutableStateFlow<List<WaterScore>>(emptyList())
    val waterScores: StateFlow<List<WaterScore>> = _waterScores.asStateFlow()
    private var lastWaterSubmitTime = 0L

    private val _incomingRoulette = MutableStateFlow<JamRoulettePayload?>(null)
    val incomingRoulette: StateFlow<JamRoulettePayload?> = _incomingRoulette.asStateFlow()

    private val _incomingArcadeRound = MutableStateFlow<JamArcadeRoundPayload?>(null)
    val incomingArcadeRound: StateFlow<JamArcadeRoundPayload?> = _incomingArcadeRound.asStateFlow()

    private val _arcadeResults = MutableStateFlow<List<JamArcadeResultPayload>>(emptyList())
    val arcadeResults: StateFlow<List<JamArcadeResultPayload>> = _arcadeResults.asStateFlow()

    private val _amHost = MutableStateFlow(false)
    val amHost: StateFlow<Boolean> = _amHost.asStateFlow()

    // Photos received from peers in the current jam, capped like iOS's
    // receivedPhotos. At most MAX_PHOTO_BYTES each, so this bounds memory to
    // well under a megabyte - iOS holds ~6 MB for the same thirty photos.
    private val _receivedPhotos = MutableStateFlow<List<MultipeerService.JamPhotoPayload>>(emptyList())
    val receivedPhotos: StateFlow<List<MultipeerService.JamPhotoPayload>> = _receivedPhotos.asStateFlow()
    private val maxReceivedPhotos = 30

    val mySettings = MutableStateFlow(JamSettings())

    /** Set from the session so the roster carries a live value. */
    val myCurrentBAC = MutableStateFlow(0.0)
    val myCurrentStatus = MutableStateFlow<String?>(null)
    val mySOSActive = MutableStateFlow(false)

    private var myParticipantID: String = UUID.randomUUID().toString()
    private var myJoinedAt: Long = 0
    private var myConnectionType: JamConnectionType = JamConnectionType.CODE
    private var confirmedOnServer = false

    /**
     * Ids that must not come back. A participant row lingers on the server for
     * up to the staleness window after a leave or a kick, and without this the
     * very next poll would put them straight back into the roster.
     */
    private val tombstones = mutableMapOf<String, Long>()
    private var pollJob: Job? = null
    private var statusJob: Job? = null

    private fun tombstone(id: String) {
        tombstones[id] = nowSeconds()
    }

    private fun liveTombstones(): Set<String> {
        val now = nowSeconds()
        tombstones.entries.removeAll { now - it.value > TOMBSTONE_SECONDS }
        return tombstones.keys.toSet()
    }

    /** Jams discovered nearby while [startNearbyDiscovery] is running. */
    val nearbyJams: StateFlow<List<Jam>> = multipeer?.discoveredJams ?: MutableStateFlow(emptyList())

    init {
        multipeer?.let { mp ->
            mp.onStatusReceived = { broadcast ->
                if (broadcast.jamID == _currentJam.value?.id) {
                    _currentJam.value = _currentJam.value?.let {
                        it.copy(participants = it.participants.upserting(broadcast.participant))
                    }
                }
            }
            mp.onControlReceived = { control -> handleIncomingControl(control) }
            mp.onRouletteReceived = { payload ->
                if (payload.jamID == _currentJam.value?.id && _incomingRoulette.value?.id != payload.id) {
                    _incomingRoulette.value = payload
                }
            }
            mp.onWaterReceived = { payload ->
                if (payload.jamID == _currentJam.value?.id) {
                    when (payload.kind) {
                        MultipeerService.WaterKind.RESET -> {
                            lastWaterSubmitTime = 0L
                            _waterScores.value = emptyList()
                        }
                        MultipeerService.WaterKind.RESULT -> applyWaterScore(
                            WaterScore(payload.participantID, payload.name, payload.milliseconds)
                        )
                    }
                }
            }
            mp.onArcadeRoundReceived = { round ->
                if (round.jamID == _currentJam.value?.id && _incomingArcadeRound.value?.id != round.id) {
                    _incomingArcadeRound.value = round
                    _arcadeResults.value = emptyList()
                }
            }
            mp.onArcadeResultReceived = { result ->
                if (result.jamID == _currentJam.value?.id) {
                    _arcadeResults.value = _arcadeResults.value
                        .filterNot { it.participantID == result.participantID } + result
                }
            }
            mp.onPhotoReceived = { photo -> appendPhoto(photo) }
        }
    }

    private fun appendPhoto(photo: MultipeerService.JamPhotoPayload) {
        val updated = _receivedPhotos.value + photo
        _receivedPhotos.value = if (updated.size > maxReceivedPhotos) {
            updated.subList(updated.size - maxReceivedPhotos, updated.size)
        } else {
            updated
        }
    }

    /**
     * Shares a photo with the jam over the proximity channel; disabled without
     * connected peers. Compressing here rather than in the picker mirrors iOS,
     * where sendPhoto takes a UIImage and steps the quality down itself - and
     * it keeps the wire budget next to the transport that imposes it.
     */
    fun sendPhoto(bitmap: Bitmap) {
        if (_currentJam.value == null) throw JamException("Kein aktiver Jam.")
        if (!mySettings.value.sharePhotos) throw JamException("Fotos teilen ist deaktiviert.")
        if (multipeer?.hasConnectedPeers != true) throw JamException("Niemand in Reichweite.")
        val jpeg = compressForWire(bitmap) ?: throw JamException("Foto zu groß.")
        val bac = if (mySettings.value.shareBAC) myCurrentBAC.value else null
        multipeer.broadcastPhoto(jpeg, myDisplayName(), bac)
        // Own copy shown immediately, without waiting on the mesh round trip.
        appendPhoto(MultipeerService.JamPhotoPayload("Du", jpeg, bac))
    }

    /**
     * Scales the long edge to 900 px and steps the quality down until the jpeg
     * fits MAX_PHOTO_BYTES, null when even the last step is too big. The
     * budget is a transport limit, not a taste one: a photo over it is dropped
     * silently by Nearby, so the last quality step is deliberately ugly rather
     * than absent.
     */
    private fun compressForWire(bitmap: Bitmap): ByteArray? {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longEdge <= 900) bitmap else {
            val factor = 900.0 / longEdge
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).toInt().coerceAtLeast(1),
                (bitmap.height * factor).toInt().coerceAtLeast(1),
                true
            )
        }
        for (quality in intArrayOf(70, 55, 40, 25, 15)) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_PHOTO_BYTES) return bytes
        }
        return null
    }

    /** Moderation messages arriving over the proximity channel. */
    private fun handleIncomingControl(control: MultipeerService.JamControl) {
        val jam = _currentJam.value ?: return
        if (control.jamID != jam.id) return
        when (control.action) {
            MultipeerService.ControlAction.KICK -> if (control.participantID == myParticipantID) {
                leaveJam()
            } else {
                tombstone(control.participantID)
                _currentJam.value = jam.copy(participants = jam.participants.filterNot { it.id == control.participantID })
            }
            MultipeerService.ControlAction.LEAVE -> {
                tombstone(control.participantID)
                _currentJam.value = jam.copy(participants = jam.participants.filterNot { it.id == control.participantID })
            }
            MultipeerService.ControlAction.TRANSFER_HOST -> {
                val uid = control.userID ?: return
                _amHost.value = control.participantID == myParticipantID
                _currentJam.value = jam.copy(hostUserID = uid, hostName = control.newHostName ?: jam.hostName)
            }
        }
    }

    /** Starts browsing for nearby jams; the create/join lobby screen owns the lifecycle. */
    fun startNearbyDiscovery() = multipeer?.startBrowsing()

    fun stopNearbyDiscovery() = multipeer?.stopBrowsing()

    // MARK: Create and join

    suspend fun createJam(visibility: JamVisibility, settings: JamSettings): Jam {
        if (visibility.usesProximity && multipeer == null) {
            throw JamException("Reine Bluetooth-Jams gibt es auf diesem Gerät nicht.")
        }
        if (visibility.usesServer && !supabase.isSignedIn.value) {
            throw JamException("Dafür musst du angemeldet sein.")
        }

        // The server row (when there is one) is written before any local state
        // changes, so a failure cannot leave an active jam screen behind with
        // nothing behind it.
        mySettings.value = settings
        myParticipantID = UUID.randomUUID().toString()
        myJoinedAt = nowSeconds()
        myConnectionType = JamConnectionType.CODE
        confirmedOnServer = false

        val hostName = supabase.myProfile.value?.displayName?.takeIf { it.isNotEmpty() } ?: "Host"
        val jam = Jam(
            id = UUID.randomUUID().toString(),
            code = generateJamCode(),
            hostUserID = if (visibility.usesServer) supabase.userId ?: "" else "",
            hostName = hostName,
            createdAtEpochSeconds = nowSeconds(),
            visibility = visibility,
            settings = settings,
            participants = listOf(makeMyParticipant())
        )
        if (visibility.usesServer) supabase.publishJam(jam)
        if (visibility.usesProximity) multipeer?.startAdvertisingJam(jam)

        _amHost.value = true
        _currentJam.value = jam
        startTimers()
        return jam
    }

    suspend fun joinJamByCode(code: String) {
        val jam = supabase.findJamByCode(code) ?: throw JamException("Kein Jam mit diesem Code.")
        // A friends-only jam stays friends-only even if the code leaks: the host
        // has to be in this device's friend list.
        if (jam.visibility == JamVisibility.FRIENDS_ONLY) {
            val hostCode = runCatching { supabase.friendCode(jam.hostUserID) }.getOrNull()
            val mine = friendCodes.map { sanitizeFriendCode(it) }.toSet()
            if (hostCode == null || sanitizeFriendCode(hostCode) !in mine) {
                throw JamException("Dieser Jam ist nur für Freunde des Hosts.")
            }
        }
        join(jam, JamConnectionType.CODE)
    }

    suspend fun joinJamFromFriend(jam: Jam) = join(jam, JamConnectionType.FRIEND)

    /** Joins a jam found via [startNearbyDiscovery], connecting over Bluetooth/Wi-Fi. */
    suspend fun joinNearbyJam(jam: Jam) = join(jam, JamConnectionType.PROXIMITY)

    private suspend fun join(jam: Jam, type: JamConnectionType) {
        // Nearby-discovered jams are always reported as usesServer (the wire
        // protocol has no room for the real visibility, see onEndpointFound),
        // so this gate only hard-blocks CODE/FRIEND joins - matching iOS,
        // whose joinJamNearby registers with the server via `try?` rather
        // than requiring sign-in first.
        if (jam.visibility.usesServer && !supabase.isSignedIn.value && type != JamConnectionType.PROXIMITY) {
            throw JamException("Dafür musst du angemeldet sein.")
        }
        myParticipantID = UUID.randomUUID().toString()
        myJoinedAt = nowSeconds()
        myConnectionType = type
        confirmedOnServer = false

        if (jam.visibility.usesServer) {
            val register: suspend () -> Unit = {
                supabase.joinJam(
                    jamID = jam.id,
                    participantID = myParticipantID,
                    initialBAC = if (mySettings.value.shareBAC) myCurrentBAC.value else null,
                    connectionType = type.raw
                )
            }
            if (type == JamConnectionType.PROXIMITY) runCatching { register() } else register()
        }
        // Connects at once if the host was already discovered; otherwise this
        // just sets activeJamID so a discovery arriving later auto-connects.
        if (jam.visibility.usesProximity) multipeer?.connectToJam(jam.id)

        _amHost.value = jam.hostUserID.isNotEmpty() && jam.hostUserID == supabase.userId
        _currentJam.value = jam.copy(
            participants = jam.participants.upserting(makeMyParticipant())
        )
        startTimers()
    }

    // MARK: Leave

    fun leaveJam() {
        val jam = _currentJam.value ?: return
        val wasHost = _amHost.value

        // Ghost jam: a leaving host hands the role to an elected member instead
        // of ending the session for everyone still in it.
        var electedHost: JamParticipant? = null
        if (wasHost) {
            val others = jam.participants.filter { it.id != myParticipantID }
            electedHost = electJamHost(others, excludingUserID = supabase.userId)
        }
        val deletesJam = wasHost && electedHost == null

        if (jam.visibility.usesProximity) {
            if (wasHost && electedHost != null) {
                multipeer?.broadcastControl(
                    MultipeerService.JamControl(
                        action = MultipeerService.ControlAction.TRANSFER_HOST,
                        jamID = jam.id,
                        participantID = electedHost.id,
                        userID = electedHost.userID,
                        newHostName = electedHost.displayName
                    )
                )
            } else {
                multipeer?.broadcastControl(
                    MultipeerService.JamControl(
                        action = MultipeerService.ControlAction.LEAVE,
                        jamID = jam.id,
                        participantID = myParticipantID
                    )
                )
            }
            multipeer?.stopAll()
        }

        stopTimers()
        _currentJam.value = null
        _amHost.value = false
        confirmedOnServer = false
        // A jam-only SOS must not outlive the jam: a signed-out user has no
        // other path to clear it (CrewView's tap handler only opens the info
        // dialog when there's no jam and no account).
        mySOSActive.value = false
        // Games are per jam and must not leak into the next one.
        _waterScores.value = emptyList()
        _incomingRoulette.value = null
        _incomingArcadeRound.value = null
        _arcadeResults.value = emptyList()
        _availableJamsFromFriends.value = emptyList()
        _receivedPhotos.value = emptyList()
        tombstones.clear()

        if (!jam.visibility.usesServer) return

        scope.launch {
            if (deletesJam) {
                runCatching { supabase.deleteJam(jam.id) }
            } else {
                // Repoint the host before deleting our own row, so the server
                // side cleanup sees a live host and keeps the jam alive.
                electedHost?.userID?.let { uid ->
                    runCatching { supabase.updateJamHost(jam.id, uid, electedHost.displayName) }
                }
                runCatching { supabase.leaveJam(jam.id) }
            }
        }
    }

    // MARK: Host powers

    fun canKick(participant: JamParticipant): Boolean =
        _amHost.value && participant.id != myParticipantID

    fun kickParticipant(participant: JamParticipant) {
        val jam = _currentJam.value ?: return
        if (!canKick(participant)) return
        tombstone(participant.id)
        _currentJam.value = jam.copy(
            participants = jam.participants.filterNot { it.id == participant.id }
        )
        if (jam.visibility.usesProximity) {
            multipeer?.broadcastControl(
                MultipeerService.JamControl(
                    action = MultipeerService.ControlAction.KICK,
                    jamID = jam.id,
                    participantID = participant.id
                )
            )
        }
        if (jam.visibility.usesServer) {
            scope.launch { runCatching { supabase.removeParticipant(jam.id, participant.id) } }
        }
    }

    fun canTransferHost(participant: JamParticipant): Boolean =
        _amHost.value && participant.id != myParticipantID && participant.userID != null

    fun transferHost(participant: JamParticipant) {
        val jam = _currentJam.value ?: return
        if (!canTransferHost(participant)) return
        val uid = participant.userID ?: return
        _amHost.value = false
        _currentJam.value = jam.copy(hostUserID = uid, hostName = participant.displayName)
        if (jam.visibility.usesProximity) {
            multipeer?.broadcastControl(
                MultipeerService.JamControl(
                    action = MultipeerService.ControlAction.TRANSFER_HOST,
                    jamID = jam.id,
                    participantID = participant.id,
                    userID = uid,
                    newHostName = participant.displayName
                )
            )
        }
        if (jam.visibility.usesServer) {
            scope.launch { runCatching { supabase.updateJamHost(jam.id, uid, participant.displayName) } }
        }
    }

    // MARK: Invitations and friend jams

    var friendCodes: List<String> = emptyList()

    suspend fun inviteFriend(friendCode: String) {
        val jam = _currentJam.value ?: return
        supabase.sendJamInvitation(friendCode, jam.id, jam.code, jam.hostName)
    }

    suspend fun refreshInvitations() {
        if (!supabase.isSignedIn.value) return
        _invitations.value = runCatching { supabase.fetchMyJamInvitations() }.getOrDefault(emptyList())
    }

    suspend fun dismissInvitation(id: String) {
        runCatching { supabase.markInvitationSeen(id) }
        _invitations.value = _invitations.value.filterNot { it.id == id }
    }

    suspend fun refreshFriendJams(codes: List<String>) {
        friendCodes = codes
        if (codes.isEmpty() || !supabase.isSignedIn.value) {
            _availableJamsFromFriends.value = emptyList()
            return
        }
        _availableJamsFromFriends.value =
            runCatching { supabase.fetchFriendJams(codes) }.getOrDefault(emptyList())
    }

    // MARK: Mini games

    // Keeps the best (lowest) time per participant, matching iOS JamService.applyWaterScore.
    private fun applyWaterScore(score: WaterScore) {
        val current = _waterScores.value
        val existingIndex = current.indexOfFirst { it.participantID == score.participantID }
        val updated = if (existingIndex >= 0) {
            if (score.ms < current[existingIndex].ms) {
                current.toMutableList().apply { set(existingIndex, score) }
            } else {
                current
            }
        } else {
            current + score
        }
        _waterScores.value = updated.sortedWith(compareBy({ it.ms }, { it.participantID }))
    }

    suspend fun submitWaterTime(milliseconds: Int) {
        val jam = _currentJam.value ?: return
        val name = myDisplayName()
        lastWaterSubmitTime = System.currentTimeMillis()
        applyWaterScore(WaterScore(myParticipantID, name, milliseconds))
        if (jam.visibility.usesProximity) {
            multipeer?.broadcastWater(
                MultipeerService.WaterPayload(jam.id, MultipeerService.WaterKind.RESULT, myParticipantID, name, milliseconds)
            )
        }
        if (jam.visibility.usesServer) {
            runCatching { supabase.submitJamWaterTime(jam.id, name, milliseconds) }
        }
    }

    suspend fun resetWaterLeaderboard() {
        val jam = _currentJam.value ?: return
        lastWaterSubmitTime = 0L
        _waterScores.value = emptyList()
        if (jam.visibility.usesProximity) {
            multipeer?.broadcastWater(
                MultipeerService.WaterPayload(jam.id, MultipeerService.WaterKind.RESET, myParticipantID, myDisplayName(), 0)
            )
        }
        if (jam.visibility.usesServer) {
            runCatching { supabase.resetJamWater(jam.id) }
        }
    }

    suspend fun startRoulette() {
        val jam = _currentJam.value ?: return
        val names = jam.participants.map { it.displayName }
        if (names.isEmpty()) return
        val payload = JamRoulettePayload(
            id = UUID.randomUUID().toString(),
            jamID = jam.id,
            participants = names,
            winnerIndex = Random.nextInt(names.size),
            starterName = myDisplayName(),
            starterID = myParticipantID
        )
        _incomingRoulette.value = payload
        if (jam.visibility.usesProximity) multipeer?.broadcastRoulette(payload)
        if (jam.visibility.usesServer) runCatching { supabase.setJamRoulette(payload) }
    }

    fun canRestartArcade(round: JamArcadeRoundPayload): Boolean =
        round.jamID == _currentJam.value?.id && round.starterID == myParticipantID

    suspend fun startArcade(game: JamArcadeGame) {
        val jam = _currentJam.value ?: return
        val start = nowSeconds().toDouble() + ARCADE_LEAD_SECONDS
        val round = JamArcadeRoundPayload(
            id = UUID.randomUUID().toString(),
            jamID = jam.id,
            game = game,
            starterID = myParticipantID,
            starterName = myDisplayName(),
            startAtEpochSeconds = start,
            // Only Reaction Royale has a go signal, and it has to be at a time
            // nobody can anticipate or the round is a coin flip on reflexes.
            signalAtEpochSeconds = if (game == JamArcadeGame.REACTION_ROYALE) {
                start + 2.5 + Random.nextDouble(3.0)
            } else {
                null
            },
            durationSeconds = if (game == JamArcadeGame.BALANCE_BATTLE) 10.0 else 5.0
        )
        _incomingArcadeRound.value = round
        _arcadeResults.value = emptyList()
        if (jam.visibility.usesProximity) multipeer?.broadcastArcadeRound(round)
        if (jam.visibility.usesServer) runCatching { supabase.setJamArcadeRound(round) }
    }

    suspend fun submitArcadeResult(value: Double, disqualified: Boolean = false) {
        val jam = _currentJam.value ?: return
        val round = _incomingArcadeRound.value ?: return
        val result = JamArcadeResultPayload(
            jamID = jam.id,
            roundID = round.id,
            participantID = myParticipantID,
            participantName = myDisplayName(),
            value = value,
            disqualified = disqualified,
            submittedAtEpochSeconds = nowSeconds().toDouble()
        )
        _arcadeResults.value = _arcadeResults.value
            .filterNot { it.participantID == myParticipantID } + result
        if (jam.visibility.usesProximity) multipeer?.broadcastArcadeResult(result)
        if (jam.visibility.usesServer) runCatching { supabase.submitJamArcadeResult(result) }
    }

    fun closeArcade() {
        _incomingArcadeRound.value = null
        _arcadeResults.value = emptyList()
    }

    fun dismissRoulette() {
        _incomingRoulette.value = null
    }

    // MARK: Polling

    private fun startTimers() {
        stopTimers()
        pollJob = scope.launch {
            while (true) {
                if (_currentJam.value?.visibility?.usesServer == true) {
                    syncParticipants()
                    syncJamGames()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        statusJob = scope.launch {
            while (true) {
                delay(STATUS_INTERVAL_MS)
                val jam = _currentJam.value ?: continue
                if (jam.visibility.usesProximity) {
                    multipeer?.broadcastParticipant(makeMyParticipant(), jam.id)
                }
                if (jam.visibility.usesServer) {
                    runCatching {
                        supabase.updateMyJamStatus(
                            jamID = jam.id,
                            bac = if (mySettings.value.shareBAC) myCurrentBAC.value else null,
                            status = if (mySettings.value.shareStatus) myCurrentStatus.value else null,
                            sosActive = mySettings.value.shareSOSStatus && mySOSActive.value
                        )
                    }
                }
                // Keep our own row in the local roster fresh too, otherwise the
                // staleness filter would eventually drop us from our own list.
                _currentJam.value = _currentJam.value?.let {
                    it.copy(participants = it.participants.upserting(makeMyParticipant()))
                }
            }
        }
    }

    private fun stopTimers() {
        pollJob?.cancel()
        pollJob = null
        statusJob?.cancel()
        statusJob = null
    }

    private suspend fun syncParticipants() {
        val jam = _currentJam.value ?: return
        val fresh = runCatching { supabase.fetchJamParticipants(jam.id) }.getOrNull() ?: return
        val myUserID = supabase.userId

        // Kick detection. The host never self kicks, and a row that was never
        // confirmed may simply not have landed yet.
        if (!_amHost.value) {
            if (!wasKickedFromJam(fresh, myParticipantID, myUserID)) {
                confirmedOnServer = true
            } else if (confirmedOnServer) {
                leaveJam()
                return
            }
        }

        val roster = activeJamRoster(
            serverRows = fresh,
            me = makeMyParticipant(),
            myUserID = myUserID,
            tombstonedIDs = liveTombstones(),
            nowEpochSeconds = nowSeconds(),
            existingParticipants = jam.participants
        )
        var updated = jam.copy(participants = roster)

        // Ghost jam: the host vanished from the roster, so every remaining
        // client elects the same replacement without asking the server.
        if (roster.none { it.userID != null && it.userID == updated.hostUserID }) {
            // Only an account can hold the host slot on a server jam. Electing
            // an anonymous member would leave hostUserID pointing at the dead
            // host and re-elect on every single poll.
            electJamHost(roster.filter { it.userID != null })?.let { elected ->
                updated = updated.copy(
                    hostUserID = elected.userID ?: updated.hostUserID,
                    hostName = elected.displayName
                )
                _amHost.value = elected.id == myParticipantID
            }
        }
        _currentJam.value = updated
    }

    private suspend fun syncJamGames() {
        val jam = _currentJam.value ?: return
        runCatching { supabase.fetchJamRoulette(jam.id) }.getOrNull()?.let { draw ->
            // Only a draw we have not shown yet, so a poll does not respin it.
            if (_incomingRoulette.value?.id != draw.id) _incomingRoulette.value = draw
        }
        runCatching { supabase.fetchJamArcadeRound(jam.id) }.getOrNull()?.let { round ->
            if (_incomingArcadeRound.value?.id != round.id) {
                _incomingArcadeRound.value = round
                _arcadeResults.value = emptyList()
            }
        }
        _incomingArcadeRound.value?.let { round ->
            runCatching { supabase.fetchJamArcadeResults(jam.id, round.id) }.getOrNull()
                ?.let { _arcadeResults.value = it }
        }
        runCatching { supabase.fetchJamWaterScores(jam.id) }.getOrNull()?.let { server ->
            val hasInFlight = (System.currentTimeMillis() - lastWaterSubmitTime) < 7000L
            _waterScores.value = mergeWaterScores(_waterScores.value, server, myParticipantID, hasInFlightSubmit = hasInFlight)
        }
    }

    // MARK: Self

    private fun myDisplayName(): String =
        supabase.myProfile.value?.displayName?.takeIf { it.isNotEmpty() } ?: "Ich"

    private fun makeMyParticipant(): JamParticipant = JamParticipant(
        id = myParticipantID,
        userID = supabase.userId,
        displayName = myDisplayName(),
        joinedAtEpochSeconds = myJoinedAt,
        connectionType = myConnectionType,
        currentBAC = if (mySettings.value.shareBAC) myCurrentBAC.value else null,
        currentStatus = if (mySettings.value.shareStatus) myCurrentStatus.value else null,
        hasSOSActive = mySettings.value.shareSOSStatus && mySOSActive.value,
        lastUpdatedEpochSeconds = nowSeconds(),
        sharedSettings = mySettings.value
    )

    private companion object {
        /** Two GETs per tick, so 12s is about ten requests a minute. */
        const val POLL_INTERVAL_MS = 12_000L
        const val STATUS_INTERVAL_MS = 30_000L
        const val TOMBSTONE_SECONDS = 180L

        /** Enough lead for every client to see the round before it starts. */
        const val ARCADE_LEAD_SECONDS = 5.0

        fun generateJamCode(): String =
            de.tipau.promille.service.JamCodeGenerator.generate()
    }
}
