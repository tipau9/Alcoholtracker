import Foundation
import SwiftData

// MARK: - PhotoMemory

@Model
final class PhotoMemory {
    var id: UUID
    var timestamp: Date
    var filename: String       // JPEG stored in app Documents/PhotoMemories/
    var caption: String?

    init(filename: String, caption: String? = nil) {
        self.id = UUID()
        self.timestamp = Date()
        self.filename = filename
        self.caption = caption
    }
}
