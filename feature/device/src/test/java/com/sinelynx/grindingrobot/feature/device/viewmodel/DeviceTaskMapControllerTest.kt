package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 设备地图控制器时序测试，响应由内存 Flow 模拟，不连接机器人。
 * 覆盖缓存交接、冷加载、版本变化、取消/迟到响应、分项失败与重试。
 * calls 记录网络意图，完整缓存命中必须为零；反复 onTask 模拟连续状态上报，不测试屏幕绘制。
 * 回调立即返回响应并断言订阅数量，以暴露先发请求后订阅造成的丢包。
 */
class DeviceTaskMapControllerTest {
    @Test fun failedLaterPathKeepsColdRecoveredRegionsAndAnchors() = runBlocking {
        val f = Fixture(this)
        try {
            f.open(); f.ready()
            f.path = f.path!!.copy(points = emptyList(), pathVersion = 0)
            f.controller.onTask("task", 8); f.ready()
            assertEquals(setOf("work"), f.controller.state.value.snapshot!!.selectedRegionIds)
            assertNotNull(f.controller.state.value.snapshot!!.regions!!.workRegions.first().startPose)
            assertNull(f.controller.state.value.snapshot!!.path)
        } finally { f.controller.close() }
    }

    @Test fun completeHandoffSkipsAllRequestsAndSurvivesVisibilityAndPoseReports() = runBlocking {
        val f = Fixture(this)
        try {
            f.warm()
            f.controller.onTask("task", 7)
            f.controller.setVisible(true)
            f.flush()
            assertNotNull(f.controller.state.value.snapshot!!.frame)
            repeat(50) { f.controller.onTask("task", 7) }
            f.controller.setVisible(false); f.controller.setVisible(true)
            f.flush()
            assertTrue(f.calls.isEmpty())
            assertNull(f.controller.state.value.informationWarning)
        } finally { f.controller.close() }
    }

    @Test fun pendingStartupWaitsThenUsesCacheWithoutRacingRequests() = runBlocking {
        val f = Fixture(this)
        try {
            val token = f.cache.stage("task", f.snapshot)
            f.open()
            assertTrue(f.controller.state.value.waitingForHandoff)
            assertTrue(f.calls.isEmpty())
            f.cache.commit(token, "task"); f.flush()
            assertFalse(f.controller.state.value.waitingForHandoff)
            assertNotNull(f.controller.state.value.snapshot!!.path)
            assertTrue(f.calls.isEmpty())
        } finally { f.controller.close() }
    }

    @Test fun failedStartupReleasesWaitAndColdLoadsOnlyAfterVisible() = runBlocking {
        val f = Fixture(this)
        try {
            val token = f.cache.stage("task", f.snapshot)
            f.controller.onTask("task", 7)
            f.cache.discard(token); f.flush()
            assertTrue(f.calls.isEmpty())
            f.controller.setVisible(true); f.ready()
            assertEquals(listOf("path:LIVE_MAP", "map:map", "regions:map"), f.calls)
        } finally { f.controller.close() }
    }

    @Test fun knownVersionChangeOnlyRefreshesPathAndKeepsMapAndConfiguration() = runBlocking {
        val f = Fixture(this)
        try {
            f.warm(); f.open()
            val previous = f.controller.state.value.snapshot!!
            f.path = f.path!!.copy(pathVersion = 8)
            f.controller.onTask("task", 8); f.ready()
            val next = f.controller.state.value.snapshot!!
            assertEquals(listOf("path:map"), f.calls)
            assertSame(previous.frame, next.frame)
            assertSame(previous.regions, next.regions)
            assertEquals(previous.temporaryObstacles, next.temporaryObstacles)
            assertEquals(previous.selectedRegionIds, next.selectedRegionIds)
            assertEquals(8, next.path!!.pathVersion)
            repeat(10) { f.controller.onTask("task", 8) }; f.flush()
            assertEquals(1, f.calls.size)
        } finally { f.controller.close() }
    }

    @Test fun coldLoadUsesResponseMapAndRecoversOnlyIdentifiedRegions() = runBlocking {
        val f = Fixture(this)
        try {
            f.open(); f.ready()
            val value = f.controller.state.value.snapshot!!
            assertEquals("map", value.mapId)
            assertEquals(setOf("work"), value.selectedRegionIds)
            assertTrue(value.temporaryObstacles.isEmpty())
            assertNotNull(f.controller.state.value.informationWarning)
            assertEquals(listOf("path:LIVE_MAP", "map:map", "regions:map"), f.calls)
            f.frame.imageBytes[0] = 9
            f.frames.emit(f.frame.copy(originX = 999.0)); f.flush()
            assertEquals(1, value.frame!!.imageBytes[0].toInt())
            assertEquals(10.0, value.frame!!.originX, 0.0)
        } finally { f.controller.close() }
    }

    @Test fun versionZeroFailureClearsOnlyPathImmediatelyAndDoesNotLoopOnStatus() = runBlocking {
        val f = Fixture(this)
        try {
            f.warm(); f.open()
            f.path = f.path!!.copy(pathVersion = 0, points = emptyList(), planned = false)
            f.controller.onTask("task", 8); f.ready()
            assertEquals("路径规划未返回数据", f.controller.state.value.pathError)
            assertNull(f.controller.state.value.snapshot!!.path)
            assertNotNull(f.controller.state.value.snapshot!!.frame)
            assertNotNull(f.controller.state.value.snapshot!!.regions)
            repeat(10) { f.controller.onTask("task", 8) }; f.flush()
            assertEquals(1, f.calls.size)
        } finally { f.controller.close() }
    }

    @Test fun timeoutAndRetryUseFreshIdsAndIgnoreLateResponse() = runBlocking {
        val f = Fixture(this, 50)
        try {
            f.warm(); f.open(); f.path = null
            f.controller.onTask("task", 8)
            withTimeout(1000) { f.controller.state.first { it.pathError != null } }
            val old = f.requests.last()
            assertEquals("路径规划未返回数据", f.controller.state.value.pathError)
            f.controller.retry()
            f.waitForRequests(2)
            val latest = f.requests.last()
            assertNotEquals(old.third, latest.third)
            f.paths.emit(f.snapshot.path!!.copy(requestId = old.third)); f.flush()
            assertTrue(f.controller.state.value.loadingPath)
            f.paths.emit(f.snapshot.path!!.copy(requestId = latest.third, pathVersion = 8))
            f.ready()
            assertEquals(8, f.controller.state.value.snapshot!!.path!!.pathVersion)
        } finally { f.controller.close() }
    }

    @Test fun hiddenPageCancelsWaitAndRechecksLatestVersionWhenShown() = runBlocking {
        val f = Fixture(this)
        try {
            f.warm(); f.open(); f.path = null
            f.controller.onTask("task", 8); f.waitForRequests(1)
            val oldId = f.requests.last().third
            f.controller.setVisible(false); f.flush()
            assertEquals(0, f.paths.subscriptionCount.value)
            f.controller.onTask("task", 9); f.flush()
            assertEquals(1, f.requests.size)
            f.controller.setVisible(true); f.waitForRequests(2)
            f.paths.emit(f.snapshot.path!!.copy(requestId = oldId)); f.flush()
            assertTrue(f.controller.state.value.loadingPath)
            f.paths.emit(f.snapshot.path!!.copy(requestId = f.requests.last().third, pathVersion = 9))
            f.ready()
            assertEquals(9, f.controller.state.value.snapshot!!.path!!.pathVersion)
        } finally { f.controller.close() }
    }

    @Test fun switchingTaskRejectsOldResponseAndTaskEndClearsCache() = runBlocking {
        val f = Fixture(this)
        try {
            f.path = null; f.open(); f.waitForRequests(1)
            val old = f.requests.last().third
            f.controller.onTask("new", 3); f.waitForRequests(2)
            f.paths.emit(f.snapshot.path!!.copy(requestId = old)); f.flush()
            assertEquals("new", f.controller.state.value.snapshot!!.taskId)
            assertNull(f.controller.state.value.snapshot!!.path)
            f.paths.emit(f.snapshot.path!!.copy(taskId = "new", requestId = f.requests.last().third, pathVersion = 3))
            f.ready()
            assertEquals("new", f.cache.state.value.active!!.taskId)
            f.controller.onTask(null, 0); f.flush()
            assertNull(f.controller.state.value.snapshot)
            assertNull(f.cache.state.value.active)
        } finally { f.controller.close() }
    }

    @Test fun failuresAreSeparateAndRetryDoesNotReloadSuccessfulParts() = runBlocking {
        val f = Fixture(this)
        try {
            f.failure = "regions-send"; f.open(); f.ready()
            assertEquals("区域请求发送失败", f.controller.state.value.regionError)
            assertNotNull(f.controller.state.value.snapshot!!.frame)
            f.failure = ""; f.controller.retry(); f.ready()
            assertNull(f.controller.state.value.regionError)
            assertEquals(1, f.calls.count { it.startsWith("map:") })
            assertEquals(1, f.requests.size)
            assertEquals(2, f.calls.count { it.startsWith("regions:") })
        } finally { f.controller.close() }
    }

    @Test fun invalidImageGeometryRetainsPathAndRegionsAndCanRetry() = runBlocking {
        val f = Fixture(this)
        try {
            f.failure = "geometry"; f.open(); f.ready()
            assertNotNull(f.controller.state.value.mapError)
            assertNull(f.controller.state.value.snapshot!!.frame)
            assertNotNull(f.controller.state.value.snapshot!!.path)
            assertNotNull(f.controller.state.value.snapshot!!.regions)
            f.failure = ""; f.controller.retry(); f.ready()
            assertNotNull(f.controller.state.value.snapshot!!.frame)
            assertEquals(1, f.requests.size)
        } finally { f.controller.close() }
    }

    @Test fun pathSendFailureFallsBackToCurrentMapAndDoesNotInventSelection() = runBlocking {
        val f = Fixture(this)
        try {
            f.failure = "path-send"; f.open(); f.ready()
            assertEquals("路径请求发送失败", f.controller.state.value.pathError)
            assertTrue(f.controller.state.value.snapshot!!.selectedRegionIds.isEmpty())
            assertEquals(listOf("path:LIVE_MAP", "map:LIVE_MAP", "regions:LIVE_MAP"), f.calls)
        } finally { f.controller.close() }
    }

    @Test fun regionIdsCanComeFromConnectionSegmentsWithoutPointMetadata() {
        val path = TaskPathPayload("task", 0, "", 0f, emptyList(), segments = listOf(
            TaskPathSegmentPayload(0, "between_regions", "", "", 0, "a", "b", 0, 2, 3)))
        assertEquals(setOf("a", "b"), path.taskRegionIds())
    }

    /**
     * 每个用例独立缓存和响应流，关闭自动响应后可手动模拟迟到消息或超时。
     * decodeSize 注入固定尺寸，frame 无需是有效 PNG；地图几何仍由生产逻辑校验。
     * 短 timeout 仅加速测试，生产等待仍为 30 秒；requests 保存地图/任务/request_id 便于关联回复。
     */
    private class Fixture(scope: CoroutineScope, timeout: Long = 1000) {
        val cache = TaskMapDisplayCache()
        val frames = MutableSharedFlow<MapImagePayload>(extraBufferCapacity = 8)
        val regions = MutableSharedFlow<MapRegionPointPayload>(extraBufferCapacity = 8)
        val paths = MutableSharedFlow<TaskPathPayload>(extraBufferCapacity = 8)
        val calls = mutableListOf<String>()
        val requests = mutableListOf<Triple<String, String, String>>()
        val frame = MapImagePayload(byteArrayOf(1, 2), 100, 50, 0.1f, 10.0, 20.0, 30f)
        val region = MapRegionPointPayload("map", 3, true, "",
            listOf(WorkRegionPointPayload(MapPreviewRegionPayload("work", "", emptyList()),
                MapRegionPosePayload(11f, 21f, 0f), null)), emptyList(), emptyList(), null)
        var path: TaskPathPayload? = TaskPathPayload("task", 7, "map", 90f,
            listOf(TaskPathPointPayload(11f, 21f, regionId = "work")), planned = false, mapId = "map")
        val snapshot = TaskMapDisplaySnapshot("task", "map", frame, 50 to 25, region,
            setOf("work"), listOf(TaskObstacleRegionConfig("temp", "", listOf(TaskPolygonPointConfig(11f, 21f)))),
            path, hasTaskConfiguration = true)
        var failure = ""
        val controller = DeviceTaskMapController(scope, cache, frames, regions, paths,
            sendMap = { mapId ->
                assertEquals(1, frames.subscriptionCount.value)
                calls += "map:$mapId"
                if (failure != "map-send") frames.tryEmit(if (failure == "geometry") frame.copy(resolution = 0f) else frame)
                failure != "map-send"
            }, sendRegions = { mapId ->
                assertEquals(1, regions.subscriptionCount.value)
                calls += "regions:$mapId"
                if (failure != "regions-send") regions.tryEmit(region.copy(mapId = mapId))
                failure != "regions-send"
            }, sendPath = { mapId, task, requestId ->
                assertEquals(1, paths.subscriptionCount.value)
                calls += "path:$mapId"; requests += Triple(mapId, task, requestId)
                if (failure != "path-send") path?.let { paths.tryEmit(it.copy(taskId = task, requestId = requestId)) }
                failure != "path-send"
            }, decodeSize = { 50 to 25 }, timeoutMs = timeout)

        fun warm() { cache.commit(cache.stage("task", snapshot), "task") }
        suspend fun open() { controller.onTask("task", 7); controller.setVisible(true); flush() }
        suspend fun flush() { repeat(15) { yield() } }
        // 仅等待各项加载结束，不代表成功；失败用例还需检查 error 与保留的数据。
        suspend fun ready() {
            flush()
            withTimeout(1500) { controller.state.first { !it.loadingMap && !it.loadingRegions && !it.loadingPath } }
        }
        suspend fun waitForRequests(count: Int) { withTimeout(1000) { while (requests.size < count) yield() } }
    }
}
