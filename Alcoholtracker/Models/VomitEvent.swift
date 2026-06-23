import Foundation
import SwiftData

// MARK: - VomitEvent
//
// A logged "Übergeben" (vomit) event during a session. Physiologically, vomiting
// expels alcohol still sitting in the stomach that has NOT yet been resorbed into
// the blood; alcohol already in the bloodstream is unaffected. The BAC engine
// therefore truncates each drink's absorption envelope at the vomit time, removing
// only the not-yet-absorbed remainder (see BACCalculator.sampledBAC). It does not
// lower the BAC that is already in the blood.
@Model
final class VomitEvent {
    @Attribute(.unique) var id: UUID
    var timestamp: Date

    init(timestamp: Date = Date()) {
        self.id = UUID()
        self.timestamp = timestamp
    }
}
