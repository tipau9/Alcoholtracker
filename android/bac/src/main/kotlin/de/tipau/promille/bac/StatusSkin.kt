package de.tipau.promille.bac

/**
 * Which band a permille value falls into. Mirrors BACStatus in
 * Models/DrinkTemplate.swift; [level] is the ordinal iOS exposes.
 */
enum class BacStatus(val level: Int, val germanName: String) {
    SOBER(0, "Nüchtern"),
    TIPSY(1, "Leicht beschwipst"),
    DRUNK(2, "Beschwipst"),
    // 0,8 to 1,5 permille is already well over the 0,5 legal limit, so this band
    // says "unfit to drive" rather than a softer "watch out".
    CAREFUL(3, "Fahruntüchtig"),
    DANGER(4, "Gefährlich");

    fun label(skin: StatusSkin): String = skin.label(this)

    companion object {
        /** Fixed default bands, used when no profile is loaded yet. */
        fun of(bac: Double): BacStatus = when {
            bac < 0.01 -> SOBER
            bac < 0.3 -> TIPSY
            bac < 0.8 -> DRUNK
            bac < 1.5 -> CAREFUL
            else -> DANGER
        }

        /** User-adjustable bands. A null profile falls back to [of]. */
        fun of(bac: Double, profile: Profile?): BacStatus {
            val p = profile ?: return of(bac)
            return when {
                bac < p.tipsyThreshold -> SOBER
                bac < p.drunkThreshold -> TIPSY
                bac < p.carefulThreshold -> DRUNK
                bac < p.dangerThreshold -> CAREFUL
                else -> DANGER
            }
        }
    }
}

/**
 * Swappable text vocabulary for the status labels. Raw values are English for
 * SwiftData backward compatibility and cross-platform sync; do not translate them.
 */
enum class StatusSkin(
    val raw: String,
    val displayName: String,
    val skinDescription: String
) {
    STANDARD("standard", "Standard", "Klassische deutsche Bezeichnungen."),
    NORMAL("normal", "Normal", "So wie man wirklich redet."),
    YOUTH("youth", "Alltag", "Alltägliche Sprache, nichts Aufgesetztes."),
    CHILL("chill", "Chill", "Entspannte, positive Formulierungen."),
    SAILOR("sailor", "Seemann", "Seemannsprache auf hoher See."),
    FORMAL("formal", "Formal", "Sachliche, medizinisch angelehnte Begriffe."),
    SCIENCE("science", "Wissenschaft", "Physiologische Effektbeschreibungen."),
    FESTIVAL("festival", "Festival", "Vom Warm Up bis zum Heimweg."),
    MEDIEVAL("medieval", "Mittelalter", "Altdeutsche Trinkbegriffe."),
    EMOJI("emoji", "Emoji", "Mit Farbkreisen auf einen Blick.");

    /** What the picker shows as a sample: always the TIPSY band. */
    val previewLabel: String get() = label(BacStatus.TIPSY)

    fun label(status: BacStatus): String {
        val i = status.level
        return LABELS.getValue(this)[i]
    }

    companion object {
        fun from(raw: String): StatusSkin = entries.firstOrNull { it.raw == raw } ?: STANDARD

        // Order per row is sober, tipsy, drunk, careful, danger.
        private val LABELS: Map<StatusSkin, List<String>> = mapOf(
            STANDARD to listOf(
                "Nüchtern", "Leicht beschwipst", "Beschwipst", "Fahruntüchtig", "Gefährlich"
            ),
            // User-requested names, exactly as specified.
            NORMAL to listOf(
                "Garnicht Drunk", "Minimal Drunk", "Bisschen Drunk", "Komplett Drunk", "Felipe"
            ),
            // Natural everyday German youth language, not cringe.
            YOUTH to listOf(
                "Clean", "Leicht angeheitert", "Angemacht", "Durch den Wind", "Komplett weg"
            ),
            CHILL to listOf(
                "Fit", "Leicht angeheitert", "Gut drauf", "Langsam machen", "Zu viel"
            ),
            SAILOR to listOf(
                "Klar Schiff", "Leichter Seegang", "Volle Fahrt", "Stürmisch", "Mann über Bord"
            ),
            FORMAL to listOf(
                "Alkoholfrei", "Gering", "Spürbar", "Erheblich", "Gefährlich"
            ),
            SCIENCE to listOf(
                "Basislinie", "Schwellenwert", "Euphorisch", "Sediert", "Toxisch"
            ),
            FESTIVAL to listOf(
                "Ankunft", "Warm Up", "Im Flow", "Volle Power", "Nach Hause"
            ),
            MEDIEVAL to listOf(
                "Klar", "Heiter", "Fröhlich", "Angetrunken", "Besoffen"
            ),
            EMOJI to listOf(
                "Fit 🟢", "Happy 🟡", "Woah 🟠", "Viel 🔴", "Stop 🚫"
            )
        )
    }
}
