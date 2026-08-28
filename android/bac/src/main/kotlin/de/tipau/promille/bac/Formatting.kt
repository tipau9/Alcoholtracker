package de.tipau.promille.bac

import java.util.Locale

// German number formatting, mirrors Utilities/Double+Permille.swift. The app is
// German-only, so the locale is pinned rather than taken from the device: a
// device set to English must still render a decimal comma.
private val GERMAN: Locale = Locale.GERMAN

/** e.g. 0.25 renders as "0,25 permille" with the real permille sign. */
fun Double.permilleString(): String = String.format(GERMAN, "%.2f ‰", this)

/** One decimal with a German comma, used inside insight sentences. */
fun Double.oneDecimalGerman(): String = String.format(GERMAN, "%.1f", this)

/** German display name of a drink category. Mirrors DrinkCategory.localizedName. */
val DrinkCategory.germanName: String
    get() = when (this) {
        DrinkCategory.BEER -> "Bier"
        DrinkCategory.WINE -> "Wein"
        DrinkCategory.SPARKLING -> "Sekt und Schaumwein"
        DrinkCategory.SPIRITS -> "Spirituose"
        DrinkCategory.LIQUEUR -> "Likör"
        DrinkCategory.COCKTAIL -> "Cocktail"
        DrinkCategory.MIXED -> "Mischgetränk"
        DrinkCategory.SHOT -> "Shot"
        DrinkCategory.CIDER -> "Cider"
        DrinkCategory.FORTIFIED -> "Likörwein"
        DrinkCategory.WATER -> "Wasser"
        DrinkCategory.SOFT_DRINK -> "Softdrink"
        DrinkCategory.JUICE -> "Saft"
        DrinkCategory.COFFEE_TEA -> "Kaffee und Tee"
        DrinkCategory.MILK -> "Milch"
        DrinkCategory.OTHER -> "Sonstiges"
    }

/**
 * Mirrors AuthGate.isValidEmail. Deliberately not a regex and deliberately not
 * android.util.Patterns: the point is to reject the typos a person makes in a
 * sign-up field, and to reject exactly the same ones the iOS gate rejects.
 */
fun isValidEmail(address: String): Boolean {
    val parts = address.split("@")
    if (parts.size != 2) return false
    val local = parts[0]
    val domain = parts[1]
    if (local.isEmpty() || !domain.contains(".")) return false
    if (domain.startsWith(".") || domain.endsWith(".")) return false
    return domain.split(".").all { it.isNotEmpty() }
}
