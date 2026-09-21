package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapImportToRadarPayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointPayload
import com.sinelynx.grindingrobot.core.model.state.PathPlanPayload
import com.sinelynx.grindingrobot.core.model.state.TaskConfigResponsePayload
import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskMapDisplaySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 一次开始研磨弹窗的显示状态：四个步骤共享 frame/regions，只有 Preview 持有本轮 plan。
 * 与建图会话、首页缩略图、设备页任务缓存相互独立；关闭弹窗后本状态清空。
 * canContinue/canStart 是交互门禁，统计缺失不是失败；错误与已有材料分开保存以便重试。
 */
data class StartGrindingSessionUiState(
    val mapId: String? = null,
    val frame: MapImagePayload? = null,
    val bitmapSize: Pair<Int, Int>? = null,
    val regions: MapRegionPointPayload? = null,
    val isMapLoading: Boolean = false,
    val isRegionsLoading: Boolean = false,
    val mapError: String? = null,
    val regionError: String? = null,
    val plan: StartGrindingPlanPreviewUiState? = null,
    val configuredTaskId: String? = null
) {
    val canContinue: Boolean get() = frame != null && bitmapSize != null && regions?.isSuccess == true &&
        !isMapLoading && !isRegionsLoading && mapError == null && regionError == null
    val canStart: Boolean get() = canContinue && plan?.isPlanning == false &&
        plan.errorMessage == null && plan.plannedPath?.hasRenderablePoints == true
    // 配置已确认即可对比旧图，即使新版路径为空；不能使用配置失败或上一轮的任务。
    val canPreviewLegacyPlan: Boolean get() = canContinue && !configuredTaskId.isNullOrBlank() &&
        plan?.isPlanning == false

    // 仅复用已有地图展示数据结构；图片只能来自本会话冻结帧，不经过 MapPreviewRequest。
    val mapPreview: MapPreviewUiState? get() = frame?.let { image ->
        MapPreviewUiState(
            imageBytes = image.imageBytes, mapWidth = image.mapWidth, mapHeight = image.mapHeight,
            resolution = image.resolution, originX = image.originX, originY = image.originY,
            headingDeg = image.headingDeg, previewScaleX = image.previewScaleX, previewScaleY = image.previewScaleY,
            workRegions = regions?.workRegions.orEmpty().filter { it.region.enabled }.map { it.region.toUiItem() },
            obstacleRegions = regions?.obstacleRegions.orEmpty().map { it.toUiItem() },
            eraseRegions = regions?.eraseRegions.orEmpty().map { it.toUiItem() },
            alignmentYawDeg = image.alignmentYawDeg, rotationAlignmentDeltaDeg = image.rotationAlignmentDeltaDeg
        )
    }
}

/**
 * 开始研磨弹窗的会话控制器，由 MapHomeViewModel 持有。
 *
 * 阅读顺序：open → 导入确认 → 单帧底图冻结 → 区域加载；进入 Preview 后 requestPlan
 * 刷新区域、提交配置并等待确认，最后按独立 request_id 获取路径。
 * Workspace/TaskParams/Obstacle 本地切换不重新取图；leavePreview 只使旧规划失效。
 * 用户点击开始时先 exportTaskDisplay 导出独立副本，再由 MapHomeViewModel 执行启动与交接。
 * close 只清理本编辑会话，不清理已导出的 TaskMapDisplayStream 缓存。
 *
 * 网络发送和响应流均由外部注入；本类负责时序和等待，不直接下发开始研磨指令。
 */
internal class StartGrindingSessionController(
    private val scope: CoroutineScope,
    private val imports: Flow<MapImportToRadarPayload>,
    private val frames: Flow<MapImagePayload>,
    private val regions: Flow<MapRegionPointPayload>,
    private val configs: Flow<TaskConfigResponsePayload>,
    private val paths: Flow<TaskPathPayload>,
    private val sendImport: (String) -> Boolean,
    private val sendMap: (String) -> Boolean,
    private val sendRegions: (String) -> Boolean,
    private val sendConfig: (taskId: String, taskName: String, mapId: String, repeats: Map<String, Int>, obstacles: List<TaskObstacleRegionConfig>) -> Boolean,
    private val sendPath: (mapId: String, taskId: String, requestId: String) -> Boolean,
    private val decodeSize: (ByteArray) -> Pair<Int, Int>?,
    private val prepareConfig: () -> Unit = {},
    private val timeoutMs: Long = 180_000L,
    legacyResponses: Flow<PathPlanPayload?> = emptyFlow(),
    sendLegacy: (mapId: String, taskId: String, requestId: String) -> Boolean = { _, _, _ -> false },
    decodeLegacySize: (ByteArray) -> Pair<Int, Int>? = decodeSize
) {
    private val _state = MutableStateFlow(StartGrindingSessionUiState())
    val state = _state.asStateFlow()
    // open/close 的本地会话编号，避免上一张地图的协程结果回填当前弹窗；不是设备地图版本。
    private var session = 0L
    private var mapJob: Job? = null
    private var regionJob: Job? = null
    private var planJob: Job? = null
    // 保存本轮预览的输入，供 matchesPlan 检查点击开始时是否仍是刚预览的那份任务。
    // inputTaskId 属于页面输入；设备确认后的 ID 单独保存在 state.configuredTaskId 中。
    private var inputTaskId: String? = null
    private var plannedRepeats: Map<String, Int> = emptyMap()
    private var plannedObstacles: List<TaskObstacleRegionConfig> = emptyList()

    // 复用 Step4 的请求关联和图片解析，但每个研磨会话拥有自己的加载器及弹窗状态。
    private val legacyLoader = Step4LegacyPreviewLoader(scope, legacyResponses, { mapId, requestId ->
        val current = state.value
        val taskId = current.configuredTaskId
        if (mapId == null || current.mapId != mapId || !current.canPreviewLegacyPlan || taskId == null) false
        else sendLegacy(mapId, taskId, requestId)
    }, decodeLegacySize, timeoutMs)
    val legacyPreview = legacyLoader.state

    fun openLegacyPreview() {
        if (state.value.canPreviewLegacyPlan) legacyLoader.open(state.value.mapId)
    }

    fun closeLegacyPreview() = legacyLoader.close()

    /** 每次打开都是新会话；先撤销上一会话的等待，再初始化指定地图。 */
    fun open(mapId: String) {
        close()
        _state.value = StartGrindingSessionUiState(mapId = mapId)
        retryMapOrRegions()
    }

    /** 清空弹窗自有材料和配置匹配记录；导出副本不再依赖这些对象的生命周期。 */
    fun close() {
        session++
        closeLegacyPreview()
        mapJob?.cancel()
        regionJob?.cancel()
        planJob?.cancel()
        inputTaskId = null
        plannedRepeats = emptyMap()
        plannedObstacles = emptyList()
        _state.value = StartGrindingSessionUiState()
    }

    /** 无底图时按导入→取图重试；已有底图时只刷新区域，保持编辑坐标基准不变。 */
    fun retryMapOrRegions() {
        val mapId = state.value.mapId ?: return
        if (state.value.frame != null) { refreshRegions(); return }
        mapJob?.cancel()
        val round = session
        _state.update { it.copy(isMapLoading = true, mapError = null) }
        mapJob = scope.launch {
            try {
                val imported = request(imports, { sendImport(mapId) }, "地图导入请求发送失败", "地图导入超时，请重试")
                if (!imported.isSuccess) throw LoadFailure(imported.message.ifBlank { "地图导入失败，请重试" })
                val frame = request(frames, { sendMap(mapId) }, "地图请求发送失败", "地图加载超时，请重试")
                val size = runCatching { decodeSize(frame.imageBytes) }.getOrNull()
                    ?.takeIf { it.first > 0 && it.second > 0 }
                    ?: throw LoadFailure("地图图片解码失败，请重试")
                if (frame.mapWidth <= 0 || frame.mapHeight <= 0 || !frame.resolution.isFinite() || frame.resolution <= 0f ||
                    !frame.originX.isFinite() || !frame.originY.isFinite() || !frame.headingDeg.isFinite() ||
                    !frame.alignmentYawDeg.isFinite() || !frame.rotationAlignmentDeltaDeg.isFinite()) {
                    throw LoadFailure("地图几何信息无效，请重试")
                }
                if (round != session) return@launch
                // 此后不再订阅地图帧；即使雷达同步触发新图，也不能改变正在编辑的坐标基准。
                _state.update { it.copy(frame = frame.copy(imageBytes = frame.imageBytes.copyOf()),
                    bitmapSize = size, isMapLoading = false) }
                refreshRegions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (round == session) _state.update { it.copy(isMapLoading = false,
                    mapError = failure.message ?: "地图加载失败，请重试") }
            }
        }
    }

    /** 返回编辑步骤后保留底图/区域，但旧路径不能再作为新配置的“已规划”凭证。 */
    fun leavePreview() {
        closeLegacyPreview()
        val wasPlanning = state.value.plan?.isPlanning == true
        planJob?.cancel()
        inputTaskId = null
        _state.update { it.copy(plan = null, configuredTaskId = null,
            isRegionsLoading = if (wasPlanning) false else it.isRegionsLoading) }
    }

    fun refreshRegions() {
        if (state.value.frame == null) return
        regionJob?.cancel()
        val round = session
        _state.update { it.copy(isRegionsLoading = true, regionError = null) }
        regionJob = scope.launch { loadRegions(round) }
    }

    private suspend fun loadRegions(round: Long): Boolean {
        val mapId = state.value.mapId ?: return false
        _state.update { it.copy(isRegionsLoading = true, regionError = null) }
        return try {
            val response = request(regions, { sendRegions(mapId) }, "区域请求发送失败", "区域加载超时，请重试") {
                it.mapId == mapId
            }
            if (!response.isSuccess) throw LoadFailure(response.message.ifBlank { "区域信息获取失败，请重试" })
            if (round != session) return false
            _state.update { it.copy(regions = response, isRegionsLoading = false) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (round == session) _state.update { it.copy(isRegionsLoading = false,
                regionError = failure.message ?: "区域信息获取失败，请重试") }
            false
        }
    }

    /**
     * 每次进入 Preview 或重试都执行完整规划前置链路，配置失败时不会发送路径请求。
     * 先保存此次输入副本；等待中配置若改变，后续 matchesPlan 会拒绝复用旧规划。
     * 面积/耗时来自本轮路径响应，缺失保持 null，不反推耗时或使用旧统计兜底。
     */
    fun requestPlan(taskId: String, taskName: String, repeats: Map<String, Int>, obstacles: List<TaskObstacleRegionConfig>) {
        val mapId = state.value.mapId ?: return
        if (state.value.frame == null || state.value.isMapLoading) return
        closeLegacyPreview()
        planJob?.cancel()
        regionJob?.cancel()
        val round = session
        inputTaskId = taskId
        plannedRepeats = repeats.toMap()
        plannedObstacles = obstacles.toList()
        _state.update { it.copy(configuredTaskId = null, plan = StartGrindingPlanPreviewUiState(isPlanning = true,
            taskId = taskId, regionRepeats = repeats.toMap())) }
        planJob = scope.launch {
            try {
                if (taskName.isBlank()) throw LoadFailure("请输入任务名称")
                // 在提交配置前刷新区域，不能把已删除或禁用的工作区悄悄替换成另一组任务输入。
                if (!loadRegions(round)) throw LoadFailure(state.value.regionError ?: "区域信息获取失败，请重试")
                val available = state.value.regions?.workRegions.orEmpty().filter { it.region.enabled }.map { it.region.regionId }.toSet()
                if (repeats.isEmpty()) throw LoadFailure("请至少选择一个工作区")
                if (!available.containsAll(repeats.keys)) throw LoadFailure("所选工作区已变更，请返回重新选择")
                prepareConfig()
                val config = request(configs, { sendConfig(taskId, taskName.trim(), mapId, repeats, obstacles) },
                    "任务配置发送失败", "任务配置响应超时") { it.taskId.isBlank() || it.taskId == taskId }
                if (!config.isSuccess) throw LoadFailure(config.message.ifBlank { "任务配置失败" })
                val resolvedId = config.taskId.ifBlank { taskId }
                _state.update { it.copy(configuredTaskId = resolvedId) }
                // 同一任务重试也必须使用全新 ID；旧请求即使任务/版本相同，也不能结束本轮等待。
                val requestId = UUID.randomUUID().toString()
                val path = request(paths, { sendPath(mapId, resolvedId, requestId) }, "路径规划请求发送失败", "路径规划未返回数据") {
                    it.requestId == requestId
                }
                if (round != session) return@launch
                _state.update { it.copy(plan = StartGrindingPlanPreviewUiState(
                    taskId = resolvedId, regionRepeats = repeats.toMap(), plannedPath = path.takeIf { it.hasRenderablePoints },
                    estimatedAreaM2 = path.totalWorkAreaM2, estimatedTimeH = step4EstimatedHours(path.estimatedTimeS),
                    errorMessage = step4PathError(true, path)
                )) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (round == session) _state.update { it.copy(plan = StartGrindingPlanPreviewUiState(
                    taskId = taskId, regionRepeats = repeats.toMap(),
                    errorMessage = failure.message ?: "路径规划未返回数据")) }
            }
        }
    }

    /** 只比较实际参与规划的任务 ID、遍数和临时禁区；统计缺失不会让可用规划失效。 */
    fun matchesPlan(taskId: String, repeats: Map<String, Int>, obstacles: List<TaskObstacleRegionConfig>): Boolean =
        state.value.canStart && inputTaskId == taskId && plannedRepeats == repeats && plannedObstacles == obstacles

    /**
     * 必须在关闭弹窗前导出；先确认配置与可用预览一致，再深拷贝图片与集合。
     * 此处不发布缓存、不发送任务指令，只把材料交给 MapHomeViewModel 的启动流程。
     * 之后 close() 清理本会话不会影响副本；hasTaskConfiguration 保留选区/临时禁区的来源语义。
     */
    fun exportTaskDisplay(taskId: String, repeats: Map<String, Int>, obstacles: List<TaskObstacleRegionConfig>): TaskMapDisplaySnapshot? {
        if (!matchesPlan(taskId, repeats, obstacles)) return null
        val current = state.value
        return TaskMapDisplaySnapshot(
            taskId = current.configuredTaskId ?: taskId, mapId = current.mapId ?: "LIVE_MAP",
            frame = current.frame, bitmapSize = current.bitmapSize, regions = current.regions,
            selectedRegionIds = repeats.keys.toSet(), temporaryObstacles = obstacles,
            path = current.plan?.plannedPath, hasTaskConfiguration = true
        ).detachedCopy()
    }

    /** 每个阶段先订阅再发送；发送失败时撤销等待，不使用旧缓存或按版本过滤本轮失败。 */
    private suspend fun <T> request(
        responses: Flow<T>, send: () -> Boolean, sendError: String, timeoutError: String,
        accept: (T) -> Boolean = { true }
    ): T = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) { responses.first(accept) }
        }
        if (!send()) { response.cancel(); throw LoadFailure(sendError) }
        response.await() ?: throw LoadFailure(timeoutError)
    }

    private class LoadFailure(message: String) : Exception(message)
}
