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

    @Test
    fun localizationResponseCarriesActiveMapAndLifecycle() {
        val response = SlLink.MapModeResponse.newBuilder()
            .setResult(SlLink.ResultCode.RESULT_SUCCESS)
            .setMode(SlLink.MapModeType.MAP_MODE_LOCALIZATION)
            .setEnabled(true)
            .setLifecycleState("LOCALIZING")
            .setMapId("map-002")
            .setMapRevision("c".repeat(64))
            .build()

        val decoded = SlLink.MapModeResponse.parseFrom(response.toByteArray())
        assertEquals(SlLink.MapModeType.MAP_MODE_LOCALIZATION, decoded.mode)
        assertTrue(decoded.enabled)
        assertEquals("LOCALIZING", decoded.lifecycleState)
        assertEquals("map-002", decoded.mapId)
        assertEquals("c".repeat(64), decoded.mapRevision)
    }
}
