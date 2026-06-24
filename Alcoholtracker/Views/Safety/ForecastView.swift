import SwiftUI

// MARK: - ForecastView (B1)
// Shows how many more drinks are safe before a given target time and BAC limit.

struct ForecastView: View {

    let drinks: [Drink]
    let profile: UserProfile

    @State private var targetTime: Date = Date().addingTimeInterval(3 * 3600)
    // Defaults to the user's driving limit in onAppear (0,5 ‰ or 0,0 ‰ in Probezeit).
    @State private var targetBAC: Double = 0.5

    private var hoursUntilTarget: Double {
        max(0, targetTime.timeIntervalSinceNow / 3600)
    }

    // Full curve evaluation at the target moment so drinks still in the
    // absorption phase are counted; a linear decay from the current value
    // would underestimate the BAC and overestimate the allowed drinks.
    private var projectedBACAtTarget: Double {
        BACCalculator.currentBAC(
            drinks: drinks,
            profile: profile,
            at: targetTime,
            stomachStatus: profile.defaultStomachStatus,
            conservative: profile.conservativeForSafety
        )
    }

    private var allowedAdditionalBAC: Double {
        max(0, targetBAC - projectedBACAtTarget)
    }

    // Realistic peak one standard drink (0,33 l / 5%) reaches on its own, identical
    // to the "+x ‰" badge in the add sheet, so both screens show the same number.
    // Shown to the user; NOT used for the safety budget below.
    private var oneStandardDrinkPeak: Double {
        max(0.01, BACCalculator.projectedPeak(
            volume: 330, abv: 5.0, category: .beer,
            profile: profile, stomachStatus: profile.defaultStomachStatus,
            conservative: profile.conservativeForSafety
        ))
    }

    // Conservative per-drink figure used only to count how many more drinks fit:
    // the raw Widmark peak with no per-drink elimination credit. projectedPeak
    // subtracts a full absorption window of elimination, which is valid for one
    // isolated drink but NOT when stacking several before a target hours away, so
    // budgeting on it would over-suggest (e.g. 4 beers for a 0,5 ‰ goal). Keeping
    // the budget on the raw peak errs safe.
    private var budgetPerDrinkBAC: Double {
        max(0.01, BACCalculator.bacContribution(
            volume: 330, abv: 5.0,
            weight: profile.weight,
            distributionFactor: profile.distributionFactor
        ))
    }

    private var allowedDrinks: Int {
        Int(allowedAdditionalBAC / budgetPerDrinkBAC)
    }

    private var isAlreadyOverLimit: Bool {
        projectedBACAtTarget >= targetBAC
    }

    // MARK: Body

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            Divider().background(Color.appBorder.opacity(0.5))

            VStack(alignment: .leading, spacing: 16) {
                targetTimePicker
                targetBACPicker
                resultCard
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .onAppear {
            // Start from the user's actual driving limit (0,0 ‰ in Probezeit).
            targetBAC = profile.drivingLimit
        }
        .onChange(of: profile.isProbationaryDriver) { _, _ in
            // The Fahr-Grenzwert segment above can flip Probezeit on/off while this
            // view is on screen. Without this the old target (e.g. 0,5 ‰) stays
            // selected, so in Probezeit no pill is highlighted and the result card
            // is computed against an illegal limit until the user taps it by hand.
            // Snap the selection to the new driving limit automatically.
            withAnimation(.easeInOut(duration: 0.15)) {
                targetBAC = profile.drivingLimit
            }
        }
    }

    // MARK: Subviews

    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: "clock.arrow.2.circlepath")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.appAccent)
            Text("Vorausschau")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appAccent)
            Spacer()
            if profile.conservativeForSafety {
                Text("WORST-CASE")
                    .font(.appMicro)
                    .foregroundStyle(Color.appBackground)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.statusOrange)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 10)
    }

    private var targetTimePicker: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("WANN MUSST DU FIT SEIN?")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                    .tracking(1)
                Text("\(String(format: "%.1f", hoursUntilTarget)) h ab jetzt")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }
            Spacer()
            DatePicker("", selection: $targetTime, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                .labelsHidden()
                .colorScheme(.dark)
                .tint(Color.appAccent)
        }
    }

    // Planning targets respect the user's legal limit. In der Probezeit (0,0 ‰)
    // "Fahrbereit" equals "Nüchtern", so a single target is shown instead of
    // offering an illegal 0,5 ‰ goal.
    private var planningTargets: [(Double, String)] {
        let limit = profile.drivingLimit
        if limit <= 0.0 {
            return [(0.0, "Fahrbereit (0,0 ‰)")]
        }
        return [(0.0, "Nüchtern"), (limit, "Fahrbereit")]
    }

    private var targetBACPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("GRENZWERT")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .tracking(1)
            HStack(spacing: 8) {
                ForEach(planningTargets, id: \.0) { limit, label in
                    Button {
                        withAnimation(.easeInOut(duration: 0.15)) { targetBAC = limit }
                    } label: {
                        Text(label)
                            .font(.appCaption)
                            .foregroundStyle(targetBAC == limit ? Color.appBackground : Color.appTextDim)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(targetBAC == limit ? Color.appAccent : Color.appBackground)
                            .clipShape(Capsule())
                            .overlay(Capsule().strokeBorder(
                                targetBAC == limit ? Color.appAccent : Color.appBorder,
                                lineWidth: 0.5
                            ))
                    }
                    .buttonStyle(.plain)
                    .animation(.easeInOut(duration: 0.15), value: targetBAC)
                }
            }
        }
    }

    private var resultCard: some View {
        HStack(spacing: 16) {
            if isAlreadyOverLimit {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 28, weight: .light))
                    .foregroundStyle(Color.statusRed)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Besser nichts mehr trinken")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.statusRed)
                    Text("Ziel-BAC bereits überschritten")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text("\(allowedDrinks)")
                            .font(.system(size: 52, weight: .light, design: .serif))
                            .foregroundStyle(allowedDrinks > 0 ? Color.appAccent : Color.appTextDim)
                            .monospacedDigit()
                        Text("noch möglich")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Text("Standarddrinks · je ~\(String(format: "%.2f", oneStandardDrinkPeak)) ‰, konservativ gerechnet")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
            }
            Spacer()
        }
        .padding(14)
        .background(Color.appBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}
