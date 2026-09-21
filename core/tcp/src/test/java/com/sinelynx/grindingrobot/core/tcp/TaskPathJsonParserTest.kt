package com.sinelynx.grindingrobot.core.tcp

import com.google.protobuf.ByteString
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

/**
 * 宽松解析契约：尽量保留实际 x/y，辅助字段或统计缺失不能拒绝整条路径。
 * 无效坐标须占据原数组位置并标记 NaN，留给绘制层断开，不能过滤后连接两侧的点。
 * 关联只信外层 request_id；业务元数据宽松读取，空/坏 JSON 也须形成可发布的本轮空结果。
 * 含中文 UTF-8 跨字节边界用例，防止逐包提前解码导致乱码或路径丢失。
 */
class TaskPathJsonParserTest {
    @After fun clear() { TaskPathStream.resetAll() }

    // 稀疏 index 和明显不同的 row/col，保护按实际 x/y 绘制、不强制连续索引的约定。
    private val geometry = """{"points":[
        {"index":10,"x":1,"y":2,"row":999,"col":999,"path_scope":"between_regions"},
        {"index":20,"x":2,"y":3,"path_scope":"between_regions"},
        {"index":30,"x":3,"y":4,"path_scope":"within_region"},
        {"index":40,"x":4,"y":5,"path_scope":"within_region"}],
        "segments":[
        {"start_point_index":10,"end_point_index":20,"path_scope":"between_regions","from_region_id":"a","to_region_id":"b"},
        {"start_point_index":30,"end_point_index":40,"path_scope":"within_region"}]}"""

    private fun envelope(requestId: String = "request", version: Int = 14) =
        SlLink.PathPointPlanResponse.newBuilder().setRequestId(requestId).setPathVersion(version).build()

    private fun parse(json: String) = TaskPathJsonParser.parse(json.encodeToByteArray(), envelope())

    @Test fun coordinatesAndSegmentsDoNotRequireAuxiliaryFields() {
        val result = parse(geometry)
        assertTrue(result.hasRenderablePoints)
        assertEquals(listOf(10, 20, 30, 40), result.points.map { it.index })
        assertEquals(1f, result.points.first().x, 0f)
        assertEquals("a", result.segments.first().fromRegionId)
        assertEquals("b", result.segments.first().toRegionId)
        assertNull(result.totalWorkAreaM2)
        assertNull(result.estimatedTimeS)
    }

    @Test fun statisticsAreIndependentOptionalValues() {
        val root = Json.parseToJsonElement(geometry).jsonObject
        val invalid = listOf(JsonNull, JsonPrimitive(""), JsonPrimitive("bad"), JsonPrimitive("NaN"),
            JsonPrimitive("1e1000"), JsonObject(emptyMap()), JsonArray(emptyList()))
        invalid.forEach { value ->
            val result = parse(JsonObject(root + mapOf(
                "total_work_area_m2" to value, "estimated_time_s" to JsonPrimitive(462.9f))).toString())
            assertNull(result.totalWorkAreaM2)
            assertEquals(462.9f, result.estimatedTimeS!!, 0.001f)
            assertTrue(result.hasRenderablePoints)
            val reverse = parse(JsonObject(root + mapOf(
                "total_work_area_m2" to JsonPrimitive(75), "estimated_time_s" to value)).toString())
            assertEquals(75f, reverse.totalWorkAreaM2!!, 0f)
            assertNull(reverse.estimatedTimeS)
            assertEquals(result.points, reverse.points)
        }
        val negative = parse("""{"points":[{"x":1,"y":2}],"total_work_area_m2":0,"estimated_time_s":-1}""")
        assertEquals(0f, negative.totalWorkAreaM2!!, 0f)
        assertEquals(-1f, negative.estimatedTimeS!!, 0f)
    }

    @Test fun mismatchedMetadataAndCountsDoNotRejectGeometry() {
        val root = Json.parseToJsonElement(geometry).jsonObject
        val result = parse(JsonObject(root + mapOf(
            "task_id" to JsonPrimitive("different"), "path_version" to JsonPrimitive(1),
            "frame_id" to JsonPrimitive("other"), "planned" to JsonPrimitive(false),
            "result" to JsonPrimitive("failed"), "path_point_count" to JsonPrimitive(999),
            "alignment_yaw" to JsonPrimitive(90))).toString())
        assertTrue(result.hasRenderablePoints)
        assertFalse(result.planned)
        assertEquals(4, result.points.size)
        assertEquals(1f, result.points[0].x, 0f)
    }

    @Test fun arrayOrderAndInvalidCoordinatesArePreservedAsBreaks() {
        val result = parse("""{"points":[{"index":20,"x":1,"y":2},null,
            {"index":10,"x":"bad","y":3},{"index":20,"x":4},
            {"x":"Infinity","y":4},{"index":-8,"x":7,"y":8}]}""")
        assertEquals(6, result.points.size)
        assertEquals(listOf(20, 1, 10, 20, 4, -8), result.points.map { it.index })
        assertTrue(result.points[1].x.isNaN())
        assertTrue(result.points[2].x.isNaN())
        assertTrue(result.points[3].y.isNaN())
        assertTrue(result.points[4].x.isNaN())
        assertTrue(result.hasRenderablePoints)
    }

    @Test fun malformedOrEmptyResponsesProduceEmptyResults() {
        listOf("", "{", "[]", """{"points":null}""", """{"points":[]}""",
            """{"planned":true,"points":[{"x":null,"y":2}]}""").forEach { json ->
            assertFalse(parse(json).hasRenderablePoints)
        }
        var logged = false
        val result = TaskPathJsonParser.parse(byteArrayOf(0xC3.toByte(), 0x28), envelope(version = 0)) { logged = true }
        assertTrue(logged)
        assertFalse(result.hasRenderablePoints)
    }

    @Test fun decodeFailureCanBeDeliveredImmediatelyWithoutReplacingCachedPath() = runBlocking {
        val success = parse(geometry)
        TaskPathStream.publish(success)
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { TaskPathStream.results.first() }
        }
        val failure = parse("{")
        TaskPathStream.publish(failure)
        assertEquals(failure, reply.await())
        assertEquals(success, TaskPathStream.pathsByTaskId.value[""])
    }

    @Test fun protobufChunksWithEmptyTaskIdAndSplitUtf8ProduceGeometry() {
        val bytes = geometry.dropLast(1).plus(""","message":"规划完成"}""").encodeToByteArray()
        // 切在首个中文字符编码内部：只有先拼接 bytes、再统一解码才能保留完整字符。
        val split = bytes.indexOfFirst { it < 0 } + 1
        val chunks = listOf(bytes.copyOfRange(0, split), bytes.copyOfRange(split, bytes.size))
        val assembler = TaskPathChunkAssembler()
        var merged: ByteArray? = null
        listOf(1, 1, 0).forEach { index ->
            val encoded = SlLink.PathPointPlanResponse.newBuilder().setTaskId("").setPathVersion(14).setRequestId("request")
                .setChunkIndex(index).setTotalChunks(2).setData(ByteString.copyFrom(chunks[index])).build().toByteArray()
            val chunk = SlLink.PathPointPlanResponse.parseFrom(encoded)
            merged = assembler.append(chunk.taskId, chunk.pathVersion, chunk.chunkIndex, chunk.totalChunks, chunk.data.toByteArray(), chunk.requestId)
            if (index == 1) assertNull(merged)
        }
        val result = TaskPathJsonParser.parse(requireNotNull(merged), envelope())
        assertEquals("规划完成", result.message)
        assertEquals(4, result.points.size)
        assertNull(result.totalWorkAreaM2)
    }

    @Test fun versionZeroFailureAfterSuccessIsDeliveredWithStatistics() {
        val assembler = TaskPathChunkAssembler()
        assembler.append("", 14, 0, 1, geometry.encodeToByteArray())
        val failure = """{"planned":false,"message":"失败","points":[],"total_work_area_m2":75,"estimated_time_s":-1}"""
        val bytes = assembler.append("", 0, 0, 1, failure.encodeToByteArray())!!
        val result = TaskPathJsonParser.parse(bytes, envelope(version = 0))
        assertFalse(result.hasRenderablePoints)
        assertEquals(75f, result.totalWorkAreaM2!!, 0f)
        assertEquals(-1f, result.estimatedTimeS!!, 0f)
    }

    @Test fun correlationAlwaysComesFromEnvelopeWhileJsonMetadataRemainsLoose() {
        val response = envelope("outer").toBuilder().setTaskId("task").setMapId("map")
            .setMapVersion(3).setFrameId("map").setPlanned(false).setMessage("outer message").build()
        val json = """{"request_id":"wrong","task_id":"other_task","map_id":"other_map",
            "path_version":0,"map_version":8,"points":[{"x":1,"y":2}]}"""
        val result = TaskPathJsonParser.parse(json.encodeToByteArray(), response)
        assertEquals("outer", result.requestId)
        assertEquals("other_task", result.taskId)
        assertEquals("other_map", result.mapId)
        assertEquals(8, result.mapVersion)
        assertEquals(0, result.pathVersion)
        assertTrue(result.hasRenderablePoints)
        assertFalse(result.planned)
        assertEquals("outer message", result.message)
        assertEquals("", TaskPathJsonParser.parse(json.encodeToByteArray(), response.toBuilder().clearRequestId().build()).requestId)
        val fallback = TaskPathJsonParser.parse(geometry.encodeToByteArray(), response)
        assertEquals("task", fallback.taskId)
        assertEquals("map", fallback.mapId)
        assertEquals(3, fallback.mapVersion)
    }

    // 统计只读 JSON，外层标量默认零无法表达 JSON 的缺失；null 与有效 0 必须保持区别。
    @Test fun envelopeStatisticsNeverFillMissingJsonStatisticsEvenWhenZeroOrNonzero() {
        for (outerValue in listOf(0f, 75f)) {
            val response = envelope().toBuilder().setTotalWorkAreaM2(outerValue).setEstimatedTimeS(486f).build()
            for (stats in listOf("", ",\"total_work_area_m2\":null,\"estimated_time_s\":\"\"")) {
                val parsed = TaskPathJsonParser.parse(("{\"points\":[{\"x\":1,\"y\":2}]" + stats + "}").encodeToByteArray(), response)
                assertNull(parsed.totalWorkAreaM2)
                assertNull(parsed.estimatedTimeS)
                assertTrue(parsed.hasRenderablePoints)
            }
            val zero = TaskPathJsonParser.parse("""{"points":[],"total_work_area_m2":0,"estimated_time_s":0}""".encodeToByteArray(), response)
            assertEquals(0f, zero.totalWorkAreaM2!!, 0f)
            assertEquals(0f, zero.estimatedTimeS!!, 0f)
        }
    }

    @Test fun undecodableDataPreservesOuterRequestIdAndFailureMessage() {
        val response = envelope("failed", 0).toBuilder().setTaskId("task").setMapId("map")
            .setMapVersion(5).setMessage("planner failed").setPlanned(false).build()
        val parsed = TaskPathJsonParser.parse(byteArrayOf(), response)
        assertEquals("failed", parsed.requestId)
        assertEquals("planner failed", parsed.message)
        assertEquals(5, parsed.mapVersion)
        assertEquals(0, parsed.pathVersion)
        assertFalse(parsed.hasRenderablePoints)
        assertNull(parsed.estimatedTimeS)
    }
}
