import SwiftUI

// MARK: - Motion
//
// Central animation vocabulary so every screen moves the same way.
// Design rule: every animation here must have a purpose and could appear in a
// banking app. No bounces, no overshoot, no playfulness.
//
//   .appSnappy  - selection state (chips, segments, toggles)
//   .appSpring  - cards, banners and toasts sliding in and out
//   .appGentle  - slow value drift (BAC number, gauges, bars)

extension Animation {
    static var appSnappy: Animation {
        AppTheme.shared.reducedMotion ? .linear(duration: 0.01) : .smooth(duration: 0.22)
    }

    static var appSpring: Animation {
        AppTheme.shared.reducedMotion ? .linear(duration: 0.01) : .smooth(duration: 0.38)
    }

    static var appGentle: Animation {
        AppTheme.shared.reducedMotion ? .linear(duration: 0.01) : .easeInOut(duration: 0.35)
    }
}

// MARK: - Shared transitions

extension AnyTransition {
    /// Banner dropping in from the top (med warning, mood prompt, errors).
    static var appBannerTop: AnyTransition {
        AppTheme.shared.reducedMotion ? .opacity : .move(edge: .top).combined(with: .opacity)
    }

    /// Toast / card rising from the bottom (undo snackbar, unlock toast, sip counter).
    static var appToastBottom: AnyTransition {
        AppTheme.shared.reducedMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity)
    }
}

// MARK: - Pressable
//
// Subtle scale-down press feedback for custom-styled buttons. Behaves like
// .buttonStyle(.plain) with a shrink while the finger is down, so it can
// replace .plain on any button that draws its own background.

struct PressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.97

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(AppTheme.shared.reducedMotion ? 1 : (configuration.isPressed ? scale : 1))
            .opacity(AppTheme.shared.reducedMotion ? 1 : (configuration.isPressed ? 0.85 : 1))
            .animation(
                AppTheme.shared.reducedMotion ? .linear(duration: 0.01) : .easeOut(duration: 0.12),
                value: configuration.isPressed
            )
    }
}

extension ButtonStyle where Self == PressableButtonStyle {
    /// Standard press feedback for card- and row-sized buttons.
    static var pressable: PressableButtonStyle { PressableButtonStyle() }
    /// Slightly stronger shrink for small controls (chips, circular icon buttons).
    static var pressableChip: PressableButtonStyle { PressableButtonStyle(scale: 0.94) }
}
