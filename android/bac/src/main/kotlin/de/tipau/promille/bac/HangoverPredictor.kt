package de.tipau.promille.bac

import kotlin.math.max
import kotlin.math.min

/**
 * Ascending severity. Declared low to high so `maxOf(level, MODERATE)` reads as
 * "at least moderate", which is how the peak-BAC floor below is expressed.
 */
enum class HangoverLevel(val germanLabel: String) {
    NONE("Kein Kater erwartet"),
    MILD("Leichtes Unbehagen möglich"),
    MODERATE("Spürbarer Kater morgen"),
    STRONG("Harter Tag morgen"),
    SEVERE("Sehr schwerer Kater morgen"),
    LETHAL("Lebensgefahr – tödlicher Bereich");

    val isPositive: Boolean get() = this == NONE

    /** Worst tier: genuine medical danger, shown with an extra warning line. */
    val isLethal: Boolean get() = this == LETHAL
}

/** Mirrors Services/HangoverPredictor.swift. */
object HangoverPredictor {

    /**
     * @param peakBAC highest permille reached during the session
     * @param durationHours hours from the first to the last drink
     * @param waterGlasses glasses of water drunk
     * @param drinksCount total drinks consumed
     */
    fun predict(
        peakBAC: Double,
        durationHours: Double,
        waterGlasses: Double,
        drinksCount: Int
    ): HangoverLevel {
        // A peak this high is a medical emergency, not a hangover. Respiratory
        // depression and aspiration become a realistic acute risk from ~3 permille
        // upward (earlier in less tolerant drinkers), so the explicit Lebensgefahr
        // alarm starts there rather than only at 4, which understated the 3-4 range.
        if (peakBAC >= 3.0) return HangoverLevel.LETHAL

        var score = 0.0
        score += peakBAC * 2.0
        score += durationHours * 0.10
        score += drinksCount * 0.08

        // Hydration eases symptoms but must not erase a high BAC. Cap the benefit
        // both per amount (~1.5 glasses per alcoholic drink) AND in total (1.5
        // score points), so good hydration lowers the forecast by roughly a tier
        // without ever making a heavy night read as harmless.
        val usefulWater = min(waterGlasses, max(2.0, drinksCount * 1.5))
        score -= min(usefulWater * 0.35, 1.5)

        // Calibrated so that 1.5-2.0 permille is usually a hard/severe hangover
        // signal, not a medical death warning, and water can move it down a tier.
        val scored = when {
            score < 1.2 -> HangoverLevel.NONE
            score < 2.0 -> HangoverLevel.MILD
            score < 3.0 -> HangoverLevel.MODERATE
            score < 4.4 -> HangoverLevel.STRONG
            else -> HangoverLevel.SEVERE
        }

        // Floor by the raw peak: a genuinely high blood-alcohol peak is toxic and
        // dehydrating on its own, so neither water nor a short session may
        // downgrade below the matching minimum tier.
        if (peakBAC >= 2.0) return maxOf(scored, HangoverLevel.MODERATE)
        if (peakBAC >= 1.2) return maxOf(scored, HangoverLevel.MILD)
        return scored
    }

    /**
     * Session variant. [waterGlasses] takes real logged glasses; null falls back to
     * the heuristic of one glass per two alcoholic drinks.
     */
    fun predict(
        drinks: List<Drink>,
        profile: Profile,
        waterGlasses: Double? = null,
        stomachStatus: StomachStatus? = null,
        conservative: Boolean? = null,
        vomitEpochSeconds: List<Long> = emptyList(),
        meals: List<MealEvent> = emptyList(),
        pace: DrinkPaceMemory = DrinkPaceMemory.disabled()
    ): HangoverLevel {
        // Only alcoholic drinks drive a hangover: a cola or water logged late must
        // not stretch the session duration.
        val alcoholic = drinks.filter { it.abv > 0 }
        val first = alcoholic.minOfOrNull { it.timestampEpochSeconds } ?: return HangoverLevel.NONE
        val last = alcoholic.maxOfOrNull { it.timestampEpochSeconds } ?: return HangoverLevel.NONE
        val durationHours = (last - first) / 3600.0

        val peakBAC = BacProjectionInput(
            drinks = drinks,
            profile = profile,
            stomachStatus = stomachStatus ?: profile.defaultStomachStatus,
            // Danger classification uses the same worst-case peak the Safety tab
            // shows, not the app-wide realistic one, so the Kater/Lebensgefahr tier
            // is never systematically below what the user sees under Sicherheit.
            conservative = conservative ?: profile.conservativeForSafety,
            vomitEpochSeconds = vomitEpochSeconds,
            meals = meals,
            pace = pace
        ).curve(
            fromEpochSeconds = first,
            hours = durationHours + 6.0,
            intervalMinutes = 15.0
        ).maxOfOrNull { it.bac } ?: 0.0

        val water = waterGlasses ?: (alcoholic.size / 2.0)
        return predict(
            peakBAC = peakBAC,
            durationHours = durationHours,
            waterGlasses = water,
            drinksCount = alcoholic.size
        )
    }
}
