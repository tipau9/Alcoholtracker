package de.tipau.promille.network

import android.content.Context
import de.tipau.promille.data.MixIngredient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

/*
 * The self-learning community database and the anonymous city trends. Reads go
 * out with the anon key, writes go through the SECURITY DEFINER contribute_*
 * RPCs, which validate the payload and record one vote per install.
 */

@Serializable
data class CommunityDrinkRow(
    val id: String,
    val barcode: String = "",
    val name: String = "",
    val category: String = "other",
    val volume: Double = 0.0,
    val abv: Double = 0.0,
    val calories: Int = 0,
    @SerialName("icon_name") val iconName: String = "mug.fill",
    @SerialName("confirmed_count") val confirmedCount: Int = 0
)

@Serializable
data class CommunityMixRow(
    val id: String,
    val name: String = "",
    val ingredients: List<MixIngredient> = emptyList(),
    @SerialName("total_volume") val totalVolume: Double = 0.0,
    @SerialName("total_abv") val totalAbv: Double = 0.0,
    val calories: Int = 0,
    @SerialName("confirmed_count") val confirmedCount: Int = 0
)

@Serializable
data class CityDrinkTrend(
    @SerialName("drink_name") val drinkName: String = "",
    val category: String = "",
    @SerialName("ping_count") val pingCount: Int = 0
)

@Serializable
data class CityRankedDrink(
    @SerialName("drink_name") val drinkName: String = "",
    val category: String = "",
    @SerialName("ping_count") val pingCount: Int = 0
)

@Serializable
data class CityHourlyTrend(val hour: Int = 0, @SerialName("ping_count") val pingCount: Int = 0)

@Serializable
data class CityCategoryTrend(
    val category: String = "",
    @SerialName("ping_count") val pingCount: Int = 0
)

@Serializable
data class CityDrinkInsights(
    @SerialName("sample_sufficient") val sampleSufficient: Boolean = false,
    @SerialName("minimum_contributors") val minimumContributors: Int = 0,
    @SerialName("contributor_count") val contributorCount: Int? = null,
    @SerialName("total_drinks") val totalDrinks: Int = 0,
    @SerialName("average_bac") val averageBAC: Double? = null,
    @SerialName("average_session_minutes") val averageSessionMinutes: Double? = null,
    @SerialName("average_drink_minutes") val averageDrinkMinutes: Double? = null,
    @SerialName("top_drinks") val topDrinks: List<CityRankedDrink> = emptyList(),
    val hourly: List<CityHourlyTrend> = emptyList(),
    val categories: List<CityCategoryTrend> = emptyList()
)

/**
 * One vote per install. Generated once and kept, so a device cannot inflate a
 * drink by rescanning it.
 */
object AnonVoter {
    private const val PREFS = "supabase_community"
    private const val KEY = "community.voterID"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}

suspend fun SupabaseService.fetchCommunityDrinks(): List<CommunityDrinkRow> {
    if (!isConfigured) throw SupabaseError.NotConfigured
    return decodeList(
        transport.publicGET(
            "/rest/v1/community_drinks?status=eq.approved&select=*" +
                "&order=confirmed_count.desc&limit=2000"
        )
    )
}

suspend fun SupabaseService.lookupCommunityBarcode(barcode: String): CommunityDrinkRow? {
    if (!isConfigured) throw SupabaseError.NotConfigured
    val encoded = URLEncoder.encode(barcode, "UTF-8")
    return decodeList<CommunityDrinkRow>(
        transport.publicGET(
            "/rest/v1/community_drinks?barcode=eq.$encoded&status=eq.approved&select=*&limit=1"
        )
    ).firstOrNull()
}

/**
 * Each scan is one vote. The RPC inserts the drink as pending and auto-approves
 * once enough distinct devices confirmed the same barcode; a row rejected in the
 * dashboard is never auto-approved again.
 */
suspend fun SupabaseService.contributeDrink(
    context: Context,
    name: String,
    category: String,
    volume: Double,
    abv: Double,
    calories: Int,
    iconName: String,
    barcode: String
) {
    if (!isConfigured || barcode.isEmpty()) return
    transport.communityPOST("/rest/v1/rpc/contribute_drink", buildJsonObject {
        put("p_barcode", barcode)
        put("p_name", name)
        put("p_category", category)
        put("p_volume", volume)
        put("p_abv", abv)
        put("p_calories", calories)
        put("p_icon_name", iconName)
        put("p_voter", AnonVoter.id(context))
    })
}

suspend fun SupabaseService.fetchCommunityMixes(): List<CommunityMixRow> {
    if (!isConfigured) throw SupabaseError.NotConfigured
    return decodeList(
        transport.publicGET(
            "/rest/v1/community_mixes?status=eq.approved&select=*" +
                "&order=confirmed_count.desc&limit=500"
        )
    )
}

suspend fun SupabaseService.contributeMix(
    context: Context,
    name: String,
    ingredients: List<MixIngredient>,
    totalVolume: Double,
    totalAbv: Double,
    calories: Int
) {
    if (!isConfigured) throw SupabaseError.NotConfigured
    val trimmed = name.trim()
    if (trimmed.isEmpty() || ingredients.isEmpty()) return
    transport.communityPOST("/rest/v1/rpc/contribute_mix", buildJsonObject {
        put("p_name", trimmed)
        // PostgREST passes the array straight into a jsonb parameter.
        put("p_ingredients", supabaseJson.encodeToJsonElement(ingredients))
        put("p_total_volume", totalVolume)
        put("p_total_abv", totalAbv)
        put("p_calories", calories)
        put("p_voter", AnonVoter.id(context))
    })
}

/**
 * Fire and forget: records that the user drank this in this city. The RPC is
 * SECURITY DEFINER and inserts without the user id, so the ping stays anonymous.
 */
suspend fun SupabaseService.pingCityDrink(
    city: String,
    drinkName: String,
    category: String,
    currentBAC: Double,
    sessionDurationMinutes: Int,
    drinkDurationMinutes: Int,
    localHour: Int
) {
    if (!isConfigured || userId == null || city.isEmpty()) return
    runCatching {
        transport.communityPOST("/rest/v1/rpc/ping_city_drink", buildJsonObject {
            put("p_city", city)
            put("p_drink_name", drinkName)
            put("p_category", category)
            put("p_current_bac", min(5.0, max(0.0, currentBAC)))
            put("p_session_duration_minutes", min(1440, max(0, sessionDurationMinutes)))
            put("p_drink_duration_minutes", min(480, max(1, drinkDurationMinutes)))
            put("p_local_hour", localHour)
        })
    }
}

suspend fun SupabaseService.fetchCityTrends(city: String, hours: Int = 24): List<CityDrinkTrend> {
    if (!isConfigured) throw SupabaseError.NotConfigured
    return decodeList(
        transport.publicRPC("city_drink_trends", buildJsonObject {
            put("p_city", city)
            put("p_hours", hours)
        })
    )
}

suspend fun SupabaseService.fetchCityInsights(city: String, hours: Int = 168): CityDrinkInsights {
    if (!isConfigured) throw SupabaseError.NotConfigured
    val raw = transport.publicRPC("city_drink_insights", buildJsonObject {
        put("p_city", city)
        put("p_hours", hours)
    })
    return runCatching { supabaseJson.decodeFromString<CityDrinkInsights>(raw) }
        .getOrElse { CityDrinkInsights() }
}
