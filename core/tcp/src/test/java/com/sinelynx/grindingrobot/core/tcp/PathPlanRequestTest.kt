package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

/**
 * 旧版规划图用于测试弹窗对比：0x050E 返回图片，returnPathChunks=false 避免附带路径分片。
 * 默认构造参数保持兼容；request_id 用于弹窗独立关联，不借用新规划请求的结果。
 * 只构造并解码报文，不实际发送；接口可能进行设备端规划，但不是机器人执行命令。
 */
class PathPlanRequestTest {
    @Test fun existingCallsKeepTheirDefaultFields() {
        val expected = SlLink.PathPlanRequest.newBuilder().setReturnPathChunks(true)
            .setMaxChunkSize(2048).setMapId("map").setTaskId("task").build()
        assertEquals(expected, buildPathPlanRequest("map", "task"))
        assertEquals("", buildPathPlanRequest().requestId)
        assertEquals("", buildPathPlanRequest().taskId)
        assertEquals(1, buildPathPlanRequest(maxChunkSize = 0).maxChunkSize)
    }

    @Test fun previewUsesOldRequestWithoutPathChunksOrExecutionCommands() {
        val request = buildPathPlanRequest(mapId = "map", returnPathChunks = false, requestId = "preview-1")
        val frame = SlFrameParser().parse(SlMessageBuilder.buildPathPlanRequestRaw(request.toByteArray(), 0x10u)).single()
        assertEquals(0x050E, frame.msgId.toInt())
        val decoded = SlLink.PathPlanRequest.parseFrom(frame.payload)
        assertFalse(decoded.returnPathChunks)
        assertFalse(decoded.forceReplan)
        assertEquals("map", decoded.mapId)
        assertEquals("", decoded.taskId)
        assertEquals("preview-1", decoded.requestId)
        assertEquals(2048, decoded.maxChunkSize)
    }
}
