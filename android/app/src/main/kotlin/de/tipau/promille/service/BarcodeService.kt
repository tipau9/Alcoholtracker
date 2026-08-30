package de.tipau.promille.service

import de.tipau.promille.bac.DrinkCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CandidateSource(val label: String) {
    LOCAL("Lokal gelernt"),
    COMMUNITY("Community-Datenbank"),
    OPEN_FOOD_FACTS("Open Food Facts"),
    MANUAL("Manuelle Eingabe")
}

data class DrinkTemplateCandidate(
    val name: String,
    val abv: Double,
    val barcode: String,
    val volume: Double = 330.0,
    val category: DrinkCategory = DrinkCategory.BEER,
    val foundInDatabase: Boolean = true,
    val source: CandidateSource = CandidateSource.OPEN_FOOD_FACTS,
    val adjustedBySanitizer: Boolean = false
) {
    companion object {
        fun create(
            name: String,
            abv: Double,
            barcode: String,
            volume: Double = 330.0,
            category: DrinkCategory = DrinkCategory.BEER,
            foundInDatabase: Boolean = true,
            source: CandidateSource = CandidateSource.OPEN_FOOD_FACTS
        ): DrinkTemplateCandidate {
            val safeABV = BarcodeService.sanitizedABV(abv)
            val safeVolume = BarcodeService.sanitizedVolumeML(volume)
            val safeCategory = BarcodeService.sanitizedCategory(category, safeABV)
            val adjusted = abs(safeABV - abv) > 0.001 ||
                abs(safeVolume - volume) > 0.001 ||
                safeCategory != category

            return DrinkTemplateCandidate(
                name = name,
                abv = safeABV,
                barcode = barcode,
                volume = safeVolume,
                category = safeCategory,
                foundInDatabase = foundInDatabase,
                source = source,
                adjustedBySanitizer = adjusted
            )
        }
    }
}

object BarcodeService {

    suspend fun lookup(barcode: String): DrinkTemplateCandidate? = withContext(Dispatchers.IO) {
        var lookupCode = barcode.trim()
        if (lookupCode.length == 12) {
            lookupCode = "0$lookupCode" // Pad UPC-A to EAN-13 for Open Food Facts
        }

        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$lookupCode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Promille-App/1.0 (Android; Getraenke-Tracker)")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode != 200) {
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val status = json.optInt("status", -1)
            if (status != 1 || !json.has("product")) {
                return@withContext null
            }

            val product = json.getJSONObject("product")
            val productName = product.optString("product_name").trim().ifEmpty {
                val brands = product.optString("brands").trim()
                if (brands.isNotEmpty()) brands.split(",").first().trim() else ""
            }

            if (productName.isEmpty()) return@withContext null

            // ABV extraction
            var abv = 0.0
            if (product.has("alcohol_value")) {
                abv = product.optDouble("alcohol_value", 0.0)
            }
            if (abv <= 0.0 && product.has("nutriments")) {
                val nutriments = product.getJSONObject("nutriments")
                abv = nutriments.optDouble("alcohol_100g", 0.0)
                if (abv <= 0.0) {
                    abv = nutriments.optDouble("alcohol", 0.0)
                }
            }
            if (abv.isNaN() || abv < 0.0) abv = 0.0

            // Quantity / volume extraction
            val quantityParts = mutableListOf<String>()
            product.optString("quantity").takeIf { it.isNotBlank() }?.let { quantityParts.add(it) }
            product.optString("product_quantity").takeIf { it.isNotBlank() }?.let { quantityParts.add(it) }
            product.optString("serving_size").takeIf { it.isNotBlank() }?.let { quantityParts.add(it) }
            product.optString("product_quantity_unit").takeIf { it.isNotBlank() }?.let { quantityParts.add(it) }

            val quantityText = quantityParts.joinToString(" ")
            val volume = sanitizedVolumeML(parseVolumeML(quantityText) ?: 330.0)

            // Category inference
            val tags = mutableListOf<String>()
            tags.add(productName)
            product.optString("categories").takeIf { it.isNotBlank() }?.let { tags.add(it) }
            product.optString("generic_name").takeIf { it.isNotBlank() }?.let { tags.add(it) }

            val categoryTags = product.optJSONArray("categories_tags")
            if (categoryTags != null) {
                for (i in 0 until categoryTags.length()) {
                    tags.add(categoryTags.optString(i))
                }
            }

            val rawCategory = inferCategory(tags, abv)
            val category = sanitizedCategory(rawCategory, abv)

            DrinkTemplateCandidate.create(
                name = productName,
                abv = abv,
                barcode = barcode,
                volume = volume,
                category = category,
                source = CandidateSource.OPEN_FOOD_FACTS
            )
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizedABV(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return min(80.0, max(0.0, value))
    }

    fun sanitizedVolumeML(value: Double): Double {
        if (!value.isFinite()) return 330.0
        return min(3000.0, max(5.0, value))
    }

    fun sanitizedCategory(category: DrinkCategory, abv: Double): DrinkCategory {
        val safeABV = sanitizedABV(abv)
        if (safeABV <= 0.05) return category
        return when (category) {
            DrinkCategory.WATER,
            DrinkCategory.SOFT_DRINK,
            DrinkCategory.JUICE,
            DrinkCategory.COFFEE_TEA,
            DrinkCategory.MILK -> DrinkCategory.MIXED
            else -> category
        }
    }

    fun parseVolumeML(text: String): Double? {
        val normalized = text.lowercase()
            .replace(",", ".")
            .replace("ℓ", "l")

        val unitPattern = "(ml|milliliter|cl|centiliter|l|liter|litre)\\b"
        val packPattern = "\\b\\d+\\s*[x×]\\s*(\\d+(?:\\.\\d+)?)\\s*$unitPattern"
        firstVolumeMatch(normalized, packPattern)?.let { return it }

        val pattern = "(\\d+(?:\\.\\d+)?)\\s*$unitPattern"
        return firstVolumeMatch(normalized, pattern)
    }

    private fun firstVolumeMatch(text: String, patternString: String): Double? {
        val matcher = Pattern.compile(patternString).matcher(text)
        if (!matcher.find()) return null
        val valueStr = matcher.group(1) ?: return null
        val unit = matcher.group(2) ?: return null
        val value = valueStr.toDoubleOrNull() ?: return null

        return when {
            unit.startsWith("ml") || unit.startsWith("milli") -> value
            unit.startsWith("cl") || unit.startsWith("centi") -> value * 10.0
            else -> value * 1000.0
        }
    }

    private fun inferCategory(tags: List<String>, abv: Double): DrinkCategory {
        val text = tags.joinToString(" ").lowercase()
        fun has(vararg needles: String): Boolean = needles.any { text.contains(it) }

        if (has("wine", "wein", "vino", "vin ")) return DrinkCategory.WINE
        if (has("sparkling", "champagne", "sekt", "prosecco", "cava")) return DrinkCategory.SPARKLING
        if (has("spirit", "whisky", "whiskey", "vodka", "wodka", "rum", "gin", "tequila", "schnaps")) return DrinkCategory.SPIRITS
        if (has("liqueur", "likör", "likoer")) return DrinkCategory.LIQUEUR
        if (has("cider")) return DrinkCategory.CIDER
        if (has("beer", "bier", "cerveza", "biere")) return DrinkCategory.BEER
        if (has("water", "wasser", "mineral-water", "eau-minerale", "still-water", "sparkling-water")) return DrinkCategory.WATER
        if (has("juice", "saft", "jus", "nectar", "smoothie")) return DrinkCategory.JUICE
        if (has("coffee", "kaffee", "café", "tea", "tee", "iced-tea", "eistee")) return DrinkCategory.COFFEE_TEA
        if (has("milk", "milch", "yoghurt-drink", "kefir")) return DrinkCategory.MILK
        if (has("soda", "soft-drink", "soft drink", "cola", "lemonade", "limonade", "energy-drink", "isotonic")) return DrinkCategory.SOFT_DRINK
        return if (abv > 0) DrinkCategory.BEER else DrinkCategory.SOFT_DRINK
    }
}
