import SwiftUI

// MARK: - StatusSkinPickerView
//
// Full-screen sheet for selecting a StatusSkin.
// Presented from SettingsView display section.

struct StatusSkinPickerView: View {
    @Binding var skin: StatusSkin
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack {
                    Text("Status-Skin")
                        .font(.appHeadline)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appTextDim)
                            .frame(width: 32, height: 32)
                            .background(Color.appCard)
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 16)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        Text("Wähle die Bezeichnungen für deinen Promille-Status.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.bottom, 4)

                        ForEach(StatusSkin.allCases, id: \.self) { s in
                            SSPSkinCard(skin: s, isSelected: skin == s) {
                                withAnimation(.spring(response: 0.25)) { skin = s }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 40)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Skin card

private struct SSPSkinCard: View {
    let skin: StatusSkin
    let isSelected: Bool
    let onTap: () -> Void

    private let allStatuses: [BACStatus] = [.sober, .tipsy, .drunk, .careful, .danger]

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(skin.displayName)
                            .font(.appBodyBold)
                            .foregroundStyle(isSelected ? Color.appAccent : Color.appText)
                        Text(skin.skinDescription)
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.appAccent)
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(allStatuses, id: \.self) { status in
                            Text(skin.label(for: status))
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(status.color)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(status.color.opacity(0.13))
                                .clipShape(Capsule())
                                .lineLimit(1)
                        }
                    }
                }
            }
            .padding(14)
            .background(isSelected ? Color.appAccent.opacity(0.08) : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: isSelected ? 1.5 : 0.5
                    )
            )
        }
        .buttonStyle(.plain)
        .contentShape(Rectangle())
    }
}

#Preview {
    @Previewable @State var skin: StatusSkin = .standard
    StatusSkinPickerView(skin: $skin)
        .preferredColorScheme(.dark)
}
