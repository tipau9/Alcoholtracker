import Foundation

enum JamCodeGenerator {
    static let codeLength = 6
    static func generate() -> String {
        let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<codeLength).map { _ in chars.randomElement() ?? "A" })
    }
}
