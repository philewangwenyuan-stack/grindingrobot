package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class TaskExecutionDeleteProtocolTest {
    @Test
    fun requestUsesExpectedMessageAndExecutionId() {
        val request = buildTaskExecutionDeleteRequest("task_001_1780000000123")
        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildTaskExecutionDeleteRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x0534, frame.msgId.toInt())
        val decoded = SlLink.TaskExecutionDeleteRequest.parseFrom(frame.payload)
        assertEquals("task_001_1780000000123", decoded.executionId)
    }

    @Test
    fun successfulResponsePreservesDeletionDetails() {
        val payload = SlLink.TaskExecutionDeleteResponse.newBuilder()
            .setResult(SlLink.ResultCode.RESULT_SUCCESS)
            .setMessage("deleted")
            .setExecutionId("execution-1")
            .setTaskId("task-1")
            .setMapId("map-1")
            .setDeleted(true)
            .setExecutionFilesDeleted(true)
            .build()
            .toPayload()

        assertTrue(payload.isSuccess)
        assertTrue(payload.deleted)
        assertTrue(payload.executionFilesDeleted)
        assertEquals("execution-1", payload.executionId)
        assertEquals("task-1", payload.taskId)
        assertEquals("map-1", payload.mapId)
    }

    @Test
    fun busyResponseIsNotSuccessful() {
        val payload = SlLink.TaskExecutionDeleteResponse.newBuilder()
            .setResult(SlLink.ResultCode.RESULT_BUSY)
            .setMessage("execution_is_running")
            .setExecutionId("execution-1")
            .setDeleted(false)
            .setExecutionFilesDeleted(false)
            .build()
            .toPayload()

        assertFalse(payload.isSuccess)
        assertEquals(SlLink.ResultCode.RESULT_BUSY_VALUE, payload.resultValue)
        assertFalse(payload.deleted)
    }
}
