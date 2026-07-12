import SwiftUI
import SwiftData
import Charts

// MARK: - TrendsView

struct TrendsView: View {
    // All charts here look at most 30 days / 4 weeks back, so we only page in
    // the last 60 days instead of the entire drinking history. The bound is
    // computed once at view creation; reopening the sheet rebuilds it.
    @Query(sort: \Drink.timestamp) private var drinks: [Drink]
    @Query private var notes: [DayNote]
    @Query private var profiles: [UserProfile]
    @Environment(\.dismiss) private var dismiss
    @Environment(SupabaseService.self) private var supabase
    @Environment(LocationService.self) private var locationService

    @State private var viewModel = HistoryViewModel()
    @State private var cityTrends: [CityDrinkTrend] = []
    @State private var cityInsights: CityDrinkInsights?
    @State private var trendsCity: String?
    @State private var loadingTrends = false
    @State private var period: InsightsPeriod = .days30
    @State private var personalInsights: PersonalInsights = .empty

    private var profile: UserProfile? { profiles.first }

    private var periodCutoff: Date? {
        period.days.flatMap { Calendar.current.date(byAdding: .day, value: -$0, to: Date()) }
    }

    private var periodDrinks: [Drink] {
        guard let cutoff = periodCutoff else { return drinks }
        return drinks.filter { $0.timestamp >= cutoff }
    }

    private var personalAnalysisKey: String {
        let drinkKey = drinks.map {
            "\($0.id.uuidString)|\($0.timestamp.timeIntervalSinceReferenceDate)|\($0.volume)|\($0.abv)|\($0.drinkDurationMinutes)"
        }.joined(separator: "#")
        return "\(period.rawValue)|\(profile?.bacProjectionKey ?? "")|\(drinkKey)"
    }
    
    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 24) {
                    if drinks.isEmpty {
                        Text("Noch keine Daten für Trends vorhanden.")
                            .font(.appBody)
                            .foregroundStyle(Color.appTextDim)
                            .padding(.top, 40)
                    } else {
                        periodPicker
                        personalOverview
                        consumptionProfileCard
                        personalTopDrinksCard
                        timeOfDayCard
                        weekdayCard
                        weeklyChart
                        categoryChart
                        if let p = profile {
                            moodChart(profile: p)
                        }
                    }
                    localTrendsCard
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 20)
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationTitle("Trends & Einblicke")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fertig") {
                        dismiss()
                    }
                    .foregroundStyle(Color.appAccent)
                }
            }
        }
        .task {
            // Use already-known city if available; otherwise request location.
            if let city = locationService.currentCity {
                trendsCity = city
            } else if locationService.status != .denied {
                locationService.requestLocation()
            }
        }
        .onChange(of: locationService.currentCity) { _, city in
            if trendsCity == nil, let city {
                trendsCity = city
            }
        }
        .task(id: trendsCity) {
            guard let city = trendsCity else { return }
            loadingTrends = true
            do {
                cityInsights = try await supabase.fetchCityInsights(city: city)
                cityTrends = cityInsights?.topDrinks.map {
                    CityDrinkTrend(drinkName: $0.drinkName, category: $0.category, pingCount: $0.pingCount)
                } ?? []
            } catch {
                // Older backends still provide the original Top-Drinks RPC.
                cityInsights = nil
                cityTrends = (try? await supabase.fetchCityTrends(city: city)) ?? []
            }
            loadingTrends = false
        }
        .task(id: personalAnalysisKey) {
            personalInsights = PersonalInsights.build(
                drinks: drinks,
                profile: profile,
                cutoff: periodCutoff
            )
        }
    }

    private var periodPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("DEIN ZEITRAUM")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            Picker("Zeitraum", selection: $period) {
                ForEach(InsightsPeriod.allCases) { item in
                    Text(item.label).tag(item)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private var personalOverview: some View {
        VStack(alignment: .leading, spacing: 14) {
            InsightsSectionHeader(icon: "person.fill", title: "Deine Übersicht", subtitle: period.label)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                InsightsMetricTile(value: "\(personalInsights.totalDrinks)", label: "Drinks", icon: "wineglass.fill", color: .appAccent)
                InsightsMetricTile(value: "\(personalInsights.drinkingDays)", label: "Trinktage", icon: "calendar", color: .statusOrange)
                InsightsMetricTile(value: oneDecimal(personalInsights.averageDrinksPerDrinkingDay), label: "Ø je Trinktag", icon: "divide", color: .appTextDim)
                InsightsMetricTile(value: "\(personalInsights.currentAlcoholFreeStreak)", label: "Tage aktuelle Pause", icon: "leaf.fill", color: .statusGreen)
                InsightsMetricTile(value: "\(Int(personalInsights.totalAlcoholGrams.rounded())) g", label: "Reinalkohol", icon: "drop.triangle.fill", color: .statusRed)
                InsightsMetricTile(value: "\(personalInsights.totalCalories)", label: "kcal", icon: "flame.fill", color: .statusOrange)
            }
        }
        .insightsCard()
    }

    private var consumptionProfileCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            InsightsSectionHeader(icon: "waveform.path.ecg", title: "Dein Konsumprofil", subtitle: "Durchschnittswerte aus deinen Sitzungen")
            VStack(spacing: 0) {
                InsightsDetailRow(label: "Typischer Start", value: formatClock(personalInsights.typicalStartMinutesAfterMidnight), icon: "clock.fill")
                InsightsDetailRow(label: "Ø Sitzungsdauer", value: formatMinutes(personalInsights.averageSessionMinutes), icon: "timer")
                InsightsDetailRow(label: "Ø Dauer pro Drink", value: formatMinutes(personalInsights.averageDrinkMinutes), icon: "hourglass")
                InsightsDetailRow(label: "Ø Trinktempo", value: "\(oneDecimal(personalInsights.averageDrinksPerHour)) Drinks/h", icon: "speedometer")
                InsightsDetailRow(label: "Ø Peak", value: personalInsights.averagePeakBAC.permilleString, icon: "chart.line.uptrend.xyaxis")
                InsightsDetailRow(
                    label: "Höchster Peak",
                    value: highestPeakText,
                    icon: "exclamationmark.triangle.fill",
                    isLast: true
                )
            }
        }
        .insightsCard()
    }

    private var highestPeakText: String {
        guard let date = personalInsights.highestPeakDate else { return "–" }
        return "\(personalInsights.highestPeakBAC.permilleString) · \(date.formatted(.dateTime.day().month()))"
    }

    private var personalTopDrinksCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            InsightsSectionHeader(icon: "trophy.fill", title: "Deine Top 5", subtitle: "Konkrete Getränke")
            if personalInsights.topDrinks.isEmpty {
                Text("Noch nicht genug Daten")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextMuted)
            } else {
                let maximum = personalInsights.topDrinks.first?.count ?? 1
                ForEach(Array(personalInsights.topDrinks.enumerated()), id: \.element.id) { index, item in
                    InsightsRankingRow(index: index, name: item.name, subtitle: item.subtitle, count: item.count, maximum: maximum)
                }
            }
        }
        .insightsCard()
    }

    private var timeOfDayCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            InsightsSectionHeader(icon: "clock.badge", title: "Uhrzeiten", subtitle: "Wann du Getränke einträgst")
            Chart(personalInsights.hourly) { bucket in
                BarMark(x: .value("Stunde", bucket.value), y: .value("Drinks", bucket.count))
                    .foregroundStyle(Color.appAccent.gradient)
                    .cornerRadius(3)
            }
            .frame(height: 170)
            .chartXAxis {
                AxisMarks(values: [0, 4, 8, 12, 16, 20, 23]) { value in
                    AxisValueLabel {
                        if let hour = value.as(Int.self) { Text("\(hour)h") }
                    }
                    .foregroundStyle(Color.appTextDim)
                }
            }
        }
        .insightsCard()
    }

    private var weekdayCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            InsightsSectionHeader(icon: "calendar.badge.clock", title: "Wochentage", subtitle: "Dein Konsummuster")
            Chart(personalInsights.weekdays) { bucket in
                BarMark(x: .value("Tag", weekdayShort(bucket.value)), y: .value("Drinks", bucket.count))
                    .foregroundStyle(Color.statusOrange.gradient)
                    .cornerRadius(4)
            }
            .frame(height: 160)
        }
        .insightsCard()
    }
    
    // MARK: - Wochen-Vergleich
    
    private var weeklyChart: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Wochenverlauf (8 Wochen)")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            
            Chart(viewModel.weeklyDrinkCounts(drinks: drinks, weeksBack: 8), id: \.weekStart) { item in
                BarMark(
                    x: .value("Woche", item.weekStart, unit: .weekOfYear),
                    y: .value("Drinks", item.count)
                )
                .foregroundStyle(Color.appAccent.gradient)
                .cornerRadius(4)
            }
            .frame(height: 180)
            .chartXAxis {
                AxisMarks(values: .stride(by: .weekOfYear)) { _ in
                    AxisValueLabel(format: .dateTime.day().month())
                        .foregroundStyle(Color.appTextDim)
                }
            }
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
    
    // MARK: - Kategorien-Verteilung
    
    private var categoryChart: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Getränke-Kategorien (\(period.label))")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            
            Chart(viewModel.categoryTrends(drinks: periodDrinks, days: period.days ?? 36_500)) { trend in
                BarMark(
                    x: .value("Anzahl", trend.count),
                    y: .value("Kategorie", trend.category)
                )
                .foregroundStyle(Color.statusOrange.gradient)
                .annotation(position: .trailing) {
                    Text("\(trend.count)")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                }
            }
            .frame(height: 200)
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
    
    // MARK: - Lokale Trends

    private var localTrendsCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            InsightsSectionHeader(
                icon: "location.fill",
                title: trendsCity.map { "Was läuft in \($0)?" } ?? "Lokale Stadt-Trends",
                subtitle: "Anonym aggregiert · letzte 7 Tage"
            )

            if locationService.status == .denied {
                InsightsEmptyState(icon: "location.slash", text: "Standort nicht erlaubt. Aktiviere ihn in den Einstellungen.")
            } else if loadingTrends {
                HStack { Spacer(); ProgressView().tint(Color.appAccent); Spacer() }
                    .padding(.vertical, 22)
            } else if let insight = cityInsights, !insight.sampleSufficient {
                InsightsEmptyState(
                    icon: "person.3.sequence.fill",
                    text: "Für detaillierte Stadtwerte werden aus Datenschutzgründen mindestens \(insight.minimumContributors) verschiedene Teilnehmende benötigt."
                )
            } else if let insight = cityInsights {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    InsightsMetricTile(value: "\(insight.totalDrinks)", label: "Einträge", icon: "wineglass.fill", color: .appAccent)
                    InsightsMetricTile(value: insight.averageBAC?.permilleString ?? "–", label: "Ø BAC beim Loggen", icon: "gauge.with.dots.needle.50percent", color: .statusOrange)
                    InsightsMetricTile(value: formatMinutes(insight.averageSessionMinutes ?? 0), label: "Ø bisherige Session", icon: "timer", color: .appTextDim)
                    InsightsMetricTile(value: formatMinutes(insight.averageDrinkMinutes ?? 0), label: "Ø pro Drink", icon: "hourglass", color: .statusGreen)
                }

                cityTopFive(insight.topDrinks)
                cityHoursChart(insight.hourly)
                cityCategoryChart(insight.categories)

                Text("Basierend auf \(insight.contributorCount ?? 0) anonymen Beiträgern. BAC und Dauer sind Schätzwerte beim Zeitpunkt des Eintrags.")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                    .fixedSize(horizontal: false, vertical: true)
            } else if cityTrends.isEmpty {
                InsightsEmptyState(
                    icon: "chart.bar.xaxis",
                    text: trendsCity == nil ? "Standort wird ermittelt …" : "Noch keine Stadt-Daten verfügbar."
                )
            } else {
                // Compatibility presentation until city_drink_insights.sql is deployed.
                cityTopFive(cityTrends.prefix(5).map {
                    CityRankedDrink(drinkName: $0.drinkName, category: $0.category, pingCount: $0.pingCount)
                })
                Text("Für Zeit-, BAC- und Dauerwerte muss die aktualisierte city_drink_trends.sql ausgeführt werden.")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }
        }
        .insightsCard()
    }

    private func cityTopFive(_ items: [CityRankedDrink]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("TOP 5 GETRÄNKE")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            let maximum = items.first?.pingCount ?? 1
            ForEach(Array(items.prefix(5).enumerated()), id: \.element.id) { index, item in
                InsightsRankingRow(
                    index: index,
                    name: item.drinkName,
                    subtitle: DrinkCategory(rawValue: item.category)?.localizedName ?? item.category,
                    count: item.pingCount,
                    maximum: maximum
                )
            }
        }
    }

    private func cityHoursChart(_ items: [CityHourlyTrend]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("BELIEBTE UHRZEITEN")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            Chart(items) { item in
                BarMark(x: .value("Stunde", item.hour), y: .value("Einträge", item.pingCount))
                    .foregroundStyle(Color.appAccent.gradient)
                    .cornerRadius(3)
            }
            .frame(height: 150)
            .chartXAxis {
                AxisMarks(values: [0, 4, 8, 12, 16, 20, 23]) { value in
                    AxisValueLabel {
                        if let hour = value.as(Int.self) { Text("\(hour)h") }
                    }
                    .foregroundStyle(Color.appTextDim)
                }
            }
        }
    }

    private func cityCategoryChart(_ items: [CityCategoryTrend]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("KATEGORIEN")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            ForEach(items.prefix(5)) { item in
                HStack {
                    Text(DrinkCategory(rawValue: item.category)?.localizedName ?? item.category)
                        .font(.appCaption)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Text("\(item.pingCount)")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appTextDim)
                }
            }
        }
    }

    // MARK: - Stimmungs-Korrelation
    
    private func moodChart(profile: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Morgen danach")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)

            let correlations = viewModel.getMoodCorrelations(drinks: periodDrinks, notes: notes, profile: profile)

            if correlations.isEmpty {
                Text("Bewerte morgens deine Nacht, um hier zu sehen, wie sich dein Promillewert auf den nächsten Tag auswirkt.")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextMuted)
            } else {
                let maxAvg = max(correlations.map(\.averagePeakBAC).max() ?? 0, 0.01)
                VStack(spacing: 10) {
                    ForEach(correlations) { corr in
                        MoodCorrelationRow(
                            mood: DayMood(rawValue: corr.moodScore) ?? .neutral,
                            averagePeakBAC: corr.averagePeakBAC,
                            nights: corr.nights,
                            fraction: corr.averagePeakBAC / maxAvg
                        )
                    }
                }

                if let insight = viewModel.moodInsight(drinks: periodDrinks, notes: notes, profile: profile) {
                    Text("Deine positiv bewerteten Morgen folgten auf Abende mit im Schnitt \(insight.goodAvg.permilleString), die negativ bewerteten auf \(insight.badAvg.permilleString).")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
}

private enum InsightsPeriod: String, CaseIterable, Identifiable {
    case days7, days30, days90, all
    var id: String { rawValue }
    var days: Int? {
        switch self {
        case .days7: return 7
        case .days30: return 30
        case .days90: return 90
        case .all: return nil
        }
    }
    var label: String {
        switch self {
        case .days7: return "7 Tage"
        case .days30: return "30 Tage"
        case .days90: return "90 Tage"
        case .all: return "Gesamt"
        }
    }
}

private extension TrendsView {
    func oneDecimal(_ value: Double) -> String {
        String(format: "%.1f", locale: germanLocale, value)
    }

    func formatMinutes(_ value: Double) -> String {
        guard value > 0 else { return "–" }
        let total = Int(value.rounded())
        if total < 60 { return "\(total) min" }
        return String(format: "%d:%02d h", total / 60, total % 60)
    }

    func formatClock(_ minutes: Int?) -> String {
        guard let minutes else { return "–" }
        return String(format: "%02d:%02d Uhr", minutes / 60, minutes % 60)
    }

    func weekdayShort(_ index: Int) -> String {
        ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"][min(6, max(0, index))]
    }
}

private struct InsightsSectionHeader: View {
    let icon: String
    let title: String
    let subtitle: String
    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.appAccent)
                .frame(width: 34, height: 34)
                .background(Color.appAccent.opacity(0.11))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.appHeadline).foregroundStyle(Color.appText)
                Text(subtitle).font(.appMicro).foregroundStyle(Color.appTextMuted)
            }
        }
    }
}

private struct InsightsMetricTile: View {
    let value: String
    let label: String
    let icon: String
    let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold)).foregroundStyle(color)
            Text(value)
                .font(.system(size: 21, weight: .bold, design: .rounded))
                .foregroundStyle(Color.appText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label).font(.appMicro).foregroundStyle(Color.appTextDim).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(13)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 13))
    }
}

private struct InsightsDetailRow: View {
    let label: String
    let value: String
    let icon: String
    var isLast = false
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.appAccent).frame(width: 20)
                Text(label).font(.appCaption).foregroundStyle(Color.appTextDim)
                Spacer()
                Text(value).font(.appCaptionBold).foregroundStyle(Color.appText).monospacedDigit()
            }
            .padding(.vertical, 11)
            if !isLast { Divider().overlay(Color.appBorder) }
        }
    }
}

private struct InsightsRankingRow: View {
    let index: Int
    let name: String
    let subtitle: String
    let count: Int
    let maximum: Int
    var body: some View {
        HStack(spacing: 10) {
            Text("\(index + 1)")
                .font(.system(size: 12, weight: .black, design: .rounded))
                .foregroundStyle(index == 0 ? Color.appBackground : Color.appTextDim)
                .frame(width: 26, height: 26)
                .background(index == 0 ? Color.appAccent : Color.appBorder.opacity(0.45))
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.appCaptionBold).foregroundStyle(Color.appText).lineLimit(1)
                Text(subtitle).font(.appMicro).foregroundStyle(Color.appTextMuted)
            }
            Spacer()
            GeometryReader { geo in
                Capsule()
                    .fill(Color.appAccent.opacity(0.16))
                    .overlay(alignment: .leading) {
                        Capsule().fill(Color.appAccent)
                            .frame(width: geo.size.width * CGFloat(count) / CGFloat(max(maximum, 1)))
                    }
            }
            .frame(width: 58, height: 5)
            Text("\(count)").font(.appCaptionBold).foregroundStyle(Color.appTextDim).frame(width: 26, alignment: .trailing)
        }
    }
}

private struct InsightsEmptyState: View {
    let icon: String
    let text: String
    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: icon).foregroundStyle(Color.appTextMuted)
            Text(text).font(.appCaption).foregroundStyle(Color.appTextMuted).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 8)
    }
}

private struct InsightsCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
}

private extension View {
    func insightsCard() -> some View { modifier(InsightsCardModifier()) }
}

// MARK: - MoodCorrelationRow
//
// One rated mood: label, night count and the average peak promille of those
// nights, with a thin bar so the good-vs-bad gap is visible at a glance.

private struct MoodCorrelationRow: View {
    let mood: DayMood
    let averagePeakBAC: Double
    let nights: Int
    let fraction: Double

    private var barColor: Color {
        switch mood {
        case .happy, .proud:  return .statusGreen
        case .regret:         return .statusOrange
        case .terrible:       return .statusRed
        case .neutral:        return .appTextMuted
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(alignment: .firstTextBaseline) {
                Text("\(mood.emoji) \(mood.label)")
                    .font(.appCaption)
                    .foregroundStyle(Color.appText)
                Text(nights == 1 ? "1 Nacht" : "\(nights) Nächte")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                Spacer()
                Text(averagePeakBAC.permilleString)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appText)
                    .monospacedDigit()
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.appBorder.opacity(0.5))
                    Capsule()
                        .fill(barColor)
                        .frame(width: max(4, geo.size.width * min(max(fraction, 0), 1)))
                }
            }
            .frame(height: 4)
        }
    }
}
