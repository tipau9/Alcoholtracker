import Foundation
@preconcurrency import MultipeerConnectivity
import UIKit

// MARK: - Wire types for proximity data exchange

struct JamStatusBroadcast: Codable {
    let jamID: UUID
    let participant: JamParticipant
}

// Control message for moderation actions exchanged over the proximity channel.
// `leave` lets a departing peer announce itself so others drop it immediately
// instead of relying on a staleness timeout; `kick` removes a participant on
// the host's behalf and signals the target to leave the jam.
struct JamControl: Codable {
    enum Action: String, Codable { case leave, kick, transferHost }
    let action: Action
    let jamID: UUID
    // For leave/kick: the affected participant. For transferHost: the NEW host's
    // participant id (and userID), so the elected device promotes itself and the
    // others update who they show as host.
    let participantID: UUID
    let userID: String?
    // New host's display name on a transferHost (so receivers update the label even
    // if that participant is not in their local roster). Defaulted for back-compat.
    var newHostName: String? = nil
}

// Round roulette: the starter picks the loser and broadcasts the ordered
// participant names + winning index, so every device runs the same spin and
// lands on the same person. Travels over BOTH the Bluetooth channel and (for
// server jams) Supabase, so online-only members take part too.
struct JamRoulettePayload: Codable, Identifiable {
    // Shared draw identity: the SAME value on every device for one draw, so a
    // draw that arrives over both transports is presented only once and is
    // de-duplicated across poll cycles. (Older peers sent no id; init(from:)
    // falls back to a fresh UUID so their payloads still decode.)
    var id = UUID()
    let jamID: UUID
    let participants: [String]
    let winnerIndex: Int
    let starterName: String

    init(id: UUID = UUID(), jamID: UUID, participants: [String], winnerIndex: Int, starterName: String) {
        self.id = id
        self.jamID = jamID
        self.participants = participants
        self.winnerIndex = winnerIndex
        self.starterName = starterName
    }

    enum CodingKeys: String, CodingKey {
        case id, jamID, participants, winnerIndex, starterName
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id           = (try c.decodeIfPresent(UUID.self, forKey: .id)) ?? UUID()
        self.jamID        = try c.decode(UUID.self, forKey: .jamID)
        self.participants = try c.decode([String].self, forKey: .participants)
        self.winnerIndex  = try c.decode(Int.self, forKey: .winnerIndex)
        self.starterName  = try c.decode(String.self, forKey: .starterName)
    }
}

// Water chug leaderboard: a participant broadcasts their finished time, or a
// reset clears everyone's board. Kept tiny so it rides the proximity channel.
struct WaterPayload: Codable {
    enum Kind: String, Codable { case result, reset }
    let jamID: UUID
    let kind: Kind
    let participantID: UUID
    let name: String
    let milliseconds: Int
}

// One participant's best water-chug time, for the in-jam leaderboard.
struct WaterScore: Identifiable, Equatable {
    let id: UUID      // participantID
    let name: String
    let ms: Int
}

// Wraps a status broadcast, a photo, a control message, a roulette draw, or a
// water-contest update so a single send path handles them all.
//
// Mesh routing: `messageID` lets every node forward a message at most once (so it
// can ride A -> B -> C hops without looping), and `ttl` is the remaining hop budget
// that bounds how far it travels. Both are absent in payloads from older builds, so
// `messageID` decodes as nil (those fall back to the legacy host-only relay) and
// `ttl` to 0. New sends always carry them, so a network of updated peers forms a
// true relay mesh instead of the old host-only star.
struct JamEnvelope: Codable {
    enum Payload: Codable {
        case status(JamStatusBroadcast)
        case photo(JamPhotoPayload)
        case control(JamControl)
        case roulette(JamRoulettePayload)
        case water(WaterPayload)
    }
    let payload: Payload
    var messageID: UUID?
    var ttl: Int

    init(payload: Payload, messageID: UUID? = UUID(), ttl: Int = 5) {
        self.payload = payload
        self.messageID = messageID
        self.ttl = ttl
    }

    enum CodingKeys: String, CodingKey { case payload, messageID, ttl }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.payload   = try c.decode(Payload.self, forKey: .payload)
        self.messageID = try c.decodeIfPresent(UUID.self, forKey: .messageID)
        self.ttl       = (try c.decodeIfPresent(Int.self, forKey: .ttl)) ?? 0
    }
}

struct JamPhotoPayload: Codable, Identifiable {
    let senderName: String
    let jpegData: Data       // compressed JPEG, max 200 KB
    // Sender's BAC at the moment the photo was taken, so the memory is pinned to
    // the exact Promille value. nil if the sender hides BAC or it was 0.
    // Optional + a CodingKey means peers on older versions decode it as nil.
    var senderBAC: Double?

    // Local identity only; not part of the wire format, so peers on older
    // versions stay compatible and each receiver assigns its own id.
    var id = UUID()

    enum CodingKeys: String, CodingKey {
        case senderName, jpegData, senderBAC
    }
}

// MARK: - MultipeerService

@Observable
final class MultipeerService: NSObject {

    var discoveredJams: [Jam] = []

    var onStatusReceived: ((JamStatusBroadcast) -> Void)?
    var onJamFound: ((Jam) -> Void)?
    var onPhotoReceived: ((JamPhotoPayload) -> Void)?
    var onControlReceived: ((JamControl) -> Void)?
    var onRouletteReceived: ((JamRoulettePayload) -> Void)?
    var onWaterReceived: ((WaterPayload) -> Void)?

    // The jam the local user is actually in. Connections are only initiated
    // and accepted for this jam: without the gate, every browsing device
    // auto-connected to every advertiser and received status broadcasts
    // before ever joining.
    var activeJamID: UUID?

    var hasConnectedPeers: Bool {
        !(mcSession?.connectedPeers.isEmpty ?? true)
    }

    private let serviceType = "promille-jam"
    private let myPeerID: MCPeerID
    private var mcSession: MCSession?
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?

    // Bounded set of recently-seen mesh message ids, so each message is forwarded
    // and delivered at most once. A ring buffer (order array) caps memory while a
    // Set keeps membership checks O(1).
    private var seenIDs: Set<UUID> = []
    private var seenOrder: [UUID] = []
    private let maxSeen = 512

    @MainActor private func hasSeen(_ id: UUID) -> Bool { seenIDs.contains(id) }

    @MainActor private func rememberSeen(_ id: UUID) {
        guard seenIDs.insert(id).inserted else { return }
        seenOrder.append(id)
        if seenOrder.count > maxSeen {
            let drop = seenOrder.removeFirst()
            seenIDs.remove(drop)
        }
    }

    override init() {
        myPeerID = MCPeerID(displayName: UIDevice.current.name)
        super.init()
    }

    // MARK: Host mode

    func startAdvertisingJam(_ jam: Jam) {
        activeJamID = jam.id
        let discoveryInfo: [String: String] = [
            "jamID": jam.id.uuidString,
            "code":  jam.code,
            "host":  jam.hostName
        ]
        // Reuse existing session to keep connected peers alive (critical for proximity join).
        // Only stop the previous advertiser, never the session itself.
        stopAdvertising()
        _ = mcSession ?? makeSession()
        advertiser = MCNearbyServiceAdvertiser(
            peer: myPeerID,
            discoveryInfo: discoveryInfo,
            serviceType: serviceType
        )
        advertiser?.delegate = self
        advertiser?.startAdvertisingPeer()
        startBrowsing()
    }

    func resetAndStartAdvertising(_ jam: Jam) {
        stopAll()
        startAdvertisingJam(jam)
    }

    // MARK: Browser mode (joining)

    func startBrowsing() {
        _ = mcSession ?? makeSession()
        // Forget previous discoveries so reopening the lobby re-reports jams:
        // the guard in foundPeer would otherwise block jams seen in an earlier
        // browsing session from ever reappearing.
        discoveredJams.removeAll()
        browser?.stopBrowsingForPeers()
        browser = MCNearbyServiceBrowser(peer: myPeerID, serviceType: serviceType)
        browser?.delegate = self
        browser?.startBrowsingForPeers()
    }

    func stopBrowsing() {
        browser?.stopBrowsingForPeers()
        browser = nil
    }

    func stopAdvertising() {
        advertiser?.stopAdvertisingPeer()
        advertiser = nil
    }

    func stopAll() {
        stopAdvertising()
        stopBrowsing()
        mcSession?.disconnect()
        mcSession = nil
        discoveredJams.removeAll()
        activeJamID = nil
    }

    // MARK: Data send

    func broadcastParticipant(_ participant: JamParticipant, jamID: UUID) {
        guard let s = mcSession, !s.connectedPeers.isEmpty else { return }
        let broadcast = JamStatusBroadcast(jamID: jamID, participant: participant)
        let envelope = JamEnvelope(payload: .status(broadcast))
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? s.send(data, toPeers: s.connectedPeers, with: .reliable)
    }

    // Sends a moderation control message (leave/kick) to all connected peers.
    // The host relays it on, so it reaches every member of the star topology.
    func broadcastControl(_ control: JamControl) {
        guard let s = mcSession, !s.connectedPeers.isEmpty else { return }
        let envelope = JamEnvelope(payload: .control(control))
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? s.send(data, toPeers: s.connectedPeers, with: .reliable)
    }

    // Broadcasts a round-roulette draw to all connected peers; the host relays it
    // so every member of the star topology runs the same spin.
    func broadcastRoulette(_ payload: JamRoulettePayload) {
        guard let s = mcSession, !s.connectedPeers.isEmpty else { return }
        let envelope = JamEnvelope(payload: .roulette(payload))
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? s.send(data, toPeers: s.connectedPeers, with: .reliable)
    }

    // Broadcasts a water-contest result or reset to all connected peers.
    func broadcastWater(_ payload: WaterPayload) {
        guard let s = mcSession, !s.connectedPeers.isEmpty else { return }
        let envelope = JamEnvelope(payload: .water(payload))
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? s.send(data, toPeers: s.connectedPeers, with: .reliable)
    }

    // Compresses image to JPEG (max 200 KB) and broadcasts to all connected peers.
    func broadcastPhoto(_ image: UIImage, senderName: String, senderBAC: Double?) {
        guard let s = mcSession, !s.connectedPeers.isEmpty else { return }
        // Resize first: quality reduction alone cannot get a 12 MP photo
        // under the cap, and unresized sends blew past the documented 200 KB.
        let scaled = image.resizedToFit(maxDimension: 1280)
        var quality: CGFloat = 0.7
        var jpeg = scaled.jpegData(compressionQuality: quality) ?? Data()
        while jpeg.count > 200_000, quality > 0.1 {
            quality -= 0.15
            jpeg = scaled.jpegData(compressionQuality: quality) ?? jpeg
        }
        guard !jpeg.isEmpty, jpeg.count <= 400_000 else { return }
        let payload = JamPhotoPayload(senderName: senderName, jpegData: jpeg, senderBAC: senderBAC)
        let envelope = JamEnvelope(payload: .photo(payload))
        guard let data = try? JSONEncoder().encode(envelope) else { return }
        try? s.send(data, toPeers: s.connectedPeers, with: .reliable)
    }

    // MARK: Private helpers

    @discardableResult
    private func makeSession() -> MCSession {
        // FIX BUG4: .required fails between certain device/OS combinations; .optional keeps privacy
        // while avoiding handshake rejections that silently prevent peers from connecting
        let s = MCSession(peer: myPeerID, securityIdentity: nil, encryptionPreference: .optional)
        s.delegate = self
        mcSession = s
        return s
    }
}

// MARK: - Image resize helper

private extension UIImage {
    func resizedToFit(maxDimension: CGFloat) -> UIImage {
        let maxSide = max(size.width, size.height)
        guard maxSide > maxDimension, maxSide > 0 else { return self }
        let factor = maxDimension / maxSide
        let newSize = CGSize(width: size.width * factor, height: size.height * factor)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}

// MARK: - MCSessionDelegate

extension MultipeerService: MCSessionDelegate {
    nonisolated func session(
        _ session: MCSession,
        peer peerID: MCPeerID,
        didChange state: MCSessionState
    ) {}

    nonisolated func session(
        _ session: MCSession,
        didReceive data: Data,
        fromPeer peerID: MCPeerID
    ) {
        let captured = data
        let sender = peerID
        Task { @MainActor [weak self] in
            guard let self else { return }
            // Try the new envelope format first, fall back to legacy bare broadcast.
            if let envelope = try? JSONDecoder().decode(JamEnvelope.self, from: captured) {
                if let mid = envelope.messageID {
                    // Mesh path: drop if already seen so a message never circulates,
                    // otherwise remember it and forward with one less hop to every
                    // OTHER connected peer (not just the host), so it can travel
                    // A -> B -> C across a chain. Then deliver locally.
                    if self.hasSeen(mid) { return }
                    self.rememberSeen(mid)
                    if envelope.ttl > 1, let s = self.mcSession {
                        let others = s.connectedPeers.filter { $0 != sender }
                        if !others.isEmpty {
                            var forwarded = envelope
                            forwarded.ttl = envelope.ttl - 1
                            if let out = try? JSONEncoder().encode(forwarded) {
                                try? s.send(out, toPeers: others, with: .reliable)
                            }
                        }
                    }
                    self.deliver(envelope.payload)
                } else {
                    // Legacy envelope (no mesh header): keep the old host-only relay
                    // so updated and older peers interoperate.
                    self.legacyRelay(captured, from: sender)
                    self.deliver(envelope.payload)
                }
            } else if let broadcast = try? JSONDecoder().decode(JamStatusBroadcast.self, from: captured) {
                self.legacyRelay(captured, from: sender)
                self.onStatusReceived?(broadcast)
            }
        }
    }

    // Delivers a decoded payload to the matching callback.
    @MainActor
    private func deliver(_ payload: JamEnvelope.Payload) {
        switch payload {
        case .status(let broadcast): onStatusReceived?(broadcast)
        case .photo(let photo):      onPhotoReceived?(photo)
        case .control(let control):  onControlReceived?(control)
        case .roulette(let r):       onRouletteReceived?(r)
        case .water(let w):          onWaterReceived?(w)
        }
    }

    // Legacy star relay: only the advertising host forwards, and only one hop, so
    // pre-mesh peers (which send no messageID) still reach each other via the host.
    @MainActor
    private func legacyRelay(_ data: Data, from sender: MCPeerID) {
        guard advertiser != nil, let s = mcSession else { return }
        let others = s.connectedPeers.filter { $0 != sender }
        if !others.isEmpty { try? s.send(data, toPeers: others, with: .reliable) }
    }

    nonisolated func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}
    nonisolated func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {}
    nonisolated func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {}
}

// MARK: - MCNearbyServiceAdvertiserDelegate

extension MultipeerService: MCNearbyServiceAdvertiserDelegate {
    nonisolated func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didNotStartAdvertisingPeer error: Error
    ) {}

    nonisolated func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didReceiveInvitationFromPeer peerID: MCPeerID,
        withContext context: Data?,
        invitationHandler: @escaping (Bool, MCSession?) -> Void
    ) {
        let contextData = context
        Task { @MainActor [weak self] in
            guard let self, let session = self.mcSession, let myJam = self.activeJamID else {
                invitationHandler(false, nil)
                return
            }
            // The inviter sends its jam id as context: only peers joining THIS
            // jam get in. Blocks lobby browsers and members of other jams.
            guard
                let data = contextData,
                let jamString = String(data: data, encoding: .utf8),
                jamString == myJam.uuidString
            else {
                invitationHandler(false, nil)
                return
            }
            invitationHandler(true, session)
        }
    }
}

// MARK: - MCNearbyServiceBrowserDelegate

extension MultipeerService: MCNearbyServiceBrowserDelegate {
    nonisolated func browser(
        _ browser: MCNearbyServiceBrowser,
        didNotStartBrowsingForPeers error: Error
    ) {}

    nonisolated func browser(
        _ browser: MCNearbyServiceBrowser,
        foundPeer peerID: MCPeerID,
        withDiscoveryInfo info: [String: String]?
    ) {
        guard
            let info,
            let code    = info["code"],
            let host    = info["host"],
            let idStr   = info["jamID"],
            let jamID   = UUID(uuidString: idStr)
        else { return }

        Task { @MainActor [weak self] in
            guard let self else { return }
            // Connect only to peers of the jam we are actually in. While just
            // browsing the lobby no connection is made: discoveryInfo alone
            // provides everything the list needs, and no status data flows
            // before the user joins.
            if let myJam = self.activeJamID, myJam == jamID, let s = self.mcSession {
                let context = myJam.uuidString.data(using: .utf8)
                browser.invitePeer(peerID, to: s, withContext: context, timeout: 30)
            }
            guard !self.discoveredJams.contains(where: { $0.id == jamID }) else { return }
            let placeholder = Jam(
                id: jamID,
                code: code,
                hostUserID: "",
                hostName: host,
                createdAt: Date(),
                visibility: .proximityAndCode,
                settings: JamSettings(),
                participants: []
            )
            self.discoveredJams.append(placeholder)
            self.onJamFound?(placeholder)
        }
    }

    nonisolated func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {}
}
