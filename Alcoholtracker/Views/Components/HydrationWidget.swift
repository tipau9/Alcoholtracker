import SwiftUI
import UIKit

// MARK: - HydrationWidget
//
// Full-width session card showing water intake, alcohol diuresis, and net
// hydration. Placed as a home-screen section widget (WidgetType.water).
// Passes an empty-state if the drinks array is empty.

struct HydrationWidget: View {
    let drinks: [Drink]
    // Optional so the exact, body-water-aware status/compensation are used when a
    // profile is available; falls back to the absolute model otherwise.
    var profile: UserProfile? = nil
    // Extra fluid lost to sweat on a warm night (from the weather model). Surfaced
    // so the "trink mehr bei Wärme" adjustment is visible rather than silent.
    var extraSweatML: Double = 0

    // Real logged water glasses (counts into net + hangover prediction).
    @State private var loggedGlasses: Int = WaterLog.glassesToday()

    private var waterIn: Double   { HydrationCalculator.sessionWaterIn(drinks: drinks) }
    private var diuresis: Double  { HydrationCalculator.sessionDiuresisLoss(drinks: drinks) }
    private var mixerBonus: Double { HydrationCalculator.sessionMixerWaterContribution(drinks: drinks) }

    private var loggedML: Double  { Double(loggedGlasses) * WaterLog.glassML }
    private var net: Double       { HydrationCalculator.sessionNetHydration(drinks: drinks) + loggedML - extraSweatML }
    // Exact compensation (grossed up for ADH pass-through), not the bare deficit.
    private var extraWater: Int   { HydrationCalculator.compensationWaterMl(netML: net) }

    private var status: HydrationStatus {
        if let p = profile { return HydrationCalculator.hydrationStatus(netML: net, profile: p) }
        return HydrationCalculator.hydrationStatus(netML: net)
    }

    private var netColor: Color { status.color }
    private var netLabel: String { status.label }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionLabel(text: "Hydration")

            if drinks.isEmpty {
                emptyState
                // Allow pre-hydration logging before the first drink of the night.
                waterLogRow
            } else {
                stats
                waterLogRow
                bar
                recommendation
            }
        }
        .onAppear { loggedGlasses = WaterLog.glassesToday() }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }

    // MARK: Empty

    private var emptyState: some View {
        HStack(spacing: 12) {
            Image(systemName: "drop.fill")
                .font(.system(size: 20))
                .foregroundStyle(Color.appTextMuted)
            Text("Noch keine Getränke heute.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextMuted)
        }
        .padding(.vertical, 8)
    }

    // MARK: Stats rows

    private var stats: some View {
        VStack(spacing: 10) {
            HydrationRow(
                icon: "drop.fill",
                iconColor: .appAccent,
                label: "Wasseraufnahme",
                value: "+\(Int(waterIn)) ml",
                detail: mixerBonus > 0 ? "davon \(Int(mixerBonus)) ml aus Mixer" : nil,
                valueColor: .appText
            )

            HydrationRow(
                icon: "arrow.down",
                iconColor: .statusOrange,
                label: "Alkohol-Diurese",
                value: "-\(Int(diuresis)) ml",
                detail: nil,
                valueColor: .statusOrange
            )

            Rectangle()
                .fill(Color.appBorder)
                .frame(height: 0.5)

            HStack {
                Image(systemName: "equal")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(netColor)
                    .frame(width: 28, height: 28)
                    .background(netColor.opacity(0.13))
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                Text("Netto")
                    .font(.appBody)
                    .foregroundStyle(Color.appText)

                Spacer()

                Text(netValueString)
                    .font(.appBodyBold)
                    .foregroundStyle(netColor)
                    .contentTransition(.numericText())
                    .animation(.easeInOut(duration: 0.3), value: net)

                Text(netLabel)
                    .font(.appMicro)
                    .foregroundStyle(netColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(netColor.opacity(0.13))
                    .clipShape(Capsule())
            }
        }
    }

    private var netValueString: String {
        net >= 0 ? "+\(Int(net)) ml" : "\(Int(net)) ml"
    }

    // MARK: Water logging

    private var waterLogRow: some View {
        HStack(spacing: 12) {
            Image(systemName: "waterbottle.fill")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.statusGreen)
                .frame(width: 28, height: 28)
                .background(Color.statusGreen.opacity(0.13))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 1) {
                Text("Wasser geloggt")
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                Text("\(loggedGlasses) \(loggedGlasses == 1 ? "Glas" : "Gläser") (\(Int(loggedML)) ml)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                    .contentTransition(.numericText())
            }

            Spacer()

            Button {
                WaterLog.removeGlassToday()
                withAnimation(.easeInOut(duration: 0.2)) { loggedGlasses = WaterLog.glassesToday() }
            } label: {
                Image(systemName: "minus.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(loggedGlasses > 0 ? Color.appTextDim : Color.appBorder)
            }
            .buttonStyle(.plain)
            .disabled(loggedGlasses == 0)
            .accessibilityLabel("Glas Wasser entfernen")

            Button {
                WaterLog.addGlassToday()
                withAnimation(.easeInOut(duration: 0.2)) { loggedGlasses = WaterLog.glassesToday() }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Color.appAccent)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Glas Wasser hinzufügen")
        }
    }

    // MARK: Bar

    private var bar: some View {
        GeometryReader { geo in
            let total = max(waterIn, diuresis, 1)
            let inW   = geo.size.width * min(waterIn / total, 1.0)
            let netW  = net >= 0
                ? geo.size.width * min(net / total, 1.0)
                : 0.0

            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.statusOrange.opacity(0.25))

                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.appAccent.opacity(0.35))
                    .frame(width: inW)

                RoundedRectangle(cornerRadius: 6)
                    .fill(netColor.opacity(0.8))
                    .frame(width: netW)
            }
        }
        .frame(height: 8)
        .animation(.easeInOut(duration: 0.4), value: net)
    }

    // MARK: Recommendation

    private var recommendation: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Image(systemName: extraWater == 0 ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(extraWater == 0 ? Color.statusGreen : Color.statusOrange)

                if extraWater == 0 {
                    Text("Kein extra Wasser nötig.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                } else {
                    Text("Trinke noch ca. \(extraWater) ml Wasser extra.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
            }

            if extraSweatML > 0 {
                HStack(spacing: 8) {
                    Image(systemName: "thermometer.sun.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.statusOrange)
                    Text("Inkl. ca. \(Int(extraSweatML)) ml Schweißverlust (warmes Wetter).")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextMuted)
                }
            }
        }
    }
}

// MARK: - Row helper

private struct HydrationRow: View {
    let icon: String
    let iconColor: Color
    let label: String
    let value: String
    let detail: String?
    let valueColor: Color

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 28, height: 28)
                .background(iconColor.opacity(0.13))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                if let detail {
                    Text(detail)
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
            }

            Spacer()

            Text(value)
                .font(.appBodyBold)
                .foregroundStyle(valueColor)
                .contentTransition(.numericText())
                .animation(.easeInOut(duration: 0.3), value: value)
        }
    }
}

#Preview {
    let sampleDrinks: [Drink] = [
        Drink(name: "Bier", volume: 330, abv: 5.0, calories: 150,
              iconName: "mug.fill", category: .beer),
        Drink(name: "Gin + Tonic", volume: 200, abv: 10.0, calories: 110,
              iconName: "drop.fill", category: .mixed,
              mixerVolume: 150, mixerWaterContent: 91)
    ]

    VStack(spacing: 20) {
        HydrationWidget(drinks: sampleDrinks)
        HydrationWidget(drinks: [])
    }
    .padding()
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
