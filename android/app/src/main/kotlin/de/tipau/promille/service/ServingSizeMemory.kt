package de.tipau.promille.service

import android.content.Context

/**
 * Port of iOS ServingSizeMemory. Remembers the serving size (volume in ml) the
 * user last chose for a given drink template, so the amount sheet pre-selects
 * that size next time instead of always defaulting to the template's nominal
 * volume. Keyed by templateID, stored in SharedPreferences. A preset is
 * matched back by its volume, so saving the chosen volume is enough to
 * restore the chosen format.
 */
object ServingSizeMemory {
    private const val PREFS = "serving_size_memory_v1"

    /** The user's last chosen volume (ml) for this template, or null if never set. */
    fun volume(context: Context, templateID: String): Double? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(templateID)) return null
        val v = prefs.getFloat(templateID, 0f).toDouble()
        return if (v > 0) v else null
    }

    /** Persists the chosen volume so it becomes the default next time. */
    fun save(context: Context, templateID: String, volume: Double) {
        if (volume <= 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(templateID, volume.toFloat())
            .apply()
    }
}
