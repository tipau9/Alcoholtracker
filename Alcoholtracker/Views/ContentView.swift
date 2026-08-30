import SwiftUI
import SwiftData

struct ContentView: View {

    @Query private var profiles: [UserProfile]

    private var profile: UserProfile? { profiles.first }

    var body: some View {
        Group {
            if profile?.hasCompletedOnboarding == true {
                MainTabView()
                    .withAccessibility(profile: profile)
            } else {
                OnboardingView()
            }
        }
        .background(
            Group {
                if #available(iOS 26, *) { Color.clear }
                else { Color.promille.background }
            }.ignoresSafeArea()
        )
        .onChange(of: profile?.highContrast)    { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.reducedMotion)  { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.largeText)      { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.accentColorHex) { _, _ in AppTheme.shared.sync(from: profile) }
        .onAppear { AppTheme.shared.sync(from: profile) }
    }
}

// MARK: - MainTabView

struct MainTabView: View {
    @SceneStorage("mainTabSelection") private var selectedTab = "home"
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag("home")

            HistoryView()
                .tabItem { Label("Verlauf", systemImage: "calendar") }
                .tag("history")

            CrewView()
                .tabItem { Label("Freunde", systemImage: "person.3.fill") }
                .tag("crew")

            SafetyView()
                .tabItem { Label("Sicher", systemImage: "shield.fill") }
                .tag("safety")

            SettingsView()
                .tabItem { Label("Profil", systemImage: "person.fill") }
                .tag("settings")

            if supabase.isAdmin {
                AdminView()
                    .tabItem { Label("Admin", systemImage: "lock.shield.fill") }
                    .tag("admin")
            }
        }
        .tint(Color.appAccent)
        .preferredColorScheme(.dark)
        .task {
            try? await supabase.refreshAdminStatus()
        }
        .onChange(of: supabase.isSignedIn) { _, _ in
            Task { try? await supabase.refreshAdminStatus() }
        }
    }
}

// MARK: - Accessibility Modifier
//
// Applied at ContentView level so every screen inherits:
//   - Dynamic Type scaling (largeText)
//   - Increased visual contrast (highContrast)
//   - Animation suppression (reducedMotion)

private struct AccessibilityModifier: ViewModifier {
    let profile: UserProfile?

    private var largeText:     Bool { profile?.largeText     ?? false }
    private var highContrast:  Bool { profile?.highContrast  ?? false }
    private var reducedMotion: Bool { profile?.reducedMotion ?? false }

    func body(content: Content) -> some View {
        content
            .largeTextIfNeeded(largeText)
            .contrast(highContrast ? 1.6 : 1.0)
            .transaction { tx in
                if reducedMotion { tx.disablesAnimations = true }
            }
            .background(highContrast ? Color.black : Color.promille.background)
    }
}

extension View {
    func withAccessibility(profile: UserProfile?) -> some View {
        modifier(AccessibilityModifier(profile: profile))
    }
}
