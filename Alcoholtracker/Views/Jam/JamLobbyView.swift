import SwiftData
import SwiftUI

// MARK: - JamLobbyView
//
// Shown by JamTabView when the user is not in a jam: start a jam, join by code,
// and browse nearby / friends' jams. Extracted from the former monolithic
// JamTabView so each jam screen lives in its own file.

struct JamLobbyView: View {
    @Environment(JamService.self) private var jamService
    @Environment(\.dismiss) private var dismiss
    @Query private var crewMembers: [CrewMember]

    @State private var showCreate = false
    @State private var codeInput  = ""
    @State private var joinError: String?
    @State private var isJoining  = false
    @State private var listJoinError: String?

    private var hasContent: Bool {
        !jamService.availableJamsNearby.isEmpty || !jamService.availableJamsFromFriends.isEmpty
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                lobbyHeader
                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {
                        startCard
                        codeSection
                        if !jamService.availableJamsNearby.isEmpty {
                            nearbySection
                        }
                        if !jamService.availableJamsFromFriends.isEmpty {
                            friendsSection
                        }
                        if !hasContent {
                            emptyHint
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 20)
                }
            }
        }
        .onAppear {
            // Friend codes feed the "Von Freunden" filter and the
            // friends-only access check when joining per code.
            jamService.friendCodes = crewMembers.compactMap(\.friendCode)
            jamService.startBrowsing()
        }
        .onDisappear {
            // Joining replaces the lobby with ActiveJamView; the browser that
            // joinJam just started must survive that transition, otherwise no
            // proximity peers are found during the jam.
            if jamService.currentJam == nil {
                jamService.stopBrowsing()
            }
        }
        .sheet(isPresented: $showCreate) {
            CreateJamSheet()
        }
        .alert(
            "Beitritt fehlgeschlagen",
            isPresented: Binding(
                get: { listJoinError != nil },
                set: { if !$0 { listJoinError = nil } }
            )
        ) {
            Button("OK", role: .cancel) { listJoinError = nil }
        } message: {
            Text(listJoinError ?? "")
        }
    }

    // MARK: Subviews

    private var lobbyHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Jam")
                    .font(.appHeadline)
                    .foregroundStyle(Color.appText)
                Text("Verbinde dich mit deiner Crew")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
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
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var startCard: some View {
        Button { showCreate = true } label: {
            HStack(spacing: 16) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 28, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Jam starten")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text("Eigenen Jam erstellen")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(Color.appAccent.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var codeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "MIT CODE BEITRETEN")
            HStack(spacing: 10) {
                TextField("6-stelliger Code", text: $codeInput)
                    .font(.system(.body, design: .monospaced, weight: .semibold))
                    .foregroundStyle(Color.appText)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.characters)
                    .onChange(of: codeInput) { _, v in
                        // Codes are A-Z/2-9 only; stray characters from paste
                        // would otherwise end up in the server query.
                        let cleaned = String(v.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }.prefix(6))
                        codeInput = cleaned
                        joinError = nil
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                    )

                Button {
                    joinByCode()
                } label: {
                    Group {
                        if isJoining {
                            ProgressView()
                                .tint(Color.appBackground)
                        } else {
                            Text("Beitreten")
                                .font(.appCaptionBold)
                        }
                    }
                    .foregroundStyle(Color.appBackground)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(codeInput.count == 6 ? Color.appAccent : Color.appTextMuted)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(codeInput.count < 6 || isJoining)
            }
            if let err = joinError {
                Text(err)
                    .font(.appCaption)
                    .foregroundStyle(Color.statusRed)
            }
        }
    }

    private var nearbySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "wave.3.right.circle.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appAccent)
                SectionLabel(text: "IN DER NÄHE")
            }
            VStack(spacing: 0) {
                ForEach(jamService.availableJamsNearby) { jam in
                    LobbyJamRow(jam: jam, isDisabled: isJoining, onJoin: { joinNearby(jam) })
                    if jam.id != jamService.availableJamsNearby.last?.id {
                        Divider().background(Color.appBorder).padding(.leading, 16)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
        }
    }

    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "person.2.circle.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.appAccent)
                SectionLabel(text: "VON FREUNDEN")
            }
            VStack(spacing: 0) {
                ForEach(jamService.availableJamsFromFriends) { jam in
                    LobbyJamRow(jam: jam, isDisabled: isJoining, onJoin: { joinFromFriend(jam) })
                    if jam.id != jamService.availableJamsFromFriends.last?.id {
                        Divider().background(Color.appBorder).padding(.leading, 16)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
        }
    }

    private var emptyHint: some View {
        VStack(spacing: 10) {
            Image(systemName: "antenna.radiowaves.left.and.right.slash")
                .font(.system(size: 36, weight: .ultraLight))
                .foregroundStyle(Color.appTextMuted)
            Text("Niemand in der Nähe oder von Freunden.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .multilineTextAlignment(.center)
            Text("Starte selbst einen Jam oder gib einen Code ein.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextMuted)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 20)
    }

    // MARK: Actions

    private func joinByCode() {
        joinError = nil
        isJoining = true
        Task {
            do {
                try await jamService.joinJamByCode(codeInput)
            } catch {
                joinError = error.localizedDescription
            }
            isJoining = false
        }
    }

    private func joinNearby(_ jam: Jam) {
        isJoining = true
        Task {
            do {
                try await jamService.joinJamNearby(jam)
            } catch {
                listJoinError = error.localizedDescription
            }
            isJoining = false
        }
    }

    private func joinFromFriend(_ jam: Jam) {
        isJoining = true
        Task {
            do {
                try await jamService.joinJamFromFriend(jam)
            } catch {
                listJoinError = error.localizedDescription
            }
            isJoining = false
        }
    }
}

// MARK: - Lobby jam row

private struct LobbyJamRow: View {
    let jam: Jam
    var isDisabled: Bool = false
    let onJoin: () -> Void

    private func relativeTime(at now: Date) -> String {
        let mins = Int(now.timeIntervalSince(jam.createdAt) / 60)
        if mins < 1  { return "Gerade gestartet" }
        if mins < 60 { return "vor \(mins) min" }
        return "vor \(mins / 60) h"
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: jam.visibility.icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 38, height: 38)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(jam.hostName)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                HStack(spacing: 6) {
                    // The host is always present, so never show "0 Teilnehmer"
                    // even before the full roster has synced in.
                    Text("\(max(1, jam.participants.count)) Teilnehmer")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                    Text("·")
                        .foregroundStyle(Color.appTextMuted)
                    TimelineView(.periodic(from: .now, by: 60)) { timeline in
                        Text(relativeTime(at: timeline.date))
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextMuted)
                    }
                }
            }
            Spacer()
            Button(action: onJoin) {
                Text("Beitreten")
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            .opacity(isDisabled ? 0.45 : 1)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - CreateJamSheet

struct CreateJamSheet: View {
    @Environment(JamService.self) private var jamService
    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var visibility: Jam.JamVisibility = .proximityAndCode
    @State private var settings   = JamSettings()
    @State private var isCreating = false
    @State private var createError: String?

    private func isLocked(_ vis: Jam.JamVisibility) -> Bool {
        vis.usesServer && !supabase.isSignedIn
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                Form {
                    Section {
                        ForEach(Jam.JamVisibility.allCases, id: \.self) { vis in
                            Button { visibility = vis } label: {
                                HStack(spacing: 14) {
                                    Image(systemName: vis.icon)
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundStyle(Color.appAccent)
                                        .frame(width: 32, height: 32)
                                        .background(Color.appAccent.opacity(0.1))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(vis.rawValue)
                                            .font(.appBody)
                                            .foregroundStyle(Color.appText)
                                        Text(vis.description)
                                            .font(.appCaption)
                                            .foregroundStyle(Color.appTextDim)
                                    }
                                    Spacer()
                                    if isLocked(vis) {
                                        Image(systemName: "lock.fill")
                                            .font(.system(size: 12))
                                            .foregroundStyle(Color.appTextMuted)
                                    } else if visibility == vis {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundStyle(Color.appAccent)
                                    }
                                }
                                .opacity(isLocked(vis) ? 0.45 : 1)
                            }
                            .buttonStyle(.plain)
                            .disabled(isLocked(vis))
                        }
                    } header: {
                        Text("Wer kann beitreten?")
                    } footer: {
                        if !supabase.isSignedIn {
                            Text("Ohne Anmeldung ist nur der Offline-Modus über Bluetooth verfügbar.")
                        }
                    }

                    Section("Was teilst du?") {
                        Toggle("Promille-Wert", isOn: $settings.shareBAC)
                        Toggle("Status (Lustig, Wackelig...)", isOn: $settings.shareStatus)
                        Toggle("Was du getrunken hast", isOn: $settings.shareDrinks)
                        Toggle("Anzahl der Drinks", isOn: $settings.shareDrinkCount)
                        Toggle("SOS-Aktivierung", isOn: $settings.shareSOSStatus)
                        Toggle("Foto-Memories", isOn: $settings.sharePhotos)
                    }

                    Section("Interaktion") {
                        Toggle("Andere können dir winken", isOn: $settings.allowWaves)
                        Toggle("Freunde joinen automatisch", isOn: $settings.autoAcceptFriends)
                    }

                    if let err = createError {
                        Section {
                            Text(err)
                                .font(.appCaption)
                                .foregroundStyle(Color.statusRed)
                        }
                    }

                    Section {
                        Button {
                            startJam()
                        } label: {
                            HStack {
                                Spacer()
                                if isCreating {
                                    ProgressView()
                                } else {
                                    HStack(spacing: 8) {
                                        Image(systemName: "waveform")
                                        Text("Jam starten")
                                    }
                                    .font(.appBodyBold)
                                    .foregroundStyle(Color.appBackground)
                                }
                                Spacer()
                            }
                            .padding(.vertical, 4)
                        }
                        .disabled(isCreating)
                        .listRowBackground(Color.appAccent)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Jam erstellen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                        .foregroundStyle(Color.appAccent)
                }
            }
            .tint(Color.appAccent)
            .onAppear {
                if isLocked(visibility) {
                    visibility = .proximityOnly
                }
            }
        }
    }

    private func startJam() {
        createError = nil
        isCreating  = true
        Task {
            do {
                try await jamService.createJam(visibility: visibility, settings: settings)
                dismiss()
            } catch {
                createError = error.localizedDescription
            }
            isCreating = false
        }
    }
}
