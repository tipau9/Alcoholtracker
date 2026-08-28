package de.tipau.promille.bac

import kotlin.math.max
import kotlin.math.min

/**
 * Learns per-category drinking pace from drinks the user explicitly marks as
 * finished before the automatic estimate. The memory only shortens Auto after a
 * repeated pattern, so one accidental tap does not distort future drinks.
 * Mirrors Services/DrinkPaceMemory.swift.
 *
 * Storage is injected so this stays a plain JVM class: Android backs it with
 * SharedPreferences, tests with an in-memory map.
 */
class DrinkPaceMemory(private val store: Store) {

    data class Sample(val count: Int, val ratioEMA: Double)

    /** Persists the whole table at once, exactly like the iOS JSON blob does. */
    interface Store {
        fun load(): Map<String, Sample>
        fun save(samples: Map<String, Sample>)
    }

    fun recordEarlyFinish(category: DrinkCategory, baseEstimate: Double, actualMinutes: Double) {
        if (baseEstimate <= 1 || actualMinutes < 1) return
        if (actualMinutes > baseEstimate * EARLY_FINISH_RATIO) return
        if (baseEstimate - actualMinutes < EARLY_FINISH_MINUTES) return

        val ratio = min(1.0, max(MINIMUM_LEARNED_RATIO, actualMinutes / baseEstimate))
        val samples = store.load().toMutableMap()
        val existing = samples[category.raw]
        samples[category.raw] = if (existing == null) {
            Sample(count = 1, ratioEMA = ratio)
        } else {
            Sample(
                count = existing.count + 1,
                ratioEMA = existing.ratioEMA * (1 - SMOOTHING) + ratio * SMOOTHING
            )
        }
        store.save(samples)
    }

    fun adjustedEstimate(category: DrinkCategory, baseMinutes: Double): Double {
        if (baseMinutes <= 1) return baseMinutes
        val sample = store.load()[category.raw] ?: return baseMinutes
        if (sample.count < MINIMUM_SAMPLES) return baseMinutes
        val learned = min(1.0, max(MINIMUM_LEARNED_RATIO, sample.ratioEMA))
        return max(1.0, baseMinutes * learned)
    }

    fun forget(category: DrinkCategory) {
        val samples = store.load().toMutableMap()
        samples.remove(category.raw)
        store.save(samples)
    }

    companion object {
        const val MINIMUM_SAMPLES = 3
        const val EARLY_FINISH_RATIO = 0.75
        const val EARLY_FINISH_MINUTES = 1.0
        const val SMOOTHING = 0.35
        const val MINIMUM_LEARNED_RATIO = 0.4

        /** Never learns, always returns the base estimate. For previews and pure math. */
        fun disabled(): DrinkPaceMemory = DrinkPaceMemory(object : Store {
            override fun load(): Map<String, Sample> = emptyMap()
            override fun save(samples: Map<String, Sample>) = Unit
        })

        fun inMemory(): DrinkPaceMemory = DrinkPaceMemory(object : Store {
            private var samples: Map<String, Sample> = emptyMap()
            override fun load(): Map<String, Sample> = samples
            override fun save(samples: Map<String, Sample>) { this.samples = samples }
        })
    }
}
