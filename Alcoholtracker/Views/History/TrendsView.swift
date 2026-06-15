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

    init() {
        let cutoff = Calendar.current.date(byAdding: .day, value: -60, to: Date()) ?? .distantPast
        _drinks = Query(
            filter: #Predicate { $0.timestamp >= cutoff },
            sort: \.timestamp
        )
    }
    
    @State private var viewModel = HistoryViewModel()
    
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