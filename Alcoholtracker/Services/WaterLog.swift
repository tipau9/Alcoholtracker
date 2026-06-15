import Foundation

// MARK: - WaterLog
//
// Per-day water glass counter, keyed by logical day (06:00 boundary).
// Stored in UserDefaults; feeds the hydration widget and the hangover
// prediction. nil means the user never logged water that day, which lets
// callers fall back to the old estimation heuristic instead of assuming zero.

enum WaterLog {

    static let glassML = 250.0

    private static let storageKey = "waterLog_v1"

    private static var storage: [String: Int] {
        get { UserDefaults.standard.dictionary(forKey: storageKey) as? [String: Int] ?? [:] }
        set { UserDefaults.standard.set(newValue, forKey: storageKey) }
    }

    // Key for a calendar day (any time-of-day component is stripped).
    private static func key(forDay day: Date) -> String {
        let comps = Calendar.current.dateComponents([.year, .month, .day], from: day)
        return String(format: "%04d-%02d-%02d", comps.year ?? 0, comps.month ?? 0, comps.day ?? 0)
    }

    private static var todayKey: String {
        key(forDay: Calendar.current.logicalDay(for: Date()))
    }

    // MARK: Reading

    /// Glasses logged for a specific calendar day; nil if nothing was ever logged.
    static func loggedGlasses(forDay day: Date) -> Int? {
        storage[key(forDay: Calendar.current.startOfDay(for: day))]
    }

    static func glassesToday() -> Int {
        storage[todayKey] ?? 0
    }

    // MARK: Writing (always against the current logical day)

    static func addGlassToday() {
        var s = storage
        s[todayKey, default: 0] += 1
        storage = s
    }

    static func removeGlassToday() {
        var s = storage
        s[todayKey] = max(0, (s[todayKey] ?? 0) - 1)
        storage = s
    }

    // MARK: Account backup

    /// Every logged day, keyed "YYYY-MM-DD". Used by HistorySyncService to back
    /// up the water log to the signed-in account.
    static var allEntries: [String: Int] { storage }

    /// Merges restored entries into the local log, keeping the higher count per
    /// day so a backup never lowers a freshly logged value.
    static func merge(_ entries: [String: Int]) {
        guard !entries.isEmpty else { return }
        var s = storage
        for (day, glasses) in entries {
            s[day] = max(s[day] ?? 0, glasses)
        }
        storage = s
    }
}
