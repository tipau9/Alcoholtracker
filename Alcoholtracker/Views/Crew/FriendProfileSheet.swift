import SwiftUI
import SwiftData

// MARK: - FriendProfileSheet
//
// Opened by tapping a friend row in CrewView. Shows live BAC, friendship
// status (does the friend follow back?), shared achievements, mutual
// friends, and local actions (driver flag, remove).
// Server features need the friendships table + achievements column,
// see SQL in SupabaseService. Missing schema degrades gracefully.

struct FriendProfileSheet: View {

    @Bindable var member: CrewMember
    let onRemove: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(SupabaseService.self) private var supabase

    private enum LoadState { case loading, loaded, offline, failed }
    @State private var loadState: LoadState = .loading
    @State private var profile: FriendProfile?
    @State private var followsMe = false
    @State private var mutualFriends: [FriendProfile] = []
    @State private var selectedAchievement: Achievement?

    // MARK: Derived

    private var sharesData: Bool { profile?.isSharing ?? true }

    // Published BAC decayed at the standard elimination rate since the last
    // server update, same logic as CrewMember.estimatedBAC but on fresh data.
    private var liveBAC: Double? {
        guard let p = profile, p.isSharing, let bac = p.currentBac, let ts = p.bacUpdatedAt else { return nil }
        let hours = max(0, Date().timeIntervalSince(ts) / 3600)
        return max(0, bac - 0.15 * hours)
    }

    private var bacUpdatedMinutes: Int? {
        guard let ts = profile?.bacUpdatedAt else { return nil }
        return Int(Date().timeIntervalSince(ts) / 60)
    }

    private var earnedAchievements: [Achievement] {
        guard sharesData, let ids = profile?.achievements, !ids.isEmpty else { return [] }
        let set = Set(ids)
        return AchievementCatalog.all.filter { set.contains($0.id) }
    }

    private var myShareCode: String {
        if let code = supabase.myProfile?.friendCode, !code.isEmpty { return code }
        return UserDefaults.standard.string(forKey: "myFriendCode") ?? ""
    }

    private static let sinceFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "d. MMMM yyyy"
        return fmt
    }()

    // MARK: Body

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                header

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        liveStatusCard

                        if loadState == .loaded {
                            friendshipCard
                        }

                        if !earnedAchievements.isEmpty {
                            achievementsCard
                        }

                        if !mutualFriends.isEmpty {
                            mutualFriendsCard
                        }

                        if loadState == .offline {
                            offlineHint
                        }

                        actionsCard
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 32)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .task { await load() }
        .sheet(item: $selectedAchievement) { achievement in
            AchievementDetailSheet(achievement: achievement)
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 14) {
            Text(member.avatarInitial)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(member.bacStatus == .sober ? Color.appText : member.bacStatus.color)
                .frame(width: 56, height: 56)
                .background(member.bacStatus.backgroundColor)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(member.bacStatus.color.opacity(0.4), lineWidth: 1.5))

            VStack(alignment: .leading, spacing: 3) {
                Text(member.name)
                    .font(.appHeadline)
                    .foregroundStyle(Color.appText)
                if let p = profile, !p.displayName.isEmpty, p.displayName != member.name {
                    Text("Profilname: \(p.displayName)")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
                HStack(spacing: 6) {
                    if let code = member.friendCode {
                        Text(code)
                            .font(.system(.caption, design: .monospaced, weight: .semibold))
                            .foregroundStyle(Color.appTextDim)
                    }
                    Text("Freund seit \(Self.sinceFormatter.string(from: member.joinedAt))")
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
            .accessibilityLabel("Schließen")
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 14)
    }

    // MARK: Live status

    private var liveStatusCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "LIVE-STATUS")

            HStack(spacing: 14) {
                if loadState == .loading {
                    ProgressView()
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 14)
                } else if let bac = liveBAC {
                    let status = BACStatus(bac: bac)
                    VStack(alignment: .leading, spacing: 6) {
                        StatusPill(status: status)
                        if let mins = bacUpdatedMinutes {
                            Text(formattedTimeSinceUpdate(minutes: mins))
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextMuted)
                        }
                    }
                    Spacer()
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(String(format: "%.2f", bac))
                            .font(.system(size: 34, weight: .light, design: .serif))
                            .foregroundStyle(status.color)
                            .monospacedDigit()
                        Text("‰")
                            .font(.appBodyBold)
                            .foregroundStyle(status.color)
                    }
                } else {
                    Image(systemName: sharesData ? "antenna.radiowaves.left.and.right.slash" : "eye.slash.fill")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Color.appTextMuted)
                        .frame(width: 36, height: 36)
                        .background(Color.appBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    Text(sharesData ? "Keine Live-Daten verfügbar." : "Teilt aktuell keine Live-Daten.")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                    Spacer()
                }
            }
            .padding(14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
        }
    }

    // MARK: Friendship status

    private var friendshipCard: some View {
        HStack(spacing: 12) {
            Image(systemName: followsMe ? "person.2.fill" : "person.fill.questionmark")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(followsMe ? Color.statusGreen : Color.statusOrange)
                .frame(width: 36, height: 36)
                .background((followsMe ? Color.statusGreen : Color.statusOrange).opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(followsMe ? "Hat dich auch als Freund" : "Hat dich noch nicht hinzugefügt")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appText)
                Text(followsMe
                     ? "Ihr seht gegenseitig eure Live-Daten."
                     : "Sende deinen Code, damit die Verbindung in beide Richtungen geht.")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            if !followsMe, !myShareCode.isEmpty {
                ShareLink(item: "Füge mich in promille. hinzu! Mein Code: \(myShareCode)") {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 34, height: 34)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(Circle())
                }
            }
        }
        .padding(14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    // MARK: Achievements

    private var achievementsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionLabel(text: "ERFOLGE")
                Spacer()
                Text("\(earnedAchievements.count) von \(AchievementCatalog.all.count)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }

            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 150, maximum: 220), spacing: 8)],
                alignment: .leading,
                spacing: 8
            ) {
                ForEach(earnedAchievements) { achievement in
                    Button {
                        selectedAchievement = achievement
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: achievement.icon)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(accentColor(achievement.accent))
                                .frame(width: 26, height: 26)
                                .background(accentColor(achievement.accent).opacity(0.13))
                                .clipShape(RoundedRectangle(cornerRadius: 7))
                            Text(achievement.title)
                                .font(.appMicro)
                                .foregroundStyle(Color.appText)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func accentColor(_ accent: Achievement.AchievementAccent) -> Color {
        switch accent {
        case .amber:  return Color.appAccent
        case .green:  return Color.statusGreen
        case .yellow: return Color.statusYellow
        case .orange: return Color.statusOrange
        }
    }

    // MARK: Mutual friends

    private var mutualFriendsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "GEMEINSAME FREUNDE")
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 120, maximum: 200), spacing: 8)],
                alignment: .leading,
                spacing: 8
            ) {
                ForEach(mutualFriends) { friend in
                    HStack(spacing: 8) {
                        Text(String(friend.displayName.prefix(1)).uppercased())
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Color.appText)
                            .frame(width: 24, height: 24)
                            .background(Color.appBorder)
                            .clipShape(Circle())
                        Text(friend.displayName.isEmpty ? friend.friendCode : friend.displayName)
                            .font(.appMicro)
                            .foregroundStyle(Color.appText)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.appBorder, lineWidth: 0.5))
                }
            }
        }
    }

    // MARK: Offline hint

    private var offlineHint: some View {
        HStack(spacing: 10) {
            Image(systemName: "icloud.slash")
                .font(.system(size: 14))
                .foregroundStyle(Color.appTextMuted)
            Text(member.friendCode == nil
                 ? "Lokaler Freund ohne Code. Live-Funktionen sind nicht verfügbar."
                 : "Melde dich an, um Live-Status, Erfolge und gemeinsame Freunde zu sehen.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
            Spacer()
        }
        .padding(14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    // MARK: Actions

    private var actionsCard: some View {
        VStack(spacing: 0) {
            Toggle(isOn: Binding(
                get: { member.isSoberBuddy },
                set: { newValue in
                    member.isSoberBuddy = newValue
                    try? context.save()
                }
            )) {
                HStack(spacing: 10) {
                    Image(systemName: "car.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.statusGreen)
                        .frame(width: 28, height: 28)
                        .background(Color.statusGreen.opacity(0.13))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text("Als Fahrer markiert")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                }
            }
            .tint(Color.statusGreen)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            Divider().background(Color.appBorder).padding(.leading, 52)

            Toggle(isOn: Binding(
                get: { member.isHome },
                set: { newValue in
                    member.isHome = newValue
                    try? context.save()
                }
            )) {
                HStack(spacing: 10) {
                    Image(systemName: "house.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 28, height: 28)
                        .background(Color.appAccent.opacity(0.13))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text("Sicher zuhause")
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                }
            }
            .tint(Color.appAccent)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            Divider().background(Color.appBorder).padding(.leading, 52)

            Toggle(isOn: Binding(
                get: { member.alertWhenHigh },
                set: { newValue in
                    member.alertWhenHigh = newValue
                    if !newValue { member.highAlertFired = false }
                    try? context.save()
                    // Permission is needed for the alert to actually deliver.
                    if newValue {
                        Task { _ = await NotificationService.requestAuthorization() }
                    }
                }
            )) {
                HStack(spacing: 10) {
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.statusOrange)
                        .frame(width: 28, height: 28)
                        .background(Color.statusOrange.opacity(0.13))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Warnen wenn zu viel")
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                        Text("Benachrichtigung bei hohem Promillewert")
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextDim)
                    }
                }
            }
            .tint(Color.statusOrange)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)

            Divider().background(Color.appBorder).padding(.leading, 52)

            Button {
                onRemove()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.statusRed)
                        .frame(width: 28, height: 28)
                        .background(Color.statusRed.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    Text("Freund entfernen")
                        .font(.appBody)
                        .foregroundStyle(Color.statusRed)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
            }
            .buttonStyle(.plain)
        }
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    // MARK: Loading

    private func load() async {
        guard supabase.isSignedIn, supabase.isConfigured,
              let code = member.friendCode, !code.isEmpty else {
            loadState = .offline
            return
        }
        do {
            let p = try await supabase.lookupFriend(code: code)
            profile = p

            // Friendship data is best-effort: a missing friendships table
            // (SQL not yet run) just leaves both sections empty.
            if let myID = supabase.session?.userId {
                let theirIDs = (try? await supabase.fetchFriendIDs(of: p.id)) ?? []
                followsMe = theirIDs.contains(myID)

                let myIDs = (try? await supabase.fetchFriendIDs(of: myID)) ?? []
                let mutualIDs = Set(theirIDs)
                    .intersection(myIDs)
                    .subtracting([myID, p.id])
                if !mutualIDs.isEmpty {
                    mutualFriends = ((try? await supabase.fetchProfiles(ids: Array(mutualIDs))) ?? [])
                        .sorted { $0.displayName < $1.displayName }
                }
            }
            loadState = .loaded
        } catch {
            loadState = .failed
        }
    }

    // MARK: - Time Formatting

    private func formattedTimeSinceUpdate(minutes: Int) -> String {
        if minutes <= 0 {
            return "Stand jetzt"
        } else if minutes < 60 {
            return "Stand vor \(minutes) min"
        } else if minutes < 1440 { // Bis zu 24 Stunden
            let hours = minutes / 60
            let remainingMins = minutes % 60
            let hourStr = hours == 1 ? "Stunde" : "Stunden"
            
            if remainingMins == 0 {
                return "Stand vor \(hours) \(hourStr)"
            } else {
                return String(format: "Stand vor %d:%02d %@", hours, remainingMins, hourStr)
            }
        } else { // Ab 24 Stunden
            let days = minutes / 1440
            let remainingHours = (minutes % 1440) / 60
            let dayStr = days == 1 ? "1 Tag" : "\(days) Tagen"
            
            if remainingHours == 0 {
                return "Stand vor \(dayStr)"
            } else {
                let hourStr = remainingHours == 1 ? "1 Stunde" : "\(remainingHours) Stunden"
                return "Stand vor \(dayStr) und \(hourStr)"
            }
        }
    }
}

// MARK: - Achievement detail sheet
//
// Opened by tapping an earned achievement on a friend's profile. Shows the
// icon, title, and the short description (subtitle).

private struct AchievementDetailSheet: View {
    let achievement: Achievement
    @Environment(\.dismiss) private var dismiss

    private var accent: Color {
        switch achievement.accent {
        case .amber:  return Color.appAccent
        case .green:  return Color.statusGreen
        case .yellow: return Color.statusYellow
        case .orange: return Color.statusOrange
        }
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 20)

                ZStack {
                    Circle()
                        .fill(accent.opacity(0.16))
                        .frame(width: 88, height: 88)
                    Image(systemName: achievement.icon)
                        .font(.system(size: 36, weight: .medium))
                        .foregroundStyle(accent)
                }
                .padding(.bottom, 18)

                Text(achievement.title)
                    .font(.appHeadline)
                    .foregroundStyle(Color.appText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                Text(achievement.subtitle)
                    .font(.appBody)
                    .foregroundStyle(Color.appTextDim)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .padding(.top, 8)

                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.statusGreen)
                    Text("Freigeschaltet")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.statusGreen)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.statusGreen.opacity(0.12))
                .clipShape(Capsule())
                .padding(.top, 18)

                Spacer()

                Button { dismiss() } label: {
                    Text("Schließen")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appBackground)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.appAccent)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.hidden)
    }
}
