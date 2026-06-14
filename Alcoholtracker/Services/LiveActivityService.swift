import ActivityKit
import Foundation

@MainActor
final class LiveActivityService {
    static let shared = LiveActivityService()
    private init() {}

    func syncActivity(bac: Double, eliminationRate: Double, drinkCount: Int, soberThreshold: Double = 0.01, warningThreshold: Double = 0.5) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        if bac <= soberThreshold {
            Task {
                for activity in Activity<PromilleActivityAttributes>.activities {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
            return
        }

        let state = PromilleActivityAttributes.ContentState(
            bac: bac,
            eliminationRate: eliminationRate,
            lastUpdated: Date(),
            drinkCount: drinkCount,
            warningThreshold: warningThreshold
        )
        // Stale after BAC reaches zero plus a 10-min buffer, capped at 12 hours.
        let hoursToSober = eliminationRate > 0
            ? AlcoholKinetics.hoursUntilThreshold(peakBAC: bac, threshold: 0.001, beta: eliminationRate)
            : 6.0
        let staleInterval = min(hoursToSober * 3600 + 600, 43_200)
        let content = ActivityContent(
            state: state,
            staleDate: Date().addingTimeInterval(staleInterval)
        )

        let active = Activity<PromilleActivityAttributes>.activities
            .first(where: { $0.activityState == .active })

        if let active {
            Task { await active.update(content) }
        } else {
            Task {
                for old in Activity<PromilleActivityAttributes>.activities {
                    await old.end(nil, dismissalPolicy: .immediate)
                }
                do {
                    _ = try Activity.request(
                        attributes: PromilleActivityAttributes(),
                        content: content,
                        pushType: nil
                    )
                } catch { /* denied or unsupported, fail silently */ }
            }
        }
    }
}
