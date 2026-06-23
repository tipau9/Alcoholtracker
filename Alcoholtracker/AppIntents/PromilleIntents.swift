import AppIntents
import Foundation

// MARK: - Shared snapshot reader

// BAC at an arbitrary moment for the Siri intents. Prefers the live curve the
// app writes (SharedStateStore.writeBACCurve), so the rising absorption phase
// right after a drink is reflected just like in the widget. Falls back to linear
// elimination from the scalar snapshot when no curve is stored yet.
//
// Previously the intents only read the scalar snapshot and decayed it linearly,
// so straight after a drink Siri reported ~0 ("nüchtern") and never showed the
// climbing value the app/widget display, the exact mismatch the curve fixes.
// nonisolated: pure snapshot math (reads App Group UserDefaults + the shared
// curve), called from AppIntents.perform() outside the main actor. Mirrors
// AlcoholKinetics. Without this, Xcode 26's default main-actor isolation makes it
// main-actor-isolated and the call from perform() warns (a Swift 6 error).
private nonisolated enum PromilleSnapshot {
    static func bac(at date: Date) -> Double {
        let defaults = UserDefaults.widgetShared
        let rate = max(0.05, defaults.double(forKey: UserDefaults.keyEliminationRate))
        let curve = SharedStateStore.readBACCurve()
        if let first = curve.first, let last = curve.last {
            if date <= first.date { return max(0, first.bac) }
            if date >= last.date {
                let elapsedH = date.timeIntervalSince(last.date) / 3600.0
                return max(0, last.bac - rate * elapsedH)
            }
            for i in 1..<curve.count where curve[i].date >= date {
                let a = curve[i - 1], b = curve[i]
                let span = b.date.timeIntervalSince(a.date)
                guard span > 0 else { return max(0, b.bac) }
                let t = date.timeIntervalSince(a.date) / span
                return max(0, a.bac + (b.bac - a.bac) * t)
            }
        }
        // No curve yet: linear decay of the legacy scalar snapshot.
        let bac = defaults.double(forKey: UserDefaults.keyCurrentBAC)
        let lastUpdated = (defaults.object(forKey: UserDefaults.keyLastUpdated) as? Date) ?? date
        let elapsedH = date.timeIntervalSince(lastUpdated) / 3600.0
        return max(0, bac - rate * elapsedH)
    }
}

// MARK: - Check current BAC

struct CheckCurrentBACIntent: AppIntent {
    static var title: LocalizedStringResource = "Promille abfragen"
    static var description = IntentDescription("Fragt deinen aktuellen Promillewert ab")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let current = PromilleSnapshot.bac(at: Date())

        let formatted = String(format: "%.2f", current).replacingOccurrences(of: ".", with: ",")
        if current < 0.01 {
            return .result(dialog: "Du bist aktuell nüchtern.")
        }
        return .result(dialog: "Du hast aktuell \(formatted) Promille.")
    }
}

// MARK: - Forecast

struct ForecastIntent: AppIntent {
    static var title: LocalizedStringResource = "Promille-Vorausschau"
    static var description = IntentDescription("Berechnet wie viele Drinks noch möglich sind")

    @Parameter(title: "Stunden bis Abfahrt")
    var hours: Double

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let defaults = UserDefaults.widgetShared
        // Real legal driving limit (0,0 ‰ in der Probezeit), falling back to the
        // warning threshold and then 0,5 ‰ for profiles saved before this key.
        let drivingLimit = (defaults.object(forKey: UserDefaults.keyDrivingLimit) as? Double)
            ?? (defaults.object(forKey: UserDefaults.keyWarningThreshold) as? Double)
            ?? 0.5

        // BAC at the target moment read straight from the live curve, which
        // already folds in both ongoing absorption and elimination over the
        // window. (The old code decayed the snapshot to "now" and then applied a
        // second, mixed-order decay on top, double-counting elimination.)
        let projectedBAC = PromilleSnapshot.bac(at: Date().addingTimeInterval(hours * 3600))
        let allowed = max(0, drivingLimit - projectedBAC)
        // Estimate per-drink contribution based on stored rate (population average drink ~ 10g alcohol).
        let perDrinkBAC = max(0.05, defaults.object(forKey: UserDefaults.keyPerDrinkBAC) as? Double ?? 0.15)
        let drinks = Int(allowed / perDrinkBAC)

        if drinks > 0 {
            return .result(dialog: "In \(Int(hours)) Stunden kannst du noch etwa \(drinks) Standarddrinks trinken.")
        }
        return .result(dialog: "In \(Int(hours)) Stunden solltest du besser nichts mehr trinken.")
    }
}

// MARK: - App Shortcuts provider

struct PromilleShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CheckCurrentBACIntent(),
            phrases: [
                "Wie viel Promille habe ich in \(.applicationName)",
                "Promille Check in \(.applicationName)",
                "Bin ich nüchtern in \(.applicationName)"
            ],
            shortTitle: "Promille checken",
            systemImageName: "drop.fill"
        )
        AppShortcut(
            intent: ForecastIntent(),
            phrases: [
                "Vorausschau in \(.applicationName)",
                "Wie viel darf ich noch trinken in \(.applicationName)"
            ],
            shortTitle: "Vorausschau",
            systemImageName: "clock.arrow.2.circlepath"
        )
    }
}
