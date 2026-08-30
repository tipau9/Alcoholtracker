import Foundation

// MARK: - ServingSizeMemory
//
// Remembers the serving size (volume in ml) the user last chose for a given drink
// template, so the next time they open the amount sheet that size ("Dose", "Flasche
// 0,5 L", ...) is pre-selected automatically instead of always defaulting to the
// template's nominal volume. Keyed by templateID, stored in UserDefaults (no
// SwiftData migration needed). A preset is matched back by its volume, so saving the
// chosen volume is enough to restore the chosen format.
enum ServingSizeMemory {

    private static let storageKey = "servingSizeMemory_v1"

    private static var storage: [String: Double] {
        get { UserDefaults.standard.dictionary(forKey: storageKey) as? [String: Double] ?? [:] }
        set { UserDefaults.standard.set(newValue, forKey: storageKey) }
    }

    /// The user's last chosen volume (ml) for this template, or nil if never set.
    static func volume(for templateID: UUID) -> Double? {
        storage[templateID.uuidString]
    }

    /// Persists the chosen volume so it becomes the default next time.
    static func save(volume: Double, for templateID: UUID) {
        guard volume > 0 else { return }
        var s = storage
        s[templateID.uuidString] = volume
        storage = s
    }
}
