import SwiftUI

// MARK: - AccentColorPicker (Feature 10)
// Grid of preset accent colors. Saving writes to both UserProfile (persistent) and
// AppTheme.shared (immediate UI update via @Observable).

struct AccentColorPicker: View {

    @Binding var selectedHex: String

    private let options: [(name: String, hex: String)] = [
        ("Bernstein",  "C9802F"),
        ("Teal",       "4AB0A5"),
        ("Ozean",      "3B82B0"),
        ("Lavendel",   "8B7EC8"),
        ("Salbei",     "6B9B6E"),
        ("Rose",       "C07B8F"),
        ("Koralle",    "E07B6B"),
        ("Silber",     "9CA3AF"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ColorPicker("Eigene Farbe (RGB)", selection: Binding(
                get: { Color(hex: selectedHex) },
                set: { newColor in
                    if let hex = newColor.toHex() {
                        selectedHex = hex
                        AppTheme.shared.accentColorHex = hex
                    }
                }
            ), supportsOpacity: false)
            .font(.appBodyBold)
            .foregroundStyle(Color.appText)
            .padding(.bottom, 8)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 14) {
                ForEach(options, id: \.hex) { option in
                    Button {
                        selectedHex = option.hex
                        AppTheme.shared.accentColorHex = option.hex
                    } label: {
                        VStack(spacing: 5) {
                            ZStack {
                                Circle()
                                    .fill(Color(hex: option.hex))
                                    .frame(width: 44, height: 44)
                                if selectedHex == option.hex {
                                    Circle()
                                        .strokeBorder(Color.white, lineWidth: 2.5)
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(.white)
                                }
                            }
                            Text(option.name)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(
                                    selectedHex == option.hex
                                    ? Color(hex: option.hex)
                                    : Color.appTextMuted
                                )
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
