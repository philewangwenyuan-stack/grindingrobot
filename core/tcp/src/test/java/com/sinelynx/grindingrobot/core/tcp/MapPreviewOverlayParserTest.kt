package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保护仍使用旧预览接口页面的 JSON 区域解析约定，不是新 0x052F Protobuf 区域响应测试。
 * 混入坏字段和不足三个有效顶点的区域，确认局部坏数据不拖累其余区域；整体坏 JSON 则返回空集合。
 * snake_case/camelCase 兼容与禁用区域过滤均属于现有旧接口行为。
 */
class MapPreviewOverlayParserTest {
    @Test
    fun parsesAllRegionTypesAndSkipsInvalidItems() {
        val parsed = MapPreviewOverlayParser.parseOrThrow(
            """
            {
              "regions": {
                "work_regions": [
                  {"region_id":"work_1","name":"wr_001","enabled":true,"points":[{"x":0,"y":0},{"x":2,"y":0},{"x":2,"y":2}]},
                  {"region_id":"work_2","name":"wr_002","points":[{"x":3,"y":0},{"x":5,"y":0},{"x":5,"y":2}]},
                  {"region_id":"disabled","enabled":false,"points":[{"x":0,"y":0},{"x":1,"y":0},{"x":1,"y":1}]}
                ],
                "obstacle_regions": [
                  {"region_id":"obstacle_1","points":[{"x":0.2,"y":0.3},{"x":0.5,"y":0.3},{"x":0.5,"y":0.7},{"x":0.2,"y":0.7}]},
                  {"region_id":{},"enabled":[],"points":[{"x":{},"y":0},{"x":0,"y":0},{"x":1,"y":0}]},
                  {"region_id":"obstacle_2","points":[{"x":1.2,"y":1.3},{"x":1.5,"y":1.3},{"x":1.5,"y":1.7},{"x":1.2,"y":1.7}]},
                  {"region_id":"bad","points":[{"x":0,"y":0},{"x":"bad","y":1},{"x":1,"y":1}]}
                ],
                "eraseRegions": [
                  {"name":"erase_1","points":[{"x":-1,"y":-1},{"x":1,"y":-1},{"x":1,"y":1},{"x":-1,"y":1}]},
                  {"name":"erase_2","points":[{"x":-2,"y":-2},{"x":2,"y":-2},{"x":2,"y":2}]},
                  {"name":"erase_3","points":[{"x":-3,"y":-3},{"x":3,"y":-3},{"x":3,"y":3}]},
                  {"name":"erase_4","points":[{"x":-4,"y":-4},{"x":4,"y":-4},{"x":4,"y":4}]},
                  {"name":"erase_5","points":[{"x":-5,"y":-5},{"x":5,"y":-5},{"x":5,"y":5}]}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(listOf("work_1", "work_2"), parsed.workRegions.map { it.regionId })
        assertEquals(listOf("obstacle_1", "obstacle_2"), parsed.obstacleRegions.map { it.regionId })
        assertEquals(5, parsed.eraseRegions.size)
        assertEquals("erase_1", parsed.eraseRegions.first().regionId)
        assertEquals(4, parsed.eraseRegions.first().points.size)
    }

    @Test
    fun malformedOrEmptyJsonReturnsEmptyRegions() {
        assertEquals(MapPreviewOverlayRegions(), MapPreviewOverlayParser.parse(""))
        val malformed = MapPreviewOverlayParser.parse("{not-json")
        assertTrue(malformed.workRegions.isEmpty())
        assertTrue(malformed.obstacleRegions.isEmpty())
        assertTrue(malformed.eraseRegions.isEmpty())
    }
}
