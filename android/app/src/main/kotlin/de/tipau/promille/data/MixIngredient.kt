package de.tipau.promille.data

import kotlinx.serialization.Serializable

/**
 * One component of a mixed drink. Same JSON shape the iOS build stores in
 * CustomMix.ingredientsData and sends to contribute_mix, so a mix shared from
 * either platform decodes on the other.
 */
@Serializable
data class MixIngredient(
    val id: String = newId(),
    val name: String,
    /** ABV in percent. */
    val abv: Double,
    /** Millilitres. */
    val volume: Double
) {
    val alcoholGrams: Double get() = volume * (abv / 100.0) * 0.789
}
