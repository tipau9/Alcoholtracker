import Foundation
import UIKit

// MARK: - Supabase trigger for empty-jam cleanup
//
// Run once in Supabase SQL Editor to auto-delete jams with 0 participants
// or when the host leaves:
//
// CREATE OR REPLACE FUNCTION check_jam_cleanup()
// RETURNS TRIGGER LANGUAGE plpgsql AS $$
// BEGIN
//   -- If the departing participant was the host, delete the entire jam
//   IF (SELECT host_user_id FROM jams WHERE id = OLD.jam_id) = OLD.user_id THEN
//     DELETE FROM jams WHERE id = OLD.jam_id;
//   -- Otherwise, if no participants are left, delete the jam
//   ELSIF (SELECT COUNT(*) FROM jam_participants WHERE jam_id = OLD.jam_id) = 0 THEN
//     DELETE FROM jams WHERE id = OLD.jam_id;
//   END IF;
//   RETURN OLD;
// END;
// $$;
//
// DROP TRIGGER IF EXISTS trg_check_jam_cleanup ON jam_participants;
// CREATE TRIGGER trg_check_jam_cleanup
// AFTER DELETE ON jam_participants
// FOR EACH ROW EXECUTE FUNCTION check_jam_cleanup();

// MARK: - JamError

enum JamError: LocalizedError {
    case notFound
    case notSignedIn
    case friendsOnly

    var errorDescription: String? {
        switch self {
        case .notFound:    return "Kein Jam mit diesem Code gefunden."
        case .notSignedIn: return "Du musst angemeldet sein, um einem Jam beizutreten."
        case .friendsOnly: return "Dieser Jam ist nur für Freunde des Hosts. Füge zuerst den Freundes-Code des Hosts hinzu."
        }
    }
}

// MARK: - PhotoShareError

enum PhotoShareError: LocalizedError {
    case sharingDisabled
    case noPeers

    var errorDescription: String? {
        switch self {
        case .sharingDisabled: return "Foto-Teilen ist in deinen Privatsphäre-Einstellungen deaktiviert."
        case .noPeers:         return "Kein Teilnehmer in Bluetooth-Reichweite. Fotos werden direkt zwischen Geräten übertragen."
        }
    }
}

// MARK: - JamService

@MainActor
@Observable
final class JamService {

    var currentJam: Jam?
    var availableJamsNearby: [Jam]       = []
    var availableJamsFromFriends: [Jam]  = []
    var mySettings: JamSettings          = JamSettings()
    var receivedPhotos: [JamPhotoPayload] = []  // photos received from peers in current jam
    // Latest round-roulette draw (locally started or received); the active jam
    // view presents the spin sheet for it and clears it on dismiss.
    var incomingRoulette: JamRoulettePayload?

    // Water-chug leaderboard for the current jam (best time per participant).
    var waterScores: [WaterScore] = []

    // Locally stored friend codes (from CrewMember), set by the lobby before
    // browsing. Used to filter "Von Freunden" jams and to verify access to
    // friends-only jams when joining per code.
    var friendCodes: [String] = []

    // Cap on kept jam photos; ~200 KB each, so this bounds memory to ~6 MB.
    private let maxReceivedPhotos = 30

    // Set by HomeView whenever the user's BAC changes.
    var myCurrentBAC: Double = UserDefaults.widgetShared.double(forKey: UserDefaults.keyCurrentBAC) {
        didSet {
            if myCurrentBAC != oldValue {
                updateLocalParticipant()
                broadcastNow()
            }
        }
    }
    var myCurrentStatus: String? = nil {
        didSet {
            if myCurrentStatus != oldValue {
                updateLocalParticipant()
                broadcastNow()
            }
        }
    }
    var mySOSActive: Bool = false {
        didSet {
            if mySOSActive != oldValue {
                updateLocalParticipant()
                // Both directions bypass the server throttle: the SOS itself is
                // urgent, and the all-clear must not linger for 30 s either.
                broadcastNow(force: true)
            }
        }
    }

    private let multipeer = MultipeerService()
    private let supabase: SupabaseService

    // Stable ID for the local user's participant row within the current jam session.
    // Using UUID() per call caused anonymous users to be duplicated every 30s.
    private let myParticipantID = UUID()
    // Track how we joined so broadcastNow sends the correct connection type.
    private var myConnectionType: JamParticipant.ConnectionType = .code

    // True only while hosting the current jam. A plain hostUserID comparison is
    // unreliable for anonymous proximity hosts (empty id == nil session), so the
    // host role is tracked explicitly. Drives the kick permission.
    private(set) var amHost = false

    // Set once our own participant row has been seen on the server. Used to
    // detect a host-initiated kick: if we were present and then vanish from the
    // server list, the host removed us and we leave the jam locally.
    private var confirmedOnServer = false

    private var statusTimer: Timer?
    private var pollTimer: Timer?
    private var lastBroadcastTime: Date = .distantPast
    private var myJoinedAt: Date?
    // Draw id of the roulette already presented, so the same draw arriving over
    // both transports (Bluetooth + server poll) is shown at most once.
    private var lastRouletteID: UUID?

    init(supabase: SupabaseService) {
        self.supabase = supabase
        setupMultipeerCallbacks()
    }

    // MARK: Setup

    private func setupMultipeerCallbacks() {
        multipeer.onStatusReceived = { [weak self] broadcast in
            self?.handleProximityBroadcast(broadcast)
        }
        multipeer.onJamFound = { [weak self] jam in
            guard let self else { return }
            if !self.availableJamsNearby.contains(where: { $0.id == jam.id }) {
                self.availableJamsNearby.append(jam)
            }
        }
        multipeer.onPhotoReceived = { [weak self] photo in
            self?.appendPhoto(photo)
        }
        multipeer.onControlReceived = { [weak self] control in
            self?.handleControl(control)
        }
        multipeer.onRouletteReceived = { [weak self] payload in
            self?.presentRoulette(payload)
        }
        multipeer.onWaterReceived = { [weak self] payload in
            guard let self, payload.jamID == self.currentJam?.id else { return }
            switch payload.kind {
            case .reset:  self.waterScores.removeAll()
            case .result: self.applyWaterScore(WaterScore(id: payload.participantID, name: payload.name, ms: payload.milliseconds))
            }
        }
    }

    // MARK: Water leaderboard

    // Keeps the best (lowest) time per participant.
    private func applyWaterScore(_ score: WaterScore) {
        if let i = waterScores.firstIndex(where: { $0.id == score.id }) {
            if score.ms < waterScores[i].ms { waterScores[i] = score }
        } else {
            waterScores.append(score)
        }
        waterScores.sort { $0.ms < $1.ms }
    }

    // Submits the local user's finished water-chug time and broadcasts it.
    func submitWaterTime(_ milliseconds: Int) {
        guard let jam = currentJam else { return }
        let name = supabase.myProfile?.displayName ?? UIDevice.current.name
        applyWaterScore(WaterScore(id: myParticipantID, name: name, ms: milliseconds))
        multipeer.broadcastWater(WaterPayload(
            jamID: jam.id, kind: .result, participantID: myParticipantID,
            name: name, milliseconds: milliseconds
        ))
        // Online members (no Bluetooth link) get it via the server poll.
        if jam.visibility.usesServer {
            Task { try? await supabase.submitJamWaterTime(
                jamID: jam.id, participantID: myParticipantID, name: name, ms: milliseconds
            ) }
        }
    }

    // Clears the leaderboard for everyone in the jam.
    func resetWaterLeaderboard() {
        guard let jam = currentJam else { return }
        waterScores.removeAll()
        multipeer.broadcastWater(WaterPayload(
            jamID: jam.id, kind: .reset, participantID: myParticipantID, name: "", milliseconds: 0
        ))
        if jam.visibility.usesServer {
            Task { try? await supabase.resetJamWater(jam.id) }
        }
    }

    // MARK: Round roulette

    // Picks a random participant to buy the next round and broadcasts the draw so
    // every peer shows the same spin and loser. Anyone in the jam can start one.
    func startRoulette() {
        guard let jam = currentJam else { return }
        let names = jam.participants.map(\.displayName)
        guard names.count >= 2 else { return }
        let winner = Int.random(in: 0..<names.count)
        let starter = supabase.myProfile?.displayName ?? "Jemand"
        let payload = JamRoulettePayload(
            jamID: jam.id, participants: names, winnerIndex: winner, starterName: starter
        )
        multipeer.broadcastRoulette(payload)
        presentRoulette(payload)     // show it locally too (and record the draw id)
        // Online members (no Bluetooth link) get the same draw via the server poll.
        if jam.visibility.usesServer {
            Task { try? await supabase.setJamRoulette(payload) }
        }
    }

    // Presents a roulette draw exactly once, regardless of which transport (or
    // both) delivered it. De-duplicates on the shared draw id.
    private func presentRoulette(_ payload: JamRoulettePayload) {
        guard payload.jamID == currentJam?.id else { return }
        guard payload.id != lastRouletteID else { return }
        lastRouletteID = payload.id
        incomingRoulette = payload
    }

    private func appendPhoto(_ photo: JamPhotoPayload) {
        receivedPhotos.append(photo)
        if receivedPhotos.count > maxReceivedPhotos {
            receivedPhotos.removeFirst(receivedPhotos.count - maxReceivedPhotos)
        }
    }

    // MARK: Photo sharing

    func sendPhoto(_ image: UIImage) throws {
        guard currentJam != nil else { return }
        guard mySettings.sharePhotos else { throw PhotoShareError.sharingDisabled }
        guard multipeer.hasConnectedPeers else { throw PhotoShareError.noPeers }
        let name = supabase.myProfile?.displayName ?? UIDevice.current.name
        // Pin the photo to the current Promille, honoring the share-BAC setting.
        let bac = mySettings.shareBAC ? myCurrentBAC : nil
        multipeer.broadcastPhoto(image, senderName: name, senderBAC: bac)
        // Add to own strip immediately so the sender sees the photo without waiting
        let jpeg = image.jpegData(compressionQuality: 0.7) ?? Data()
        appendPhoto(JamPhotoPayload(senderName: "Du", jpegData: jpeg, senderBAC: bac))
    }

    // MARK: Browsing (for lobby)

    func startBrowsing() {
        availableJamsNearby.removeAll()
        availableJamsFromFriends.removeAll()
        multipeer.startBrowsing()
        Task { await fetchFriendJams() }
    }

    func stopBrowsing() {
        multipeer.stopBrowsing()
    }

    // MARK: Create

    @discardableResult
    func createJam(visibility: Jam.JamVisibility, settings: JamSettings) async throws -> Jam {
        // Server registration happens BEFORE any local state changes: if it
        // throws, no half-created jam (active UI + advertising, but no server
        // row and no timers) is left behind.
        if visibility.usesServer, !supabase.isSignedIn {
            throw JamError.notSignedIn
        }

        mySettings = settings
        myJoinedAt = Date()
        myConnectionType = .code
        amHost = true
        confirmedOnServer = false
        let jam = Jam(
            id: UUID(),
            code: JamCodeGenerator.generate(),
            hostUserID: supabase.session?.userId ?? "",
            hostName: supabase.myProfile?.displayName ?? UIDevice.current.name,
            createdAt: Date(),
            visibility: visibility,
            settings: settings,
            participants: [makeMyParticipant(type: .code)]
        )

        if visibility.usesServer {
            try await supabase.publishJam(jam)
        }

        currentJam = jam
        multipeer.resetAndStartAdvertising(jam)  // clean slate for new host session

        AchievementCatalog.totalJamsCreated += 1
        startTimers(useServer: visibility.usesServer)
        return jam
    }

    // MARK: Join per Code

    func joinJamByCode(_ code: String) async throws {
        guard let jam = try await supabase.findJamByCode(code) else {
            throw JamError.notFound
        }
        // Friends-only jams stay friends-only even with a leaked code: the
        // host must be in the local friend list.
        if jam.visibility == .friendsOnly {
            let hostCode = try await supabase.friendCode(ofUser: jam.hostUserID)
            let mine = Set(friendCodes.map { SupabaseService.sanitizeCode($0) })
            guard let hc = hostCode, mine.contains(SupabaseService.sanitizeCode(hc)) else {
                throw JamError.friendsOnly
            }
        }
        // Register in Supabase so the host and other participants see us in syncParticipants().
        // Ignore 409 Conflict (already in the table from a previous session).
        if jam.visibility.usesServer {
            try await supabase.joinJam(jam.id, participantID: myParticipantID, initialBAC: mySettings.shareBAC ? myCurrentBAC : nil)
        }
        try await joinJam(jam, via: .code)
    }

    // MARK: Join per Proximity

    func joinJamNearby(_ jam: Jam) async throws {
        // If the jam also uses a Supabase server, register there too so non-proximity
        // peers can see us when they poll.
        if jam.visibility.usesServer {
            try? await supabase.joinJam(
                jam.id, participantID: myParticipantID,
                initialBAC: mySettings.shareBAC ? myCurrentBAC : nil,
                connectionType: JamParticipant.ConnectionType.proximity.rawValue
            )
        }
        try await joinJam(jam, via: .proximity)
    }

    // MARK: Join from Friend

    func joinJamFromFriend(_ jam: Jam) async throws {
        try await supabase.joinJam(
            jam.id, participantID: myParticipantID,
            initialBAC: mySettings.shareBAC ? myCurrentBAC : nil,
            connectionType: JamParticipant.ConnectionType.friend.rawValue
        )
        try await joinJam(jam, via: .friend)
    }

    // MARK: Leave
    //
    // Host leaving: delete the entire Jam (no orphaned rows).
    // Guest leaving: remove only this participant row; a Supabase trigger
    // cleans up the jam if no participants remain.

    func leaveJam() {
        guard let jam = currentJam else { return }
        let isHost = amHost
        // Tell connected peers we are leaving so they drop us right away, while
        // the session is still up. Done before stopAll() tears it down.
        multipeer.broadcastControl(
            JamControl(action: .leave, jamID: jam.id,
                       participantID: myParticipantID, userID: supabase.session?.userId)
        )
        stopTimers()
        multipeer.stopAll()
        availableJamsNearby.removeAll()
        availableJamsFromFriends.removeAll()
        receivedPhotos.removeAll()     // clear photos so next jam starts fresh
        waterScores.removeAll()        // games are per-jam; do not leak into the next
        incomingRoulette = nil
        lastRouletteID = nil
        amHost = false
        confirmedOnServer = false
        currentJam = nil               // immediate: UI transitions to lobby right away
        Task {
            guard jam.visibility.usesServer else { return }
            if isHost {
                try? await supabase.deleteJam(jam.id)
            } else {
                try? await supabase.leaveJam(jam.id)
            }
        }
    }

    // MARK: Kick (host only)
    //
    // Removes a participant from the jam. The local list updates immediately, a
    // control message tells the kicked client and the other peers to drop them,
    // and on server jams the participant row is deleted so polling does not
    // re-add them. The kicked client also detects its own removal in
    // syncParticipants when out of Bluetooth range.

    func canKick(_ participant: JamParticipant) -> Bool {
        guard amHost else { return false }
        if participant.id == myParticipantID { return false }
        if let uid = participant.userID, uid == supabase.session?.userId { return false }
        return true
    }

    func kickParticipant(_ participant: JamParticipant) {
        guard let jam = currentJam, canKick(participant) else { return }
        var mutableJam = jam
        mutableJam.participants.removeAll { $0.id == participant.id }
        currentJam = mutableJam

        multipeer.broadcastControl(
            JamControl(action: .kick, jamID: jam.id,
                       participantID: participant.id, userID: participant.userID)
        )

        if jam.visibility.usesServer {
            Task { try? await supabase.removeParticipant(jamID: jam.id, participant: participant) }
        }
    }

    // MARK: Control messages received (proximity)

    private func handleControl(_ control: JamControl) {
        guard var jam = currentJam, jam.id == control.jamID else { return }
        switch control.action {
        case .leave:
            jam.participants.removeAll {
                $0.id == control.participantID
                    || (control.userID != nil && $0.userID == control.userID)
            }
            currentJam = jam
        case .kick:
            let meTargeted = control.participantID == myParticipantID
                || (control.userID != nil && control.userID == supabase.session?.userId)
            if meTargeted {
                leaveJam()
            } else {
                jam.participants.removeAll {
                    $0.id == control.participantID
                        || (control.userID != nil && $0.userID == control.userID)
                }
                currentJam = jam
            }
        }
    }

    // MARK: Live privacy update

    func updateMySettings(_ settings: JamSettings) {
        mySettings = settings
        guard var jam = currentJam else { return }
        // Lookup by stable participant id: matching on userID would hit the
        // first nil-userID row for anonymous users, i.e. possibly a stranger.
        if let idx = jam.participants.firstIndex(where: { $0.id == myParticipantID }) {
            jam.participants[idx].sharedSettings = settings
            currentJam = jam
        }
        broadcastNow()
    }

    // MARK: Private join

    private func joinJam(_ jam: Jam, via type: JamParticipant.ConnectionType) async throws {
        // Keep the user's own sharing preferences — do not overwrite with the host's settings.
        myConnectionType = type
        myJoinedAt = Date()
        amHost = false
        confirmedOnServer = false
        var mutableJam = jam
        mutableJam.participants.upsert(makeMyParticipant(type: type))
        currentJam = mutableJam

        // As a joiner, browse for proximity peers but do NOT advertise as host.
        // activeJamID gates multipeer connections to this jam's peers only.
        multipeer.activeJamID = jam.id
        multipeer.startBrowsing()
        startTimers(useServer: jam.visibility.usesServer)

        // Pull the existing roster right away so the host and other members show
        // up immediately instead of after the first poll interval.
        if jam.visibility.usesServer {
            await syncParticipants()
        }
    }

    // MARK: Timers

    private func startTimers(useServer: Bool) {
        stopTimers()
        statusTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                // Always pull the freshest BAC from shared UserDefaults (written by
                // SessionViewModel.recalculate() every 30 s). onChange(of: currentBAC) in
                // HomeView only fires when the value changes, so we need this separate
                // sync to keep the jam participant row up-to-date between drink events.
                let latestBAC = UserDefaults.widgetShared.double(forKey: UserDefaults.keyCurrentBAC)
                if self.myCurrentBAC != latestBAC {
                    self.myCurrentBAC = latestBAC   // didSet calls updateLocalParticipant + broadcastNow
                } else {
                    self.broadcastNow()             // heartbeat: keep server / peers in sync
                }
            }
        }
        if useServer {
            // Short interval so a new participant appears in near real-time for
            // everyone in the jam. One lightweight GET per tick.
            pollTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
                Task { @MainActor [weak self] in
                    await self?.syncParticipants()
                    await self?.syncJamGames()
                }
            }
        }
    }

    private func stopTimers() {
        statusTimer?.invalidate()
        statusTimer = nil
        pollTimer?.invalidate()
        pollTimer = nil
    }

    // MARK: Local Participant Update

    private func updateLocalParticipant() {
        guard var jam = currentJam else { return }
        let me = makeMyParticipant(type: myConnectionType)
        jam.participants.upsert(me)
        currentJam = jam
        multipeer.broadcastParticipant(me, jamID: jam.id)
    }

    // MARK: Status broadcast

    private func broadcastNow(force: Bool = false) {
        guard let jam = currentJam else { return }
        let me = makeMyParticipant(type: myConnectionType)
        multipeer.broadcastParticipant(me, jamID: jam.id)

        let now = Date()
        guard force || now.timeIntervalSince(lastBroadcastTime) >= 5 else { return }
        lastBroadcastTime = now

        if jam.visibility.usesServer {
            Task {
                try? await supabase.updateMyJamStatus(
                    jamID: jam.id,
                    bac: mySettings.shareBAC ? myCurrentBAC : nil,
                    status: mySettings.shareStatus ? myCurrentStatus : nil,
                    sosActive: mySOSActive
                )
            }
        }
    }

    // MARK: Proximity data received

    private func handleProximityBroadcast(_ broadcast: JamStatusBroadcast) {
        if var jam = currentJam, jam.id == broadcast.jamID {
            jam.participants.upsert(broadcast.participant)
            currentJam = jam
        } else if let idx = availableJamsNearby.firstIndex(where: { $0.id == broadcast.jamID }) {
            availableJamsNearby[idx].participants.upsert(broadcast.participant)
        }
    }

    // Note: proximity peers are intentionally NOT pruned on a staleness timer.
    // Once someone has joined the jam they stay in the list even if they walk
    // out of Bluetooth range; they are only removed on an explicit leave/kick
    // control message or, for server jams, when their row disappears.

    // MARK: Supabase sync

    private func syncParticipants() async {
        guard let jam = currentJam, jam.visibility.usesServer else { return }

        let fresh: [JamParticipant]
        do {
            fresh = try await supabase.fetchJamParticipants(jam.id)
        } catch {
            return
        }
        var mutableJam = jam

        let myUserID = supabase.session?.userId

        // Kick detection: if we previously appeared on the server and now our
        // row is gone, the host removed us. Leave the jam locally. The host
        // never self-kicks (leaving the jam there deletes it for everyone).
        if !amHost {
            let mePresent = fresh.contains {
                $0.id == myParticipantID || ($0.userID != nil && $0.userID == myUserID)
            }
            if mePresent {
                confirmedOnServer = true
            } else if confirmedOnServer {
                leaveJam()
                return
            }
        }

        let now = Date()
        var activeParticipants: [JamParticipant] = []

        for p in fresh {
            if let uid = myUserID, p.userID == uid { continue }
            if p.id == myParticipantID { continue }
            if now.timeIntervalSince(p.lastUpdated) > 120 { continue }
            activeParticipants.append(p)
        }

        // Keep multipeer-only peers (anonymous proximity users without a server
        // row): rebuilding purely from server rows made them flicker in and out
        // every poll cycle. They persist regardless of age so someone who walks
        // out of Bluetooth range is not dropped (removed only on leave/kick).
        let serverIDs = Set(activeParticipants.map(\.id))
        let serverUserIDs = Set(activeParticipants.compactMap(\.userID))
        for p in jam.participants {
            guard p.id != myParticipantID, p.connectionType == .proximity else { continue }
            if serverIDs.contains(p.id) { continue }
            if let uid = p.userID, serverUserIDs.contains(uid) { continue }
            activeParticipants.append(p)
        }

        if let me = jam.participants.first(where: { $0.id == myParticipantID }) {
            activeParticipants.append(me)
        }

        mutableJam.participants = activeParticipants
        currentJam = mutableJam
    }

    // Pulls the server-backed mini-games (roulette draw + water leaderboard) so
    // online-only members see the same spin and times as the Bluetooth peers.
    private func syncJamGames() async {
        guard let jam = currentJam, jam.visibility.usesServer else { return }

        if let draw = try? await supabase.fetchJamRoulette(jam.id) {
            presentRoulette(draw)
        }
        if let server = try? await supabase.fetchJamWaterScores(jam.id) {
            mergeServerWaterScores(server)
        }
    }

    // The server is authoritative for members with a server row: replace those
    // entries with the fetched set (so a reset propagates to everyone), while
    // keeping our own freshly submitted time and any Bluetooth-only proximity
    // peers that have no server row. Mirrors the roster merge in syncParticipants.
    private func mergeServerWaterScores(_ server: [WaterScore]) {
        let serverIDs = Set(server.map(\.id))
        let proximityIDs = Set(
            (currentJam?.participants ?? [])
                .filter { $0.connectionType == .proximity }
                .map(\.id)
        )
        let kept = waterScores.filter { s in
            if serverIDs.contains(s.id) { return false }
            return s.id == myParticipantID || proximityIDs.contains(s.id)
        }
        waterScores = (server + kept).sorted { $0.ms < $1.ms }
    }

    private func fetchFriendJams() async {
        guard supabase.isSignedIn else { return }
        availableJamsFromFriends = (try? await supabase.fetchFriendJams(friendCodes: friendCodes)) ?? []
    }

    // Server-only refresh of friend jams for the Friends tab banner (no
    // Bluetooth advertising/browsing). Skipped while already in a jam so the
    // active session is not disturbed.
    func refreshFriendJamsForLobby(friendCodes codes: [String]) async {
        guard currentJam == nil else { return }
        friendCodes = codes
        await fetchFriendJams()
    }

    // MARK: Self participant builder

    private func makeMyParticipant(type: JamParticipant.ConnectionType) -> JamParticipant {
        let name = supabase.myProfile?.displayName ?? UIDevice.current.name
        return JamParticipant(
            id: myParticipantID,        // stable ID prevents duplication in upsert
            userID: supabase.session?.userId,
            displayName: name,
            avatar: String(name.prefix(1)).uppercased(),
            joinedAt: myJoinedAt ?? Date(),
            connectionType: type,
            currentBAC: mySettings.shareBAC ? myCurrentBAC : nil,
            currentStatus: mySettings.shareStatus ? myCurrentStatus : nil,
            hasSOSActive: mySOSActive,
            lastUpdated: Date(),
            sharedSettings: mySettings
        )
    }
}
