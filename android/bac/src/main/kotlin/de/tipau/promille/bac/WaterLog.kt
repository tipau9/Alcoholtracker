package de.tipau.promille.bac

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Glasses of water the user logged, keyed by LOGICAL day. Mirrors
 * Services/WaterLog.swift, including the key format: HistorySyncService backs the
 * table up to Supabase, so an Android row has to be readable by iOS and back.
 *
 * Storage is injected so this stays a plain JVM class.
 */
class WaterLog(private val store: Store) {

    /** Persists the whole table at once, exactly like the iOS JSON blob does. */
    interface Store {
        fun load(): Map<String, Int>
        fun save(entries: Map<String, Int>)
    }

    val allEntries: Map<String, Int> get() = store.load()

    /**
     * Null means "never logged", which lets the caller fall back to the estimation
     * heuristic instead of reading a silent zero as a real zero.
     */
    fun loggedGlasses(day: LocalDate): Int? = store.load()[key(day)]

    fun loggedGlasses(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Int? =
        loggedGlasses(LogicalDay.dateOf(epochSeconds, zone))

    fun glassesToday(nowEpochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        loggedGlasses(nowEpochSeconds, zone) ?: 0

    fun addGlassToday(nowEpochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()) {
        val k = key(LogicalDay.dateOf(nowEpochSeconds, zone))
        val entries = store.load().toMutableMap()
        entries[k] = (entries[k] ?: 0) + 1
        store.save(entries)
    }

    fun removeGlassToday(nowEpochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()) {
        val k = key(LogicalDay.dateOf(nowEpochSeconds, zone))
        val entries = store.load().toMutableMap()
        val next = (entries[k] ?: 0) - 1
        entries[k] = if (next < 0) 0 else next
        store.save(entries)
    }

    /**
     * Keeps the HIGHER count per day, so restoring a backup can never lower a
     * value the user has already logged today on this device.
     */
    fun merge(other: Map<String, Int>) {
        val entries = store.load().toMutableMap()
        for ((day, count) in other) {
            val existing = entries[day] ?: 0
            if (count > existing) entries[day] = count
        }
        store.save(entries)
    }

    fun clear() = store.save(emptyMap())

    companion object {
        const val GLASS_ML = 250.0

        /** Key of the backing blob, shared with iOS. */
        const val STORAGE_KEY = "waterLog_v1"

        /** Wire format of a day key. Locale.ROOT so digits never localise. */
        fun key(day: LocalDate): String =
            String.format(Locale.ROOT, "%04d-%02d-%02d", day.year, day.monthValue, day.dayOfMonth)

        fun inMemory(): WaterLog = WaterLog(object : Store {
            private var entries: Map<String, Int> = emptyMap()
            override fun load(): Map<String, Int> = entries
            override fun save(entries: Map<String, Int>) { this.entries = entries }
        })

        /** Never records anything. For previews and pure math. */
        fun disabled(): WaterLog = WaterLog(object : Store {
            override fun load(): Map<String, Int> = emptyMap()
            override fun save(entries: Map<String, Int>) = Unit
        })
    }
}
