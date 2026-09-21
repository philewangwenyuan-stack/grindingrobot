package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * results 是每轮结果事件，pathsByTaskId 是可绘制路径缓存，两者用途不能混用。
 * 新等待必须收到版本 0 失败和旧版本结果；无可绘制点的响应不应抹掉历史成功缓存。
 * 是否可绘制取决于实际有效坐标，不要求 planned/result、分段摘要或统计字段齐全。
 */
class TaskPathStreamTest {
    @After fun clear() { TaskPathStream.resetAll() }

    @Test fun freshResultsDeliverVersionZeroFailureWithoutReplayingCachedSuccess() = runBlocking {
        val success = TaskPathPayload("task", 8, "map", 0f, listOf(TaskPathPointPayload(1f, 2f)))
        TaskPathStream.publish(success)
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { TaskPathStream.results.first() }
        }
        val failure = success.copy(pathVersion = 0, points = emptyList(), planned = false, message = "失败")
        TaskPathStream.publish(failure)
        assertEquals(failure, next.await())
        // 失败事件已结束本轮等待；缓存仍有历史路径，不代表本轮规划成功。
        assertEquals(success, TaskPathStream.pathsByTaskId.value["task"])
    }

    @Test fun actualPointsCanBeCachedWithoutSuccessFlagsSegmentsOrStatistics() {
        val path = TaskPathPayload("", 0, "", 0f, listOf(TaskPathPointPayload(1f, 2f)),
            planned = false, result = "failed", pathPointCount = 999)
        TaskPathStream.publish(path)
        assertTrue(path.hasRenderablePoints)
        assertEquals(path, TaskPathStream.pathsByTaskId.value[""])
        assertNull(path.totalWorkAreaM2)
        assertNull(path.estimatedTimeS)
        TaskPathStream.publish(path.copy(points = listOf(TaskPathPointPayload(Float.NaN, 1f))))
        assertEquals(path, TaskPathStream.pathsByTaskId.value[""])
    }

    @Test fun olderVersionIsStillDeliveredToCurrentWaiter() = runBlocking {
        val recent = TaskPathPayload("", 8, "map", 0f, listOf(TaskPathPointPayload(1f, 2f)))
        TaskPathStream.publish(recent)
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { TaskPathStream.results.first() }
        }
        val older = recent.copy(pathVersion = 1)
        TaskPathStream.publish(older)
        assertEquals(older, next.await())
        assertEquals(recent, TaskPathStream.pathsByTaskId.value[""])
    }

    @Test fun requestCorrelationAndMapMetadataSurviveVersionZeroFailure() = runBlocking {
        val success = TaskPathPayload("task", 8, "map", 0f, listOf(TaskPathPointPayload(1f, 2f)),
            requestId = "old", mapId = "map_1", mapVersion = 3)
        TaskPathStream.publish(success)
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { TaskPathStream.results.first { it.requestId == "new" } }
        }
        TaskPathStream.publish(success.copy(requestId = "other"))
        val failure = success.copy(requestId = "new", mapVersion = 4, pathVersion = 0, points = emptyList(), planned = false)
        TaskPathStream.publish(failure)
        assertEquals(failure, next.await())
        assertEquals(8, TaskPathStream.pathsByTaskId.value["task"]!!.pathVersion)
    }
}
