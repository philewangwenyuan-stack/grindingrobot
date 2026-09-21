package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.model.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class StartGrindingSessionControllerTest {
    @Test fun previewAndRetryTransmitNamesAndRejectBlankNames() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val names = mutableListOf<String>()
        val regions = MapRegionPointPayload("map", 1, true, "", listOf(
            WorkRegionPointPayload(MapPreviewRegionPayload("region", "区域", emptyList()), null, null)
        ), emptyList(), emptyList(), null)
        val controller = StartGrindingSessionController(
            scope = scope,
            imports = flowOf(MapImportToRadarPayload(0, true, "")),
            frames = flowOf(MapImagePayload(byteArrayOf(1), 10, 10, 1f, 0.0, 0.0, 0f)),
            regions = flowOf(regions),
            configs = flowOf(TaskConfigResponsePayload(0, false, "测试拒绝", "task")),
            paths = flowOf(),
            sendImport = { true }, sendMap = { true }, sendRegions = { true },
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
}
