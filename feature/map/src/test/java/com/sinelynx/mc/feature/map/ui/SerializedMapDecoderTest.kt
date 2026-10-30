package com.sinelynx.grindingrobot.feature.map.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SerializedMapDecoderTest {
    @Test fun simultaneousScreensShareOneDecodeOfTheSameFrame() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val decoder = SerializedMapDecoder {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            Any()
        }
        val bytes = byteArrayOf(1)
        val first = async(start = CoroutineStart.UNDISPATCHED) { decoder.decode(bytes) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val second = async(start = CoroutineStart.UNDISPATCHED) { decoder.decode(bytes) }
        release.countDown()
        assertSame(first.await(), second.await())
        assertEquals(1, calls.get())
        assertNull(decoder.peek(byteArrayOf(1))) // Equal content is not the same snapshot.
    }

    @Test fun cancelledNativeDecodeDoesNotOverlapNewFrameOrDecodeCancelledWaiter() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = mutableListOf<Int>()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val decoder = SerializedMapDecoder { bytes ->
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            synchronized(calls) { calls.add(bytes[0].toInt()) }
            try {
                if (bytes[0].toInt() == 1) {
                    entered.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                }
                Any()
            } finally { active.decrementAndGet() }
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { decoder.decode(byteArrayOf(1)) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        first.cancel()
        val stale = async(start = CoroutineStart.UNDISPATCHED) { decoder.decode(byteArrayOf(2)) }
        stale.cancelAndJoin()
        val newest = async(start = CoroutineStart.UNDISPATCHED) { decoder.decode(byteArrayOf(3)) }
        release.countDown()
        first.join()
        assertNotNull(newest.await())
        assertEquals(listOf(1, 3), calls)
        assertEquals(1, maximum.get())
    }
}
