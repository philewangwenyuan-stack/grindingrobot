package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class MapProcessSettingsTest {
    @Test fun recipeSaveIsMapBoundAndContainsNoActuatorSettings() {
        val recipe = SlLink.MapProcessSettings.newBuilder().setRunSpeed(.03f).setDiscSpeedRpm(925).build()
        val request = buildMapProcessWriteRequest(recipe, "map-a", "region-a")
        assertTrue(request.hasProcess())
        assertFalse(request.hasChassis())
        assertFalse(request.hasMap())
        assertEquals("map-a", request.processMapId)
        assertEquals("region-a", request.processRegionId)
        assertEquals(recipe, request.process)
    }

    @Test fun oldFirmwareSuccessWithoutRecipeEchoCannotConfirmSave() {
        val response = SlLink.SettingsWriteResponse.newBuilder().setResult(SlLink.ResultCode.RESULT_SUCCESS)
        assertNotEquals(SlLink.ResultCode.RESULT_SUCCESS_VALUE, mapProcessReply(response.build()).resultValue)
        response.setProcess(SlLink.MapProcessSettings.newBuilder().setRunSpeed(.03f).build()).setProcessRegionId("region-a")
        assertEquals(SlLink.ResultCode.RESULT_SUCCESS_VALUE, mapProcessReply(response.build()).resultValue)
        response.setResult(SlLink.ResultCode.RESULT_FAILED).setMessage("disk full")
        assertEquals("disk full", mapProcessReply(response.build()).message)
        assertEquals(SlLink.ResultCode.RESULT_FAILED_VALUE, mapProcessReply(response.build()).resultValue)
    }
    @Test fun regionQueryKeepsDistinctRecipesAndDoesNotFillMissingRecipesFromNeighbors() {
        val response = SlLink.MapRegionPointResponse.newBuilder().setMapId("map-a")
            .setResult(SlLink.ResultCode.RESULT_SUCCESS)
        for ((id, speed) in listOf("region-a" to .03f, "region-b" to .07f)) {
            response.addWorkRegions(SlLink.WorkRegionPointInfo.newBuilder().setRegion(
                SlLink.PolygonRegion.newBuilder().setRegionId(id).setEnabled(true)
                    .setProcess(SlLink.MapProcessSettings.newBuilder().setRunSpeed(speed).setDiscSpeedRpm(if (id == "region-a") 925 else 1200))
            ))
        }
        response.addWorkRegions(SlLink.WorkRegionPointInfo.newBuilder().setRegion(
            SlLink.PolygonRegion.newBuilder().setRegionId("legacy").setEnabled(true)))
        val regions = parseMapRegionPoints(response.build()).workRegions
        assertEquals(.03f, regions[0].region.processSettings!!.runSpeed, 0f)
        assertEquals(925, regions[0].region.processSettings!!.discSpeedRpm)
        assertEquals(.07f, regions[1].region.processSettings!!.runSpeed, 0f)
        assertEquals(1200, regions[1].region.processSettings!!.discSpeedRpm)
        assertNull(regions[2].region.processSettings)
    }

}
