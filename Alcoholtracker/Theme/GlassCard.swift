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
}
