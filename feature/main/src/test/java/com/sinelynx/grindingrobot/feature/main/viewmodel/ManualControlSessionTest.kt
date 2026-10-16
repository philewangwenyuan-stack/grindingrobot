package com.sinelynx.grindingrobot.feature.main.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ManualControlSessionTest {
    @Test fun releaseDoesNotWaitForTaskStopAndLateAckCannotReplayMovement() = runBlocking {
        val ack = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Int>()
        val session = ManualControlSession(this, { ack.await() }, sent::add)
        session.request(100, true)
        yield()
        session.request(0, false)
        assertEquals(listOf(0), sent)
        assertFalse(session.hasRequestedControl)
        ack.complete(true)
        yield()
        assertEquals(listOf(0), sent)
    }

    @Test fun onlyNewestPositionIsSentAfterTaskStopConfirmation() = runBlocking {
        val ack = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Int>()
        var prepares = 0
        val session = ManualControlSession(this, { prepares++; ack.await() }, sent::add)
        session.request(100, true)
        yield()
        session.request(200, true)
        ack.complete(true)
        yield()
        assertEquals(1, prepares)
        assertEquals(listOf(200), sent)
    }

    @Test fun failedPreparationAndCancellationNeverSendPendingMotion() = runBlocking {
        val ack = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Int>()
        val session = ManualControlSession(this, { ack.await() }, sent::add)
        session.request(100, true)
        yield()
        ack.complete(false)
        yield()
        assertTrue(sent.isEmpty())
        assertFalse(session.hasRequestedControl)
        val waiting = CompletableDeferred<Boolean>()
        val cancelled = ManualControlSession(this, { waiting.await() }, sent::add)
        cancelled.request(100, true)
        yield()
        cancelled.cancel()
        waiting.complete(true)
        yield()
        assertTrue(sent.isEmpty())
    }
}
