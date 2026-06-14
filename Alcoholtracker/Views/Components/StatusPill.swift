import SwiftUI

// MARK: - StatusPill
//
// Colored capsule badge reflecting the current BACStatus.
// Used on HomeView beneath the BAC number, and on crew cards.

struct StatusPill: View {

    let status: BACStatus
    var skin: StatusSkin = .standard

    private var icon: String {
        switch status {
        case .sober:   return "checkmark.circle.fill"
        case .tipsy:   return "circle.fill"
        case .drunk:   return "exclamationmark.circle.fill"
        case .careful: return "exclamationmark.triangle.fill"
        case .danger:  return "xmark.octagon.fill"
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
            Text(status.label(for: skin))
                .font(.appCaptionBold)
        }
        .foregroundStyle(status.color)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(status.backgroundColor)
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(status.color.opacity(0.3), lineWidth: 0.5))
        .animation(.easeInOut(duration: 0.3), value: status)
    }
}

#Preview {
    VStack(spacing: 12) {
        ForEach(BACStatus.allCases, id: \.self) { s in
            HStack(spacing: 8) {
                StatusPill(status: s, skin: .standard)
                StatusPill(status: s, skin: .sailor)
                StatusPill(status: s, skin: .emoji)
            }
        }
    }
    .padding()
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
