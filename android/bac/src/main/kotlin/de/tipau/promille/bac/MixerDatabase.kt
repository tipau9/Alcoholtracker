package de.tipau.promille.bac

/**
 * Non-alcoholic component of a mixed drink. It carries no alcohol, so it only
 * affects calories and the hydration model, never the permille number.
 */
enum class MixerCategory(
    val raw: String,
    val germanName: String,
    val symbolName: String
) {
    SODA("soda", "Softdrink", "cup.and.saucer.fill"),
    BITTER("bitter", "Bitter", "drop.fill"),
    ENERGY("energy", "Energy", "bolt.fill"),
    JUICE("juice", "Saft", "leaf.fill"),
    WATER("water", "Wasser", "drop"),
    CREAM("cream", "Cremig", "drop.circle.fill"),
    TEA("tea", "Tee", "cup.and.saucer"),
    OTHER("other", "Sonstiges", "star.fill");

    companion object {
        fun from(raw: String): MixerCategory = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

data class Mixer(
    val name: String,
    val category: MixerCategory,
    val caloriesPer100ml: Int,
    val waterContentPercent: Double,
    val icon: String
) {
    val id: String get() = name
}

/**
 * Built-in mixer catalog, generated from Services/MixerDatabase.swift. The values
 * feed calories and hydration, so keep both sides in step.
 */
object MixerDatabase {

    fun entries(category: MixerCategory): List<Mixer> = ALL.filter { it.category == category }

    /** A blank query returns everything, matching the iOS picker. */
    fun search(query: String): List<Mixer> {
        if (query.trim().isEmpty()) return ALL
        val q = query.lowercase()
        return ALL.filter { it.name.lowercase().contains(q) }
    }

    /** Categories in declaration order, names sorted inside each one. */
    fun grouped(): List<Pair<MixerCategory, List<Mixer>>> {
        val byCategory = ALL.groupBy { it.category }
        return MixerCategory.entries.mapNotNull { cat ->
            byCategory[cat]?.let { cat to it.sortedBy { m -> m.name } }
        }
    }

    val ALL: List<Mixer> = listOf(
        Mixer("Coca-Cola", MixerCategory.SODA, 41, 89.0, "cup.and.saucer.fill"),
        Mixer("Coca-Cola Zero", MixerCategory.SODA, 0, 99.0, "cup.and.saucer.fill"),
        Mixer("Pepsi", MixerCategory.SODA, 42, 89.0, "cup.and.saucer.fill"),
        Mixer("Pepsi Max", MixerCategory.SODA, 0, 99.0, "cup.and.saucer.fill"),
        Mixer("Sprite", MixerCategory.SODA, 38, 90.0, "cup.and.saucer.fill"),
        Mixer("Fanta Orange", MixerCategory.SODA, 40, 89.0, "cup.and.saucer.fill"),
        Mixer("7Up", MixerCategory.SODA, 38, 90.0, "cup.and.saucer.fill"),
        Mixer("Spezi", MixerCategory.SODA, 40, 88.0, "cup.and.saucer.fill"),
        Mixer("Limonade Zitrone", MixerCategory.SODA, 38, 90.0, "cup.and.saucer.fill"),
        Mixer("Club Soda", MixerCategory.SODA, 0, 100.0, "cup.and.saucer.fill"),
        Mixer("Tonic Water", MixerCategory.BITTER, 35, 91.0, "drop.fill"),
        Mixer("Bitter Lemon", MixerCategory.BITTER, 37, 91.0, "drop.fill"),
        Mixer("Ginger Ale", MixerCategory.BITTER, 35, 90.0, "drop.fill"),
        Mixer("Ginger Beer", MixerCategory.BITTER, 42, 89.0, "drop.fill"),
        Mixer("Schweppes Russian", MixerCategory.BITTER, 36, 91.0, "drop.fill"),
        Mixer("Red Bull", MixerCategory.ENERGY, 46, 88.0, "bolt.fill"),
        Mixer("Red Bull Sugarfree", MixerCategory.ENERGY, 4, 99.0, "bolt.fill"),
        Mixer("Monster Energy", MixerCategory.ENERGY, 46, 88.0, "bolt.fill"),
        Mixer("Monster Zero", MixerCategory.ENERGY, 4, 99.0, "bolt.fill"),
        Mixer("Rockstar", MixerCategory.ENERGY, 50, 87.0, "bolt.fill"),
        Mixer("Effect Energy", MixerCategory.ENERGY, 47, 87.0, "bolt.fill"),
        Mixer("Burn Energy Drink", MixerCategory.ENERGY, 44, 88.0, "bolt.fill"),
        Mixer("Orangensaft", MixerCategory.JUICE, 45, 88.0, "leaf.fill"),
        Mixer("Apfelsaft", MixerCategory.JUICE, 47, 87.0, "leaf.fill"),
        Mixer("Cranberrysaft", MixerCategory.JUICE, 46, 87.0, "leaf.fill"),
        Mixer("Ananassaft", MixerCategory.JUICE, 53, 86.0, "leaf.fill"),
        Mixer("Grapefruitsaft", MixerCategory.JUICE, 38, 90.0, "leaf.fill"),
        Mixer("Limettensaft", MixerCategory.JUICE, 25, 93.0, "leaf.fill"),
        Mixer("Zitronensaft", MixerCategory.JUICE, 22, 93.0, "leaf.fill"),
        Mixer("Tomatensaft", MixerCategory.JUICE, 17, 94.0, "leaf.fill"),
        Mixer("Maracujasaft", MixerCategory.JUICE, 51, 86.0, "leaf.fill"),
        Mixer("Traubensaft", MixerCategory.JUICE, 60, 84.0, "leaf.fill"),
        Mixer("Kokoswasser", MixerCategory.JUICE, 19, 95.0, "leaf.fill"),
        Mixer("Pfirsichsaft", MixerCategory.JUICE, 49, 88.0, "leaf.fill"),
        Mixer("Multivitamin", MixerCategory.JUICE, 50, 87.0, "leaf.fill"),
        Mixer("Mineralwasser", MixerCategory.WATER, 0, 100.0, "drop"),
        Mixer("Soda Water", MixerCategory.WATER, 0, 100.0, "drop"),
        Mixer("Sprudelwasser", MixerCategory.WATER, 0, 100.0, "drop"),
        Mixer("Sahne", MixerCategory.CREAM, 340, 57.0, "drop.circle.fill"),
        Mixer("Kokosmilch", MixerCategory.CREAM, 180, 66.0, "drop.circle.fill"),
        Mixer("Vollmilch", MixerCategory.CREAM, 61, 88.0, "drop.circle.fill"),
        Mixer("Mandelmilch", MixerCategory.CREAM, 24, 96.0, "drop.circle.fill"),
        Mixer("Haferdrink", MixerCategory.CREAM, 46, 89.0, "drop.circle.fill"),
        Mixer("Eistee Pfirsich", MixerCategory.TEA, 30, 92.0, "cup.and.saucer"),
        Mixer("Eistee Zitrone", MixerCategory.TEA, 28, 92.0, "cup.and.saucer"),
        Mixer("Schwarzer Tee", MixerCategory.TEA, 1, 99.0, "cup.and.saucer"),
        Mixer("Gruener Tee", MixerCategory.TEA, 1, 99.0, "cup.and.saucer"),
        Mixer("Grenadine", MixerCategory.OTHER, 280, 40.0, "star.fill"),
        Mixer("Zuckersirup", MixerCategory.OTHER, 290, 30.0, "star.fill"),
        Mixer("Agavensirup", MixerCategory.OTHER, 310, 20.0, "star.fill"),
        Mixer("Limettenkordial", MixerCategory.OTHER, 250, 40.0, "star.fill"),
        Mixer("Maracujasirup", MixerCategory.OTHER, 270, 35.0, "star.fill"),
        Mixer("Ingwersirup", MixerCategory.OTHER, 295, 28.0, "star.fill"),
        Mixer("Erdbeersirup", MixerCategory.OTHER, 265, 37.0, "star.fill"),
        Mixer("Espresso", MixerCategory.OTHER, 5, 98.0, "cup.and.saucer.fill"),
    )
}
