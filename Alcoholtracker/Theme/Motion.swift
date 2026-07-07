import SwiftUI

// MARK: - Motion
//
// Central animation vocabulary so every screen moves the same way.
// Use these tokens instead of ad-hoc .easeInOut / .spring values:
//
//   .appSnappy  - selection state (chips, segments, toggles)
//   .appSpring  - cards, banners and toasts sliding in and out
//   .appPop     - playful emphasis (counters, badges, unlocks)
//   .appGentle  - slow value drift (BAC number, gauges, bars)

extension Animation {
    static let appSnappy = Animation.snappy(duration: 0.28, extraBounce: 0.08)
    static let appSpring = Animation.spring(response: 0.42, dampingFraction: 0.82)
    static let appPop    = Animation.spring(response: 0.3, dampingFraction: 0.6)
    static let appGentle = Animation.easeInOut(duration: 0.35)
}

// MARK: - Shared transitions

extension AnyTransition {
    /// Banner dropping in from the top (med warning, mood prompt, errors).
    static let appBannerTop = AnyTransition.move(edge: .top)
        .combined(with: .opacity)
        .combined(with: .scale(scale: 0.96, anchor: .top))

    /// Toast / card rising from the bottom (undo snackbar, unlock toast, sip counter).
    static let appToastBottom = AnyTransition.move(edge: .bottom)
        .combined(with: .opacity)
        .combined(with: .scale(scale: 0.96, anchor: .bottom))
}

// MARK: - Pressable
//
// Springy scale-down press feedback for custom-styled buttons. Behaves like
// .buttonStyle(.plain) with a shrink while the finger is down, so it can
// replace .plain on any button that draws its own background.

struct PressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.65), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PressableButtonStyle {
    /// Standard press feedback for card- and row-sized buttons.
    static var pressable: PressableButtonStyle { PressableButtonStyle() }
    /// Stronger shrink for small controls (chips, circular icon buttons).
    static var pressableChip: PressableButtonStyle { PressableButtonStyle(scale: 0.9) }
}
