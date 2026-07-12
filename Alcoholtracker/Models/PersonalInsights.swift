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

        return PersonalInsights(
            totalDrinks: alcohol.count,
            drinkingDays: sessions.count,
            alcoholFreeDays: alcoholFreeDays,
            currentAlcoholFreeStreak: currentStreak,
            totalAlcoholGrams: alcohol.reduce(0) { $0 + $1.alcoholGrams },
            totalCalories: alcohol.reduce(0) { $0 + $1.calories },
            totalVolumeML: alcohol.reduce(0) { $0 + $1.volume },
            averageDrinksPerDrinkingDay: Double(alcohol.count) / Double(max(sessions.count, 1)),
            averageDrinkMinutes: average(alcohol.map(\.effectiveDrinkDurationMinutes)),
            averageSessionMinutes: average(sessionMinutes),
            averageDrinksPerHour: average(sessionPaces),
            averagePeakBAC: average(peaks.map(\.1)),
            highestPeakBAC: highest?.1 ?? 0,
            highestPeakDate: highest?.0,
            typicalStartMinutesAfterMidnight: typicalStart,
            topDrinks: drinkCounts.map {
                RankedItem(name: $0.key, subtitle: $0.value.category, count: $0.value.count)
            }.sorted { $0.count == $1.count ? $0.name < $1.name : $0.count > $1.count }.prefix(5).map { $0 },
            topCategories: categoryCounts.map {
                RankedItem(name: $0.key, subtitle: "Kategorie", count: $0.value)
            }.sorted { $0.count == $1.count ? $0.name < $1.name : $0.count > $1.count }.prefix(5).map { $0 },
            hourly: (0..<24).map { TimeBucket(value: $0, count: hourCounts[$0, default: 0]) },
            weekdays: (0..<7).map { TimeBucket(value: $0, count: weekdayCounts[$0, default: 0]) }
        )
    }
}
