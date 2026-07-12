import Foundation

enum JamCodeGenerator {
    static func generate() -> String {
        let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<8).map { _ in chars.randomElement() ?? "A" })
    }
}
