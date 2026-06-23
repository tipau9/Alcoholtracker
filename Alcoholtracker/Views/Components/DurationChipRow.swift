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

    // (label, minutes). 0 = auto-estimate from category + volume.
    private let options: [(String, Double)] = [
        ("Auto", 0),
        ("30 min", 30),
        ("1 Std", 60),
        ("2 Std", 120),
        ("3 Std", 180),
    ]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(options, id: \.1) { option in
                let isSelected = Int(durationMinutes) == Int(option.1)
                Button {
                    durationMinutes = option.1
                } label: {
                    Text(option.0)
                        .font(.appCaption)
                        .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(isSelected ? Color.appAccent : Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(isSelected ? Color.appAccent : Color.appBorder, lineWidth: 0.5)
                        )
                }
                .buttonStyle(.plain)
                .animation(.easeInOut(duration: 0.15), value: isSelected)
            }
        }
    }
}
