package de.tipau.promille.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire shapes for the PostgREST tables. Every optional field carries a default,
 * because a column that does not exist yet on the server must degrade into "off"
 * rather than fail the whole decode, the same way the Swift init(from:) does.
 */

@Serializable
data class FriendProfile(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("friend_code") val friendCode: String = "",
    @SerialName("current_bac") val currentBac: Double? = null,
    @SerialName("bac_updated_at") val bacUpdatedAtRaw: String? = null,
    @SerialName("is_sharing") val isSharing: Boolean = true,
    val achievements: List<String>? = null,
    @SerialName("sos_active") val sosActive: Boolean = false,
    @SerialName("is_probationary") val isProbationary: Boolean = false
) {
    val bacUpdatedAt: Double? get() = Timestamps.parse(bacUpdatedAtRaw)
}

@Serializable
data class RemoteDrink(
    val id: String,
    val name: String = "",
    val volume: Double = 0.0,
    val abv: Double = 0.0,
    val calories: Int = 0,
    @SerialName("icon_name") val iconName: String = "mug.fill",
    val category: String = "other",
    @SerialName("mixer_volume") val mixerVolume: Double = 0.0,
    @SerialName("mixer_water_content") val mixerWaterContent: Double = 0.0,
    @SerialName("drink_duration_minutes") val drinkDurationMinutes: Double = 0.0,
    @SerialName("template_id") val templateID: String? = null,
    @SerialName("consumed_at") val consumedAtRaw: String? = null
) {
    val consumedAtEpochSeconds: Long get() = (Timestamps.parse(consumedAtRaw) ?: 0.0).toLong()
}

@Serializable
data class RemoteDayNote(
    @SerialName("day_start") val dayStart: String,
    val text: String = "",
    val mood: Int = 0
)
