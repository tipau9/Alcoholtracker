package de.tipau.promille.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The starter catalog, mirroring DrinkDatabase. The entries come from
 * assets/drink_catalog.json, which android/tools/extract_drink_catalog.py
 * generates out of DrinkDatabase.swift. Never edit the JSON by hand: it feeds a
 * permille number and the Swift arrays stay the single source of truth.
 */
object DrinkCatalog {

    @Serializable
    data class Entry(
        val name: String,
        val category: String,
        val volumeML: Double,
        val abv: Double,
        val calories: Int,
        val iconName: String
    )

    @Serializable
    data class Catalog(val version: Int, val drinks: List<Entry>)

    private const val ASSET = "drink_catalog.json"
    private const val PREFS = "drink_database"

    /** Same key the iOS build uses, so the two gates read the same way. */
    private const val VERSION_KEY = "DrinkDatabaseVersion"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): Catalog = json.decodeFromString(Catalog.serializer(), raw)

    /**
     * The id is derived from the entry, not random: a re-seed after cleared data
     * then hands out the same ids, so drink rows keep pointing at their template.
     */
    fun Entry.toEntity(): DrinkTemplateEntity = DrinkTemplateEntity(
        id = java.util.UUID.nameUUIDFromBytes(
            "${name}_${category}_${volumeML}".toByteArray()
        ).toString(),
        name = name,
        categoryRaw = category,
        volume = volumeML,
        abv = abv,
        calories = calories,
        iconName = iconName
    )

    /**
     * Ports seedIfNeeded one for one. It seeds when the catalog version advanced
     * OR the store is unexpectedly empty; the empty-store branch self-heals an
     * install whose data was cleared while the version flag still read "seeded",
     * which would otherwise leave the drink list blank forever.
     */
    suspend fun seedIfNeeded(context: Context, dao: DrinkTemplateDao) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getInt(VERSION_KEY, 0)
        val existing = dao.getAll()

        val catalog = parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
        if (stored >= catalog.version && existing.isNotEmpty()) return

        val existingNames = existing.map { it.name }.toSet()
        dao.insertAll(catalog.drinks.filter { it.name !in existingNames }.map { it.toEntity() })

        // Patch icons on existing non-custom entries to match the current catalog.
        val canonical = catalog.drinks.associate { it.name to it.iconName }
        val repaired = existing.filter { !it.isCustom }
            .mapNotNull { entry ->
                val icon = canonical[entry.name] ?: return@mapNotNull null
                if (icon == entry.iconName) null else entry.copy(iconName = icon)
            }
        repaired.forEach { dao.update(it) }

        prefs.edit().putInt(VERSION_KEY, catalog.version).apply()
    }
}
