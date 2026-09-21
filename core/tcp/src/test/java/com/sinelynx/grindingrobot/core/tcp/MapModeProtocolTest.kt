package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class MapModeProtocolTest {

    @Test
    fun `build mapping mode request uses 0x0512 and expected payload`() {
        val request = SlLink.MapModeRequest.newBuilder()
            .setMode(SlLink.MapModeType.MAP_MODE_MAPPING)
            .setEnabled(true)
            .setMapKind(0)
            .build()

        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildMapModeRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x0512, frame.msgId.toInt())
        assertEquals(0x10, frame.dstId.toInt())
        assertEquals(0x08, frame.compId.toInt())

        val decoded = SlLink.MapModeRequest.parseFrom(frame.payload)
        assertEquals(SlLink.MapModeType.MAP_MODE_MAPPING, decoded.mode)
        assertTrue(decoded.enabled)
        assertEquals(0, decoded.mapKind)
    }
}
