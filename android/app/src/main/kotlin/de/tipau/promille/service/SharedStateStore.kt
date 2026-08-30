package de.tipau.promille.service

import android.content.Context

/**
 * Port of iOS SharedStateStore. On iOS the App Group UserDefaults is the only
 * channel between the app and the widget process; on Android the widget lives
 * in-process, but the same snapshot is still needed so the widget shows the
 * last known BAC on reboot / re-add instead of resetting to 0,00 - those
 * events fire AppWidgetProvider.onUpdate with no app process involved to
 * recompute anything.
 */
object SharedStateStore {
    private const val PREFS = "shared_state"
    private const val KEY_BAC = "bac"
    private const val KEY_STATUS_TEXT = "status_text"
    private const val KEY_STATUS_COLOR = "status_color"

    data class Snapshot(val bac: Double, val statusText: String, val statusColor: Int)

    fun save(context: Context, bac: Double, statusText: String, statusColor: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_BAC, bac.toFloat())
            .putString(KEY_STATUS_TEXT, statusText)
            .putInt(KEY_STATUS_COLOR, statusColor)
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_BAC)) return null
        return Snapshot(
            bac = prefs.getFloat(KEY_BAC, 0f).toDouble(),
            statusText = prefs.getString(KEY_STATUS_TEXT, null) ?: return null,
            statusColor = prefs.getInt(KEY_STATUS_COLOR, 0)
        )
    }
}
