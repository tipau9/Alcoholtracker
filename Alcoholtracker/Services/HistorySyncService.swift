import Foundation
import SwiftData

// MARK: - HistorySyncService
//
// Backs up the on-device drinking history (drinks + day notes) to the signed-in
// Supabase account and restores it on a fresh install. The local SwiftData store
// is the source of truth: on each sync the local set is pushed (upsert) and, in
// the ongoing `.authoritative` mode, server rows that no longer exist locally are
// deleted so deletions propagate.
//
// Two safety exceptions never delete anything:
//   * Fresh device (empty local store, non-empty backup) -> rows are restored.
//   * First sync right after sign-in (`merge: true`) -> the account backup and
//     the device's current history are unioned, so signing into an existing
//     account never wipes either side.
//
// This is a single-account backup/restore model (the most recently synced device
// wins for deletions), not a live multi-device CRDT merge.

@MainActor
@Observable
final class HistorySyncService {

    private(set) var isSyncing = false
    private(set) var lastSyncDate: Date?

    private let modelContext: ModelContext
    private let supabase: SupabaseService

    // yyyy-MM-dd in the device's calendar; round-trips DayNote.dayStart (a local
    // startOfDay) to the Postgres `date` column and back.
    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    init(modelContext: ModelContext, supabase: SupabaseService) {
        self.modelContext = modelContext
        self.supabase = supabase
    }

    /// Push local history to the account and reconcile. Pass `merge: true` for the
    /// first sync after a sign-in so the account backup is unioned with the device
    /// instead of one overwriting the other. Best-effort: any failure leaves local
    /// data intact and is retried on the next launch / foreground.
    func sync(merge: Bool = false) async {
        guard !isSyncing, supabase.isSignedIn, supabase.isConfigured else { return }
        isSyncing = true
        defer { isSyncing = false }

        // The very first successful sync on this install is always a union, so
        // upgrading an already-signed-in device never clobbers a backup another
        // device made. Authoritative (delete-propagating) syncs start afterwards.
        let firstSync = !UserDefaults.standard.bool(forKey: Self.didInitialSyncKey)
        let useMerge = merge || firstSync

        do {
            try await syncDrinks(merge: useMerge)
            try await syncNotes(merge: useMerge)
            UserDefaults.standard.set(true, forKey: Self.didInitialSyncKey)
            lastSyncDate = Date()
        } catch {
            // Transient (network/server); retried next time.
        }
    }

    private static let didInitialSyncKey = "history.didInitialSync"

    // MARK: Drinks

    private func syncDrinks(merge: Bool) async throws {
        let local = (try? modelContext.fetch(FetchDescriptor<Drink>())) ?? []
        let remote = try await supabase.fetchDrinkHistory()
        let localIDs = Set(local.map(\.id))

        if !local.isEmpty {
            try await supabase.uploadDrinkHistory(local.map(drinkRow))
        }

        // Import server rows we don't have locally when merging or restoring.
        if merge || local.isEmpty {
            var imported = false
            for r in remote {
                guard let id = UUID(uuidString: r.id), !localIDs.contains(id) else { continue }
                insertDrink(from: r, id: id)
                imported = true
            }
            if imported { try? modelContext.save() }
        }

        // Ongoing authoritative mode: propagate local deletions to the server.
        if !merge, !local.isEmpty {
            let stale = remote.compactMap { UUID(uuidString: $0.id) }.filter { !localIDs.contains($0) }
            try await supabase.deleteDrinkHistory(ids: stale)
        }
    }

    private func drinkRow(_ d: Drink) -> [String: Any] {
        var row: [String: Any] = [
            "id": d.id.uuidString,
            "name": d.name,
            "volume": d.volume,
            "abv": d.abv,
            "calories": d.calories,
            "icon_name": d.iconName,
            "category": d.categoryRaw,
            "mixer_volume": d.mixerVolume,
            "mixer_water_content": d.mixerWaterContent,
            "drink_duration_minutes": d.drinkDurationMinutes,
            "consumed_at": Self.isoFormatter.string(from: d.timestamp),
        ]
        if let tid = d.templateID { row["template_id"] = tid.uuidString }
        return row
    }

    private func insertDrink(from r: SupabaseService.RemoteDrink, id: UUID) {
        let drink = Drink(
            name: r.name,
            volume: r.volume,
            abv: r.abv,
            calories: r.calories,
            iconName: r.iconName,
            category: DrinkCategory(rawValue: r.category) ?? .other,
            timestamp: r.consumedAt,
            templateID: r.templateID.flatMap { UUID(uuidString: $0) },
            mixerVolume: r.mixerVolume,
            mixerWaterContent: r.mixerWaterContent
        )
        drink.id = id                              // preserve server identity for future upserts
        drink.drinkDurationMinutes = r.drinkDurationMinutes
        modelContext.insert(drink)
    }

    // MARK: Day notes

    private func syncNotes(merge: Bool) async throws {
        let local = (try? modelContext.fetch(FetchDescriptor<DayNote>())) ?? []
        let remote = try await supabase.fetchDayNotes()
        let localDays = Set(local.map { Self.dayFormatter.string(from: $0.dayStart) })

        if !local.isEmpty {
            try await supabase.uploadDayNotes(local.map(noteRow))
        }

        if merge || local.isEmpty {
            var imported = false
            for r in remote where !localDays.contains(r.dayStart) {
                guard let day = Self.dayFormatter.date(from: r.dayStart) else { continue }
                let note = DayNote(dayStart: day, text: r.text, mood: DayMood(rawValue: r.mood) ?? .neutral)
                modelContext.insert(note)
                imported = true
            }
            if imported { try? modelContext.save() }
        }

        if !merge, !local.isEmpty {
            let stale = remote.map(\.dayStart).filter { !localDays.contains($0) }
            try await supabase.deleteDayNotes(days: stale)
        }
    }

    private func noteRow(_ n: DayNote) -> [String: Any] {
        [
            "day_start": Self.dayFormatter.string(from: n.dayStart),
            "text": n.text,
            "mood": n.moodRaw,
        ]
    }
}
