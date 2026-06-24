import SwiftUI

// MARK: - DrinkEditSheet
// Tap any logged drink in the history to edit its volume and time, or delete it.

struct DrinkEditSheet: View {

    let drink: Drink
    let profile: UserProfile?
    let onSave: (Double, Date) -> Void
    let onDelete: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var volumeText: String
    @State private var timestamp: Date
    @State private var durationMinutes: Double
    @State private var showDeleteConfirm = false

    init(
        drink: Drink,
        profile: UserProfile?,
        onSave: @escaping (Double, Date) -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.drink = drink
        self.profile = profile
        self.onSave = onSave
        self.onDelete = onDelete
        self._volumeText = State(initialValue: "\(Int(drink.volume))")
        self._timestamp = State(initialValue: drink.timestamp)
        self._durationMinutes = State(initialValue: drink.drinkDurationMinutes)
    }

    private var volume: Double {
        Double(volumeText.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private var isValid: Bool { volume > 0 && volume <= 3000 }

    private var bacContribution: Double? {
        guard let p = profile, volume > 0 else { return nil }
        return BACCalculator.projectedPeak(
            volume: volume, abv: drink.abv, category: drink.category,
            profile: p, stomachStatus: p.defaultStomachStatus,
            drinkDurationMinutes: durationMinutes, conservative: p.conservativeForApp
        )
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack(spacing: 14) {
                    DrinkIconView(drink: drink, size: 18)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(drink.name)
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appText)
                        Text("\(String(format: "%.1f", drink.abv)) % Alk.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                        if drink.mixerVolume > 0 {
                            Text("Spirituose \(Int(drink.volume - drink.mixerVolume)) ml, Mixer \(Int(drink.mixerVolume)) ml")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextMuted)
                        }
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
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 20)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        VStack(alignment: .leading, spacing: 8) {
                            SectionLabel(text: "MENGE")
                            HStack(spacing: 8) {
                                TextField("ml", text: $volumeText)
                                    .keyboardType(.numberPad)
                                    .font(.appBody)
                                    .foregroundStyle(Color.appText)
                                    .autocorrectionDisabled()
                                Text("ml")
                                    .font(.appBodyBold)
                                    .foregroundStyle(Color.appTextDim)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )
                        }

                        if drink.mixerVolume > 0 {
                            DESDrinkMixInfo(drink: drink)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            SectionLabel(text: "TRINKDAUER")
                            DurationChipRow(durationMinutes: $durationMinutes)
                            Text("Über welchen Zeitraum getrunken. Längere Dauer verteilt die Aufnahme und senkt den Peak.")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextMuted)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            SectionLabel(text: "UHRZEIT")
                            DatePicker("", selection: $timestamp, displayedComponents: [.hourAndMinute])
                                .datePickerStyle(.wheel)
                                .labelsHidden()
                                .frame(maxWidth: .infinity)
                                .background(Color.appCard)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                )
                        }

                        if let bac = bacContribution {
                            HStack(spacing: 12) {
                                Image(systemName: "gauge.with.dots.needle.33percent")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundStyle(Color.appAccent)
                                    .frame(width: 32, height: 32)
                                    .background(Color.appAccent.opacity(0.10))
                                    .clipShape(RoundedRectangle(cornerRadius: 9))
                                Text("Geschätzte Wirkung")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appTextDim)
                                Spacer()
                                Text(bac.signedPermilleString)
                                    .font(.appCaptionBold)
                                    .foregroundStyle(Color.statusOrange)
                            }
                            .padding(14)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )
                        }

                        PrimaryButton(title: "Speichern", icon: "checkmark", isDisabled: !isValid) {
                            // Persisted via the same context save onSave triggers.
                            drink.drinkDurationMinutes = durationMinutes
                            onSave(volume, timestamp)
                            dismiss()
                        }

                        Button { showDeleteConfirm = true } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "trash")
                                    .font(.system(size: 14, weight: .medium))
                                Text("Drink entfernen")
                                    .font(.appBodyBold)
                            }
                            .foregroundStyle(Color.statusRed)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.statusRed.opacity(0.10))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .strokeBorder(Color.statusRed.opacity(0.3), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .confirmationDialog("Drink wirklich entfernen?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Entfernen", role: .destructive) {
                onDelete()
                dismiss()
            }
            Button("Abbrechen", role: .cancel) {}
        }
    }
}

// MARK: - Mix details card

private struct DESDrinkMixInfo: View {
    let drink: Drink

    private var spiritML: Int   { Int(drink.volume - drink.mixerVolume) }
    private var mixerML: Int    { Int(drink.mixerVolume) }
    private var spiritPct: Int  { drink.volume > 0 ? Int((drink.volume - drink.mixerVolume) / drink.volume * 100) : 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "MIX-DETAILS")
            HStack(spacing: 0) {
                DESStat(label: "Spirituose", value: "\(spiritML) ml", color: .appAccent)
                Rectangle()
                    .fill(Color.appBorder)
                    .frame(width: 0.5, height: 32)
                DESStat(label: "Mixer", value: "\(mixerML) ml", color: .appTextDim)
                Rectangle()
                    .fill(Color.appBorder)
                    .frame(width: 0.5, height: 32)
                DESStat(label: "Spi.-Anteil", value: "\(spiritPct) %", color: .appAccent)
            }
            .padding(14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }
}

private struct DESStat: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.appCaptionBold)
                .foregroundStyle(color)
            Text(label)
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
        .frame(maxWidth: .infinity)
    }
}
