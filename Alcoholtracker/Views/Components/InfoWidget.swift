import SwiftUI

// MARK: - InfoWidget
//
// Small card used in the 2x2 home-screen grid.
// Spec interface: icon, label, value.
// Extended with optional iconColor and isHighlighted for HomeView use.

struct InfoWidget: View {

    let icon: String
    let label: String
    let value: String
    var iconColor: Color = .appAccent
    var isHighlighted: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 30, height: 30)
                .background(iconColor.opacity(0.13))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 3) {
                Text(value)
                    .font(.system(size: 20, weight: .light, design: .serif))
                    .foregroundStyle(Color.appText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .animation(.easeInOut(duration: 0.3), value: value)

                Text(label)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(
                    isHighlighted ? Color.statusOrange.opacity(0.5) : Color.appBorder,
                    lineWidth: 0.5
                )
        )
    }
}

#Preview {
    HStack(spacing: 12) {
        InfoWidget(icon: "car.fill",   label: "Bis 0,5 ‰", value: "1h 42m",
                   iconColor: .appAccent)
        InfoWidget(icon: "flame.fill", label: "Kalorien",  value: "380 kcal",
                   iconColor: .statusOrange, isHighlighted: true)
    }
    .padding()
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
