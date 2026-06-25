import Combine
import SwiftUI
import SwiftData

// MARK: - SafetyView
// Shows current BAC, time-to-threshold timers, and emergency action buttons.

struct SafetyView: View {

    @Environment(\.openURL) private var openURL
    @Environment(\.modelContext) private var context
    @Environment(SupabaseService.self) private var supabase
    @Query private var profiles: [UserProfile]
    @Query private var allDrinks: [Drink]
    @State private var now = Date()
    @State private var locationService = LocationService()
    @State private var showRidePicker = false

    init() {
        // Only today's logical day is ever evaluated here, so fetch just the
        // last 48h from the database instead of paging the whole history into
        // RAM. 48h comfortably covers the 06:00 logical-day boundary. The cutoff
        // is fixed at view creation; the tab is rebuilt on relaunch.
        let cutoff = Date().addingTimeInterval(-48 * 3600)
        _allDrinks = Query(
            filter: #Predicate { $0.timestamp >= cutoff },
            sort: \.timestamp
        )
    }

    private var profile: UserProfile? { profiles.first }

    private var todaysDrinks: [Drink] {
        let logicalStart = Calendar.current.logicalDayStart(for: now)
        return allDrinks.filter { $0.timestamp >= logicalStart }
    }

    private var currentBAC: Double {
        guard let p = profile else { return 0 }
        // Use the same conservative setting as the readiness timers below so the big
        // headline pegel and the Nüchtern/Fahrbereit times are derived from one curve.
        return BACCalculator.currentBAC(drinks: todaysDrinks, profile: p, at: now,
                                        stomachStatus: stomachStatus, conservative: p.conservativeForSafety)
    }

    private var stomachStatus: StomachStatus { profile?.defaultStomachStatus ?? .light }
    private var bacStatus: BACStatus {
        guard let p = profile else { return BACStatus(bac: currentBAC) }
        return BACStatus(bac: currentBAC, profile: p)
    }
    private var skin: StatusSkin { profile?.statusSkin ?? .standard }

    private func hoursUntil(_ target: Double) -> Double? {
        guard let p = profile else { return nil }
        // The readiness timers respect the "Konservativ rechnen" switch so the
        // Fahrbereit/Nüchtern times are worst-case when the user wants them safe.
        return BACCalculator.hoursUntilBAC(
            target, drinks: todaysDrinks, profile: p, from: now,
            stomachStatus: stomachStatus, conservative: p.conservativeForSafety)
    }

    // MARK: Body

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                SFTopBar()
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 24) {
                        SFBACCard(bac: currentBAC, status: bacStatus, skin: skin)
                        timersSection
                        driveModeSection
                        if let p = profile {
                            ForecastView(drinks: todaysDrinks, profile: p)
                        }
                        actionsSection
                        SFDisclaimer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 32)
                }
            }
        }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { date in
            now = date
        }
        .task(id: supabase.isSignedIn) {
            // Make sure the server reflects the current Probezeit setting even if
            // it was chosen before this synced, so friends get the right label.
            guard supabase.isSignedIn, let p = profile else { return }
            try? await supabase.updateProbation(p.isProbationaryDriver)
        }
        .sheet(isPresented: $showRidePicker) {
            RidePickerSheet(locationService: locationService)
        }
    }

    // MARK: Timers section

    private var timersSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "ZEITEN")
            VStack(spacing: 0) {
                SFTimerRow(
                    label: "Nüchtern",
                    icon: "checkmark.circle.fill",
                    iconColor: Color.statusGreen,
                    hours: hoursUntil(profile?.tipsyThreshold ?? 0.01),
                    readyLabel: "Bereits nüchtern"
                )
                Divider()
                    .background(Color.appBorder)
                    .padding(.leading, 54)
                SFTimerRow(
                    label: isProbation ? "Fahrbereit (0,0 ‰)" : "Fahrbereit (0,5 ‰)",
                    icon: "car.fill",
                    iconColor: Color.appAccent,
                    hours: hoursUntil(driveTarget),
                    readyLabel: "Fahrbereit"
                )
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }

    // MARK: Drive mode (Probezeit)

    private var isProbation: Bool { profile?.isProbationaryDriver ?? false }

    // In Probezeit the 0,0 ‰ limit is unreachable as an exact value, so the
    // "fahrbereit" target is the practically-sober tipsy threshold.
    private var driveTarget: Double {
        isProbation ? (profile?.tipsyThreshold ?? 0.01) : 0.5
    }

    private func setProbation(_ on: Bool) {
        profile?.isProbationaryDriver = on
        try? context.save()
        // Publish so friends who marked you as driver use YOUR limit.
        if supabase.isSignedIn {
            Task { try? await supabase.updateProbation(on) }
        }
    }

    // Single source of truth for the driving limit. Replaces the earlier
    // standalone toggle so it no longer overlaps with the forecast selector.
    // Drives the Fahrbereit timer, the forecast default, and the "Fahrbereit"
    // labels of driver-marked friends.
    private var driveModeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "FAHR-GRENZWERT")
            HStack(spacing: 10) {
                SFLimitSegment(
                    title: "0,5 ‰",
                    subtitle: "Standard",
                    selected: !isProbation
                ) { setProbation(false) }
                SFLimitSegment(
                    title: "Probezeit",
                    subtitle: "0,0 ‰",
                    selected: isProbation
                ) { setProbation(true) }
            }
            Text("Gilt für deine Fahrbereit-Zeit, die Vorausschau und die Fahrbereit-Anzeige bei als Fahrer markierten Freunden.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
    }

    // MARK: Actions section

    private var actionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "AKTIONEN")
            VStack(spacing: 10) {
                SFActionButton(
                    icon: "car.circle.fill",
                    title: "Heimfahrt",
                    subtitle: "Uber oder Apple Maps öffnen",
                    color: Color.appAccent
                ) {
                    showRidePicker = true
                }
                if let p = profile,
                   let name = p.emergencyContactName,
                   let phone = p.emergencyContactPhone,
                   !phone.isEmpty {
                    SFActionButton(
                        icon: "phone.fill",
                        title: "Notfallkontakt anrufen",
                        subtitle: name,
                        color: Color.statusOrange
                    ) {
                        let cleaned = phone.filter { $0.isNumber || $0 == "+" }
                        if let url = URL(string: "tel:\(cleaned)") {
                            openURL(url)
                        }
                    }
                } else {
                    SFNoContactRow()
                }
            }
        }
    }
}

// MARK: - Top bar

private struct SFTopBar: View {
    var body: some View {
        HStack {
            Text("Sicherheit")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            Spacer()
        }
    }
}

// MARK: - BAC card

private struct SFBACCard: View {
    let bac: Double
    let status: BACStatus
    let skin: StatusSkin

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            HStack {
                Image(systemName: "drop.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(status.color)
                Text("Aktueller Pegel")
                    .font(.appCaptionBold)
                    .foregroundStyle(status.color)
                Spacer()
                StatusPill(status: status, skin: skin)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 10)

            Divider().background(Color.appBorder.opacity(0.5))

            VStack(alignment: .leading, spacing: 4) {
                Text(String(format: "%.2f", bac))
                    .font(.system(size: 56, weight: .light, design: .serif))
                    .foregroundStyle(status.color)
                    .monospacedDigit()
                Text("Promille (‰)")
                    .font(.appCaption)
                    .foregroundStyle(status.color.opacity(0.7))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            Divider().background(Color.appBorder.opacity(0.5))

            Text("Widmark-Schätzwert. Individuelle Faktoren können abweichen. Kein Atemtest. Im Zweifel nicht fahren.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
        }
        .background(status.backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(status.color.opacity(0.4), lineWidth: 1)
        )
    }
}

// MARK: - Timer row

private struct SFTimerRow: View {
    let label: String
    let icon: String
    let iconColor: Color
    // hoursUntilBAC returns 0 when the BAC is ALREADY at/below the target and
    // nil when it will NOT be reached within the 24h forecast window (still far
    // off, e.g. a very high current BAC). Those two must read differently: only
    // 0 means "ready"; nil must show it is more than a day away so the safety
    // screen never tells an intoxicated user they are already nüchtern.
    let hours: Double?
    let readyLabel: String

    private var isReady: Bool {
        guard let h = hours else { return false }
        return h <= 0
    }

    private var rowIconColor: Color {
        isReady ? iconColor : Color.appTextDim
    }

    private var displayText: String {
        guard let h = hours else { return "> 24 h" }
        guard h > 0 else { return readyLabel }
        let totalMin = Int(h * 60)
        let hr = totalMin / 60
        let min = totalMin % 60
        if hr == 0 { return "\(min) min" }
        if min == 0 { return "\(hr) h" }
        return "\(hr) h \(min) min"
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(rowIconColor)
                .frame(width: 30, height: 30)
                .background(rowIconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appText)

            Spacer()

            Text(displayText)
                .font(.appBodyBold)
                .foregroundStyle(isReady ? iconColor : Color.appText)
                .monospacedDigit()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }
}

// MARK: - Driving limit segment

private struct SFLimitSegment: View {
    let title: String
    let subtitle: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(title)
                    .font(.appBodyBold)
                    .foregroundStyle(selected ? Color.appBackground : Color.appText)
                Text(subtitle)
                    .font(.appMicro)
                    .foregroundStyle(selected ? Color.appBackground.opacity(0.8) : Color.appTextDim)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(selected ? Color.appAccent : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(selected ? Color.appAccent : Color.appBorder, lineWidth: selected ? 1 : 0.5)
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: selected)
    }
}

// MARK: - Action button

private struct SFActionButton: View {
    let icon: String
    let title: String
    let subtitle: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(color)
                    .frame(width: 44, height: 44)
                    .background(color.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text(subtitle)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - No contact row

private struct SFNoContactRow: View {
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "person.crop.circle.badge.questionmark")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.appTextDim)
                .frame(width: 44, height: 44)
                .background(Color.appTextDim.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 2) {
                Text("Kein Notfallkontakt")
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appTextDim)
                Text("In den Einstellungen hinterlegen")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextMuted)
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

// MARK: - Disclaimer

private struct SFDisclaimer: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "info.circle")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
                Text("Rechtlicher Hinweis")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appTextMuted)
            }
            Text("Diese App liefert Schätzwerte auf Basis des Widmark-Modells. Sie ersetzt keinen Atemtest und keine medizinische Beurteilung. Berechnungen sind Näherungswerte. Im Zweifel nicht fahren.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

// MARK: - Preview

#Preview {
    let controller = PersistenceController.preview
    return SafetyView()
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}
