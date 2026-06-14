import SwiftUI
import UIKit

// MARK: - Settings reusable rows
//
// Small, self-contained row controls used to compose the settings sections.
// Extracted from SettingsView so the screen is assembled from focused
// components instead of one massive file, which keeps SwiftUI state diffing and
// Xcode compile times manageable.

// MARK: - Numeric row

struct STNumericRow: View {
    let label: String
    let unit: String
    let format: String
    let range: ClosedRange<Double>
    @Binding var value: Double

    @State private var text = ""
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 12) {
            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            TextField("", text: $text)
                .keyboardType(.decimalPad)
                .font(.appBodyBold)
                .foregroundStyle(Color.appAccent)
                .multilineTextAlignment(.trailing)
                .frame(width: 64)
                .focused($isFocused)
                .onSubmit { commit() }
            Text(unit)
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .frame(width: 36, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .onAppear { text = String(format: format, value) }
        .onChange(of: value) { _, v in text = String(format: format, v) }
        .onChange(of: isFocused) { _, focused in if !focused { commit() } }
    }

    private func commit() {
        guard let v = Double(text.replacingOccurrences(of: ",", with: ".")) else {
            text = String(format: format, value)
            return
        }
        value = max(range.lowerBound, min(range.upperBound, v))
    }
}

// MARK: - Gender row

struct STGenderRow: View {
    @Binding var gender: Gender

    var body: some View {
        HStack {
            Text("Geschlecht")
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            Picker("Geschlecht", selection: $gender) {
                ForEach(Gender.allCases, id: \.self) { g in
                    Text(g.localizedName).tag(g)
                }
            }
            .tint(Color.appAccent)
            .pickerStyle(MenuPickerStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Elimination rate row

struct STElimRow: View {
    @Binding var value: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Abbaurate")
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                Spacer()
                Text(String(format: "%.3f ‰/h", value))
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .monospacedDigit()
            }
            Slider(value: $value, in: 0.10...0.20, step: 0.005)
                .tint(Color.appAccent)
            HStack {
                Text("Langsam (0,10)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Spacer()
                Text("Schnell (0,20)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

// MARK: - Contact field row

struct STContactField: View {
    let label: String
    let placeholder: String
    let keyboard: UIKeyboardType
    @Binding var value: String

    var body: some View {
        HStack(spacing: 12) {
            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appText)
                .frame(width: 120, alignment: .leading)
            TextField(placeholder, text: $value)
                .keyboardType(keyboard)
                .font(.appBody)
                .foregroundStyle(Color.appAccent)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

// MARK: - Warning threshold row

struct STThresholdRow: View {
    @Binding var value: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Warnschwelle")
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                Spacer()
                Text(String(format: "%.2f ‰", value))
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .monospacedDigit()
            }
            Slider(value: $value, in: 0.2...1.5, step: 0.05)
                .tint(Color.appAccent)
            HStack {
                Text("Entspannt (0,2)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Spacer()
                Text("Streng (1,5)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

// MARK: - BAC threshold row (for customizable status thresholds)

struct STBACThresholdRow: View {
    let label: String
    let color: Color
    let range: ClosedRange<Double>
    @Binding var value: Double

    // Guard against inverted ranges when thresholds are at their bounds
    private var safeRange: ClosedRange<Double> {
        guard range.lowerBound < range.upperBound else {
            return range.lowerBound...max(range.lowerBound + 0.05, range.upperBound)
        }
        return range
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HStack(spacing: 6) {
                    Circle()
                        .fill(color)
                        .frame(width: 8, height: 8)
                    Text(label)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                }
                Spacer()
                Text(String(format: "%.2f ‰", value))
                    .font(.appCaptionBold)
                    .foregroundStyle(color)
                    .monospacedDigit()
            }
            Slider(value: $value, in: safeRange, step: 0.05)
                .tint(color)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Home style row

struct STHomeStyleRow: View {
    @Binding var style: HomeStyle

    var body: some View {
        HStack {
            Text("Home-Ansicht")
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            Picker("Home-Ansicht", selection: $style) {
                ForEach(HomeStyle.allCases, id: \.self) { s in
                    Text(s.localizedName).tag(s)
                }
            }
            .tint(Color.appAccent)
            .pickerStyle(MenuPickerStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Toggle row

struct STToggleRow: View {
    let icon: String
    let label: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Text(subtitle)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
            }
        }
        .tint(Color.appAccent)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Stomach status row

struct STStomachRow: View {
    @Binding var status: StomachStatus

    var body: some View {
        HStack {
            Text("Standard-Magen")
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            Picker("Standard-Magen", selection: $status) {
                ForEach(StomachStatus.allCases, id: \.self) { s in
                    Text(s.localizedName).tag(s)
                }
            }
            .tint(Color.appAccent)
            .pickerStyle(.menu)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Destructive action row

struct STDestructiveRow: View {
    let icon: String
    let label: String
    let action: () -> Void

    @State private var showConfirm = false

    var body: some View {
        Button { showConfirm = true } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.statusRed)
                    .frame(width: 22)
                Text(label)
                    .font(.appBody)
                    .foregroundStyle(Color.statusRed)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        .alert("Fotos löschen?", isPresented: $showConfirm) {
            Button("Löschen", role: .destructive, action: action)
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Alle gespeicherten Erinnerungsfotos werden dauerhaft gelöscht.")
        }
    }
}

// MARK: - Status skin row

struct STSkinRow: View {
    @Binding var skin: StatusSkin
    @State private var showPicker = false

    var body: some View {
        Button { showPicker = true } label: {
            HStack(spacing: 12) {
                Image(systemName: "textformat.characters")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Status-Skin")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Text(skin.displayName)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        .contentShape(Rectangle())
        .sheet(isPresented: $showPicker) {
            StatusSkinPickerView(skin: $skin)
        }
    }
}

// MARK: - Share sheet wrapper

struct STShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
