package com.sinelynx.grindingrobot.core.model.state

import org.junit.Assert.*
import org.junit.Test

/**
 * 任务显示缓存的交接契约：stage 冻结候选，commit 按最终任务 ID 接纳，discard 只撤销同一 token。
 * 只验证显示资料所有权，不把缓存命中等同于设备进入 RUNNING。
 * 图片使用任意字节验证复制；区域和临时禁区用可变容器检验深拷贝边界。
 */
class TaskMapDisplayCacheTest {
    private fun snapshot() = TaskMapDisplaySnapshot("task", "map",
        frame = MapImagePayload(byteArrayOf(1), 100, 50, 0.1f, 3.0, 4.0, 20f),
        bitmapSize = 50 to 25, path = TaskPathPayload("task", 7, "map", 0f,
            listOf(TaskPathPointPayload(4f, 5f))), hasTaskConfiguration = true)

    @Test fun stagedImageAndCollectionsAreDetachedAndCommitDoesNotMeanRunning() {
        val cache = TaskMapDisplayCache()
        val vertices = mutableListOf(MapPreviewPointPayload(1f, 2f))
        val points = mutableListOf(TaskPolygonPointConfig(3f, 4f))
        val selected = mutableSetOf("work")
        val source = snapshot().copy(selectedRegionIds = selected,
            temporaryObstacles = listOf(TaskObstacleRegionConfig("temp", "", points)),
            regions = MapRegionPointPayload("map", 1, true, "",
                listOf(WorkRegionPointPayload(MapPreviewRegionPayload("work", "", vertices), null, null)),
                emptyList(), emptyList(), null))
        val token = cache.stage("input", source)
        source.frame!!.imageBytes[0] = 9
        // 若 stage 仍引用页面草稿，关闭页面或继续编辑会使待交接资料一起变化。
        selected.clear(); vertices.clear(); points.clear()
        assertTrue(cache.state.value.pending!!.matches("input"))
        assertTrue(cache.state.value.pending!!.matches("task"))
        assertNull(cache.state.value.active)
        cache.commit(token, "task")
        val active = cache.state.value.active!!
        assertEquals(1, active.frame!!.imageBytes[0].toInt())
        assertEquals(setOf("work"), active.selectedRegionIds)
        assertEquals(1, active.regions!!.workRegions.first().region.points.size)
        assertEquals(1, active.temporaryObstacles.first().points.size)
        assertNotNull(active.path)
        assertNull(cache.state.value.pending)
    }

    // 旧协程无论成功还是失败，都不能凭旧 token 修改新候选。
    @Test fun failureAndLateOldCoroutineCannotRemoveOrCommitNewCandidate() {
        val cache = TaskMapDisplayCache()
        val first = cache.stage("task", snapshot())
        val second = cache.stage("second", snapshot().copy(taskId = "second"))
        cache.discard(first); cache.commit(first, "task")
        assertEquals(second, cache.state.value.pending!!.token)
        assertNull(cache.state.value.active)
        cache.discard(second)
        assertNull(cache.state.value.pending)
    }

    @Test fun finalTaskIdChangeKeepsMapButDoesNotRelabelOldPath() {
        val cache = TaskMapDisplayCache()
        cache.commit(cache.stage("task", snapshot()), "confirmed")
        assertEquals("confirmed", cache.state.value.active!!.taskId)
        assertNotNull(cache.state.value.active!!.frame)
        assertNull(cache.state.value.active!!.path)
        cache.clearTask("other")
        assertNotNull(cache.state.value.active)
        cache.clearTask("confirmed")
        assertNull(cache.state.value.active)
    }

    @Test fun backgroundDisplayUpdateCannotOverwriteStartupCandidate() {
        val cache = TaskMapDisplayCache()
        val token = cache.stage("task", snapshot())
        cache.update(TaskMapDisplaySnapshot("other"))
        assertNull(cache.state.value.active)
        cache.commit(token, "task")
        cache.discard(token)
        cache.update(TaskMapDisplaySnapshot("other"))
        assertEquals("task", cache.state.value.active!!.taskId)
        assertNotNull(cache.state.value.active)
    }
}
