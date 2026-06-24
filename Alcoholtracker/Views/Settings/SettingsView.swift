import SwiftUI
import SwiftData
import UIKit

// MARK: - SettingsView
// Profile editing, safety thresholds, display preferences, and accessibility.
//
// The screen is composed from focused section components (see
// Views/Settings/Components/). Only the sections that depend on this view's
// local @State or several environment services live here; the stateless,
// profile-driven sections are standalone components.

struct SettingsView: View {

    @Query private var profiles: [UserProfile]
    // Only the count is needed on screen; the full history is fetched on demand
    // (export / notification reschedule) instead of paging every Drink into
    // memory each time Settings opens.
    @State private var drinkCount = 0
    @Environment(\.modelContext) private var context
    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementService.self) private var achievements
    @State private var showAuth = false
    @State private var showAchievements = false
    @State private var notifyEnabled = NotificationService.isEnabled
    @State private var showNotifyDeniedAlert = false
    @State private var exportFile: ExportFile? = nil
    @State private var showDeleteAccountConfirm = false

    private var profile: UserProfile? { profiles.first }

    private struct ExportFile: Identifiable {
        let id = UUID()
        let url: URL
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            if let p = profile {
                VStack(spacing: 0) {
                    profileHero

                    Divider().background(Color.appBorder)

                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 28) {
                            SettingsProfileSection(p: p, save: save)
                            SettingsSafetySection(p: p, save: save)
                            SettingsLimitsSection(p: p, save: save)
                            notificationsSection(p)
                            SettingsDisplaySection(p: p, save: save)
                            SettingsAccentColorSection(p: p, save: save)
                            SettingsMeasurementsSection(p: p, save: save)
                            SettingsThresholdSection(p: p, save: save)
                            SettingsAccessibilitySection(p: p, save: save)
                            SettingsMedicationSection(p: p, save: save)
                            SettingsHealthKitSection(p: p, save: save)
                            achievementsSection
                            dataSection
                            accountSection
                            privacySection
                            SettingsAboutSection()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 40)
                    }
                }
            }
        }
        .task {
            drinkCount = (try? context.fetchCount(FetchDescriptor<Drink>())) ?? 0
        }
    }

    // MARK: - Profile hero

    private var profileHero: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.appAccent.opacity(0.15))
                    .frame(width: 60, height: 60)
                Image(systemName: "person.fill")
                    .font(.system(size: 26, weight: .medium))
                    .foregroundStyle(Color.appAccent)
            }

            VStack(alignment: .leading, spacing: 4) {
                if supabase.isSignedIn, let p = supabase.myProfile {
                    Text(p.displayName.isEmpty ? "Kein Name" : p.displayName)
                        .font(.appTitle)
                        .foregroundStyle(Color.appText)
                    Text(p.friendCode)
                        .font(.system(.caption, design: .monospaced, weight: .bold))
                        .foregroundStyle(Color.appAccent)
                        .tracking(2)
                } else {
                    Text("Profil")
                        .font(.appTitle)
                        .foregroundStyle(Color.appText)
                    Text("Kein Konto verbunden")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                Text("\(achievements.unlockedCount)/\(achievements.totalCount)")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                Text("Achievements")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 16)
    }

    // MARK: - Save helper

    private func save() { try? context.save() }

    // MARK: - Notifications section

    private func notificationsSection(_ p: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "MITTEILUNGEN")
            VStack(spacing: 0) {
                STToggleRow(
                    icon: "bell.badge.fill",
                    label: "Nüchternheits-Erinnerung",
                    subtitle: "Meldung wenn du rechnerisch nüchtern bzw. unter deiner Warnschwelle bist",
                    isOn: Binding(
                        get: { notifyEnabled },
                        set: { newValue in
                            notifyEnabled = newValue
                            NotificationService.isEnabled = newValue
                            if newValue {
                                Task {
                                    let granted = await NotificationService.requestAuthorization()
                                    if granted {
                                        // 48h window covers rolling sessions past 06:00;
                                        // fully metabolised drinks contribute zero anyway.
                                        let lookback = Date().addingTimeInterval(-48 * 3600)
                                        let recentDescriptor = FetchDescriptor<Drink>(
                                            predicate: #Predicate { $0.timestamp >= lookback }
                                        )
                                        let recent = (try? context.fetch(recentDescriptor)) ?? []
                                        await NotificationService.reschedule(
                                            drinks: recent,
                                            profile: p,
                                            stomachStatus: p.defaultStomachStatus
                                        )
                                    } else {
                                        notifyEnabled = false
                                        NotificationService.isEnabled = false
                                        showNotifyDeniedAlert = true
                                    }
                                }
                            } else {
                                NotificationService.cancelAll()
                            }
                        }
                    )
                )
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
        .alert("Mitteilungen nicht erlaubt", isPresented: $showNotifyDeniedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Bitte erlaube Mitteilungen für promille in den iOS-Einstellungen.")
        }
    }

    // MARK: - Data section (export)

    private var dataSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "DATEN")
            Button {
                let all = (try? context.fetch(
                    FetchDescriptor<Drink>(sortBy: [SortDescriptor(\.timestamp)])
                )) ?? []
                if let url = try? ExportService.csvURL(drinks: all) {
                    exportFile = ExportFile(url: url)
                }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 22)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Verlauf als CSV exportieren")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Text("\(drinkCount) Drinks, öffnet sich in Excel und Numbers")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.appTextDim)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .buttonStyle(.plain)
            .contentShape(Rectangle())
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
            .disabled(drinkCount == 0)
            .opacity(drinkCount == 0 ? 0.5 : 1)
        }
        .sheet(item: $exportFile) { file in
            STShareSheet(url: file.url)
        }
    }

    // MARK: - Achievements section

    private var achievementsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "ACHIEVEMENTS")
            Button { showAchievements = true } label: {
                HStack(spacing: 12) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 22)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Achievements")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Text("\(achievements.unlockedCount) von \(achievements.totalCount) freigeschaltet")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.appTextDim)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .buttonStyle(.plain)
            .contentShape(Rectangle())
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
        .sheet(isPresented: $showAchievements) {
            AchievementsView()
        }
    }

    // MARK: - Account section

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "KONTO")
            VStack(spacing: 0) {
                if supabase.isSignedIn, let p = supabase.myProfile {
                    HStack(spacing: 12) {
                        Image(systemName: "person.fill")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.appAccent)
                            .frame(width: 22)
                        Text(p.displayName.isEmpty ? "Kein Name" : p.displayName)
                            .font(.appBody)
                            .foregroundStyle(p.displayName.isEmpty ? Color.appTextMuted : Color.appText)
                        Spacer()
                        Text(p.friendCode)
                            .font(.system(.caption, design: .monospaced, weight: .bold))
                            .foregroundStyle(Color.appAccent)
                            .tracking(2)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)

                    Divider().background(Color.appBorder).padding(.leading, 16)

                    Toggle(isOn: Binding(
                        get: { supabase.myProfile?.isSharing ?? true },
                        set: { val in Task { try? await supabase.updateSharing(val) } }
                    )) {
                        HStack(spacing: 12) {
                            Image(systemName: "dot.radiowaves.left.and.right")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.appAccent)
                                .frame(width: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("BAC teilen")
                                    .font(.appBody)
                                    .foregroundStyle(Color.appText)
                                Text("Freunde können deinen BAC sehen")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appTextDim)
                            }
                        }
                    }
                    .tint(Color.appAccent)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)

                    Divider().background(Color.appBorder).padding(.leading, 16)

                    Button { Task { await supabase.signOut() } } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "arrow.right.square")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.statusRed)
                                .frame(width: 22)
                            Text("Abmelden")
                                .font(.appBody)
                                .foregroundStyle(Color.statusRed)
                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)

                    Divider().background(Color.appBorder).padding(.leading, 16)

                    Button { showDeleteAccountConfirm = true } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "trash")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.statusRed)
                                .frame(width: 22)
                            Text("Konto löschen")
                                .font(.appBody)
                                .foregroundStyle(Color.statusRed)
                            Spacer()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)
                } else {
                    Button { showAuth = true } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "person.circle")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Color.appAccent)
                                .frame(width: 22)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Anmelden")
                                    .font(.appBody)
                                    .foregroundStyle(Color.appText)
                                Text("Live-BAC mit Freunden teilen")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appTextDim)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(Color.appTextDim)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    .alert("Konto wirklich löschen?", isPresented: $showDeleteAccountConfirm) {
        Button("Löschen", role: .destructive) {
            Task {
                try? await supabase.deleteAccount()
            }
        }
        Button("Abbrechen", role: .cancel) {}
    } message: {
        Text("Diese Aktion kann nicht rückgängig gemacht werden. Alle deine Online-Daten werden unwiderruflich gelöscht.")
    }
        .sheet(isPresented: $showAuth) { AuthGate() }
    }

    // MARK: - Privacy section

    private var privacySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "DATENSCHUTZ")
            VStack(spacing: 0) {
                STDestructiveRow(
                    icon: "photo.on.rectangle",
                    label: "Alle Erinnerungsfotos löschen",
                    action: deleteAllPhotos
                )
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )

            Text("Fotos werden lokal auf deinem Gerät im App-Ordner gespeichert und nirgendwo hochgeladen.")
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .multilineTextAlignment(.leading)
        }
    }

    private func deleteAllPhotos() {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("PhotoMemories", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        for file in files {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent(file))
        }
        let descriptor = FetchDescriptor<PhotoMemory>()
        let all = (try? context.fetch(descriptor)) ?? []
        all.forEach { context.delete($0) }
        try? context.save()
    }
}

// MARK: - Preview

#Preview {
    let controller = PersistenceController.preview
    return SettingsView()
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}
