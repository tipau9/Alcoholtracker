package de.tipau.promille.data

import android.content.Context
import de.tipau.promille.bac.WaterLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * SharedPreferences backing for [WaterLog]. One JSON blob under the key iOS uses,
 * because HistorySyncService ships the whole table to Supabase and back.
 */
class WaterLogStore(context: Context) : WaterLog.Store {

    private val prefs = context.getSharedPreferences("water_log", Context.MODE_PRIVATE)
    private val json = Json
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    override fun load(): Map<String, Int> {
        val raw = prefs.getString(WaterLog.STORAGE_KEY, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    override fun save(entries: Map<String, Int>) {
        prefs.edit()
            .putString(WaterLog.STORAGE_KEY, json.encodeToString(serializer, entries))
            .apply()
    }
}
