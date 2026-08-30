import Foundation

// MARK: - Jam

struct Jam: Identifiable, Codable {
    let id: UUID
    let code: String
    // Mutable so the host role can be handed over (host transfer / ghost jams).
    var hostUserID: String
    var hostName: String
    let createdAt: Date
    var visibility: JamVisibility
    var settings: JamSettings
    var participants: [JamParticipant]

    enum JamVisibility: String, Codable, CaseIterable {
        case proximityAndCode = "Proximity + Code"
        case friendsOnly      = "Nur Freunde"
        case codeOnly         = "Nur per Code"
        case proximityOnly    = "Nur in der Nähe"

        var description: String {
            switch self {
            case .proximityAndCode: return "Sichtbar für alle in der Nähe und per Code"
            case .friendsOnly:      return "Nur deine Freunde können beitreten"
            case .codeOnly:         return "Niemand wird automatisch sehen, nur mit Code"
            case .proximityOnly:    return "Funktioniert offline, nur per Bluetooth"
            }
        }

        var icon: String {
            switch self {
            case .proximityAndCode: return "antenna.radiowaves.left.and.right.circle"
            case .friendsOnly:      return "person.2.circle.fill"
            case .codeOnly:         return "key.fill"
            case .proximityOnly:    return "wave.3.right.circle.fill"
            }
        }

        var usesServer: Bool {
            switch self {
            case .proximityOnly: return false
            default:             return true
            }
        }

        var usesProximity: Bool {
            switch self {
            case .proximityAndCode, .proximityOnly: return true
            default:                                return false
            }
        }
    }
}

// MARK: - JamSettings

struct JamSettings: Codable, Equatable {
    var shareBAC: Bool          = true
    var shareStatus: Bool       = true
    var shareDrinks: Bool       = true
    var shareDrinkCount: Bool   = true
    var shareSOSStatus: Bool    = true
    var sharePhotos: Bool       = true
    var shareLocation: Bool     = false
    var allowWaves: Bool        = true
    var autoAcceptFriends: Bool = true
}

// MARK: - JamParticipant

struct JamParticipant: Identifiable, Codable {
    let id: UUID
    let userID: String?
    let displayName: String
    let avatar: String
    let joinedAt: Date
    let connectionType: ConnectionType
    var currentBAC: Double?
    var currentStatus: String?
    var hasSOSActive: Bool
    var lastUpdated: Date
    var sharedSettings: JamSettings?

    enum ConnectionType: String, Codable {
        case proximity = "Proximity"
        case friend    = "Freund"
        case code      = "Code"

        var icon: String {
            switch self {
            case .proximity: return "wave.3.right.circle.fill"
            case .friend:    return "person.2.circle.fill"
            case .code:      return "key.fill"
            }
        }

        var label: String {
            switch self {
            case .proximity: return "In der Nähe"
            case .friend:    return "Freund"
            case .code:      return "Code"
            }
        }
    }
}

// MARK: - Array upsert / CRDT merge helpers

extension Array where Element == JamParticipant {
    // Last-Writer-Wins upsert: an incoming row only replaces an existing one when
    // it is at least as fresh (lastUpdated). This makes the roster a CRDT-style
    // LWW map, so a stale broadcast arriving late after an offline reconnect can no
    // longer clobber a newer status, and merges are order-independent.
    mutating func upsert(_ participant: JamParticipant) {
        let existingIdx: Int? =
            (participant.userID.flatMap { uid in firstIndex(where: { $0.userID == uid }) })
            ?? firstIndex(where: { $0.id == participant.id })
        if let idx = existingIdx {
            if participant.lastUpdated >= self[idx].lastUpdated {
                self[idx] = participant
            }
        } else {
            append(participant)
        }
    }

    // Conflict-free merge of another roster into this one (used when two sides
    // reconnect after being offline). Pure LWW union: every participant is upserted,
    // so the result is the same regardless of which side is merged into which.
    mutating func merge(_ other: [JamParticipant]) {
        for p in other { upsert(p) }
    }
}
