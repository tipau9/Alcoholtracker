import SwiftUI
import SwiftData
import WidgetKit
import UIKit

@MainActor
@main
struct PromilleApp: App {

    let persistence: PersistenceController
    @State private var supabase: SupabaseService
    @State private var jamService: JamService
    @State private var offlineSync: OfflineSyncService
    @State private var historySync: HistorySyncService
    @State private var achievements = AchievementService()
    @State private var health = HealthKitService()
    private let theme = AppTheme.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // The app is always dark themed; force the keyboard to match so number
        // pads (age, weight, ...) don't appear as a washed-out light keyboard.
        UITextField.appearance().keyboardAppearance = .dark

        // Must be registered before any scene becomes active (BGTaskScheduler requirement).
        BackgroundRefreshService.registerTasks()

        persistence = PersistenceController.shared

        let sb = SupabaseService()
        _supabase    = State(initialValue: sb)
        _jamService  = State(initialValue: JamService(supabase: sb))
        let sync = OfflineSyncService(
            modelContext: persistence.container.mainContext,
            supabase: sb
        )
        _offlineSync = State(initialValue: sync)
        _historySync = State(initialValue: HistorySyncService(
            modelContext: persistence.container.mainContext,
            supabase: sb
        ))
        achievements.supabase = sb

        // Let the background-processing task flush the offline queue using the
        // already-running service (avoids rebuilding the SwiftData stack).
        BackgroundRefreshService.onSupabaseSync = { await sync.syncAll() }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .modelContainer(persistence.container)
                .environment(supabase)
                .environment(jamService)
                .environment(offlineSync)
                .environment(historySync)
                .environment(achievements)
                .environment(health)
                .environment(theme)
                .preferredColorScheme(.dark)
                .task {
                    seedDrinkDatabase()
                    await syncCommunityDrinks()
                    syncThemeFromProfile()
                    await historySync.sync()
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background:
                WidgetCenter.shared.reloadAllTimelines()
                BackgroundRefreshService.scheduleWidgetRefresh()
                BackgroundRefreshService.scheduleSupabaseSync()
                Task { await historySync.sync() }
            default:
                break
            }
        }
    }

    @MainActor
    private func syncThemeFromProfile() {
        let ctx = persistence.container.mainContext
        let profile = (try? ctx.fetch(FetchDescriptor<UserProfile>()))?.first
        theme.sync(from: profile)
    }

    @MainActor
    private func seedDrinkDatabase() {
        DrinkDatabase.seedIfNeeded(in: persistence.container.mainContext)
    }

    @MainActor
    private func syncCommunityDrinks() async {
        guard supabase.isConfigured else { return }
        // Only sync once per 24 hours to avoid unnecessary network calls.
        let lastSync = UserDefaults.standard.double(forKey: "community.lastSync")
        guard Date().timeIntervalSince1970 - lastSync > 86_400 else { return }

        guard let rows = try? await supabase.fetchCommunityDrinks(), !rows.isEmpty else { return }

        let ctx = persistence.container.mainContext
        let existing = (try? ctx.fetch(FetchDescriptor<DrinkTemplate>())) ?? []
        let existingBarcodes = Set(existing.compactMap { $0.barcode.isEmpty ? nil : $0.barcode })
        let existingNames    = Set(existing.map { $0.name.lowercased() })

        var added = false
        for row in rows {
            if existingBarcodes.contains(row.barcode) { continue }
            if existingNames.contains(row.name.lowercased()) { continue }
            let t = DrinkTemplate(
                name:     row.name,
                category: DrinkCategory(rawValue: row.category) ?? .other,
                volume:   row.volume,
                abv:      row.abv,
                calories: row.calories,
                iconName: row.iconName
            )
            t.barcode = row.barcode
            ctx.insert(t)
            added = true
        }
        if added { try? ctx.save() }
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "community.lastSync")
    }
}
