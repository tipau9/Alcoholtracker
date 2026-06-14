import Foundation

enum JamCodeGenerator {
    static func generate() -> String {
        let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<6).map { _ in chars.randomElement() ?? "A" })
    }
}
