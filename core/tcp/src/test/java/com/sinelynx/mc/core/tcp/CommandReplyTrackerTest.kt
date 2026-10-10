package com.sinelynx.grindingrobot.core.tcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandReplyTrackerTest {
    private fun frame(seq: Int, targetId: Int = 0x10) = ByteArray(15).apply {
        this[4] = seq.toByte()
        this[5] = (seq shr 8).toByte()
        this[9] = targetId.toByte()
    }

    private val success = CommandReply(0, "ok")

    @Test fun stopAndPauseRepliesCanArriveInReverseOrder() = runBlocking {
        val tracker = CommandReplyTracker()
        val pause = tracker.register(frame(301), "task")
        val stop = tracker.register(frame(302), "task")
        assertSame(stop, tracker.complete(302, 0x10, "task", success))
        assertFalse(pause.reply.isCompleted)
        assertSame(pause, tracker.complete(301, 0x10, "task", success))
        assertEquals(success, stop.reply.await())
        assertEquals(success, pause.reply.await())
    }

    @Test fun identicalRepliesStillCompleteSeparateRequests() = runBlocking {
        val tracker = CommandReplyTracker()
        val first = tracker.register(frame(20), "task")
        tracker.complete(20, 0x10, "task", success)
        val second = tracker.register(frame(21), "task")
        tracker.complete(21, 0x10, "task", success)
        assertEquals(first.reply.await(), second.reply.await())
    }

    @Test fun wrongTaskDeviceOrNonzeroAckCannotConfirmRequest() {
        val tracker = CommandReplyTracker()
        val ticket = tracker.register(frame(15), "task")
        assertNull(tracker.complete(15, 0x10, "other", success))
        assertNull(tracker.complete(15, 0x20, "task", success))
        assertNull(tracker.complete(16, 0x10, "task", success))
        assertFalse(ticket.reply.isCompleted)
    }

    @Test fun missingAckDoesNotGuessBetweenPauseAndStop() {
        val tracker = CommandReplyTracker()
        val pause = tracker.register(frame(0), "task")
        val stop = tracker.register(frame(1), "task")
        assertNull(tracker.complete(0, 0x10, "task", success))
        assertFalse(pause.reply.isCompleted)
        assertFalse(stop.reply.isCompleted)
    }

    @Test fun legacyMissingAckMatchesOnlyUniqueTask() {
        val tracker = CommandReplyTracker()
        val ticket = tracker.register(frame(65_535), "task")
        tracker.register(frame(0), "other")
        assertSame(ticket, tracker.complete(0, 0x10, "task", success))
    }

    @Test fun manualDriveReplyCannotConfirmUnsequencedEmergencyStop() {
        val tracker = CommandReplyTracker()
        val ticket = tracker.register(frame(0), "")
        assertNull(tracker.complete(0, 0x10, null, success, allowUnsequenced = false))
        assertFalse(ticket.reply.isCompleted)
        assertSame(ticket, tracker.complete(0, 0x10, null, success, allowUnsequenced = true))
    }

    @Test fun removedRequestDoesNotConsumeLaterReply() {
        val tracker = CommandReplyTracker()
        val ticket = tracker.register(frame(42), "task")
        tracker.remove(ticket)
        assertNull(tracker.complete(42, 0x10, "task", success))
        assertFalse(ticket.reply.isCompleted)
    }

    @Test fun timedOutPauseDoesNotLetItsLateMissingAckConfirmStop() {
        val tracker = CommandReplyTracker()
        val pause = tracker.register(frame(50), "task")
        val stop = tracker.register(frame(51), "task")
        tracker.remove(pause)
        assertNull(tracker.complete(0, 0x10, "task", success))
        assertFalse(stop.reply.isCompleted)
        assertSame(stop, tracker.complete(51, 0x10, "task", success))
    }

    @Test fun timeoutRetryCannotConsumeOldReplyWithoutAck() {
        val tracker = CommandReplyTracker()
        val first = tracker.register(frame(70), "task")
        tracker.remove(first)
        val retry = tracker.register(frame(71), "task")
        assertNull(tracker.complete(0, 0x10, "task", success))
        assertSame(retry, tracker.complete(71, 0x10, "task", success))
    }

    @Test fun disconnectFinishesOldWaitersAndResetsLegacyMatching() = runBlocking {
        val tracker = CommandReplyTracker()
        val old = tracker.register(frame(90), "task")
        tracker.resetConnection()
        assertEquals(-1, old.reply.await().resultValue)
        val next = tracker.register(frame(91), "task")
        tracker.remove(old)
        assertSame(next, tracker.complete(0, 0x10, "task", success))
    }
}
