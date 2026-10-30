package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapModeResponsePayload
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MapModeAwaiterTest {
    private fun response(seq: Int, success: Boolean = true, enabled: Boolean = true) =
        MapModeResponsePayload(0, success, "device reply", 2, enabled, 0, requestSeq = seq)

    @Test fun disabledFailureFinishesWithoutWaitingForEnabledReply() = runBlocking {
        val replies = MutableSharedFlow<MapModeResponsePayload>(extraBufferCapacity = 8)
        val failed = response(7, success = false, enabled = false)
        val result = awaitMapMode(replies, MutableStateFlow(ConnectionState.CONNECTED), 1000) { prepared ->
            prepared(7)
            replies.tryEmit(response(6, enabled = false)) // Previous stop must not satisfy this start.
            replies.tryEmit(failed)
            true
        }
        assertSame(failed, result)
        assertEquals(0, replies.subscriptionCount.value)
    }

    @Test fun staleStopIsIgnoredUntilMatchingStartArrives() = runBlocking {
        val replies = MutableSharedFlow<MapModeResponsePayload>(extraBufferCapacity = 8)
        val started = response(9)
        assertSame(started, awaitMapMode(replies, MutableStateFlow(ConnectionState.CONNECTED), 1000) {
            it(9)
            replies.tryEmit(response(8, enabled = false))
            replies.tryEmit(started)
            true
        })
    }

    @Test fun disconnectEndsWaitPromptlyAndCleansCollectors() = runBlocking {
        val replies = MutableSharedFlow<MapModeResponsePayload>()
        val connection = MutableStateFlow(ConnectionState.CONNECTED)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { awaitMapMode(replies, connection, 10_000) { it(3); true } }
        }
        connection.value = ConnectionState.DISCONNECTED
        assertTrue(waiting.await().exceptionOrNull() is IOException)
        assertEquals(0, replies.subscriptionCount.value)
    }

    @Test fun timeoutAndSendFailureAreReported() = runBlocking {
        val replies = MutableSharedFlow<MapModeResponsePayload>()
        val connection = MutableStateFlow(ConnectionState.CONNECTED)
        assertTrue(runCatching { awaitMapMode(replies, connection, 30) { it(1); true } }.exceptionOrNull() is IOException)
        assertTrue(runCatching { awaitMapMode(replies, connection, 1000) { false } }.exceptionOrNull() is IOException)
        assertEquals(0, replies.subscriptionCount.value)
    }

    @Test fun leavingPageCancelsWaitWithoutConvertingToFailure() = runBlocking {
        val replies = MutableSharedFlow<MapModeResponsePayload>()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            awaitMapMode(replies, MutableStateFlow(ConnectionState.CONNECTED), 10_000) { it(1); true }
        }
        waiting.cancelAndJoin()
        assertTrue(waiting.isCancelled)
        assertEquals(0, replies.subscriptionCount.value)
    }
}
