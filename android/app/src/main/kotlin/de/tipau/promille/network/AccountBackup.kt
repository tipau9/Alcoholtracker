package de.tipau.promille.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.time.Instant
import java.time.format.DateTimeFormatter

/*
 * The user_backup.data document. Field names are the Swift property names, not
 * snake_case: ProfileBackup and friends have no CodingKeys, so the wire carries
 * camelCase. This is the opposite convention from every other DTO in this
 * package, and getting it wrong is silent: syncSettings uploads the whole blob
 * every sync, so a field this side does not know is a field deleted from the
 * server, and the iOS decoder then substitutes a default without any error.
 *
 * For the same reason the field list mirrors ProfileBackup.swift exactly. The
 * Android-only profile columns (conservativeSafety, conservativeEverywhere,
 * homeSectionOrderRaw) are deliberately absent: iOS does not back them up, so
 * adding them here would only make the blob asymmetric.
 */

/** Dates in the blob are Swift's .iso8601, which has NO fractional seconds. */
object BlobDates {

    private val whole: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun format(epochSeconds: Long): String = whole.format(Instant.ofEpochSecond(epochSeconds))

    fun parse(raw: String?): Long? = Timestamps.parse(raw)?.toLong()
}

/** explicitNulls = false so an absent optional stays absent, like Swift's encodeIfPresent. */
internal val blobJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class AccountBackup(
    val profile: ProfileBackup? = null,
    val waterLog: Map<String, Int>? = null,
    val customMixes: List<MixBackup>? = null,
    val customDrinks: List<TemplateBackup>? = null
)

/**
 * Every field carries the UserProfile default, mirroring the hand written
 * tolerant decoder on the Swift side: a backup written by an older build must
 * never fail to decode as a whole, because that silently drops the restore.
 */
@Serializable
data class ProfileBackup(
    val weight: Double = 70.0,
    val height: Double = 175.0,
    val age: Int = 25,
    /** Null only when an old backup predates the field; the local value is kept. */
    val birthDate: String? = null,
    val genderRaw: String = "diverse",
    val eliminationRate: Double = 0.15,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val homeStyleRaw: String = "detailed",
    val activeWidgetsRaw: String = "",
    val largeText: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val toleranceMode: Boolean = false,
    val warningThreshold: Double = 0.5,
    val stomachStatusRaw: String = "light",
    val statusSkinRaw: String = "standard",
    val tipsyThreshold: Double = 0.01,
    val drunkThreshold: Double = 0.30,
    val carefulThreshold: Double = 0.80,
    val dangerThreshold: Double = 1.50,
    val accentColorHex: String = "C9802F",
    val sipVolumeML: Double = 25.0,
    val activeMedicationsRaw: String = "",
    val healthKitEnabled: Boolean = false,
    val weeklyDrinkLimit: Int = 0,
    val soberDaysGoal: Int = 4,
    val isProbationaryDriver: Boolean = false,
    val drunkModeAuto: Boolean = false,
    val onboardingStepsCompleted: List<String> = emptyList(),
    val hasCompletedOnboarding: Boolean = false
)

@Serializable
data class MixBackup(
    val id: String,
    val name: String,
    /** Passed through untouched so ids inside keep whatever case wrote them. */
    val ingredients: JsonArray,
    val createdAt: String
)

@Serializable
data class TemplateBackup(
    val id: String,
    val name: String,
    val categoryRaw: String,
    val volume: Double,
    val abv: Double,
    val calories: Int,
    val iconName: String,
    val usageCount: Int = 0,
    val barcode: String = ""
)
