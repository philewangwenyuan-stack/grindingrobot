package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

/**
 * 分片层只负责字节完整性：按序拼接，重复索引替换，缺片不返回部分内容。
 * 新协议以 request_id 分组；缺少 request_id 时才回退 task_id + path_version，两类键互不冲突。
 * A/B/C 等夹具刻意不是 JSON，证明组包层不检查路径业务内容；JSON 由独立解析器测试。
 */
class TaskPathChunkAssemblerTest {
    @Test fun taskPathRequestUsesExpectedMessageAndPayload() {
        val request = buildPathPointPlanRequest(taskId = "task_001", requestId = "request_001", mapId = "map_001", forceReplan = true)
        val frame = SlFrameParser().parse(SlMessageBuilder.buildPathPointPlanRequestRaw(request.toByteArray(), 0x10u)).single()
        assertEquals(0x0505, frame.msgId.toInt())
        assertArrayEquals(request.toByteArray(), frame.payload)
        val decoded = SlLink.PathPointPlanRequest.parseFrom(frame.payload)
        assertEquals("request_001", decoded.requestId)
        assertEquals("map_001", decoded.mapId)
        assertTrue(decoded.forceReplan)
    }

    @Test fun assemblesOutOfOrderChunksAndReplacesDuplicates() {
        listOf("", "task", " ").forEach { taskId ->
            val assembler = TaskPathChunkAssembler()
            assertNull(assembler.append(taskId, 13, 1, 3, "wrong".encodeToByteArray()))
            assertNull(assembler.append(taskId, 13, 1, 3, "B".encodeToByteArray()))
            assertNull(assembler.append(taskId, 13, 0, 3, "A".encodeToByteArray()))
            assertArrayEquals("ABC".encodeToByteArray(), assembler.append(taskId, 13, 2, 3, "C".encodeToByteArray()))
        }
    }

    @Test fun emptyTaskIdAcceptsDeviceSizedChunks() {
        val assembler = TaskPathChunkAssembler()
        val chunks = listOf(ByteArray(2048) { 1 }, ByteArray(2048) { 2 }, ByteArray(1631) { 3 })
        assertNull(assembler.append("", 13, 2, 3, chunks[2]))
        assertNull(assembler.append("", 13, 0, 3, chunks[0]))
        assertNull(assembler.append("", 13, 0, 3, chunks[0]))
        assertArrayEquals(chunks[0] + chunks[1] + chunks[2], assembler.append("", 13, 1, 3, chunks[1]))
    }

    @Test fun differentVersionsAndTaskIdsAssembleIndependently() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("", 2, 0, 2, "A".encodeToByteArray()))
        assertNull(assembler.append("", 3, 0, 2, "X".encodeToByteArray()))
        assertNull(assembler.append("task", 2, 0, 2, "M".encodeToByteArray()))
        assertArrayEquals("XY".encodeToByteArray(), assembler.append("", 3, 1, 2, "Y".encodeToByteArray()))
        assertArrayEquals("AB".encodeToByteArray(), assembler.append("", 2, 1, 2, "B".encodeToByteArray()))
        assertArrayEquals("MN".encodeToByteArray(), assembler.append("task", 2, 1, 2, "N".encodeToByteArray()))
        assertArrayEquals("failure".encodeToByteArray(), assembler.append("", 0, 0, 1, "failure".encodeToByteArray()))
    }

    @Test fun rejectsOnlyUnusableChunkCountAndIndex() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("", 1, -1, 1, byteArrayOf(1)))
        assertNull(assembler.append("", 1, 1, 1, byteArrayOf(1)))
        assertNull(assembler.append("", 1, 0, 0, byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(1), assembler.append("", -1, 0, 1, byteArrayOf(1)))
    }

    @Test fun missingChunksNeverProducePartialJsonAndInputBytesAreCopied() {
        val assembler = TaskPathChunkAssembler()
        val first = byteArrayOf(1)
        assertNull(assembler.append("", 1, 0, 3, first))
        first[0] = 9
        assertNull(assembler.append("", 1, 2, 3, byteArrayOf(3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), assembler.append("", 1, 1, 3, byteArrayOf(2)))
    }

    @Test fun changedTotalRestartsOnlyThatAssembly() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("", 1, 0, 3, byteArrayOf(9)))
        assertNull(assembler.append("", 1, 1, 2, byteArrayOf(2)))
        assertArrayEquals(byteArrayOf(1, 2), assembler.append("", 1, 0, 2, byteArrayOf(1)))
    }

    @Test fun differentRequestsWithSameTaskAndVersionNeverMix() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("task", 7, 1, 2, byteArrayOf(2), "first"))
        assertNull(assembler.append("task", 7, 0, 2, byteArrayOf(3), "retry"))
        assertNull(assembler.append("task", 7, 1, 2, byteArrayOf(9), "first"))
        assertArrayEquals(byteArrayOf(3, 4), assembler.append("task", 7, 1, 2, byteArrayOf(4), "retry"))
        assertArrayEquals(byteArrayOf(1, 9), assembler.append("task", 7, 0, 2, byteArrayOf(1), "first"))
        assertArrayEquals(byteArrayOf(), assembler.append("task", 0, 0, 1, byteArrayOf(), "failure"))
    }

    // 同一 request_id 的辅助字段变化不拆组；同名旧任务键也不能混入新请求分片。
    @Test fun requestKeyDoesNotDependOnAuxiliaryTaskOrVersionAndCannotCollideWithLegacyKey() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("task", 7, 0, 2, byteArrayOf(1), "request"))
        assertNull(assembler.append("request", 0, 0, 2, byteArrayOf(3)))
        assertArrayEquals(byteArrayOf(1, 2), assembler.append("other", 0, 1, 2, byteArrayOf(2), "request"))
        assertArrayEquals(byteArrayOf(3, 4), assembler.append("request", 0, 1, 2, byteArrayOf(4)))
    }
}
