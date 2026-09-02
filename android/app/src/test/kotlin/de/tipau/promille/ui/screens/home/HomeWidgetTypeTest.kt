package de.tipau.promille.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetTypeTest {

    /** The one that used to fail: an empty set came back as every widget. */
    @Test
    fun `turning every widget off survives a round trip`() {
        val raw = HomeWidgetType.serialize(emptySet())
        assertEquals(HomeWidgetType.EXPLICIT_NONE_RAW, raw)
        assertEquals(emptySet<HomeWidgetType>(), HomeWidgetType.parseActiveWidgets(raw))
    }

    /** Blank is the column default (Entities.kt:46) and means a fresh profile. */
    @Test
    fun `blank means everything is on`() {
        assertEquals(HomeWidgetType.entries.toSet(), HomeWidgetType.parseActiveWidgets(""))
    }

    /** iOS-only tokens must survive a toggle, or syncing back deletes them there. */
    @Test
    fun `unknown tokens ride along`() {
        val raw = "timeToLimit,water,hangover"
        val active = HomeWidgetType.parseActiveWidgets(raw)
        val next = HomeWidgetType.serialize(active - HomeWidgetType.WATER, HomeWidgetType.foreignTokens(raw))
        assertTrue(next.split(",").contains("hangover"))
        assertEquals(setOf(HomeWidgetType.TIME_TO_LIMIT), HomeWidgetType.parseActiveWidgets(next))
    }

    /** Turning every Android widget off keeps iOS's, rather than writing the sentinel. */
    @Test
    fun `unknown tokens outlive an empty selection`() {
        val foreign = HomeWidgetType.foreignTokens("water,hangover")
        assertEquals("hangover", HomeWidgetType.serialize(emptySet(), foreign))
        assertEquals(emptySet<HomeWidgetType>(), HomeWidgetType.parseActiveWidgets("hangover"))
    }

    @Test
    fun `a chosen subset survives a round trip`() {
        val chosen = setOf(HomeWidgetType.WATER, HomeWidgetType.DAY_STATS)
        assertEquals(chosen, HomeWidgetType.parseActiveWidgets(HomeWidgetType.serialize(chosen)))
    }
}
