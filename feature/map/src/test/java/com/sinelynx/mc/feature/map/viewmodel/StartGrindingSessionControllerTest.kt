package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.model.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class StartGrindingSessionControllerTest {
    @Test fun changingMapsWaitsForTheCancelledRequestToReleaseItsOwner() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var owned = false
        val released = CompletableDeferred<Unit>()
        fun regions(map: String) = MapRegionPointPayload(map, 1, true, "",
            emptyList(), emptyList(), emptyList(), null)
        val controller = StartGrindingSessionController(
            scope = scope,
            imports = flowOf(MapImportToRadarPayload(0, true, "", "old"),
                MapImportToRadarPayload(0, true, "", "new")),
            loadMap = { map ->
                if (map == "old") {
                    owned = true
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { delay(30); owned = false; released.complete(Unit) }
                    }
                }
                check(!owned) { "Previous map still owns the snapshot request" }
                MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f)
            },
            regions = flowOf(regions("old"), regions("new")), configs = flowOf(), paths = flowOf(),
            sendImport = { true }, sendRegions = { true },
            sendConfig = { _, _, _, _, _ -> false }, sendPath = { _, _, _ -> false },
            decodeSize = { 10 to 10 })
        try {
            controller.open("old")
            assertTrue(owned)
            controller.open("new")
            withTimeout(1000) { controller.state.first { it.canContinue } }
            assertTrue(released.isCompleted)
            assertEquals("new", controller.state.value.mapId)
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun aDifferentMapsImportResponseCannotStartTheSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val imports = kotlinx.coroutines.flow.MutableSharedFlow<MapImportToRadarPayload>()
        var snapshotLoads = 0
        val controller = StartGrindingSessionController(
            scope = scope, imports = imports,
            loadMap = {
                snapshotLoads++
                MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f)
            },
            regions = flowOf(MapRegionPointPayload("map", 1, true, "",
                emptyList(), emptyList(), emptyList(), null)),
            configs = flowOf(), paths = flowOf(), sendImport = { true }, sendRegions = { true },
            sendConfig = { _, _, _, _, _ -> false }, sendPath = { _, _, _ -> false },
            decodeSize = { 10 to 10 })
        try {
            controller.open("map")
            imports.emit(MapImportToRadarPayload(0, true, "", "other-map"))
            assertEquals(0, snapshotLoads)
            imports.emit(MapImportToRadarPayload(0, true, "", "map"))
            withTimeout(1000) { controller.state.first { it.canContinue } }
            assertEquals(1, snapshotLoads)
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun mapFailureCanBeRetriedOnTheSameMap() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failMap = true
        val controller = StartGrindingSessionController(
            scope = scope,
            imports = flowOf(MapImportToRadarPayload(0, true, "", mapId = "map")),
            loadMap = {
                if (failMap) throw java.io.IOException("地图数据连接已断开，请重试")
                MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f)
            },
            regions = flowOf(MapRegionPointPayload("map", 1, true, "", emptyList(), emptyList(), emptyList(), null)),
            configs = flowOf(), paths = flowOf(),
            sendImport = { true }, sendRegions = { true },
            sendConfig = { _, _, _, _, _ -> false }, sendPath = { _, _, _ -> false },
            decodeSize = { 10 to 10 })
        try {
            controller.open("map")
            assertFalse(controller.state.value.isMapLoading)
            assertEquals("地图数据连接已断开，请重试", controller.state.value.mapError)
            failMap = false
            controller.retryMapOrRegions()
            runBlocking { withTimeout(1000) { controller.state.first { it.canContinue } } }
            assertEquals("map", controller.state.value.mapId)
            assertNull(controller.state.value.mapError)
        } finally { controller.close(); scope.cancel() }
    }

    @Test fun previewAndRetryTransmitNamesAndRejectBlankNames() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val names = mutableListOf<String>()
        val regions = MapRegionPointPayload("map", 1, true, "", listOf(
            WorkRegionPointPayload(MapPreviewRegionPayload("region", "区域", emptyList()), null, null)
        ), emptyList(), emptyList(), null)
        val controller = StartGrindingSessionController(
            scope = scope,
            imports = flowOf(MapImportToRadarPayload(0, true, "", mapId = "map")),
            loadMap = { MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f) },
            regions = flowOf(regions),
            configs = flowOf(TaskConfigResponsePayload(0, false, "测试拒绝", "task")),
            paths = flowOf(),
            sendImport = { true }, sendRegions = { true },
            sendConfig = { id, name, map, repeats, _ ->
                assertEquals("task", id)
                assertEquals("map", map)
                assertEquals(mapOf("region" to 2), repeats)
                names += name
                true
            },
            sendPath = { _, _, _ -> fail("配置拒绝后不应规划"); false },
            decodeSize = { 10 to 10 }
        )
        try {
            controller.open("map")
            runBlocking { withTimeout(1000) { controller.state.first { it.canContinue } } }
            assertTrue(controller.state.value.canContinue)
            controller.requestPlan("task", "  车间研磨  ", mapOf("region" to 2), emptyList())
            controller.requestPlan("task", "重试名称", mapOf("region" to 2), emptyList())
            assertEquals(listOf("车间研磨", "重试名称"), names)
            controller.requestPlan("task", " \t", mapOf("region" to 2), emptyList())
            assertEquals(2, names.size)
            assertEquals("请输入任务名称", controller.state.value.plan?.errorMessage)
        } finally {
            controller.close()
            scope.cancel()
        }
    }
    @Test fun repeatedInFlightPlanIsCoalescedButChangedInputsReplaceWaiter() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var sends = 0
        val regions = MapRegionPointPayload("map", 1, true, "", listOf(
            WorkRegionPointPayload(MapPreviewRegionPayload("region", "区域", emptyList()), null, null)
        ), emptyList(), emptyList(), null)
        val controller = StartGrindingSessionController(
            scope=scope, imports=flowOf(MapImportToRadarPayload(0,true,"",mapId="map")),
            loadMap={MapImagePayload(byteArrayOf(1),10,10,1f,0.0,0.0,0f)},
            regions=flowOf(regions), configs=kotlinx.coroutines.flow.MutableSharedFlow(), paths=flowOf(),
            sendImport={true}, sendRegions={true},
            sendConfig={_,_,_,_,_-> sends++; true}, sendPath={_,_,_->false}, decodeSize={10 to 10})
        try {
            controller.open("map")
            runBlocking { withTimeout(1000) { controller.state.first { it.canContinue } } }
            controller.requestPlan("task","A",mapOf("region" to 1),emptyList())
            controller.requestPlan("task","A",mapOf("region" to 1),emptyList())
            assertEquals(1,sends)
            controller.requestPlan("task","B",mapOf("region" to 2),emptyList())
            assertEquals(2,sends)
        } finally { controller.close(); scope.cancel() }
    }

}
