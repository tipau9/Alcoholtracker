import SwiftUI

// MARK: - DurationChipRow
//
// Picks how long a drink is consumed over ("verzögerter Start" / sipping). The
// value maps to Drink.drinkDurationMinutes, where 0 means "auto-estimate" and a
// positive value stretches the BAC absorption window (see BACCalculator
// .absorptionWindowMinutes), flattening and lowering the peak for slowly sipped
// drinks. Short durations may match the auto estimate and leave the curve
// unchanged; longer ones (a bottle of wine over two hours) move it noticeably.

struct DurationChipRow: View {
    @Binding var durationMinutes: Double
    let estimatedMinutes: Double?

    // (label, minutes). 0 = auto-estimate from category + volume.
    private let options: [(String, Double)] = [
        ("Auto", 0),
        ("30 min", 30),
        ("1 Std", 60),
        ("2 Std", 120),
        ("3 Std", 180),
    ]

    init(durationMinutes: Binding<Double>, estimatedMinutes: Double? = nil) {
        self._durationMinutes = durationMinutes
        self.estimatedMinutes = estimatedMinutes
    }

    private var isCustom: Bool {
        durationMinutes > 0 && !options.dropFirst().contains { Int($0.1) == Int(durationMinutes) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                ForEach(options, id: \.1) { option in
                    let isSelected = Int(durationMinutes) == Int(option.1)
                    Button {
                        durationMinutes = option.1
                    } label: {
                        VStack(spacing: 2) {
                            Text(option.0)
                                .font(.appCaption)
                            if option.1 == 0, let estimatedMinutes {
                                Text(formatDuration(estimatedMinutes))
                                    .font(.appMicro)
                            }
                        }
                        .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, option.1 == 0 && estimatedMinutes != nil ? 6 : 9)
                        .background(isSelected ? Color.appAccent : Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(isSelected ? Color.appAccent : Color.appBorder, lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.pressable)
                    .animation(.appSnappy, value: isSelected)
                }
            }

            HStack(spacing: 10) {
                Button {
                    if durationMinutes <= 0 {
                        durationMinutes = estimatedMinutes ?? 30
                    }
                } label: {
                    Label("Eigene Dauer", systemImage: "slider.horizontal.3")
                        .font(.appCaptionBold)
                        .foregroundStyle(isCustom ? Color.appBackground : Color.appTextDim)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(isCustom ? Color.appAccent : Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(isCustom ? Color.appAccent : Color.appBorder, lineWidth: 0.5)
                        )
                }
                .buttonStyle(.pressable)

                if durationMinutes > 0 {
                    HStack(spacing: 6) {
                        Text(formatDuration(durationMinutes))
                            .font(.appCaptionBold)
                            .foregroundStyle(Color.appText)
                            .monospacedDigit()
                            .frame(minWidth: 56, alignment: .leading)
                        Stepper("", value: $durationMinutes, in: 1...240, step: 5)
                            .labelsHidden()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )
                }
            }
        }
    }

    private func formatDuration(_ minutes: Double) -> String {
        let rounded = max(1, Int(minutes.rounded()))
        if rounded < 60 { return "\(rounded) min" }
        let hours = rounded / 60
        let mins = rounded % 60
        return mins == 0 ? "\(hours) Std" : "\(hours)h \(mins)m"
    }
}
