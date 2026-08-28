package de.tipau.promille.bac

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

/** Accent colour of a badge. The theme maps these to the shared colour tokens. */
enum class AchievementAccent { AMBER, GREEN, YELLOW, ORANGE }

data class Achievement(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val accent: AchievementAccent
)

/**
 * Badge definitions and the earning rules. Mirrors
 * Services/AchievementCatalog.swift; the ids are mirrored to profiles.achievements
 * in Supabase, so they are a wire contract and must never be renamed.
 *
 * iOS reads the clock, UserDefaults and the SwiftData lists inline. Here every one
 * of those is a parameter, which is also what makes the rules testable.
 */
object AchievementCatalog {

    /**
     * Everything a full evaluation pass needs. The two derived metrics are lazy,
     * so an already unlocked badge never pays for the heavy history walk.
     */
    class EvalContext(
        private val drinks: List<Drink>,
        private val profile: Profile?,
        val nowEpochSeconds: Long,
        /**
         * First time this device evaluated achievements. Bounds the sober streak so
         * a fresh install is never credited for days from before the app existed.
         */
        private val installDateEpochSeconds: Long,
        val zone: ZoneId = ZoneId.systemDefault(),
        customPeakDayBAC: Double? = null,
        customSoberStreak: Int? = null
    ) {
        val peakDayBAC: Double by lazy {
            customPeakDayBAC ?: peakDayBAC(drinks, profile, zone)
        }
        val soberStreak: Int by lazy {
            customSoberStreak ?: soberStreak(drinks, nowEpochSeconds, installDateEpochSeconds, zone)
        }
    }

    fun isEarned(
        id: String,
        drinks: List<Drink>,
        hasCustomTemplate: Boolean,
        /** Crew members that are not the user themselves. */
        crewCount: Int,
        photoCount: Int,
        jamsCreated: Int,
        cache: EvalContext
    ): Boolean {
        val zone = cache.zone
        fun named(category: DrinkCategory, vararg needles: String) = drinks.any { d ->
            d.category == category && needles.any { d.name.contains(it, ignoreCase = true) }
        }
        fun distinctNames(category: DrinkCategory) =
            drinks.filter { it.category == category }.map { it.name }.toSet().size

        return when (id) {

            // Erste Schritte
            "first_beer" -> drinks.any { it.category == DrinkCategory.BEER }
            "first_wine" -> drinks.any { it.category == DrinkCategory.WINE }
            "first_sparkling" -> drinks.any { it.category == DrinkCategory.SPARKLING }
            "first_cocktail" -> drinks.any { it.category == DrinkCategory.COCKTAIL }
            "first_shot" -> drinks.any { it.category == DrinkCategory.SHOT }
            "first_cider" -> drinks.any { it.category == DrinkCategory.CIDER }
            "first_fortified" -> drinks.any { it.category == DrinkCategory.FORTIFIED }

            // Vielfalt
            "categories_3" -> drinks.map { it.category }.toSet().size >= 3
            "categories_5" -> drinks.map { it.category }.toSet().size >= 5
            "categories_all" -> ALCOHOLIC_CATEGORIES.all { cat ->
                drinks.any { it.category == cat }
            }
            "abv_spectrum" ->
                drinks.any { it.abv < 5 } &&
                    drinks.any { it.abv >= 5 && it.abv <= 20 } &&
                    drinks.any { it.abv > 20 }
            "session_variety" -> drinks
                .groupBy { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }
                .values.any { day -> day.map { it.category }.toSet().size >= 3 }
            "first_mix" -> drinks.any { it.mixerVolumeML > 0 }
            "first_custom" -> hasCustomTemplate
            "first_crew" -> crewCount >= 1
            "first_photo" -> photoCount > 0

            // Konsum-Meilensteine
            "drinks_10" -> drinks.size >= 10
            "drinks_50" -> drinks.size >= 50
            "drinks_100" -> drinks.size >= 100
            "drinks_500" -> drinks.size >= 500

            // Bier-Spezialisten
            "beers_5_different" -> distinctNames(DrinkCategory.BEER) >= 5
            "beers_10_different" -> distinctNames(DrinkCategory.BEER) >= 10
            "first_pilsner" -> named(DrinkCategory.BEER, "pils", "pilsner")
            // "weiss" and "weiß" are both listed because the iOS matcher is
            // diacritic sensitive; keep both spellings on this side too.
            "first_weissbier" -> named(DrinkCategory.BEER, "weiss", "weiß", "weizen", "hefe")
            "first_mass" -> drinks.any { it.category == DrinkCategory.BEER && it.volumeML >= 500 }
            "first_altbier" -> named(DrinkCategory.BEER, "alt")
            "local_specialties" ->
                named(DrinkCategory.BEER, "kölsch", "koelsch", "kolsch") &&
                    named(DrinkCategory.BEER, "alt")

            // BAC-Stufen
            "bac_05" -> cache.peakDayBAC >= 0.5
            "bac_10" -> cache.peakDayBAC >= 1.0
            "bac_15" -> cache.peakDayBAC >= 1.5

            // Nüchternheit-Streaks, counted from today backwards
            "sober_3" -> cache.soberStreak >= 3
            "sober_7" -> cache.soberStreak >= 7
            "sober_14" -> cache.soberStreak >= 14
            "sober_30" -> cache.soberStreak >= 30

            // Cocktails
            "cocktails_5" -> distinctNames(DrinkCategory.COCKTAIL) >= 5
            "cocktails_10" -> distinctNames(DrinkCategory.COCKTAIL) >= 10

            // Spirituosen
            "spirits_5" -> distinctNames(DrinkCategory.SPIRITS) >= 5
            "first_whisky" ->
                named(DrinkCategory.SPIRITS, "whisky", "whiskey", "bourbon", "scotch")
            "wine_both" ->
                named(DrinkCategory.WINE, "rot", "spätburgunder", "cabernet", "merlot") &&
                    named(
                        DrinkCategory.WINE,
                        "weiß", "weiss", "riesling", "sauvignon", "chardonnay"
                    )

            // Zeit-basiert
            "night_owl" -> drinks.any { hourOf(it, zone) < 4 }
            "early_bird" -> drinks.any { hourOf(it, zone) in 6..11 }
            // Calendar date on purpose, not the logical day: a drink at 01:00 on
            // January 1 is no longer Silvester.
            "silvester" -> drinks.any {
                val d = Instant.ofEpochSecond(it.timestampEpochSeconds).atZone(zone)
                d.monthValue == 12 && d.dayOfMonth == 31
            }
            "monday_drink" -> drinks.any {
                Instant.ofEpochSecond(it.timestampEpochSeconds)
                    .atZone(zone).dayOfWeek == DayOfWeek.MONDAY
            }

            // Social
            "crew_5" -> crewCount >= 5
            "photo_5" -> photoCount >= 5
            "jam_created" -> jamsCreated >= 1

            // Spezial
            "all_beer_styles" -> {
                val beers = drinks.filter { it.category == DrinkCategory.BEER }
                fun anyBeer(vararg needles: String) =
                    beers.any { b -> needles.any { b.name.contains(it, ignoreCase = true) } }
                anyBeer("pils") && anyBeer("weiss", "weiß", "weizen") &&
                    anyBeer("dunkel", "schwarz", "stout", "doppelbock", "bock")
            }
            "spirits_variety" -> {
                val types = mutableSetOf<String>()
                for (d in drinks.filter { it.category == DrinkCategory.SPIRITS }) {
                    val n = d.name.lowercase()
                    if (n.contains("vodka") || n.contains("wodka")) types += "vodka"
                    if (n.contains("gin")) types += "gin"
                    if (n.contains("rum")) types += "rum"
                    if (n.contains("whisky") || n.contains("whiskey")) types += "whisky"
                    if (n.contains("tequila")) types += "tequila"
                    if (n.contains("cognac") || n.contains("brandy")) types += "cognac"
                }
                types.size >= 3
            }
            "multi_session" -> drinks.filter { it.abv > 0 }
                .map { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }
                .toSet().size >= 5

            else -> false
        }
    }

    private val ALCOHOLIC_CATEGORIES = listOf(
        DrinkCategory.BEER, DrinkCategory.WINE, DrinkCategory.SPARKLING,
        DrinkCategory.SPIRITS, DrinkCategory.LIQUEUR, DrinkCategory.COCKTAIL,
        DrinkCategory.MIXED, DrinkCategory.SHOT, DrinkCategory.CIDER,
        DrinkCategory.FORTIFIED, DrinkCategory.OTHER
    )

    private fun hourOf(drink: Drink, zone: ZoneId): Int =
        Instant.ofEpochSecond(drink.timestampEpochSeconds).atZone(zone).hour

    /**
     * Highest single logical day peak across all history. With a profile it uses
     * the real curve; without one it falls back to a plain Widmark accumulation on
     * average body stats so the badges still move before onboarding finishes.
     */
    fun peakDayBAC(drinks: List<Drink>, profile: Profile?, zone: ZoneId): Double {
        val grouped = drinks.filter { it.abv > 0 }
            .groupBy { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }

        if (profile != null) {
            return grouped.values.maxOfOrNull { dayDrinks ->
                BacProjectionInput(
                    drinks = dayDrinks,
                    profile = profile,
                    stomachStatus = profile.defaultStomachStatus,
                    conservative = profile.conservativeForApp
                ).peakBac()
            } ?: 0.0
        }

        return grouped.values.maxOfOrNull { dayDrinks ->
            val sorted = dayDrinks.sortedBy { it.timestampEpochSeconds }
            val first = sorted.firstOrNull() ?: return@maxOfOrNull 0.0
            var currentBAC = 0.0
            var lastTime = first.timestampEpochSeconds
            var peak = 0.0
            for (d in sorted) {
                val hoursPassed = (d.timestampEpochSeconds - lastTime) / 3600.0
                currentBAC = max(0.0, currentBAC - hoursPassed * 0.15)
                currentBAC += BacCalculator.bacContribution(
                    volumeML = d.volumeML, abv = d.abv,
                    weightKg = 70.0, distributionFactor = 0.68
                )
                peak = max(peak, currentBAC)
                lastTime = d.timestampEpochSeconds
            }
            peak
        } ?: 0.0
    }

    /** Consecutive logical days up to and including today without any alcohol. */
    fun soberStreak(
        drinks: List<Drink>,
        nowEpochSeconds: Long,
        installDateEpochSeconds: Long,
        zone: ZoneId
    ): Int {
        val alcohol = drinks.filter { it.abv > 0 }
        // A streak has to follow a recorded drink: someone who never tracked
        // anything has not earned one.
        if (alcohol.isEmpty()) return 0

        val drinkDays: Set<LocalDate> =
            alcohol.map { LogicalDay.dateOf(it.timestampEpochSeconds, zone) }.toSet()
        val today = LogicalDay.dateOf(nowEpochSeconds, zone)

        var streak = 0
        var day = today
        // Capped at 365 so a long clean history cannot walk back across millennia.
        while (day !in drinkDays && streak < 365) {
            streak += 1
            day = day.minusDays(1)
        }

        // Bound by how long the app has known this user, which is what stops
        // sober_3 and sober_7 from unlocking straight after a fresh install.
        val oldestDrinkDay = drinkDays.minOrNull() ?: today
        val installDay = LogicalDay.dateOf(installDateEpochSeconds, zone)
        val knownSince = if (installDay < oldestDrinkDay) installDay else oldestDrinkDay
        val daysKnown = ChronoUnit.DAYS.between(knownSince, today).toInt()
        return minOf(streak, max(0, daysKnown))
    }

    val ALL: List<Achievement> = listOf(

        // Erste Schritte
        Achievement("first_beer", "Prost!", "Erstes Bier eingetragen", "mug.fill", AchievementAccent.AMBER),
        Achievement("first_wine", "Weingut", "Erstes Glas Wein eingetragen", "wineglass.fill", AchievementAccent.ORANGE),
        Achievement("first_sparkling", "Bubbles", "Sekt oder Schaumwein eingetragen", "wineglass", AchievementAccent.YELLOW),
        Achievement("first_cocktail", "Barhocker", "Ersten Cocktail eingetragen", "wineglass", AchievementAccent.GREEN),
        Achievement("first_shot", "Schnapsglas", "Ersten Shot eingetragen", "drop.fill", AchievementAccent.YELLOW),
        Achievement("first_cider", "Ciderfan", "Erstes Glas Cider eingetragen", "mug.fill", AchievementAccent.GREEN),
        Achievement("first_fortified", "Kellermeister", "Ersten Likoerwein eingetragen", "wineglass.fill", AchievementAccent.AMBER),

        // Vielfalt
        Achievement("categories_3", "Entdecker", "3 verschiedene Kategorien probiert", "star.fill", AchievementAccent.GREEN),
        Achievement("categories_5", "Vielfaltstrinker", "5 verschiedene Kategorien probiert", "star.circle.fill", AchievementAccent.AMBER),
        Achievement("categories_all", "Komplettist", "Alle Kategorien mindestens einmal probiert", "trophy.fill", AchievementAccent.AMBER),
        Achievement("abv_spectrum", "Breites Spektrum", "Unter 5%, 5-20% und ueber 20% ABV probiert", "chart.bar.fill", AchievementAccent.ORANGE),
        Achievement("session_variety", "Abwechslungsreich", "An einem Abend 3 Kategorien kombiniert", "shuffle", AchievementAccent.GREEN),

        // Eigene Kreationen
        Achievement("first_mix", "Mixer", "Ersten eigenen Mix erstellt", "arrow.2.squarepath", AchievementAccent.YELLOW),
        Achievement("first_custom", "Eigenes Rezept", "Ersten eigenen Drink erstellt", "pencil", AchievementAccent.GREEN),

        // Social
        Achievement("first_crew", "Kein Soloabend", "Erste Person zur Crew hinzugefuegt", "person.2.fill", AchievementAccent.ORANGE),
        Achievement("first_photo", "Erinnerungsfoto", "Erstes Foto im Abend gemacht", "camera.fill", AchievementAccent.GREEN),

        // Konsum-Meilensteine
        Achievement("drinks_10", "Einsteiger", "10 Drinks insgesamt eingetragen", "10.circle.fill", AchievementAccent.GREEN),
        Achievement("drinks_50", "Stammgast", "50 Drinks insgesamt eingetragen", "50.circle.fill", AchievementAccent.AMBER),
        Achievement("drinks_100", "Jahrhundert-Trinker", "100 Drinks insgesamt eingetragen", "100.circle.fill", AchievementAccent.ORANGE),
        Achievement("drinks_500", "Legendaer", "500 Drinks insgesamt eingetragen", "star.circle.fill", AchievementAccent.YELLOW),

        // Bier-Spezialisten
        Achievement("beers_5_different", "Bierkarte", "5 verschiedene Biere probiert", "mug.fill", AchievementAccent.AMBER),
        Achievement("beers_10_different", "Biersommelier", "10 verschiedene Biere probiert", "mug.fill", AchievementAccent.ORANGE),
        Achievement("first_pilsner", "Pilsfreund", "Erstes Pils eingetragen", "cylinder.fill", AchievementAccent.GREEN),
        Achievement("first_weissbier", "Weizenglas", "Erstes Weizenbier eingetragen", "wineglass.fill", AchievementAccent.YELLOW),
        Achievement("first_mass", "Auf die Maß!", "Erste Maß Bier (500 ml+) eingetragen", "mug.fill", AchievementAccent.AMBER),
        Achievement("first_altbier", "Alt-Meister", "Erstes Altbier eingetragen", "cup.and.saucer.fill", AchievementAccent.ORANGE),
        Achievement("local_specialties", "Nord-Sued", "Kölsch und Altbier an einem Tag probiert", "map.fill", AchievementAccent.GREEN),

        // BAC-Stufen
        Achievement("bac_05", "Haelfte erreicht", "0,5 Promille in einer Sitzung erreicht", "gauge.with.dots.needle.50percent", AchievementAccent.YELLOW),
        Achievement("bac_10", "Volle Pulle", "1,0 Promille in einer Sitzung erreicht", "gauge.with.dots.needle.67percent", AchievementAccent.ORANGE),
        Achievement("bac_15", "Felipe", "1,5 Promille in einer Sitzung erreicht", "exclamationmark.triangle.fill", AchievementAccent.ORANGE),

        // Nüchternheits-Streaks
        Achievement("sober_3", "Drei Tage durch", "3 Tage am Stueck ohne Alkohol", "leaf.fill", AchievementAccent.GREEN),
        Achievement("sober_7", "Gute Woche", "7 Tage am Stueck ohne Alkohol", "checkmark.seal.fill", AchievementAccent.GREEN),
        Achievement("sober_14", "Zwei Wochen", "14 Tage am Stueck ohne Alkohol", "flame.fill", AchievementAccent.AMBER),
        Achievement("sober_30", "Monats-Champion", "30 Tage am Stueck ohne Alkohol", "trophy.fill", AchievementAccent.GREEN),

        // Cocktail-Connaisseur
        Achievement("cocktails_5", "Cocktailkarte", "5 verschiedene Cocktails eingetragen", "wineglass", AchievementAccent.GREEN),
        Achievement("cocktails_10", "Bar-Kenner", "10 verschiedene Cocktails eingetragen", "wineglass.fill", AchievementAccent.AMBER),

        // Spirituosen
        Achievement("spirits_5", "Spirituosen-Fan", "5 verschiedene Spirituosen eingetragen", "drop.fill", AchievementAccent.AMBER),
        Achievement("first_whisky", "Single Malt", "Erstes Whisky oder Bourbon eingetragen", "drop.fill", AchievementAccent.ORANGE),
        Achievement("wine_both", "Rotwein trifft Weiss", "Roten und weissen Wein eingetragen", "wineglass.fill", AchievementAccent.ORANGE),

        // Zeit-basiert
        Achievement("night_owl", "Nachteule", "Drink zwischen 0 und 4 Uhr eingetragen", "moon.stars.fill", AchievementAccent.YELLOW),
        Achievement("early_bird", "Fruehstuecks-Bier", "Drink zwischen 6 und 12 Uhr eingetragen", "sunrise.fill", AchievementAccent.AMBER),
        Achievement("silvester", "Gutes Neues", "Drink am 31. Dezember eingetragen", "fireworks", AchievementAccent.YELLOW),
        Achievement("monday_drink", "Montags-Freude", "Drink an einem Montag eingetragen", "calendar.badge.plus", AchievementAccent.GREEN),

        // Social erweitert
        Achievement("crew_5", "Die Gang ist da", "5 Personen in der Crew", "person.3.fill", AchievementAccent.ORANGE),
        Achievement("photo_5", "Abend im Bild", "5 Erinnerungsfotos gemacht", "photo.on.rectangle", AchievementAccent.GREEN),
        Achievement("jam_created", "Jam-Session", "Ersten Jam erstellt", "waveform", AchievementAccent.AMBER),

        // Spezial
        Achievement("all_beer_styles", "Bierstil-Experte", "Pils, Weizen und Dunkel alle probiert", "mug.fill", AchievementAccent.AMBER),
        Achievement("spirits_variety", "Spirit-Ranger", "3 verschiedene Spirituosen-Typen probiert", "drop.circle.fill", AchievementAccent.ORANGE),
        Achievement("multi_session", "Abend-Regulaer", "An 5 verschiedenen Tagen Alkohol eingetragen", "calendar.badge.checkmark", AchievementAccent.GREEN),
    )
}
