import Foundation
import SwiftData

// MARK: - PhotoMemory

@Model
final class PhotoMemory {
    var id: UUID
    var timestamp: Date
    var filename: String       // JPEG stored in app Documents/PhotoMemories/
    var caption: String?
    var bacAtTime: Double?     // BAC (‰) at moment of capture; nil for non-jam photos

    init(filename: String, caption: String? = nil, bacAtTime: Double? = nil) {
        self.id = UUID()
        self.timestamp = Date()
        self.filename = filename
        self.caption = caption
        self.bacAtTime = bacAtTime
    }
}
