import Foundation

// MARK: - AchievementCatalog
//
// Original design avoided quantity-based achievements. The expanded catalog
// (v2) adds BAC-level, streak, and milestone achievements per user request.
// BAC estimates use the real user profile via BACCalculator.peakBAC; the
// 70 kg / 0.68 approximation is only a fallback when no profile exists yet
// (0.68 is a gender-neutral blood-r, consistent with UserProfile.distributionFactor).
// Day grouping uses the logical day (06:00 to 05:59, matches SessionViewModel).

enum AchievementCatalog {

    // MARK: UserDefaults key for Jam achievement tracking
    static var totalJamsCreated: Int {
        get { UserDefaults.standard.integer(forKey: "com.tipau.jams.created") }
        set { UserDefaults.standard.set(newValue, forKey: "com.tipau.jams.created") }
    }

    // MARK: Achievement definitions

    static let all: [Achievement] = [

        // --- Erste Schritte ---
        Achievement(id: "first_beer",     title: "Prost!",            subtitle: "Erstes Bier eingetragen",                       icon: "mug.fill",                accent: .amber),
        Achievement(id: "first_wine",     title: "Weingut",           subtitle: "Erstes Glas Wein eingetragen",                  icon: "wineglass.fill",          accent: .orange),
        Achievement(id: "first_sparkling",title: "Bubbles",           subtitle: "Sekt oder Schaumwein eingetragen",              icon: "wineglass",               accent: .yellow),
        Achievement(id: "first_cocktail", title: "Barhocker",         subtitle: "Ersten Cocktail eingetragen",                   icon: "wineglass",               accent: .green),
        Achievement(id: "first_shot",     title: "Schnapsglas",       subtitle: "Ersten Shot eingetragen",                       icon: "drop.fill",               accent: .yellow),
        Achievement(id: "first_cider",    title: "Ciderfan",          subtitle: "Erstes Glas Cider eingetragen",                 icon: "mug.fill",                accent: .green),
        Achievement(id: "first_fortified",title: "Kellermeister",     subtitle: "Ersten Likoerwein eingetragen",                 icon: "wineglass.fill",          accent: .amber),

        // --- Vielfalt ---
        Achievement(id: "categories_3",   title: "Entdecker",         subtitle: "3 verschiedene Kategorien probiert",            icon: "star.fill",               accent: .green),
        Achievement(id: "categories_5",   title: "Vielfaltstrinker",  subtitle: "5 verschiedene Kategorien probiert",            icon: "star.circle.fill",        accent: .amber),
        Achievement(id: "categories_all", title: "Komplettist",       subtitle: "Alle Kategorien mindestens einmal probiert",    icon: "trophy.fill",             accent: .amber),
        Achievement(id: "abv_spectrum",   title: "Breites Spektrum",  subtitle: "Unter 5%, 5-20% und ueber 20% ABV probiert",   icon: "chart.bar.fill",          accent: .orange),
        Achievement(id: "session_variety",title: "Abwechslungsreich", subtitle: "An einem Abend 3 Kategorien kombiniert",        icon: "shuffle",                 accent: .green),

        // --- Eigene Kreationen ---
        Achievement(id: "first_mix",      title: "Mixer",             subtitle: "Ersten eigenen Mix erstellt",                   icon: "arrow.2.squarepath",      accent: .yellow),
        Achievement(id: "first_custom",   title: "Eigenes Rezept",    subtitle: "Ersten eigenen Drink erstellt",                 icon: "pencil",                  accent: .green),

        // --- Social ---
        Achievement(id: "first_crew",     title: "Kein Soloabend",    subtitle: "Erste Person zur Crew hinzugefuegt",            icon: "person.2.fill",           accent: .orange),
        Achievement(id: "first_photo",    title: "Erinnerungsfoto",   subtitle: "Erstes Foto im Abend gemacht",                  icon: "camera.fill",             accent: .green),

        // --- Konsum-Meilensteine ---
        Achievement(id: "drinks_10",      title: "Einsteiger",        subtitle: "10 Drinks insgesamt eingetragen",               icon: "10.circle.fill",          accent: .green),
        Achievement(id: "drinks_50",      title: "Stammgast",         subtitle: "50 Drinks insgesamt eingetragen",               icon: "50.circle.fill",          accent: .amber),
        Achievement(id: "drinks_100",     title: "Jahrhundert-Trinker",subtitle: "100 Drinks insgesamt eingetragen",             icon: "100.circle.fill",         accent: .orange),
        Achievement(id: "drinks_500",     title: "Legendaer",         subtitle: "500 Drinks insgesamt eingetragen",              icon: "star.circle.fill",        accent: .yellow),

        // --- Bier-Spezialisten ---
        Achievement(id: "beers_5_different",  title: "Bierkarte",     subtitle: "5 verschiedene Biere probiert",                 icon: "mug.fill",                accent: .amber),
        Achievement(id: "beers_10_different", title: "Biersommelier", subtitle: "10 verschiedene Biere probiert",                icon: "mug.fill",                accent: .orange),
        Achievement(id: "first_pilsner",      title: "Pilsfreund",    subtitle: "Erstes Pils eingetragen",                       icon: "cylinder.fill",           accent: .green),
        Achievement(id: "first_weissbier",    title: "Weizenglas",    subtitle: "Erstes Weizenbier eingetragen",                 icon: "wineglass.fill",          accent: .yellow),
        Achievement(id: "first_mass",         title: "Auf die Maß!",  subtitle: "Erste Maß Bier (500 ml+) eingetragen",          icon: "mug.fill",                accent: .amber),
        Achievement(id: "first_altbier",      title: "Alt-Meister",   subtitle: "Erstes Altbier eingetragen",                    icon: "cup.and.saucer.fill",     accent: .orange),
        Achievement(id: "local_specialties",  title: "Nord-Sued",     subtitle: "Kölsch und Altbier an einem Tag probiert",      icon: "map.fill",                accent: .green),

        // --- BAC-Stufen ---
        Achievement(id: "bac_05",         title: "Haelfte erreicht",  subtitle: "0,5 Promille in einer Sitzung erreicht",        icon: "gauge.with.dots.needle.50percent", accent: .yellow),
        Achievement(id: "bac_10",         title: "Volle Pulle",       subtitle: "1,0 Promille in einer Sitzung erreicht",        icon: "gauge.with.dots.needle.67percent", accent: .orange),
        Achievement(id: "bac_15",         title: "Felipe",            subtitle: "1,5 Promille in einer Sitzung erreicht",        icon: "exclamationmark.triangle.fill",    accent: .orange),

        // --- Nüchternheits-Streaks ---
        Achievement(id: "sober_3",        title: "Drei Tage durch",   subtitle: "3 Tage am Stueck ohne Alkohol",                 icon: "leaf.fill",               accent: .green),
        Achievement(id: "sober_7",        title: "Gute Woche",        subtitle: "7 Tage am Stueck ohne Alkohol",                 icon: "checkmark.seal.fill",     accent: .green),
        Achievement(id: "sober_14",       title: "Zwei Wochen",       subtitle: "14 Tage am Stueck ohne Alkohol",                icon: "flame.fill",              accent: .amber),
        Achievement(id: "sober_30",       title: "Monats-Champion",   subtitle: "30 Tage am Stueck ohne Alkohol",                icon: "trophy.fill",             accent: .green),

        // --- Cocktail-Connaisseur ---
        Achievement(id: "cocktails_5",    title: "Cocktailkarte",     subtitle: "5 verschiedene Cocktails eingetragen",          icon: "wineglass",               accent: .green),
        Achievement(id: "cocktails_10",   title: "Bar-Kenner",        subtitle: "10 verschiedene Cocktails eingetragen",         icon: "wineglass.fill",          accent: .amber),

        // --- Spirituosen ---
        Achievement(id: "spirits_5",      title: "Spirituosen-Fan",   subtitle: "5 verschiedene Spirituosen eingetragen",        icon: "drop.fill",               accent: .amber),
        Achievement(id: "first_whisky",   title: "Single Malt",       subtitle: "Erstes Whisky oder Bourbon eingetragen",        icon: "drop.fill",               accent: .orange),
        Achievement(id: "wine_both",      title: "Rotwein trifft Weiss", subtitle: "Roten und weissen Wein eingetragen",         icon: "wineglass.fill",          accent: .orange),

        // --- Zeit-basiert ---
        Achievement(id: "night_owl",      title: "Nachteule",         subtitle: "Drink zwischen 0 und 4 Uhr eingetragen",        icon: "moon.stars.fill",         accent: .yellow),
        Achievement(id: "early_bird",     title: "Fruehstuecks-Bier", subtitle: "Drink vor 12 Uhr eingetragen",                  icon: "sunrise.fill",            accent: .amber),
        Achievement(id: "silvester",      title: "Gutes Neues",       subtitle: "Drink am 31. Dezember eingetragen",             icon: "fireworks",               accent: .yellow),
        Achievement(id: "monday_drink",   title: "Montags-Freude",    subtitle: "Drink an einem Montag eingetragen",             icon: "calendar.badge.plus",     accent: .green),

        // --- Social erweitert ---
        Achievement(id: "crew_5",         title: "Die Gang ist da",   subtitle: "5 Personen in der Crew",                        icon: "person.3.fill",           accent: .orange),
        Achievement(id: "photo_5",        title: "Abend im Bild",     subtitle: "5 Erinnerungsfotos gemacht",                    icon: "photo.on.rectangle",      accent: .green),
        Achievement(id: "jam_created",    title: "Jam-Session",       subtitle: "Ersten Jam erstellt",                           icon: "waveform",                accent: .amber),

        // --- Spezial ---
        Achievement(id: "all_beer_styles",title: "Bierstil-Experte",  subtitle: "Pils, Weizen und Dunkel alle probiert",         icon: "mug.fill",                accent: .amber),
        Achievement(id: "spirits_variety",title: "Spirit-Ranger",     subtitle: "3 verschiedene Spirituosen-Typen probiert",     icon: "drop.circle.fill",        accent: .orange),
        Achievement(id: "multi_session",  title: "Abend-Regulaer",    subtitle: "An 5 verschiedenen Tagen Alkohol eingetragen",  icon: "calendar.badge.checkmark",accent: .green),
    ]

    // MARK: - isEarned

    // Caches the whole-history derived metrics that would otherwise be recomputed
    // once per BAC/streak achievement within a single evaluation pass: peakDayBAC
    // for bac_05/10/15 and soberStreak for sober_3/7/14/30. The values are lazy,
    // so when those achievements are already unlocked the heavy work never runs.
    // One instance is built per evaluate() pass on the main actor, so the lazy
    // stored properties are accessed single-threaded.
    final class EvalContext {
        private let drinks: [Drink]
        private let profile: UserProfile?

        init(drinks: [Drink], profile: UserProfile?) {
            self.drinks = drinks
            self.profile = profile
        }

        lazy var peakDayBAC: Double = AchievementCatalog.peakDayBAC(drinks: drinks, profile: profile)
        lazy var soberStreak: Int = AchievementCatalog.soberStreak(drinks: drinks)
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    static func isEarned(
        id: String,
        drinks: [Drink],
        templates: [DrinkTemplate],
        crew: [CrewMember],
        photos: [PhotoMemory],
        profile: UserProfile?,
        cache: EvalContext
    ) -> Bool {
        switch id {

        // Erste Schritte
        case "first_beer":      return drinks.contains { $0.category == .beer }
        case "first_wine":      return drinks.contains { $0.category == .wine }
        case "first_sparkling": return drinks.contains { $0.category == .sparkling }
        case "first_cocktail":  return drinks.contains { $0.category == .cocktail }
        case "first_shot":      return drinks.contains { $0.category == .shot }
        case "first_cider":     return drinks.contains { $0.category == .cider }
        case "first_fortified": return drinks.contains { $0.category == .fortified }

        // Vielfalt
        case "categories_3":    return Set(drinks.map(\.categoryRaw)).count >= 3
        case "categories_5":    return Set(drinks.map(\.categoryRaw)).count >= 5
        case "categories_all":  return Set(drinks.map(\.categoryRaw)).count >= DrinkCategory.allCases.count
        case "abv_spectrum":
            return drinks.contains { $0.abv < 5 }
                && drinks.contains { $0.abv >= 5 && $0.abv <= 20 }
                && drinks.contains { $0.abv > 20 }
        case "session_variety":
            let byDay = Dictionary(grouping: drinks) { logicalDay(of: $0.timestamp) }
            return byDay.values.contains { Set($0.map(\.categoryRaw)).count >= 3 }
        case "first_mix":    return drinks.contains { $0.mixerVolume > 0 }
        case "first_custom": return templates.contains { $0.isCustom }
        case "first_crew":   return crew.contains { !$0.isSelf }
        case "first_photo":  return !photos.isEmpty

        // Konsum-Meilensteine
        case "drinks_10":  return drinks.count >= 10
        case "drinks_50":  return drinks.count >= 50
        case "drinks_100": return drinks.count >= 100
        case "drinks_500": return drinks.count >= 500

        // Bier-Spezialisten
        case "beers_5_different":
            return Set(drinks.filter { $0.category == .beer }.map(\.name)).count >= 5
        case "beers_10_different":
            return Set(drinks.filter { $0.category == .beer }.map(\.name)).count >= 10
        case "first_pilsner":
            return drinks.contains {
                $0.category == .beer
                && ($0.name.localizedCaseInsensitiveContains("pils") || $0.name.localizedCaseInsensitiveContains("pilsner"))
            }
        case "first_weissbier":
            return drinks.contains {
                $0.category == .beer
                && ($0.name.localizedCaseInsensitiveContains("weiss")
                    || $0.name.localizedCaseInsensitiveContains("weiß")
                    || $0.name.localizedCaseInsensitiveContains("weizen")
                    || $0.name.localizedCaseInsensitiveContains("hefe"))
            }
        case "first_mass":
            return drinks.contains { $0.category == .beer && $0.volume >= 500 }
        case "first_altbier":
            return drinks.contains {
                $0.category == .beer && $0.name.localizedCaseInsensitiveContains("alt")
            }
        case "local_specialties":
            let hasKolsch = drinks.contains {
                $0.category == .beer
                && ($0.name.localizedCaseInsensitiveContains("kölsch")
                    || $0.name.localizedCaseInsensitiveContains("koelsch")
                    || $0.name.localizedCaseInsensitiveContains("kolsch"))
            }
            let hasAlt = drinks.contains {
                $0.category == .beer && $0.name.localizedCaseInsensitiveContains("alt")
            }
            return hasKolsch && hasAlt

        // BAC-Stufen
        case "bac_05": return cache.peakDayBAC >= 0.5
        case "bac_10": return cache.peakDayBAC >= 1.0
        case "bac_15": return cache.peakDayBAC >= 1.5

        // Nüchternheit-Streaks (current streak from today backwards)
        case "sober_3":  return cache.soberStreak >= 3
        case "sober_7":  return cache.soberStreak >= 7
        case "sober_14": return cache.soberStreak >= 14
        case "sober_30": return cache.soberStreak >= 30

        // Cocktails
        case "cocktails_5":
            return Set(drinks.filter { $0.category == .cocktail }.map(\.name)).count >= 5
        case "cocktails_10":
            return Set(drinks.filter { $0.category == .cocktail }.map(\.name)).count >= 10

        // Spirituosen
        case "spirits_5":
            return Set(drinks.filter { $0.category == .spirits }.map(\.name)).count >= 5
        case "first_whisky":
            return drinks.contains {
                $0.category == .spirits
                && ($0.name.localizedCaseInsensitiveContains("whisky")
                    || $0.name.localizedCaseInsensitiveContains("whiskey")
                    || $0.name.localizedCaseInsensitiveContains("bourbon")
                    || $0.name.localizedCaseInsensitiveContains("scotch"))
            }
        case "wine_both":
            let hasRed = drinks.contains {
                $0.category == .wine
                && ($0.name.localizedCaseInsensitiveContains("rot")
                    || $0.name.localizedCaseInsensitiveContains("spätburgunder")
                    || $0.name.localizedCaseInsensitiveContains("cabernet")
                    || $0.name.localizedCaseInsensitiveContains("merlot"))
            }
            let hasWhite = drinks.contains {
                $0.category == .wine
                && ($0.name.localizedCaseInsensitiveContains("weiß")
                    || $0.name.localizedCaseInsensitiveContains("weiss")
                    || $0.name.localizedCaseInsensitiveContains("riesling")
                    || $0.name.localizedCaseInsensitiveContains("sauvignon")
                    || $0.name.localizedCaseInsensitiveContains("chardonnay"))
            }
            return hasRed && hasWhite

        // Zeit-basiert
        case "night_owl":
            return drinks.contains {
                let hour = Calendar.current.component(.hour, from: $0.timestamp)
                return hour >= 0 && hour < 4
            }
        case "early_bird":
            return drinks.contains {
                let hour = Calendar.current.component(.hour, from: $0.timestamp)
                return hour >= 6 && hour < 12
            }
        case "silvester":
            return drinks.contains {
                let comps = Calendar.current.dateComponents([.month, .day], from: $0.timestamp)
                return comps.month == 12 && comps.day == 31
            }
        case "monday_drink":
            return drinks.contains {
                Calendar.current.component(.weekday, from: $0.timestamp) == 2
            }

        // Social
        case "crew_5":    return crew.filter { !$0.isSelf }.count >= 5
        case "photo_5":   return photos.count >= 5
        case "jam_created": return totalJamsCreated >= 1

        // Spezial
        case "all_beer_styles":
            let beers = drinks.filter { $0.category == .beer }
            let hasPils  = beers.contains { $0.name.localizedCaseInsensitiveContains("pils") }
            let hasWeizen = beers.contains {
                $0.name.localizedCaseInsensitiveContains("weiss")
                || $0.name.localizedCaseInsensitiveContains("weiß")
                || $0.name.localizedCaseInsensitiveContains("weizen")
            }
            let hasDunkel = beers.contains {
                $0.name.localizedCaseInsensitiveContains("dunkel")
                || $0.name.localizedCaseInsensitiveContains("schwarz")
                || $0.name.localizedCaseInsensitiveContains("stout")
                || $0.name.localizedCaseInsensitiveContains("doppelbock")
                || $0.name.localizedCaseInsensitiveContains("bock")
            }
            return hasPils && hasWeizen && hasDunkel
        case "spirits_variety":
            let spirits = drinks.filter { $0.category == .spirits }
            var types = Set<String>()
            for d in spirits {
                let n = d.name.lowercased()
                if n.contains("vodka") || n.contains("wodka")  { types.insert("vodka") }
                if n.contains("gin")                            { types.insert("gin") }
                if n.contains("rum")                            { types.insert("rum") }
                if n.contains("whisky") || n.contains("whiskey"){ types.insert("whisky") }
                if n.contains("tequila")                        { types.insert("tequila") }
                if n.contains("cognac") || n.contains("brandy") { types.insert("cognac") }
            }
            return types.count >= 3
        case "multi_session":
            return Set(drinks.filter { $0.abv > 0 }.map { logicalDay(of: $0.timestamp) }).count >= 5

        default: return false
        }
    }

    // MARK: - Helpers

    // Logical day key for a timestamp: drinks before 06:00 belong to the
    // previous calendar day, so one evening never counts as two days.
    private static func logicalDay(of timestamp: Date) -> Date {
        Calendar.current.logicalDayStart(for: timestamp)
    }

    // Highest single-day peak BAC across all history, using the real user
    // profile and absorption/elimination curve. Falls back to a simple
    // Widmark accumulation with average body stats when no profile exists.
    private static func peakDayBAC(drinks: [Drink], profile: UserProfile?) -> Double {
        let grouped = Dictionary(grouping: drinks.filter { $0.abv > 0 }) {
            logicalDay(of: $0.timestamp)
        }

        if let p = profile {
            return grouped.values.map { dayDrinks in
                BACCalculator.peakBAC(
                    drinks: dayDrinks,
                    profile: p,
                    stomachStatus: p.defaultStomachStatus
                )
            }.max() ?? 0.0
        }

        return grouped.values.map { dayDrinks in
            let sorted = dayDrinks.sorted { $0.timestamp < $1.timestamp }
            guard let first = sorted.first else { return 0.0 }

            var currentBAC = 0.0
            var lastTime = first.timestamp
            var peak = 0.0

            for d in sorted {
                let hoursPassed = d.timestamp.timeIntervalSince(lastTime) / 3600.0
                currentBAC = max(0, currentBAC - (hoursPassed * 0.15))

                currentBAC += BACCalculator.bacContribution(
                    volume: d.volume, abv: d.abv,
                    weight: 70, distributionFactor: 0.68
                )
                peak = max(peak, currentBAC)
                lastTime = d.timestamp
            }
            return peak
        }.max() ?? 0.0
    }

    // First time the app computed achievements on this device. Used to bound the
    // sober streak so a brand-new user is never credited for "sober" days from
    // before the app existed.
    private static let installDateKey = "achievements.installDate"
    private static func installDate() -> Date {
        let ud = UserDefaults.standard
        if let d = ud.object(forKey: installDateKey) as? Date { return d }
        let now = Date()
        ud.set(now, forKey: installDateKey)
        return now
    }

    // Current sober streak: consecutive logical days (including today) without alcohol.
    private static func soberStreak(drinks: [Drink]) -> Int {
        let alcoholDrinks = drinks.filter { $0.abv > 0 }
        // No alcohol history at all — user has never tracked a drink, so there is
        // no tracked sober streak yet. (A streak must follow a recorded drink.)
        guard !alcoholDrinks.isEmpty else { return 0 }

        let cal = Calendar.current
        let drinkDays = Set(alcoholDrinks.map { logicalDay(of: $0.timestamp) })
        var streak = 0
        var day = logicalDay(of: Date())
        // Cap at 365 to avoid calendar arithmetic across millennia.
        while !drinkDays.contains(day), streak < 365 {
            streak += 1
            guard let prev = cal.date(byAdding: .day, value: -1, to: day) else { break }
            day = prev
        }

        // Bound by how long the app has actually known this user: a fresh user
        // can never be credited a sober streak longer than the app has tracked
        // them, which is what stops sober_3 / sober_7 from unlocking right after
        // first launch. "Known since" is the earlier of the install date and the
        // oldest tracked drink, so existing users with real history keep their
        // streak (the install date is only reset to now on a fresh install).
        let oldestDrinkDay = drinkDays.min() ?? logicalDay(of: Date())
        let knownSince = min(logicalDay(of: installDate()), oldestDrinkDay)
        let daysKnown = cal.dateComponents(
            [.day], from: knownSince, to: logicalDay(of: Date())
        ).day ?? 0
        return min(streak, max(0, daysKnown))
    }
}
