import Foundation

struct PersonalInsights {
    struct RankedItem: Identifiable {
        let name: String
        let subtitle: String
        let count: Int
        var id: String { name + "|" + subtitle }
    }

    struct TimeBucket: Identifiable {
        let value: Int
        let count: Int
        var id: Int { value }
    }

    let totalDrinks: Int
    let drinkingDays: Int
    let alcoholFreeDays: Int
    let currentAlcoholFreeStreak: Int
    let totalAlcoholGrams: Double
    let totalCalories: Int
    let totalVolumeML: Double
    let averageDrinksPerDrinkingDay: Double
    let averageDrinkMinutes: Double
    let averageSessionMinutes: Double
    let averageDrinksPerHour: Double
    let averagePeakBAC: Double
    let highestPeakBAC: Double
    let highestPeakDate: Date?
    let typicalStartMinutesAfterMidnight: Int?
    let topDrinks: [RankedItem]
    let topCategories: [RankedItem]
    let hourly: [TimeBucket]
    let weekdays: [TimeBucket]

    static let empty = PersonalInsights(
        totalDrinks: 0, drinkingDays: 0, alcoholFreeDays: 0,
        currentAlcoholFreeStreak: 0, totalAlcoholGrams: 0, totalCalories: 0,
        totalVolumeML: 0, averageDrinksPerDrinkingDay: 0,
        averageDrinkMinutes: 0, averageSessionMinutes: 0,
        averageDrinksPerHour: 0, averagePeakBAC: 0, highestPeakBAC: 0,
        highestPeakDate: nil, typicalStartMinutesAfterMidnight: nil,
        topDrinks: [], topCategories: [], hourly: [], weekdays: []
    )

    static func build(
        drinks: [Drink],
        profile: UserProfile?,
        cutoff: Date?,
        now: Date = Date()
    ) -> PersonalInsights {
        let calendar = Calendar.current
        let alcohol = drinks
            .filter { $0.abv > 0.01 && (cutoff == nil || $0.timestamp >= cutoff!) && $0.timestamp <= now }
            .sorted { $0.timestamp < $1.timestamp }
        guard !alcohol.isEmpty else { return .empty }

        var sessions: [Date: [Drink]] = [:]
        for drink in alcohol {
            sessions[calendar.logicalDay(for: drink.timestamp), default: []].append(drink)
        }

        let sessionValues = sessions.values.map { $0.sorted { $0.timestamp < $1.timestamp } }
        let sessionMinutes = sessionValues.map { session -> Double in
            guard let first = session.first else { return 0 }
            let end = session.map(\.estimatedFinishedAt).max() ?? first.estimatedFinishedAt
            return min(1440, max(first.effectiveDrinkDurationMinutes, end.timeIntervalSince(first.timestamp) / 60))
        }
        let sessionPaces = zip(sessionValues, sessionMinutes).map { pair in
            Double(pair.0.count) / max(pair.1 / 60, 0.25)
        }

        let peaks: [(Date, Double)] = sessions.map { day, dayDrinks in
            guard let profile else { return (day, 0) }
            let peak = BACProjectionInput(
                drinks: dayDrinks,
                profile: profile,
                stomachStatus: profile.defaultStomachStatus,
                conservative: profile.conservativeForApp,
                vomitTimes: []
            ).peakBAC()
            return (day, peak)
        }
        let highest = peaks.max { $0.1 < $1.1 }

        var drinkCounts: [String: (category: String, count: Int)] = [:]
        var categoryCounts: [String: Int] = [:]
        var hourCounts: [Int: Int] = [:]
        var weekdayCounts: [Int: Int] = [:]
        for drink in alcohol {
            let key = drink.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let old = drinkCounts[key] ?? (drink.category.localizedName, 0)
            drinkCounts[key] = (old.category, old.count + 1)
            categoryCounts[drink.category.localizedName, default: 0] += 1
            hourCounts[calendar.component(.hour, from: drink.timestamp), default: 0] += 1
            // Monday = 0 ... Sunday = 6.
            let weekday = (calendar.component(.weekday, from: drink.timestamp) + 5) % 7
            weekdayCounts[weekday, default: 0] += 1
        }

        let firstDay = cutoff.map { calendar.logicalDay(for: $0) }
            ?? sessions.keys.min()
            ?? calendar.logicalDay(for: now)
        let today = calendar.logicalDay(for: now)
        let observedDays = max(1, (calendar.dateComponents([.day], from: firstDay, to: today).day ?? 0) + 1)
        let drinkingDaySet = Set(sessions.keys)
        let alcoholFreeDays = max(0, observedDays - drinkingDaySet.count)

        var currentStreak = 0
        var cursor = today
        while cursor >= firstDay && !drinkingDaySet.contains(cursor) {
            currentStreak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }

        // Average session starts on a circular 24-hour clock. Shift early-morning
        // starts past midnight so late-night sessions do not average to noon.
        let shiftedStarts = sessionValues.compactMap { session -> Int? in
            guard let first = session.first else { return nil }
            let hour = calendar.component(.hour, from: first.timestamp)
            let minute = calendar.component(.minute, from: first.timestamp)
            let raw = hour * 60 + minute
            return raw < 6 * 60 ? raw + 24 * 60 : raw
        }
        let typicalStart = shiftedStarts.isEmpty
            ? nil
            : (Int(Double(shiftedStarts.reduce(0, +)) / Double(shiftedStarts.count)) % (24 * 60))

        func average(_ values: [Double]) -> Double {
            values.isEmpty ? 0 : values.reduce(0, +) / Double(values.count)
        }

        // Broken into locals so the type-checker does not time out on one
        // giant initializer expression.
        let totalAlcoholGrams = alcohol.reduce(0.0) { $0 + $1.alcoholGrams }
        let totalCalories = alcohol.reduce(0) { $0 + $1.calories }
        let totalVolumeML = alcohol.reduce(0.0) { $0 + $1.volume }
        let averagePerDrinkingDay = Double(alcohol.count) / Double(max(sessions.count, 1))
        let drinkMinutes = average(alcohol.map(\.effectiveDrinkDurationMinutes))
        let peakAverage = average(peaks.map(\.1))

        func topFive(_ items: [RankedItem]) -> [RankedItem] {
            let sorted = items.sorted { a, b in
                if a.count != b.count { return a.count > b.count }
                return a.name < b.name
            }
            return Array(sorted.prefix(5))
        }
        let drinkItems: [RankedItem] = drinkCounts.map { entry in
            RankedItem(name: entry.key, subtitle: entry.value.category, count: entry.value.count)
        }
        let categoryItems: [RankedItem] = categoryCounts.map { entry in
            RankedItem(name: entry.key, subtitle: "Kategorie", count: entry.value)
        }
        let topDrinks = topFive(drinkItems)
        let topCategories = topFive(categoryItems)
        let hourly = (0..<24).map { TimeBucket(value: $0, count: hourCounts[$0, default: 0]) }
        let weekdays = (0..<7).map { TimeBucket(value: $0, count: weekdayCounts[$0, default: 0]) }

        return PersonalInsights(
            totalDrinks: alcohol.count,
            drinkingDays: sessions.count,
            alcoholFreeDays: alcoholFreeDays,
            currentAlcoholFreeStreak: currentStreak,
            totalAlcoholGrams: totalAlcoholGrams,
            totalCalories: totalCalories,
            totalVolumeML: totalVolumeML,
            averageDrinksPerDrinkingDay: averagePerDrinkingDay,
            averageDrinkMinutes: drinkMinutes,
            averageSessionMinutes: average(sessionMinutes),
            averageDrinksPerHour: average(sessionPaces),
            averagePeakBAC: peakAverage,
            highestPeakBAC: highest?.1 ?? 0,
            highestPeakDate: highest?.0,
            typicalStartMinutesAfterMidnight: typicalStart,
            topDrinks: topDrinks,
            topCategories: topCategories,
            hourly: hourly,
            weekdays: weekdays
        )
    }
}
