import SwiftUI
import Charts

// MARK: - FullScreenBACChart (Feature 8)
// Full-screen interactive BAC chart, opened by tapping the chart on HomeView.

struct FullScreenBACChart: View {

    let session: SessionViewModel
    let profile: UserProfile?

    @Environment(\.dismiss) private var dismiss
    @State private var selectedPoint: BACCalculator.BACPoint?

    private var curveData: [BACCalculator.BACPoint] {
        session.bacCurve24h
    }

    // Legal limit for this user: 0,0 ‰ in der Probezeit, sonst 0,5 ‰.
    private var drivingLimit: Double { profile?.drivingLimit ?? 0.5 }

    private var limitDate: Date? {
        guard let p = profile else { return nil }
        return BACCalculator.hoursUntilBAC(
            drivingLimit,
            drinks: session.drinks,
            profile: p,
            stomachStatus: session.stomachStatus
        ).map { Date().addingTimeInterval($0 * 3600) }
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                if let pt = selectedPoint {
                    selectedOverlay(pt)
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                }
                chart
                    .padding(.horizontal, 16)
                    .padding(.top, selectedPoint == nil ? 16 : 8)
                    .frame(maxHeight: .infinity)
                legend
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("BAC-Verlauf")
                    .font(.system(size: 26, weight: .light, design: .serif))
                    .foregroundStyle(Color.appText)
                Text("24-Stunden-Ansicht")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Color.appTextDim)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 4)
    }

    private func selectedOverlay(_ pt: BACCalculator.BACPoint) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(String(format: "%.2f", pt.bac).replacingOccurrences(of: ".", with: ","))
                .font(.system(size: 42, weight: .light, design: .serif))
                .foregroundStyle(Color.appAccent)
                .monospacedDigit()
            Text("‰")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.appAccent.opacity(0.7))
            Spacer()
            Text(pt.date.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute()))
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                .foregroundStyle(Color.appTextDim)
        }
    }

    // Extracted so the ForEach resolves unambiguously as ChartContent (inline in
    // a Chart {} the type-checker can otherwise pick MapContentBuilder and fail).
    @ChartContentBuilder
    private func curveMarks(_ points: [BACCalculator.BACPoint]) -> some ChartContent {
        ForEach(points) { pt in
            AreaMark(
                x: .value("Zeit", pt.date),
                y: .value("BAC", pt.bac)
            )
            .foregroundStyle(
                LinearGradient(
                    colors: [Color.appAccent.opacity(0.35), Color.appAccent.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            LineMark(
                x: .value("Zeit", pt.date),
                y: .value("BAC", pt.bac)
            )
            .foregroundStyle(Color.appAccent)
            .lineStyle(StrokeStyle(lineWidth: 2.5))
        }
    }

    private var chart: some View {
        Chart {
            curveMarks(curveData)

            // In der Probezeit liegt die Grenze bei 0,0 ‰ (auf der Achse), eine
            // Linie dort wäre nutzlos, deshalb nur bei einer echten Grenze zeigen.
            if drivingLimit > 0 {
                RuleMark(y: .value("Fahrgrenze", drivingLimit))
                    .foregroundStyle(Color.statusRed.opacity(0.55))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 4]))
                    .annotation(position: .topLeading) {
                        Text("\(String(format: "%.1f", drivingLimit).replacingOccurrences(of: ".", with: ",")) Promille")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundStyle(Color.statusRed.opacity(0.8))
                    }
            }

            RuleMark(x: .value("Jetzt", Date()))
                .foregroundStyle(Color.appTextDim.opacity(0.4))
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))

            if let pt = selectedPoint {
                PointMark(
                    x: .value("Zeit", pt.date),
                    y: .value("BAC", pt.bac)
                )
                .foregroundStyle(Color.appAccent)
                .symbolSize(80)
            }
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: .hour, count: 3)) { value in
                AxisGridLine().foregroundStyle(Color.appBorder.opacity(0.5))
                AxisValueLabel {
                    if let d = value.as(Date.self) {
                        Text(d.formatted(.dateTime.hour(.twoDigits(amPM: .omitted))))
                            .font(.system(size: 10))
                            .foregroundStyle(Color.appTextDim)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks { value in
                AxisGridLine().foregroundStyle(Color.appBorder.opacity(0.3))
                AxisValueLabel {
                    if let v = value.as(Double.self) {
                        Text(String(format: "%.1f", v))
                            .font(.system(size: 10))
                            .foregroundStyle(Color.appTextDim)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geo in
                Rectangle()
                    .fill(Color.clear)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { drag in
                                let x = drag.location.x - geo.frame(in: .local).minX
                                if let date: Date = proxy.value(atX: x) {
                                    selectedPoint = curveData.min { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) }
                                }
                            }
                            .onEnded { _ in selectedPoint = nil }
                    )
            }
        }
    }

    private var legend: some View {
        HStack(spacing: 20) {
            HStack(spacing: 5) {
                Circle().fill(Color.appAccent).frame(width: 7, height: 7)
                Text("Verlauf").font(.system(size: 11)).foregroundStyle(Color.appTextDim)
            }
            if drivingLimit > 0 {
                HStack(spacing: 5) {
                    Circle().fill(Color.statusRed).frame(width: 7, height: 7)
                    Text("\(String(format: "%.1f", drivingLimit).replacingOccurrences(of: ".", with: ",")) Promille")
                        .font(.system(size: 11)).foregroundStyle(Color.appTextDim)
                }
            }
            HStack(spacing: 5) {
                Rectangle().fill(Color.appTextDim.opacity(0.5)).frame(width: 14, height: 1)
                Text("Jetzt").font(.system(size: 11)).foregroundStyle(Color.appTextDim)
            }
            Spacer()
        }
    }
}
