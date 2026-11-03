package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import sl_link.SlLink
import sl_link.SlMessageBuilder

class DrawingProtocolTest {
    @After fun clearPolicy() = com.sinelynx.grindingrobot.core.model.state.DrawingTaskPolicy.clear()
    @Test fun processAndDrawingFieldsRoundTripWithoutCollision(){
        val polygon=SlLink.PolygonRegion.newBuilder().setRegionId("drawing:test").setSource("drawing").setSubtype("foundation")
            .setInflationM(.3f).setDrawingAction(SlLink.DrawingObstacleAction.DRAWING_IGNORE)
            .setProcess(SlLink.MapProcessSettings.newBuilder().setRunSpeed(.4f)).build()
        val reply=SlLink.MapRegionPointResponse.newBuilder().setMapId("map").addObstacleRegions(polygon)
            .setDrawingLayer(SlLink.DrawingObstacleLayerInfo.newBuilder().setVersion(3).setStatus(SlLink.DrawingLayerStatus.DRAWING_STALE)).build()
        val parsed=parseMapRegionPoints(SlLink.MapRegionPointResponse.parseFrom(reply.toByteArray()))
        assertEquals("drawing",parsed.obstacleRegions.single().source)
        assertEquals(.3f,parsed.obstacleRegions.single().inflationM,1e-6f)
        assertEquals(.4f,parsed.obstacleRegions.single().processSettings!!.runSpeed,1e-6f)
        assertEquals(1,parsed.obstacleRegions.single().drawingAction)
        assertTrue(parsed.drawingLayer!!.stale)
        assertEquals(10,SlLink.PolygonRegion.PROCESS_FIELD_NUMBER)
    }
    @Test fun pointPlanCarriesDraftThroughExistingRequestBuilder(){
        val request=buildPathPointPlanRequest(mapId="map",drawingDraftId="draft",drawingDraftVersion=7)
        val result=SlLink.PathPointPlanRequest.parseFrom(request.toByteArray())
        assertEquals("draft",result.drawingDraftId);assertEquals(7,result.drawingDraftVersion);assertEquals("",result.taskId)
    }
    @Test fun drawingUsesDataChannelAndBoundedFrame(){
        val request=SlLink.DrawingDraftRequest.newBuilder().setMapId("map").setRequestId("request").build()
        val frame=SlMessageBuilder.buildDrawingDraftRequestRaw(request.toByteArray(),0x10u)
        assertEquals(0x053A,ChannelRouting.id(frame));assertFalse(ChannelRouting.control(frame))
    }
    @Test(expected=IllegalArgumentException::class) fun oversizeDrawingFrameRejected(){SlMessageBuilder.buildDrawingDraftRequestRaw(ByteArray(65536),0x10u)}
    @Test fun planEnvelopePreservesDrawingVersions(){
        val response=SlLink.PathPointPlanResponse.newBuilder().setDrawingDraftVersion(2).setDrawingLayerVersion(9).build()
        val payload=TaskPathJsonParser.emptyPayload(response)
        assertEquals(2,payload.drawingDraftVersion);assertEquals(9,payload.drawingLayerVersion)
    }
}
