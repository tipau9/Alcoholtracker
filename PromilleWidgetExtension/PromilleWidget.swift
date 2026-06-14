import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Shared constants

private let kAppGroup          = "group.com.tipau.Alcoholtracker"
private let kKeyBAC            = "currentBAC"
private let kKeyRate           = "eliminationRate"
private let kKeyDate           = "bacLastUpdated"
private let kKeyWarningThreshold = "warningThreshold"

// MARK: - Inline colors (mirrors Colors.swift without importing main target)

private let cBackground = Color(red: 0.039, green: 0.031, blue: 0.027)
private let cCard       = Color(red: 0.074, green: 0.063, blue: 0.051)
private let cAccent     = Color(red: 0.788, green: 0.502, blue: 0.184)
private let cText       = Color(red: 0.941, green: 0.910, blue: 0.824)
private let cTextDim    = Color(red: 0.659, green: 0.620, blue: 0.537)
private let cBorder     = Color(red: 0.165, green: 0.129, blue: 0.110)

// Colors follow the user's custom thresholds from SharedStatusConfig so the
// widget always agrees with the in-app status.
private func wStatusColor(bac: Double, config: SharedStatusConfig) -> Color {
    switch bac {
    case ..<config.tipsyThreshold:   return Color(red: 0.420, green: 0.608, blue: 0.431)
    case ..<config.drunkThreshold:   return Color(red: 0.898, green: 0.757, blue: 0.345)
    case ..<config.carefulThreshold: return Color(red: 0.898, green: 0.627, blue: 0.333)
    default:                         return Color(red: 0.898, green: 0.314, blue: 0.314)
    }
}

private func wFormatHours(_ hours: Double) -> String {
    guard hours > 0 else { return "Jetzt" }
    let totalMin = Int(hours * 60)
    let h = totalMin / 60
    let m = totalMin % 60
    if h == 0 { return "\(m) min" }
    if m == 0 { return "\(h) h" }
    return "\(h) h \(m) min"
}

// MARK: - Timeline entry

struct PromilleEntry: TimelineEntry {
    let date: Date
    let bac: Double
    let eliminationRate: Double
    let lastUpdated: Date
    let warningThreshold: Double
    var statusConfig: SharedStatusConfig = .fallback

    var statusColor: Color { wStatusColor(bac: bac, config: statusConfig) }
    var statusLabel: String { statusConfig.label(forBAC: bac) }

    func hoursUntil(_ threshold: Double) -> Double? {
        guard bac > threshold, eliminationRate > 0 else { return nil }
        return (bac - threshold) / eliminationRate
    }
}

// MARK: - Timeline provider

struct PromilleProvider: TimelineProvider {

    private var groupDefaults: UserDefaults {
        guard let ud = UserDefaults(suiteName: kAppGroup) else {
            assertionFailure("App Group UserDefaults nicht verfügbar — Entitlements prüfen")
            return .standard
        }
        return ud
    }

    func placeholder(in context: Context) -> PromilleEntry {
        PromilleEntry(date: Date(), bac: 0.82, eliminationRate: 0.15, lastUpdated: Date(), warningThreshold: 0.5)
    }

    func getSnapshot(in context: Context, completion: @escaping (PromilleEntry) -> Void) {
        completion(entry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PromilleEntry>) -> Void) {
        let now = Date()
        var entries: [PromilleEntry] = []
        var minuteOffset = 0
        while minuteOffset <= 360 {
            let entryDate = now.addingTimeInterval(Double(minuteOffset) * 60)
            let e = entry(at: entryDate)
            entries.append(e)
            if e.bac == 0 { break }
            minuteOffset += 15
        }
        if entries.isEmpty {
            entries = [entry(at: now)]
        }

        let bacNow = entries.first?.bac ?? 0
        let refreshInterval: TimeInterval = bacNow > 0 ? 30 * 60 : 4 * 3600
        let refreshAfter = now.addingTimeInterval(refreshInterval)
        completion(Timeline(entries: entries, policy: .after(refreshAfter)))
    }

    private func entry(at date: Date) -> PromilleEntry {
        let defaults         = groupDefaults
        let rawBAC           = defaults.double(forKey: kKeyBAC)
        let rawRate          = defaults.double(forKey: kKeyRate)
        let rate             = rawRate > 0 ? max(0.05, min(0.30, rawRate)) : 0.15
        let lastUpdated      = (defaults.object(forKey: kKeyDate) as? Date) ?? date
        let warningThreshold = defaults.object(forKey: kKeyWarningThreshold) as? Double ?? 0.5
        return PromilleEntry(
            date: date,
            bac: projectedBAC(at: date, fallbackBAC: rawBAC, fallbackUpdated: lastUpdated, rate: rate),
            eliminationRate: rate,
            lastUpdated: lastUpdated,
            warningThreshold: warningThreshold,
            statusConfig: SharedStateStore.readStatusConfig()
        )
    }

    // BAC at a moment, preferring the real curve written by the app (covers the
    // absorption phase, so rising values are shown correctly). Falls back to
    // linear elimination from the last known point or the legacy scalar snapshot.
    private func projectedBAC(at date: Date, fallbackBAC: Double, fallbackUpdated: Date, rate: Double) -> Double {
        let curve = SharedStateStore.readBACCurve()
        guard let first = curve.first, let last = curve.last else {
            let elapsedH = date.timeIntervalSince(fallbackUpdated) / 3600.0
            return max(0, fallbackBAC - rate * elapsedH)
        }
        if date <= first.date { return max(0, first.bac) }
        if date >= last.date {
            let elapsedH = date.timeIntervalSince(last.date) / 3600.0
            return max(0, last.bac - rate * elapsedH)
        }
        for i in 1..<curve.count where curve[i].date >= date {
            let a = curve[i - 1], b = curve[i]
            let span = b.date.timeIntervalSince(a.date)
            guard span > 0 else { return max(0, b.bac) }
            let t = date.timeIntervalSince(a.date) / span
            return max(0, a.bac + (b.bac - a.bac) * t)
        }
        return max(0, last.bac)
    }
}

// MARK: - Entry view router

struct PromilleWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PromilleEntry

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                WSmallView(entry: entry)
            case .systemMedium:
                WMediumView(entry: entry)
            case .systemLarge:
                WLargeView(entry: entry)
            case .accessoryCircular:
                WCircularView(entry: entry)
            case .accessoryRectangular:
                WRectangularView(entry: entry)
            default:
                WSmallView(entry: entry)
            }
        }
        .widgetURL(URL(string: "promille://open"))
    }
}

// MARK: - Small widget (systemSmall)

private struct WSmallView: View {
    let entry: PromilleEntry
    private var hasDrinks: Bool { !SharedStateStore.readSession().drinks.isEmpty }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                Image(systemName: "drop.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(cAccent)
                Text("promille.")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(cText)
            }
            Spacer()
            Text(String(format: "%.2f", entry.bac))
                .font(.system(size: 44, weight: .light, design: .serif))
                .foregroundStyle(entry.statusColor)
                .monospacedDigit()
            Text("‰")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(entry.statusColor)
            Spacer()
            // Interactive button (iOS 17+): repeat last drink
            HStack(spacing: 6) {
                if entry.bac < 0.01 && !hasDrinks {
                    // Empty state: invite instead of a meaningless status pill
                    HStack(spacing: 4) {
                        Image(systemName: "plus.circle")
                            .font(.system(size: 10, weight: .semibold))
                        Text("Tippe zum Loggen")
                            .font(.system(size: 11, weight: .medium))
                    }
                    .foregroundStyle(cTextDim)
                } else {
                    Text(entry.statusLabel)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(entry.statusColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(entry.statusColor.opacity(0.15))
                        .clipShape(Capsule())
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                Spacer(minLength: 0)
                if hasDrinks {
                    Button(intent: AddLastDrinkIntent()) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(cAccent)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(cBackground, for: ContainerBackgroundPlacement.widget)
    }
}

// MARK: - Medium widget (systemMedium)

private struct WMediumView: View {
    let entry: PromilleEntry

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 5) {
                    Image(systemName: "drop.fill")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(cAccent)
                    Text("promille.")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(cText)
                }
                Spacer()
                Text(String(format: "%.2f", entry.bac))
                    .font(.system(size: 52, weight: .light, design: .serif))
                    .foregroundStyle(entry.statusColor)
                    .monospacedDigit()
                Text("‰")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(entry.statusColor)
                Spacer()
                Text(entry.statusLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(entry.statusColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(entry.statusColor.opacity(0.15))
                    .clipShape(Capsule())
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxHeight: .infinity)

            Rectangle()
                .fill(cBorder)
                .frame(width: 0.5)
                .padding(.vertical, 10)
                .padding(.horizontal, 14)

            VStack(alignment: .leading, spacing: 14) {
                WTimerRow(
                    icon: "checkmark.circle.fill",
                    label: "Nüchtern",
                    value: entry.hoursUntil(0.01).map { wFormatHours($0) } ?? "Jetzt",
                    color: Color(red: 0.420, green: 0.608, blue: 0.431)
                )
                WTimerRow(
                    icon: "car.fill",
                    label: "Fahrbereit",
                    value: entry.hoursUntil(entry.warningThreshold).map { wFormatHours($0) } ?? "Jetzt",
                    color: cAccent
                )
            }
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .padding(14)
        .containerBackground(cBackground, for: ContainerBackgroundPlacement.widget)
    }
}

private struct WTimerRow: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(color)
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(cTextDim)
            }
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(cText)
                .monospacedDigit()
        }
    }
}

// MARK: - Large widget (systemLarge)

private struct WLargeView: View {
    let entry: PromilleEntry

    private var barFraction: Double { min(1, entry.bac / 2.5) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "drop.fill")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(cAccent)
                    Text("promille.")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(cText)
                }
                Spacer()
                Text(entry.statusLabel)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(entry.statusColor)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(entry.statusColor.opacity(0.15))
                    .clipShape(Capsule())
            }

            Spacer()

            Text(String(format: "%.2f", entry.bac))
                .font(.system(size: 72, weight: .light, design: .serif))
                .foregroundStyle(entry.statusColor)
                .monospacedDigit()
            Text("Promille (‰)")
                .font(.system(size: 12, weight: .regular))
                .foregroundStyle(entry.statusColor.opacity(0.7))

            Spacer()

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(cBorder)
                        .frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(entry.statusColor)
                        .frame(width: geo.size.width * barFraction, height: 8)
                }
            }
            .frame(height: 8)

            Spacer()

            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Bis nüchtern")
                        .font(.system(size: 11))
                        .foregroundStyle(cTextDim)
                    Text(entry.hoursUntil(0.01).map { wFormatHours($0) } ?? "Jetzt")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(cText)
                        .monospacedDigit()
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Bis fahrbereit")
                        .font(.system(size: 11))
                        .foregroundStyle(cTextDim)
                    Text(entry.hoursUntil(entry.warningThreshold).map { wFormatHours($0) } ?? "Jetzt")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(cText)
                        .monospacedDigit()
                }
            }
        }
        .padding(16)
        .containerBackground(cBackground, for: ContainerBackgroundPlacement.widget)
    }
}

// MARK: - Circular lock screen (accessoryCircular)

private struct WCircularView: View {
    let entry: PromilleEntry

    var body: some View {
        VStack(spacing: 0) {
            Text(String(format: "%.2f", entry.bac))
                .font(.system(size: 14, weight: .semibold, design: .serif))
                .foregroundStyle(entry.statusColor)
                .monospacedDigit()
            Text("‰")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(entry.statusColor)
        }
        .containerBackground(cBackground, for: ContainerBackgroundPlacement.widget)
    }
}

// MARK: - Rectangular lock screen (accessoryRectangular)

private struct WRectangularView: View {
    let entry: PromilleEntry

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "drop.fill")
                .font(.system(size: 18, weight: .light))
                .foregroundStyle(entry.statusColor)
            VStack(alignment: .leading, spacing: 1) {
                Text(String(format: "%.2f ‰", entry.bac))
                    .font(.system(size: 16, weight: .semibold, design: .serif))
                    .foregroundStyle(entry.statusColor)
                    .monospacedDigit()
                Text(entry.statusLabel)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(cTextDim)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(cBackground, for: ContainerBackgroundPlacement.widget)
    }
}

// MARK: - Widget configuration

struct PromilleWidget: Widget {
    private let kind = "PromilleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PromilleProvider()) { entry in
            PromilleWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("promille.")
        .description("Aktueller Promillewert auf einen Blick.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .systemLarge,
            .accessoryCircular,
            .accessoryRectangular,
        ])
    }
}

// MARK: - Widget bundle

@main
struct PromilleWidgetBundle: WidgetBundle {
    var body: some Widget {
        PromilleWidget()
        PromilleLockScreenWidget()
        PromilleLiveActivity()
    }
}
