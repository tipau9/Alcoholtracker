import SwiftUI

// MARK: - Settings sections
//
// Self-contained section components extracted from SettingsView. Each takes the
// active UserProfile and a `save` closure (a thin wrapper over the model
// context save) so it can mutate the profile without owning persistence. The
// state-heavy sections (account, notifications, data, achievements, privacy)
// stay in SettingsView because they depend on its local @State and several
// environment services.

// MARK: - Profile

struct SettingsProfileSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "PROFIL")
            VStack(spacing: 0) {
                STNumericRow(
                    label: "Gewicht",
                    unit: "kg",
                    format: "%.1f",
                    range: 30...250,
                    value: Binding(get: { p.weight }, set: { p.weight = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STNumericRow(
                    label: "Größe",
                    unit: "cm",
                    format: "%.0f",
                    range: 100...250,
                    value: Binding(get: { p.height }, set: { p.height = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                // FIX FEATURE9: DatePicker replaces age int slider
                HStack {
                    Text("Geburtsdatum")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    DatePicker(
                        "",
                        selection: Binding(get: { p.birthDate }, set: { p.birthDate = $0; p.age = Calendar.current.dateComponents([.year], from: $0, to: Date()).year ?? p.age; save() }),
                        in: ...Calendar.current.date(byAdding: .year, value: -16, to: Date())!,
                        displayedComponents: .date
                    )
                    .labelsHidden()
                    .colorScheme(.dark)
                    .tint(Color.appAccent)
                }
                .padding(.horizontal, 16)
                .frame(height: 52)
                Divider().background(Color.appBorder).padding(.leading, 16)
                STGenderRow(
                    gender: Binding(get: { p.gender }, set: { p.gender = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STElimRow(
                    value: Binding(get: { p.eliminationRate }, set: { p.eliminationRate = $0; save() })
                )
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

// MARK: - Safety

struct SettingsSafetySection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "SICHERHEIT")
            VStack(spacing: 0) {
                STContactField(
                    label: "Notfallkontakt",
                    placeholder: "Name eingeben",
                    keyboard: .default,
                    value: Binding(
                        get: { p.emergencyContactName ?? "" },
                        set: {
                            let v = $0.trimmingCharacters(in: .whitespaces)
                            p.emergencyContactName = v.isEmpty ? nil : v
                            save()
                        }
                    )
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STContactField(
                    label: "Telefonnummer",
                    placeholder: "+49 123 456789",
                    keyboard: .phonePad,
                    value: Binding(
                        get: { p.emergencyContactPhone ?? "" },
                        set: {
                            let v = $0.trimmingCharacters(in: .whitespaces)
                            p.emergencyContactPhone = v.isEmpty ? nil : v
                            save()
                        }
                    )
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STThresholdRow(
                    value: Binding(get: { p.warningThreshold }, set: { p.warningThreshold = $0; save() })
                )
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

// MARK: - Limits & Ziele

struct SettingsLimitsSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "LIMITS & ZIELE")
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Wochenlimit")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Spacer()
                        Text(p.weeklyDrinkLimit == 0 ? "Keines" : "\(p.weeklyDrinkLimit) Drinks")
                            .font(.appCaptionBold)
                            .foregroundStyle(Color.appAccent)
                    }
                    Slider(value: Binding(get: { Double(p.weeklyDrinkLimit) }, set: { p.weeklyDrinkLimit = Int($0); save() }), in: 0...30, step: 1)
                        .tint(Color.appAccent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)

                Divider().background(Color.appBorder).padding(.leading, 16)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Alkoholfreie Tage")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Spacer()
                        Text("\(p.soberDaysGoal) pro Woche")
                            .font(.appCaptionBold)
                            .foregroundStyle(Color.appAccent)
                    }
                    Slider(value: Binding(get: { Double(p.soberDaysGoal) }, set: { p.soberDaysGoal = Int($0); save() }), in: 1...7, step: 1)
                        .tint(Color.appAccent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
        }
    }
}

// MARK: - Display

struct SettingsDisplaySection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "DARSTELLUNG")
            VStack(spacing: 0) {
                STHomeStyleRow(
                    style: Binding(get: { p.homeStyle }, set: { p.homeStyle = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STStomachRow(
                    status: Binding(get: { p.defaultStomachStatus }, set: { p.defaultStomachStatus = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STToggleRow(
                    icon: "gauge.with.dots.needle.67percent",
                    label: "Toleranzmodus",
                    subtitle: "Passt die Berechnung für regelmäßige Trinker an",
                    isOn: Binding(get: { p.toleranceMode }, set: { p.toleranceMode = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STToggleRow(
                    icon: "moon.zzz.fill",
                    label: "Drunk-Modus",
                    subtitle: "Vereinfacht die Startseite automatisch bei hohem Pegel",
                    isOn: Binding(get: { p.drunkModeAuto }, set: { p.drunkModeAuto = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STSkinRow(
                    skin: Binding(get: { p.statusSkin }, set: { p.statusSkin = $0; save() })
                )
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

// MARK: - Accent color (Feature 10)

struct SettingsAccentColorSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "AKZENTFARBE")
            AccentColorPicker(
                selectedHex: Binding(
                    get: { p.accentColorHex },
                    set: { p.accentColorHex = $0; AppTheme.shared.accentColorHex = $0; save() }
                )
            )
            .padding(16)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }
}

// MARK: - Messungen

struct SettingsMeasurementsSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "MESSUNGEN")
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Schluckgröße")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Spacer()
                        Text("\(Int(p.sipVolumeML)) ml")
                            .font(.system(size: 14, weight: .semibold, design: .serif))
                            .foregroundStyle(Color.appAccent)
                            .monospacedDigit()
                    }
                    Slider(
                        value: Binding(get: { p.sipVolumeML }, set: { p.sipVolumeML = $0; save() }),
                        in: 10...50, step: 5
                    )
                    .tint(Color.appAccent)
                    Text("Standard: 25 ml. Aus einer Flasche eher 20 ml, aus einem Glas eher 30 ml.")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
            }
            .padding(16)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
        }
    }
}

// MARK: - Status-Schwellen

struct SettingsThresholdSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionLabel(text: "STATUS-SCHWELLEN")
                Spacer()
                Button("Zurücksetzen") {
                    p.tipsyThreshold   = 0.01
                    p.drunkThreshold   = 0.30
                    p.carefulThreshold = 0.80
                    p.dangerThreshold  = 1.50
                    save()
                }
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .buttonStyle(.plain)
            }

            Text("Passe an, ab welchem Promille-Wert du in den jeweiligen Status wechselst. Nüchtern beginnt immer bei 0,00 ‰.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)

            // FIX BUG2: labels adapt to active StatusSkin
            VStack(spacing: 0) {
                STBACThresholdRow(
                    label: "\(p.statusSkin.label(for: .tipsy)) ab",
                    color: Color.statusYellow,
                    range: 0.01...min(p.drunkThreshold - 0.05, 0.49),
                    value: Binding(get: { p.tipsyThreshold },
                                   set: { p.tipsyThreshold = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STBACThresholdRow(
                    label: "\(p.statusSkin.label(for: .drunk)) ab",
                    color: Color.statusOrange,
                    range: (p.tipsyThreshold + 0.05)...min(p.carefulThreshold - 0.05, 0.99),
                    value: Binding(get: { p.drunkThreshold },
                                   set: { p.drunkThreshold = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STBACThresholdRow(
                    label: "\(p.statusSkin.label(for: .careful)) ab",
                    color: Color.statusRed,
                    range: (p.drunkThreshold + 0.05)...min(p.dangerThreshold - 0.05, 1.44),
                    value: Binding(get: { p.carefulThreshold },
                                   set: { p.carefulThreshold = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STBACThresholdRow(
                    label: "\(p.statusSkin.label(for: .danger)) ab",
                    color: Color.statusRed.opacity(0.7),
                    range: (p.carefulThreshold + 0.05)...2.50,
                    value: Binding(get: { p.dangerThreshold },
                                   set: { p.dangerThreshold = $0; save() })
                )
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

// MARK: - Accessibility

struct SettingsAccessibilitySection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "BARRIEREFREIHEIT")
            VStack(spacing: 0) {
                STToggleRow(
                    icon: "textformat.size.larger",
                    label: "Größer Text",
                    subtitle: "Schriftgröße erhöhen",
                    isOn: Binding(get: { p.largeText }, set: { p.largeText = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STToggleRow(
                    icon: "circle.lefthalf.filled",
                    label: "Hoher Kontrast",
                    subtitle: "Helleres Farbschema aktivieren",
                    isOn: Binding(get: { p.highContrast }, set: { p.highContrast = $0; save() })
                )
                Divider().background(Color.appBorder).padding(.leading, 16)
                STToggleRow(
                    icon: "hand.raised.fill",
                    label: "Bewegungen reduzieren",
                    subtitle: "Animationen minimieren",
                    isOn: Binding(get: { p.reducedMotion }, set: { p.reducedMotion = $0; save() })
                )
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

// MARK: - Medication (B3)

struct SettingsMedicationSection: View {
    let p: UserProfile
    let save: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "MEDIKAMENTE (AKTUELL)")
            VStack(spacing: 0) {
                ForEach(Array(MedicationFlag.allCases.enumerated()), id: \.element.rawValue) { idx, med in
                    Toggle(isOn: Binding(
                        get: { p.activeMedications.contains(med) },
                        set: { on in
                            var current = p.activeMedications
                            if on { current.append(med) } else { current.removeAll { $0 == med } }
                            p.activeMedications = current
                            save()
                        }
                    )) {
                        HStack(spacing: 12) {
                            Image(systemName: med.symbolName)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.appAccent)
                                .frame(width: 22)
                            Text(med.rawValue)
                                .font(.appBody)
                                .foregroundStyle(Color.appText)
                        }
                    }
                    .tint(Color.appAccent)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if idx < MedicationFlag.allCases.count - 1 {
                        Divider().background(Color.appBorder).padding(.leading, 16)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            Text("Aktive Medikamente werden bei deinem ersten Drink des Abends als Hinweis angezeigt. Kein medizinischer Rat.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
    }
}

// MARK: - Apple Health (B7)

struct SettingsHealthKitSection: View {
    let p: UserProfile
    let save: () -> Void
    @Environment(HealthKitService.self) private var health

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "APPLE HEALTH")
            VStack(spacing: 0) {
                if health.isAvailable {
                    Toggle(isOn: Binding(
                        get: { p.healthKitEnabled },
                        set: { val in
                            p.healthKitEnabled = val
                            save()
                            if val { Task { await health.requestAuthorization() } }
                        }
                    )) {
                        HStack(spacing: 12) {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.statusRed)
                                .frame(width: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Trinken in Health exportieren")
                                    .font(.appBody)
                                    .foregroundStyle(Color.appText)
                                Text("Alkoholeinheiten in Apple Health speichern")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appTextDim)
                            }
                        }
                    }
                    .tint(Color.statusRed)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                } else {
                    HStack(spacing: 12) {
                        Image(systemName: "heart.slash")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.appTextDim)
                            .frame(width: 22)
                        Text("Apple Health nicht verfügbar")
                            .font(.appBody)
                            .foregroundStyle(Color.appTextDim)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            Text("Speichert Alkoholeinheiten und Gramm reinen Alkohols pro Drink in Apple Health.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
    }
}

// MARK: - About

struct SettingsAboutSection: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "ÜBER")
            VStack(spacing: 0) {
                HStack {
                    Text("Version")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appTextDim)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            Text("Diese App liefert Schätzwerte nach dem Widmark-Modell. Sie ersetzt keinen Atemtest und keine medizinische Beurteilung. Im Zweifel nicht fahren.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .multilineTextAlignment(.leading)
        }
    }
}
