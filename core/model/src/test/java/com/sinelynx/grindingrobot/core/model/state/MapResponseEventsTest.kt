package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * 同时保护旧 StateFlow 状态读取和新 SharedFlow 请求事件语义。
 * 先发布旧响应、再订阅事件、最后发布相同内容：新等待不能读到历史值，也不能被内容去重。
 * UNDISPATCHED 确保发布前已建立订阅；测试结束清理单例仅用于隔离用例，不是生产请求步骤。
 */
class MapResponseEventsTest {
    @After fun clear() { MapImageStream.reset(); MapImportToRadarStream.reset() }

    @Test fun identicalFramesRemainFreshEventsWhileOldStateIsNotReplayed() = runBlocking {
        val frame = MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f)
        MapImageStream.publish(frame)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { MapImageStream.frames.first() }
        yield()
        assertFalse(waiting.isCompleted)
        MapImageStream.publish(frame.copy(imageBytes = byteArrayOf(1)))
        assertEquals(frame, withTimeout(1000) { waiting.await() })
        assertEquals(frame, MapImageStream.payload.value)
    }

    @Test fun identicalImportsDoNotRequireClearingSharedState() = runBlocking {
        val response = MapImportToRadarPayload(0, true, "ok")
        MapImportToRadarStream.publish(response)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { MapImportToRadarStream.responses.first() }
        yield()
        assertFalse(waiting.isCompleted)
        MapImportToRadarStream.publish(response)
        assertEquals(response, withTimeout(1000) { waiting.await() })
        assertSame(response, MapImportToRadarStream.payload.value)
    }
}
