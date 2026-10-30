package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TaskHistoryQueryTest {
    private class Fixture {
        val replies = MutableStateFlow<TaskExecutionHistoryPayload?>(null)
        val connection = MutableStateFlow(ConnectionState.CONNECTED)

        suspend fun query(timeoutMs: Long = 1_000L, send: () -> Boolean = { true }) =
            awaitTaskHistory(
                replies, connection, timeoutMs,
                accepts = { matchesTaskHistoryQuery(it) },
                reset = { replies.value = null }, send = send
            )

        fun assertReleased() {
            assertEquals(0, replies.subscriptionCount.value)
            assertEquals(0, connection.subscriptionCount.value)
        }
    }

    @Test fun immediateReplyIsReceivedAndOldStateIsCleared() = runBlocking {
        val fixture = Fixture()
        fixture.replies.value = TaskExecutionHistoryPayload(true, "old")
        val result = fixture.query {
            fixture.replies.value = TaskExecutionHistoryPayload(true, "new")
            true
        }
        assertEquals("new", result.message)
        fixture.assertReleased()
    }

    @Test fun foregroundWaitsForExistingQueryAndCannotConsumeItsReply() = runBlocking {
        val fixture = Fixture()
        val sends = mutableListOf<String>()
        val background = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.query { sends.add("background"); true }
        }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.query { sends.add("foreground"); true }
        }
        assertEquals(listOf("background"), sends)
        fixture.replies.value = TaskExecutionHistoryPayload(true, "background result")
        assertEquals("background result", background.await().message)
        withTimeout(1_000) { while (sends.size < 2) yield() }
        assertFalse(foreground.isCompleted)
        fixture.replies.value = TaskExecutionHistoryPayload(true, "foreground result")
        assertEquals("foreground result", foreground.await().message)
        fixture.assertReleased()
    }

    @Test fun leavingPageReleasesOwnerAndNextQueryCanStart() = runBlocking {
        val fixture = Fixture()
        val leaving = async(start = CoroutineStart.UNDISPATCHED) { fixture.query() }
        leaving.cancelAndJoin()
        fixture.assertReleased()
        assertTrue(fixture.query {
            fixture.replies.value = TaskExecutionHistoryPayload(true, "next")
            true
        }.isSuccess)
    }

    @Test fun cancellingQueuedPageDoesNotReleaseTheActiveOwner() = runBlocking {
        val fixture = Fixture()
        var sends = 0
        val active = async(start = CoroutineStart.UNDISPATCHED) { fixture.query { sends++; true } }
        val leaving = async(start = CoroutineStart.UNDISPATCHED) { fixture.query { sends++; true } }
        leaving.cancelAndJoin()
        val next = async(start = CoroutineStart.UNDISPATCHED) { fixture.query { sends++; true } }
        assertEquals(1, sends)
        fixture.replies.value = TaskExecutionHistoryPayload(true, "active")
        active.await()
        withTimeout(1_000) { while (sends < 2) yield() }
        fixture.replies.value = TaskExecutionHistoryPayload(true, "next")
        assertEquals("next", next.await().message)
        fixture.assertReleased()
    }

    @Test fun timeoutReleasesOwnerAndAllowsRetry() = runBlocking {
        val fixture = Fixture()
        assertFalse(fixture.query(timeoutMs = 30).isSuccess)
        fixture.assertReleased()
        assertTrue(fixture.query {
            fixture.replies.value = TaskExecutionHistoryPayload(true, "retry")
            true
        }.isSuccess)
    }

    @Test fun disconnectEndsWaitAndCleansCollectors() = runBlocking {
        val fixture = Fixture()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { fixture.query() }
        fixture.connection.value = ConnectionState.DISCONNECTED
        assertEquals("设备连接已断开", withTimeout(1_000) { waiting.await() }.message)
        fixture.assertReleased()
        var sent = false
        assertFalse(fixture.query { sent = true; true }.isSuccess)
        assertFalse(sent)
    }

    @Test fun sendFailureAndExceptionCannotLeaveLockHeld() = runBlocking {
        val fixture = Fixture()
        assertFalse(fixture.query { false }.isSuccess)
        fixture.assertReleased()
        assertFalse(fixture.query { throw IllegalStateException("send failed") }.isSuccess)
        fixture.assertReleased()
        assertTrue(fixture.query {
            fixture.replies.value = TaskExecutionHistoryPayload(true, "retry")
            true
        }.isSuccess)
    }

    @Test fun unrelatedFiltersAreRejectedAndParserFailureEndsCurrentQuery() = runBlocking {
        val response = TaskExecutionHistoryPayload(true, "", startTime = 100, endTime = 200)
        assertTrue(matchesTaskHistoryQuery(response, startTime = 100, endTime = 200))
        assertFalse(matchesTaskHistoryQuery(response.copy(startTime = 0), startTime = 100, endTime = 200))
        assertFalse(matchesTaskHistoryQuery(response.copy(mapId = "other"), startTime = 100, endTime = 200))
        assertFalse(matchesTaskHistoryQuery(response.copy(taskId = "other"), startTime = 100, endTime = 200))
        val fixture = Fixture()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { fixture.query() }
        fixture.replies.value = response
        yield()
        assertFalse(waiting.isCompleted)
        fixture.replies.value = TaskExecutionHistoryPayload(false, "解析失败")
        assertEquals("解析失败", waiting.await().message)
        fixture.assertReleased()
    }
}
