package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapRequestOutcome
import com.sinelynx.grindingrobot.core.model.state.MapRequestResultPayload
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MapSnapshotLoaderTest {
    private fun frame(id: Long) = MapImagePayload(
        byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f, requestId = id)
    private fun result(id: Long, outcome: MapRequestOutcome) =
        MapRequestResultPayload(id, 1, "map", outcome, 0, "")

    private class Fixture {
        val frames = MutableSharedFlow<MapImagePayload>(extraBufferCapacity = 16)
        val results = MutableSharedFlow<MapRequestResultPayload>(extraBufferCapacity = 16)
        val cancelled = mutableListOf<Long>()
        var serial = 0L
        var send: (String, Long) -> Boolean = { _, _ -> true }
        var cancel: (Long) -> Unit = { cancelled += it }
        fun loader() = MapSnapshotLoader(frames, results,
            send = { map, id -> send(map, id) }, cancel = { cancel(it) },
            nextId = { ++serial }, pause = {})
    }

    @Test fun subscribesBeforeSendAndIgnoresPreviousSnapshot() = runBlocking {
        val f = Fixture()
        f.send = { _, id ->
            f.frames.tryEmit(frame(id + 100))
            f.results.tryEmit(result(id, MapRequestOutcome.READY))
            f.frames.tryEmit(frame(id))
            true
        }
        assertEquals(1L, f.loader().load("map", 1000).requestId)
        assertEquals(listOf(1L), f.cancelled)
    }

    @Test fun terminalFailureEndsWaitingWithoutWaitingForAnImage() = runBlocking {
        for (outcome in listOf(MapRequestOutcome.NOT_FOUND, MapRequestOutcome.ERROR)) {
            val f = Fixture()
            f.send = { _, id -> f.results.tryEmit(result(id, outcome)); true }
            try {
                withTimeout(1000) { f.loader().load("map", 180_000) }
                fail("Failure response must terminate the request")
            } catch (_: IOException) {}
            assertEquals(listOf(1L), f.cancelled)
        }
    }

    @Test fun notReadyRetriesWithFreshIdentityAndHasABoundedRetryCount() = runBlocking {
        val f = Fixture()
        f.send = { _, id -> f.results.tryEmit(result(id, MapRequestOutcome.NOT_READY)); true }
        try {
            f.loader().load("map", 1000)
            fail("An unavailable map must become a retryable error")
        } catch (_: IOException) {}
        assertEquals(listOf(1L, 2L, 3L), f.cancelled)
    }

    @Test fun notReadyCanRecoverWithoutSwitchingMaps() = runBlocking {
        val f = Fixture()
        f.send = { map, id ->
            assertEquals("same-map", map)
            if (id == 1L) f.results.tryEmit(result(id, MapRequestOutcome.NOT_READY))
            else f.frames.tryEmit(frame(id))
            true
        }
        assertEquals(2L, f.loader().load("same-map", 1000).requestId)
        assertEquals(listOf(1L, 2L), f.cancelled)
    }

    @Test fun cancellationReleasesOldOwnerBeforeAnotherMapLoads() = runBlocking {
        val f = Fixture()
        var owner: Long? = null
        f.send = { _, id ->
            if (owner != null) false else {
                owner = id
                if (id > 1) {
                    f.frames.tryEmit(frame(1)) // delayed result from the cancelled map
                    f.frames.tryEmit(frame(id))
                }
                true
            }
        }
        f.cancel = { id -> f.cancelled += id; if (owner == id) owner = null }
        val loader = f.loader()
        val old = async(start = CoroutineStart.UNDISPATCHED) { loader.load("old", 1000) }
        assertEquals(1L, owner)
        old.cancelAndJoin()
        assertNull(owner)
        assertEquals(2L, loader.load("new", 1000).requestId)
        assertNull(owner)
    }

    @Test fun sendFailureDoesNotReleaseSomeoneElsesRequest() = runBlocking {
        val f = Fixture()
        var owner: Long? = 99L
        f.send = { _, _ -> false }
        f.cancel = { id -> if (owner == id) owner = null }
        try { f.loader().load("map", 1000); fail("Busy request must fail") }
        catch (_: IOException) {}
        assertEquals(99L, owner)
    }

    @Test fun timeoutReleasesTheRequest() = runBlocking {
        val f = Fixture()
        try { f.loader().load("map", 80); fail("Timeout expected") }
        catch (_: IOException) {}
        assertEquals(listOf(1L), f.cancelled)
    }
}
