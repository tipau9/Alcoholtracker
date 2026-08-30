package de.tipau.promille.bac

import kotlin.math.max
import kotlin.math.min

// Kotlin mirror of the iOS domain types the BAC engine reads. Every constant here
// is pinned by testdata/bac_vectors.json; changing one without regenerating the
// vectors on the Swift side makes the two apps disagree about a permille number.

enum class Gender(val raw: String) {
    MALE("male"),
    FEMALE("female"),
    DIVERSE("diverse");

    companion object {
        fun from(raw: String): Gender = entries.firstOrNull { it.raw == raw } ?: DIVERSE
    }
}

/** Gastric emptying time and resorption deficit per stomach fullness. */
enum class StomachStatus(
    val raw: String,
    val absorptionMinutes: Double,
    val peakFactor: Double,
    val germanName: String
) {
    EMPTY("empty", 45.0, 0.90, "Leer"),
    LIGHT("light", 75.0, 0.81, "Leicht gefüllt"),
    FULL("full", 90.0, 0.75, "Satt");

    companion object {
        fun from(raw: String): StomachStatus = entries.firstOrNull { it.raw == raw } ?: LIGHT
    }
}

/**
 * Scales [StomachStatus.absorptionMinutes]. CO2 accelerates gastric emptying;
 * shots reach peak faster due to small volume and rapid transit.
 */
enum class DrinkCategory(val raw: String, val absorptionModifier: Double, val germanName: String) {
    BEER("beer", 0.85, "Bier"),
    WINE("wine", 1.0, "Wein"),
    SPARKLING("sparkling", 0.85, "Sekt und Schaumwein"),
    SPIRITS("spirits", 1.0, "Spirituose"),
    LIQUEUR("liqueur", 1.0, "Likör"),
    COCKTAIL("cocktail", 1.0, "Cocktail"),
    MIXED("mixed", 1.0, "Mischgetränk"),
    SHOT("shot", 0.75, "Shot"),
    CIDER("cider", 0.85, "Cider"),
    FORTIFIED("fortified", 1.0, "Likörwein"),
    WATER("water", 1.0, "Wasser"),
    SOFT_DRINK("softDrink", 1.0, "Softdrink"),
    JUICE("juice", 1.0, "Saft"),
    COFFEE_TEA("coffeeTea", 1.0, "Kaffee und Tee"),
    MILK("milk", 1.0, "Milch"),
    OTHER("other", 1.0, "Sonstiges");

    companion object {
        fun from(raw: String): DrinkCategory = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

/**
 * A meal stretches only the absorption time that remains when it is logged. The
 * total dose is conserved, so food never removes alcohol already in the blood.
 */
enum class MealImpact(
    val raw: String,
    val remainingAbsorptionMultiplier: Double,
    val activeDurationSeconds: Double,
    val germanName: String
) {
    SNACK("snack", 1.15, 2 * 3600.0, "Snack"),
    LIGHT_MEAL("lightMeal", 1.35, 3 * 3600.0, "Leichte Mahlzeit"),
    FULL_MEAL("fullMeal", 1.65, 4 * 3600.0, "Volle Mahlzeit");

    companion object {
        fun from(raw: String): MealImpact = entries.firstOrNull { it.raw == raw } ?: LIGHT_MEAL
    }
}

/**
 * One logged drink. [offsetMinutes] is measured on whatever timeline the caller
 * uses; the engine only ever looks at differences.
 *
 * [drinkDurationMinutes] must be positive. iOS falls back to a persisted pace
 * history when it is zero, which is not portable, so the caller supplies it.
 */
data class DrinkInput(
    val offsetMinutes: Double,
    val volumeML: Double,
    val abv: Double,
    val category: DrinkCategory,
    val drinkDurationMinutes: Double
)

data class MealInput(val offsetMinutes: Double, val impact: MealImpact)

/**
 * Body data plus the elimination settings. Widmark r comes from Watson (1980)
 * total body water, divided by the blood-water fraction so the result is a BLOOD
 * factor (the legal permille basis) rather than a body-water one.
 */
data class Profile(
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val gender: Gender,
    val eliminationRate: Double = 0.15,
    val toleranceMode: Boolean = false,
    val isProbationaryDriver: Boolean = false,
    val conservativeSafety: Boolean = false,
    val conservativeEverywhere: Boolean = false,
    val defaultStomachStatus: StomachStatus = StomachStatus.LIGHT,
    // Status band edges, user-adjustable. Defaults mirror UserProfile.swift.
    val warningThreshold: Double = 0.5,
    val tipsyThreshold: Double = 0.01,
    val drunkThreshold: Double = 0.30,
    val carefulThreshold: Double = 0.80,
    val dangerThreshold: Double = 1.50
) {
    /** Engine floor is 30 kg, below the 35 kg onboarding minimum, so legacy data never raises BAC. */
    val validatedWeight: Double get() = min(max(weightKg, 30.0), 250.0)

    val validatedHeight: Double get() = min(max(heightCm, 120.0), 230.0)

    val validatedAge: Int get() = min(max(age, 18), 100)

    /** Total body water in litres (Watson 1980). */
    val totalBodyWaterL: Double
        get() {
            val a = validatedAge.toDouble()
            val male = 2.447 - 0.09516 * a + 0.1074 * validatedHeight + 0.3362 * validatedWeight
            val female = -2.097 + 0.1069 * validatedHeight + 0.2466 * validatedWeight
            return when (gender) {
                Gender.MALE -> male
                Gender.FEMALE -> female
                Gender.DIVERSE -> (male + female) / 2.0
            }
        }

    val distributionFactor: Double
        get() = min(max((totalBodyWaterL / validatedWeight) / 0.806, 0.50), 0.90)

    /** Regular drinkers metabolise faster; tolerance mode enforces a 0.20 permille/h floor. */
    val effectiveEliminationRate: Double
        get() = if (toleranceMode) max(eliminationRate, 0.20) else eliminationRate

    /**
     * Worst-case math must not assume the faster metabolism of tolerance mode: a
     * higher rate shortens the sober/driveable times and errs optimistic, which is
     * the opposite of what a worst-case readout should do.
     */
    fun resolvedEliminationRate(conservative: Boolean): Double =
        if (conservative) eliminationRate else effectiveEliminationRate

    /**
     * Whether the safety readiness timers and forecast use the worst-case model:
     * either the safety-only switch or the app-wide switch turns it on.
     */
    val conservativeForSafety: Boolean get() = conservativeSafety || conservativeEverywhere

    /**
     * Whether the rest of the app (home, charts, badges) uses the worst-case model.
     * Only the app-wide switch does this.
     */
    val conservativeForApp: Boolean get() = conservativeEverywhere

    /** 0.0 during the probationary period, otherwise the German 0.5 permille limit. */
    val drivingLimit: Double get() = if (isProbationaryDriver) 0.0 else 0.5

    fun mayDrive(bac: Double): Boolean =
        if (isProbationaryDriver) bac <= 0.005 else bac < 0.5

    /**
     * Identity of everything a projection depends on. The thresholds do not bend
     * the BAC curve, but they do change what a session writes out of it: driving
     * readiness, status bands, notifications, the widget and the notification.
     */
    val bacProjectionKey: String
        get() = listOf(
            weightKg, heightCm, gender.raw, age,
            eliminationRate, toleranceMode,
            isProbationaryDriver,
            warningThreshold, tipsyThreshold, drunkThreshold,
            carefulThreshold, dangerThreshold
        ).joinToString("|")

    companion object {
        val DEFAULT = Profile(
            weightKg = 75.0,
            heightCm = 175.0,
            age = 25,
            gender = Gender.MALE
        )
    }
}
