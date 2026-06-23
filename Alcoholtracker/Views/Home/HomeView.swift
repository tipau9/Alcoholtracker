import SwiftUI
import SwiftData
import Charts
import UIKit

// MARK: - HomeView

struct HomeView: View {

    @Query private var profiles: [UserProfile]
    @Query(sort: [SortDescriptor(\DrinkTemplate.usageCount, order: .reverse)]) private var allTemplates: [DrinkTemplate]
    @Query private var crewMembers: [CrewMember]
    // Home only pages in the last few days of drinks (for the mood prompt and as
    // the change trigger). The full history is scanned transiently inside the
    // achievement task, not held in view state and re-diffed on every BAC tick.
    @Query private var recentDrinks: [Drink]
    @Query private var allPhotos: [PhotoMemory]
    @Query private var allNotes: [DayNote]
    @Environment(\.modelContext) private var context

    init() {
        let cutoff = Calendar.current.date(byAdding: .day, value: -3, to: Date()) ?? .distantPast
        _recentDrinks = Query(filter: #Predicate { $0.timestamp >= cutoff }, sort: \.timestamp)
    }
    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementService.self) private var achievements
    @Environment(HealthKitService.self) private var health
    @Environment(JamService.self) private var jamService
    @Environment(LocationService.self) private var locationService
    @State private var session = SessionViewModel()
    @State private var showAddDrink = false
    @State private var showResetAlert = false
    @State private var showEditSheet = false
    @State private var amountTemplate: DrinkTemplate? = nil
    @State private var editingDrink: Drink? = nil
    @State private var showUnlockToast = false
    @State private var showMedWarning = false
    @State private var medWarningShownThisSession = false
    @State private var showMoodPrompt = false
    @State private var undoDismissTask: Task<Void, Never>? = nil
    // Lets the user drop back to the normal home for this session even while the
    // BAC is still high; re-arms once they sober back under the threshold.
    @State private var drunkModeDismissed = false

    private var profile: UserProfile? { profiles.first }

    // Drunk-Mode: auto-simplify the home above the "careful" threshold.
    private var isDrunkMode: Bool {
        guard profile?.drunkModeAuto == true, !drunkModeDismissed else { return false }
        return session.currentBAC >= (profile?.carefulThreshold ?? 0.8)
    }

    // Composite key of BAC-relevant fields — configure() only re-runs when one of these changes.
    private var bacConfigureKey: String {
        guard let p = profile else { return "" }
        return "\(p.weight)-\(p.height)-\(p.genderRaw)-\(p.eliminationRate)-\(p.toleranceMode)-\(p.birthDate.timeIntervalSinceReferenceDate)"
    }

    private var alertCrew: [CrewMember] {
        crewMembers.filter { !$0.isHome && $0.careScore >= 40 }
    }

    private var topFavourites: [DrinkTemplate] { Array(allTemplates.prefix(3)) }

    // MARK: Morning mood prompt

    private var yesterdayLogical: Date {
        let cal = Calendar.current
        let today = cal.logicalDay(for: Date())
        return cal.date(byAdding: .day, value: -1, to: today) ?? today
    }

    private var moodPromptDismissKey: String {
        "moodPromptDismissed_\(Int(yesterdayLogical.timeIntervalSinceReferenceDate))"
    }

    private func evaluateMoodPrompt() {
        let cal = Calendar.current
        let day = yesterdayLogical
        guard !UserDefaults.standard.bool(forKey: moodPromptDismissKey) else { return }
        let hadAlcohol = recentDrinks.contains {
            $0.abv > 0.01 && cal.logicalDay(for: $0.timestamp) == day
        }
        guard hadAlcohol else { return }
        let existingMood = allNotes.first { cal.isDate($0.dayStart, inSameDayAs: day) }?.mood ?? .neutral
        guard existingMood == .neutral else { return }
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            showMoodPrompt = true
        }
    }

    private func saveMoodForYesterday(_ mood: DayMood) {
        let cal = Calendar.current
        let day = yesterdayLogical
        if let note = allNotes.first(where: { cal.isDate($0.dayStart, inSameDayAs: day) }) {
            note.mood = mood
        } else {
            context.insert(DayNote(dayStart: day, text: "", mood: mood))
        }
        try? context.save()
        UserDefaults.standard.set(true, forKey: moodPromptDismissKey)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.easeInOut(duration: 0.25)) { showMoodPrompt = false }
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            if isDrunkMode {
                DrunkHomeView(
                    session: session,
                    skin: profile?.statusSkin ?? .standard,
                    showAddDrink: $showAddDrink,
                    onExit: { withAnimation(.easeInOut(duration: 0.25)) { drunkModeDismissed = true } }
                )
            } else if (profile?.homeStyle ?? .detailed) == .minimal {
                MinimalHomeView(session: session, showAddDrink: $showAddDrink, skin: profile?.statusSkin ?? .standard)
            } else {
                DetailedHomeView(
                    session: session,
                    alertCrew: alertCrew,
                    favourites: topFavourites,
                    profile: profile,
                    showAddDrink: $showAddDrink,
                    showResetAlert: $showResetAlert,
                    showEditSheet: $showEditSheet,
                    onLongPressFavourite: { amountTemplate = $0 },
                    onEditDrink: { editingDrink = $0 }
                )
            }

            if showMoodPrompt && !showMedWarning {
                VStack {
                    MorningMoodPrompt(
                        onSelect: { saveMoodForYesterday($0) },
                        onDismiss: {
                            UserDefaults.standard.set(true, forKey: moodPromptDismissKey)
                            withAnimation(.easeInOut(duration: 0.25)) { showMoodPrompt = false }
                        }
                    )
                    .padding(.top, 8)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(35)
            }

            if showMedWarning, let meds = profile?.activeMedications, !meds.isEmpty {
                VStack {
                    MedicationWarningBanner(medications: meds) {
                        withAnimation { showMedWarning = false }
                    }
                    .padding(.top, 8)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(40)
            }

            // Sip counter overlay — replaces bottom area while counting
            if session.activeSipDrink != nil {
                VStack {
                    Spacer()
                    SipCounterView(session: session, profile: profile)
                        .padding(.bottom, 20)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: session.activeSipDrink != nil)
                .zIndex(45)
            }

            if let undoLabel = session.undoLabel, session.activeSipDrink == nil, !showUnlockToast {
                VStack {
                    Spacer()
                    UndoSnackbar(label: undoLabel) {
                        undoDismissTask?.cancel()
                        session.performUndo()
                    }
                    .padding(.bottom, 24)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(47)
            }

            if showUnlockToast, let first = achievements.newlyUnlocked.first {
                VStack {
                    Spacer()
                    AchievementUnlockToast(
                        achievement: first,
                        count: achievements.newlyUnlocked.count,
                        onDismiss: {
                            withAnimation(.easeInOut(duration: 0.25)) { showUnlockToast = false }
                            achievements.acknowledgeUnlocks()
                        }
                    )
                    .padding(.bottom, 24)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(50)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: showUnlockToast)
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: session.undoVersion)
        .task {
            if let p = profile {
                session.healthKit = health
                session.configure(profile: p, context: context)
            }
            evaluateMoodPrompt()
        }
        .onChange(of: session.undoVersion) { _, _ in
            // Restart the auto-hide window whenever a new undoable action appears.
            undoDismissTask?.cancel()
            undoDismissTask = Task {
                try? await Task.sleep(for: .seconds(6))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.25)) { session.clearUndo() }
            }
        }
        .onChange(of: bacConfigureKey) { _, _ in
            if let p = profile {
                session.healthKit = health
                session.configure(profile: p, context: context)
            }
        }
        .alert("Sitzung zurücksetzen?", isPresented: $showResetAlert) {
            Button("Zurücksetzen", role: .destructive) { session.resetSession() }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Alle heutigen Drinks werden gelöscht.")
        }
        .sheet(isPresented: $showAddDrink) {
            QuickAddSheet(
                profile: profile,
                lastBottleLevels: session.lastBottleLevels,
                onAdd: { drink in
                    session.addDrink(drink)
                    pingCityTrend(drink: drink)
                },
                onBottleDrink: { template, size, start, current in
                    session.addBottleDrink(template: template, bottleSize: size,
                                           startLevel: start, currentLevel: current)
                    pingCityTrend(name: template.name, category: template.category.rawValue)
                },
                onStartSipCounter: { template in
                    session.startSipCounter(for: template)
                }
            )
        }
        .sheet(isPresented: $showEditSheet) {
            if let p = profile {
                HomeEditSheet(profile: p)
            }
        }
        .sheet(item: $amountTemplate) { template in
            AmountInputSheet(template: template, profile: profile) { drink in
                session.addDrink(drink)
                pingCityTrend(drink: drink)
            }
        }
        .sheet(item: $editingDrink) { drink in
            DrinkEditSheet(
                drink: drink,
                profile: profile,
                onSave: { vol, ts in session.updateDrink(drink, volume: vol, timestamp: ts) },
                onDelete: { session.removeDrink(drink) }
            )
        }
        .onChange(of: session.currentBAC) { _, bac in
            jamService.myCurrentBAC = bac
            // Re-arm drunk-mode once back under the threshold.
            if bac < (profile?.carefulThreshold ?? 0.8) { drunkModeDismissed = false }
            guard supabase.isSignedIn else { return }
            Task { try? await supabase.publishBAC(bac) }
        }
        .onChange(of: session.drinks.count) { old, new in
            guard new > old, !medWarningShownThisSession,
                  let meds = profile?.activeMedications, !meds.isEmpty else { return }
            medWarningShownThisSession = true
            withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                showMedWarning = true
            }
            Task {
                try? await Task.sleep(for: .seconds(6))
                withAnimation { showMedWarning = false }
            }
        }
        .task(id: recentDrinks.map(\.id)) {
            // Keep the session in sync with edits made outside it (history tab,
            // widget quick-add) so home BAC never shows stale data.
            session.loadTodaysDrinks()
            // Achievements need the whole history (lifetime counts, streaks);
            // fetch it on demand here instead of holding it in view state.
            let allDrinks = (try? context.fetch(FetchDescriptor<Drink>())) ?? []
            await achievements.evaluate(
                drinks: allDrinks, templates: allTemplates,
                crew: crewMembers, photos: allPhotos,
                profile: profile
            )
        }
        .onChange(of: achievements.newlyUnlocked.count) { _, count in
            guard count > 0 else { return }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            if !showUnlockToast {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                    showUnlockToast = true
                }
                // Auto-hide after 3 s — but do NOT acknowledgeUnlocks here;
                // onDismiss (user tap) handles it so concurrent unlocks are not lost.
                Task {
                    try? await Task.sleep(for: .seconds(3))
                    withAnimation(.easeInOut(duration: 0.25)) { showUnlockToast = false }
                }
            }
        }
    }

    private func pingCityTrend(drink: Drink) {
        pingCityTrend(name: drink.name, category: drink.categoryRaw)
    }

    private func pingCityTrend(name: String, category: String) {
        guard let city = locationService.currentCity else { return }
        Task { await supabase.pingCityDrink(city: city, drinkName: name, category: category) }
    }
}

// MARK: - Detailed Mode

private struct DetailedHomeView: View {
    let session: SessionViewModel
    let alertCrew: [CrewMember]
    let favourites: [DrinkTemplate]
    let profile: UserProfile?
    @Binding var showAddDrink: Bool
    @Binding var showResetAlert: Bool
    @Binding var showEditSheet: Bool
    let onLongPressFavourite: (DrinkTemplate) -> Void
    let onEditDrink: (Drink) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(LocationService.self) private var locationService
    @Environment(WeatherProvider.self) private var weather

    // FIX FEATURE8: full-screen chart on tap
    @State private var showFullChart = false
    // 0 = top (hero full size), 1 = scrolled into the list (hero de-emphasised),
    // so attention shifts to the content below as you scroll.
    @State private var heroCollapse: CGFloat = 0

    private var heroScale: CGFloat { 1 - 0.12 * heroCollapse }
    private var heroOpacity: Double { 1 - 0.4 * Double(heroCollapse) }

    private var activeWidgets: [WidgetType] { profile?.activeWidgets ?? WidgetType.allCases }
    private var skin: StatusSkin { profile?.statusSkin ?? .standard }

    // Extra sweat loss (ml) from a warm night, fed into the water recommendation.
    // Uses the session span (first drink to now, capped at 6 h) as the time spent
    // out. Zero unless WeatherKit returned a warm temperature.
    private var weatherSweatML: Double {
        guard weather.isWarm, let temp = weather.currentTempC,
              let first = session.drinks.map(\.timestamp).min() else { return 0 }
        let hours = min(6, max(0, Date().timeIntervalSince(first) / 3600))
        return HydrationCalculator.heatSweatLossMl(tempC: temp, hours: hours)
    }

    // Memoised: computing this in body ran a full BAC integration on every scroll
    // frame, because body re-evaluates as heroCollapse changes. It only actually
    // changes when the BAC does, so it is refreshed from .task / .onChange instead.
    @State private var bacTrend: BACTrend = .stable

    private func computeBACTrend() -> BACTrend {
        guard session.currentBAC > 0.01, let p = profile else { return .stable }
        let fiveMinutesAgo = BACCalculator.currentBAC(
            drinks: session.drinks,
            profile: p,
            at: Date().addingTimeInterval(-300),
            stomachStatus: session.stomachStatus
        )
        if session.currentBAC > fiveMinutesAgo + 0.005 { return .rising }
        if session.currentBAC < fiveMinutesAgo - 0.005 { return .falling }
        return .stable
    }

    var body: some View {
        ZStack(alignment: Alignment.bottomTrailing) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // Probe: tracks how far the content has scrolled so the hero
                    // can shrink/fade as the list comes into focus.
                    GeometryReader { geo in
                        Color.clear.preference(
                            key: QAScrollOffsetPreferenceKey.self,
                            value: geo.frame(in: .named("homeScroll")).minY
                        )
                    }
                    .frame(height: 0)

                    HomeTopBar(
                        profile: profile,
                        onReset: { showResetAlert = true },
                        onEdit: { showEditSheet = true }
                    )
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    if !alertCrew.isEmpty {
                        CrewAlertBanner(members: alertCrew)
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                    }

                    BACDisplaySection(
                        bac: session.currentBAC,
                        status: session.bacStatus,
                        trend: bacTrend,
                        skin: skin
                    )
                    .scaleEffect(heroScale, anchor: .top)
                    .opacity(heroOpacity)
                    .padding(.top, 28)

                    if let pacing = session.pacingWarning {
                        PacingHintBanner(message: pacing)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    if let limit = profile?.weeklyDrinkLimit, limit > 0 {
                        WeeklyLimitCard(used: session.currentWeekDrinkCount, limit: limit)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                    }

                    if session.drinks.isEmpty {
                        EmptyDrinkHint(onAdd: { showAddDrink = true })
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                    }

                    if activeWidgets.contains(.stomachStatus) {
                        StomachStatusPicker(session: session)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                    }

                    if !session.drinks.isEmpty {
                        VomitActionCard(session: session)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                    }

                    if session.bacCurve.count > 1 && activeWidgets.contains(.bacCurve) {
                        BACCurveChartView(
                            session: session,
                            warningThreshold: profile?.warningThreshold ?? 0.5
                        )
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                        // FIX FEATURE8: tap opens full-screen interactive chart
                        .onTapGesture { showFullChart = true }
                        .overlay(alignment: .topTrailing) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(Color.appTextDim)
                                .padding(18)
                        }
                        .fullScreenCover(isPresented: $showFullChart) {
                            FullScreenBACChart(session: session, profile: profile)
                        }
                    }

                    let gridTypes: [WidgetType] = [.timeToLimit, .water, .calories, .drinkCount]
                    if gridTypes.contains(where: { activeWidgets.contains($0) }) {
                        HomeWidgetGrid(session: session, active: activeWidgets, extraSweatML: weatherSweatML)
                            .padding(.horizontal, 16)
                            .padding(.top, 20)
                    }

                    if !favourites.isEmpty && activeWidgets.contains(.favStrip) {
                        FavouritesStrip(
                            templates: favourites,
                            onAdd: { template in
                                // Honor the user's remembered serving size for this drink.
                                let drink = Drink.from(
                                    template: template,
                                    volume: ServingSizeMemory.volume(for: template.id))
                                session.addDrink(drink)
                                pingCityTrend(drink: drink)
                            },
                            onLongPress: onLongPressFavourite
                        )
                        .padding(.top, 20)
                    }

                    if !session.drinks.isEmpty && activeWidgets.contains(.drinkHistory) {
                        DrinkHistorySection(session: session, onEdit: onEditDrink)
                            .padding(.horizontal, 16)
                            .padding(.top, 20)
                    }

                    Text("Widmark-Schätzwert. Müdigkeit, Medikamente und individuelle Faktoren können stark abweichen. Kein Ersatz für einen Atemtest. Im Zweifel nicht fahren.")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                        .padding(.top, 24)
                        .padding(.bottom, 110)
                }
            }
            .coordinateSpace(name: "homeScroll")
            .onPreferenceChange(QAScrollOffsetPreferenceKey.self) { y in
                // y starts near 0 and goes negative as the content scrolls up.
                let t = min(max(-y / 150, 0), 1)
                withAnimation(.easeOut(duration: 0.12)) { heroCollapse = t }
            }

            FABButton(title: "Drink hinzufügen") { showAddDrink = true }
                .padding(.trailing, 24)
                .padding(.bottom, 32)
        }
        // Refresh the memoised trend only when the BAC actually changes, not on
        // every scroll frame (this body re-evaluates as heroCollapse animates).
        .task { bacTrend = computeBACTrend() }
        .onChange(of: session.currentBAC) { _, _ in bacTrend = computeBACTrend() }
    }

    private func pingCityTrend(drink: Drink) {
        guard let city = locationService.currentCity else { return }
        Task { await supabase.pingCityDrink(city: city, drinkName: drink.name, category: drink.categoryRaw) }
    }
}

// MARK: - Minimal Mode

private struct MinimalHomeView: View {
    let session: SessionViewModel
    @Binding var showAddDrink: Bool
    let skin: StatusSkin

    @Query private var profiles: [UserProfile]
    @Environment(\.modelContext) private var context
    private var profile: UserProfile? { profiles.first }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 16) {
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(session.currentBAC.bacFormatted)
                            .font(.system(size: 130, weight: .ultraLight, design: .serif))
                            .foregroundStyle(Color.appText)
                            .contentTransition(.numericText(countsDown: false))
                            .animation(.easeInOut(duration: 0.4), value: session.currentBAC)
                            .monospacedDigit()
                            // Long-press to exit minimal mode without needing Settings
                            .contextMenu {
                                Button {
                                    profile?.homeStyle = .detailed
                                    try? context.save()
                                } label: {
                                    Label("Detaillierter Modus", systemImage: "slider.horizontal.3")
                                }
                                Button {
                                    showAddDrink = true
                                } label: {
                                    Label("Drink hinzufügen", systemImage: "plus")
                                }
                            }

                        Text("‰")
                            .font(.system(size: 36, weight: .ultraLight, design: .serif))
                            .foregroundStyle(Color.appTextDim)
                    }

                    StatusPill(status: session.bacStatus, skin: skin)
                }

                Spacer()

                PrimaryButton(title: "Drink hinzufügen", icon: "plus") {
                    showAddDrink = true
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 48)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Small exit button in corner as always-visible affordance
            Button {
                profile?.homeStyle = .detailed
                try? context.save()
            } label: {
                Image(systemName: "rectangle.expand.diagonal")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
                    .frame(width: 32, height: 32)
                    .background(Color.appCard.opacity(0.8))
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .padding(.trailing, 20)
            .padding(.bottom, 110)
        }
    }
}

// MARK: - Drunk Mode Home

// Stripped-down, oversized layout shown automatically above the careful
// threshold (opt-in via Settings). Big numbers, one giant target, nothing else.
private struct DrunkHomeView: View {
    let session: SessionViewModel
    let skin: StatusSkin
    @Binding var showAddDrink: Bool
    let onExit: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: onExit) {
                    HStack(spacing: 6) {
                        Image(systemName: "rectangle.expand.diagonal")
                        Text("Normale Ansicht")
                    }
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appTextMuted)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.appCard)
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(Color.appBorder, lineWidth: 0.5))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)

            Spacer()

            VStack(spacing: 20) {
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text(session.currentBAC.bacFormatted)
                        .font(.system(size: 150, weight: .ultraLight, design: .serif))
                        .foregroundStyle(session.bacStatus.color)
                        .contentTransition(.numericText(countsDown: false))
                        .animation(.easeInOut(duration: 0.4), value: session.currentBAC)
                        .monospacedDigit()
                    Text("‰")
                        .font(.system(size: 40, weight: .ultraLight, design: .serif))
                        .foregroundStyle(Color.appTextDim)
                }
                StatusPill(status: session.bacStatus, skin: skin)
            }

            Spacer()

            Button { showAddDrink = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "plus")
                    Text("Drink hinzufügen")
                }
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Color.appBackground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .background(Color.appAccent)
                .clipShape(RoundedRectangle(cornerRadius: 24))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            .padding(.bottom, 48)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Top Bar

private struct HomeTopBar: View {
    let profile: UserProfile?
    let onReset: () -> Void
    let onEdit: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            SectionLabel(text: "AKTUELL")
            Spacer()

            Button(action: onReset) {
                Image(systemName: "arrow.counterclockwise")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
                    .frame(width: 34, height: 34)
                    .background(Color.appCard)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)

            Button(action: onEdit) {
                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
                    .frame(width: 34, height: 34)
                    .background(Color.appCard)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Freunde Alert Banner

private struct CrewAlertBanner: View {
    let members: [CrewMember]

    private var label: String {
        if members.count == 1 {
            return "\(members[0].name) braucht vielleicht Aufmerksamkeit."
        }
        return "\(members.count) Personen brauchen vielleicht Aufmerksamkeit."
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "person.crop.circle.badge.exclamationmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.statusOrange)

            Text(label)
                .font(.appCaption)
                .foregroundStyle(Color.appText)

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.appTextDim)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.statusOrange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.statusOrange.opacity(0.3), lineWidth: 0.5)
        )
    }
}

// MARK: - BAC Trend

private enum BACTrend: Equatable {
    case rising, stable, falling

    var symbol: String {
        switch self {
        case .rising:  return "arrow.up.forward"
        case .stable:  return "minus"
        case .falling: return "arrow.down.forward"
        }
    }

    var tintColor: Color {
        switch self {
        case .rising:  return Color.statusOrange
        case .stable:  return Color.appTextDim
        case .falling: return Color.statusGreen
        }
    }
}

// MARK: - BAC Display Section

private struct BACDisplaySection: View {
    let bac: Double
    let status: BACStatus
    let trend: BACTrend
    let skin: StatusSkin

    var body: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [status.color.opacity(0.10), Color.clear],
                        center: .center,
                            startRadius: 60,
                            endRadius: 140
                        )
                    )
                    .frame(width: 280, height: 280)
                    .animation(.easeInOut(duration: 0.6), value: status)

                Circle()
                    .strokeBorder(status.color.opacity(0.20), lineWidth: 1)
                    .frame(width: 220, height: 220)
                    .animation(.easeInOut(duration: 0.6), value: status)

                VStack(spacing: 0) {
                    HStack(alignment: .lastTextBaseline, spacing: 6) {
                        Text(bac.bacFormatted)
                            .font(.appDisplay)
                            .foregroundStyle(Color.appText)
                            .contentTransition(.numericText(countsDown: false))
                            .animation(.easeInOut(duration: 0.4), value: bac)
                            .monospacedDigit()

                        if bac > 0.01 {
                            Image(systemName: trend.symbol)
                                .font(.system(size: 16, weight: .medium))
                                .foregroundStyle(trend.tintColor)
                                .animation(.easeInOut(duration: 0.3), value: trend)
                        }
                    }

                    Text("‰")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                        .offset(y: -4)
                }
            }

            StatusPill(status: status, skin: skin)
        }
    }
}

// MARK: - BAC Curve Chart

private struct BACCurveChartView: View {
    let session: SessionViewModel
    let warningThreshold: Double

    @State private var showFullDay = false

    private var displayPoints: [BACCalculator.BACPoint] {
        showFullDay ? session.bacCurve24h : session.bacCurve
    }

    // Extracted so the ForEach resolves unambiguously as ChartContent. Inline in
    // a Chart {} the type-checker can otherwise (non-deterministically) try to
    // treat ForEach as MapContent and fail with a MapContentBuilder error.
    @ChartContentBuilder
    private func curveMarks(_ points: [BACCalculator.BACPoint]) -> some ChartContent {
        ForEach(points) { p in
            pointMarks(p)
        }
    }

    // The per-point marks live in their own @ChartContentBuilder function so the
    // ForEach closure is a single, explicitly-ChartContent expression. A bare
    // multi-mark ForEach body lets the type-checker pick MapContentBuilder.
    @ChartContentBuilder
    private func pointMarks(_ p: BACCalculator.BACPoint) -> some ChartContent {
        AreaMark(
            x: .value("Zeit", p.date),
            y: .value("BAC", p.bac)
        )
        .foregroundStyle(
            LinearGradient(
                colors: [Color.appAccent.opacity(0.22), Color.clear],
                startPoint: UnitPoint.top,
                endPoint: UnitPoint.bottom
            )
        )
        .interpolationMethod(.catmullRom)

        LineMark(
            x: .value("Zeit", p.date),
            y: .value("BAC", p.bac)
        )
        .foregroundStyle(Color.appAccent)
        .lineStyle(StrokeStyle(lineWidth: 1.5))
        .interpolationMethod(.catmullRom)
    }

    var body: some View {
        // Integrate the curve once per render: yMax and the chart marks both need
        // it, and session.bacCurve(24h) is an uncached full integration per access.
        let points = displayPoints
        let yMax = max(1.0, (points.map(\.bac).max() ?? 0) + 0.2)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                SectionLabel(text: "VERLAUF")
                Spacer()
                HStack(spacing: 0) {
                    ChartTimeButton(label: "8h", isSelected: !showFullDay) { showFullDay = false }
                    ChartTimeButton(label: "24h", isSelected: showFullDay)  { showFullDay = true  }
                }
                .background(Color.appBackground)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )
            }

            Chart {
                curveMarks(points)

                RuleMark(y: .value("Grenzwert", warningThreshold))
                    .foregroundStyle(Color.statusOrange.opacity(0.55))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .annotation(position: .top, alignment: .trailing) {
                        Text(String(format: "%.1f‰", warningThreshold))
                            .font(.appMicro)
                            .foregroundStyle(Color.statusOrange)
                    }

                RuleMark(x: .value("Jetzt", Date()))
                    .foregroundStyle(Color.appBorder.opacity(0.8))
                    .lineStyle(StrokeStyle(lineWidth: 1))
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: .hour, count: showFullDay ? 4 : 2)) { _ in
                    AxisValueLabel(format: .dateTime.hour(.twoDigits(amPM: .omitted)))
                        .foregroundStyle(Color.appTextDim)
                    AxisGridLine()
                        .foregroundStyle(Color.appBorder.opacity(0.4))
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading, values: .stride(by: 0.5)) { val in
                    if let v = val.as(Double.self), v >= 0 {
                        AxisValueLabel {
                            Text(String(format: "%.1f", v))
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextDim)
                        }
                        AxisGridLine()
                            .foregroundStyle(Color.appBorder.opacity(0.4))
                    }
                }
            }
            .chartYScale(domain: 0...yMax)
            .frame(height: 140)
            .animation(.easeInOut(duration: 0.3), value: showFullDay)
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

private struct ChartTimeButton: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.appMicro)
                .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(isSelected ? Color.appAccent : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 7))
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: isSelected)
    }
}

// MARK: - Stomach Status Picker

private struct StomachStatusPicker: View {
    let session: SessionViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "MAGEN")
            HStack(spacing: 8) {
                ForEach(StomachStatus.allCases, id: \.self) { status in
                    StomachChip(
                        status: status,
                        isSelected: session.stomachStatus == status,
                        onSelect: { session.stomachStatus = status }
                    )
                }
            }
        }
    }
}

private struct StomachChip: View {
    let status: StomachStatus
    let isSelected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 6) {
                Image(systemName: status.symbolName)
                    .font(.system(size: 11, weight: .medium))
                Text(status.localizedName)
                    .font(.appCaption)
            }
            .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .background(isSelected ? Color.appAccent : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: 0.5
                    )
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: isSelected)
    }
}

// MARK: - Übergeben (tactical vomit) action

private struct VomitActionCard: View {
    let session: SessionViewModel
    @State private var showConfirm = false

    private var count: Int { session.vomitEvents.count }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "ÜBERGEBEN")
            HStack(spacing: 12) {
                Image(systemName: "arrow.up.heart.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.statusOrange)
                    .frame(width: 30, height: 30)
                    .background(Color.statusOrange.opacity(0.13))
                    .clipShape(RoundedRectangle(cornerRadius: 9))

                VStack(alignment: .leading, spacing: 1) {
                    Text(count == 0 ? "Übergeben loggen" : "\(count)x geloggt")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Text("Entfernt noch nicht aufgenommenen Alkohol aus dem Magen")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                        .lineLimit(2)
                }

                Spacer()

                if count > 0 {
                    Button {
                        session.removeLastVomit()
                    } label: {
                        Image(systemName: "arrow.uturn.backward.circle.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(Color.appTextDim)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Letztes Übergeben rückgängig")
                }

                Button {
                    showConfirm = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Color.statusOrange)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Übergeben loggen")
            }
            .padding(14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
        .confirmationDialog("Übergeben loggen?", isPresented: $showConfirm, titleVisibility: .visible) {
            Button("Übergeben loggen") { session.logVomit() }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Nur noch nicht ins Blut aufgenommener Alkohol wird entfernt. Der aktuelle Promillewert ändert sich dadurch nicht sprunghaft.")
        }
    }
}

// MARK: - Widget Grid

private struct HomeWidgetGrid: View {
    let session: SessionViewModel
    let active: [WidgetType]
    // Heat-driven sweat loss (ml) added to the water deficit (Wetter-Korrelation).
    var extraSweatML: Double = 0

    // Label reflects the user's actual legal limit (0,0 ‰ in der Probezeit).
    private var limitLabel: String {
        let s = String(format: "%.1f", session.drivingLimit).replacingOccurrences(of: ".", with: ",")
        return "Bis \(s) ‰"
    }

    private var untilLimitText: String {
        let limit = session.drivingLimit
        guard session.currentBAC > limit + 0.005 else {
            return session.currentBAC > 0.01 ? "Fahrbereit" : "Nüchtern"
        }
        guard let h = session.hoursUntil(limit) else { return "> 24 h" }
        return h <= 0 ? "Fahrbereit" : h.asHoursMinutes
    }

    private var waterText: String {
        // Exact compensation: grosses the deficit up for ADH pass-through, credits
        // water already logged today, and adds warm-weather sweat loss, so the tile
        // reflects what is still needed rather than the raw shortfall.
        let loggedML = Double(WaterLog.glassesToday()) * WaterLog.glassML
        let glasses = HydrationCalculator.compensationGlasses(
            for: session.drinks, extraNetML: loggedML - extraSweatML)
        if glasses == 0 { return "Ausreichend" }
        return "\(glasses) \(glasses == 1 ? "Glas" : "Gläser")"
    }

    private var isOverLimit: Bool { session.currentBAC > session.drivingLimit + 0.005 }

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
            spacing: 12
        ) {
            if active.contains(.timeToLimit) {
                InfoWidget(
                    icon: "car.fill",
                    label: limitLabel,
                    value: untilLimitText,
                    iconColor: isOverLimit ? Color.statusOrange : Color.appAccent,
                    isHighlighted: isOverLimit
                )
            }

            if active.contains(.water) {
                InfoWidget(
                    icon: "drop.fill",
                    label: "Wasser",
                    value: waterText,
                    iconColor: Color.appAccent
                )
            }

            if active.contains(.calories) {
                InfoWidget(
                    icon: "flame.fill",
                    label: "Kalorien",
                    value: session.totalCalories == 0 ? "0 kcal" : "\(session.totalCalories) kcal",
                    iconColor: Color.statusOrange
                )
            }

            if active.contains(.drinkCount) {
                InfoWidget(
                    icon: "figure.walk",
                    label: "Drinks heute",
                    value: "\(session.drinks.count)",
                    iconColor: Color.statusGreen
                )
            }

            if active.contains(.hangover) {
                let level = session.hangoverForecast
                InfoWidget(
                    icon: level.symbolName,
                    label: "Kater-Prognose",
                    value: level.label,
                    iconColor: level.color
                )
            }
        }
    }
}

// MARK: - Favourites Strip

private struct FavouritesStrip: View {
    let templates: [DrinkTemplate]
    let onAdd: (DrinkTemplate) -> Void
    let onLongPress: (DrinkTemplate) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "SCHNELL HINZUFÜGEN")
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(templates) { template in
                        FavChip(
                            template: template,
                            onTap: { onAdd(template) },
                            onLongPress: { onLongPress(template) }
                        )
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

private struct FavChip: View {
    let template: DrinkTemplate
    let onTap: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            DrinkIconView(template: template, size: 12)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.appAccent)
            Text(template.name)
                .font(.appCaption)
                .foregroundStyle(Color.appText)
                .lineLimit(1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(Color.appCard)
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(Color.appBorder, lineWidth: 0.5))
        .contentShape(Capsule())
        .onTapGesture { onTap() }
        .onLongPressGesture(minimumDuration: 0.5, perform: { onLongPress() })
    }
}

// MARK: - Drink History Section

private struct DrinkHistorySection: View {
    let session: SessionViewModel
    let onEdit: (Drink) -> Void

    private var recent: [Drink] {
        Array(session.drinks.suffix(4).reversed())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionLabel(text: "HEUTE")
                Spacer()
                let count = session.drinks.count
                Text("\(count) Drink\(count == 1 ? "" : "s")")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }

            ForEach(recent) { drink in
                DrinkRowView(
                    drink: drink,
                    onDelete: { session.removeDrink(drink) },
                    onDuplicate: { session.duplicateDrink(drink) },
                    onEdit: { onEdit(drink) }
                )
            }
        }
    }
}

private struct DrinkRowView: View {
    let drink: Drink
    let onDelete: () -> Void
    let onDuplicate: () -> Void
    let onEdit: () -> Void

    // Custom swipe: the home list is a ScrollView, not a List, so the SwiftUI
    // .swipeActions modifier is a no-op here. Right = duplicate, left = delete;
    // the action fires on release past the threshold (no persistent open state).
    @State private var offset: CGFloat = 0
    private let threshold: CGFloat = 72

    private var card: some View {
        HStack(spacing: 12) {
            DrinkIconView(drink: drink, size: 15)
                .font(.system(size: 15))
                .foregroundStyle(Color.appAccent)
                .frame(width: 36, height: 36)
                .background(Color.appAccent.opacity(0.10))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(drink.name)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)

                Text("\(Int(drink.volume)) ml · \(String(format: "%.1f", drink.abv)) % · \(drink.calories) kcal")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            Text(drink.timestamp.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute()))
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
        .padding(12)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }

    var body: some View {
        ZStack {
            // Action hints revealed behind the card while dragging.
            RoundedRectangle(cornerRadius: 14)
                .fill((offset >= 0 ? Color.statusGreen : Color.statusRed).opacity(0.18))
            HStack {
                Label("Duplizieren", systemImage: "plus.square.on.square")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.statusGreen)
                    .opacity(offset > 8 ? 1 : 0)
                Spacer()
                Label("Entfernen", systemImage: "trash")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.statusRed)
                    .opacity(offset < -8 ? 1 : 0)
            }
            .labelStyle(.titleAndIcon)
            .padding(.horizontal, 18)

            card
                .offset(x: offset)
                .contentShape(RoundedRectangle(cornerRadius: 14))
                .onTapGesture { onEdit() }
                .gesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { value in
                            guard abs(value.translation.width) > abs(value.translation.height) else { return }
                            offset = max(-110, min(110, value.translation.width))
                        }
                        .onEnded { value in
                            let dx = value.translation.width
                            if dx > threshold {
                                UINotificationFeedbackGenerator().notificationOccurred(.success)
                                onDuplicate()
                            } else if dx < -threshold {
                                UINotificationFeedbackGenerator().notificationOccurred(.warning)
                                onDelete()
                            }
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { offset = 0 }
                        }
                )
        }
    }
}

// MARK: - Empty Drink Hint

private struct EmptyDrinkHint: View {
    let onAdd: () -> Void

    var body: some View {
        Button(action: onAdd) {
            HStack(spacing: 14) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 36, height: 36)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Ersten Drink hinzufügen")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text("Tippe, um mit der Aufzeichnung zu beginnen")
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
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .contentShape(RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Achievement Unlock Toast

private struct AchievementUnlockToast: View {
    let achievement: Achievement
    let count: Int
    let onDismiss: () -> Void

    var body: some View {
        Button(action: onDismiss) {
            HStack(spacing: 12) {
                Image(systemName: achievement.icon)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 40, height: 40)
                    .background(Color.appAccent.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 11))

                VStack(alignment: .leading, spacing: 2) {
                    Text(count > 1 ? "\(count) neue Achievements" : "Achievement freigeschaltet")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                    Text(count > 1 ? "\(achievement.title) und mehr" : achievement.title)
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                }

                Spacer()

                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .glassCard(cornerRadius: 16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5)
            )
            .shadow(color: .black.opacity(0.3), radius: 12, y: 4)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
    }
}

// MARK: - Medication Warning Banner (B3)

private struct MedicationWarningBanner: View {
    let medications: [MedicationFlag]
    let onDismiss: () -> Void

    private var topMed: MedicationFlag { medications.first ?? .ibuprofen }

    // Names of the other active meds, so the banner reflects every one the user
    // configured instead of silently ignoring all but the first.
    private var othersText: String? {
        let rest = medications.dropFirst().map(\.rawValue)
        return rest.isEmpty ? nil : "Auch aktiv: " + rest.joined(separator: ", ")
    }

    var body: some View {
        Button(action: onDismiss) {
            HStack(spacing: 12) {
                Image(systemName: "pills.fill")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.statusOrange)
                    .frame(width: 36, height: 36)
                    .background(Color.statusOrange.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 2) {
                    Text(medications.count > 1
                         ? "Medikamenten-Hinweis (\(medications.count) aktiv)"
                         : "Medikamenten-Hinweis")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                    Text("\(topMed.rawValue): \(topMed.warningText)")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                        .fixedSize(horizontal: false, vertical: true)
                    if let othersText {
                        Text(othersText)
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextDim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text("Kein medizinischer Rat.")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
                Spacer()
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .glassCard(cornerRadius: 16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.statusOrange.opacity(0.4), lineWidth: 0.5)
            )
            .shadow(color: .black.opacity(0.3), radius: 10, y: 4)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 20)
    }
}

// MARK: - Pacing hint

private struct PacingHintBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "drop.fill")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.statusOrange)
                .frame(width: 36, height: 36)
                .background(Color.statusOrange.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text("Trink-Tempo")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Text(message)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .glassCard(cornerRadius: 16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.statusOrange.opacity(0.4), lineWidth: 0.5)
        )
    }
}

// MARK: - Weekly limit card

private struct WeeklyLimitCard: View {
    let used: Int
    let limit: Int

    private var fraction: Double {
        guard limit > 0 else { return 0 }
        return min(Double(used) / Double(limit), 1.0)
    }

    private var tint: Color {
        if used >= limit { return Color.statusRed }
        if used >= limit - 2 { return Color.statusOrange }
        return Color.appAccent
    }

    private var caption: String {
        if used >= limit { return "Wochenlimit erreicht" }
        if used >= limit - 2 { return "Wochenlimit fast erreicht" }
        return "Diese Woche"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(caption)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Spacer()
                Text("\(used)/\(limit) Drinks")
                    .font(.appCaptionBold)
                    .foregroundStyle(tint)
                    .monospacedDigit()
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.appBorder.opacity(0.4))
                    Capsule()
                        .fill(tint)
                        .frame(width: max(6, geo.size.width * fraction))
                }
            }
            .frame(height: 8)
            .animation(.easeInOut(duration: 0.3), value: fraction)
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

// MARK: - Preview

#Preview("Detailliert") {
    HomeView()
        .modelContainer(PersistenceController.preview.container)
        .preferredColorScheme(.dark)
}
