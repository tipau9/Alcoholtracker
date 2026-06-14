import SwiftUI

// MARK: - BottleModeSheet
// Two-step flow:
//   Step 1: template search (when no template pre-selected)
//   Step 2: bottle level UI

struct BottleModeSheet: View {

    let profile: UserProfile?
    let allTemplates: [DrinkTemplate]
    let lastBottleLevels: [UUID: Double]
    let onAdd: (DrinkTemplate, Double, Double, Double) -> Void  // template, bottleSize, startLevel, currentLevel

    @Environment(\.dismiss) private var dismiss
    @State private var selectedTemplate: DrinkTemplate? = nil
    @State private var searchQuery = ""

    var body: some View {
        if let template = selectedTemplate {
            BottleLevelView(
                template: template,
                profile: profile,
                savedLevel: lastBottleLevels[template.id],
                onAdd: { size, start, current in
                    onAdd(template, size, start, current)
                    dismiss()
                },
                onBack: { selectedTemplate = nil }
            )
        } else {
            BottleTemplatePickerView(
                allTemplates: allTemplates,
                searchQuery: $searchQuery,
                onSelect: { selectedTemplate = $0 },
                onDismiss: { dismiss() }
            )
        }
    }
}

// MARK: - Template Picker (Step 1)

private struct BottleTemplatePickerView: View {

    let allTemplates: [DrinkTemplate]
    @Binding var searchQuery: String
    let onSelect: (DrinkTemplate) -> Void
    let onDismiss: () -> Void
    @FocusState private var searchFocused: Bool

    private var results: [DrinkTemplate] {
        if searchQuery.isEmpty { return Array(allTemplates.prefix(60)) }
        let q = searchQuery.lowercased()
        return Array(allTemplates.lazy.filter { $0.name.localizedStandardContains(q) }.prefix(40))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Aus Flasche")
                            .font(.appHeadline)
                            .foregroundStyle(Color.appText)
                        Text("Welches Getränk ist in der Flasche?")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                    Button { onDismiss() } label: {
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
                .padding(.top, 16)
                .padding(.bottom, 12)

                HStack {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.appTextDim)
                    TextField("Getränk suchen...", text: $searchQuery)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                        .focused($searchFocused)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Color.appCard)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.appBorder, lineWidth: 0.5))
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { searchFocused = true }
                .padding(.horizontal, 16)
                .padding(.bottom, 10)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        ForEach(results) { template in
                            Button { onSelect(template) } label: {
                                HStack(spacing: 14) {
                                    Image(systemName: template.iconName)
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundStyle(Color.appAccent)
                                        .frame(width: 36, height: 36)
                                        .background(Color.appAccent.opacity(0.1))
                                        .clipShape(RoundedRectangle(cornerRadius: 9))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(template.name)
                                            .font(.appBody)
                                            .foregroundStyle(Color.appText)
                                            .lineLimit(1)
                                        Text("\(template.abv, specifier: "%.1f")% vol")
                                            .font(.appCaption)
                                            .foregroundStyle(Color.appTextDim)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12))
                                        .foregroundStyle(Color.appTextMuted)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                            }
                            .buttonStyle(.plain)
                            Divider().background(Color.appBorder).padding(.leading, 66)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Bottle Level UI (Step 2)

private struct BottleLevelView: View {

    let template: DrinkTemplate
    let profile: UserProfile?
    let onAdd: (Double, Double, Double) -> Void  // bottleSize, startLevel, currentLevel
    let onBack: () -> Void

    @State private var bottleSize: Double
    @State private var startLevel: Double
    @State private var currentLevel: Double

    init(template: DrinkTemplate, profile: UserProfile?, savedLevel: Double?, onAdd: @escaping (Double, Double, Double) -> Void, onBack: @escaping () -> Void) {
        self.template = template
        self.profile = profile
        self.onAdd = onAdd
        self.onBack = onBack
        let sizes = template.category.commonBottleSizes
        let defaultSize = sizes.first(where: { $0.volumeML == 700 })?.volumeML
            ?? sizes.first?.volumeML ?? 700
        _bottleSize = State(initialValue: defaultSize)
        let savedL = savedLevel ?? 1.0
        _startLevel = State(initialValue: savedL)
        _currentLevel = State(initialValue: savedL)
    }

    private var consumedML: Double { max(0, (startLevel - currentLevel) * bottleSize) }
    private var promilleContrib: Double {
        guard let p = profile else { return 0 }
        let grams = (consumedML * template.abv / 100.0) * 0.789
        return grams / (p.weight * p.distributionFactor)
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                // Header
                HStack {
                    Button { onBack() } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.appAccent)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 8)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(template.name)
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appText)
                            .lineLimit(1)
                        Text("\(template.abv, specifier: "%.1f")% vol")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 10)

                // Result card + Add button (always visible)
                VStack(spacing: 10) {
                    HStack(spacing: 0) {
                        VStack(spacing: 2) {
                            Text("\(Int(consumedML))")
                                .font(.system(size: 30, weight: .light, design: .serif))
                                .foregroundStyle(Color.appText)
                                .monospacedDigit()
                            Text("ml getrunken")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextMuted)
                        }
                        .frame(maxWidth: .infinity)
                        Rectangle().fill(Color.appBorder).frame(width: 0.5, height: 40)
                        VStack(spacing: 2) {
                            Text("+\(promilleContrib, specifier: "%.2f") ‰")
                                .font(.system(size: 30, weight: .light, design: .serif))
                                .foregroundStyle(Color.appAccent)
                                .monospacedDigit()
                            Text("Promille")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextMuted)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.vertical, 12)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))

                    Button {
                        if consumedML > 0 { onAdd(bottleSize, startLevel, currentLevel) }
                    } label: {
                        Text(consumedML > 0 ? "Hinzufügen" : "Pegelstand einstellen")
                            .font(.appBodyBold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(consumedML > 0 ? Color.appAccent : Color.appCard)
                            .foregroundStyle(consumedML > 0 ? Color.white : Color.appTextMuted)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(consumedML <= 0)
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {

                        // Bottle size selector
                        VStack(alignment: .leading, spacing: 8) {
                            Text("FLASCHENGRÖSSE")
                                .font(.system(size: 10, weight: .medium))
                                .tracking(2)
                                .foregroundStyle(Color.appTextMuted)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(template.category.commonBottleSizes, id: \.volumeML) { size in
                                        Button {
                                            bottleSize = size.volumeML
                                            startLevel = 1.0
                                            currentLevel = 1.0
                                        } label: {
                                            Text(size.label)
                                                .font(.system(size: 12, weight: .medium))
                                                .padding(.horizontal, 14)
                                                .padding(.vertical, 8)
                                                .background(bottleSize == size.volumeML ? Color.appAccent.opacity(0.18) : Color.appCard)
                                                .foregroundStyle(bottleSize == size.volumeML ? Color.appAccent : Color.appTextDim)
                                                .clipShape(Capsule())
                                                .overlay(Capsule().strokeBorder(bottleSize == size.volumeML ? Color.appAccent : Color.appBorder, lineWidth: 1))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }

                        // Bottle visual + sliders
                        HStack(spacing: 20) {
                            BottleGraphic(startLevel: startLevel, currentLevel: currentLevel)
                                .frame(width: 72, height: 260)

                            VStack(spacing: 20) {
                                // Start level buttons
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text("Vorher war die Flasche...")
                                            .font(.appCaption)
                                            .foregroundStyle(Color.appText)
                                        Spacer()
                                        Text("\(Int(startLevel * 100))%")
                                            .font(.system(size: 13, weight: .semibold, design: .serif))
                                            .foregroundStyle(Color.appTextDim)
                                            .monospacedDigit()
                                    }
                                    LevelButtons(
                                        levels: [1.0, 0.75, 0.5, 0.25],
                                        selected: startLevel,
                                        onSelect: { v in
                                            startLevel = v
                                            if currentLevel > startLevel { currentLevel = startLevel }
                                        }
                                    )
                                }

                                // Current level slider + buttons
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text("Jetzt ist sie...")
                                            .font(.appCaption)
                                            .foregroundStyle(Color.appText)
                                        Spacer()
                                        Text("\(Int(currentLevel * 100))%")
                                            .font(.system(size: 13, weight: .semibold, design: .serif))
                                            .foregroundStyle(Color.appAccent)
                                            .monospacedDigit()
                                    }
                                    Slider(value: $currentLevel, in: 0...startLevel, step: 0.05)
                                        .tint(Color.appAccent)
                                    LevelButtons(
                                        levels: [0.0, 0.25, 0.5, 0.75],
                                        selected: currentLevel,
                                        onSelect: { v in currentLevel = min(v, startLevel) }
                                    )
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))

                        // Details
                        VStack(spacing: 6) {
                            bottleDetailRow(label: "Flaschengröße", value: "\(Int(bottleSize)) ml")
                            bottleDetailRow(label: "Start", value: "\(Int(startLevel * bottleSize)) ml (\(Int(startLevel * 100))%)")
                            bottleDetailRow(label: "Jetzt", value: "\(Int(currentLevel * bottleSize)) ml (\(Int(currentLevel * 100))%)")
                            bottleDetailRow(label: "Getrunken", value: "\(Int(consumedML)) ml")
                        }
                        .padding(14)
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .padding(16)
                }
            }
        }
    }

    private func bottleDetailRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.appCaption)
                .foregroundStyle(Color.appTextMuted)
            Spacer()
            Text(value)
                .font(.system(size: 12, weight: .medium, design: .serif))
                .foregroundStyle(Color.appTextDim)
                .monospacedDigit()
        }
    }
}

// MARK: - Reusable level button row

private struct LevelButtons: View {
    let levels: [Double]
    let selected: Double
    let onSelect: (Double) -> Void

    private func label(_ v: Double) -> String {
        switch v {
        case 0.0: return "Leer"
        case 0.25: return "1/4"
        case 0.5: return "Halb"
        case 0.75: return "3/4"
        case 1.0: return "Voll"
        default: return "\(Int(v * 100))%"
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            ForEach(levels, id: \.self) { v in
                let active = abs(selected - v) < 0.03
                Button { onSelect(v) } label: {
                    Text(label(v))
                        .font(.system(size: 11, weight: .medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(active ? Color.appAccent.opacity(0.18) : Color.appCard)
                        .foregroundStyle(active ? Color.appAccent : Color.appTextDim)
                        .clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(active ? Color.appAccent : Color.appBorder, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
