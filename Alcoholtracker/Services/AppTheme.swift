import Foundation
import SwiftUI

// MARK: - AppTheme
//
// Observable singleton bridging UserProfile accessibility flags and accent color
// into SwiftUI environment. ContentView syncs from UserProfile on load and on change.

@Observable
final class AppTheme {
    static let shared = AppTheme()

    var highContrast: Bool  = false
    var reducedMotion: Bool = false
    var largeText: Bool     = false

    // FIX FEATURE10: dynamic accent color driven by user preference
    var accentColorHex: String = "C9802F" {
        didSet { UserDefaults.standard.set(accentColorHex, forKey: "accentColorHex") }
    }

    var accentColor: Color { Color(hex: accentColorHex) }

    private init() {
        if let saved = UserDefaults.standard.string(forKey: "accentColorHex") {
            accentColorHex = saved
        }
    }

    func sync(from profile: UserProfile?) {
        guard let p = profile else { return }
        highContrast   = p.highContrast
        reducedMotion  = p.reducedMotion
        largeText      = p.largeText
        accentColorHex = p.accentColorHex
    }
}
