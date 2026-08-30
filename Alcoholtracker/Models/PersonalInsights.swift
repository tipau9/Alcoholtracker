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

    struct Discovery: Identifiable {
        let title: String
        let detail: String
        let evidence: String
        let icon: String
        var id: String { title + "|" + evidence }
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
    let discoveries: [Discovery]

    static let empty = PersonalInsights(
        totalDrinks: 0, drinkingDays: 0, alcoholFreeDays: 0,
        currentAlcoholFreeStreak: 0, totalAlcoholGrams: 0, totalCalories: 0,
        totalVolumeML: 0, averageDrinksPerDrinkingDay: 0,
        averageDrinkMinutes: 0, averageSessionMinutes: 0,
        averageDrinksPerHour: 0, averagePeakBAC: 0, highestPeakBAC: 0,
        highestPeakDate: nil, typicalStartMinutesAfterMidnight: nil,
        topDrinks: [], topCategories: [], hourly: [], weekdays: [], discoveries: []
    )

    static func build(
        drinks: [Drink],
        profile: UserProfile?,
        cutoff: Date?,
        notes: [DayNote] = [],
        breathalyzerReadings: [BreathalyzerReading] = [],
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

        var discoveries: [Discovery] = []
        if sessions.count >= 5 {
            let firstGaps = sessionValues.compactMap { session -> Double? in
                guard session.count >= 2 else { return nil }
                return session[1].timestamp.timeIntervalSince(session[0].timestamp) / 60
            }.sorted()
            if firstGaps.count >= 5 {
                let medianGap = firstGaps[firstGaps.count / 2]
                if medianGap < 30 {
                    discoveries.append(Discovery(
                        title: "Schneller Einstieg",
                        detail: "Zwischen deinem ersten und zweiten Drink liegen typischerweise nur \(Int(medianGap.rounded())) Minuten.",
                        evidence: "Median aus \(firstGaps.count) vergleichbaren Sessions",
                        icon: "bolt.fill"
                    ))
                }
            }

            let orderedSessions = sessions.sorted { $0.key < $1.key }
            if orderedSessions.count >= 8 {
                func shiftedEndMinute(_ drinks: [Drink]) -> Double {
                    guard let end = drinks.map(\.estimatedFinishedAt).max() else { return 0 }
                    let h = calendar.component(.hour, from: end)
                    let m = calendar.component(.minute, from: end)
                    let raw = Double(h * 60 + m)
                    return raw < 360 ? raw + 1440 : raw
                }
                let endMinutes = orderedSessions.map { shiftedEndMinute($0.value) }
                let split = endMinutes.count / 2
                let older = average(Array(endMinutes[..<split]))
                let newer = average(Array(endMinutes[split...]))
                let shift = newer - older
                if abs(shift) >= 30 {
                    discoveries.append(Discovery(
                        title: shift > 0 ? "Deine Abende enden später" : "Deine Abende enden früher",
                        detail: "Im jüngeren Zeitraum lag dein letzter Drink im Schnitt \(Int(abs(shift).rounded())) Minuten \(shift > 0 ? "später" : "früher").",
                        evidence: "Vergleich von je \(split) Sessions",
                        icon: shift > 0 ? "moon.stars.fill" : "sunrise.fill"
                    ))
                }
            }

            if let favorite = topDrinks.first, alcohol.count >= 10 {
                let share = Double(favorite.count) / Double(alcohol.count)
                if share >= 0.4 {
                    discoveries.append(Discovery(
                        title: "Dein klarer Favorit",
                        detail: "\(favorite.name) macht \(Int((share * 100).rounded())) % deiner Einträge in diesem Zeitraum aus.",
                        evidence: "\(favorite.count) von \(alcohol.count) Drinks",
                        icon: "star.fill"
                    ))
                }
            }
        }

        let relevantReadings = breathalyzerReadings.filter {
            $0.timestamp <= now && (cutoff == nil || $0.timestamp >= cutoff!)
        }
        if relevantReadings.count >= 5 {
            let differences = relevantReadings.map { $0.measuredBAC - $0.estimatedBAC }
            let bias = average(differences)
            let meanAbsoluteError = average(differences.map(abs))
            discoveries.append(Discovery(
                title: "Messung und Schätzung",
                detail: "Deine Breathalyser-Werte lagen im Mittel \(abs(bias).permilleString) \(bias >= 0 ? "über" : "unter") der App-Schätzung.",
                evidence: "Ø absolute Abweichung \(meanAbsoluteError.permilleString) aus \(relevantReadings.count) Messungen",
                icon: "wind"
            ))
        }

        if notes.count >= 6 {
            let drinksByCalendarDay = Dictionary(grouping: alcohol) {
                calendar.startOfDay(for: calendar.logicalDay(for: $0.timestamp))
            }
            let positive = notes.filter { $0.mood == .happy || $0.mood == .proud }
                .compactMap { drinksByCalendarDay[calendar.startOfDay(for: $0.dayStart)]?.count }
            let negative = notes.filter { $0.mood == .regret || $0.mood == .terrible }
                .compactMap { drinksByCalendarDay[calendar.startOfDay(for: $0.dayStart)]?.count }
            if positive.count >= 3, negative.count >= 3 {
                let positiveAverage = average(positive.map(Double.init))
                let negativeAverage = average(negative.map(Double.init))
                if negativeAverage - positiveAverage >= 1 {
                    discoveries.append(Discovery(
                        title: "Morgenstimmung und Drinkzahl",
                        detail: "Abende mit negativer Morgenbewertung hatten im Mittel \(oneDecimalForInsight(negativeAverage - positiveAverage)) mehr Drinks.",
                        evidence: "\(positive.count + negative.count) bewertete Abende · Zusammenhang, keine Ursache",
                        icon: "face.dashed.fill"
                    ))
                }
            }

            var hydratedMoods: [Bool] = []
            var lessHydratedMoods: [Bool] = []
            for note in notes where note.mood != .neutral {
                let day = calendar.startOfDay(for: note.dayStart)
                guard let session = drinksByCalendarDay[day], !session.isEmpty,
                      let glasses = WaterLog.loggedGlasses(forDay: day) else { continue }
                let wasNegative = note.mood == .regret || note.mood == .terrible
                if glasses * 2 >= session.count {
                    hydratedMoods.append(wasNegative)
                } else {
                    lessHydratedMoods.append(wasNegative)
                }
            }
            if hydratedMoods.count >= 3, lessHydratedMoods.count >= 3 {
                let hydratedNegativeRate = Double(hydratedMoods.filter { $0 }.count) / Double(hydratedMoods.count)
                let lessHydratedNegativeRate = Double(lessHydratedMoods.filter { $0 }.count) / Double(lessHydratedMoods.count)
                if lessHydratedNegativeRate - hydratedNegativeRate >= 0.2 {
                    discoveries.append(Discovery(
                        title: "Wasser und Morgenstimmung",
                        detail: "An Abenden mit mindestens einem Glas Wasser je zwei Drinks hast du den Morgen seltener negativ bewertet.",
                        evidence: "\(hydratedMoods.count + lessHydratedMoods.count) bewertete Abende · Zusammenhang, keine Ursache",
                        icon: "drop.fill"
                    ))
                }
            }
        }

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
            weekdays: weekdays,
            discoveries: Array(discoveries.prefix(5))
        )
    }
}

private func oneDecimalForInsight(_ value: Double) -> String {
    String(format: "%.1f", value).replacingOccurrences(of: ".", with: ",")
}
