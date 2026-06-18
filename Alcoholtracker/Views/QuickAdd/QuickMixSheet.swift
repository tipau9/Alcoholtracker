import SwiftUI
import SwiftData

// MARK: - QuickMixSheet
//
// Full-height sheet for building a spirit + mixer drink.
// BAC is calculated from the spirit portion only (mixer is non-alcoholic).
// Calories = scaled spirit calories + mixer calories by volume.

struct QuickMixSheet: View {
    let profile: UserProfile?
    let onAdd: (Drink) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(SupabaseService.self) private var supabase
    @State private var shareConfirm = false

    @Query(sort: [SortDescriptor(\DrinkTemplate.usageCount, order: .reverse)])
    private var allTemplates: [DrinkTemplate]

    @State private var selectedSpirit: DrinkTemplate? = nil
    @State private var selectedMixer: Mixer? = nil
    @State private var spiritFraction: Double = 0.25
    @State private var totalVolumeMl: Double = 200
    @State private var mixerCategory: MixerCategory? = nil
    @State private var spiritSearch: String = ""
    @FocusState private var spiritSearchFocused: Bool

    // MARK: Derived

    private var spiritVol: Double { totalVolumeMl * spiritFraction }
    private var mixerVol: Double  { totalVolumeMl * (1 - spiritFraction) }

    private var spiritTemplates: [DrinkTemplate] {
        let base = allTemplates.filter {
            $0.category == .spirits || $0.category == .liqueur || $0.category == .shot
        }
        guard !spiritSearch.trimmingCharacters(in: .whitespaces).isEmpty else { return base }
        let q = spiritSearch.lowercased()
        return base.filter { $0.name.lowercased().contains(q) }
    }

    private var visibleMixers: [Mixer] {
        if let cat = mixerCategory { return MixerDatabase.entries(for: cat) }
        return MixerDatabase.all
    }

    private var effectiveABV: Double {
        guard let spirit = selectedSpirit else { return 0 }
        return spirit.abv * spiritFraction
    }

    private var bacContribution: Double {
        guard let spirit = selectedSpirit else { return 0 }
        let w = profile?.weight ?? 70
        let r = profile?.distributionFactor ?? 0.60
        return BACCalculator.bacContribution(
            volume: spiritVol, abv: spirit.abv, weight: w, distributionFactor: r
        )
    }

    private var totalCalories: Int {
        var cals = 0
        if let spirit = selectedSpirit, spirit.volume > 0 {
            cals += Int(spiritVol * Double(spirit.calories) / spirit.volume)
        }
        if let mixer = selectedMixer {
            cals += Int(mixerVol / 100.0 * Double(mixer.caloriesPer100ml))
        }
        return cals
    }

    private var waterFromMixer: Double {
        guard let mixer = selectedMixer else { return 0 }
        return mixerVol * mixer.waterContentPercent / 100.0
    }

    private var canAdd: Bool { selectedSpirit != nil && selectedMixer != nil }

    private let volumePresets: [Double] = [100, 150, 200, 250, 300, 400, 500]

    // MARK: Body

    var body: some View {
        VStack(spacing: 0) {
            // Fixed header — always visible regardless of scroll position
            QMSHandle()
            VStack(alignment: .leading, spacing: 0) {
                QMSHeader(title: "Quick Mix") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)

                if canAdd {
                    // Compact inline BAC preview at the top so no scrolling needed
                    compactStats
                        .padding(.horizontal, 20)
                        .padding(.bottom, 12)
                }
            }

            Divider().background(Color.appBorder)

            // Scrollable selection area
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 28) {
                    spiritSection
                    ratioVolumeSection
                    mixerSection
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 120) // room for sticky button
            }
        }
        // Sticky add button pinned to safe area bottom
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                Divider().background(Color.appBorder)
                HStack(spacing: 12) {
                    // Share this mix to the community (same self-learning DB as
                    // the cocktail creator).
                    Button(action: shareMix) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(canAdd ? Color.appAccent : Color.appTextMuted)
                            .frame(width: 52, height: 52)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                    .disabled(!canAdd)

                    addButton
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            .background(.ultraThinMaterial)
        }
        .background(Color.appBackground)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .alert("Mix geteilt", isPresented: $shareConfirm) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Danke! Dein Mix wird für andere sichtbar, sobald genug Leute ihn teilen oder er freigegeben wird.")
        }
    }

    private func shareMix() {
        guard let spirit = selectedSpirit, let mixer = selectedMixer else { return }
        let name = "\(spirit.name) + \(mixer.name)"
        let ingredients = [
            MixIngredient(name: spirit.name, abv: spirit.abv, volume: spiritVol),
            MixIngredient(name: mixer.name, abv: 0, volume: mixerVol)
        ]
        Task {
            try? await supabase.contributeMix(
                name: name,
                ingredients: ingredients,
                totalVolume: totalVolumeMl,
                totalAbv: effectiveABV,
                calories: totalCalories
            )
        }
        shareConfirm = true
    }

    // MARK: Compact stats (always visible at top when spirit+mixer selected)

    private var compactStats: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "chart.bar.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(bacStatusColor)
                Text(String(format: "+%.2f ‰", bacContribution))
                    .font(.appCaptionBold)
                    .foregroundStyle(bacStatusColor)
                    .contentTransition(.numericText())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(bacStatusColor.opacity(0.10))
            .clipShape(Capsule())

            HStack(spacing: 6) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appTextDim)
                Text("\(totalCalories) kcal")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.appCard)
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(Color.appBorder, lineWidth: 0.5))

            HStack(spacing: 6) {
                Image(systemName: "drop.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appAccent)
                Text(String(format: "%.1f %%", effectiveABV))
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Color.appCard)
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(Color.appBorder, lineWidth: 0.5))

            Spacer()
        }
        .animation(.easeOut(duration: 0.2), value: bacContribution)
    }

    // MARK: Spirit

    private var spiritSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "Alkohol-Basis")

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.appTextDim)
                TextField("Suchen...", text: $spiritSearch)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .autocorrectionDisabled()
                    .focused($spiritSearchFocused)
                if !spiritSearch.isEmpty {
                    Button { spiritSearch = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.appTextDim)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.appBorder, lineWidth: 0.5))
            .contentShape(RoundedRectangle(cornerRadius: 12))
            .onTapGesture { spiritSearchFocused = true }

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(spiritTemplates) { template in
                        QMSSpiritCard(
                            template: template,
                            isSelected: selectedSpirit?.id == template.id
                        ) { selectedSpirit = template }
                    }
                }
                .padding(.horizontal, 1)
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: Ratio and Volume

    private var ratioVolumeSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "Verhältnis")
                MixRatioSlider(spiritFraction: $spiritFraction)

                if selectedSpirit != nil || selectedMixer != nil {
                    HStack {
                        if let spirit = selectedSpirit {
                            Text(spirit.name)
                                .font(.appCaption)
                                .foregroundStyle(Color.appTextDim)
                            Text("\(Int(spiritVol)) ml")
                                .font(.appCaptionBold)
                                .foregroundStyle(Color.appAccent)
                                .contentTransition(.numericText())
                        }
                        Spacer()
                        if let mixer = selectedMixer {
                            Text("\(Int(mixerVol)) ml")
                                .font(.appCaptionBold)
                                .foregroundStyle(Color.appTextDim)
                                .contentTransition(.numericText())
                            Text(mixer.name)
                                .font(.appCaption)
                                .foregroundStyle(Color.appTextDim)
                        }
                    }
                    .animation(.easeOut(duration: 0.15), value: spiritFraction)
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "Gesamtmenge")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(volumePresets, id: \.self) { vol in
                            QMSChip(
                                label: "\(Int(vol)) ml",
                                isSelected: totalVolumeMl == vol
                            ) { totalVolumeMl = vol }
                        }
                    }
                    .padding(.horizontal, 1)
                }
            }
        }
    }

    // MARK: Mixer

    private var mixerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "Mixer")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    QMSChip(label: "Alle", isSelected: mixerCategory == nil) {
                        mixerCategory = nil
                    }
                    ForEach(MixerCategory.allCases, id: \.self) { cat in
                        QMSChip(
                            label: cat.localizedName,
                            isSelected: mixerCategory == cat
                        ) { mixerCategory = mixerCategory == cat ? nil : cat }
                    }
                }
                .padding(.horizontal, 1)
            }

            LazyVStack(spacing: 6) {
                ForEach(visibleMixers) { mixer in
                    QMSMixerRow(
                        mixer: mixer,
                        isSelected: selectedMixer?.id == mixer.id
                    ) { selectedMixer = mixer }
                }
            }
        }
    }

    private var bacStatusColor: Color {
        if bacContribution < 0.2 { return .statusGreen }
        if bacContribution < 0.4 { return .statusYellow }
        if bacContribution < 0.6 { return .statusOrange }
        return .statusRed
    }

    // MARK: Add button

    private var addButton: some View {
        PrimaryButton(
            title: "Hinzufügen",
            icon: "plus",
            isDisabled: !canAdd
        ) {
            guard let spirit = selectedSpirit, let mixer = selectedMixer else { return }

            let blendedABV = spirit.abv * spiritFraction
            let spiritCals = spirit.volume > 0 ? Int(spiritVol * Double(spirit.calories) / spirit.volume) : 0
            let mixerCals  = Int(mixerVol / 100.0 * Double(mixer.caloriesPer100ml))

            let drink = Drink(
                name: "\(spirit.name) + \(mixer.name)",
                volume: totalVolumeMl,
                abv: blendedABV,
                calories: spiritCals + mixerCals,
                iconName: spirit.iconName,
                category: .mixed,
                mixerVolume: mixerVol,
                mixerWaterContent: mixer.waterContentPercent
            )

            onAdd(drink)
            dismiss()
        }
    }
}

// MARK: - Sheet chrome

private struct QMSHandle: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2.5)
            .fill(Color.appBorder)
            .frame(width: 36, height: 5)
            .padding(.top, 12)
            .padding(.bottom, 4)
    }
}

private struct QMSHeader: View {
    let title: String
    let onClose: () -> Void

    var body: some View {
        HStack {
            Text(title)
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            Spacer()
            Button(action: onClose) {
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
    }
}

// MARK: - Spirit card

private struct QMSSpiritCard: View {
    let template: DrinkTemplate
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                DrinkIconView(template: template, size: 20)
                    .font(.system(size: 20))
                    .foregroundStyle(isSelected ? Color.appBackground : Color.appAccent)
                    .frame(width: 48, height: 48)
                    .background(isSelected ? Color.appAccent : Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 13))
                    .overlay(
                        RoundedRectangle(cornerRadius: 13)
                            .strokeBorder(isSelected ? Color.appAccent : Color.appBorder, lineWidth: 0.5)
                    )

                Text(template.name)
                    .font(.appMicro)
                    .foregroundStyle(isSelected ? Color.appText : Color.appTextDim)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(width: 68)

                Text(String(format: "%.0f%%", template.abv))
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }
        }
        .buttonStyle(.plain)
        .frame(width: 68)
    }
}

// MARK: - Reusable chip (category + volume)

private struct QMSChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.appCaptionBold)
                .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? Color.appAccent : Color.appCard)
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(
                    isSelected ? Color.appAccent : Color.appBorder,
                    lineWidth: 0.5
                ))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Mixer row

private struct QMSMixerRow: View {
    let mixer: Mixer
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: mixer.icon)
                    .font(.system(size: 16))
                    .foregroundStyle(isSelected ? Color.appBackground : Color.appAccent)
                    .frame(width: 36, height: 36)
                    .background(isSelected ? Color.appAccent : Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(isSelected ? Color.appAccent : Color.appBorder, lineWidth: 0.5)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(mixer.name)
                        .font(.appBody)
                        .foregroundStyle(isSelected ? Color.appAccent : Color.appText)
                    Text("\(mixer.caloriesPer100ml) kcal/100 ml")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(isSelected ? Color.appAccent.opacity(0.08) : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        isSelected ? Color.appAccent.opacity(0.4) : Color.appBorder,
                        lineWidth: 0.5
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Stat badge

private struct QMSStat: View {
    let icon: String
    let label: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundStyle(color)

            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text(value)
                        .font(.appCaptionBold)
                        .foregroundStyle(color)
                        .contentTransition(.numericText())
                    Text(unit)
                        .font(.appMicro)
                        .foregroundStyle(color.opacity(0.7))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(color.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(color.opacity(0.2), lineWidth: 0.5)
        )
    }
}

// MARK: - Metric row (secondary stats bar)

private struct QMSMetricRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 3) {
            Text(label.uppercased())
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .tracking(0.5)
            Text(value)
                .font(.appCaptionBold)
                .foregroundStyle(Color.appText)
                .contentTransition(.numericText())
        }
        .frame(maxWidth: .infinity)
    }
}

private struct QMSMetricDivider: View {
    var body: some View {
        Rectangle()
            .fill(Color.appBorder)
            .frame(width: 0.5)
            .padding(.vertical, 6)
    }
}

#Preview {
    QuickMixSheet(profile: nil) { _ in }
        .environment(SupabaseService())
        .preferredColorScheme(.dark)
}
