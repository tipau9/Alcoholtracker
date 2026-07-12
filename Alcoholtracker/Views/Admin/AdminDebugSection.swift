import SwiftUI
import SwiftData

// MARK: - AdminDebugSection
//
// In-app test tooling, reachable only from the server-gated Admin tab. Lets an
// admin exercise social + BAC features without a second device or manual SQL:
// inject fake jam participants, simulate current BAC, seed history, and flip
// profile states. Every debug-created drink is stamped with DebugTools.marker
// so cleanup only ever removes test data, never real entries.
struct AdminDebugSection: View {
    @Environment(\.modelContext) private var context
    @Environment(JamService.self) private var jam
    @Environment(AchievementService.self) private var achievements
    @Query private var profiles: [UserProfile]
    private var profile: UserProfile? { profiles.first }

    @State private var status: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let status {
                Text(status)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.appAccent.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            jamGroup
            bacGroup
            historyGroup
            statesGroup
        }
    }

    // MARK: Jam

    private var jamGroup: some View {
        group("JAM-TEST") {
            if jam.currentJam == nil {
                note("Kein aktiver Jam. Starte oder tritt einem Jam bei, dann erscheinen die Fakes im Roster.")
            } else {
                Text("Fake-Teilnehmer im Jam: \(jam.debugFakeParticipantCount)")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }
            grid {
                actionButton("+1 Fake-Teilnehmer", "person.badge.plus", tint: .appAccent, disabled: jam.currentJam == nil) {
                    jam.addFakeParticipant()
                    show("Fake-Teilnehmer hinzugefügt (\(jam.debugFakeParticipantCount))")
                }
                actionButton("+3 Fake-Teilnehmer", "person.3.fill", tint: .appAccent, disabled: jam.currentJam == nil) {
                    for _ in 0..<3 { jam.addFakeParticipant() }
                    show("3 Fake-Teilnehmer hinzugefügt (\(jam.debugFakeParticipantCount))")
                }
                actionButton("Fakes entfernen", "person.badge.minus", tint: .statusRed, disabled: jam.currentJam == nil) {
                    jam.removeFakeParticipants()
                    show("Fake-Teilnehmer entfernt")
                }
            }
        }
    }

    // MARK: BAC

    private var bacGroup: some View {
        group("BAC SIMULIEREN") {
            note("Fügt echte Test-Drinks mit aktuellem Zeitstempel hinzu, sodass Home, Safety, Widgets und Projektion reagieren.")
            grid {
                actionButton("+1 Bier", "mug.fill", tint: .appAccent) {
                    DebugTools.addDrinks(1, spacingMinutes: 0, into: context)
                    show("1 Bier hinzugefügt")
                }
                actionButton("+1 Schnaps", "wineglass.fill", tint: .appAccent) {
                    DebugTools.addShot(into: context)
                    show("1 Schnaps hinzugefügt")
                }
                actionButton("3 Drinks (letzte 2h)", "clock.arrow.circlepath", tint: .appAccent) {
                    DebugTools.addDrinks(3, spacingMinutes: 40, into: context)
                    show("3 Drinks über die letzten 2 h verteilt")
                }
            }
        }
    }

    // MARK: History

    private var historyGroup: some View {
        group("VERLAUF") {
            note("Füllt History, Trends und Statistiken mit Test-Sessions der letzten 14 Tage.")
            grid {
                actionButton("14 Tage Verlauf säen", "chart.bar.doc.horizontal", tint: .appAccent) {
                    DebugTools.seedHistory(into: context)
                    show("Verlauf gesät")
                }
                actionButton("Alle Test-Drinks löschen", "trash.fill", tint: .statusRed) {
                    let removed = DebugTools.clearTestDrinks(from: context)
                    show("\(removed) Test-Drinks gelöscht")
                }
            }
        }
    }

    // MARK: States

    private var statesGroup: some View {
        group("STATES") {
            VStack(spacing: 10) {
                Toggle("Toleranz-Modus", isOn: bind(\.toleranceMode))
                Toggle("Konservativ rechnen", isOn: bind(\.conservativeSafety))
                Toggle("Fahranfänger (Probezeit)", isOn: bind(\.isProbationaryDriver))
                Toggle("SOS aktiv", isOn: Binding(
                    get: { jam.mySOSActive },
                    set: { jam.mySOSActive = $0 }
                ))
            }
            .font(.appBody)
            .foregroundStyle(Color.appText)
            .tint(Color.appAccent)
            .disabled(profile == nil)

            grid {
                actionButton("Alle Achievements freischalten", "trophy.fill", tint: .statusGreen) {
                    achievements.debugUnlockAll()
                    show("Alle Achievements freigeschaltet (\(achievements.unlockedCount))")
                }
                actionButton("Achievements zurücksetzen", "arrow.uturn.backward.circle", tint: .statusRed) {
                    achievements.debugReset()
                    show("Achievements zurückgesetzt")
                }
            }
        }
    }

    // MARK: Building blocks

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: title)
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }

    private func grid<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
            content()
        }
    }

    private func actionButton(
        _ title: String,
        _ icon: String,
        tint: Color,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
        }
        .buttonStyle(AdminActionButtonStyle(tint: tint))
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
    }

    private func note(_ text: String) -> some View {
        Text(text)
            .font(.appMicro)
            .foregroundStyle(Color.appTextMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func bind(_ keyPath: ReferenceWritableKeyPath<UserProfile, Bool>) -> Binding<Bool> {
        Binding(
            get: { profile?[keyPath: keyPath] ?? false },
            set: { newValue in
                profile?[keyPath: keyPath] = newValue
                try? context.save()
            }
        )
    }

    private func show(_ text: String) {
        status = text
    }
}

// MARK: - DebugTools
//
// Data-side helpers for the admin debug panel. All inserted drinks carry
// DebugTools.marker as their templateID so clearTestDrinks can remove exactly
// the test data and nothing the user actually logged.
enum DebugTools {
    static let marker = UUID(uuidString: "DEB00000-0000-0000-0000-0000000DEB01")!

    static func addDrinks(_ count: Int, spacingMinutes: Double, into context: ModelContext) {
        let now = Date()
        for i in 0..<count {
            context.insert(makeBeer(at: now.addingTimeInterval(-Double(i) * spacingMinutes * 60)))
        }
        try? context.save()
    }

    static func addShot(into context: ModelContext) {
        context.insert(makeShot(at: Date()))
        try? context.save()
    }

    static func seedHistory(into context: ModelContext) {
        let cal = Calendar.current
        for dayOffset in 1...14 {
            // Leave roughly a third of the days dry so trends have real gaps.
            if Int.random(in: 0..<3) == 0 { continue }
            guard let day = cal.date(byAdding: .day, value: -dayOffset, to: Date()) else { continue }
            var start = cal.date(bySettingHour: 19, minute: Int.random(in: 0..<50), second: 0, of: day) ?? day
            for _ in 0..<Int.random(in: 1...5) {
                context.insert(Bool.random() ? makeBeer(at: start) : makeShot(at: start))
                start = start.addingTimeInterval(Double(Int.random(in: 20...45)) * 60)
            }
        }
        try? context.save()
    }

    @discardableResult
    static func clearTestDrinks(from context: ModelContext) -> Int {
        let marker: UUID? = self.marker
        let descriptor = FetchDescriptor<Drink>(predicate: #Predicate { $0.templateID == marker })
        guard let drinks = try? context.fetch(descriptor) else { return 0 }
        drinks.forEach { context.delete($0) }
        try? context.save()
        return drinks.count
    }

    private static func makeBeer(at timestamp: Date) -> Drink {
        Drink(name: "Test-Bier", volume: 500, abv: 5.0, calories: 210,
              iconName: "mug.fill", category: .beer, timestamp: timestamp, templateID: marker)
    }

    private static func makeShot(at timestamp: Date) -> Drink {
        Drink(name: "Test-Schnaps", volume: 40, abv: 40, calories: 90,
              iconName: "wineglass.fill", category: .shot, timestamp: timestamp, templateID: marker)
    }
}
