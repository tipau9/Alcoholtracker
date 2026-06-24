import Foundation

@MainActor
@Observable
final class AchievementService {

    private(set) var unlockedIDs: Set<String> = []
    private(set) var newlyUnlocked: [Achievement] = []

    private let defaultsKey = "com.tipau.achievements.v1"

    // Set by the app at launch; unlocked ids are mirrored to the profile so
    // friends can see them on the profile sheet. nil keeps everything local.
    var supabase: SupabaseService?
    private var hasPublishedThisSession = false

    var unlockedCount: Int { unlockedIDs.count }
    var totalCount: Int { AchievementCatalog.all.count }

    init() {
        let saved = UserDefaults.standard.stringArray(forKey: defaultsKey) ?? []
        unlockedIDs = Set(saved)
    }

    func evaluate(
        drinks: [Drink],
        templates: [DrinkTemplate],
        crew: [CrewMember],
        photos: [PhotoMemory],
        profile: UserProfile?
    ) async {
        // Nothing left to earn: skip the whole-history scan entirely.
        guard unlockedIDs.count < AchievementCatalog.all.count else {
            publishIfNeeded(didUnlock: false)
            return
        }

        // Runs on the main actor on purpose: the scan reads SwiftData models,
        // which are bound to the main-actor model context. Shipping them into
        // a detached task was a data race wearing a performance costume.
        // The cache derives peakDayBAC / soberStreak at most once per pass.
        let currentIDs = unlockedIDs
        let cache = AchievementCatalog.EvalContext(drinks: drinks, profile: profile)
        let freshUnlocks: [Achievement] = AchievementCatalog.all.filter { a in
            !currentIDs.contains(a.id) &&
            AchievementCatalog.isEarned(
                id: a.id,
                drinks: drinks,
                templates: templates,
                crew: crew,
                photos: photos,
                profile: profile,
                cache: cache
            )
        }

        if !freshUnlocks.isEmpty {
            freshUnlocks.forEach { unlockedIDs.insert($0.id) }
            newlyUnlocked.append(contentsOf: freshUnlocks)
            UserDefaults.standard.set(Array(unlockedIDs), forKey: defaultsKey)
        }

        publishIfNeeded(didUnlock: !freshUnlocks.isEmpty)
    }

    // Mirrors the unlocked set to the Supabase profile: on every new unlock,
    // plus once per session so a sign-in after launch still syncs.
    private func publishIfNeeded(didUnlock: Bool) {
        guard let supabase, supabase.isSignedIn else { return }
        guard didUnlock || !hasPublishedThisSession else { return }
        hasPublishedThisSession = true
        let ids = Array(unlockedIDs)
        Task { try? await supabase.publishAchievements(ids) }
    }

    func acknowledgeUnlocks() {
        newlyUnlocked = []
    }

    func isUnlocked(_ id: String) -> Bool {
        unlockedIDs.contains(id)
    }

    func delete(id: String) {
        guard unlockedIDs.contains(id) else { return }
        unlockedIDs.remove(id)
        UserDefaults.standard.set(Array(unlockedIDs), forKey: defaultsKey)
    }
}
