import SwiftUI
import SwiftData

// MARK: - AddFriendSheet
//
// When the user is signed in and Supabase is configured:
//   entering a 6-character code auto-triggers a friend lookup.
// When signed out: manual name entry only (offline mode).

struct AddFriendSheet: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(SupabaseService.self) private var supabase
    @Query private var existingMembers: [CrewMember]

    @State private var name = ""
    @State private var friendCode = ""

    @State private var lookupResult: FriendProfile? = nil
    @State private var isLooking = false
    @State private var lookupError: String? = nil
    @State private var lookupTask: Task<Void, Never>? = nil

    // Server-generated profile codes are longer than the 6-char jam codes;
    // accept a range instead of hardcoding one length.
    private let codeLengthRange = 6...12

    private var liveMode: Bool { supabase.isSignedIn && supabase.isConfigured }

    private var isValid: Bool {
        if liveMode {
            // The code must have resolved to a real profile: storing a dead
            // code would silently never sync any BAC. The name is optional,
            // it falls back to the profile's display name.
            return lookupResult != nil
        }
        return !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func isDuplicate(_ code: String) -> Bool {
        existingMembers.contains { $0.friendCode?.uppercased() == code.uppercased() }
    }

    // The user's own friend code. Adding yourself would create a self-referential
    // "friend" that can never sync, so it is rejected.
    private var myOwnCode: String {
        if let c = supabase.myProfile?.friendCode, !c.isEmpty { return c }
        return UserDefaults.standard.string(forKey: "myFriendCode") ?? ""
    }

    private func isOwnCode(_ code: String) -> Bool {
        let mine = SupabaseService.sanitizeCode(myOwnCode)
        return !mine.isEmpty && SupabaseService.sanitizeCode(code) == mine
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack(spacing: 14) {
                    Image(systemName: "person.badge.plus")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))

                    Text("Freund hinzufügen")
                        .font(.appBodyBold)
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
                .padding(.top, 12)
                .padding(.bottom, 20)

                VStack(spacing: 14) {

                    // Code field (required in live mode, optional otherwise)
                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: liveMode ? "FREUNDES-CODE" : "FREUNDES-CODE (OPTIONAL)")
                        TextField("Code eingeben", text: $friendCode)
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.characters)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )

                        // Lookup feedback (live mode only)
                        if liveMode && !friendCode.isEmpty {
                            if isLooking {
                                HStack(spacing: 8) {
                                    ProgressView().scaleEffect(0.78)
                                    Text("Freund wird gesucht...")
                                        .font(.appCaption)
                                        .foregroundStyle(Color.appTextDim)
                                }
                            } else if let found = lookupResult {
                                HStack(spacing: 8) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(Color.statusGreen)
                                    Text("\(found.displayName.isEmpty ? found.friendCode : found.displayName) gefunden")
                                        .font(.appCaption)
                                        .foregroundStyle(Color.statusGreen)
                                }
                            } else if let err = lookupError {
                                HStack(spacing: 8) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundStyle(Color.statusRed)
                                    Text(err)
                                        .font(.appCaption)
                                        .foregroundStyle(Color.statusRed)
                                        .multilineTextAlignment(.leading)
                                }
                            }
                        }
                    }

                    // Name field. In live mode the profile's display name is
                    // used automatically; typing here only overrides it.
                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: liveMode ? "NAME (OPTIONAL)" : "NAME")
                        TextField(liveMode ? "Wird vom Profil übernommen" : "z.B. Max Mustermann", text: $name)
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                            .autocorrectionDisabled()
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )
                    }

                    PrimaryButton(title: "Hinzufügen", icon: "plus", isDisabled: !isValid) {
                        addFriend()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .presentationDetents([.height(480)])
        .presentationDragIndicator(.hidden)
        .onChange(of: friendCode) { _, code in
            let cleaned = String(code.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }.prefix(codeLengthRange.upperBound))
            if cleaned != code { friendCode = cleaned; return }

            lookupTask?.cancel()
            lookupResult = nil
            lookupError = nil
            guard cleaned.count >= codeLengthRange.lowerBound else { return }
            if isOwnCode(cleaned) {
                lookupError = "Das ist dein eigener Code. Du kannst dich nicht selbst hinzufügen."
                return
            }
            if isDuplicate(cleaned) {
                lookupError = "Dieser Code ist bereits in deiner Liste."
                return
            }
            guard liveMode else { return }
            // Debounced: codes vary in length, so wait until typing pauses
            // instead of firing a failing lookup at every keystroke past 6.
            lookupTask = Task {
                try? await Task.sleep(for: .milliseconds(400))
                guard !Task.isCancelled else { return }
                await lookup(cleaned)
            }
        }
    }

    // MARK: Lookup

    private func lookup(_ code: String) async {
        isLooking = true
        defer { isLooking = false }
        do {
            let found = try await supabase.lookupFriend(code: code)
            guard code == friendCode else { return }  // stale response, input changed
            lookupResult = found
        } catch let e as SupabaseError {
            guard code == friendCode else { return }
            lookupError = e.errorDescription
        } catch {
            guard code == friendCode else { return }
            lookupError = error.localizedDescription
        }
    }

    // MARK: Add

    private func addFriend() {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        let code = friendCode.trimmingCharacters(in: .whitespaces).uppercased()

        // Manual entry wins; otherwise the looked-up profile name is used.
        let resolvedName: String
        if !trimmedName.isEmpty {
            resolvedName = trimmedName
        } else if let found = lookupResult {
            resolvedName = found.displayName.isEmpty ? found.friendCode : found.displayName
        } else {
            return
        }

        if !code.isEmpty, isOwnCode(code) {
            lookupError = "Das ist dein eigener Code. Du kannst dich nicht selbst hinzufügen."
            return
        }
        if !code.isEmpty, isDuplicate(code) {
            lookupError = "Dieser Code ist bereits in deiner Liste."
            return
        }
        let member = CrewMember(
            name: resolvedName,
            friendCode: code.isEmpty ? nil : code
        )
        context.insert(member)
        try? context.save()

        // Register the follow edge server-side so the friend's profile can
        // show "hat dich auch hinzugefügt" and mutual friends work.
        if let found = lookupResult {
            let friendID = found.id
            Task { try? await supabase.addFriendship(friendID: friendID) }
        }
        dismiss()
    }
}
