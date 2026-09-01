package de.tipau.promille.ui.screens.home

import org.junit.Assert.assertEquals
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

    @Test
    fun `a chosen subset survives a round trip`() {
        val chosen = setOf(HomeWidgetType.WATER, HomeWidgetType.DAY_STATS)
        assertEquals(chosen, HomeWidgetType.parseActiveWidgets(HomeWidgetType.serialize(chosen)))
    }
}
