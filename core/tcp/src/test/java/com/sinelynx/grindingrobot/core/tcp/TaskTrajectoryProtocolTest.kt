package com.sinelynx.grindingrobot.core.tcp

import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class TaskTrajectoryProtocolTest {
    @Test
    fun requestUsesExpectedMessageAndPagingParameters() {
        val request = buildTaskTrajectoryRequest(
            executionId = "task_001_1780000000123",
            taskId = "task_001",
            startIndex = 4096,
            maxPoints = 14_400,
            sampleStep = 1,
            maxChunkSize = 4096
        )
        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildTaskTrajectoryRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x0532, frame.msgId.toInt())
        val decoded = SlLink.TaskTrajectoryRequest.parseFrom(frame.payload)
        assertEquals("task_001_1780000000123", decoded.executionId)
        assertEquals("task_001", decoded.taskId)
        assertEquals(0L, decoded.startTime)
        assertEquals(0L, decoded.endTime)
        assertEquals(4096, decoded.startIndex)
        assertEquals(14_400, decoded.maxPoints)
        assertEquals(1, decoded.sampleStep)
        assertEquals(4096, decoded.maxChunkSize)
    }

    @Test
    fun assemblerJoinsOutOfOrderChunksReplacesDuplicatesAndConvertsUnits() {
        val assembler = TaskTrajectoryChunkAssembler()
        val chunk0 = chunk(
            chunkIndex = 0,
            point = point(index = 10, xMm = 1250, linearSpeedMmps = 350),
            imagePart = byteArrayOf(1, 2)
        )
        val replacementChunk0 = chunk(
            chunkIndex = 0,
            point = point(index = 10, xMm = 1500, linearSpeedMmps = 400),
            imagePart = byteArrayOf(1, 3)
        )
        val chunk1 = chunk(
            chunkIndex = 1,
            point = point(index = 11, xMm = 2500, linearSpeedMmps = 600),
            imagePart = byteArrayOf(4, 5),
            hasMore = true,
            nextIndex = 12
        )

        assertTrue(assembler.append(chunk1) is TaskTrajectoryChunkResult.Pending)
        assertTrue(assembler.append(chunk0) is TaskTrajectoryChunkResult.Complete)

        assembler.clear()
        assertTrue(assembler.append(chunk0) is TaskTrajectoryChunkResult.Pending)
        assertTrue(assembler.append(replacementChunk0) is TaskTrajectoryChunkResult.Pending)
        val result = assembler.append(chunk1)
        assertTrue(result is TaskTrajectoryChunkResult.Complete)
        val payload = (result as TaskTrajectoryChunkResult.Complete).payload

        assertEquals(listOf(10, 11), payload.points.map { it.index })
        assertEquals(1.5f, payload.points.first().xMeters, 0f)
        assertEquals(0.4f, payload.points.first().linearSpeedMps, 0f)
        assertEquals(90f, payload.points.first().headingDeg, 0f)
        assertEquals(0.25f, payload.points.first().angularSpeedRadps, 0f)
        assertTrue(payload.hasMore)
        assertEquals(12, payload.nextIndex)
        assertArrayEquals(byteArrayOf(1, 3, 4, 5), payload.map.imageBytes)
    }

    @Test
    fun assemblerRejectsInvalidOrInconsistentChunks() {
        val assembler = TaskTrajectoryChunkAssembler()
        val invalidIndex = chunk(chunkIndex = 2, point = point(1, 0, 0))
        assertTrue(assembler.append(invalidIndex) is TaskTrajectoryChunkResult.Invalid)

        val first = chunk(chunkIndex = 0, point = point(1, 0, 0))
        val otherPage = chunk(chunkIndex = 1, point = point(2, 0, 0), startIndex = 99)
        assertTrue(assembler.append(first) is TaskTrajectoryChunkResult.Pending)
        assertTrue(assembler.append(otherPage) is TaskTrajectoryChunkResult.Invalid)
    }

    private fun chunk(
        chunkIndex: Int,
        point: SlLink.TaskTrajectoryPoint,
        imagePart: ByteArray = byteArrayOf(),
        startIndex: Int = 10,
        hasMore: Boolean = false,
        nextIndex: Int = 0
    ): SlLink.TaskTrajectoryChunk = SlLink.TaskTrajectoryChunk.newBuilder()
        .setResult(SlLink.ResultCode.RESULT_SUCCESS)
        .setExecutionId("execution-1")
        .setTaskId("task-1")
        .setMapId("map-1")
        .setChunkIndex(chunkIndex)
        .setTotalChunks(2)
        .setTotalPointCount(20)
        .setReturnedPointCount(2)
        .setStartIndex(startIndex)
        .setNextIndex(nextIndex)
        .setHasMore(hasMore)
        .setStartedAtMs(1_780_000_000_123L)
        .setSampleStep(1)
        .setMapAvailable(true)
        .setMapImageTotalChunks(2)
        .setMapImageChunkIndex(chunkIndex)
        .setMapImageData(ByteString.copyFrom(imagePart))
        .addPoints(point)
        .build()

    private fun point(
        index: Int,
        xMm: Int,
        linearSpeedMmps: Int
    ): SlLink.TaskTrajectoryPoint = SlLink.TaskTrajectoryPoint.newBuilder()
        .setIndex(index)
        .setOffsetMs(index * 1000)
        .setXMm(xMm)
        .setYMm(-500)
        .setHeadingMdeg(90_000)
        .setLinearSpeedMmps(linearSpeedMmps)
        .setAngularSpeedMradps(250)
        .setDiscSpeedRpm(1200)
        .setSpeedAvailable(true)
        .setDiscEnabled(true)
        .setTaskState(SlLink.TaskState.TASK_STATE_RUNNING)
        .build()
}
