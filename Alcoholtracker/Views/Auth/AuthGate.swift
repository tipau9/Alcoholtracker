import SwiftUI

// MARK: - AuthGate
//
// Sign-in / sign-up sheet presented from CrewView and SettingsView.
// Reads SupabaseService from the environment; no Supabase SDK required.

struct AuthGate: View {

    @Environment(SupabaseService.self) private var supabase
    @Environment(HistorySyncService.self) private var historySync
    @Environment(\.dismiss) private var dismiss

    @State private var mode: AGMode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var confirmationSent = false

    private enum AGMode { case signIn, signUp }

    private func isValidEmail(_ address: String) -> Bool {
        let parts = address.components(separatedBy: "@")
        guard parts.count == 2 else { return false }
        let local  = parts[0]
        let domain = parts[1]
        guard !local.isEmpty, domain.contains(".") else { return false }
        guard !domain.hasPrefix("."), !domain.hasSuffix(".") else { return false }
        let domainParts = domain.components(separatedBy: ".")
        return domainParts.allSatisfy { !$0.isEmpty }
    }

    private var isValid: Bool {
        let emailOK = isValidEmail(email.trimmingCharacters(in: .whitespaces))
        let passOK  = password.count >= 6
        let nameOK  = mode == .signIn || !displayName.trimmingCharacters(in: .whitespaces).isEmpty
        return emailOK && passOK && nameOK && !isLoading
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {

                // Drag handle
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                // Header
                HStack(spacing: 14) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))
                    Text(mode == .signIn ? "Anmelden" : "Registrieren")
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

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 20) {

                        // Not configured warning
                        if !supabase.isConfigured {
                            HStack(spacing: 10) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(Color.statusOrange)
                                Text("Supabase nicht konfiguriert. Bitte SupabaseConfig.swift ausfuellen.")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.statusOrange)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color.statusOrange.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.statusOrange.opacity(0.3), lineWidth: 0.5)
                            )
                        }

                        // Mode switcher
                        HStack(spacing: 0) {
                            AGModeButton(title: "Anmelden",      selected: mode == .signIn)  { withAnimation(.spring(response: 0.2)) { mode = .signIn } }
                            AGModeButton(title: "Registrieren",  selected: mode == .signUp)  { withAnimation(.spring(response: 0.2)) { mode = .signUp } }
                        }
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )

                        // Form
                        VStack(spacing: 0) {
                            if mode == .signUp {
                                AGField(label: "Anzeigename", placeholder: "z.B. Max M.") {
                                    TextField("z.B. Max M.", text: $displayName)
                                        .textContentType(.name)
                                        .textInputAutocapitalization(.words)
                                        .autocorrectionDisabled()
                                }
                                Divider().background(Color.appBorder).padding(.leading, 16)
                            }
                            AGField(label: "E-Mail", placeholder: "name@beispiel.de") {
                                TextField("name@beispiel.de", text: $email)
                                    .keyboardType(.emailAddress)
                                    .textContentType(.emailAddress)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                            }
                            Divider().background(Color.appBorder).padding(.leading, 16)
                            AGField(label: "Passwort", placeholder: mode == .signUp ? "Min. 6 Zeichen" : "") {
                                SecureField(mode == .signUp ? "Min. 6 Zeichen" : "", text: $password)
                                    .textContentType(mode == .signIn ? .password : .newPassword)
                            }
                        }
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )
                        .animation(.easeInOut(duration: 0.18), value: mode)

                        // Error banner
                        if let msg = errorMessage {
                            HStack(spacing: 10) {
                                Image(systemName: "exclamationmark.circle.fill")
                                    .foregroundStyle(Color.statusRed)
                                Text(msg)
                                    .font(.appCaption)
                                    .foregroundStyle(Color.statusRed)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color.statusRed.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.statusRed.opacity(0.25), lineWidth: 0.5)
                            )
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        // Email-Bestätigung ausstehend
                        if confirmationSent {
                            HStack(spacing: 10) {
                                Image(systemName: "envelope.badge.fill")
                                    .foregroundStyle(Color.statusGreen)
                                Text("Bestätigungsmail gesendet. Bitte E-Mail öffnen, bestätigen und dann hier anmelden.")
                                    .font(.appCaption)
                                    .foregroundStyle(Color.statusGreen)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(Color.statusGreen.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.statusGreen.opacity(0.3), lineWidth: 0.5)
                            )
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }

                        // Submit button
                        Button {
                            Task { await submit() }
                        } label: {
                            HStack(spacing: 8) {
                                if isLoading {
                                    ProgressView()
                                        .tint(Color.appBackground)
                                        .scaleEffect(0.85)
                                } else {
                                    Image(systemName: mode == .signIn ? "arrow.right.circle.fill" : "person.badge.plus")
                                        .font(.system(size: 15, weight: .semibold))
                                }
                                Text(mode == .signIn ? "Anmelden" : "Konto erstellen")
                                    .font(.appBodyBold)
                            }
                            .foregroundStyle(isValid ? Color.appBackground : Color.appTextMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 15)
                            .background(isValid ? Color.appAccent : Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .strokeBorder(isValid ? Color.clear : Color.appBorder, lineWidth: 0.5)
                            )
                            .animation(.easeInOut(duration: 0.15), value: isValid)
                        }
                        .buttonStyle(.plain)
                        .disabled(!isValid || !supabase.isConfigured)

                        // Privacy note
                        Text("Dein BAC wird mit Freunden geteilt, die deinen Code kennen. E-Mail und Passwort werden verschluesselt bei Supabase gespeichert.")
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextMuted)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 40)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .onChange(of: mode) { _, _ in
            errorMessage = nil
            confirmationSent = false
        }
    }

    // MARK: Submit

    private func submit() async {
        isLoading = true
        errorMessage = nil
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces).lowercased()
        do {
            if mode == .signIn {
                try await supabase.signIn(email: trimmedEmail, password: password)
            } else {
                try await supabase.signUp(
                    email: trimmedEmail,
                    password: password,
                    displayName: displayName.trimmingCharacters(in: .whitespaces)
                )
            }
            // First sync after sign-in: union the account backup with whatever
            // history this device already has (never deletes either side).
            Task { await historySync.sync(merge: true) }
            dismiss()
        } catch SupabaseError.emailConfirmationRequired {
            withAnimation {
                confirmationSent = true
                mode = .signIn
                password = ""
            }
        } catch {
            withAnimation { errorMessage = error.localizedDescription }
        }
        isLoading = false
    }
}

// MARK: - Sub-views

private struct AGModeButton: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.appCaptionBold)
                .foregroundStyle(selected ? Color.appAccent : Color.appTextDim)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(selected ? Color.appAccent.opacity(0.12) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .padding(2)
    }
}

private struct AGField<F: View>: View {
    let label: String
    let placeholder: String
    @ViewBuilder let field: () -> F

    var body: some View {
        HStack {
            Text(label)
                .font(.appBody)
                .foregroundStyle(Color.appText)
                .frame(width: 108, alignment: .leading)
            field()
                .font(.appBody)
                .foregroundStyle(Color.appAccent)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}
