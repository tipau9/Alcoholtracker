import SwiftUI
import SwiftData

// MARK: - MixCreatorSheet
// Compose a cocktail mix from individual ingredients with live BAC preview.
// "Sofort trinken" adds a one-off Drink; "Speichern" also persists a CustomMix
// and DrinkTemplate so it appears in QuickAdd next time.

struct MixCreatorSheet: View {

    let profile: UserProfile?
    let onAdd: (Drink) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(SupabaseService.self) private var supabase

    @State private var mixName = ""
    @State private var ingredients: [MixIngredient] = []
    @State private var showAddForm = false
    @State private var showCommunity = false
    @State private var shareConfirm = false

    private var totalBACContribution: Double {
        guard let p = profile else { return 0 }
        return ingredients.reduce(0.0) { sum, ing in
            sum + BACCalculator.bacContribution(
                volume: ing.volume,
                abv: ing.abv,
                weight: p.weight,
                distributionFactor: p.distributionFactor
            )
        }
    }

    private var isReadyToAdd: Bool  { !ingredients.isEmpty }
    private var isReadyToSave: Bool {
        isReadyToAdd && !mixName.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                MCHandle()

                MCHeader(title: "Mix erstellen") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 16)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 24) {
                        Button { showCommunity = true } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "person.2.wave.2.fill")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Color.appAccent)
                                Text("Community-Mische ansehen")
                                    .font(.appBody)
                                    .foregroundStyle(Color.appText)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(Color.appTextDim)
                            }
                            .padding(14)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
                        }
                        .buttonStyle(.plain)

                        MCNameField(text: $mixName)
                        ingredientsSection
                        if !ingredients.isEmpty {
                            MCPreviewCard(
                                ingredients: ingredients,
                                profile: profile,
                                totalBAC: totalBACContribution
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 12)
                }
                .safeAreaInset(edge: .bottom) {
                    Group {
                        if isReadyToAdd {
                            MCActionBar(
                                isReadyToSave: isReadyToSave,
                                onDrink: {
                                    let mix = buildMix()
                                    onAdd(mix.asDrink())
                                    dismiss()
                                },
                                onSave: {
                                    let mix = buildMix()
                                    context.insert(mix)
                                    context.insert(mix.asTemplate())
                                    try? context.save()
                                    onAdd(mix.asDrink())
                                    dismiss()
                                },
                                onShare: { shareMix() }
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                    .animation(.easeInOut(duration: 0.22), value: isReadyToAdd)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .sheet(isPresented: $showCommunity) {
            CommunityMixesSheet { row in
                // Import an approved community mix into the user's own mixes.
                let mix = CustomMix(name: row.name, ingredients: row.ingredients)
                context.insert(mix)
                context.insert(mix.asTemplate())
                try? context.save()
            }
        }
        .alert("Mix geteilt", isPresented: $shareConfirm) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Danke! Dein Mix wird für andere sichtbar, sobald genug Leute ihn teilen oder er freigegeben wird.")
        }
    }

    private func shareMix() {
        let mix = buildMix()
        // Save locally too so the user keeps their own copy.
        context.insert(mix)
        context.insert(mix.asTemplate())
        try? context.save()
        let ings = mix.ingredients
        let vol = mix.totalVolume
        let abv = mix.totalAbv
        let cal = mix.estimatedCalories
        let name = mix.name
        Task { try? await supabase.contributeMix(name: name, ingredients: ings, totalVolume: vol, totalAbv: abv, calories: cal) }
        shareConfirm = true
    }

    // MARK: Ingredients section

    private var ingredientsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "ZUTATEN")

            if !ingredients.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(ingredients.enumerated()), id: \.element.id) { (i, ing) in
                        MCIngredientRow(ingredient: ing) {
                            withAnimation { ingredients.removeAll { $0.id == ing.id } }
                        }
                        if i < ingredients.count - 1 {
                            Divider()
                                .background(Color.appBorder)
                                .padding(.leading, 54)
                        }
                    }
                }
                .background(Color.appCard)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )
            }

            if showAddForm {
                MCAddIngredientForm { name, volume, abv in
                    let ing = MixIngredient(name: name, abv: abv, volume: volume)
                    withAnimation { ingredients.append(ing) }
                    showAddForm = false
                } onCancel: {
                    showAddForm = false
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            } else {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) { showAddForm = true }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.appAccent)
                        Text("Zutat hinzufügen")
                            .font(.appBody)
                            .foregroundStyle(Color.appAccent)
                        Spacer()
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
            }
        }
        .animation(.easeInOut(duration: 0.18), value: showAddForm)
    }

    private func buildMix() -> CustomMix {
        let name = mixName.trimmingCharacters(in: .whitespaces).isEmpty
            ? "Mix"
            : mixName.trimmingCharacters(in: .whitespaces)
        return CustomMix(name: name, ingredients: ingredients)
    }
}

// MARK: - Sheet chrome

private struct MCHandle: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2.5)
            .fill(Color.appBorder)
            .frame(width: 36, height: 5)
            .padding(.top, 12)
            .padding(.bottom, 4)
    }
}

private struct MCHeader: View {
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

// MARK: - Name field

private struct MCNameField: View {
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "MIX-NAME")
            TextField("z.B. Gin Tonic", text: $text)
                .font(.appBody)
                .foregroundStyle(Color.appText)
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

// MARK: - Ingredient row

private struct MCIngredientRow: View {
    let ingredient: MixIngredient
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "drop.fill")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 30, height: 30)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(ingredient.name)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                Text(String(format: "%.0f ml, %.1f%%", ingredient.volume, ingredient.abv))
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            Button(action: onDelete) {
                Image(systemName: "trash")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.statusRed)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }
}

// MARK: - Add ingredient form

private struct MCAddIngredientForm: View {
    let onAdd: (String, Double, Double) -> Void
    let onCancel: () -> Void

    @State private var name = ""
    @State private var volumeStr = ""
    @State private var abvStr = ""

    private var volume: Double? { Double(volumeStr.replacingOccurrences(of: ",", with: ".")) }
    private var abv: Double?    { Double(abvStr.replacingOccurrences(of: ",", with: ".")) }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && (volume ?? 0) > 0
        && (abv ?? 0) > 0
        && (abv ?? 101) <= 96
    }

    var body: some View {
        VStack(spacing: 12) {
            TextField("Name (z.B. Gin)", text: $name)
                .font(.appBody)
                .foregroundStyle(Color.appText)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color.appBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )

            HStack(spacing: 10) {
                MCNumericField(placeholder: "Menge (ml)", text: $volumeStr)
                MCNumericField(placeholder: "Alkohol (%)", text: $abvStr)
            }

            HStack(spacing: 10) {
                Button("Abbrechen", action: onCancel)
                    .font(.appBody)
                    .foregroundStyle(Color.appTextDim)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.appBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )
                    .buttonStyle(.plain)

                Button {
                    guard let v = volume, let a = abv else { return }
                    onAdd(name.trimmingCharacters(in: .whitespaces), v, a)
                } label: {
                    Text("Hinzufügen")
                        .font(.appBodyBold)
                        .foregroundStyle(isValid ? Color.appBackground : Color.appTextMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(isValid ? Color.appAccent : Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(!isValid)
            }
        }
        .padding(16)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appAccent.opacity(0.35), lineWidth: 1)
        )
    }
}

private struct MCNumericField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        TextField(placeholder, text: $text)
            .keyboardType(.decimalPad)
            .font(.appBody)
            .foregroundStyle(Color.appText)
            .autocorrectionDisabled()
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.appBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
    }
}

// MARK: - Preview card

private struct MCPreviewCard: View {
    let ingredients: [MixIngredient]
    let profile: UserProfile?
    let totalBAC: Double

    private var totalVolume: Double {
        ingredients.reduce(0) { $0 + $1.volume }
    }
    private var effectiveABV: Double {
        guard totalVolume > 0 else { return 0 }
        let pure = ingredients.reduce(0.0) { $0 + $1.volume * ($1.abv / 100.0) }
        return (pure / totalVolume) * 100.0
    }
    private var calories: Int {
        Int(ingredients.reduce(0.0) { $0 + $1.alcoholGrams } * 7)
    }
    private var badgeColor: Color { totalBAC > 0.3 ? Color.statusOrange : Color.statusGreen }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "VORSCHAU")

            VStack(spacing: 0) {
                MCPreviewRow(
                    label: "Gesamtmenge",
                    value: String(format: "%.0f ml", totalVolume)
                )
                Divider().background(Color.appBorder).padding(.leading, 16)

                MCPreviewRow(
                    label: "Eff. Alkohol",
                    value: String(format: "%.1f %%", effectiveABV)
                )
                Divider().background(Color.appBorder).padding(.leading, 16)

                MCPreviewRow(
                    label: "Kalorien",
                    value: "\(calories) kcal"
                )
                Divider().background(Color.appBorder).padding(.leading, 16)

                HStack {
                    MCPreviewRow(
                        label: "BAC-Beitrag",
                        value: String(format: "+%.2f ‰", totalBAC)
                    )
                    if totalBAC > 0 {
                        Text(totalBAC > 0.3 ? "Stark" : "Moderat")
                            .font(.appCaptionBold)
                            .foregroundStyle(badgeColor)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(badgeColor.opacity(0.15))
                            .clipShape(Capsule())
                            .padding(.trailing, 16)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }
}

private struct MCPreviewRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appTextDim)
            Spacer()
            Text(value)
                .font(.appBodyBold)
                .foregroundStyle(Color.appText)
                .monospacedDigit()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
    }
}

// MARK: - Action bar

private struct MCActionBar: View {
    let isReadyToSave: Bool
    let onDrink: () -> Void
    let onSave: () -> Void
    let onShare: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            // Share the recipe to the community (needs a name + ingredients).
            Button(action: onShare) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isReadyToSave ? Color.appAccent : Color.appTextMuted)
                    .frame(width: 52, height: 52)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.appBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .disabled(!isReadyToSave)

            Button(action: onDrink) {
                HStack(spacing: 6) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 13, weight: .semibold))
                    Text("Sofort trinken")
                        .font(.appBodyBold)
                }
                .foregroundStyle(Color.appText)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(Color.appCard)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )
            }
            .buttonStyle(.plain)

            PrimaryButton(
                title: "Speichern",
                icon: "square.and.arrow.down",
                isDisabled: !isReadyToSave,
                action: onSave
            )
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color.appBackground.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            LinearGradient(
                colors: [Color.appBackground.opacity(0), Color.appBackground],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 24)
            .offset(y: -24)
            .allowsHitTesting(false)
        }
    }
}

#Preview {
    let controller = PersistenceController.preview
    let profile = (try? controller.container.mainContext.fetch(FetchDescriptor<UserProfile>()))?.first
    return MixCreatorSheet(profile: profile) { _ in }
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}
