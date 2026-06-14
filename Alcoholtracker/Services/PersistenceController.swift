import Foundation
import SwiftData

// MARK: - PersistenceController

/// Central SwiftData stack. Uses the App Group store (group.com.tipau.Alcoholtracker)
/// so the WidgetKit extension can share lightweight UserDefaults snapshots
/// without opening the SwiftData container directly.
final class PersistenceController {

    static let shared = PersistenceController()

    // All model types that SwiftData must persist
    static let schema = Schema([
        UserProfile.self,
        DrinkTemplate.self,
        Drink.self,
        CustomMix.self,
        CrewMember.self,
        PhotoMemory.self,
        DayNote.self,
        PendingSyncOperation.self,
    ])

    let container: ModelContainer

    // MARK: Init

    /// - Parameter inMemory: true for tests and SwiftUI previews.
    init(inMemory: Bool = false) {
        func makeConfig() -> ModelConfiguration {
            if inMemory {
                return ModelConfiguration(schema: Self.schema, isStoredInMemoryOnly: true)
            }
            if let url = PersistenceController.appGroupStoreURL() {
                return ModelConfiguration(schema: Self.schema, url: url)
            }
            return ModelConfiguration(schema: Self.schema)
        }

        let config = makeConfig()

        do {
            container = try ModelContainer(for: Self.schema, configurations: [config])
        } catch {
            // First attempt failed (schema change or corruption).
            // Try an in-memory fallback before touching any stored data.
            let fallback = ModelConfiguration(schema: Self.schema, isStoredInMemoryOnly: true)
            do {
                container = try ModelContainer(for: Self.schema, configurations: [fallback])
                // Only nuke the on-disk store after we confirmed the app can run.
                if !inMemory {
                    PersistenceController.nukeStores()
                    UserDefaults.standard.removeObject(forKey: "DrinkDatabaseVersion")
                }
            } catch let fallbackError {
                // In-memory init should never fail; guard against the impossible.
                fatalError("SwiftData ModelContainer konnte nicht initialisiert werden: \(fallbackError)")
            }
        }
    }

    // MARK: Store recovery

    private static func nukeStores() {
        // Default SwiftData location
        if let appSupport = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            let contents = (try? FileManager.default
                .contentsOfDirectory(at: appSupport, includingPropertiesForKeys: nil)) ?? []
            for url in contents {
                let name = url.lastPathComponent
                if name.hasSuffix(".store") || name.hasSuffix(".store-wal") || name.hasSuffix(".store-shm") {
                    try? FileManager.default.removeItem(at: url)
                }
            }
        }
        // App Group location
        if let groupURL = appGroupStoreURL() {
            let dir = groupURL.deletingLastPathComponent()
            let base = groupURL.lastPathComponent
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(at: dir.appendingPathComponent(base + suffix))
            }
        }
    }

    // MARK: Preview

    @MainActor
    static let preview: PersistenceController = {
        let controller = PersistenceController(inMemory: true)
        let ctx = controller.container.mainContext

        // Seed drinks for previews
        DrinkDatabase.seedIfNeeded(in: ctx)

        // Sample session
        let profile = UserProfile(weight: 75, height: 180, age: 28, gender: .male)
        ctx.insert(profile)

        let drinks: [Drink] = [
            Drink(name: "Augustiner Helle", volume: 500, abv: 5.2,  calories: 210,
                  iconName: "mug.fill",        category: .beer, timestamp: Date().addingTimeInterval(-5400)),
            Drink(name: "Rotwein (Glas)",   volume: 200, abv: 13.0, calories: 170,
                  iconName: "wineglass.fill", category: .wine, timestamp: Date().addingTimeInterval(-3000)),
        ]
        drinks.forEach { ctx.insert($0) }

        try? ctx.save()
        return controller
    }()

    // MARK: App Group

    private static func appGroupStoreURL() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: "group.com.tipau.Alcoholtracker")?
            .appendingPathComponent("promille.store")
    }
}

// MARK: - Shared UserDefaults for widget
//
// nonisolated: AppIntents and widget providers read these outside the main
// actor. UserDefaults is documented thread-safe, hence the (unsafe) opt-out.

nonisolated extension UserDefaults {
    /// Shared suite used to pass the current BAC snapshot to WidgetKit without
    /// requiring the widget to open a SwiftData store directly.
    nonisolated(unsafe) static let widgetShared: UserDefaults = {
        guard let ud = UserDefaults(suiteName: "group.com.tipau.Alcoholtracker") else {
            assertionFailure("App Group UserDefaults nicht verfügbar — Entitlements prüfen")
            return .standard
        }
        return ud
    }()

    static let keyCurrentBAC        = "currentBAC"
    static let keyBACStatus         = "bacStatus"
    static let keyLastUpdated       = "bacLastUpdated"
    static let keyEliminationRate   = "eliminationRate"
    static let keyWarningThreshold  = "warningThreshold"
    static let keyPerDrinkBAC       = "perDrinkBAC"
}
