import Foundation

// MARK: - FriendProfile
//
// Snapshot of a user's public Supabase profile.
// Fetched via SupabaseService; not persisted in SwiftData.

struct FriendProfile: Codable, Identifiable {
    let id: String
    var displayName: String
    var friendCode: String
    var currentBac: Double?   // null for users who have never tracked a drink
    var bacUpdatedAt: Date?   // null for users who have never tracked a drink
    var isSharing: Bool
    // Unlocked achievement ids; nil until the achievements column exists
    // server-side (see SQL in SupabaseService).
    var achievements: [String]?
    // App-wide SOS flag (independent of jams); false until the sos_active
    // column exists server-side (see SQL in SupabaseService.setSOS).
    var sosActive: Bool
    // The user's own Probezeit (0,0 ‰ driving limit); false until the
    // is_probationary column exists server-side (see SupabaseService).
    var isProbationary: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case displayName    = "display_name"
        case friendCode     = "friend_code"
        case currentBac     = "current_bac"
        case bacUpdatedAt   = "bac_updated_at"
        case isSharing      = "is_sharing"
        case achievements
        case sosActive      = "sos_active"
        case isProbationary = "is_probationary"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id           = try c.decode(String.self, forKey: .id)
        displayName  = (try? c.decode(String.self, forKey: .displayName)) ?? ""
        friendCode   = (try? c.decode(String.self, forKey: .friendCode)) ?? ""
        currentBac   = try? c.decodeIfPresent(Double.self, forKey: .currentBac)
        bacUpdatedAt = try? c.decodeIfPresent(Date.self, forKey: .bacUpdatedAt)
        isSharing    = (try? c.decode(Bool.self, forKey: .isSharing)) ?? true
        achievements = try? c.decodeIfPresent([String].self, forKey: .achievements)
        sosActive    = (try? c.decodeIfPresent(Bool.self, forKey: .sosActive)) ?? false
        isProbationary = (try? c.decodeIfPresent(Bool.self, forKey: .isProbationary)) ?? false
    }
}

// MARK: - AccountSession

struct AccountSession: Codable {
    let accessToken: String
    let refreshToken: String
    let userId: String
    let expiresAt: Double   // seconds since 1970
}
