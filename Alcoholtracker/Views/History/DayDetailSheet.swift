import SwiftUI
import SwiftData

// MARK: - DayDetailSheet
//
// Presented when the user taps a calendar cell in HistoryView.
// Shows drink list, BAC estimate, stats, and an optional day note with mood.

struct DayDetailSheet: View {

    let date: Date
    let allDrinks: [Drink]
    let allNotes: [DayNote]
    let profile: UserProfile?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    @State private var noteText: String = ""
    @State private var selectedMood: DayMood = .neutral
    @State private var editingDrink: Drink? = nil

    private let cal = Calendar.current

    // MARK: Derived

    private var dayDrinks: [Drink] {
        // Logical day: 06:00 on `date` to 05:59 next day (matches SessionViewModel).
        let start = cal.date(bySettingHour: 6, minute: 0, second: 0, of: date) ?? cal.startOfDay(for: date)
        let end   = cal.date(byAdding: .day, value: 1, to: start) ?? start
        return allDrinks
            .filter { $0.timestamp >= start && $0.timestamp < end }
            .sorted { $0.timestamp < $1.timestamp }
    }

    private var existingNote: DayNote? {
        allNotes.first { cal.isDate($0.dayStart, inSameDayAs: date) }
    }

    private var peakBAC: Double {
        guard let p = profile, !dayDrinks.isEmpty else { return 0 }
        // Same computation as the calendar cell color so both always agree.
        return BACCalculator.peakBAC(
            drinks: dayDrinks,
            profile: p,
            stomachStatus: p.defaultStomachStatus
        )
    }

    private var bacStatusForDay: BACStatus {
        guard let p = profile else { return BACStatus(bac: peakBAC) }
        return BACStatus(bac: peakBAC, profile: p)
    }
    private var skin: StatusSkin { profile?.statusSkin ?? .standard }

    // MARK: Formatted dates

    private static let dateTitleFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "d. MMMM yyyy"
        return fmt
    }()

    private static let dayOfWeekFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "EEEE"
        return fmt
    }()

    private var dateTitle: String { Self.dateTitleFormatter.string(from: date) }
    private var dayOfWeekLabel: String { Self.dayOfWeekFormatter.string(from: date) }

    private var isToday: Bool { cal.isDateInToday(date) }

    // MARK: Body

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(dateTitle)
                            .font(.appHeadline)
                            .foregroundStyle(Color.appText)
                        Text(isToday ? "Heute" : dayOfWeekLabel)
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appTextDim)
                            .frame(width: 32, height: 32)
                            .background(Color.appCard)
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Schließen")
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 16)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {

                        if dayDrinks.isEmpty {
                            DDSEmptyState()
                        } else {
                            statsCard
                            hangoverCard
                            drinkList
                        }

                        noteCard
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 40)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .sheet(item: $editingDrink) { drink in
            DrinkEditSheet(
                drink: drink,
                profile: profile,
                onSave: { volume, timestamp in updateDrink(drink, volume: volume, timestamp: timestamp) },
                onDelete: { deleteDrink(drink) }
            )
        }
        .onAppear {
            if let note = existingNote {
                noteText    = note.text
                selectedMood = note.mood
            }
        }
        .onDisappear {
            saveNoteIfNeeded()
        }
    }

    // MARK: Stats card

    private var statsCard: some View {
        VStack(spacing: 14) {
            SectionLabel(text: "ZUSAMMENFASSUNG")
            HStack(spacing: 0) {
                DDSStat(
                    icon: "drop.fill",
                    iconColor: bacStatusForDay.color,
                    label: "Spitzen-BAC",
                    value: peakBAC.permilleString,
                    valueColor: bacStatusForDay.color
                )
                Divider()
                    .frame(maxHeight: 48)
                    .background(Color.appBorder)
                DDSStat(
                    icon: "mug.fill",
                    iconColor: Color.appAccent,
                    label: "Drinks",
                    value: "\(dayDrinks.count)",
                    valueColor: Color.appText
                )
                Divider()
                    .frame(maxHeight: 48)
                    .background(Color.appBorder)
                DDSStat(
                    icon: "flame.fill",
                    iconColor: Color.statusOrange,
                    label: "Kalorien",
                    value: "\(dayDrinks.reduce(0) { $0 + $1.calories }) kcal",
                    valueColor: Color.appText
                )
            }
            .padding(.vertical, 12)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            StatusPill(status: bacStatusForDay, skin: skin)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Hangover card

    private var hangoverCard: some View {
        let level: HangoverLevel = {
            guard let p = profile else { return .none }
            let water = WaterLog.loggedGlasses(forDay: date).map(Double.init)
            return HangoverPredictor.predict(drinks: dayDrinks, profile: p, waterGlasses: water)
        }()

        return HStack(spacing: 14) {
            Image(systemName: level.symbolName)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(level.color)
                .frame(width: 40, height: 40)
                .background(level.color.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 2) {
                Text("Kater-Prognose")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Text(level.label)
                    .font(.appCaptionBold)
                    .foregroundStyle(level.isLethal ? level.color : Color.appText)
                if level.isLethal {
                    Text("Solche Werte sind lebensgefährlich. Im Zweifel Notruf 112.")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer()
        }
        .padding(14)
        .background(level.isLethal ? level.color.opacity(0.10) : Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(level.isLethal ? level.color.opacity(0.5) : Color.appBorder, lineWidth: level.isLethal ? 1 : 0.5)
        )
    }

    // MARK: Drink list

    private var drinkList: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "GETRÄNKE")
            VStack(spacing: 0) {
                ForEach(Array(dayDrinks.enumerated()), id: \.element.id) { idx, drink in
                    Button {
                        editingDrink = drink
                    } label: {
                        DDSDrinkRow(
                            drink: drink,
                            isNextCalendarDay: !cal.isDate(drink.timestamp, inSameDayAs: date)
                        )
                    }
                    .buttonStyle(.plain)
                    if idx < dayDrinks.count - 1 {
                        Divider()
                            .background(Color.appBorder)
                            .padding(.leading, 52)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }

    // MARK: Note card

    private var noteCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "NOTIZ")

            VStack(spacing: 14) {
                HStack(spacing: 8) {
                    ForEach(DayMood.allCases, id: \.self) { mood in
                        Button {
                            withAnimation(.spring(response: 0.25)) {
                                selectedMood = mood
                            }
                        } label: {
                            VStack(spacing: 3) {
                                Text(mood.emoji)
                                    .font(.system(size: 26))
                                Text(mood.label)
                                    .font(.system(size: 9, weight: .medium))
                                    .foregroundStyle(selectedMood == mood ? Color.appAccent : Color.appTextMuted)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(selectedMood == mood ? Color.appAccent.opacity(0.12) : Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .strokeBorder(
                                        selectedMood == mood ? Color.appAccent : Color.appBorder,
                                        lineWidth: selectedMood == mood ? 1.5 : 0.5
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                TextField("Kurze Notiz zum Abend...", text: $noteText, axis: .vertical)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .lineLimit(3...6)
                    .padding(12)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )
            }
        }
    }

    // MARK: Drink editing

    // Same calorie scaling as SessionViewModel.updateDrink so edits behave
    // identically no matter where they happen.
    private func updateDrink(_ drink: Drink, volume: Double, timestamp: Date) {
        guard volume > 0, drink.volume > 0 else { return }
        drink.calories = Int((Double(drink.calories) / drink.volume * volume).rounded())
        drink.volume = volume
        drink.timestamp = timestamp
        try? context.save()
    }

    private func deleteDrink(_ drink: Drink) {
        context.delete(drink)
        try? context.save()
    }

    // MARK: Save

    private func saveNoteIfNeeded() {
        let trimmed = noteText.trimmingCharacters(in: .whitespaces)
        let isBlank = trimmed.isEmpty && selectedMood == .neutral

        if let existing = existingNote {
            if isBlank {
                // User cleared text and mood: remove the note entirely.
                context.delete(existing)
            } else if existing.text != trimmed || existing.mood != selectedMood {
                existing.text = trimmed
                existing.mood = selectedMood
            } else {
                return // nothing changed, skip the write
            }
        } else {
            guard !isBlank else { return }
            context.insert(DayNote(dayStart: date, text: trimmed, mood: selectedMood))
        }
        try? context.save()
    }
}

// MARK: - Sub-views

private struct DDSStat: View {
    let icon: String
    let iconColor: Color
    let label: String
    let value: String
    let valueColor: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(iconColor)
            Text(value)
                .font(.appBodyBold)
                .foregroundStyle(valueColor)
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(label)
                .font(.appMicro)
                .foregroundStyle(Color.appTextDim)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 8)
    }
}

private struct DDSDrinkRow: View {
    let drink: Drink
    // True for drinks logged after midnight that belong to this logical day.
    let isNextCalendarDay: Bool

    private static let timeFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "HH:mm"
        return fmt
    }()

    private var timeLabel: String { Self.timeFormatter.string(from: drink.timestamp) }

    var body: some View {
        HStack(spacing: 12) {
            DrinkIconView(drink: drink, size: 14)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 36, height: 36)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 9))

            VStack(alignment: .leading, spacing: 2) {
                Text(drink.name)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                Text("\(Int(drink.volume)) ml · \(String(format: "%.1f", drink.abv)) %")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            HStack(spacing: 3) {
                Text(timeLabel)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                    .monospacedDigit()
                if isNextCalendarDay {
                    Text("+1")
                        .font(.system(size: 8, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                        .baselineOffset(4)
                }
            }

            Image(systemName: "chevron.right")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Color.appTextMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }
}

private struct DDSEmptyState: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "moon.zzz.fill")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(Color.appTextMuted)
            Text("Kein Alkohol an diesem Tag.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }
}

