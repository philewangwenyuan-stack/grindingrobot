package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class SystemCacheClearProtocolTest {
    @Test
    fun requestUsesExpectedMessageIdAndSelectedFields() {
        val request = requireNotNull(
            buildSystemCacheClearRequest(
                clearMemoryCache = true,
                clearTemporaryFiles = false,
                clearLogs = true
            )
        )
        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildSystemCacheClearRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x0536, frame.msgId.toInt())
        val decoded = SlLink.SystemCacheClearRequest.parseFrom(frame.payload)
        assertTrue(decoded.clearMemoryCache)
        assertFalse(decoded.clearTemporaryFiles)
        assertTrue(decoded.clearLogs)
    }

    @Test
    fun requestSupportsEachCategoryAndAllCategories() {
        val combinations = listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(true, true, true)
        )

        combinations.forEach { (memory, temporary, logs) ->
            val request = requireNotNull(buildSystemCacheClearRequest(memory, temporary, logs))
            assertEquals(memory, request.clearMemoryCache)
            assertEquals(temporary, request.clearTemporaryFiles)
            assertEquals(logs, request.clearLogs)
        }
    }

    @Test
    fun requestRejectsEmptySelection() {
        assertNull(buildSystemCacheClearRequest(false, false, false))
    }

    @Test
    fun responsePreservesCleanupStatistics() {
        val payload = SlLink.SystemCacheClearResponse.newBuilder()
            .setResult(SlLink.ResultCode.RESULT_SUCCESS)
            .setMessage("cleared")
            .setMemoryCacheCleared(true)
            .setTemporaryFilesCleared(7)
            .setTemporaryBytesReleased(4_294_967_296L)
            .setLogFilesCleared(3)
            .setLogBytesReleased(8_589_934_592L)
            .setFailedItems(1)
            .build()
            .toPayload()

        assertTrue(payload.isSuccess)
        assertTrue(payload.memoryCacheCleared)
        assertEquals(7, payload.temporaryFilesCleared)
        assertEquals(4_294_967_296L, payload.temporaryBytesReleased)
        assertEquals(3, payload.logFilesCleared)
        assertEquals(8_589_934_592L, payload.logBytesReleased)
        assertEquals(1, payload.failedItems)
    }

    @Test
    fun failedResponseIsNotSuccessful() {
        val payload = SlLink.SystemCacheClearResponse.newBuilder()
            .setResult(SlLink.ResultCode.RESULT_FAILED)
            .setMessage("clear_failed")
            .build()
            .toPayload()

        assertFalse(payload.isSuccess)
        assertEquals("clear_failed", payload.message)
    }
}
