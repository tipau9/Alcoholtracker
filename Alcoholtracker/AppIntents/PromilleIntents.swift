import AppIntents
import Foundation

// MARK: - Check current BAC

struct CheckCurrentBACIntent: AppIntent {
    static var title: LocalizedStringResource = "Promille abfragen"
    static var description = IntentDescription("Fragt deinen aktuellen Promillewert ab")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let defaults = UserDefaults.widgetShared
        let bac = defaults.double(forKey: UserDefaults.keyCurrentBAC)
        let rate = defaults.double(forKey: UserDefaults.keyEliminationRate)
        let lastUpdated = (defaults.object(forKey: UserDefaults.keyLastUpdated) as? Date) ?? Date()

        let elapsed = Date().timeIntervalSince(lastUpdated) / 3600
        let current = max(0, bac - rate * elapsed)

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
        let bac = defaults.double(forKey: UserDefaults.keyCurrentBAC)
        let rate = max(0.05, defaults.double(forKey: UserDefaults.keyEliminationRate))
        let lastUpdated = (defaults.object(forKey: UserDefaults.keyLastUpdated) as? Date) ?? Date()
        let drivingLimit = defaults.object(forKey: UserDefaults.keyWarningThreshold) as? Double ?? 0.5

        let elapsed = Date().timeIntervalSince(lastUpdated) / 3600
        let currentBAC = max(0, bac - rate * elapsed)
        let projectedBAC = max(0, AlcoholKinetics.bacAtTime(peakBAC: currentBAC, hoursSincePeak: hours, beta: rate))
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
