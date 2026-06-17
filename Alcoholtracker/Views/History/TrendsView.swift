import SwiftUI
import SwiftData
import Charts

// MARK: - TrendsView

struct TrendsView: View {
    // All charts here look at most 30 days / 4 weeks back, so we only page in
    // the last 60 days instead of the entire drinking history. The bound is
    // computed once at view creation; reopening the sheet rebuilds it.
    @Query private var drinks: [Drink]
    @Query private var notes: [DayNote]
    @Query private var profiles: [UserProfile]
    @Environment(\.dismiss) private var dismiss
    @Environment(SupabaseService.self) private var supabase
    @Environment(LocationService.self) private var locationService

    init() {
        let cutoff = Calendar.current.date(byAdding: .day, value: -60, to: Date()) ?? .distantPast
        _drinks = Query(
            filter: #Predicate { $0.timestamp >= cutoff },
            sort: \.timestamp
        )
    }

    @State private var viewModel = HistoryViewModel()
    @State private var cityTrends: [CityDrinkTrend] = []
    @State private var trendsCity: String?
    @State private var loadingTrends = false

    private var profile: UserProfile? { profiles.first }
    
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
            cityTrends = (try? await supabase.fetchCityTrends(city: city)) ?? []
            loadingTrends = false
        }
    }
    
    // MARK: - Wochen-Vergleich
    
    private var weeklyChart: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Letzte 4 Wochen (Drinks)")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            
            Chart(viewModel.weeklyDrinkCounts(drinks: drinks, weeksBack: 4), id: \.weekStart) { item in
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
            Text("Getränke-Kategorien (30 Tage)")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            
            Chart(viewModel.categoryTrends(drinks: drinks, days: 30)) { trend in
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
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "location.fill")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                Text(trendsCity.map { "Trends in \($0)" } ?? "Lokale Trends")
                    .font(.appHeadline)
                    .foregroundStyle(Color.appText)
            }

            if locationService.status == .denied {
                HStack(spacing: 10) {
                    Image(systemName: "location.slash")
                        .foregroundStyle(Color.appTextMuted)
                    Text("Standort nicht erlaubt. Aktiviere ihn in den Einstellungen.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            } else if loadingTrends {
                HStack {
                    Spacer()
                    ProgressView().tint(Color.appAccent)
                    Spacer()
                }
                .padding(.vertical, 12)
            } else if cityTrends.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "chart.bar.xaxis")
                        .foregroundStyle(Color.appTextMuted)
                    Text(trendsCity == nil
                         ? "Standort wird ermittelt..."
                         : "Noch keine Trend-Daten fuer \(trendsCity!) in den letzten 24 Stunden.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 4)
            } else {
                let maxCount = Double(cityTrends.first?.pingCount ?? 1)
                VStack(spacing: 10) {
                    ForEach(Array(cityTrends.prefix(7).enumerated()), id: \.element.id) { index, trend in
                        HStack(spacing: 10) {
                            Text("\(index + 1)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Color.appTextMuted)
                                .frame(width: 16, alignment: .center)
                            Image(systemName: DrinkCategory(rawValue: trend.category)?.symbolName ?? "wineglass")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.appAccent)
                                .frame(width: 20)
                            Text(trend.drinkName)
                                .font(.appCaption)
                                .foregroundStyle(Color.appText)
                                .lineLimit(1)
                            Spacer()
                            GeometryReader { geo in
                                let fraction = Double(trend.pingCount) / maxCount
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(Color.appAccent.opacity(0.18))
                                        .frame(width: geo.size.width, height: 6)
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(Color.appAccent)
                                        .frame(width: geo.size.width * fraction, height: 6)
                                }
                            }
                            .frame(width: 64, height: 6)
                            Text("\(trend.pingCount)")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextDim)
                                .frame(width: 22, alignment: .trailing)
                        }
                    }
                }
            }

            Text("Letzte 24 Stunden")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    // MARK: - Stimmungs-Korrelation
    
    private func moodChart(profile: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Stimmung vs. Höchstpromille")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            
            let correlations = viewModel.getMoodCorrelations(drinks: drinks, notes: notes, profile: profile)
            
            if correlations.isEmpty {
                Text("Protokolliere deine Morgenstimmung, um hier Zusammenhänge mit deinem Promillewert zu sehen.")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextMuted)
            } else {
                Chart(correlations) { corr in
                    PointMark(
                        x: .value("Stimmung", DayMood(rawValue: corr.moodScore)?.emoji ?? ""),
                        y: .value("Ø Peak BAC", corr.averagePeakBAC)
                    )
                    .symbolSize(400)
                    .foregroundStyle(Color.statusRed.opacity(0.8))
                }
                .frame(height: 180)
                .chartYAxis {
                    AxisMarks { val in
                        if let v = val.as(Double.self) {
                            AxisValueLabel("\(String(format: "%.1f", v)) ‰")
                                .foregroundStyle(Color.appTextDim)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
}