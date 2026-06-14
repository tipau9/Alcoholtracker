import SwiftUI
import SwiftData

// MARK: - OnboardingView
// 3-page first-launch flow. Collects body data and optional emergency contact,
// then inserts a UserProfile with hasCompletedOnboarding = true to dismiss itself.

struct OnboardingView: View {

    @Environment(\.modelContext) private var context
    @State private var page = 0
    @State private var stepsCompleted: [String] = ["welcome"]

    // Staged profile fields
    @State private var weightStr = "70"
    @State private var heightStr = "175"
    // FIX FEATURE9: birth date instead of raw age int
    @State private var birthDate: Date = Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date()
    @State private var gender: Gender = .diverse
    @State private var emergencyName = ""
    @State private var emergencyPhone = ""

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                ONProgressDots(current: page, total: 4)
                    .padding(.top, 64)
                    .padding(.bottom, 12)

                Group {
                    switch page {
                    case 0:
                        ONWelcomePage {
                            withAnimation(.easeInOut(duration: 0.28)) { page = 1 }
                        }
                    case 1:
                        ONProfilePage(
                            weightStr: $weightStr,
                            heightStr: $heightStr,
                            birthDate: $birthDate,
                            gender: $gender
                        ) {
                            stepsCompleted.append("profile")
                            withAnimation(.easeInOut(duration: 0.28)) { page = 2 }
                        }
                    case 2:
                        ONFeaturesPage {
                            stepsCompleted.append("features")
                            withAnimation(.easeInOut(duration: 0.28)) { page = 3 }
                        }
                    default:
                        ONSafetyPage(
                            emergencyName: $emergencyName,
                            emergencyPhone: $emergencyPhone,
                            onFinish: finish
                        )
                    }
                }
                .id(page)
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))
            }
        }
    }

    private func finish() {
        var completed = stepsCompleted
        completed.append("safety")

        let w = max(30.0, min(250.0, Double(weightStr.replacingOccurrences(of: ",", with: ".")) ?? 70))
        let h = max(100.0, min(250.0, Double(heightStr.replacingOccurrences(of: ",", with: ".")) ?? 175))
        let derivedAge = Calendar.current.dateComponents([.year], from: birthDate, to: Date()).year ?? 25
        let a = max(16, min(99, derivedAge))
        let name = emergencyName.trimmingCharacters(in: .whitespaces)
        let phone = emergencyPhone.filter { $0.isNumber || $0 == "+" }
        let profile = UserProfile(
            weight: w,
            height: h,
            age: a,
            gender: gender,
            emergencyContactName: name.isEmpty ? nil : name,
            emergencyContactPhone: phone.isEmpty ? nil : phone,
            hasCompletedOnboarding: true
        )
        // FIX FEATURE9: store birthDate for accurate age computation
        profile.birthDate = birthDate
        profile.onboardingStepsCompleted = completed
        context.insert(profile)
        try? context.save()
    }
}

// MARK: - Progress dots

private struct ONProgressDots: View {
    let current: Int
    let total: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<total, id: \.self) { i in
                Capsule()
                    .fill(i == current ? Color.appAccent : Color.appBorder)
                    .frame(width: i == current ? 20 : 8, height: 8)
                    .animation(.easeInOut(duration: 0.22), value: current)
            }
        }
    }
}

// MARK: - Page 0: Welcome

private struct ONWelcomePage: View {
    let onNext: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 20) {
                Image(systemName: "drop.fill")
                    .font(.system(size: 56, weight: .light))
                    .foregroundStyle(Color.appAccent)

                VStack(spacing: 10) {
                    Text("promille.")
                        .font(.appHeadline)
                        .foregroundStyle(Color.appText)
                    Text("Dein Abend. Dein Tempo.")
                        .font(.appTitle)
                        .foregroundStyle(Color.appText)
                        .multilineTextAlignment(.center)
                }

                Text("Widmark-Berechnung in Echtzeit, Freunde im Blick, Jam-Modus für die Gruppe und sichere Heimfahrt per Uber.")
                    .font(.appBody)
                    .foregroundStyle(Color.appTextDim)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)

                LazyVGrid(
                    columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                    spacing: 10
                ) {
                    ONFeatureChip(icon: "drop.fill",        label: "BAC-Schätzung")
                    ONFeatureChip(icon: "waveform",         label: "Jam-Modus")
                    ONFeatureChip(icon: "car.circle.fill",  label: "Heimfahrt")
                    ONFeatureChip(icon: "camera.fill",      label: "Erinnerungen")
                }
                .padding(.horizontal, 8)
                .padding(.top, 4)
            }
            .padding(.horizontal, 40)
            Spacer()
            PrimaryButton(title: "Weiter", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 32)
                .padding(.bottom, 48)
        }
    }
}

// MARK: - Page 1: Body data

// FIX FEATURE9: accepts birthDate Binding instead of ageStr String
private struct ONProfilePage: View {
    @Binding var weightStr: String
    @Binding var heightStr: String
    @Binding var birthDate: Date
    @Binding var gender: Gender
    let onNext: () -> Void

    private var maxBirthDate: Date {
        Calendar.current.date(byAdding: .year, value: -16, to: Date()) ?? Date()
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Körperdaten")
                            .font(.appTitle)
                            .foregroundStyle(Color.appText)
                        Text("Für eine genaue Widmark-Berechnung.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }

                    VStack(spacing: 0) {
                        ONNumRow(label: "Gewicht", unit: "kg", text: $weightStr)
                        Divider().background(Color.appBorder).padding(.leading, 16)
                        ONNumRow(label: "Größe", unit: "cm", text: $heightStr)
                        Divider().background(Color.appBorder).padding(.leading, 16)
                        HStack {
                            Text("Geburtsdatum")
                                .font(.appBody)
                                .foregroundStyle(Color.appText)
                            Spacer()
                            DatePicker("", selection: $birthDate, in: ...maxBirthDate, displayedComponents: .date)
                                .labelsHidden()
                                .colorScheme(.dark)
                                .tint(Color.appAccent)
                        }
                        .padding(.horizontal, 16)
                        .frame(height: 52)
                        Divider().background(Color.appBorder).padding(.leading, 16)
                        ONGenderRow(gender: $gender)
                    }
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }

            PrimaryButton(title: "Weiter", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 32)
                .padding(.top, 8)
                .padding(.bottom, 48)
        }
    }
}

private struct ONNumRow: View {
    let label: String
    let unit: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 12) {
            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            TextField("0", text: $text)
                .keyboardType(.decimalPad)
                .font(.appBodyBold)
                .foregroundStyle(Color.appAccent)
                .multilineTextAlignment(.trailing)
                .frame(width: 64)
            Text(unit)
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .frame(width: 36, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

private struct ONGenderRow: View {
    @Binding var gender: Gender

    var body: some View {
        HStack {
            Text("Geschlecht")
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            Picker("Geschlecht", selection: $gender) {
                ForEach(Gender.allCases, id: \.self) { g in
                    Text(g.localizedName).tag(g)
                }
            }
            .tint(Color.appAccent)
            .pickerStyle(MenuPickerStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Page 2: Emergency contact

private struct ONSafetyPage: View {
    @Binding var emergencyName: String
    @Binding var emergencyPhone: String
    let onFinish: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Notfallkontakt")
                            .font(.appTitle)
                            .foregroundStyle(Color.appText)
                        Text("Optional, aber empfohlen. Du kannst diese Person direkt aus der Safety-Ansicht anrufen – ein Tipp, auch wenn du betrunken bist.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }

                    HStack(spacing: 12) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.statusGreen)
                            .frame(width: 32, height: 32)
                            .background(Color.statusGreen.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 9))
                        Text("Ein Tipp auf \"Notfallkontakt anrufen\" reicht – selbst nachts oder wenn du die Nummer vergessen hast.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(12)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.appBorder, lineWidth: 0.5))

                    VStack(spacing: 12) {
                        ONLabeledField(
                            label: "Name",
                            placeholder: "z.B. Max Mustermann",
                            text: $emergencyName,
                            keyboard: .default
                        )
                        ONLabeledField(
                            label: "Telefonnummer",
                            placeholder: "+49 123 456789",
                            text: $emergencyPhone,
                            keyboard: .phonePad
                        )
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.appTextMuted)
                        Text("Daten werden nur lokal auf deinem Gerät gespeichert.")
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextMuted)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }

            PrimaryButton(title: "Los geht's", icon: "checkmark", action: onFinish)
                .padding(.horizontal, 32)
                .padding(.top, 8)
                .padding(.bottom, 48)
        }
    }
}

// MARK: - Page 2: Features tour

private struct ONFeaturesPage: View {
    let onNext: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Deine Abende")
                            .font(.appTitle)
                            .foregroundStyle(Color.appText)
                        Text("Alles, was du brauchst für einen guten Abend.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }

                    VStack(spacing: 12) {
                        ONFeatureRow(
                            icon: "trophy.fill",
                            accent: Color.appAccent,
                            title: "Achievements",
                            subtitle: "Entdecke Getränke-Vielfalt und schalte 16 einzigartige Achievements frei"
                        )
                        ONFeatureRow(
                            icon: "person.2.fill",
                            accent: Color.statusGreen,
                            title: "Crew",
                            subtitle: "Behalte Freunde im Blick und feiert gemeinsam"
                        )
                        ONFeatureRow(
                            icon: "camera.fill",
                            accent: Color.statusOrange,
                            title: "Erinnerungen",
                            subtitle: "Fotos vom Abend festhalten"
                        )
                        ONFeatureRow(
                            icon: "shield.fill",
                            accent: Color.statusYellow,
                            title: "Sicher nach Hause",
                            subtitle: "Taxi oder Uber direkt aus der App buchen"
                        )
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }

            PrimaryButton(title: "Weiter", icon: "arrow.right", action: onNext)
                .padding(.horizontal, 32)
                .padding(.top, 8)
                .padding(.bottom, 48)
        }
    }
}

private struct ONFeatureRow: View {
    let icon: String
    let accent: Color
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(accent)
                .frame(width: 44, height: 44)
                .background(accent.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                Text(subtitle)
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

private struct ONLabeledField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let keyboard: UIKeyboardType

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
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

// MARK: - Feature chip (welcome page)

private struct ONFeatureChip: View {
    let icon: String
    let label: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 20)
            Text(label)
                .font(.appCaption)
                .foregroundStyle(Color.appText)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

// MARK: - Preview

#Preview {
    let config = ModelConfiguration(isStoredInMemoryOnly: true)
    let container = try! ModelContainer(for: PersistenceController.schema, configurations: config)
    return OnboardingView()
        .modelContainer(container)
        .preferredColorScheme(.dark)
}
