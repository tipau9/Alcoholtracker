import SwiftUI

// MARK: - Liquid Glass helper
//
// A single call site for the app's translucent card surfaces. On iOS 26+ it uses
// the real Liquid Glass material; on iOS 18-25 it falls back to the existing
// ultraThinMaterial + rounded clip, so behaviour is unchanged on older systems.
// The deployment target stays iOS 18; building requires the iOS 26 SDK (Xcode 26)
// because glassEffect is referenced even though it is gated behind #available.
//
// Only surfaces that are ALREADY translucent material should use this. Opaque
// Color.appCard surfaces stay as they are (theme-token rule, dark-only contrast).

extension View {
    @ViewBuilder
    func glassCard(cornerRadius: CGFloat = 16) -> some View {
        if #available(iOS 26, *) {
            self.glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            self
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        }
    }

    /// A hairline top-lit edge over a card's existing border, mimicking the way
    /// Apple's glass surfaces catch light from above. Adds on top of the normal
    /// `appBorder` stroke instead of replacing it, so it stays subtle rather than
    /// a full glow.
    func appleLichtkante(cornerRadius: CGFloat = 16) -> some View {
        overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .strokeBorder(
                    LinearGradient(
                        colors: [Color.white.opacity(0.20), Color.white.opacity(0)],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    lineWidth: 0.75
                )
        )
    }
}
