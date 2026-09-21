package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlLink

/**
 * Step3 区域配置协议回归测试：方向四值经 Protobuf 编解码后原样保留，包括负号。
 * 小写输入仅规范大小写，未知方向回退 X；方向字段是字符串而非旋转角或枚举序号。
 * 更新/删除依赖稳定 regionId，显示编号变化不能导致误建或误删区域。
 */
class WorkspaceMapEditCommandTest {

    @Test
    fun workspaceDirection_preservesAllFourValuesThroughProtobufSerialization() {
        listOf("X", "-X", "Y", "-Y").forEach { direction ->
            val response = SlLink.MapEditCommand.parseFrom(workspaceCommand(direction).toByteArray())
            assertEquals(direction, response.region.globalDirection)
        }
    }

    @Test
    fun workspaceDirection_normalizesCaseWithoutRemovingNegativeSign() {
        mapOf("x" to "X", "-x" to "-X", "y" to "Y", "-y" to "-Y",
            "" to "X", "unknown" to "X").forEach { (input, expected) ->
            assertEquals(expected, workspaceCommand(input).region.globalDirection)
        }
    }

    private fun workspaceCommand(direction: String) = buildWorkspaceUpsertCommand(
        editId = "edit", mapId = "map", regionId = "work", areaCode = "001",
        points = listOf(0f to 0f, 1f to 0f, 1f to 1f), startPose = null, endPose = null,
        globalDirection = direction
    )

    @Test
    fun workspaceUpsert_preservesStableRegionIdWhenAreaCodeChanges() {
        val command = buildWorkspaceUpsertCommand(
            editId = "edit_1",
            mapId = "map_1",
            regionId = "work_region_stable",
            areaCode = "009",
            points = listOf(0f to 0f, 2f to 0f, 2f to 1f, 0f to 1f),
            startPose = 0.5f to 0.5f,
            endPose = 1.5f to 0.5f,
            globalDirection = "Y"
        )

        assertEquals(SlLink.MapEditOperation.MAP_EDIT_OP_UPSERT_WORK_REGION, command.operation)
        assertEquals("work_region_stable", command.region.regionId)
        assertEquals("wr_009", command.region.name)
        assertEquals("Y", command.region.globalDirection)
        assertEquals(4, command.region.pointsCount)
        assertTrue(command.hasStartPose())
        assertTrue(command.hasEndPose())
    }

    @Test
    fun obstacleDelete_targetsStableObstacleRegionId() {
        val command = buildDeleteRegionCommand(
            editId = "edit_2",
            mapId = "map_1",
            regionId = "obstacle_region_stable",
            regionType = SlLink.RegionType.REGION_TYPE_OBSTACLE
        )

        assertEquals(SlLink.MapEditOperation.MAP_EDIT_OP_DELETE_REGION, command.operation)
        assertEquals("obstacle_region_stable", command.targetRegionId)
        assertEquals(SlLink.RegionType.REGION_TYPE_OBSTACLE, command.targetRegionType)
    }
}
