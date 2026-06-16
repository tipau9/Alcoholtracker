import Foundation
import Security

// MARK: - SupabaseError

enum SupabaseError: LocalizedError {
    case notConfigured
    case notSignedIn
    case invalidCredentials
    case networkError(Error)
    case serverError(Int, String)
    case decodingError(Error)
    case friendNotFound
    case emailConfirmationRequired

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Supabase ist nicht konfiguriert. Bitte SupabaseConfig.swift ausfuellen."
        case .notSignedIn:
            return "Nicht angemeldet."
        case .invalidCredentials:
            return "E-Mail oder Passwort falsch."
        case .networkError(let e):
            return "Netzwerkfehler: \(e.localizedDescription)"
        case .serverError(let code, let msg):
            return "Serverfehler \(code): \(msg)"
        case .decodingError:
            return "Serverantwort konnte nicht verarbeitet werden."
        case .friendNotFound:
            return "Kein Nutzer mit diesem Code gefunden."
        case .emailConfirmationRequired:
            return "Bestätigungsmail gesendet. Bitte E-Mail bestätigen und dann anmelden."
        }
    }
}

// MARK: - GoTrue response models (file-private)

private struct SBAuthResponse: Decodable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
    let user: SBUser
    enum CodingKeys: String, CodingKey {
        case accessToken  = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn    = "expires_in"
        case user
    }
}

private struct SBUser: Decodable { let id: String }

// MARK: - SupabaseService

@MainActor
@Observable
final class SupabaseService {

    private(set) var session: AccountSession?
    private(set) var myProfile: FriendProfile?

    var isSignedIn: Bool { session != nil }
    var isConfigured: Bool { SupabaseConfig.isReady }

    // Reusable ISO-8601 formatters. Building an ISO8601DateFormatter is
    // expensive, so we create each exactly once and share it across every
    // decode. ISO8601DateFormatter is thread-safe for `date(from:)` once its
    // options are set, and these are only ever read; nonisolated(unsafe) lets
    // the Sendable decoding closure reference them without main-actor hops.
    nonisolated(unsafe) private static let iso8601WithFractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    nonisolated(unsafe) private static let iso8601Plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    // ISO-8601 decoder that handles both fractional and whole seconds.
    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { container in
            let str = try container.singleValueContainer().decode(String.self)
            if let date = iso8601WithFractional.date(from: str) { return date }
            if let date = iso8601Plain.date(from: str) { return date }
            throw DecodingError.dataCorruptedError(
                in: try container.singleValueContainer(),
                debugDescription: "Cannot parse date string: \(str)"
            )
        }
        return d
    }()

    init() {
        session = Keychain.loadSession()
    }

    // MARK: Auth

    func signUp(email: String, password: String, displayName: String) async throws {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let body: [String: Any] = [
            "email":    email,
            "password": password,
            "data":     ["display_name": displayName],
        ]
        let data = try await authPOST("/auth/v1/signup", body: body)
        applySession(try decodeGoTrue(data))
        try await syncMyProfile()
    }

    func signIn(email: String, password: String) async throws {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let body: [String: Any] = ["email": email, "password": password]
        let data = try await authPOST("/auth/v1/token?grant_type=password", body: body)
        applySession(try decodeGoTrue(data))
        try await syncMyProfile()
    }

    func signOut() async {
        guard let s = session else { return }
        var req = buildRequest("/auth/v1/logout", method: "POST")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        _ = try? await URLSession.shared.data(for: req)
        clearSession()
    }

    // MARK: Account Deletion
    //
    // Run once in the Supabase SQL Editor to allow users to delete their own account:
    //
    // CREATE OR REPLACE FUNCTION delete_user()
    // RETURNS void
    // LANGUAGE plpgsql
    // SECURITY DEFINER
    // AS $$
    // BEGIN
    //   DELETE FROM auth.users WHERE id = auth.uid();
    // END;
    // $$;

    func deleteAccount() async throws {
        guard session != nil else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restPOST("/rest/v1/rpc/delete_user", body: [:])
        clearSession()
    }

    // MARK: - Account history sync (drinks + day notes)
    //
    // Backs up the on-device history to the signed-in account. Orchestration and
    // SwiftData live in HistorySyncService; this layer only speaks PostgREST.
    // RLS scopes every row to auth.uid(), so the GETs need no user_id filter.

    struct RemoteDrink: Decodable {
        let id: String
        let name: String
        let volume: Double
        let abv: Double
        let calories: Int
        let iconName: String
        let category: String
        let mixerVolume: Double
        let mixerWaterContent: Double
        let drinkDurationMinutes: Double
        let templateID: String?
        let consumedAt: Date

        enum CodingKeys: String, CodingKey {
            case id, name, volume, abv, calories, category
            case iconName             = "icon_name"
            case mixerVolume          = "mixer_volume"
            case mixerWaterContent    = "mixer_water_content"
            case drinkDurationMinutes = "drink_duration_minutes"
            case templateID           = "template_id"
            case consumedAt           = "consumed_at"
        }
    }

    struct RemoteDayNote: Decodable {
        let dayStart: String   // Postgres `date`, e.g. "2026-06-15"
        let text: String
        let mood: Int

        enum CodingKeys: String, CodingKey {
            case dayStart = "day_start"
            case text, mood
        }
    }

    func fetchDrinkHistory() async throws -> [RemoteDrink] {
        try await refreshIfNeeded()
        let data = try await restGET("/rest/v1/drink_history?select=*")
        return (try? Self.decoder.decode([RemoteDrink].self, from: data)) ?? []
    }

    func uploadDrinkHistory(_ rows: [[String: Any]]) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let withUser = rows.map { row -> [String: Any] in
            var r = row; r["user_id"] = s.userId; return r
        }
        try await upsert("/rest/v1/drink_history", rows: withUser)
    }

    func deleteDrinkHistory(ids: [UUID]) async throws {
        guard !ids.isEmpty else { return }
        try await refreshIfNeeded()
        let list = ids.map(\.uuidString).joined(separator: ",")
        try await restDELETE("/rest/v1/drink_history?id=in.(\(list))")
    }

    func fetchDayNotes() async throws -> [RemoteDayNote] {
        try await refreshIfNeeded()
        let data = try await restGET("/rest/v1/day_notes?select=*")
        return (try? Self.decoder.decode([RemoteDayNote].self, from: data)) ?? []
    }

    func uploadDayNotes(_ rows: [[String: Any]]) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let withUser = rows.map { row -> [String: Any] in
            var r = row; r["user_id"] = s.userId; return r
        }
        try await upsert("/rest/v1/day_notes", rows: withUser)
    }

    func deleteDayNotes(days: [String]) async throws {
        guard !days.isEmpty else { return }
        try await refreshIfNeeded()
        let list = days.joined(separator: ",")
        try await restDELETE("/rest/v1/day_notes?day_start=in.(\(list))")
    }

    // Single per-user JSON document (profile/settings, water log, custom mixes
    // and drinks). Returned as the raw `data` object for the caller to decode.
    func fetchUserBackup() async throws -> Data? {
        try await refreshIfNeeded()
        let raw = try await restGET("/rest/v1/user_backup?select=data")
        guard let arr = try? JSONSerialization.jsonObject(with: raw) as? [[String: Any]],
              let dataObject = arr.first?["data"] else { return nil }
        return try? JSONSerialization.data(withJSONObject: dataObject)
    }

    func uploadUserBackup(_ dataObject: Any) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let row: [String: Any] = ["user_id": s.userId, "data": dataObject]
        try await upsert("/rest/v1/user_backup", rows: [row])
    }

    // Bulk upsert (POST + resolution=merge-duplicates) of a JSON array of rows.
    private func upsert(_ path: String, rows: [[String: Any]]) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        guard !rows.isEmpty else { return }
        var req = buildRequest(path, method: "POST")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("return=minimal,resolution=merge-duplicates", forHTTPHeaderField: "Prefer")
        req.httpBody = try JSONSerialization.data(withJSONObject: rows)
        _ = try await perform(req)
    }

    // MARK: Profile

    func syncMyProfile() async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let data = try await restGET("/rest/v1/profiles?id=eq.\(s.userId)&select=*")
        myProfile = try Self.decoder.decode([FriendProfile].self, from: data).first
    }

    func publishBAC(_ bac: Double) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let isoNow = Self.iso8601WithFractional.string(from: Date())
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["current_bac": bac, "bac_updated_at": isoNow]
        )
        myProfile?.currentBac = bac
        myProfile?.bacUpdatedAt = Date()
    }

    func updateDisplayName(_ name: String) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["display_name": name]
        )
        myProfile?.displayName = name
    }

    func updateSharing(_ sharing: Bool) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["is_sharing": sharing]
        )
        myProfile?.isSharing = sharing
    }

    // Publishes the user's Probezeit (0,0 ‰ limit) so friends can show the
    // correct "Fahrbereit" / "Darf nicht mehr fahren" label using the driver's
    // own limit. Requires a boolean column on the profiles table:
    //   ALTER TABLE profiles ADD COLUMN is_probationary boolean NOT NULL DEFAULT false;
    func updateProbation(_ on: Bool) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["is_probationary": on]
        )
        myProfile?.isProbationary = on
    }

    // App-wide SOS flag on the user's profile, so friends (not only jam members)
    // see it on their next poll. Requires a boolean column on the profiles table:
    //   ALTER TABLE profiles ADD COLUMN sos_active boolean NOT NULL DEFAULT false;
    //   ALTER TABLE profiles ADD COLUMN sos_updated_at timestamptz;
    func setSOS(_ active: Bool) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let isoNow = Self.iso8601WithFractional.string(from: Date())
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["sos_active": active, "sos_updated_at": isoNow]
        )
        myProfile?.sosActive = active
    }

    // MARK: Friends

    // Friend codes are user input and end up in PostgREST query strings:
    // strip everything outside A-Z/0-9 so pasted junk cannot break the URL.
    nonisolated static func sanitizeCode(_ code: String) -> String {
        String(code.uppercased().unicodeScalars.filter {
            ("A"..."Z").contains(Character($0)) || ("0"..."9").contains(Character($0))
        }.map(Character.init))
    }

    func lookupFriend(code: String) async throws -> FriendProfile {
        guard isConfigured else { throw SupabaseError.notConfigured }
        try await refreshIfNeeded()
        let clean = Self.sanitizeCode(code)
        guard !clean.isEmpty else { throw SupabaseError.friendNotFound }
        let data = try await restRPC("friend_profiles_by_codes", body: ["p_codes": [clean]])
        let list = try Self.decoder.decode([FriendProfile].self, from: data)
        guard let found = list.first else { throw SupabaseError.friendNotFound }
        return found
    }

    func fetchFriendsBAC(codes: [String]) async throws -> [FriendProfile] {
        guard isConfigured, !codes.isEmpty else { return [] }
        try await refreshIfNeeded()
        let cleaned = codes.map { Self.sanitizeCode($0) }.filter { !$0.isEmpty }
        guard !cleaned.isEmpty else { return [] }
        let data = try await restRPC("friend_profiles_by_codes", body: ["p_codes": cleaned])
        // The function returns non-sharing friends too (with BAC nulled out);
        // keep the old behaviour of surfacing only the ones actively sharing.
        return try Self.decoder.decode([FriendProfile].self, from: data)
            .filter { $0.isSharing }
    }

    // Friend code of a single user id, used to verify friends-only jam access.
    func friendCode(ofUser userID: String) async throws -> String? {
        guard !userID.isEmpty, UUID(uuidString: userID) != nil else { return nil }
        try await refreshIfNeeded()
        let data = try await restRPC("friend_profiles_by_ids", body: ["p_ids": [userID]])
        return try Self.decoder.decode([FriendProfile].self, from: data).first?.friendCode
    }

    // Profiles for a set of user ids (mutual friends display).
    func fetchProfiles(ids: [String]) async throws -> [FriendProfile] {
        let valid = ids.filter { UUID(uuidString: $0) != nil }
        guard !valid.isEmpty else { return [] }
        try await refreshIfNeeded()
        let data = try await restRPC("friend_profiles_by_ids", body: ["p_ids": valid])
        return try Self.decoder.decode([FriendProfile].self, from: data)
    }

    // MARK: Friendships (server-side follow model)
    //
    // Run once in the Supabase SQL Editor:
    //
    // CREATE TABLE IF NOT EXISTS friendships (
    //   follower_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    //   friend_id   uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    //   created_at  timestamptz NOT NULL DEFAULT now(),
    //   PRIMARY KEY (follower_id, friend_id)
    // );
    // ALTER TABLE friendships ENABLE ROW LEVEL SECURITY;
    // CREATE POLICY "friendships read"   ON friendships FOR SELECT TO authenticated USING (true);
    // CREATE POLICY "friendships insert" ON friendships FOR INSERT TO authenticated WITH CHECK (auth.uid() = follower_id);
    // CREATE POLICY "friendships delete" ON friendships FOR DELETE TO authenticated USING (auth.uid() = follower_id);
    //
    // And for shared achievements:
    //
    // ALTER TABLE profiles ADD COLUMN IF NOT EXISTS achievements jsonb NOT NULL DEFAULT '[]'::jsonb;
    //
    // This is a follow model, not request/accept: adding a friend registers
    // the edge server-side so the other person's profile can show "follows
    // you too" and mutual friends can be computed.

    func addFriendship(friendID: String) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        guard UUID(uuidString: friendID) != nil, friendID != s.userId else { return }
        try await refreshIfNeeded()
        try await restPOST("/rest/v1/friendships", body: [
            "follower_id": s.userId,
            "friend_id":   friendID
        ], ignoreDuplicates: true)
    }

    func removeFriendship(friendID: String) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        guard UUID(uuidString: friendID) != nil else { return }
        try await refreshIfNeeded()
        try await restDELETE(
            "/rest/v1/friendships?follower_id=eq.\(s.userId)&friend_id=eq.\(friendID)"
        )
    }

    // User ids this person follows (their friend list).
    func fetchFriendIDs(of userID: String) async throws -> [String] {
        guard UUID(uuidString: userID) != nil else { return [] }
        try await refreshIfNeeded()
        let data = try await restGET("/rest/v1/friendships?follower_id=eq.\(userID)&select=friend_id")
        struct Row: Decodable {
            let friendID: String
            enum CodingKeys: String, CodingKey { case friendID = "friend_id" }
        }
        return try Self.decoder.decode([Row].self, from: data).map(\.friendID)
    }

    // MARK: Shared achievements

    func publishAchievements(_ ids: [String]) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restPATCH(
            "/rest/v1/profiles?id=eq.\(s.userId)",
            body: ["achievements": ids.sorted()]
        )
    }

    // MARK: Jams

    func publishJam(_ jam: Jam) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        let settingsData = try JSONEncoder().encode(jam.settings)
        let settingsObj  = try JSONSerialization.jsonObject(with: settingsData)
        try await restPOST("/rest/v1/jams", body: [
            "id":           jam.id.uuidString,
            "code":         jam.code,
            "host_user_id": s.userId,
            "host_name":    jam.hostName,
            "visibility":   jam.visibility.rawValue,
            "settings":     settingsObj
        ])
        let me = jam.participants.first(where: { $0.userID == s.userId })
        var participantBody: [String: Any] = [
            "id":              (me?.id ?? UUID()).uuidString,
            "jam_id":          jam.id.uuidString,
            "user_id":         s.userId,
            "display_name":    myProfile?.displayName ?? jam.hostName,
            "connection_type": "Code",
            "has_sos_active":  false,
            "last_updated":    ISO8601DateFormatter().string(from: Date())
        ]
        participantBody["current_bac"] = me?.currentBAC ?? NSNull()
        
        try await restPOST("/rest/v1/jam_participants", body: participantBody, ignoreDuplicates: true)
    }

    func findJamByCode(_ code: String) async throws -> Jam? {
        try await refreshIfNeeded()
        let clean = Self.sanitizeCode(code)
        guard !clean.isEmpty else { return nil }
        let data  = try await restGET("/rest/v1/jams?code=eq.\(clean)&ended_at=is.null&select=*")
        return try Self.decoder.decode([JamRow].self, from: data).compactMap { $0.toJam() }.first
    }

    func joinJam(_ jamID: UUID, participantID: UUID, initialBAC: Double?, connectionType: String = "Code") async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        var body: [String: Any] = [
            "id":              participantID.uuidString,
            "jam_id":          jamID.uuidString,
            "user_id":         s.userId,
            "display_name":    myProfile?.displayName ?? "Anonym",
            "connection_type": connectionType,
            "has_sos_active":  false,
            "last_updated":    ISO8601DateFormatter().string(from: Date())
        ]
        body["current_bac"] = initialBAC ?? NSNull()

        try await restPOST("/rest/v1/jam_participants", body: body, ignoreDuplicates: true)
    }

    func leaveJam(_ jamID: UUID) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        try await restDELETE(
            "/rest/v1/jam_participants?jam_id=eq.\(jamID.uuidString)&user_id=eq.\(s.userId)"
        )
    }

    // Deletes the entire jam row (host-only action); cascade removes participants via DB FK.
    func deleteJam(_ jamID: UUID) async throws {
        try await refreshIfNeeded()
        try await restDELETE("/rest/v1/jams?id=eq.\(jamID.uuidString)")
    }

    // Host-initiated removal of another participant (kick). Deletes that one
    // participant row by its id. The host-delete RLS policy this needs is in
    // supabase/jams_security.sql ("jam_participants_delete_self_or_host").
    func removeParticipant(jamID: UUID, participant: JamParticipant) async throws {
        try await refreshIfNeeded()
        try await restDELETE(
            "/rest/v1/jam_participants?jam_id=eq.\(jamID.uuidString)&id=eq.\(participant.id.uuidString)"
        )
    }

    // Privacy filtering happens caller-side: withheld values arrive here as nil
    // and are written as SQL null, so the server never stores hidden data.
    func updateMyJamStatus(
        jamID: UUID,
        bac: Double?,
        status: String?,
        sosActive: Bool
    ) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        try await refreshIfNeeded()
        var body: [String: Any] = [
            "has_sos_active": sosActive,
            "last_updated":   ISO8601DateFormatter().string(from: Date())
        ]
        body["current_bac"]    = bac ?? NSNull()
        body["current_status"] = status ?? NSNull()
        try await restPATCH(
            "/rest/v1/jam_participants?jam_id=eq.\(jamID.uuidString)&user_id=eq.\(s.userId)",
            body: body
        )
    }

    // Roster read goes through a SECURITY DEFINER function that only returns
    // members of a jam the caller belongs to (or hosts), so jam_participants is
    // no longer directly SELECTable and cannot be enumerated across jams.
    // See supabase/jams_security.sql.
    func fetchJamParticipants(_ jamID: UUID) async throws -> [JamParticipant] {
        try await refreshIfNeeded()
        let data = try await restRPC(
            "jam_participants_for_member",
            body: ["p_jam_id": jamID.uuidString]
        )
        return try Self.decoder.decode([JamParticipantRow].self, from: data)
            .compactMap { $0.toParticipant() }
    }

    // RLS note — the "Visible jams" policy must NOT use EXISTS on jam_participants if
    // jam_participants itself has a policy that reads from jams (infinite recursion, HTTP 500).
    // Safe alternative: disable RLS on the jams table and filter client-side, OR use a
    // SECURITY DEFINER function. For now the app filters client-side (see below).
    //
    // "Von Freunden" means exactly that: only jams whose host is one of the
    // locally stored friends are returned. The codes are resolved to user ids
    // first; without that filter every signed-in user would see every
    // friends-only jam worldwide.
    func fetchFriendJams(friendCodes: [String]) async throws -> [Jam] {
        guard let s = session else { return [] }
        let cleaned = friendCodes.map { Self.sanitizeCode($0) }.filter { !$0.isEmpty }
        guard !cleaned.isEmpty else { return [] }
        try await refreshIfNeeded()

        let profileData = try await restRPC("friend_profiles_by_codes", body: ["p_codes": cleaned])
        let hostIDs = try Self.decoder.decode([FriendProfile].self, from: profileData)
            .map(\.id)
            .filter { UUID(uuidString: $0) != nil }
        guard !hostIDs.isEmpty else { return [] }

        let idList = hostIDs.joined(separator: ",")
        let data = try await restGET(
            "/rest/v1/jams?host_user_id=in.(\(idList))&visibility=eq.Nur%20Freunde&ended_at=is.null&select=*"
        )
        let all = try Self.decoder.decode([JamRow].self, from: data).compactMap { $0.toJam() }
        // Exclude jams hosted by self (already shown as currentJam)
        return all.filter { $0.hostUserID != s.userId }
    }

    // MARK: Community Drinks

    func fetchCommunityDrinks() async throws -> [CommunityDrinkRow] {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let data = try await publicGET(
            "/rest/v1/community_drinks?status=eq.approved&select=*&order=confirmed_count.desc&limit=2000"
        )
        return try Self.decoder.decode([CommunityDrinkRow].self, from: data)
    }

    func lookupCommunityBarcode(_ barcode: String) async throws -> CommunityDrinkRow? {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let encoded = barcode.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? barcode
        let data = try await publicGET(
            "/rest/v1/community_drinks?barcode=eq.\(encoded)&status=eq.approved&select=*&limit=1"
        )
        return try Self.decoder.decode([CommunityDrinkRow].self, from: data).first
    }

    // Self-learning community DB: each scan is one vote (one per install via the
    // anonymous voter id). The server-side contribute_drink() RPC inserts the
    // drink as 'pending', records the vote, and auto-approves once enough
    // distinct devices confirmed the same barcode. Manual approval/rejection in
    // the Supabase dashboard still works and takes precedence (a 'rejected' row
    // is never auto-approved). See supabase/community_drinks.sql.
    func contributeDrink(
        name: String,
        category: DrinkCategory,
        volume: Double,
        abv: Double,
        calories: Int,
        iconName: String,
        barcode: String
    ) async throws {
        guard isConfigured, !barcode.isEmpty else { return }
        try await communityPOST("/rest/v1/rpc/contribute_drink", body: [
            "p_barcode":   barcode,
            "p_name":      name,
            "p_category":  category.rawValue,
            "p_volume":    volume,
            "p_abv":       abv,
            "p_calories":  calories,
            "p_icon_name": iconName,
            "p_voter":     Self.anonVoterID()
        ])
    }

    // MARK: Community Mixes (self-learning, same crowd+manual model as drinks)

    func fetchCommunityMixes() async throws -> [CommunityMixRow] {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let data = try await publicGET(
            "/rest/v1/community_mixes?status=eq.approved&select=*&order=confirmed_count.desc&limit=500"
        )
        return try Self.decoder.decode([CommunityMixRow].self, from: data)
    }

    // Shares a user-built mix to the community via the contribute_mix RPC: stored
    // as pending, one vote per install, auto-approved once enough distinct
    // devices share the same recipe (or approved/rejected manually in the
    // dashboard). See supabase/community_mixes.sql.
    func contributeMix(
        name: String,
        ingredients: [MixIngredient],
        totalVolume: Double,
        totalAbv: Double,
        calories: Int
    ) async throws {
        guard isConfigured else { throw SupabaseError.notConfigured }
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !ingredients.isEmpty else { return }
        // Encode ingredients to a JSON value PostgREST passes straight to jsonb.
        let ingredientJSON = (try? JSONSerialization.jsonObject(
            with: JSONEncoder().encode(ingredients)
        )) ?? []
        try await communityPOST("/rest/v1/rpc/contribute_mix", body: [
            "p_name":         trimmed,
            "p_ingredients":  ingredientJSON,
            "p_total_volume": totalVolume,
            "p_total_abv":    totalAbv,
            "p_calories":     calories,
            "p_voter":        Self.anonVoterID()
        ])
    }

    // Stable anonymous id for crowd voting, persisted per install. Counts
    // distinct devices without needing the user to be signed in, and keeps one
    // device from inflating a drink's confirmation count by re-scanning.
    private static func anonVoterID() -> String {
        let key = "community.voterID"
        if let v = UserDefaults.standard.string(forKey: key) { return v }
        let v = UUID().uuidString
        UserDefaults.standard.set(v, forKey: key)
        return v
    }

    // MARK: HTTP primitives

    private func authPOST(_ path: String, body: [String: Any]) async throws -> Data {
        var req = buildRequest(path, method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(req)
    }

    // Public GET — uses anon key as Bearer (works for RLS policies that allow anon role)
    private func publicGET(_ path: String) async throws -> Data {
        var req = buildRequest(path, method: "GET")
        req.setValue("Bearer \(SupabaseConfig.anonKey)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        return try await perform(req)
    }

    // Public POST — uses anon key as Bearer; ignoreDuplicates uses ON CONFLICT DO NOTHING
    private func publicPOST(_ path: String, body: [String: Any], ignoreDuplicates: Bool = false) async throws {
        var req = buildRequest(path, method: "POST")
        req.setValue("Bearer \(SupabaseConfig.anonKey)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let prefer = ignoreDuplicates
            ? "return=minimal,resolution=ignore-duplicates"
            : "return=minimal"
        req.setValue(prefer, forHTTPHeaderField: "Prefer")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        _ = try await perform(req)
    }

    // Community contribution write. Prefers the signed-in user's token so the
    // server keys the crowd vote on a real account id (much harder to sybil than
    // the request IP, which is the fallback for users who never signed in). The
    // contribute_* RPCs are SECURITY DEFINER and validate the payload, so either
    // role only ever reaches the controlled insert-and-vote path.
    private func communityPOST(_ path: String, body: [String: Any]) async throws {
        var req = buildRequest(path, method: "POST")
        let token = session?.accessToken ?? SupabaseConfig.anonKey
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        _ = try await perform(req)
    }

    private func restGET(_ path: String) async throws -> Data {
        guard let s = session else { throw SupabaseError.notSignedIn }
        var req = buildRequest(path, method: "GET")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        return try await perform(req)
    }

    // Calls a Postgres function as the signed-in user and returns its rows.
    // Used for the SECURITY DEFINER friend-profile lookups, which require an
    // exact friend_code / id and so cannot be enumerated (see
    // supabase/profiles_security.sql).
    private func restRPC(_ function: String, body: [String: Any]) async throws -> Data {
        guard let s = session else { throw SupabaseError.notSignedIn }
        var req = buildRequest("/rest/v1/rpc/\(function)", method: "POST")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(req)
    }

    private func restPATCH(_ path: String, body: [String: Any]) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        var req = buildRequest(path, method: "PATCH")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        _ = try await perform(req)
    }

    private func restPOST(_ path: String, body: [String: Any], ignoreDuplicates: Bool = false) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        var req = buildRequest(path, method: "POST")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let prefer = ignoreDuplicates ? "return=minimal,resolution=ignore-duplicates" : "return=minimal"
        req.setValue(prefer, forHTTPHeaderField: "Prefer")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        _ = try await perform(req)
    }

    private func restDELETE(_ path: String) async throws {
        guard let s = session else { throw SupabaseError.notSignedIn }
        var req = buildRequest(path, method: "DELETE")
        req.setValue("Bearer \(s.accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        _ = try await perform(req)
    }

    private func buildRequest(_ path: String, method: String) -> URLRequest {
        guard let url = URL(string: SupabaseConfig.projectURL + path) else {
            assertionFailure("Ungültige Supabase URL: \(SupabaseConfig.projectURL)\(path)")
            return URLRequest(url: URL(string: "about:blank")!)
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue(SupabaseConfig.anonKey, forHTTPHeaderField: "apikey")
        return req
    }

    private func perform(_ req: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: req)
        } catch {
            throw SupabaseError.networkError(error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw SupabaseError.networkError(URLError(.badServerResponse))
        }
        guard (200...299).contains(http.statusCode) else {
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let msg = json?["msg"] as? String
                ?? json?["message"] as? String
                ?? String(data: data, encoding: .utf8)
                ?? "Unbekannter Fehler"
            if http.statusCode == 400 && msg.lowercased().contains("invalid") {
                throw SupabaseError.invalidCredentials
            }
            throw SupabaseError.serverError(http.statusCode, msg)
        }
        return data
    }

    // MARK: Token refresh

    private func refreshIfNeeded() async throws {
        guard let s = session else { return }
        let refreshMarginSeconds: Double = 120
        guard Date().timeIntervalSince1970 >= s.expiresAt - refreshMarginSeconds else { return }
        do {
            let body: [String: Any] = ["refresh_token": s.refreshToken]
            let data = try await authPOST("/auth/v1/token?grant_type=refresh_token", body: body)
            applySession(try decodeGoTrue(data))
        } catch let e as SupabaseError {
            // Only a definitive auth rejection invalidates the session.
            // Network failures and server outages must not delete the Keychain
            // session, otherwise a dead spot silently signs the user out.
            switch e {
            case .networkError, .decodingError:
                throw e
            case .serverError(let code, _) where code >= 500:
                throw e
            default:
                clearSession()
                throw SupabaseError.notSignedIn
            }
        } catch {
            throw SupabaseError.networkError(error)
        }
    }

    // MARK: Session helpers

    private func decodeGoTrue(_ data: Data) throws -> SBAuthResponse {
        // When Supabase email confirmation is ON, signup returns the user object
        // without access_token/refresh_token. Detect this before attempting full decode.
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           json["access_token"] == nil {
            throw SupabaseError.emailConfirmationRequired
        }
        do { return try JSONDecoder().decode(SBAuthResponse.self, from: data) }
        catch { throw SupabaseError.decodingError(error) }
    }

    private func applySession(_ resp: SBAuthResponse) {
        let s = AccountSession(
            accessToken:  resp.accessToken,
            refreshToken: resp.refreshToken,
            userId:       resp.user.id,
            expiresAt:    Date().timeIntervalSince1970 + Double(resp.expiresIn)
        )
        session = s
        Keychain.saveSession(s)
    }

    private func clearSession() {
        session = nil
        myProfile = nil
        Keychain.deleteSession()
    }
}

// MARK: - Jam decoder helpers (file-private)

private struct JamRow: Decodable {
    let id: String
    let code: String
    let hostUserID: String?
    let hostName: String?
    let visibility: String?
    let settings: JamSettings?
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id, code
        case hostUserID = "host_user_id"
        case hostName   = "host_name"
        case visibility, settings
        case createdAt  = "created_at"
    }

    // nil for malformed rows: a random fallback UUID would break upsert
    // identity and duplicate the jam in every poll cycle.
    func toJam() -> Jam? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        return Jam(
            id: uuid,
            code: code,
            hostUserID: hostUserID ?? "",
            hostName: hostName ?? "Anonym",
            createdAt: createdAt,
            visibility: Jam.JamVisibility(rawValue: visibility ?? "") ?? .codeOnly,
            settings: settings ?? JamSettings(),
            participants: []
        )
    }
}

private struct JamParticipantRow: Decodable {
    let id: String
    let userID: String?
    let displayName: String?
    let connectionType: String?
    var currentBAC: Double?
    let currentStatus: String?
    let hasSOSActive: Bool?          // optional: older rows may not have this column
    let joinedAt: Date?              // optional: DB may not have default set yet
    let lastUpdated: Date?

    enum CodingKeys: String, CodingKey {
        case id
        case userID         = "user_id"
        case displayName    = "display_name"
        case connectionType = "connection_type"
        case currentBAC     = "current_bac"
        case currentStatus  = "current_status"
        case hasSOSActive   = "has_sos_active"
        case joinedAt       = "joined_at"
        case lastUpdated    = "last_updated"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        userID = try container.decodeIfPresent(String.self, forKey: .userID)
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
        connectionType = try container.decodeIfPresent(String.self, forKey: .connectionType)
        
        // Robustes Parsing für NUMERIC (fängt ab, falls PostgREST die Zahl als String oder mit Komma liefert)
        if let bacDouble = try? container.decodeIfPresent(Double.self, forKey: .currentBAC) {
            currentBAC = bacDouble
        } else if let bacStr = try? container.decodeIfPresent(String.self, forKey: .currentBAC), let bac = Double(bacStr.replacingOccurrences(of: ",", with: ".")) {
            currentBAC = bac
        } else {
            currentBAC = nil
        }
        
        currentStatus = try container.decodeIfPresent(String.self, forKey: .currentStatus)
        hasSOSActive = try container.decodeIfPresent(Bool.self, forKey: .hasSOSActive)
        joinedAt = try container.decodeIfPresent(Date.self, forKey: .joinedAt)
        lastUpdated = try container.decodeIfPresent(Date.self, forKey: .lastUpdated)
    }

    // nil for malformed rows, same reasoning as JamRow.toJam().
    func toParticipant() -> JamParticipant? {
        guard let uuid = UUID(uuidString: id) else { return nil }
        let name = displayName ?? "Anonym"
        let now  = Date()
        return JamParticipant(
            id: uuid,
            userID: userID,
            displayName: name,
            avatar: String(name.prefix(1)).uppercased(),
            joinedAt: joinedAt ?? now,
            connectionType: JamParticipant.ConnectionType(rawValue: connectionType ?? "") ?? .code,
            currentBAC: currentBAC,
            currentStatus: currentStatus,
            hasSOSActive: hasSOSActive ?? false,
            lastUpdated: lastUpdated ?? now,
            sharedSettings: nil
        )
    }
}

// MARK: - CommunityDrinkRow

struct CommunityDrinkRow: Decodable, Identifiable {
    let id: UUID
    let barcode: String
    let name: String
    let category: String
    let volume: Double
    let abv: Double
    let calories: Int
    let iconName: String
    let confirmedCount: Int

    enum CodingKeys: String, CodingKey {
        case id, barcode, name, category, volume, abv, calories
        case iconName       = "icon_name"
        case confirmedCount = "confirmed_count"
    }
}

// MARK: - CommunityMixRow

struct CommunityMixRow: Decodable, Identifiable {
    let id: UUID
    let name: String
    let ingredients: [MixIngredient]
    let totalVolume: Double
    let totalAbv: Double
    let calories: Int
    let confirmedCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, ingredients, calories
        case totalVolume    = "total_volume"
        case totalAbv       = "total_abv"
        case confirmedCount = "confirmed_count"
    }
}

// MARK: - Keychain (file-private)

private enum Keychain {
    private static let service = "com.tipau.Alcoholtracker.supabase"
    private static let account = "session"

    static func saveSession(_ s: AccountSession) {
        guard let data = try? JSONEncoder().encode(s) else { return }
        let base: [String: Any] = [
            kSecClass      as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(base as CFDictionary)
        var add = base
        add[kSecValueData as String] = data
        SecItemAdd(add as CFDictionary, nil)
    }

    static func loadSession() -> AccountSession? {
        let query: [String: Any] = [
            kSecClass      as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(AccountSession.self, from: data)
    }

    static func deleteSession() {
        let query: [String: Any] = [
            kSecClass      as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
