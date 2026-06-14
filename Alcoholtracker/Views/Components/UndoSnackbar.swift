import SwiftUI

// MARK: - UndoSnackbar
//
// Bottom toast shown after an undoable action (drink added, session reset).
// Drunk-friendly: large tap target, auto-hides after a few seconds.

struct UndoSnackbar: View {
    let label: String
    let onUndo: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.appAccent)

            Text(label)
                .font(.appCaption)
                .foregroundStyle(Color.appText)
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            Spacer(minLength: 8)

            Button(action: onUndo) {
                Text("Rückgängig")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.appAccent.opacity(0.14))
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.35), radius: 14, y: 6)
        .padding(.horizontal, 20)
    }
}
