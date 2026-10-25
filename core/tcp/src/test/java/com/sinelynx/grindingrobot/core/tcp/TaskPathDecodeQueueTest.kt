package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class TaskPathDecodeQueueTest {
    private fun response(id: String) = SlLink.PathPointPlanResponse.newBuilder()
        .setTaskId("task").setRequestId(id).setMapId("map").setPathVersion(7).build()
    private fun result(response: SlLink.PathPointPlanResponse) = TaskPathJsonParser.emptyPayload(response)

    @Test fun slowParseDoesNotHoldCallerAndAcceptedResultsStayInOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val results = Channel<TaskPathPayload>(Channel.UNLIMITED)
        val queue = TaskPathDecodeQueue(scope, { results.trySend(it) }, decode = { _, metadata ->
            if (metadata.requestId == "one") { entered.complete(Unit); release.await() }
            result(metadata)
        })
        try {
            assertTrue(queue.submit(byteArrayOf(1), response("one")))
            withTimeout(5_000) { entered.await() }
            assertTrue(queue.submit(byteArrayOf(2), response("two")))
            assertTrue(queue.submit(byteArrayOf(3), response("three")))
            // This is reached while the first parse is still blocked: an ACK/status handler can run.
            assertFalse(release.isCompleted)
            assertTrue(results.tryReceive().isFailure)
            assertFalse(queue.submit(byteArrayOf(4), response("overflow")))
            val busy = withTimeout(5_000) { results.receive() }
            assertEquals("overflow", busy.requestId)
            assertEquals("task", busy.taskId)
            assertEquals("map", busy.mapId)
            assertEquals(7, busy.pathVersion)
            assertTrue(busy.points.isEmpty())
            assertTrue(busy.message.contains("繁忙"))
            release.complete(Unit)
            assertEquals(listOf("one", "two", "three"), withTimeout(5_000) {
                List(3) { results.receive().requestId }
            })
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun resetDropsWaitingAndInFlightOldConnectionResults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val decoded = mutableListOf<String>()
        val results = Channel<TaskPathPayload>(Channel.UNLIMITED)
        val queue = TaskPathDecodeQueue(scope, { results.trySend(it) }, decode = { _, metadata ->
            decoded.add(metadata.requestId)
            if (metadata.requestId == "old-active") { entered.complete(Unit); release.await() }
            result(metadata)
        })
        try {
            queue.submit(byteArrayOf(1), response("old-active"))
            withTimeout(5_000) { entered.await() }
            queue.submit(byteArrayOf(2), response("old-waiting"))
            queue.reset()
            assertTrue(queue.submit(byteArrayOf(3), response("new")))
            release.complete(Unit)
            assertEquals("new", withTimeout(5_000) { results.receive().requestId })
            assertEquals(listOf("old-active", "new"), decoded)
            assertTrue(results.tryReceive().isFailure)
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun byteBudgetIncludesActiveWorkAndIsReleasedAfterCompletion() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val results = Channel<TaskPathPayload>(Channel.UNLIMITED)
        val queue = TaskPathDecodeQueue(scope, { results.trySend(it) }, decode = { _, metadata ->
            if (metadata.requestId == "active") { entered.complete(Unit); release.await() }
            result(metadata)
        }, maxPendingBytes = 4)
        try {
            assertTrue(queue.submit(ByteArray(3), response("active")))
            withTimeout(5_000) { entered.await() }
            assertFalse(queue.submit(ByteArray(2), response("too-big")))
            assertEquals("too-big", withTimeout(5_000) { results.receive().requestId })
            assertTrue(queue.submit(ByteArray(1), response("waiting")))
            release.complete(Unit)
            withTimeout(5_000) { repeat(2) { results.receive() } }
            assertTrue(queue.submit(ByteArray(4), response("fits-again")))
            assertEquals("fits-again", withTimeout(5_000) { results.receive().requestId })
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun malformedAndUnexpectedFailureFinishRequestWithoutKillingWorker() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val results = Channel<TaskPathPayload>(Channel.UNLIMITED)
        val queue = TaskPathDecodeQueue(scope, { results.trySend(it) }, decode = { bytes, metadata ->
            if (metadata.requestId == "throws") error("bad input")
            assertTrue(metadata.data.isEmpty)
            TaskPathJsonParser.parse(bytes, metadata)
        })
        try {
            queue.submit(byteArrayOf(0xC3.toByte()), response("bad-json"))
            queue.submit(byteArrayOf(), response("throws"))
            assertEquals("bad-json", withTimeout(5_000) { results.receive().requestId })
            assertEquals("throws", withTimeout(5_000) { results.receive().requestId })
            queue.submit("""{"points":[{"x":1,"y":2}]}""".encodeToByteArray(), response("good"))
            assertTrue(withTimeout(5_000) { results.receive() }.hasRenderablePoints)
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun assemblerResetCannotCompleteAResponseAcrossConnections() {
        val assembler = TaskPathChunkAssembler()
        assertNull(assembler.append("task", 1, 0, 2, byteArrayOf(1), "request"))
        assembler.clear()
        assertNull(assembler.append("task", 1, 1, 2, byteArrayOf(2), "request"))
        assertArrayEquals(byteArrayOf(3, 2), assembler.append("task", 1, 0, 2, byteArrayOf(3), "request"))
    }
}
