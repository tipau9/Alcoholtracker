import PhotosUI
import SwiftData
import SwiftUI

// MARK: - ActiveJamView
//
// Shown by JamTabView while the user is in a jam: participant roster, jam code,
// shared photos, SOS, and leave. Extracted from the former monolithic
// JamTabView along with its private row/banner helpers.

struct ActiveJamView: View {
    let jam: Jam
    @Environment(JamService.self) private var jamService
    @Environment(SupabaseService.self) private var supabase
    @Environment(\.modelContext) private var context
    @Query private var crewMembers: [CrewMember]
    @State private var showPrivacySettings = false
    @State private var showLeaveConfirm   = false
    @State private var longPressParticipant: JamParticipant?
    @State private var photoPickerItem: PhotosPickerItem?
    @State private var fullscreenPhoto: FullscreenPhoto?
    @State private var photoShareError: String?
    @State private var showWaterContest = false
    @State private var showInviteSheet  = false

    // Friends whose display name is not yet among the jam participants.
    // Name-based match is pragmatic: we don't hold friend UUIDs locally.
    private var uninvitedFriends: [CrewMember] {
        let participantNames = Set(jam.participants.map { $0.displayName.lowercased() })
        return crewMembers.filter { m in
            !m.isSelf && !participantNames.contains(m.name.lowercased())
        }
    }

    private var sortedParticipants: [JamParticipant] {
        // Stable tiebreak so rows of equal BAC do not swap on every update.
        jam.participants.sorted {
            let a = $0.currentBAC ?? -1
            let b = $1.currentBAC ?? -1
            if a != b { return a > b }
            return $0.id.uuidString < $1.id.uuidString
        }
    }

    private var sosParticipants: [JamParticipant] {
        jam.participants.filter { $0.hasSOSActive }
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                activeHeader
                if !uninvitedFriends.isEmpty {
                    uninvitedFriendsStrip
                }
                if !sosParticipants.isEmpty {
                    SOSBanner(participants: sosParticipants)
                }
                Divider().background(Color.appBorder)
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        jamCodeCard
                        participantList
                        if !jamService.receivedPhotos.isEmpty {
                            jamPhotoStrip
                        }
                        actionButtons
                        leaveButton
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                }
            }
        }
        .sheet(isPresented: $showPrivacySettings) {
            JamPrivacySheet(currentSettings: jamService.mySettings)
        }
        .sheet(isPresented: $showInviteSheet) {
            InviteFriendsSheet(jam: jam, friends: uninvitedFriends)
        }
        .sheet(item: $longPressParticipant) { p in
            ParticipantPrivacySheet(
                participant: p,
                canKick: jamService.canKick(p),
                onKick: { jamService.kickParticipant(p) },
                canTransferHost: jamService.canTransferHost(p),
                onTransferHost: { jamService.transferHost(to: p) }
            )
        }
        .sheet(item: Binding(
            get: { jamService.incomingRoulette },
            set: { if $0 == nil { jamService.incomingRoulette = nil } }
        )) { payload in
            RoundRouletteSheet(
                payload: payload,
                onReroll: { jamService.startRoulette() },
                onClose: { jamService.incomingRoulette = nil }
            )
        }
        .sheet(isPresented: $showWaterContest) {
            WaterContestSheet()
        }
        .sheet(item: $fullscreenPhoto) { full in
            ZStack {
                Color.black.ignoresSafeArea()
                Image(uiImage: full.image)
                    .resizable()
                    .scaledToFit()
                if let bac = full.bac, bac > 0 {
                    VStack {
                        Spacer()
                        Text(bac.permilleString)
                            .font(.system(size: 20, weight: .bold, design: .serif))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(BACStatus(bac: bac).color.opacity(0.85))
                            .clipShape(Capsule())
                            .padding(.bottom, 40)
                    }
                }
            }
            .presentationDetents([.large])
        }
        .onChange(of: photoPickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    do {
                        try jamService.sendPhoto(image)
                        // Save to personal photo history with BAC at time of capture.
                        if let filename = PhotoMemoryService.save(image) {
                            let bac = jamService.myCurrentBAC > 0 ? jamService.myCurrentBAC : nil
                            context.insert(PhotoMemory(filename: filename, bacAtTime: bac))
                            try? context.save()
                        }
                    } catch {
                        photoShareError = error.localizedDescription
                    }
                }
                photoPickerItem = nil
            }
        }
        .alert(
            "Foto nicht geteilt",
            isPresented: Binding(
                get: { photoShareError != nil },
                set: { if !$0 { photoShareError = nil } }
            )
        ) {
            Button("OK", role: .cancel) { photoShareError = nil }
        } message: {
            Text(photoShareError ?? "")
        }
        .confirmationDialog(
            "Jam verlassen?",
            isPresented: $showLeaveConfirm,
            titleVisibility: .visible
        ) {
            Button("Verlassen", role: .destructive) { jamService.leaveJam() }
            Button("Abbrechen", role: .cancel) {}
        }
    }

    // MARK: Subviews

    private var uninvitedFriendsStrip: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                Image(systemName: "person.badge.plus")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.statusOrange)
                Text("Noch nicht dabei")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Spacer()
                Button { showInviteSheet = true } label: {
                    Text("Alle einladen")
                        .font(.appMicro)
                        .foregroundStyle(Color.appAccent)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(uninvitedFriends) { friend in
                        FriendInviteChip(friend: friend, jam: jam)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
            }
        }
        .background(Color.statusOrange.opacity(0.05))
    }

    private var activeHeader: some View {
        HStack(spacing: 12) {
            Image(systemName: "waveform")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 40, height: 40)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 2) {
                Text(jam.hostName)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.statusGreen)
                        .frame(width: 6, height: 6)
                    Text("Jam aktiv")
                        .font(.appCaption)
                        .foregroundStyle(Color.statusGreen)
                }
            }
            Spacer()
            Text("\(jam.participants.count)")
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
            Button { showInviteSheet = true } label: {
                Image(systemName: "person.badge.plus")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(uninvitedFriends.isEmpty ? Color.appTextDim : Color.appAccent)
                    .frame(width: 32, height: 32)
                    .background(uninvitedFriends.isEmpty ? Color.appCard : Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(
                        uninvitedFriends.isEmpty ? Color.appBorder : Color.appAccent.opacity(0.3),
                        lineWidth: 0.5
                    ))
            }
            .buttonStyle(.plain)
            Button { showPrivacySettings = true } label: {
                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 15, weight: .medium))
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

    private var jamCodeCard: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Jam Code")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                Text(jam.code)
                    .font(.system(.title2, design: .monospaced, weight: .bold))
                    .foregroundStyle(Color.appText)
                    .tracking(6)
            }
            Spacer()
            ShareLink(
                item: "Tritt meinem Jam bei! Code: \(jam.code)"
            ) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 40, height: 40)
                    .background(Color.appAccent.opacity(0.12))
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    private var participantList: some View {
        VStack(spacing: 0) {
            ForEach(sortedParticipants) { participant in
                ActiveParticipantRow(participant: participant)
                    .onLongPressGesture {
                        longPressParticipant = participant
                    }
                if participant.id != sortedParticipants.last?.id {
                    Divider().background(Color.appBorder).padding(.leading, 68)
                }
            }
        }
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    private var jamPhotoStrip: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Jam-Fotos")
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
                .padding(.leading, 2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(jamService.receivedPhotos) { photo in
                        JamPhotoThumb(photo: photo) { image in
                            fullscreenPhoto = FullscreenPhoto(image: image, bac: photo.senderBAC)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }

    private var actionButtons: some View {
      VStack(spacing: 12) {
        HStack(spacing: 12) {
            PhotosPicker(selection: $photoPickerItem, matching: .images) {
                VStack(spacing: 6) {
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                    Text("Foto teilen")
                        .font(.appCaption)
                        .foregroundStyle(Color.appAccent)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.appAccent.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.8)
                )
            }

            ActionChip(icon: "drop.fill", label: "Wasser") {
                showWaterContest = true
            }
        }
        HStack(spacing: 12) {
            ActionChip(icon: "dice.fill", label: "Runde") {
                jamService.startRoulette()
            }

            ActionChip(
                icon: jamService.mySOSActive ? "checkmark.shield.fill" : "sos",
                label: jamService.mySOSActive ? "SOS aktiv" : "SOS",
                tint: jamService.mySOSActive ? Color.statusGreen : Color.statusRed
            ) {
                jamService.mySOSActive.toggle()
            }
        }
        ActionChip(icon: "person.badge.plus", label: "Freunde einladen") {
            showInviteSheet = true
        }
      }
    }

    private var leaveButton: some View {
        Button { showLeaveConfirm = true } label: {
            Text("Jam verlassen")
                .font(.appBodyBold)
                .foregroundStyle(Color.statusRed)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.statusRed.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(Color.statusRed.opacity(0.3), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Jam photo thumbnail
//
// Decodes the JPEG once per photo in .task instead of in every body pass;
// with a dozen 200 KB photos the inline decode caused visible scroll jank.

private struct JamPhotoThumb: View {
    let photo: JamPhotoPayload
    let onTap: (UIImage) -> Void

    @State private var image: UIImage?

    var body: some View {
        Button {
            if let image { onTap(image) }
        } label: {
            VStack(spacing: 4) {
                Group {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    } else {
                        Color.appBorder
                    }
                }
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                // BAC the photo was taken at, pinned to the bottom of the thumb.
                .overlay(alignment: .bottomLeading) {
                    if let bac = photo.senderBAC, bac > 0 {
                        Text(bac.permilleString)
                            .font(.system(size: 9, weight: .bold))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(BACStatus(bac: bac).color.opacity(0.9))
                            .clipShape(Capsule())
                            .padding(4)
                    }
                }

                Text(photo.senderName)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .task(id: photo.id) {
            let data = photo.jpegData
            image = await Task.detached(priority: .userInitiated) {
                UIImage(data: data)
            }.value
        }
    }
}

// MARK: - SOS Banner

private struct SOSBanner: View {
    let participants: [JamParticipant]

    private var names: String {
        participants.map { $0.displayName }.joined(separator: ", ")
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "sos")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.white)
            VStack(alignment: .leading, spacing: 1) {
                Text("SOS aktiv")
                    .font(.appCaptionBold)
                    .foregroundStyle(.white)
                Text(names)
                    .font(.appCaption)
                    .foregroundStyle(.white.opacity(0.85))
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(Color.statusRed)
    }
}

// MARK: - Participant row in active jam

private struct ActiveParticipantRow: View {
    let participant: JamParticipant

    private var bacText: String {
        if participant.sharedSettings?.shareBAC == false {
            return "BAC verborgen"
        }
        guard let bac = participant.currentBAC else { return "Lädt..." }
        return bac.permilleString
    }

    private var bacColor: Color {
        guard participant.sharedSettings?.shareBAC != false,
              let bac = participant.currentBAC else { return Color.appTextMuted }
        return BACStatus(bac: bac).color
    }

    private var statusText: String? {
        guard participant.sharedSettings?.shareStatus != false else { return nil }
        return participant.currentStatus
    }

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Text(participant.avatar)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.appText)
                    .frame(width: 40, height: 40)
                    .background(Color.appBorder)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 1))

                if participant.hasSOSActive {
                    Circle()
                        .fill(Color.statusRed)
                        .frame(width: 12, height: 12)
                        .overlay(
                            Image(systemName: "exclamationmark")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundStyle(.white)
                        )
                        .offset(x: 14, y: -14)
                }
            }
            .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(participant.displayName)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                    Image(systemName: participant.connectionType.icon)
                        .font(.system(size: 10))
                        .foregroundStyle(Color.appTextMuted)
                }
                if let st = statusText {
                    Text(st)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
            }

            Spacer()

            Text(bacText)
                .font(.system(size: 17, weight: .light, design: .serif))
                .foregroundStyle(bacColor)
                .monospacedDigit()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

// MARK: - Action chip

private struct ActionChip: View {
    let icon: String
    let label: String
    var tint: Color = Color.appAccent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(tint)
                Text(label)
                    .font(.appCaption)
                    .foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(tint.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(tint.opacity(0.3), lineWidth: 0.8)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Fullscreen photo (carries the BAC it was taken at)

private struct FullscreenPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
    let bac: Double?
}

// MARK: - Invite friends sheet

private struct InviteFriendsSheet: View {
    let jam: Jam
    let friends: [CrewMember]
    @Environment(\.dismiss) private var dismiss
    @Environment(SupabaseService.self) private var supabase
    @State private var sentIDs: Set<UUID> = []

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Text("Freunde einladen")
                        .font(.appHeadline)
                        .foregroundStyle(Color.appText)
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
                .padding(.top, 20)
                .padding(.bottom, 16)

                if friends.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "person.3")
                            .font(.system(size: 40))
                            .foregroundStyle(Color.appTextMuted)
                        Text("Alle Freunde sind dabei!")
                            .font(.appBody)
                            .foregroundStyle(Color.appTextDim)
                        Text("Alle deine Crew-Mitglieder sind bereits im Jam.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 32)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 0) {
                            ForEach(friends) { friend in
                                HStack(spacing: 12) {
                                    Text(friend.avatarInitial)
                                        .font(.appBodyBold)
                                        .foregroundStyle(Color.statusOrange)
                                        .frame(width: 40, height: 40)
                                        .background(Color.statusOrange.opacity(0.12))
                                        .clipShape(Circle())
                                        .overlay(Circle().strokeBorder(Color.statusOrange.opacity(0.3), lineWidth: 1))
                                    Text(friend.name)
                                        .font(.appBody)
                                        .foregroundStyle(Color.appText)
                                    Spacer()
                                    HStack(spacing: 8) {
                                        // In-app notification via Supabase (shown when friend has a code)
                                        if let code = friend.friendCode {
                                            Button {
                                                sentIDs.insert(friend.id)
                                                Task {
                                                    await supabase.sendJamInvitation(
                                                        inviteeCode: code,
                                                        jamID: jam.id,
                                                        jamCode: jam.code,
                                                        hostName: jam.hostName
                                                    )
                                                }
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Image(systemName: sentIDs.contains(friend.id) ? "checkmark" : "bell.badge")
                                                        .font(.system(size: 11, weight: .semibold))
                                                    Text(sentIDs.contains(friend.id) ? "Eingeladen" : "Benachrichtigen")
                                                        .font(.appCaptionBold)
                                                }
                                                .foregroundStyle(sentIDs.contains(friend.id) ? Color.statusGreen : Color.appAccent)
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background((sentIDs.contains(friend.id) ? Color.statusGreen : Color.appAccent).opacity(0.12))
                                                .clipShape(Capsule())
                                                .overlay(Capsule().strokeBorder(
                                                    (sentIDs.contains(friend.id) ? Color.statusGreen : Color.appAccent).opacity(0.3),
                                                    lineWidth: 0.5
                                                ))
                                            }
                                            .buttonStyle(.plain)
                                            .disabled(sentIDs.contains(friend.id))
                                            .animation(.easeInOut(duration: 0.2), value: sentIDs.contains(friend.id))
                                        }
                                        // OS share sheet as fallback
                                        ShareLink(item: "Tritt meinem Jam bei! Code: \(jam.code)") {
                                            Image(systemName: "square.and.arrow.up")
                                                .font(.system(size: 13, weight: .medium))
                                                .foregroundStyle(Color.appTextDim)
                                                .frame(width: 32, height: 32)
                                                .background(Color.appCard)
                                                .clipShape(Circle())
                                                .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                                if friend.id != friends.last?.id {
                                    Divider().background(Color.appBorder).padding(.leading, 68)
                                }
                            }
                        }
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.appBorder, lineWidth: 0.5))
                        .padding(.horizontal, 20)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Friend invite chip (used in uninvited strip)

private struct FriendInviteChip: View {
    let friend: CrewMember
    let jam: Jam
    @Environment(SupabaseService.self) private var supabase
    @State private var invited = false

    var body: some View {
        Button {
            guard !invited, let code = friend.friendCode else { return }
            invited = true
            Task {
                await supabase.sendJamInvitation(
                    inviteeCode: code,
                    jamID: jam.id,
                    jamCode: jam.code,
                    hostName: jam.hostName
                )
            }
        } label: {
            VStack(spacing: 5) {
                ZStack(alignment: .topTrailing) {
                    Text(friend.avatarInitial)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(invited ? Color.appTextMuted : Color.statusOrange)
                        .frame(width: 40, height: 40)
                        .background(invited ? Color.appCard : Color.statusOrange.opacity(0.14))
                        .clipShape(Circle())
                        .overlay(Circle().strokeBorder(
                            invited ? Color.appBorder : Color.statusOrange.opacity(0.4),
                            lineWidth: 1
                        ))
                    Image(systemName: invited ? "checkmark.circle.fill" : "plus.circle.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(invited ? Color.statusGreen : Color.statusOrange)
                        .background(Color.appBackground.clipShape(Circle()))
                        .offset(x: 5, y: -5)
                }
                Text(friend.name)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(invited ? Color.appTextMuted : Color.appText)
                    .lineLimit(1)
                    .frame(width: 58)
            }
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.2), value: invited)
        .disabled(friend.friendCode == nil)
    }
}
