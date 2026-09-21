package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.core.model.state.TaskPolygonPointConfig
import com.sinelynx.grindingrobot.core.model.state.TaskRegionRepeatConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlLink

/**
 * 开始研磨流程把本轮临时禁区写入 TaskConfig.obstacle_regions，而不是写入地图公共禁区。
 * 用 Protobuf 往返编解码确认稳定 ID、类型、启用/闭合标记、优先级和世界坐标顶点不会丢失。
 * 只验证序列化，不提交配置，也不改变禁区的持久化语义。
 */
class TaskConfigBuilderTest {
    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        buildTaskConfig("task", " \t\n", "map", emptyList(), emptyList())
    }

    @Test
    fun responseNameSurvivesDecodingAndPayloadMapping() {
        val response = SlLink.TaskConfigResponse.newBuilder()
            .setTaskId("task").setTaskName("车间研磨任务")
            .setResult(SlLink.ResultCode.RESULT_SUCCESS).build()
        val payload = SlLink.TaskConfigResponse.parseFrom(response.toByteArray()).toPayload()
        assertEquals("车间研磨任务", payload.taskName)
        assertEquals("task", payload.taskId)
        assertTrue(payload.isSuccess)
    }

    @Test
    fun obstacleRegions_areSerializedWithStructuredMetadataAndPoints() {
        val taskConfig = buildTaskConfig(
            taskId = "task_001",
            taskName = "  车间研磨任务  ",
            mapId = "map_001",
            regionRepeats = listOf(TaskRegionRepeatConfig("work_001", 2)),
            obstacles = listOf(
                TaskObstacleRegionConfig(
                    regionId = "obstacle_region_12345",
                    name = "obstacle_region_12345",
                    points = listOf(
                        TaskPolygonPointConfig(2f, 1f),
                        TaskPolygonPointConfig(3.5f, 1f),
                        TaskPolygonPointConfig(3.5f, 2.5f),
                        TaskPolygonPointConfig(2f, 2.5f)
                    )
                )
            )
        )

        val decoded = SlLink.TaskConfig.parseFrom(taskConfig.toByteArray())
        val obstacle = decoded.obstacleRegionsList.single()

        assertEquals("task_001", decoded.taskId)
        assertEquals("车间研磨任务", decoded.taskName)
        assertEquals("map_001", decoded.mapId)
        assertEquals("obstacle_region_12345", obstacle.regionId)
        assertEquals(obstacle.regionId, obstacle.name)
        assertEquals(SlLink.RegionType.REGION_TYPE_OBSTACLE, obstacle.regionType)
        assertTrue(obstacle.enabled)
        assertTrue(obstacle.closed)
        assertEquals(10, obstacle.priority)
        assertEquals(4, obstacle.pointsCount)
        assertEquals(2f, obstacle.pointsList[0].x, 0.0001f)
        assertEquals(1f, obstacle.pointsList[0].y, 0.0001f)
    }
}
