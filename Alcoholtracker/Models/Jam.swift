import Foundation

// MARK: - Jam

struct Jam: Identifiable, Codable {
    let id: UUID
    let code: String
    let hostUserID: String
    let hostName: String
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

// MARK: - Array upsert helper

extension Array where Element == JamParticipant {
    mutating func upsert(_ participant: JamParticipant) {
        if let uid = participant.userID,
           let idx = firstIndex(where: { $0.userID == uid }) {
            self[idx] = participant
        } else if let idx = firstIndex(where: { $0.id == participant.id }) {
            self[idx] = participant
        } else {
            append(participant)
        }
    }
}
