import Foundation
import Observation
import SwiftData

// MARK: - HistoryViewModel

@Observable
final class HistoryViewModel {

    var visibleMonth: Date

    // Drinks for the currently browsed window only (visible month plus the
    // neighbouring months). Loaded on demand from the model context instead of
    // holding the entire drinking history in memory via @Query, so memory stays
    // bounded no matter how many years of drinks are stored.
    private(set) var windowDrinks: [Drink] = []

    private let cal = Calendar.current

    init() {
        let now = Date()
        visibleMonth = cal.date(from: cal.dateComponents([.year, .month], from: now)) ?? now
    }

    // MARK: Windowed fetch

    // Loads drinks for [previous month start ... month after visible, exclusive],
    // i.e. a three-month window centred on the visible month. That fully covers
    // the visible grid, the logical-day spill at month edges, and the previous
    // month needed for the trend comparison, while never paging in old history.
    @MainActor
    func loadWindow(context: ModelContext) {
        guard
            let windowStart = cal.date(byAdding: .month, value: -1, to: visibleMonth),
            let windowEnd = cal.date(byAdding: .month, value: 2, to: visibleMonth)
        else { return }

        let descriptor = FetchDescriptor<Drink>(
            predicate: #Predicate { $0.timestamp >= windowStart && $0.timestamp < windowEnd },
            sortBy: [SortDescriptor(\.timestamp)]
        )
        windowDrinks = (try? context.fetch(descriptor)) ?? []
    }

    // MARK: Navigation

    private static let monthFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "MMMM yyyy"
        return fmt
    }()

    var monthTitle: String {
        Self.monthFormatter.string(from: visibleMonth)
    }

    var canGoNext: Bool {
        let next = cal.date(byAdding: .month, value: 1, to: visibleMonth) ?? visibleMonth
        let current = cal.date(from: cal.dateComponents([.year, .month], from: Date())) ?? Date()
        return next <= current
    }

    func previousMonth() {
        visibleMonth = cal.date(byAdding: .month, value: -1, to: visibleMonth) ?? visibleMonth
    }

    func nextMonth() {
        guard canGoNext else { return }
        visibleMonth = cal.date(byAdding: .month, value: 1, to: visibleMonth) ?? visibleMonth
    }

    func goToCurrentMonth() {
        let now = Date()
        visibleMonth = cal.date(from: cal.dateComponents([.year, .month], from: now)) ?? now
    }

    // MARK: Calendar grid
    // Returns 7-column cells; nil = empty padding before the 1st of the month.
    // Week start follows Calendar.current.firstWeekday (locale-aware).
    func gridDays() -> [Date?] {
        guard let range = cal.range(of: .day, in: .month, for: visibleMonth),
              let firstDay = cal.date(from: cal.dateComponents([.year, .month], from: visibleMonth))
        else { return [] }

        let rawWeekday = cal.component(.weekday, from: firstDay) // 1=Sun, 2=Mon … 7=Sat
        let firstWeekday = cal.firstWeekday                      // 1=Sun or 2=Mon depending on locale
        let offset = (rawWeekday - firstWeekday + 7) % 7
        var days: [Date?] = Array(repeating: nil, count: offset)
        for day in range {
            days.append(cal.date(byAdding: .day, value: day - 1, to: firstDay))
        }
        while days.count % 7 != 0 { days.append(nil) }
        return days
    }

    // MARK: Stats

    // Precomputed month aggregate so the view does a single pass over all
    // drinks instead of one full filter per calendar cell.
    struct MonthStats {
        // Keyed by midnight of the calendar day (matches gridDays dates).
        let byDay: [Date: DayStats]
        let drinkDays: Int    // days where alcohol was consumed
        let totalDrinks: Int  // all logged drinks in the month
        let totalCals: Int    // calories of all logged drinks in the month

        func stats(for date: Date) -> DayStats? { byDay[date] }
    }

    // Comparison against the previous month for the trend row.
    struct MonthTrend {
        let previousTotalDrinks: Int
        let limitedToDays: Int?   // non-nil when the running month is compared partially
    }

    func previousMonthComparison(drinks: [Drink], notes: [DayNote]) -> MonthTrend? {
        guard let prevMonth = cal.date(byAdding: .month, value: -1, to: visibleMonth) else { return nil }
        // Running month: compare only the elapsed day range, otherwise full month.
        let limit: Int? = canGoNext ? nil : cal.component(.day, from: Date())
        let prev = monthStats(for: prevMonth, drinks: drinks, notes: notes, limitToDays: limit)
        guard prev.totalDrinks > 0 else { return nil }
        return MonthTrend(previousTotalDrinks: prev.totalDrinks, limitedToDays: limit)
    }

    // MARK: - Charts, Trends & Mood
    
    struct CategoryTrend: Identifiable {
        let id = UUID()
        let category: String
        let count: Int
    }
    
    func categoryTrends(drinks: [Drink], days: Int = 30) -> [CategoryTrend] {
        let cutoff = cal.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        let recent = drinks.filter { $0.timestamp >= cutoff && $0.abv > 0 }
        
        var counts: [String: Int] = [:]
        for d in recent {
            counts[d.category.localizedName, default: 0] += 1
        }
        return counts.map { CategoryTrend(category: $0.key, count: $0.value) }
            .sorted { $0.count > $1.count }
    }
    
    func weeklyDrinkCounts(drinks: [Drink], weeksBack: Int = 4) -> [(weekStart: Date, count: Int)] {
        var calendar = Calendar.current
        calendar.firstWeekday = 2 // Monday
        
        var result: [(Date, Int)] = []
        let now = Date()
        guard let currentWeekStart = calendar.date(from: calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: now)) else { return [] }
        
        for i in 0..<weeksBack {
            guard let weekStart = calendar.date(byAdding: .weekOfYear, value: -i, to: currentWeekStart),
                  let weekEnd = calendar.date(byAdding: .weekOfYear, value: 1, to: weekStart) else { continue }
            
            let count = drinks.filter { $0.timestamp >= weekStart && $0.timestamp < weekEnd && $0.abv > 0 }.count
            result.append((weekStart, count))
        }
        return result.reversed() // oldest first for charts
    }

    struct MoodCorrelation: Identifiable {
        let id = UUID()
        let moodScore: Int
        let averagePeakBAC: Double
    }

    // Korreliert Kater/Stimmung mit dem Peak BAC der Nacht, auf die sich die
    // Notiz bezieht.
    func getMoodCorrelations(drinks: [Drink], notes: [DayNote], profile: UserProfile) -> [MoodCorrelation] {
        // 1. Drinks nach logischem Tag (Mitternacht des logischen Tages)
        //    gruppieren - dieselbe Schluesselform wie note.dayStart.
        var drinksByDay: [Date: [Drink]] = [:]
        for d in drinks where d.abv > 0 {
            let day = cal.logicalDay(for: d.timestamp)
            drinksByDay[day, default: []].append(d)
        }

        // 2. Pro Stimmung (MoodScore) die Peak-BACs sammeln. note.dayStart ist
        //    bereits die Trinknacht: MorningMoodPrompt und das Tages-Detail
        //    speichern die Stimmung unter der Nacht, auf die sie sich bezieht -
        //    daher kein Tagesversatz mehr.
        var bacsByMood: [Int: [Double]] = [:]

        for note in notes where note.mood != .neutral {
            if let dayDrinks = drinksByDay[note.dayStart] {
                let peakBAC = BACCalculator.peakBAC(drinks: dayDrinks, profile: profile, stomachStatus: profile.defaultStomachStatus, conservative: profile.conservativeForApp)
                bacsByMood[note.moodRaw, default: []].append(peakBAC)
            }
        }
        
        // 3. Durchschnittlichen BAC pro Stimmung berechnen
        return bacsByMood.map { (mood, bacs) in
            let avgBAC = bacs.reduce(0.0, +) / Double(bacs.count)
            return MoodCorrelation(moodScore: mood, averagePeakBAC: avgBAC)
        }.sorted { $0.moodScore > $1.moodScore }
    }

    // Logical day: 06:00 on `date` to 05:59 the next day (matches SessionViewModel).
    func monthStats(drinks: [Drink], notes: [DayNote]) -> MonthStats {
        monthStats(for: visibleMonth, drinks: drinks, notes: notes)
    }

    // limitToDays: only aggregate the first N days, used to compare a running
    // month fairly against the same range of the previous month.
    func monthStats(for month: Date, drinks: [Drink], notes: [DayNote], limitToDays: Int? = nil) -> MonthStats {
        guard let range = cal.range(of: .day, in: .month, for: month),
              let firstDay = cal.date(from: cal.dateComponents([.year, .month], from: month))
        else { return MonthStats(byDay: [:], drinkDays: 0, totalDrinks: 0, totalCals: 0) }

        var dayDates: [Date] = range.compactMap { cal.date(byAdding: .day, value: $0 - 1, to: firstDay) }
        if let limit = limitToDays, limit < dayDates.count {
            dayDates = Array(dayDates.prefix(limit))
        }
        let dayStarts: [Date] = dayDates.map {
            cal.date(bySettingHour: 6, minute: 0, second: 0, of: $0) ?? cal.startOfDay(for: $0)
        }
        guard let windowStart = dayStarts.first,
              let lastStart = dayStarts.last,
              let windowEnd = cal.date(byAdding: .day, value: 1, to: lastStart)
        else { return MonthStats(byDay: [:], drinkDays: 0, totalDrinks: 0, totalCals: 0) }

        // Single pass: bucket drinks into their logical day.
        var drinksByDay: [Date: [Drink]] = [:]
        for drink in drinks where drink.timestamp >= windowStart && drink.timestamp < windowEnd {
            drinksByDay[cal.logicalDay(for: drink.timestamp), default: []].append(drink)
        }

        var notesByDay: [Date: DayNote] = [:]
        for note in notes {
            notesByDay[cal.startOfDay(for: note.dayStart)] = note
        }

        var byDay: [Date: DayStats] = [:]
        var drinkDays = 0, totalDrinks = 0, totalCals = 0
        for (date, start) in zip(dayDates, dayStarts) {
            let key = cal.startOfDay(for: date)
            let dayDrinks = (drinksByDay[key] ?? []).sorted { $0.timestamp < $1.timestamp }
            let s = DayStats(date: start, drinks: dayDrinks, note: notesByDay[key])
            byDay[date] = s
            if s.hadAlcohol { drinkDays += 1 }
            totalDrinks += s.drinkCount
            totalCals   += s.totalCalories
        }
        return MonthStats(byDay: byDay, drinkDays: drinkDays, totalDrinks: totalDrinks, totalCals: totalCals)
    }
}
