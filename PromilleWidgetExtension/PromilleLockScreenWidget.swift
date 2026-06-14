import AppIntents
import Foundation
import SwiftUI
import WidgetKit

// MARK: - AddLastDrinkIntent (B6)
// Writes a pending drink to the App Group so the main app can persist it.
// TARGET MEMBERSHIP: PromilleWidgetExtension

struct AddLastDrinkIntent: AppIntent {
    static var title: LocalizedStringResource = "Letzten Drink nochmal"
    static var description = IntentDescription("Letzten Drink erneut hinzufügen")

    func perform() async throws -> some IntentResult {
        let session = SharedStateStore.readSession()
        guard let last = session.drinks.last else { return .result() }

        let pending = PendingWidgetDrink(
            id: UUID(),
            name: last.name,
            volume: last.volume,
            abv: last.abv,
            calories: last.calories,
            iconName: last.iconName,
            categoryRaw: last.categoryRaw,
            timestamp: Date()
        )
        SharedStateStore.appendPendingDrink(pending)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

// MARK: - Lock Screen Widget View (accessoryRectangular)

private struct PromilleLockScreenWidgetView: View {
    let entry: PromilleEntry

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(String(format: "%.2f", entry.bac))
                    .font(.system(size: 20, weight: .semibold, design: .serif))
                    .foregroundStyle(entry.statusColor)
                    .monospacedDigit()
                Text("‰")
                    .font(.system(size: 10))
                    .foregroundStyle(entry.statusColor.opacity(0.8))
            }

            Spacer()

            Button(intent: AddLastDrinkIntent()) {
                VStack(spacing: 2) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(Color(red: 0.788, green: 0.502, blue: 0.184))
                    Text("Nochmal")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundStyle(Color(red: 0.659, green: 0.620, blue: 0.537))
                }
            }
            .buttonStyle(.plain)
            .disabled(SharedStateStore.readSession().drinks.isEmpty)
        }
        .containerBackground(Color(red: 0.039, green: 0.031, blue: 0.027), for: .widget)
    }
}

// MARK: - Lock Screen Widget

struct PromilleLockScreenWidget: Widget {
    let kind = "PromilleLockScreenQuickAdd"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PromilleProvider()) { entry in
            PromilleLockScreenWidgetView(entry: entry)
        }
        .configurationDisplayName("Quick-Add")
        .description("Letzten Drink nochmal hinzufügen")
        .supportedFamilies([.accessoryRectangular])
    }
}
