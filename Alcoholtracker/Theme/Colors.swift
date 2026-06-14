import SwiftUI

// MARK: - Promille Color Palette
//
// Dark-first, warm bar aesthetic. A high-contrast palette is layered on top
// for the accessibility toggle; swap by reading UserProfile.highContrast.
//
// Usage: Color.promille.background, Color.promille.accent, etc.

extension Color {
    enum promille {

        // MARK: Backgrounds
        static let background = Color(hex: "#0A0807")   // very dark warm brown
        static let card        = Color(hex: "#13100D")
        static let border      = Color(hex: "#2A211C")

        // MARK: Text
        static let textPrimary = Color(hex: "#F0E8D2")
        static let textDimmed  = Color(hex: "#A89E89")
        static let textMuted   = Color(hex: "#6E665B")

        // MARK: Accent
        static let accent      = Color(hex: "#C9802F")   // warm amber

        // MARK: Status
        static let statusGreen   = Color(hex: "#6B9B6E")
        static let statusYellow  = Color(hex: "#E5C158")
        static let statusOrange  = Color(hex: "#E5A055")
        static let statusRed     = Color(hex: "#E55050")
        static let statusDarkRed = Color(hex: "#B82828")

        // MARK: High-contrast overrides
        enum highContrast {
            static let background = Color.black
            static let card        = Color(hex: "#111111")
            static let textPrimary = Color.white
            static let accent      = Color(hex: "#FFB04D")
        }
    }
}

// MARK: - BACStatus color mapping

extension BACStatus {
    var color: Color {
        switch self {
        case .sober:   return .promille.statusGreen
        case .tipsy:   return .promille.statusYellow
        case .drunk:   return .promille.statusOrange
        case .careful: return .promille.statusRed
        case .danger:  return .promille.statusDarkRed
        }
    }

    var backgroundColor: Color {
        color.opacity(0.15)
    }
}

// MARK: - Hex initialiser

extension Color {
    init(hex: String) {
        let h = hex.trimmingCharacters(in: .init(charactersIn: "#"))
        var rgb: UInt64 = 0
        Scanner(string: h).scanHexInt64(&rgb)

        let r = Double((rgb >> 16) & 0xFF) / 255
        let g = Double((rgb >>  8) & 0xFF) / 255
        let b = Double( rgb        & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}

// MARK: - Flat Color.app* aliases (spec-compatible shorthand for all new views)

extension Color {
    // Backgrounds
    static let appBackground = promille.background
    static let appCard       = promille.card
    static let appBorder     = promille.border

    // Text
    static let appText       = promille.textPrimary
    static let appTextDim    = promille.textDimmed
    static let appTextMuted  = promille.textMuted

    // FIX FEATURE10: dynamic accent driven by user preference via AppTheme
    static var appAccent: Color { AppTheme.shared.accentColor }

    // Status (no prefix, matches spec)
    static let statusGreen   = promille.statusGreen
    static let statusYellow  = promille.statusYellow
    static let statusOrange  = promille.statusOrange
    static let statusRed     = promille.statusRed
    static let statusDarkRed = promille.statusDarkRed
}

// MARK: - Convenience view modifiers

struct PromilleCardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Color.promille.card)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.promille.border, lineWidth: 0.5)
            )
    }
}

extension View {
    func promilleCard() -> some View {
        modifier(PromilleCardStyle())
    }
}

extension Color {
    func toHex() -> String? {
        let uic = UIColor(self)
        guard let components = uic.cgColor.components, components.count >= 3 else {
            return nil
        }
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        var a = Float(1.0)
        
        if components.count >= 4 {
            a = Float(components[3])
        }
        
        if a != Float(1.0) {
            return String(format: "%02lX%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255), lroundf(a * 255))
        } else {
            return String(format: "%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255))
        }
    }
}
