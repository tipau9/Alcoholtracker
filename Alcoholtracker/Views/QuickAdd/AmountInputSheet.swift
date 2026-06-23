import SwiftUI

// MARK: - AmountInputSheet
// Long-press sheet for adjusting drink volume before adding.
// Shows category-appropriate serving size presets above a continuous slider.

struct AmountInputSheet: View {

    let template: DrinkTemplate
    let profile: UserProfile?
    let onAdd: (Drink) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var volume: Double
    @State private var selectedPresetID: UUID? = nil
    @State private var durationMinutes: Double = 0

    private let presets: [ServingSize]
    private let sliderRange: ClosedRange<Double>

    init(template: DrinkTemplate, profile: UserProfile?, onAdd: @escaping (Drink) -> Void) {
        self.template = template
        self.profile  = profile
        self.onAdd    = onAdd
        self.presets  = ServingSize.presets(for: template.category)

        let range: ClosedRange<Double>
        switch template.category {
        case .beer, .cider, .mixed:          range = 100...2000
        case .wine, .sparkling, .fortified:  range = 50...750
        case .spirits, .liqueur:             range = 5...200
        case .shot:                          range = 5...100
        case .cocktail, .other:              range = 50...600
        }
        self.sliderRange = range

        let clamped = min(range.upperBound, max(range.lowerBound, template.volume))
        self._volume = State(initialValue: clamped)

        // Use the already-created presets array so UUIDs match
        let initial = self.presets.first { abs($0.volumeML - clamped) < 0.5 }
        self._selectedPresetID = State(initialValue: initial?.id)
    }

    private var isValid: Bool { volume > 0 }

    private var bacContribution: Double? {
        guard let p = profile, volume > 0 else { return nil }
        // Realistic peak this drink reaches, so the preview matches the live BAC.
        return BACCalculator.projectedPeak(
            volume: volume, abv: template.abv, category: template.category,
            profile: p, stomachStatus: p.defaultStomachStatus,
            drinkDurationMinutes: durationMinutes
        )
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                // Handle
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                // Drink header
                HStack(spacing: 14) {
                    DrinkIconView(template: template, size: 18)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(template.name)
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appText)
                        Text("\(String(format: "%.1f", template.abv)) % Alk.")
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
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 16)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {

                        // Serving size preset grid
                        if !presets.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                SectionLabel(text: "GRÖSSE WÄHLEN")
                                LazyVGrid(
                                    columns: Array(
                                        repeating: GridItem(.flexible(), spacing: 10),
                                        count: 3
                                    ),
                                    spacing: 10
                                ) {
                                    ForEach(presets) { preset in
                                        AISServingSizeCell(
                                            preset: preset,
                                            isSelected: selectedPresetID == preset.id,
                                            onTap: {
                                                withAnimation(.spring(response: 0.25)) {
                                                    volume = preset.volumeML
                                                    selectedPresetID = preset.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Volume display and custom slider
                        VStack(alignment: .leading, spacing: 10) {
                            SectionLabel(text: "EIGENE MENGE")

                            HStack(alignment: .lastTextBaseline, spacing: 4) {
                                Text("\(Int(volume))")
                                    .font(.system(size: 48, weight: .light, design: .serif))
                                    .foregroundStyle(Color.appText)
                                    .monospacedDigit()
                                    .contentTransition(.numericText())
                                Text("ml")
                                    .font(.system(size: 16, weight: .regular))
                                    .foregroundStyle(Color.appTextDim)
                                    .padding(.bottom, 4)
                                Spacer()
                                if selectedPresetID == nil {
                                    Text("Eigene Menge")
                                        .font(.system(size: 10, weight: .medium))
                                        .tracking(1)
                                        .foregroundStyle(Color.appAccent)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.appAccent.opacity(0.12))
                                        .clipShape(Capsule())
                                }
                            }

                            Slider(value: $volume, in: sliderRange)
                                .tint(Color.appAccent)
                                .onChange(of: volume) { _, newValue in
                                    if let id = selectedPresetID,
                                       let preset = presets.first(where: { $0.id == id }),
                                       abs(preset.volumeML - newValue) > 5 {
                                        selectedPresetID = nil
                                    }
                                }

                            HStack {
                                Text("\(Int(sliderRange.lowerBound)) ml")
                                    .font(.appMicro)
                                    .foregroundStyle(Color.appTextDim)
                                Spacer()
                                Text("\(Int(sliderRange.upperBound)) ml")
                                    .font(.appMicro)
                                    .foregroundStyle(Color.appTextDim)
                            }
                        }

                        // Drinking duration ("verzögerter Start" / sipping)
                        VStack(alignment: .leading, spacing: 10) {
                            SectionLabel(text: "TRINKDAUER")
                            DurationChipRow(durationMinutes: $durationMinutes)
                        }

                        // BAC preview
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
                                    .contentTransition(.numericText())
                            }
                            .padding(14)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )
                        }

                        PrimaryButton(
                            title: "Hinzufügen",
                            icon: "plus",
                            isDisabled: !isValid
                        ) {
                            let drink = Drink.from(template: template, volume: volume)
                            drink.drinkDurationMinutes = durationMinutes
                            onAdd(drink)
                            dismiss()
                        }

                        Color.clear.frame(height: 4)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Serving size cell

private struct AISServingSizeCell: View {
    let preset: ServingSize
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 5) {
                Image(systemName: preset.icon)
                    .font(.system(size: 20, weight: .regular))
                    .foregroundStyle(isSelected ? Color.appAccent : Color.appTextDim)

                Text(preset.name)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(isSelected ? Color.appAccent : Color.appText)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)

                Text("\(Int(preset.volumeML)) ml")
                    .font(.system(size: 10))
                    .foregroundStyle(Color.appTextDim)
                    .monospacedDigit()

                if let desc = preset.description {
                    Text(desc)
                        .font(.system(size: 9))
                        .foregroundStyle(Color.appTextDim.opacity(0.8))
                        .lineLimit(1)
                }
            }
            .padding(.vertical, 10)
            .padding(.horizontal, 8)
            .frame(maxWidth: .infinity, minHeight: 80)
            .background(isSelected ? Color.appAccent.opacity(0.10) : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: isSelected ? 1.5 : 0.5
                    )
            )
        }
        .buttonStyle(.plain)
        .contentShape(Rectangle())
    }
}
