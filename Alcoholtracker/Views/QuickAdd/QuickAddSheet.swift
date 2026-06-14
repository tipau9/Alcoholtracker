import SwiftUI
import SwiftData

// MARK: - QuickAddSheet
// Full-height sheet: favourites grid with BAC preview, search, category list,
// and custom brand entry at the bottom.

struct QuickAddSheet: View {

    let profile: UserProfile?
    let lastBottleLevels: [UUID: Double]
    let onAdd: (Drink) -> Void
    let onBottleDrink: ((DrinkTemplate, Double, Double, Double) -> Void)?   // template, size, start, current
    let onStartSipCounter: ((DrinkTemplate) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(SupabaseService.self) private var supabase

    @Query(sort: [SortDescriptor(\DrinkTemplate.usageCount, order: .reverse)])
    private var allTemplates: [DrinkTemplate]

    @Query private var recentDrinks: [Drink]

    @State private var searchQuery = ""
    @State private var debouncedQuery = ""
    @State private var showBottomBar = true
    @State private var showCustomEntry = false
    @State private var showMixCreator = false
    @State private var showQuickMix = false
    @State private var showBottleMode = false
    @State private var showSipPicker = false
    @State private var amountTemplate: DrinkTemplate? = nil
    @State private var selectedCategory: DrinkCategory? = nil
    @State private var showBarcodeScanner = false
    @State private var barcodeCandidate: DrinkTemplateCandidate? = nil
    @State private var isLookingUpBarcode = false
    @State private var barcodeError: String? = nil

    init(
        profile: UserProfile?,
        lastBottleLevels: [UUID: Double] = [:],
        onAdd: @escaping (Drink) -> Void,
        onBottleDrink: ((DrinkTemplate, Double, Double, Double) -> Void)? = nil,
        onStartSipCounter: ((DrinkTemplate) -> Void)? = nil
    ) {
        self.profile = profile
        self.lastBottleLevels = lastBottleLevels
        self.onAdd = onAdd
        self.onBottleDrink = onBottleDrink
        self.onStartSipCounter = onStartSipCounter
        let cutoff = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        _recentDrinks = Query(
            filter: #Predicate<Drink> { d in d.timestamp >= cutoff }
        )
    }

    // PERF: pre-grouped dictionary so we don't filter 800+ items per category per render
    private var templatesByCategory: [DrinkCategory: [DrinkTemplate]] {
        Dictionary(grouping: allTemplates, by: \.category)
    }

    private var favourites: [DrinkTemplate] {
        var counts: [UUID: Int] = [:]
        for d in recentDrinks {
            guard let tid = d.templateID else { continue }
            counts[tid, default: 0] += 1
        }
        let sorted = allTemplates
            .filter { counts[$0.id] != nil }
            .sorted { (counts[$0.id] ?? 0) > (counts[$1.id] ?? 0) }
        return Array(sorted.prefix(6))
    }

    // PERF: capped at 50, uses debouncedQuery so we don't recompute on every keystroke
    private var searchResults: [DrinkTemplate] {
        guard !debouncedQuery.isEmpty else { return [] }
        let lower = debouncedQuery.lowercased()
        return Array(allTemplates
            .lazy
            .filter { $0.name.localizedStandardContains(lower) }
            .prefix(50))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                QAHandle()

                QAHeader(title: "Drink hinzufügen") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 12)

                HStack(spacing: 8) {
                    QASearchBar(text: $searchQuery)
                    Button {
                        showBarcodeScanner = true
                    } label: {
                        Image(systemName: isLookingUpBarcode ? "rays" : "barcode.viewfinder")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.appAccent)
                            .frame(width: 44, height: 44)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                    .disabled(isLookingUpBarcode)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 10)

                Divider()
                    .background(Color.appBorder)

                ZStack(alignment: .bottom) {
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 0) {
                            GeometryReader { geo in
                                Color.clear.preference(
                                    key: QAScrollOffsetPreferenceKey.self,
                                    value: geo.frame(in: .named("qa_scroll")).minY
                                )
                            }
                            .frame(height: 0)

                            if debouncedQuery.isEmpty {
                                if !favourites.isEmpty {
                                    QAFavouritesSection(
                                        templates: favourites,
                                        profile: profile,
                                        onAdd: { amountTemplate = $0 },
                                        onLongPress: { amountTemplate = $0 }
                                    )
                                    .padding(.horizontal, 16)
                                    .padding(.top, 20)
                                }

                                // PERF: use pre-grouped dict to avoid O(n*k) linear scans
                                let groups = templatesByCategory
                                QACategoryFilterBar(
                                    categories: DrinkCategory.allCases.filter { groups[$0] != nil },
                                    selected: $selectedCategory
                                )
                                .padding(.top, 16)

                                ForEach(DrinkCategory.allCases, id: \.self) { cat in
                                    if selectedCategory == nil || selectedCategory == cat {
                                        if let items = groups[cat], !items.isEmpty {
                                            QACategorySection(
                                                category: cat,
                                                templates: items,
                                                profile: profile,
                                                onAdd: { amountTemplate = $0 },
                                                onLongPress: { amountTemplate = $0 }
                                            )
                                            .padding(.horizontal, 16)
                                            .padding(.top, 20)
                                        }
                                    }
                                }
                            } else {
                                QASearchResults(
                                    templates: searchResults,
                                    query: debouncedQuery,
                                    profile: profile,
                                    onAdd: { amountTemplate = $0 },
                                    onLongPress: { amountTemplate = $0 },
                                    onCustom: { showCustomEntry = true }
                                )
                                .padding(.horizontal, 16)
                                .padding(.top, 16)
                            }

                            // Fixed bottom clearance so list items are never hidden behind the bar.
                            Color.clear.frame(height: 100)
                        }
                    }
                    .coordinateSpace(name: "qa_scroll")
                    .onPreferenceChange(QAScrollOffsetPreferenceKey.self) { value in
                        // Hysteresis: different thresholds for hide vs. show prevent the feedback
                        // loop that safeAreaInset caused (layout change -> offset jump -> re-show).
                        let shouldHide = value < -40
                        let shouldShow = value > -10
                        if shouldHide && showBottomBar {
                            withAnimation(.easeInOut(duration: 0.2)) { showBottomBar = false }
                        } else if shouldShow && !showBottomBar {
                            withAnimation(.easeInOut(duration: 0.2)) { showBottomBar = true }
                        }
                    }

                    if showBottomBar {
                        QABottomBar(
                            onCustomBrand:  { showCustomEntry = true },
                            onQuickMix:     { showQuickMix = true },
                            onMixCreator:   { showMixCreator = true },
                            onBottleMode:   { showBottleMode = true },
                            onSipCounter:   onStartSipCounter != nil ? { showSipPicker = true } : nil
                        )
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: showBottomBar)
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        // PERF: 150ms debounce — avoids refiltering 800+ items on every keystroke
        .task(id: searchQuery) {
            try? await Task.sleep(for: .milliseconds(150))
            debouncedQuery = searchQuery
        }
        .sheet(isPresented: $showBottleMode) {
            BottleModeSheet(
                profile: profile,
                allTemplates: allTemplates,
                lastBottleLevels: lastBottleLevels,
                onAdd: { template, size, start, current in
                    if let cb = onBottleDrink {
                        cb(template, size, start, current)
                    } else {
                        // Fallback: create drink directly when no session callback provided
                        let ml = (start - current) * size
                        guard ml > 0 else { return }
                        let cal = template.volume > 0
                            ? Int(Double(template.calories) / template.volume * ml) : 0
                        let d = Drink(name: template.name, volume: ml, abv: template.abv,
                                      calories: cal, iconName: template.iconName,
                                      category: template.category, templateID: template.id)
                        template.usageCount += 1
                        onAdd(d)
                    }
                    dismiss()
                }
            )
        }
        .sheet(isPresented: $showSipPicker) {
            SipTemplatePicker(allTemplates: allTemplates) { template in
                showSipPicker = false
                onStartSipCounter?(template)
                dismiss()
            }
        }
        .sheet(isPresented: $showQuickMix) {
            QuickMixSheet(profile: profile) { drink in
                onAdd(drink)
                dismiss()
            }
        }
        .sheet(isPresented: $showMixCreator) {
            MixCreatorSheet(profile: profile) { drink in
                onAdd(drink)
                dismiss()
            }
        }
        .sheet(isPresented: $showCustomEntry) {
            CustomBrandSheet(profile: profile) { name, volume, abv in
                let cal = Int(volume * abv / 100.0 * 0.789 * 7.1)
                let template = DrinkTemplate(
                    name: name,
                    category: .other,
                    volume: volume,
                    abv: abv,
                    calories: cal,
                    isCustom: true
                )
                context.insert(template)
                try? context.save()
                pick(template)
            }
        }
        .sheet(item: $amountTemplate) { template in
            AmountInputSheet(template: template, profile: profile) { drink in
                onAdd(drink)
                dismiss()
            }
        }
        .fullScreenCover(isPresented: $showBarcodeScanner) {
            BarcodeScannerView(
                onBarcodeDetected: { code in
                    showBarcodeScanner = false
                    isLookingUpBarcode = true
                    barcodeError = nil
                    Task {
                        defer { isLookingUpBarcode = false }
                        do {
                            // 1. Check community DB first (faster + grows with each scan)
                            if let row = try? await supabase.lookupCommunityBarcode(code) {
                                barcodeCandidate = DrinkTemplateCandidate(
                                    name:     row.name,
                                    abv:      row.abv,
                                    barcode:  row.barcode,
                                    volume:   row.volume,
                                    category: DrinkCategory(rawValue: row.category) ?? .beer
                                )
                                return
                            }
                            // 2. Fall back to Open Food Facts
                            if let candidate = try await BarcodeService.lookup(barcode: code) {
                                barcodeCandidate = candidate
                            } else {
                                // Not in any database: let the user enter it by
                                // hand. The manual entry carries the scanned
                                // barcode and feeds the community DB, so the app
                                // learns products that exist nowhere else.
                                barcodeCandidate = DrinkTemplateCandidate(
                                    name: "", abv: 0, barcode: code,
                                    volume: 330, category: .beer,
                                    foundInDatabase: false
                                )
                            }
                        } catch {
                            barcodeError = "Netzwerkfehler beim Barcode-Lookup."
                        }
                    }
                },
                onCancel: { showBarcodeScanner = false }
            )
        }
        .sheet(isPresented: Binding(
            get: { barcodeCandidate != nil },
            set: { if !$0 { barcodeCandidate = nil } }
        )) {
            if let candidate = barcodeCandidate {
                BarcodeCandidateSheet(candidate: candidate, profile: profile) { name, vol, abv, category in
                    let cal = Int(vol * abv / 100.0 * 0.789 * 7.1)
                    let template = DrinkTemplate(
                        name: name, category: category,
                        volume: vol, abv: abv, calories: cal, isCustom: true
                    )
                    template.barcode = candidate.barcode
                    context.insert(template)
                    try? context.save()
                    onAdd(Drink.from(template: template))
                    // Upload to community DB so other users benefit from this
                    // scan (also for products entered fully by hand).
                    let capturedBarcode = candidate.barcode
                    Task {
                        try? await supabase.contributeDrink(
                            name:     name,
                            category: category,
                            volume:   vol,
                            abv:      abv,
                            calories: cal,
                            iconName: category.symbolName,
                            barcode:  capturedBarcode
                        )
                    }
                    barcodeCandidate = nil
                    dismiss()
                }
            }
        }
        .alert("Barcode-Fehler", isPresented: Binding(
            get: { barcodeError != nil },
            set: { if !$0 { barcodeError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(barcodeError ?? "")
        }
    }

    private func pick(_ template: DrinkTemplate) {
        let drink = Drink.from(template: template)
        onAdd(drink)
        dismiss()
    }
}

// MARK: - Sheet chrome

private struct QAHandle: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2.5)
            .fill(Color.appBorder)
            .frame(width: 36, height: 5)
            .padding(.top, 12)
            .padding(.bottom, 4)
    }
}

private struct QAHeader: View {
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

private struct QASearchBar: View {
    @Binding var text: String
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15))
                .foregroundStyle(Color.appTextDim)

            TextField("Drink suchen...", text: $text)
                .font(.appBody)
                .foregroundStyle(Color.appText)
                .autocorrectionDisabled()
                .focused($focused)

            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(Color.appTextDim)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        // Make the whole pill (icon + text field + clear button) a single tap target.
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture { focused = true }
    }
}

// MARK: - BAC contribution helper (shared by Favourites, Category, and Search sections)

private func bacContribution(for template: DrinkTemplate, profile: UserProfile?) -> Double? {
    guard let p = profile else { return nil }
    return BACCalculator.bacContribution(
        volume: template.volume, abv: template.abv,
        weight: p.weight, distributionFactor: p.distributionFactor
    )
}

// MARK: - Favourites Grid

private struct QAFavouritesSection: View {
    let templates: [DrinkTemplate]
    let profile: UserProfile?
    let onAdd: (DrinkTemplate) -> Void
    let onLongPress: (DrinkTemplate) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "FAVORITEN")
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
                spacing: 12
            ) {
                ForEach(templates) { t in
                    QADrinkCard(
                        template: t,
                        contribution: bacContribution(for: t, profile: profile),
                        onAdd: { onAdd(t) },
                        onLongPress: { onLongPress(t) }
                    )
                }
            }
        }
    }

}

private struct QADrinkCard: View {
    let template: DrinkTemplate
    let contribution: Double?
    let onAdd: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: template.iconName)
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 44, height: 44)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            Text(template.name)
                .font(.appBodyBold)
                .foregroundStyle(Color.appText)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            Text("\(Int(template.volume)) ml · \(String(format: "%.1f", template.abv)) %")
                .font(.appMicro)
                .foregroundStyle(Color.appTextDim)

            if let c = contribution {
                QABACBadge(contribution: c)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .onTapGesture { onAdd() }
        .onLongPressGesture(minimumDuration: 0.5, perform: { onLongPress() })
    }
}

// MARK: - Category List

private struct QACategorySection: View {
    let category: DrinkCategory
    let templates: [DrinkTemplate]
    let profile: UserProfile?
    let onAdd: (DrinkTemplate) -> Void
    let onLongPress: (DrinkTemplate) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: category.symbolName)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.appAccent)
                SectionLabel(text: category.localizedName.uppercased())
            }
            VStack(spacing: 0) {
                ForEach(0..<templates.count, id: \.self) { idx in
                    let t = templates[idx]
                    QADrinkRow(
                        template: t,
                        contribution: bacContribution(for: t, profile: profile),
                        onAdd: { onAdd(t) },
                        onLongPress: { onLongPress(t) }
                    )
                    if idx < templates.count - 1 {
                        Divider()
                            .background(Color.appBorder.opacity(0.5))
                            .padding(.leading, 62)
                    }
                }
            }
            .padding(.horizontal, 14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }
}

private struct QADrinkRow: View {
    let template: DrinkTemplate
    let contribution: Double?
    let onAdd: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: template.iconName)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 34, height: 34)
                .background(Color.appAccent.opacity(0.10))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(template.name)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .lineLimit(1)
                Text("\(Int(template.volume)) ml · \(String(format: "%.1f", template.abv)) %")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            if let c = contribution {
                QABACBadge(contribution: c)
            }

            Image(systemName: "plus.circle")
                .font(.system(size: 18, weight: .light))
                .foregroundStyle(Color.appAccent)
        }
        .padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture { onAdd() }
        .onLongPressGesture(minimumDuration: 0.5, perform: { onLongPress() })
    }
}

// MARK: - Search Results

private struct QASearchResults: View {
    let templates: [DrinkTemplate]
    let query: String
    let profile: UserProfile?
    let onAdd: (DrinkTemplate) -> Void
    let onLongPress: (DrinkTemplate) -> Void
    let onCustom: () -> Void

    var body: some View {
        if templates.isEmpty {
            VStack(spacing: 20) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 40, weight: .ultraLight))
                    .foregroundStyle(Color.appTextDim)
                Text("Kein Ergebnis für \"\(query)\"")
                    .font(.appBody)
                    .foregroundStyle(Color.appTextDim)
                    .multilineTextAlignment(.center)
                Button("Als eigene Marke erfassen", action: onCustom)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 48)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                SectionLabel(text: "ERGEBNISSE")
                VStack(spacing: 0) {
                    ForEach(0..<templates.count, id: \.self) { idx in
                        let t = templates[idx]
                        QADrinkRow(
                            template: t,
                            contribution: bacContribution(for: t, profile: profile),
                            onAdd: { onAdd(t) },
                            onLongPress: { onLongPress(t) }
                        )
                        if idx < templates.count - 1 {
                            Divider()
                                .background(Color.appBorder.opacity(0.5))
                                .padding(.leading, 62)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(Color.appCard)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )
            }
        }
    }
}

// MARK: - BAC Contribution Badge

private struct QABACBadge: View {
    let contribution: Double

    private var tint: Color { contribution > 0.3 ? .statusOrange : .statusGreen }

    var body: some View {
        Text(String(format: "+%.2f‰", contribution))
            .font(.appMicro)
            .foregroundStyle(tint)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(tint.opacity(0.15))
            .clipShape(Capsule())
    }
}

// MARK: - Bottom Bar (pinned): compact icon-chip row

private struct QABottomBar: View {
    let onCustomBrand:  () -> Void
    let onQuickMix:     () -> Void
    let onMixCreator:   () -> Void
    let onBottleMode:   () -> Void
    var onSipCounter:   (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 0) {
            QAActionChip(icon: "waterbottle.fill", title: "Flasche",   action: onBottleMode)
            if let sipAction = onSipCounter {
                QAActionChip(icon: "hand.tap.fill",  title: "Schlucke", action: sipAction)
            }
            QAActionChip(icon: "drop.fill",          title: "Quick Mix", action: onQuickMix)
            QAActionChip(icon: "wineglass",          title: "Cocktail",  action: onMixCreator)
            QAActionChip(icon: "plus.circle.fill",   title: "Eigene",    action: onCustomBrand)
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 20)
        .background(Color.appBackground.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            LinearGradient(
                colors: [Color.appBackground.opacity(0), Color.appBackground],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 28)
            .offset(y: -28)
            .allowsHitTesting(false)
        }
    }
}

private struct QAActionChip: View {
    let icon: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 52, height: 46)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )
                Text(title)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Category Filter

private struct QACategoryFilterBar: View {
    let categories: [DrinkCategory]
    @Binding var selected: DrinkCategory?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                QACategoryChip(icon: nil, label: "Alle", isSelected: selected == nil) {
                    selected = nil
                }
                ForEach(categories, id: \.self) { cat in
                    QACategoryChip(icon: cat.symbolName, label: cat.localizedName, isSelected: selected == cat) {
                        selected = selected == cat ? nil : cat
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

private struct QACategoryChip: View {
    let icon: String?
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 11, weight: .medium))
                }
                Text(label)
                    .font(.appCaptionBold)
            }
            .foregroundStyle(isSelected ? Color.appBackground : Color.appTextDim)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(isSelected ? Color.appAccent : Color.appCard)
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(
                isSelected ? Color.appAccent : Color.appBorder,
                lineWidth: isSelected ? 1.0 : 0.5
            ))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Custom Brand Sheet

struct CustomBrandSheet: View {

    let profile: UserProfile?
    let onSave: (String, Double, Double) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var volumeText = "330"
    @State private var abvText = "5.0"

    private var volume: Double {
        Double(volumeText.replacingOccurrences(of: ",", with: ".")) ?? 0
    }
    private var abv: Double {
        Double(abvText.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && volume > 0 && abv > 0 && abv <= 96
    }

    private var bacPreview: Double? {
        guard let p = profile, volume > 0, abv > 0 else { return nil }
        return BACCalculator.bacContribution(
            volume: volume, abv: abv,
            weight: p.weight, distributionFactor: p.distributionFactor
        )
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                QAHandle()

                QAHeader(title: "Eigene Marke") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 16)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        QAFormField(
                            label: "NAME",
                            placeholder: "z.B. Pilsner Urquell",
                            text: $name,
                            suffix: nil,
                            isNumeric: false
                        )

                        HStack(spacing: 12) {
                            QAFormField(
                                label: "MENGE",
                                placeholder: "330",
                                text: $volumeText,
                                suffix: "ml",
                                isNumeric: true
                            )
                            QAFormField(
                                label: "ALKOHOL",
                                placeholder: "5.0",
                                text: $abvText,
                                suffix: "%",
                                isNumeric: true
                            )
                        }

                        if let bac = bacPreview {
                            QABACPreviewRow(contribution: bac)
                        }

                        PrimaryButton(
                            title: "Hinzufügen",
                            icon: "plus",
                            isDisabled: !isValid
                        ) {
                            onSave(
                                name.trimmingCharacters(in: .whitespaces),
                                volume, abv
                            )
                            dismiss()
                        }
                        .padding(.top, 8)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    .padding(.bottom, 48)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Form Field

private struct QAFormField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let suffix: String?
    var isNumeric: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionLabel(text: label)
            HStack(spacing: 8) {
                TextField(placeholder, text: $text)
                    .keyboardType(isNumeric ? .decimalPad : .default)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .autocorrectionDisabled()
                if let suffix {
                    Text(suffix)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextMuted)
                }
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
    }
}

// MARK: - BAC Preview Row

private struct QABACPreviewRow: View {
    let contribution: Double

    var body: some View {
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

            Text(String(format: "+%.2f ‰", contribution))
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
}

// MARK: - Category picker row (barcode candidate)

private struct BCCategoryRow: View {
    @Binding var category: DrinkCategory

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionLabel(text: "KATEGORIE")
            Menu {
                Picker("Kategorie", selection: $category) {
                    ForEach(DrinkCategory.allCases, id: \.self) { c in
                        Label(c.localizedName, systemImage: c.symbolName).tag(c)
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: category.symbolName)
                        .font(.system(size: 14))
                        .foregroundStyle(Color.appAccent)
                    Text(category.localizedName)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 12))
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
        }
    }
}

// MARK: - BarcodeCandidateSheet (B8)

struct BarcodeCandidateSheet: View {
    let candidate: DrinkTemplateCandidate
    let profile: UserProfile?
    let onConfirm: (String, Double, Double, DrinkCategory) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var volumeText: String
    @State private var abvText: String
    @State private var category: DrinkCategory

    init(candidate: DrinkTemplateCandidate, profile: UserProfile?, onConfirm: @escaping (String, Double, Double, DrinkCategory) -> Void) {
        self.candidate = candidate
        self.profile = profile
        self.onConfirm = onConfirm
        _name = State(initialValue: candidate.name)
        _volumeText = State(initialValue: "\(Int(candidate.volume))")
        _abvText = State(initialValue: String(format: "%.1f", candidate.abv))
        _category = State(initialValue: candidate.category)
    }

    private var volume: Double { Double(volumeText.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private var abv: Double { Double(abvText.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private var isValid: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty && volume > 0 && abv > 0 }

    private var bacPreview: Double? {
        guard let p = profile, volume > 0, abv > 0 else { return nil }
        return BACCalculator.bacContribution(
            volume: volume, abv: abv,
            weight: p.weight, distributionFactor: p.distributionFactor
        )
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                QAHandle()
                QAHeader(title: candidate.foundInDatabase ? "Gescannter Drink" : "Neues Produkt") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 16)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        HStack(spacing: 8) {
                            Image(systemName: candidate.foundInDatabase ? "barcode.viewfinder" : "plus.viewfinder")
                                .font(.system(size: 13))
                                .foregroundStyle(candidate.foundInDatabase ? Color.statusGreen : Color.appAccent)
                            Text(candidate.foundInDatabase
                                 ? "Gefunden bei Open Food Facts"
                                 : "Nicht in der Datenbank. Trag die Werte ein, dann lernt die App diesen Barcode.")
                                .font(.appCaption)
                                .foregroundStyle(candidate.foundInDatabase ? Color.statusGreen : Color.appTextDim)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        QAFormField(label: "NAME", placeholder: "Produktname", text: $name, suffix: nil, isNumeric: false)
                        BCCategoryRow(category: $category)
                        HStack(spacing: 12) {
                            QAFormField(label: "MENGE", placeholder: "330", text: $volumeText, suffix: "ml", isNumeric: true)
                            QAFormField(label: "ALKOHOL", placeholder: "5.0", text: $abvText, suffix: "%", isNumeric: true)
                        }
                        if let bac = bacPreview {
                            QABACPreviewRow(contribution: bac)
                        }
                        PrimaryButton(title: "Hinzufügen", icon: "plus", isDisabled: !isValid) {
                            onConfirm(name.trimmingCharacters(in: .whitespaces), volume, abv, category)
                        }
                        .padding(.top, 8)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    .padding(.bottom, 48)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - SipTemplatePicker (used by showSipPicker sheet)

private struct SipTemplatePicker: View {
    let allTemplates: [DrinkTemplate]
    let onSelect: (DrinkTemplate) -> Void
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    private var results: [DrinkTemplate] {
        if query.isEmpty { return Array(allTemplates.prefix(50)) }
        return Array(allTemplates.lazy.filter { $0.name.localizedStandardContains(query) }.prefix(30))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Text("Schluck-Zahler starten")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appTextDim)
                            .frame(width: 30, height: 30).background(Color.appCard).clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }.buttonStyle(.plain)
                }
                .padding(.horizontal, 20).padding(.vertical, 14)

                HStack {
                    Image(systemName: "magnifyingglass").font(.system(size: 14)).foregroundStyle(Color.appTextDim)
                    TextField("Getränk suchen...", text: $query).font(.appBody).foregroundStyle(Color.appText)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(Color.appCard).clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.appBorder, lineWidth: 0.5))
                .padding(.horizontal, 16).padding(.bottom, 8)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        ForEach(results) { t in
                            Button { onSelect(t) } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: t.iconName).font(.system(size: 15))
                                        .foregroundStyle(Color.appAccent).frame(width: 34, height: 34)
                                        .background(Color.appAccent.opacity(0.1))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(t.name).font(.appBody).foregroundStyle(Color.appText).lineLimit(1)
                                        Text("\(t.abv, specifier: "%.1f")% vol").font(.appCaption).foregroundStyle(Color.appTextDim)
                                    }
                                    Spacer()
                                    Image(systemName: "hand.tap.fill").font(.system(size: 12)).foregroundStyle(Color.appAccent)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 11)
                            }.buttonStyle(.plain)
                            Divider().background(Color.appBorder).padding(.leading, 62)
                        }
                    }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }
}

// MARK: - Preview

#Preview("QuickAdd") {
    let controller = PersistenceController.preview
    let profile = try? controller.container.mainContext.fetch(
        FetchDescriptor<UserProfile>()
    ).first
    return QuickAddSheet(profile: profile) { _ in }
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}

#Preview("Eigene Marke") {
    let controller = PersistenceController.preview
    let profile = try? controller.container.mainContext.fetch(
        FetchDescriptor<UserProfile>()
    ).first
    return CustomBrandSheet(profile: profile) { _, _, _ in }
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}

// MARK: - Scroll Offset Preference Key
struct QAScrollOffsetPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}
