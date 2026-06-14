import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Lock Screen / notification banner view

struct LALockScreenView: View {
    let context: ActivityViewContext<PromilleActivityAttributes>

    private var bac: Double { context.state.bac }
    private var rate: Double { context.state.eliminationRate }

    var body: some View {
        let warningThreshold = context.state.warningThreshold

        HStack(alignment: .center, spacing: 16) {

            // Left column: big BAC + status label
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .top, spacing: 2) {
                    Text(laFormatBAC(bac))
                        .font(.system(size: 40, weight: .light, design: .serif))
                        .foregroundStyle(laStatusColor(bac))
                        .monospacedDigit()
                    Text("‰")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(laStatusColor(bac))
                        .padding(.top, 7)
                }
                Text(laStatusLabel(bac))
                    .font(.system(size: 9, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(laStatusColor(bac))
                if let clock = laSoberClock(bac, rate: rate, since: context.state.lastUpdated) {
                    Text("nüchtern ~ \(clock)")
                        .font(.system(size: 10))
                        .foregroundStyle(Color(red: 0.659, green: 0.620, blue: 0.537))
                }
            }

            Rectangle()
                .fill(Color(red: 0.165, green: 0.129, blue: 0.110))
                .frame(width: 0.5)
                .padding(.vertical, 4)

            // Right column: two countdown rows
            VStack(alignment: .leading, spacing: 12) {
                LATimerRow(
                    icon: "checkmark.circle.fill",
                    label: "Nüchtern",
                    value: laCountdown(bac, threshold: 0.01, rate: rate),
                    color: Color(red: 0.420, green: 0.608, blue: 0.431)
                )
                LATimerRow(
                    icon: "car.fill",
                    label: "Fahrbereit",
                    value: laCountdown(bac, threshold: warningThreshold, rate: rate),
                    color: Color(red: 0.788, green: 0.502, blue: 0.184)
                )
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

private struct LATimerRow: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: 10))
                    .foregroundStyle(Color(red: 0.659, green: 0.620, blue: 0.537))
                Text(value)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(red: 0.941, green: 0.910, blue: 0.824))
                    .monospacedDigit()
            }
        }
    }
}

// MARK: - Live Activity widget

struct PromilleLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PromilleActivityAttributes.self) { context in
            LALockScreenView(context: context)
                .activityBackgroundTint(Color(red: 0.039, green: 0.031, blue: 0.027))
                .activitySystemActionForegroundColor(Color(red: 0.941, green: 0.910, blue: 0.824))
                .widgetURL(URL(string: "promille://open"))
        } dynamicIsland: { context in
            let bac = context.state.bac
            let rate = context.state.eliminationRate

            return DynamicIsland {
                // Expanded: shown when user long-presses the island
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 4) {
                            Image(systemName: "drop.fill")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(Color(red: 0.788, green: 0.502, blue: 0.184))
                            Text("promille.")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color(red: 0.941, green: 0.910, blue: 0.824))
                        }
                        HStack(alignment: .top, spacing: 2) {
                            Text(laFormatBAC(bac))
                                .font(.system(size: 34, weight: .light, design: .serif))
                                .foregroundStyle(laStatusColor(bac))
                                .monospacedDigit()
                            Text("‰")
                                .font(.system(size: 12))
                                .foregroundStyle(laStatusColor(bac))
                                .padding(.top, 5)
                        }
                    }
                    .padding(.leading, 4)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 6) {
                        Text(laStatusLabel(bac))
                            .font(.system(size: 9, weight: .semibold))
                            .tracking(1)
                            .foregroundStyle(laStatusColor(bac))
                        Text("\(context.state.drinkCount) Drinks")
                            .font(.system(size: 11))
                            .foregroundStyle(Color(red: 0.659, green: 0.620, blue: 0.537))
                    }
                    .padding(.trailing, 4)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 28) {
                        LAExpandedTimer(
                            icon: "checkmark.circle.fill",
                            label: "Nüchtern",
                            value: laCountdown(bac, threshold: 0.01, rate: rate),
                            color: Color(red: 0.420, green: 0.608, blue: 0.431)
                        )
                        LAExpandedTimer(
                            icon: "car.fill",
                            label: "Fahrbereit",
                            value: laCountdown(bac, threshold: context.state.warningThreshold, rate: rate),
                            color: Color(red: 0.788, green: 0.502, blue: 0.184)
                        )
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                }

            } compactLeading: {
                // Drop icon removed: BAC number + "‰" split across leading/trailing
                // is already tight on compact island — icon caused overflow under notch.
                Text(laFormatBAC(bac))
                    .font(.system(size: 14, weight: .semibold, design: .serif))
                    .foregroundStyle(laStatusColor(bac))
                    .monospacedDigit()
                    .padding(.leading, 2)

            } compactTrailing: {
                Text("‰")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(laStatusColor(bac))

            } minimal: {
                Text(laFormatBACShort(bac))
                    .font(.system(size: 13, weight: .semibold, design: .serif))
                    .foregroundStyle(laStatusColor(bac))
                    .monospacedDigit()
            }
            .widgetURL(URL(string: "promille://open"))
            .keylineTint(laStatusColor(bac))
        }
    }
}

private struct LAExpandedTimer: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: 9))
                    .foregroundStyle(Color(red: 0.659, green: 0.620, blue: 0.537))
                Text(value)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(red: 0.941, green: 0.910, blue: 0.824))
                    .monospacedDigit()
            }
        }
    }
}

// MARK: - File-local helpers (mirrors PromilleWidget.swift without private)

private func laStatusColor(_ bac: Double) -> Color {
    switch bac {
    case ..<0.01: return Color(red: 0.420, green: 0.608, blue: 0.431)
    case ..<0.3:  return Color(red: 0.898, green: 0.757, blue: 0.345)
    case ..<0.8:  return Color(red: 0.898, green: 0.627, blue: 0.333)
    default:      return Color(red: 0.898, green: 0.314, blue: 0.314)
    }
}

private func laStatusLabel(_ bac: Double) -> String {
    switch bac {
    case ..<0.01: return "NÜCHTERN"
    case ..<0.3:  return "LEICHT"
    case ..<0.8:  return "BESCHWIPST"
    case ..<1.5:  return "AUFPASSEN"
    default:      return "GEFÄHRLICH"
    }
}

private func laFormatBAC(_ value: Double) -> String {
    String(format: "%.2f", value).replacingOccurrences(of: ".", with: ",")
}

private func laFormatBACShort(_ value: Double) -> String {
    if value < 0.01 { return "0,0" }
    return String(format: "%.1f", value).replacingOccurrences(of: ".", with: ",")
}

// Absolute clock time the user is expected to drop below 0.01 ‰. Computed from
// the snapshot (lastUpdated + remaining hours), so it stays correct on the lock
// screen even without further push updates, unlike a live-drifting countdown.
private func laSoberClock(_ bac: Double, rate: Double, since: Date) -> String? {
    guard bac > 0.01, rate > 0 else { return nil }
    let seconds = ((bac - 0.01) / rate) * 3600
    let soberAt = since.addingTimeInterval(seconds)
    let fmt = DateFormatter()
    fmt.locale = Locale(identifier: "de_DE")
    fmt.dateFormat = "HH:mm"
    return fmt.string(from: soberAt)
}

private func laCountdown(_ bac: Double, threshold: Double, rate: Double) -> String {
    guard bac > threshold, rate > 0 else { return "Jetzt" }
    let totalMin = Int(((bac - threshold) / rate) * 60)
    let h = totalMin / 60
    let m = totalMin % 60
    if h > 0 && m > 0 { return "\(h)h \(m)m" }
    if h > 0 { return "\(h)h" }
    return "\(m)m"
}
