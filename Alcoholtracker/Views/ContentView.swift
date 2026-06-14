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
        .background(Color.promille.background.ignoresSafeArea())
        .onChange(of: profile?.highContrast)    { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.reducedMotion)  { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.largeText)      { _, _ in AppTheme.shared.sync(from: profile) }
        .onChange(of: profile?.accentColorHex) { _, _ in AppTheme.shared.sync(from: profile) }
        .onAppear { AppTheme.shared.sync(from: profile) }
    }
}

// MARK: - MainTabView

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }

            HistoryView()
                .tabItem { Label("Verlauf", systemImage: "calendar") }

            CrewView()
                .tabItem { Label("Freunde", systemImage: "person.3.fill") }

            SafetyView()
                .tabItem { Label("Sicher", systemImage: "shield.fill") }

            SettingsView()
                .tabItem { Label("Profil", systemImage: "person.fill") }
        }
        .tint(Color.appAccent)
        .preferredColorScheme(.dark)
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
