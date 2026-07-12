import ActivityKit
import Foundation

struct PromilleActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var bac: Double
        var eliminationRate: Double
        var lastUpdated: Date
        var drinkCount: Int
        var warningThreshold: Double = 0.5
        // Legal driving limit in ‰ (0,0 in der Probezeit, sonst 0,5). The driving
        // row counts down to this, not to the freely configurable Warnschwelle.
        var drivingLimit: Double = 0.5
        // Absolute times the app's full BAC engine expects the user to drop below
        // the sober / driving-limit thresholds. nil = not reached within the
        // forecast horizon. Precomputed (not linear from a snapshot) so the lock
        // screen reflects the still-rising absorption phase correctly.
        var soberAt: Date? = nil
        var driveReadyAt: Date? = nil
    }
}
