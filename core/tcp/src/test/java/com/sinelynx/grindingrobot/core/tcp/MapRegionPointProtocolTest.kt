package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

/**
 * 验证 0x052E 请求构帧及 0x052F 响应的数据映射，不连接 TCP 或机器人。
 * 空 map_id 保留当前地图语义；姿态和裁剪区以 available 标记为准，不能仅凭子消息存在判为有效。
 * 裁剪区虽未参与页面可视化，仍须完整保留在响应模型中。
 */
class MapRegionPointProtocolTest {
    @Test fun requestSupportsCurrentMapAndNamedMap() {
        listOf("", "map_001").forEach { mapId ->
            val request = SlLink.MapRegionPointRequest.newBuilder().setMapId(mapId).build()
            val frame = SlFrameParser().parse(SlMessageBuilder.buildMapRegionPointRequestRaw(request.toByteArray(), 0x10u)).single()
            assertEquals(0x052E, frame.msgId.toInt())
            assertEquals(mapId, SlLink.MapRegionPointRequest.parseFrom(frame.payload).mapId)
        }
    }

    @Test fun parsesEveryRegionAndHonorsPoseAndCropAvailability() {
        val polygon = SlLink.PolygonRegion.newBuilder().setRegionId("r").setName("区域").setEnabled(true)
            .addPoints(SlLink.PolygonPoint.newBuilder().setX(1f).setY(2f)).build()
        val pose = SlLink.Pose2D.newBuilder().setX(3f).setY(4f).setHeadingDeg(5f).build()
        // 故意提供 endPose 但标记不可用，避免把默认/残留姿态当作有效终点。
        val work = SlLink.WorkRegionPointInfo.newBuilder().setRegion(polygon)
            .setStartPoseAvailable(true).setStartPose(pose).setEndPoseAvailable(false).setEndPose(pose)
        val response = SlLink.MapRegionPointResponse.newBuilder().setMapId("map_001").setMapVersion(8)
            .addWorkRegions(work).addObstacleRegions(polygon).addEraseRegions(polygon)
            .setCropRegionAvailable(true).setCropRegion(polygon).build()
        val parsed = parseMapRegionPoints(SlLink.MapRegionPointResponse.parseFrom(response.toByteArray()))
        assertTrue(parsed.isSuccess)
        assertEquals(8, parsed.mapVersion)
        assertEquals(3f, parsed.workRegions.single().startPose!!.x, 0f)
        assertNull(parsed.workRegions.single().endPose)
        assertEquals(1, parsed.obstacleRegions.size)
        assertEquals(1, parsed.eraseRegions.size)
        assertNotNull(parsed.cropRegion)
        assertNull(parseMapRegionPoints(response.toBuilder().setCropRegionAvailable(false).build()).cropRegion)
    }
}
