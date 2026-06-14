import SwiftUI
import SwiftData

// MARK: - HistoryView
//
// Monthly calendar grid showing drinking history.
// Each day cell is color-coded by peak BAC estimate.
// Tap any past day to open DayDetailSheet.

struct HistoryView: View {

    // Drinks are not loaded via @Query (which would page the entire history into
    // memory). The view model fetches only the browsed month window; notes are
    // one-per-day and small enough to keep as a @Query.
    @Query private var allNotes: [DayNote]
    @Query private var profiles: [UserProfile]
    @Environment(\.modelContext) private var context

    @State private var vm = HistoryViewModel()
    @State private var selectedDay: SelectedDate? = nil
    @State private var showTrends = false

    private var profile: UserProfile? { profiles.first }

    private var gridDays: [Date?] { vm.gridDays() }

    var body: some View {
        let windowDrinks = vm.windowDrinks
        let monthStats = vm.monthStats(drinks: windowDrinks, notes: allNotes)
        let trend = vm.previousMonthComparison(drinks: windowDrinks, notes: allNotes)
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                HVTopBar(title: vm.monthTitle, canNext: vm.canGoNext) {
                    vm.previousMonth()
                } onNext: {
                    vm.nextMonth()
                } onToday: {
                    vm.goToCurrentMonth()
                } onTrends: {
                    showTrends = true
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 12)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {
                        HVWeekdayHeader()
                        calendarGrid(monthStats)
                        HVMonthSummary(
                            drinkDays: monthStats.drinkDays,
                            totalDrinks: monthStats.totalDrinks,
                            totalCals: monthStats.totalCals,
                            trend: trend
                        )
                        legend
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 40)
                }
            }
        }
        .onAppear { vm.loadWindow(context: context) }
        .onChange(of: vm.visibleMonth) { vm.loadWindow(context: context) }
        .sheet(item: $selectedDay, onDismiss: { vm.loadWindow(context: context) }) { selection in
            DayDetailSheet(
                date: selection.date,
                allDrinks: windowDrinks,
                allNotes: allNotes,
                profile: profile
            )
        }
        .sheet(isPresented: $showTrends) {
            TrendsView()
        }
    }

    // MARK: Calendar grid

    private func calendarGrid(_ monthStats: HistoryViewModel.MonthStats) -> some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 7),
            spacing: 5
        ) {
            ForEach(gridDays.indices, id: \.self) { idx in
                if let date = gridDays[idx] {
                    HVDayCell(
                        date: date,
                        stats: monthStats.stats(for: date) ?? DayStats(date: date, drinks: [], note: nil),
                        profile: profile
                    ) {
                        selectedDay = SelectedDate(date: date)
                    }
                } else {
                    Color.clear.aspectRatio(1, contentMode: .fit)
                }
            }
        }
    }

    // MARK: Legend

    private var legend: some View {
        let skin = profile?.statusSkin ?? .standard
        return LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), alignment: .leading), count: 3),
            alignment: .leading,
            spacing: 8
        ) {
            ForEach([BACStatus.sober, .tipsy, .drunk, .careful, .danger], id: \.self) { status in
                HStack(spacing: 5) {
                    Circle()
                        .fill(status.color.opacity(0.30))
                        .frame(width: 10, height: 10)
                        .overlay(Circle().strokeBorder(status.color, lineWidth: 0.5))
                    Text(skin.label(for: status))
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Top bar

private struct HVTopBar: View {
    let title: String
    let canNext: Bool
    let onPrev: () -> Void
    let onNext: () -> Void
    let onToday: () -> Void
    let onTrends: () -> Void

    var body: some View {
        HStack {
            Text("Verlauf")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)

            Button(action: onTrends) {
                Image(systemName: "chart.bar.xaxis")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 30, height: 30)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(.leading, 6)

            Spacer()

            HStack(spacing: 4) {
                // Quick jump back to the current month after browsing the past
                if canNext {
                    Button(action: onToday) {
                        Text("Heute")
                            .font(.appMicro)
                            .foregroundStyle(Color.appAccent)
                            .padding(.horizontal, 10)
                            .frame(height: 32)
                            .background(Color.appAccent.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }

                Button(action: onPrev) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.appTextDim)
                        .frame(width: 32, height: 32)
                        .background(Color.appCard)
                        .clipShape(Circle())
                        .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Vorheriger Monat")

                Text(title)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appText)
                    .frame(minWidth: 110, alignment: .center)

                Button(action: onNext) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(canNext ? Color.appTextDim : Color.appBorder)
                        .frame(width: 32, height: 32)
                        .background(Color.appCard)
                        .clipShape(Circle())
                        .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                }
                .buttonStyle(.plain)
                .disabled(!canNext)
                .accessibilityLabel("Nächster Monat")
            }
        }
    }
}

// MARK: - Weekday header

private struct HVWeekdayHeader: View {
    private var labels: [String] {
        // German symbols to match the app language; order follows the device
        // calendar so the header stays aligned with the grid offset.
        var localized = Calendar.current
        localized.locale = Locale(identifier: "de_DE")
        let symbols = localized.shortStandaloneWeekdaySymbols  // starts at Sunday
        let first = Calendar.current.firstWeekday - 1          // 0-indexed
        return (0..<7).map { symbols[($0 + first) % 7] }
    }

    var body: some View {
        HStack(spacing: 5) {
            ForEach(labels, id: \.self) { label in
                Text(label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - Day cell

private struct HVDayCell: View {
    let date: Date
    let stats: DayStats
    let profile: UserProfile?
    let onTap: () -> Void

    private let cal = Calendar.current

    private var isToday: Bool { cal.isDateInToday(date) }
    private var isFuture: Bool { date > cal.startOfDay(for: Date()) }

    private var cellColor: Color {
        guard !isFuture, stats.hadAlcohol else { return .clear }
        // Without a profile no BAC estimate is possible; mark the day generically.
        guard let p = profile else { return Color.appAccent.opacity(0.25) }
        return stats.bacStatus(profile: p).color.opacity(0.30)
    }

    private var moodNote: DayNote? {
        guard let note = stats.note, note.mood != .neutral else { return nil }
        return note
    }

    var body: some View {
        Button(action: onTap) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(cellColor == .clear ? Color.appCard : cellColor)

                if isToday {
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(Color.appAccent, lineWidth: 1.5)
                } else {
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(Color.appBorder.opacity(0.5), lineWidth: 0.5)
                }

                VStack(spacing: 2) {
                    Text("\(cal.component(.day, from: date))")
                        .font(.system(size: 13, weight: isToday ? .bold : .regular))
                        .foregroundStyle(
                            isFuture ? Color.appTextMuted.opacity(0.4)
                            : isToday ? Color.appAccent
                            : Color.appText
                        )
                        .monospacedDigit()

                    // Mood wins over the generic dot: the cell background already
                    // signals alcohol, so the emoji adds information instead of hiding it.
                    if let note = moodNote, !isFuture {
                        Text(note.mood.emoji)
                            .font(.system(size: 8))
                    } else if stats.hadAlcohol && !isFuture {
                        Circle()
                            .fill(Color.appText.opacity(0.4))
                            .frame(width: 4, height: 4)
                    } else {
                        Color.clear.frame(width: 4, height: 4)
                    }
                }
            }
            .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
        .disabled(isFuture)
        .accessibilityLabel(accessibilityText)
    }

    private var accessibilityText: String {
        let day = cal.component(.day, from: date)
        var parts = ["Tag \(day)"]
        if isToday { parts.append("heute") }
        if stats.drinkCount > 0 {
            parts.append("\(stats.drinkCount) \(stats.drinkCount == 1 ? "Drink" : "Drinks")")
        }
        if let note = moodNote {
            parts.append(note.mood.label)
        }
        return parts.joined(separator: ", ")
    }
}

// MARK: - Month summary strip

private struct HVMonthSummary: View {
    let drinkDays: Int
    let totalDrinks: Int
    let totalCals: Int
    let trend: HistoryViewModel.MonthTrend?

    var body: some View {
        VStack(spacing: 10) {
            SectionLabel(text: "MONATS-ÜBERSICHT")
            HStack(spacing: 0) {
                HVSummaryTile(icon: "calendar", label: "Tage", value: "\(drinkDays)", color: Color.appAccent)
                Divider().frame(maxHeight: 44).background(Color.appBorder)
                HVSummaryTile(icon: "mug.fill", label: "Drinks", value: "\(totalDrinks)", color: Color.appAccent)
                Divider().frame(maxHeight: 44).background(Color.appBorder)
                HVSummaryTile(icon: "flame.fill", label: "Kalorien", value: "\(totalCals)", color: Color.statusOrange)
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            if let trend {
                trendRow(trend)
            }
        }
    }

    private func trendRow(_ trend: HistoryViewModel.MonthTrend) -> some View {
        let prev = trend.previousTotalDrinks
        let diff = totalDrinks - prev
        let pct = Int((Double(abs(diff)) / Double(prev) * 100).rounded())

        let icon: String
        let color: Color
        let text: String
        if diff < 0 {
            icon = "arrow.down.right"
            color = Color.statusGreen
            text = "\(pct)% weniger Drinks als im Vormonat"
        } else if diff > 0 {
            icon = "arrow.up.right"
            color = Color.statusOrange
            text = "\(pct)% mehr Drinks als im Vormonat"
        } else {
            icon = "equal"
            color = Color.appTextDim
            text = "Gleich viele Drinks wie im Vormonat"
        }

        return HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 1) {
                Text(text)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                if let days = trend.limitedToDays {
                    Text("Vergleich: jeweils die ersten \(days) Tage")
                        .font(.system(size: 9))
                        .foregroundStyle(Color.appTextMuted)
                }
            }
            Spacer()
        }
        .padding(.horizontal, 4)
    }
}

private struct HVSummaryTile: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(color)
            Text(value)
                .font(.appBodyBold)
                .foregroundStyle(Color.appText)
                .monospacedDigit()
            Text(label)
                .font(.appMicro)
                .foregroundStyle(Color.appTextDim)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }
}

// Stable-ID wrapper so sheet(item:) has no race condition with optional state.
private struct SelectedDate: Identifiable {
    let id = UUID()
    let date: Date
}

#Preview {
    let controller = PersistenceController.preview
    return HistoryView()
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}
