import Foundation
import SwiftData

// MARK: - CrewMember

// Kept as @Model (not plain struct) so mock data persists across launches
// and the architecture is ready for real CloudKit or API data in later versions.
@Model
final class CrewMember {
    var id: UUID
    var name: String
    var avatarInitial: String
    var currentBAC: Double
    // Stores the server-side bac_updated_at (when the friend's BAC was last
    // published), kept under its historical name to avoid a SwiftData
    // migration. It is NOT the time of the friend's last drink.
    var lastDrinkTimestamp: Date?
    var drinksLastHour: Int
    var isHome: Bool
    var isSoberBuddy: Bool
    var isSharing: Bool
    var isSelf: Bool
    var joinedAt: Date
    var friendCode: String?

    // Friend's app-wide SOS flag, refreshed from the server poll.
    // Inline default required for SwiftData lightweight migration.
    var sosActive: Bool = false

    // Friend's own Probezeit setting (0,0 ‰ limit), refreshed from the server.
    // Drives the "Fahrbereit" vs "Darf nicht mehr fahren" label for a friend you
    // marked as driver, using THEIR limit (you don't know it otherwise).
    // Inline default required for SwiftData lightweight migration.
    var isProbationaryDriver: Bool = false

    // When true, a local notification fires if this friend's BAC gets too high.
    // Set per friend on their profile sheet.
    var alertWhenHigh: Bool = false

    // Dedup guard so the high-BAC alert fires once per episode, reset when the
    // friend drops back below the threshold.
    var highAlertFired: Bool = false

    // MARK: Computed

    var bacUpdatedMinutesAgo: Int? {
        guard let ts = lastDrinkTimestamp else { return nil }
        return Int(Date().timeIntervalSince(ts) / 60)
    }

    // True once a live BAC value has ever arrived from the server.
    var hasLiveData: Bool { lastDrinkTimestamp != nil }

    // Published BAC decayed at the standard elimination rate since the last
    // server update: a friend who closed the app at 1.2 does not stay at
    // 1.2 forever in our list.
    var estimatedBAC: Double {
        guard let ts = lastDrinkTimestamp else { return currentBAC }
        let hours = max(0, Date().timeIntervalSince(ts) / 3600)
        return max(0, currentBAC - 0.15 * hours)
    }

    var bacStatus: BACStatus { BACStatus(bac: estimatedBAC) }

    var careScore: Int {
        var score = bacStatus.level * 20
        if let mins = bacUpdatedMinutesAgo, mins < 10, estimatedBAC > 1.0 { score += 10 }
        return score
    }

    init(
        name: String,
        currentBAC: Double = 0,
        lastDrinkTimestamp: Date? = nil,
        drinksLastHour: Int = 0,
        isHome: Bool = false,
        isSoberBuddy: Bool = false,
        isSharing: Bool = true,
        isSelf: Bool = false,
        friendCode: String? = nil
    ) {
        self.id = UUID()
        self.name = name
        self.avatarInitial = String(name.prefix(1)).uppercased()
        self.currentBAC = currentBAC
        self.lastDrinkTimestamp = lastDrinkTimestamp
        self.drinksLastHour = drinksLastHour
        self.isHome = isHome
        self.isSoberBuddy = isSoberBuddy
        self.isSharing = isSharing
        self.isSelf = isSelf
        self.joinedAt = Date()
        self.friendCode = friendCode
    }

}
