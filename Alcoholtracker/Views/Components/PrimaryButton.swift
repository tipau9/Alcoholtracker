import SwiftUI

// MARK: - PrimaryButton
//
// Full-width accent button for primary actions.
// Use the `role` param for destructive variants (red background).

struct PrimaryButton: View {

    let title: String
    var icon: String? = nil
    var isDestructive: Bool = false
    var isDisabled: Bool = false
    let action: () -> Void

    private var tint: Color { isDestructive ? .statusRed : .appAccent }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                }
                Text(title)
                    .font(.appBodyBold)
            }
            .foregroundStyle(Color.appBackground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(tint.opacity(isDisabled ? 0.4 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(color: tint.opacity(isDisabled ? 0 : 0.3), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
    }
}

// MARK: - FAB variant

struct FABButton: View {
    let title: String
    var icon: String = "plus"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                Text(title)
                    .font(.appBodyBold)
            }
            .foregroundStyle(Color.appBackground)
            .padding(.horizontal, 22)
            .padding(.vertical, 15)
            .background(Color.appAccent)
            .clipShape(Capsule())
            .shadow(color: Color.appAccent.opacity(0.45), radius: 14, x: 0, y: 6)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    VStack(spacing: 16) {
        PrimaryButton(title: "Weiter", icon: "arrow.right") {}
        PrimaryButton(title: "Sitzung zurücksetzen", isDestructive: true) {}
        PrimaryButton(title: "Deaktiviert", isDisabled: true) {}
        FABButton(title: "Drink hinzufügen") {}
    }
    .padding()
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
