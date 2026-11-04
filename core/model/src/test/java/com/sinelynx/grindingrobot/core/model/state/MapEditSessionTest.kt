package com.sinelynx.grindingrobot.core.model.state

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class MapEditSessionTest {
    @After fun clear() = MapEditSession.adopt(null)
    @Test fun bindingIsExplicitlyScopedAndDoesNotResolveLiveMap() {
        val binding = MapEditBinding("draft_a", "base", 3)
        MapEditSession.adopt(binding)
        assertEquals(binding, MapEditSession.forMap("draft_a"))
        assertNull(MapEditSession.forMap(null))
        assertNull(MapEditSession.forMap("LIVE_MAP"))
        assertNull(MapEditSession.forMap("draft_b"))
    }
    @Test fun lateAcknowledgementCannotDecreaseTheVersion() {
        MapEditSession.adopt(MapEditBinding("draft_a", "base", 3))
        MapEditSession.edited("draft_a", "base", 4); MapEditSession.edited("draft_a", "base", 2)
        assertEquals(4, MapEditSession.binding!!.editVersion)
    }
    @Test fun acknowledgementsFromAnotherMapOrBaseCannotAdvanceTheCurrentSession() {
        MapEditSession.adopt(MapEditBinding("draft_b", "new", 0))
        MapEditSession.edited("draft_a", "old", 99)
        MapEditSession.edited("draft_b", "old", 99)
        assertEquals(0, MapEditSession.binding!!.editVersion)
    }
    @Test fun resumeCanAuthoritativelyReplaceTheBinding() {
        MapEditSession.adopt(MapEditBinding("draft_a", "old", 8))
        MapEditSession.adopt(MapEditBinding("draft_b", "new", 0))
        assertEquals(0, MapEditSession.binding!!.editVersion)
        assertNull(MapEditSession.forMap("draft_a"))
    }
}
