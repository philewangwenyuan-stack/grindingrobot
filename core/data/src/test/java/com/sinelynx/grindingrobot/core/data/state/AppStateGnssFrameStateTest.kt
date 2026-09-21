package com.sinelynx.grindingrobot.core.data.state

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStateGnssFrameStateTest {

    @Test
    fun `AppState exposes whole-frame GNSS state flow`() {
        val typeName = AppState::class.java
            .getMethod("getGnssFrame")
            .genericReturnType
            .typeName

        assertEquals(
            "kotlinx.coroutines.flow.StateFlow<com.sinelynx.grindingrobot.core.model.gnss.GnssFrame>",
            typeName
        )
    }
}
