import ActivityKit
import Foundation

struct PromilleActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var bac: Double
        var eliminationRate: Double
        var lastUpdated: Date
        var drinkCount: Int
        var warningThreshold: Double = 0.5
    }
}
