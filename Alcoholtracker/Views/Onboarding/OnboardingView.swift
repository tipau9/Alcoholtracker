import SwiftUI
import SwiftData

// MARK: - OnboardingView
// 5-page first-launch flow in question style: welcome, weight (ruler picker),
// biological gender, age + height (wheels), favourite drinks (searchable over
// the full template catalog). Finishing inserts a UserProfile with
// hasCompletedOnboarding = true and seeds usageCount on the four chosen
// templates so they surface as quick-add chips on the Home screen.

struct OnboardingView: View {

    @Environment(\.modelContext) private var context
    @Query(sort: [SortDescriptor(\DrinkTemplate.name)]) private var templates: [DrinkTemplate]

    @State private var page = 0
    @State private var forward = true

    // Staged profile fields
    @State private var weightKg: Int = 75
    @State private var gender: Gender? = nil
    @State private var age: Int = 25
    @State private var heightCm: Int = 180
    @State private var favoriteIDs: [UUID] = []

    private let pageCount = 5

    var body: some View {
        ZStack(alignment: .top) {
            Color.appBackground.ignoresSafeArea()

            Group {
                switch page {
                case 0: ONWelcomePage(onNext: { advance() })
                case 1: ONWeightPage(weightKg: $weightKg, onNext: { advance() })
                case 2: ONGenderPage(gender: $gender, onNext: { advance() })
                case 3: ONBodyPage(age: $age, heightCm: $heightCm, onNext: { advance() })
                default: ONFavoritesPage(
                    templates: templates,
                    favoriteIDs: $favoriteIDs,
                    onFinish: finish
                )
                }
            }
            .id(page)
            .transition(.asymmetric(
                insertion: .move(edge: forward ? .trailing : .leading).combined(with: .opacity),
                removal: .move(edge: forward ? .leading : .trailing).combined(with: .opacity)
            ))

            header
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 30).onEnded { value in
                // Page swipe. Skipped on the ruler and favourites pages where
                // horizontal drags belong to inner scroll views.
                guard page != 1 && page != 4 else { return }
                let w = value.translation.width
                let h = value.translation.height
                guard abs(w) > 90, abs(w) > abs(h) * 1.5 else { return }
                if w < 0 { advance() } else { goBack() }
            }
        )
    }

    // MARK: Header (back + progress dots)

    private var header: some View {
        ZStack {
            HStack(spacing: 8) {
                ForEach(0..<pageCount, id: \.self) { i in
                    Capsule()
                        .fill(i == page ? Color.appAccent : Color.appBorder)
                        .frame(width: i == page ? 22 : 7, height: 7)
                        .animation(.appSnappy, value: page)
                }
            }

            HStack {
                Button(action: goBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.appTextDim)
                        .frame(width: 40, height: 40)
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )
                }
                .buttonStyle(.plain)
                .opacity(page == 0 ? 0 : 1)
                .disabled(page == 0)
                Spacer()
            }
            .padding(.horizontal, 16)
        }
        .padding(.top, 8)
    }

    // MARK: Navigation

    private func canLeave(_ p: Int) -> Bool {
        if p == 2 { return gender != nil }
        return true
    }

    private func advance() {
        guard page < pageCount - 1, canLeave(page) else { return }
        forward = true
        withAnimation(.appSpring) { page += 1 }
    }

    private func goBack() {
        guard page > 0 else { return }
        forward = false
        withAnimation(.appSpring) { page -= 1 }
    }

    // MARK: Finish

    private func finish() {
        let w = Double(max(30, min(250, weightKg)))
        let h = Double(max(100, min(250, heightCm)))
        let a = max(18, min(99, age))
        let profile = UserProfile(
            weight: w,
            height: h,
            age: a,
            gender: gender ?? .male,
            emergencyContactName: nil,
            emergencyContactPhone: nil,
            hasCompletedOnboarding: true
        )
        profile.birthDate = Calendar.current.date(byAdding: .year, value: -a, to: Date()) ?? Date()
        profile.onboardingStepsCompleted = ["welcome", "weight", "gender", "body", "favorites"]
        context.insert(profile)

        // Seed the quick-add ranking: Home shows the templates with the highest
        // usageCount, so the four picks get descending starter counts.
        for (i, id) in favoriteIDs.enumerated() {
            if let t = templates.first(where: { $0.id == id }) {
                t.usageCount = max(t.usageCount, favoriteIDs.count - i)
            }
        }
        try? context.save()
    }
}

// MARK: - Page 0: Welcome

private struct ONWelcomePage: View {
    let onNext: () -> Void
    @State private var pulsing = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [Color.appAccent.opacity(0.16), .clear],
                            center: .center,
                            startRadius: 10,
                            endRadius: 170
                        )
                    )
                    .frame(width: 340, height: 340)
                    .scaleEffect(pulsing ? 1.12 : 0.94)
                    .animation(.easeInOut(duration: 2.8).repeatForever(autoreverses: true), value: pulsing)

                VStack(spacing: 14) {
                    HStack(spacing: 0) {
                        Text("promille")
                            .foregroundStyle(Color.appText)
                        Text(".")
                            .foregroundStyle(Color.appAccent)
                    }
                    .font(.appSerifLogo)

                    Text("Trink bewusst.")
                        .font(.appBody)
                        .foregroundStyle(Color.appTextDim)
                }
            }
            .onAppear { pulsing = true }

            Spacer()

            PrimaryButton(title: "Los geht's", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 24)

            Text("Nur für Personen ab 18 Jahren. Promillewerte sind Schätzungen und ersetzen keinen Atemtest.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
                .padding(.top, 16)
                .padding(.bottom, 24)
        }
    }
}

// MARK: - Page 1: Weight (horizontal ruler)

private struct ONWeightPage: View {
    @Binding var weightKg: Int
    let onNext: () -> Void

    private enum WeightUnit: String { case kg, lbs }
    @State private var unit: WeightUnit = .kg
    @State private var displayValue: Int = 75

    private var range: ClosedRange<Int> { unit == .kg ? 40...200 : 88...440 }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ONQuestionHeader(
                title: "Wie viel wiegst du?",
                subtitle: "Dein Gewicht fließt direkt in die Widmark-Berechnung ein."
            )

            ONUnitToggle(
                options: [WeightUnit.kg.rawValue, WeightUnit.lbs.rawValue],
                selected: unit.rawValue
            ) { picked in
                let newUnit = WeightUnit(rawValue: picked) ?? .kg
                guard newUnit != unit else { return }
                unit = newUnit
                displayValue = newUnit == .kg
                    ? weightKg
                    : Int((Double(weightKg) / 0.45359237).rounded())
            }
            .padding(.top, 8)

            Spacer()

            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(displayValue)")
                    .font(.appSerifValue)
                    .foregroundStyle(Color.appText)
                    .contentTransition(.numericText())
                    .animation(.snappy(duration: 0.2), value: displayValue)
                Text(unit.rawValue)
                    .font(.appTitle)
                    .foregroundStyle(Color.appTextDim)
            }
            .frame(maxWidth: .infinity)

            ONRulerPicker(value: $displayValue, range: range, majorEvery: 10)
                .frame(height: 96)
                .padding(.top, 24)
                .onChange(of: displayValue) { _, newValue in
                    weightKg = unit == .kg
                        ? newValue
                        : Int((Double(newValue) * 0.45359237).rounded())
                }

            Spacer()

            PrimaryButton(title: "Weiter", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
        }
        .padding(.top, 72)
        .onAppear {
            displayValue = unit == .kg
                ? weightKg
                : Int((Double(weightKg) / 0.45359237).rounded())
        }
    }
}

// Apple-Health-style horizontal ruler: ticks every unit, labels on majors,
// teal needle marks the centered value.
private struct ONRulerPicker: View {
    @Binding var value: Int
    let range: ClosedRange<Int>
    let majorEvery: Int

    @State private var scrolledID: Int?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .bottom, spacing: 0) {
                        ForEach(Array(range), id: \.self) { v in
                            tick(v)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollPosition(id: $scrolledID, anchor: .center)
                .scrollTargetBehavior(.viewAligned)
                .safeAreaPadding(.horizontal, max(0, geo.size.width / 2 - 7))
                .mask(
                    LinearGradient(
                        stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .black, location: 0.15),
                            .init(color: .black, location: 0.85),
                            .init(color: .clear, location: 1)
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )

                RoundedRectangle(cornerRadius: 2)
                    .fill(Color.appAccent)
                    .frame(width: 3, height: 56)
                    .shadow(color: Color.appAccent.opacity(0.6), radius: 7)
                    .offset(y: -10)
                    .allowsHitTesting(false)
            }
        }
        .sensoryFeedback(.selection, trigger: value)
        .onChange(of: scrolledID) { _, newID in
            if let newID, newID != value { value = newID }
        }
        .task {
            if scrolledID == nil { scrolledID = value }
        }
        .onChange(of: range) { _, newRange in
            // Unit switch rebuilt the tick list: re-center on the converted value.
            scrolledID = min(newRange.upperBound, max(newRange.lowerBound, value))
        }
    }

    private func tick(_ v: Int) -> some View {
        let isMajor = v % majorEvery == 0
        return VStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 1)
                .fill(isMajor ? Color.appTextMuted : Color.appBorder)
                .frame(width: 2, height: isMajor ? 44 : 24)
            Text(isMajor ? "\(v)" : " ")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .fixedSize()
        }
        .frame(width: 14, alignment: .bottom)
        .frame(maxHeight: .infinity, alignment: .bottom)
    }
}

// MARK: - Page 2: Biological gender

private struct ONGenderPage: View {
    @Binding var gender: Gender?
    let onNext: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ONQuestionHeader(
                title: "Dein biologisches Geschlecht?",
                subtitle: "Wähle das biologische Geschlecht für die Berechnung."
            )

            HStack(spacing: 16) {
                ONGenderCard(
                    symbol: "figure.stand",
                    label: "Männlich",
                    isSelected: gender == .male
                ) { gender = .male }
                ONGenderCard(
                    symbol: "figure.stand.dress",
                    label: "Weiblich",
                    isSelected: gender == .female
                ) { gender = .female }
            }
            .padding(.top, 16)

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "info.circle")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .padding(.top, 1)
                Text("Beeinflusst die Berechnung über den Widmark-Faktor: Alkohol verteilt sich im Körperwasser physiologisch unterschiedlich.")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
            .padding(.top, 24)

            Spacer()

            PrimaryButton(
                title: "Weiter",
                icon: "arrow.right",
                isDisabled: gender == nil,
                action: onNext
            )
            .padding(.horizontal, 24)
            .padding(.bottom, 40)
        }
        .padding(.top, 72)
    }
}

private struct ONGenderCard: View {
    let symbol: String
    let label: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 42, weight: .light))
                    .foregroundStyle(isSelected ? Color.appAccent : Color.appTextDim)
                Text(label)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 170)
            .background(isSelected ? Color.appAccent.opacity(0.1) : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: isSelected ? 1.5 : 0.5
                    )
            )
            .overlay(alignment: .topTrailing) {
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(Color.appAccent)
                        .padding(12)
                        .transition(.scale.combined(with: .opacity))
                }
            }
        }
        .buttonStyle(.plain)
        .animation(.appSnappy, value: isSelected)
    }
}

// MARK: - Page 3: Age + height (wheels)

private struct ONBodyPage: View {
    @Binding var age: Int
    @Binding var heightCm: Int
    let onNext: () -> Void

    private enum HeightUnit: String { case cm, ftin = "ft/in" }
    @State private var heightUnit: HeightUnit = .cm
    @State private var heightInch: Int = 71

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ONQuestionHeader(
                title: "Alter & Größe",
                subtitle: "Beides verfeinert die Schätzung deines Körperwassers (Watson-Formel)."
            )

            VStack(alignment: .leading, spacing: 6) {
                Text("Alter")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appTextDim)
                ONWheelCard {
                    Picker("Alter", selection: $age) {
                        ForEach(18...99, id: \.self) { a in
                            Text("\(a)").tag(a)
                        }
                    }
                    .pickerStyle(.wheel)
                }
            }
            .padding(.top, 12)

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Größe")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appTextDim)
                    Spacer()
                    ONUnitToggle(
                        options: [HeightUnit.cm.rawValue, HeightUnit.ftin.rawValue],
                        selected: heightUnit.rawValue,
                        compact: true
                    ) { picked in
                        let newUnit = HeightUnit(rawValue: picked) ?? .cm
                        guard newUnit != heightUnit else { return }
                        if newUnit == .ftin {
                            heightInch = Int((Double(heightCm) / 2.54).rounded())
                        }
                        heightUnit = newUnit
                    }
                }
                ONWheelCard {
                    if heightUnit == .cm {
                        Picker("Größe", selection: $heightCm) {
                            ForEach(140...220, id: \.self) { c in
                                Text("\(c) cm").tag(c)
                            }
                        }
                        .pickerStyle(.wheel)
                    } else {
                        Picker("Größe", selection: $heightInch) {
                            ForEach(55...86, id: \.self) { inch in
                                Text("\(inch / 12)'\(inch % 12)\"").tag(inch)
                            }
                        }
                        .pickerStyle(.wheel)
                        .onChange(of: heightInch) { _, newInch in
                            heightCm = min(220, max(140, Int((Double(newInch) * 2.54).rounded())))
                        }
                    }
                }
            }
            .padding(.top, 16)

            Spacer()

            PrimaryButton(title: "Weiter", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
        }
        .padding(.top, 72)
    }
}

private struct ONWheelCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .frame(height: 118)
            .frame(maxWidth: .infinity)
            .clipped()
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
    }
}

// MARK: - Page 4: Favourite drinks (searchable catalog)

private struct ONFavoritesPage: View {
    let templates: [DrinkTemplate]
    @Binding var favoriteIDs: [UUID]
    let onFinish: () -> Void

    @State private var searchText = ""
    @State private var selectedCategory: DrinkCategory? = nil
    @FocusState private var searchFocused: Bool

    private var filtered: [DrinkTemplate] {
        var list = templates
        if let cat = selectedCategory {
            list = list.filter { $0.category == cat }
        }
        let query = searchText.trimmingCharacters(in: .whitespaces)
        if !query.isEmpty {
            list = list.filter { $0.name.localizedStandardContains(query) }
        }
        return list
    }

    private var selectedTemplates: [DrinkTemplate] {
        favoriteIDs.compactMap { id in templates.first(where: { $0.id == id }) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                ONQuestionHeader(
                    title: "Deine Favoriten",
                    subtitle: "Wähle deine 4 häufigsten Drinks. Suche nach allem, was die App kennt.",
                    horizontalPadding: 0
                )
                Spacer()
                Text("\(favoriteIDs.count)/4")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.appAccent.opacity(0.1))
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 24)

            // Search field
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
                TextField("Suchen, z.B. Salitos Ice oder Kölsch", text: $searchText)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .focused($searchFocused)
                    .autocorrectionDisabled()
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.appTextMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        searchFocused ? Color.appAccent.opacity(0.6) : Color.appBorder,
                        lineWidth: searchFocused ? 1 : 0.5
                    )
            )
            .padding(.horizontal, 24)
            .padding(.top, 12)

            // Category chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ONCategoryChip(label: "Alle", isOn: selectedCategory == nil) {
                        selectedCategory = nil
                    }
                    ForEach(DrinkCategory.allCases, id: \.self) { cat in
                        ONCategoryChip(label: cat.localizedName, isOn: selectedCategory == cat) {
                            selectedCategory = selectedCategory == cat ? nil : cat
                        }
                    }
                }
                .padding(.horizontal, 24)
            }
            .padding(.top, 10)

            // Selected chips (visible while searching elsewhere)
            if !selectedTemplates.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(selectedTemplates) { t in
                            HStack(spacing: 6) {
                                Text(t.name)
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appText)
                                    .lineLimit(1)
                                Image(systemName: "xmark")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(Color.appTextDim)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(Color.appAccent.opacity(0.12))
                            .clipShape(Capsule())
                            .overlay(Capsule().strokeBorder(Color.appAccent.opacity(0.5), lineWidth: 0.5))
                            .onTapGesture {
                                favoriteIDs.removeAll { $0 == t.id }
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                }
                .padding(.top, 10)
            }

            // Results grid
            ScrollView(showsIndicators: false) {
                LazyVGrid(
                    columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
                    spacing: 12
                ) {
                    ForEach(filtered) { t in
                        ONDrinkCard(
                            template: t,
                            isSelected: favoriteIDs.contains(t.id),
                            isDimmed: favoriteIDs.count >= 4 && !favoriteIDs.contains(t.id)
                        ) {
                            toggle(t)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 8)

                if filtered.isEmpty {
                    Text(templates.isEmpty
                         ? "Der Getränkekatalog wird noch geladen."
                         : "Keine Treffer. Passe Suche oder Kategorie an.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 32)
                }
            }
            .scrollDismissesKeyboard(.immediately)

            PrimaryButton(
                title: favoriteIDs.count == 4 ? "Fertig" : "Noch \(4 - favoriteIDs.count) wählen",
                icon: favoriteIDs.count == 4 ? "checkmark" : nil,
                isDisabled: favoriteIDs.count != 4,
                action: onFinish
            )
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .padding(.bottom, 40)
        }
        .padding(.top, 64)
    }

    private func toggle(_ t: DrinkTemplate) {
        if favoriteIDs.contains(t.id) {
            favoriteIDs.removeAll { $0 == t.id }
        } else if favoriteIDs.count < 4 {
            favoriteIDs.append(t.id)
        }
    }
}

private struct ONCategoryChip: View {
    let label: String
    let isOn: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.appCaptionBold)
                .foregroundStyle(isOn ? Color.appBackground : Color.appTextDim)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isOn ? Color.appAccent : Color.appCard)
                .clipShape(Capsule())
                .overlay(
                    Capsule().strokeBorder(
                        isOn ? Color.clear : Color.appBorder,
                        lineWidth: 0.5
                    )
                )
        }
        .buttonStyle(.plain)
    }
}

private struct ONDrinkCard: View {
    let template: DrinkTemplate
    let isSelected: Bool
    let isDimmed: Bool
    let onTap: () -> Void

    private var meta: String {
        let vol = template.volume >= 1000
            ? "\(String(format: "%.1f", template.volume / 1000).replacingOccurrences(of: ".", with: ",")) l"
            : "\(Int(template.volume)) ml"
        let abv = String(format: "%.1f", template.abv).replacingOccurrences(of: ".", with: ",")
        return "\(vol) \u{00B7} \(abv)%"
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    DrinkIconView(iconName: template.iconName, name: template.name, category: template.category, size: 20)
                        .foregroundStyle(isSelected ? Color.appAccent : Color.appTextDim)
                    Spacer()
                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 17))
                            .foregroundStyle(Color.appAccent)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                Spacer(minLength: 0)
                Text(template.name)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(meta)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 96)
            .background(isSelected ? Color.appAccent.opacity(0.1) : Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: isSelected ? 1.5 : 0.5
                    )
            )
            .opacity(isDimmed ? 0.4 : 1)
        }
        .buttonStyle(.plain)
        .animation(.appSnappy, value: isSelected)
    }
}

// MARK: - Shared pieces

private struct ONQuestionHeader: View {
    let title: String
    let subtitle: String
    var horizontalPadding: CGFloat = 24

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.appSerifTitle)
                .foregroundStyle(Color.appText)
            Text(subtitle)
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, horizontalPadding)
    }
}

private struct ONUnitToggle: View {
    let options: [String]
    let selected: String
    var compact: Bool = false
    let onPick: (String) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { opt in
                Button {
                    onPick(opt)
                } label: {
                    Text(opt)
                        .font(.appCaptionBold)
                        .foregroundStyle(opt == selected ? Color.appBackground : Color.appTextDim)
                        .padding(.horizontal, compact ? 12 : 20)
                        .padding(.vertical, compact ? 5 : 8)
                        .background(opt == selected ? Color.appAccent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 9))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .padding(.horizontal, compact ? 0 : 24)
        .animation(.appSnappy, value: selected)
    }
}

// MARK: - Preview

#Preview {
    let config = ModelConfiguration(isStoredInMemoryOnly: true)
    let container = try! ModelContainer(for: PersistenceController.schema, configurations: config)
    DrinkDatabase.seedIfNeeded(in: container.mainContext)
    return OnboardingView()
        .modelContainer(container)
        .preferredColorScheme(.dark)
}
