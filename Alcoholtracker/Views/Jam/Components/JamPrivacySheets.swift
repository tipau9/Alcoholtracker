import SwiftUI

// MARK: - Jam privacy sheets
//
// The live "what am I sharing" sheet and the per-participant detail sheet
// (long-press), plus their shared flow-tag layout. Extracted from the former
// monolithic JamTabView.

// MARK: - Live privacy settings sheet

struct JamPrivacySheet: View {
    @Environment(JamService.self) private var jamService
    @Environment(\.dismiss) private var dismiss

    @State private var draft: JamSettings

    init(currentSettings: JamSettings) {
        _draft = State(initialValue: currentSettings)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                Form {
                    Section("Was teilst du gerade?") {
                        Toggle("Promille-Wert", isOn: $draft.shareBAC)
                        Toggle("Status (Lustig, Wackelig...)", isOn: $draft.shareStatus)
                        Toggle("Was du getrunken hast", isOn: $draft.shareDrinks)
                        Toggle("Anzahl der Drinks", isOn: $draft.shareDrinkCount)
                        Toggle("SOS-Aktivierung", isOn: $draft.shareSOSStatus)
                        Toggle("Foto-Memories", isOn: $draft.sharePhotos)
                    }
                    Section("Interaktion") {
                        Toggle("Andere können dir winken", isOn: $draft.allowWaves)
                    }
                    Section {
                        Text("Änderungen werden sofort an alle Teilnehmer übertragen.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Meine Privatsphäre")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fertig") { dismiss() }
                        .foregroundStyle(Color.appAccent)
                }
            }
            .tint(Color.appAccent)
            // Apply on disappear so swiping the sheet down does not silently
            // discard the changes; the text above promises immediate effect.
            .onDisappear {
                jamService.updateMySettings(draft)
            }
        }
    }
}

// MARK: - Participant privacy detail sheet (long-press)

struct ParticipantPrivacySheet: View {
    let participant: JamParticipant
    var canKick: Bool = false
    var onKick: () -> Void = {}
    var canTransferHost: Bool = false
    var onTransferHost: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var showKickConfirm = false
    @State private var showTransferConfirm = false

    private var shared: [String] {
        guard let s = participant.sharedSettings else { return [] }
        var list: [String] = []
        if s.shareBAC        { list.append("Promille-Wert") }
        if s.shareStatus     { list.append("Status") }
        if s.shareDrinks     { list.append("Drinks") }
        if s.shareDrinkCount { list.append("Anzahl Drinks") }
        if s.shareSOSStatus  { list.append("SOS-Status") }
        if s.sharePhotos     { list.append("Fotos") }
        return list
    }

    private var hidden: [String] {
        guard let s = participant.sharedSettings else { return [] }
        var list: [String] = []
        if !s.shareBAC        { list.append("Promille-Wert") }
        if !s.shareStatus     { list.append("Status") }
        if !s.shareDrinks     { list.append("Drinks") }
        if !s.shareDrinkCount { list.append("Anzahl Drinks") }
        if !s.shareSOSStatus  { list.append("SOS-Status") }
        if !s.sharePhotos     { list.append("Fotos") }
        return list
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)

                HStack(spacing: 14) {
                    Text(participant.avatar)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.appText)
                        .frame(width: 44, height: 44)
                        .background(Color.appBorder)
                        .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(participant.displayName)
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appText)
                        Text(participant.connectionType.label)
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
                .padding(.bottom, 20)

                VStack(spacing: 16) {
                    if participant.sharedSettings == nil {
                        // Settings travel only over the Bluetooth channel;
                        // server-synced participants do not transmit them.
                        Text("Privatsphäre-Details sind nur bei direkter Bluetooth-Verbindung sichtbar. Verborgene Werte bleiben trotzdem verborgen.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if !shared.isEmpty {
                        privacyGroup(
                            title: "\(participant.displayName) teilt:",
                            items: shared,
                            color: Color.statusGreen
                        )
                    }
                    if !hidden.isEmpty {
                        privacyGroup(
                            title: "Verbirgt:",
                            items: hidden,
                            color: Color.appTextMuted
                        )
                    }

                    if canTransferHost {
                        Button { showTransferConfirm = true } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "crown.fill")
                                Text("Host übergeben")
                            }
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appAccent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.appAccent.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 4)
                    }

                    if canKick {
                        Button { showKickConfirm = true } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "person.fill.xmark")
                                Text("Aus Jam entfernen")
                            }
                            .font(.appBodyBold)
                            .foregroundStyle(Color.statusRed)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.statusRed.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .strokeBorder(Color.statusRed.opacity(0.3), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 4)
                    }
                }
                .padding(.horizontal, 20)
                Spacer()
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.hidden)
        .confirmationDialog(
            "Teilnehmer entfernen?",
            isPresented: $showKickConfirm,
            titleVisibility: .visible
        ) {
            Button("Entfernen", role: .destructive) {
                onKick()
                dismiss()
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("\(participant.displayName) wird aus dem Jam entfernt.")
        }
        .confirmationDialog(
            "Host übergeben?",
            isPresented: $showTransferConfirm,
            titleVisibility: .visible
        ) {
            Button("Host übergeben") {
                onTransferHost()
                dismiss()
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("\(participant.displayName) wird zum Host. Du bleibst als Teilnehmer im Jam.")
        }
    }

    private func privacyGroup(title: String, items: [String], color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.appCaptionBold)
                .foregroundStyle(Color.appTextDim)
            FlowTags(items: items, color: color)
        }
    }
}

// MARK: - Flow tag layout

private struct FlowTags: View {
    let items: [String]
    let color: Color

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 90, maximum: 160))],
            alignment: .leading,
            spacing: 8
        ) {
            ForEach(items, id: \.self) { item in
                Text(item)
                    .font(.appCaption)
                    .foregroundStyle(color)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(color.opacity(0.1))
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(color.opacity(0.25), lineWidth: 0.5))
            }
        }
    }
}
