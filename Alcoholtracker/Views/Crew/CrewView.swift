import SwiftUI
import SwiftData
import UIKit

// MARK: - CrewView

struct CrewView: View {

    @Environment(\.modelContext) private var context
    @Query private var allMembers: [CrewMember]
    @Query private var profiles: [UserProfile]
    @Query(sort: [SortDescriptor(\PhotoMemory.timestamp, order: .reverse)]) private var memories: [PhotoMemory]
    @State private var showAddFriend = false
    @State private var showJam = false
    @State private var showCapture = false
    @State private var selectedMemory: PhotoMemory?
    @Environment(SupabaseService.self) private var supabase
    @Environment(JamService.self) private var jamService
    @Environment(\.scenePhase) private var scenePhase
    @State private var showAuth = false
    @State private var myFriendCode: String = ""
    @State private var showSOSInfo = false
    @State private var memberToDelete: CrewMember?
    @State private var profileMember: CrewMember?
    // My app-wide SOS (independent of jams), mirrored from the server profile.
    @State private var myFriendSOS = false
    @State private var joiningJamID: UUID?

    // MARK: Derived state

    private var profile: UserProfile? { profiles.first }

    // Whether a driver-marked member counts as ready to drive, honoring THAT
    // friend's own Probezeit setting (0,0 ‰) versus the standard 0,5 ‰ limit.
    // It uses the friend's limit, not yours, since you cannot know theirs
    // otherwise; it is synced from their profile via the BAC poll.
    private func mayDrive(_ member: CrewMember) -> Bool {
        member.isProbationaryDriver ? member.estimatedBAC <= 0.005 : member.estimatedBAC < 0.5
    }

    private var active: [CrewMember] {
        allMembers.filter { !$0.isHome }
    }
    private var atHome: [CrewMember] {
        allMembers.filter { $0.isHome }
    }
    private var soberBuddy: CrewMember? {
        allMembers.first { $0.isSoberBuddy && !$0.isHome }
    }
    private var needsAttention: [CrewMember] {
        allMembers
            .filter { !$0.isHome && $0.careScore >= 40 }
            .sorted { $0.careScore > $1.careScore }
    }
    private var friendsWithSOS: [CrewMember] {
        allMembers.filter { $0.sosActive }
    }

    // MARK: Body

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                CRTopBar(memberCount: allMembers.count, onAdd: { showAddFriend = true }, onJam: { showJam = true })
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                Divider().background(Color.appBorder)

                if !supabase.isSignedIn {
                    CRAuthBanner { showAuth = true }
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                }

                if allMembers.isEmpty {
                    emptyState
                } else {
                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 20) {
                            if !friendsWithSOS.isEmpty {
                                FriendSOSBanner(members: friendsWithSOS) { profileMember = $0 }
                            }

                            if let jam = jamService.currentJam {
                                ActiveJamBanner(jam: jam) { showJam = true }
                            } else {
                                ForEach(jamService.availableJamsFromFriends) { jam in
                                    FriendJamBanner(
                                        jam: jam,
                                        isJoining: joiningJamID == jam.id
                                    ) { joinFriendJam(jam) }
                                }
                            }

                            MyCodeCard(code: myFriendCode, isLive: supabase.isSignedIn)

                            PhotoMemoryStrip(
                                memories: memories,
                                onAdd: { showCapture = true },
                                onSelect: { selectedMemory = $0 }
                            )

                            if let top = needsAttention.first {
                                CareCard(member: top)
                            }
                            if let buddy = soberBuddy {
                                SoberBuddyCard(member: buddy, canDrive: mayDrive(buddy))
                            }
                            activeSection
                            homeSection
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 12)
                    }
                    .safeAreaInset(edge: .bottom) {
                        // SOS reaches all friends via the profile flag (server
                        // poll) and, if you are in a jam, its participants too.
                        SOSBar(isActive: sosActive) { toggleSOS() }
                    }
                }
            }
        }
        .task {
            myFriendCode = resolvedFriendCode()
        }
        .alert("SOS", isPresented: $showSOSInfo) {
            Button("Anmelden") { showAuth = true }
            Button("Jam öffnen") { showJam = true }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("SOS erreicht deine Freunde, sobald du angemeldet bist. Ohne Anmeldung funktioniert SOS nur in einem aktiven Jam.")
        }
        .confirmationDialog(
            "Freund entfernen?",
            isPresented: Binding(
                get: { memberToDelete != nil },
                set: { if !$0 { memberToDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Entfernen", role: .destructive) {
                if let member = memberToDelete {
                    deleteMember(member)
                }
                memberToDelete = nil
            }
            Button("Abbrechen", role: .cancel) { memberToDelete = nil }
        } message: {
            Text(memberToDelete.map { "\($0.name) wird aus deiner Liste entfernt." } ?? "")
        }
        .onChange(of: supabase.myProfile?.friendCode) { _, newCode in
            if let newCode { myFriendCode = newCode }
        }
        // Re-keyed on both sign-in and scenePhase: when the app leaves the
        // foreground the id changes, SwiftUI cancels this task (the loop's
        // sleep throws and it returns), so no network polling runs in the
        // background. Returning to .active restarts it.
        .task(id: SyncTaskKey(signedIn: supabase.isSignedIn, active: scenePhase == .active)) {
            guard supabase.isSignedIn, scenePhase == .active else { return }
            try? await supabase.syncMyProfile()
            await syncFriendsLoop()
        }
        .sheet(isPresented: $showAddFriend) {
            AddFriendSheet()
        }
        .sheet(isPresented: $showJam) {
            JamTabView()
        }
        .sheet(isPresented: $showCapture) {
            PhotoCaptureView()
        }
        .sheet(isPresented: $showAuth) {
            AuthGate()
        }
        .sheet(item: $selectedMemory) { memory in
            PhotoDetailView(memory: memory) { selectedMemory = nil }
        }
        .sheet(item: $profileMember) { member in
            FriendProfileSheet(member: member) {
                profileMember = nil
                memberToDelete = member
            }
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: 24) {
            Spacer()

            if let jam = jamService.currentJam {
                ActiveJamBanner(jam: jam) { showJam = true }
                    .padding(.horizontal, 20)
            }

            MyCodeCard(code: myFriendCode, isLive: supabase.isSignedIn)
                .padding(.horizontal, 20)

            VStack(spacing: 8) {
                Image(systemName: "person.badge.plus")
                    .font(.system(size: 44, weight: .ultraLight))
                    .foregroundStyle(Color.appTextMuted)

                Text("Noch keine Freunde")
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)

                Text("Teile deinen Code und füge Freunde per Code hinzu.")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }

            Button { showAddFriend = true } label: {
                HStack(spacing: 8) {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                    Text("Freund hinzufügen")
                        .font(.appBodyBold)
                }
                .foregroundStyle(Color.appAccent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5)
                )
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)

            Spacer()
        }
    }

    // MARK: Sections

    @ViewBuilder
    private var activeSection: some View {
        if !active.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "AKTIV")
                memberCard(members: active)
            }
        }
    }

    @ViewBuilder
    private var homeSection: some View {
        if !atHome.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "SICHER ZUHAUSE")
                memberCard(members: atHome)
                    .opacity(0.58)
            }
        }
    }

    private func memberCard(members: [CrewMember]) -> some View {
        VStack(spacing: 0) {
            ForEach(members) { member in
                SwipeToDeleteRow(onDelete: { memberToDelete = member }) {
                    VStack(spacing: 0) {
                        CrewMemberRow(member: member, mayDrive: mayDrive(member)) {
                            member.isHome.toggle()
                            try? context.save()
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { profileMember = member }
                        .contextMenu {
                            Button {
                                profileMember = member
                            } label: {
                                Label("Profil anzeigen", systemImage: "person.crop.circle")
                            }
                            Button {
                                member.isSoberBuddy.toggle()
                                try? context.save()
                            } label: {
                                Label(
                                    member.isSoberBuddy ? "Nicht mehr Fahrer" : "Als Fahrer markieren",
                                    systemImage: member.isSoberBuddy ? "car.fill" : "car"
                                )
                            }
                            Button(role: .destructive) {
                                memberToDelete = member
                            } label: {
                                Label("Entfernen", systemImage: "trash")
                            }
                        }
                        if member.id != members.last?.id {
                            Divider()
                                .background(Color.appBorder)
                                .padding(.leading, 68)
                        }
                    }
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

    // MARK: Actions

    private func deleteMember(_ member: CrewMember) {
        // Remove the server-side follow edge too; the id is resolved via the
        // stored code because CrewMember does not persist the user id.
        if supabase.isSignedIn, let code = member.friendCode, !code.isEmpty {
            Task {
                if let profile = try? await supabase.lookupFriend(code: code) {
                    try? await supabase.removeFriendship(friendID: profile.id)
                }
            }
        }
        context.delete(member)
        try? context.save()
    }

    // MARK: Friend code

    private func resolvedFriendCode() -> String {
        if let code = supabase.myProfile?.friendCode { return code }
        if let saved = UserDefaults.standard.string(forKey: "myFriendCode") { return saved }
        let code = JamCodeGenerator.generate()
        UserDefaults.standard.set(code, forKey: "myFriendCode")
        return code
    }

    // MARK: Live sync

    private func syncFriendsLoop() async {
        while !Task.isCancelled {
            await syncFriendsBAC()
            // Refresh joinable friend jams for the banner while not in a jam.
            await jamService.refreshFriendJamsForLobby(
                friendCodes: allMembers.compactMap(\.friendCode)
            )
            do {
                try await Task.sleep(for: .seconds(60))
            } catch {
                return
            }
        }
    }

    private func syncFriendsBAC() async {
        let codes = allMembers.compactMap { $0.friendCode }.filter { !$0.isEmpty }
        guard !codes.isEmpty else { return }
        guard let fresh = try? await supabase.fetchFriendsBAC(codes: codes) else { return }

        // Keep my own mirrored SOS state in step with the server in case it was
        // set from another device.
        myFriendSOS = supabase.myProfile?.sosActive ?? myFriendSOS

        let dangerLimit = profile?.dangerThreshold ?? 1.5

        for p in fresh {
            guard let member = allMembers.first(where: {
                $0.friendCode?.uppercased() == p.friendCode.uppercased()
            }) else { continue }

            member.currentBAC = p.currentBac ?? 0
            member.lastDrinkTimestamp = p.bacUpdatedAt
            member.isProbationaryDriver = p.isProbationary

            // Friend SOS: notify on the rising edge (was off, now on).
            if p.sosActive, !member.sosActive {
                await NotificationService.notifyNow(
                    id: "promille.friend.sos.\(member.id.uuidString)",
                    title: "SOS von \(member.name)",
                    body: "\(member.name) braucht Hilfe. Tippe, um das Profil zu öffnen."
                )
                UINotificationFeedbackGenerator().notificationOccurred(.warning)
            }
            member.sosActive = p.sosActive

            // High-BAC warning, once per episode, only if enabled for this friend.
            if member.alertWhenHigh {
                if member.estimatedBAC >= dangerLimit, !member.highAlertFired {
                    member.highAlertFired = true
                    await NotificationService.notifyNow(
                        id: "promille.friend.high.\(member.id.uuidString)",
                        title: "\(member.name) trinkt viel",
                        body: "\(member.name) liegt rechnerisch bei \(member.estimatedBAC.permilleString). Vielleicht mal nachfragen."
                    )
                } else if member.estimatedBAC < dangerLimit, member.highAlertFired {
                    member.highAlertFired = false   // reset so the next episode can alert again
                }
            }
        }
        try? context.save()
    }

    // MARK: SOS

    private var sosActive: Bool { myFriendSOS || jamService.mySOSActive }

    private func toggleSOS() {
        let willActivate = !sosActive
        // No channel available without sign-in and without a jam.
        if !supabase.isSignedIn && jamService.currentJam == nil {
            showSOSInfo = true
            return
        }
        if willActivate {
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        }
        if supabase.isSignedIn {
            myFriendSOS = willActivate
            Task { try? await supabase.setSOS(willActivate) }
        }
        if jamService.currentJam != nil {
            jamService.mySOSActive = willActivate
        }
    }

    // MARK: Friend jams

    private func joinFriendJam(_ jam: Jam) {
        guard joiningJamID == nil else { return }
        joiningJamID = jam.id
        Task {
            try? await jamService.joinJamFromFriend(jam)
            joiningJamID = nil   // success swaps the banner for ActiveJamBanner
        }
    }
}

// MARK: - Sync task key

// Combined identity for the friend-sync .task: a change in either field tears
// down the running loop and starts a fresh one (or none, when not active).
private struct SyncTaskKey: Equatable {
    let signedIn: Bool
    let active: Bool
}

// MARK: - Auth banner

private struct CRAuthBanner: View {
    let onSignIn: () -> Void

    var body: some View {
        Button(action: onSignIn) {
            HStack(spacing: 10) {
                Image(systemName: "person.crop.circle.badge.plus")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Live-BAC aktivieren")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                    Text("Anmelden um BAC-Daten mit Freunden zu teilen")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.appAccent.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.appAccent.opacity(0.25), lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Top bar

private struct CRTopBar: View {
    let memberCount: Int
    let onAdd: () -> Void
    let onJam: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Text("Freunde")
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            Spacer()
            if memberCount > 0 {
                Text("\(memberCount) \(memberCount == 1 ? "Person" : "Personen")")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }
            Button(action: onJam) {
                Image(systemName: "waveform")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 32, height: 32)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(.leading, 12)
            Button(action: onAdd) {
                Image(systemName: "person.badge.plus")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 32, height: 32)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(.leading, 8)
        }
    }
}

// MARK: - My code card

private struct MyCodeCard: View {
    let code: String
    // False while signed out: the code only exists locally then and friends
    // cannot use it for live BAC until the user signs in.
    let isLive: Bool

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: "person.text.rectangle.fill")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 44, height: 44)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 4) {
                Text("Mein Code")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                Text(code.isEmpty ? "······" : code)
                    .font(.system(.title3, design: .monospaced, weight: .bold))
                    .foregroundStyle(Color.appText)
                    .tracking(4)
                if !isLive {
                    Text("Wird mit Anmeldung für Live-BAC aktiviert")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
            }

            Spacer()

            ShareLink(item: "Mein Freundes-Code für promille.: \(code)") {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 36, height: 36)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
    }
}

// MARK: - Active jam banner

// Shown above the friend code when the user is in a jam, so the jam is reachable
// with one tap instead of via the small waveform icon in the top bar.
private struct ActiveJamBanner: View {
    let jam: Jam
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: "waveform")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 44, height: 44)
                    .background(Color.appAccent.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color.statusGreen)
                            .frame(width: 6, height: 6)
                        Text("Aktiver Jam")
                            .font(.appCaptionBold)
                            .foregroundStyle(Color.statusGreen)
                    }
                    Text("\(max(1, jam.participants.count)) Teilnehmer · Tippen zum Öffnen")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.appAccent.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Friend SOS banner

// Shown at the very top when one or more friends have an active SOS.
private struct FriendSOSBanner: View {
    let members: [CrewMember]
    let onTap: (CrewMember) -> Void

    var body: some View {
        VStack(spacing: 8) {
            ForEach(members) { member in
                Button { onTap(member) } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "sos")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.statusRed)
                            .clipShape(Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(member.name) hat SOS ausgelöst")
                                .font(.appBodyBold)
                                .foregroundStyle(Color.statusRed)
                            Text("Tippen, um das Profil zu öffnen")
                                .font(.appCaption)
                                .foregroundStyle(Color.appTextDim)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color.statusRed.opacity(0.7))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color.statusRed.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(Color.statusRed.opacity(0.45), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Friend jam banner

// Lets the user jump straight into a friend's jam from the Friends tab, without
// opening the jam lobby first.
private struct FriendJamBanner: View {
    let jam: Jam
    let isJoining: Bool
    let onJoin: () -> Void

    var body: some View {
        Button(action: onJoin) {
            HStack(spacing: 14) {
                Image(systemName: "person.2.wave.2.fill")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 44, height: 44)
                    .background(Color.appAccent.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    Text("\(jam.hostName) jammt gerade")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text("Jam von Freunden · Tippen zum Beitreten")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }

                Spacer()

                if isJoining {
                    ProgressView().tint(Color.appAccent)
                } else {
                    Text("Beitreten")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appAccent)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.appAccent.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appAccent.opacity(0.25), lineWidth: 0.8)
            )
        }
        .buttonStyle(.plain)
        .disabled(isJoining)
    }
}

// MARK: - Swipe to delete row

private struct SwipeToDeleteRow<Content: View>: View {
    let onDelete: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var offset: CGFloat = 0
    @State private var prevOffset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .trailing) {
            Button(action: onDelete) {
                ZStack {
                    Color.statusRed
                    Image(systemName: "trash")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.trailing, 24)
                }
                .frame(minWidth: 80, maxWidth: 80, maxHeight: .infinity)
            }
            .buttonStyle(.plain)

            content()
                .background(Color.appCard)
                .offset(x: offset)
                .gesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { value in
                            guard abs(value.translation.width) > abs(value.translation.height) else { return }
                            let proposed = prevOffset + value.translation.width
                            offset = min(max(proposed, -80), 0)
                        }
                        .onEnded { value in
                            let proposed = prevOffset + value.translation.width
                            let shouldOpen = proposed < -40
                            prevOffset = shouldOpen ? -80 : 0
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                offset = prevOffset
                            }
                        }
                )
        }
        .clipped()
    }
}

// MARK: - Avatar

private struct CRAvatar: View {
    let initial: String
    let status: BACStatus
    let size: CGFloat

    var body: some View {
        Text(initial)
            .font(.system(size: size * 0.38, weight: .semibold))
            .foregroundStyle(status == .sober ? Color.appText : status.color)
            .frame(width: size, height: size)
            .background(status.backgroundColor)
            .clipShape(Circle())
            .overlay(
                Circle()
                    .strokeBorder(status.color.opacity(status == .sober ? 0.25 : 0.55), lineWidth: 1.5)
            )
    }
}

// MARK: - Care card

private struct CareCard: View {
    let member: CrewMember

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(member.bacStatus.color)
                Text("Aufmerksamkeit nötig")
                    .font(.appCaptionBold)
                    .foregroundStyle(member.bacStatus.color)
                Spacer()
                Text("Höchster Risikowert")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 10)

            Divider().background(Color.appBorder.opacity(0.6))

            HStack(spacing: 14) {
                CRAvatar(initial: member.avatarInitial, status: member.bacStatus, size: 48)

                VStack(alignment: .leading, spacing: 4) {
                    Text(member.name)
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    StatusPill(status: member.bacStatus)
                    if let mins = member.bacUpdatedMinutesAgo {
                        Text(mins <= 0 ? "Gerade aktualisiert" : "Aktualisiert vor \(mins) min")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 0) {
                    Text(String(format: "%.2f", member.estimatedBAC))
                        .font(.system(size: 38, weight: .light, design: .serif))
                        .foregroundStyle(member.bacStatus.color)
                        .monospacedDigit()
                    Text("‰")
                        .font(.appBodyBold)
                        .foregroundStyle(member.bacStatus.color)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .background(member.bacStatus.backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(member.bacStatus.color.opacity(0.4), lineWidth: 1)
        )
    }
}

// MARK: - Sober buddy card

private struct SoberBuddyCard: View {
    let member: CrewMember
    // Honors the user's Probezeit setting: a marked driver who is over the
    // legal limit is shown as "Darf nicht mehr fahren" instead of "Fahrbereit".
    let canDrive: Bool

    private var accent: Color { canDrive ? Color.statusGreen : Color.statusRed }

    var body: some View {
        HStack(spacing: 14) {
            CRAvatar(initial: member.avatarInitial, status: member.bacStatus, size: 40)

            VStack(alignment: .leading, spacing: 2) {
                Text(canDrive ? "Fahrbereit" : "Darf nicht mehr fahren")
                    .font(.appCaptionBold)
                    .foregroundStyle(accent)
                Text(member.name)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
            }

            Spacer()

            Image(systemName: canDrive ? "car.fill" : "exclamationmark.triangle.fill")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(accent)
                .frame(width: 36, height: 36)
                .background(accent.opacity(0.1))
                .clipShape(Circle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(accent.opacity(0.07))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(accent.opacity(0.28), lineWidth: 0.8)
        )
    }
}

// MARK: - Member row

private struct CrewMemberRow: View {
    let member: CrewMember
    let mayDrive: Bool
    let onToggleHome: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            CRAvatar(initial: member.avatarInitial, status: member.bacStatus, size: 40)

            VStack(alignment: .leading, spacing: 3) {
                Text(member.name)
                    .font(.appBody)
                    .foregroundStyle(member.isHome ? Color.appTextDim : Color.appText)

                HStack(spacing: 6) {
                    if member.isSoberBuddy && !member.isHome {
                        Text(mayDrive ? "Fahrbereit" : "Darf nicht mehr fahren")
                            .font(.appCaption)
                            .foregroundStyle(mayDrive ? Color.statusGreen : Color.statusRed)
                    } else if member.isHome {
                        Text("Zuhause angekommen")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    } else if member.hasLiveData {
                        Text(member.estimatedBAC.permilleString)
                            .font(.appCaption)
                            .foregroundStyle(member.bacStatus.color)
                            .monospacedDigit()
                        if let mins = member.bacUpdatedMinutesAgo {
                            Text(formattedTimeSinceUpdate(minutes: mins))
                                .font(.appCaption)
                                .foregroundStyle(Color.appTextMuted)
                        }
                    } else {
                        Text("Keine Live-Daten")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextMuted)
                    }
                }
            }

            Spacer()

            Button(action: onToggleHome) {
                Image(systemName: member.isHome ? "house.fill" : "house")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(member.isHome ? Color.statusGreen : Color.appTextDim)
                    .frame(width: 34, height: 34)
                    .background(member.isHome ? Color.statusGreen.opacity(0.12) : Color.appBackground)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

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

// MARK: - SOS bar

private struct SOSBar: View {
    let isActive: Bool
    let onSOS: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Button(action: onSOS) {
                HStack(spacing: 10) {
                    Image(systemName: isActive ? "checkmark.shield.fill" : "sos")
                        .font(.system(size: 16, weight: .semibold))
                    Text(isActive ? "SOS aktiv. Tippen zum Beenden" : "SOS senden")
                        .font(.appBodyBold)
                }
                .foregroundStyle(isActive ? Color.statusGreen : Color.appBackground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    isActive
                        ? Color.statusGreen.opacity(0.15)
                        : Color.statusRed
                )
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .strokeBorder(
                            isActive ? Color.statusGreen : Color.clear,
                            lineWidth: 1.5
                        )
                )
            }
            .buttonStyle(.plain)
            .animation(.easeInOut(duration: 0.25), value: isActive)
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

// MARK: - Preview

#Preview {
    let controller = PersistenceController.preview
    let supabase = SupabaseService()
    return CrewView()
        .modelContainer(controller.container)
        .environment(supabase)
        .environment(JamService(supabase: supabase))
        .preferredColorScheme(.dark)
}
