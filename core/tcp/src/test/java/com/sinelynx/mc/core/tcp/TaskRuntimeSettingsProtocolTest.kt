package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink
import sl_link.SlMessageBuilder
import sl_link.SlFrameParser

class TaskRuntimeSettingsProtocolTest {
    @Test fun queryContainsNoSetpointSoOldSettingsWritesAreNeverUsed() {
        val request = SlLink.TaskRuntimeSettingsRequest.newBuilder().setTaskId("task").build()
        val frame = SlFrameParser().parse(SlMessageBuilder.buildTaskRuntimeSettingsRequestRaw(request.toByteArray(), 0x10u)).single()
        assertEquals(0x0538, frame.msgId.toInt())
        assertEquals(0x08, frame.compId.toInt())
        val decoded = SlLink.TaskRuntimeSettingsRequest.parseFrom(frame.payload)
        assertFalse(decoded.hasRunSpeed())
        assertFalse(decoded.hasDiscSpeedRpm())
    }

    @Test fun zeroRpmUpdatePreservesOptionalPresenceWithoutChangingSpeed() {
        val request = SlLink.TaskRuntimeSettingsRequest.newBuilder().setTaskId("task").setDiscSpeedRpm(0).build()
        val decoded = SlLink.TaskRuntimeSettingsRequest.parseFrom(request.toByteArray())
        assertTrue(decoded.hasDiscSpeedRpm())
        assertFalse(decoded.hasRunSpeed())
    }

    @Test fun strictMatchingAcceptsWrappedSequenceZeroButNeverFallsBackToWrongAck() {
        val tracker = CommandReplyTracker()
        val frame = ByteArray(15).apply { this[9] = 0x10 }
        val ticket = tracker.register(frame, "task")
        assertNull(tracker.complete(12, 0x10, "task", CommandReply(0, "ok"), requireExactAck = true))
        assertSame(ticket, tracker.complete(0, 0x10, "task", CommandReply(0, "ok"), requireExactAck = true))
    }
}
