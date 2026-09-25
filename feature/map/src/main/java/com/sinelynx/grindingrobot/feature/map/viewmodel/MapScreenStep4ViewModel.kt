package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.database.datasource.MapDataSource
import com.sinelynx.grindingrobot.core.database.entity.MapEntity
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapSaveStream
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.core.model.state.WorkRegionPointPayload
import com.sinelynx.grindingrobot.core.model.state.PathPlanPayload
import com.sinelynx.grindingrobot.core.model.state.PathPlanStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

data class Step4SaveMapDraft(
    val mapId: String,
    val mapName: String,
    val estimatedAreaM2: Float,
    val estimatedDurationHours: Float,
)

data class MapScreenStep4UiState(
    val mapImageBytes: ByteArray? = null,
    val mapImageSize: Pair<Int, Int>? = null,
    val bitmapDecodeSize: Pair<Int, Int>? = null,
    val mapGeo: MapGeo? = null,
    val planningTaskId: String = "",
    val planningPathVersion: Int = 0,
    val plannedPath: TaskPathPayload? = null,
    val workRegions: List<WorkRegionPointPayload> = emptyList(),
    val previewWorkRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewObstacleRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewEraseRegions: List<MapPreviewRegionItem> = emptyList(),
    val isRegionsLoading: Boolean = false,
    val regionError: String? = null,
    val robotPose: DevicePosePayload? = null,
    val mapName: String = "",
    val estimatedAreaM2: Float? = null,
    val estimatedDurationHours: Float? = null,
    val isPlanning: Boolean = false,
    val planningError: String? = null,
    val isSaving: Boolean = false,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val robotFootprint: List<com.sinelynx.grindingrobot.core.data.state.AppState.FootprintPoint> = emptyList()
) {
    val canPreviewLegacyPlan: Boolean get() = !isPlanning && !isSaving
    // 统计值不参与保存门禁；只要求底图、区域加载完成且本轮存在有效路径坐标。
    val canSave: Boolean get() = mapImageBytes != null && mapGeo != null && !isPlanning &&
        !isRegionsLoading && !isSaving && regionError == null && planningError == null &&
        plannedPath?.hasRenderablePoints == true
}

/** 使用设备估时，APP 只做秒转小时；缺失保留 null，负值统一作为“--”的展示哨兵。 */
internal fun step4EstimatedHours(seconds: Float?): Float? =
    seconds?.takeIf { it.isFinite() }?.let { if (it >= 0f) it / 3600f else -1f }

internal fun step4PathError(sent: Boolean, path: TaskPathPayload?): String? = when {
    !sent -> "路径规划请求发送失败"
    path?.hasRenderablePoints != true -> "路径规划未返回数据"
    else -> null
}

/** 兼容数据库的非空字段：仅在保存边界把缺失统计转为 0，不提前覆盖页面的空白状态。 */
internal fun MapScreenStep4UiState.toSaveDraft(mapId: String) = Step4SaveMapDraft(
    mapId = mapId, mapName = mapName,
    estimatedAreaM2 = estimatedAreaM2 ?: 0f,
    estimatedDurationHours = estimatedDurationHours ?: 0f
)

/** 旧规划图对比专用状态，图片及几何均来自旧接口，不与建图快照或新版统计混用。 */
data class Step4LegacyPreviewUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val requestId: String = "",
    val imageBytes: ByteArray? = null,
    val bitmapSize: Pair<Int, Int>? = null,
    val mapGeo: MapGeo? = null,
    val error: String? = null,
    val warning: String? = null
) {
    val rotationDeg: Float get() = mapGeo?.let { it.alignmentYawDeg + it.rotationAlignmentDeltaDeg } ?: 0f
}

// 只管理对比弹窗；不写入建图会话或路径流，也不清空其他页面共用的旧接口响应。
internal class Step4LegacyPreviewLoader(
    private val scope: CoroutineScope,
    private val responses: Flow<PathPlanPayload?>,
    private val send: (mapId: String?, requestId: String) -> Boolean,
    private val decodeSize: (ByteArray) -> Pair<Int, Int>?,
    private val timeoutMs: Long = MAP_TCP_REQUEST_TIMEOUT_MS
) {
    private val _state = MutableStateFlow(Step4LegacyPreviewUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun open(mapId: String?) {
        job?.cancel()
        // 每次重试分配新 ID，排除 StateFlow 重放及上次请求的迟到响应；依赖设备原样回传 request_id。
        val requestId = UUID.randomUUID().toString()
        _state.value = Step4LegacyPreviewUiState(isVisible = true, isLoading = true, requestId = requestId)
        job = scope.launch {
            val reply = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(timeoutMs) {
                    responses.filterNotNull().first { it.requestId == requestId }
                }
            }
            val sent = send(mapId, requestId)
            val response = if (sent) reply.await() else { reply.cancel(); null }
            ensureActive()
            val result = when {
                !sent -> Step4LegacyPreviewUiState(error = "旧版路径规划请求发送失败")
                response == null -> Step4LegacyPreviewUiState(error = "旧版路径规划超时，未收到地图数据")
                else -> response.toLegacyPreview(decodeSize)
            }
            _state.update { current ->
                if (current.isVisible && current.requestId == requestId)
                    result.copy(isVisible = true, requestId = requestId) else current
            }
        }
    }

    /** 取消 APP 的等待并释放弹窗状态，不代表取消设备已经开始的规划。 */
    fun close() {
        job?.cancel()
        job = null
        _state.value = Step4LegacyPreviewUiState()
    }
}

/** 有可解码图片即可查看；统计/结果标记不阻断显示，几何不足时只禁用旋转和机器人定位。 */
internal fun PathPlanPayload.toLegacyPreview(decodeSize: (ByteArray) -> Pair<Int, Int>?): Step4LegacyPreviewUiState {
    val bytes = previewImageBytes?.takeIf { it.isNotEmpty() }
        ?: return Step4LegacyPreviewUiState(error = message.ifBlank { "旧版路径规划未返回预览图" })
    val size = runCatching { decodeSize(bytes) }.getOrNull()
        ?.takeIf { it.first > 0 && it.second > 0 }
        ?: return Step4LegacyPreviewUiState(error = "旧版规划图片解码失败")
    val responseOriginX = originX
    val responseOriginY = originY
    val geo = if (width > 0 && height > 0 && resolution.isFinite() && resolution > 0f &&
        responseOriginX?.isFinite() == true && responseOriginY?.isFinite() == true &&
        (headingDeg ?: 0f).isFinite() && alignmentYawDeg.isFinite() && rotationAlignmentDeltaDeg.isFinite()) {
        MapGeo(width, height, resolution, responseOriginX, responseOriginY, headingDeg ?: 0f, mapVersion,
            alignmentYawDeg = alignmentYawDeg, rotationAlignmentDeltaDeg = rotationAlignmentDeltaDeg)
    } else null
    return Step4LegacyPreviewUiState(imageBytes = bytes, bitmapSize = size, mapGeo = geo,
        warning = if (geo == null) "地图几何信息不可用，图片未旋转，机器人标记未显示" else null)
}

@HiltViewModel
class MapScreenStep4ViewModel @Inject constructor(
    private val mapDataSource: MapDataSource,
    private val tcpManager: TcpManager,
    private val appState: com.sinelynx.grindingrobot.core.data.state.AppState
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapScreenStep4UiState())
    val uiState: StateFlow<MapScreenStep4UiState> = _uiState.asStateFlow()

    private val legacyPreview = Step4LegacyPreviewLoader(
        scope = viewModelScope,
        responses = PathPlanStream.payload,
        send = { mapId, requestId ->
            // 只看旧接口生成的图片，关闭额外 0x0506 分片，避免干扰新版路径的本轮等待。
            tcpManager.requestPathPlan(mapId = mapId, returnPathChunks = false, requestId = requestId)
        },
        decodeSize = { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                (bitmap.width to bitmap.height).also { bitmap.recycle() }
            }
        }
    )
    val legacyPreviewUiState: StateFlow<Step4LegacyPreviewUiState> = legacyPreview.state

    fun openLegacyPreview(mapId: String? = null) {
        if (_uiState.value.canPreviewLegacyPlan) legacyPreview.open(mapId)
    }

    fun closeLegacyPreview() = legacyPreview.close()

    init {
        viewModelScope.launch {
            appState.robotSettings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        robotWidth = settings?.robotWidth,
                        robotLength = settings?.robotLength,
                        robotFootprint = settings?.footprint.orEmpty()
                    )
                }
            }
        }
        viewModelScope.launch {
            DeviceStatusStream.pose.collect { pose ->
                _uiState.update { it.copy(robotPose = pose) }
            }
        }
    }

    private var loadJob: Job? = null

    /** 每次进入重新查询区域和规划路径；图片固定取 Step1 快照，不能用旧成功路径结束本轮加载。 */
    fun loadMapData(mapId: String? = null) {
        closeLegacyPreview()
        loadJob?.cancel()
        val snapshot = MapBuildSessionStream.snapshotFor(mapId)
        if (snapshot == null) {
            _uiState.update { it.copy(mapImageBytes = null, mapGeo = null, plannedPath = null,
                isPlanning = false, isRegionsLoading = false,
                planningError = "地图缓存不存在，请返回第一步重新获取") }
            return
        }
        val frame = snapshot.frame
        _uiState.update { it.copy(mapImageBytes = frame.imageBytes, mapImageSize = frame.mapWidth to frame.mapHeight,
            bitmapDecodeSize = decodeImageSize(frame.imageBytes), mapGeo = frame.toMapGeo(),
            plannedPath = null, planningTaskId = "", planningPathVersion = 0,
            workRegions = emptyList(), previewWorkRegions = emptyList(), previewObstacleRegions = emptyList(),
            previewEraseRegions = emptyList(), isRegionsLoading = true, regionError = null,
            isPlanning = true, planningError = null, estimatedAreaM2 = null, estimatedDurationHours = null) }
        loadJob = viewModelScope.launch {
            launch {
                val regions = tcpManager.loadMapRegions(mapId)
                val error = if (regions?.isSuccess == true) null else
                    regions?.message?.ifBlank { null } ?: "区域信息获取失败，请重试"
                _uiState.update { it.copy(isRegionsLoading = false, regionError = error,
                    workRegions = regions?.workRegions.orEmpty(),
                    previewWorkRegions = regions?.workRegions.orEmpty().map { info -> info.region.toUiItem() },
                    previewObstacleRegions = regions?.obstacleRegions.orEmpty().map { region -> region.toUiItem() },
                    previewEraseRegions = regions?.eraseRegions.orEmpty().map { region -> region.toUiItem() }) }
            }
            launch {
                val (sent, result) = awaitStep4PathPointPlan(TaskPathStream.results, send = { requestId ->
                    tcpManager.requestPathPointPlan(taskId = "",
                        mapId = snapshot.requestedMapId.ifEmpty { "LIVE_MAP" },
                        requestId = requestId, forceReplan = true)
                })
                val error = step4PathError(sent, result)
                _uiState.update { it.copy(isPlanning = false, planningError = error,
                    plannedPath = result?.takeIf { path -> path.hasRenderablePoints },
                    planningTaskId = result?.taskId.orEmpty(), planningPathVersion = result?.pathVersion ?: 0,
                    estimatedAreaM2 = result?.totalWorkAreaM2,
                    estimatedDurationHours = step4EstimatedHours(result?.estimatedTimeS)) }
                if (error != null) ToastUtils.showError(error)
            }
        }
    }

    fun cancelMapLoading() {
        loadJob?.cancel()
        closeLegacyPreview()
    }

    /** 取消建图时同时通知板端停止 Super-LIO 建图节点。 */
    fun onCancelBuild() {
        cancelMapLoading()
        val sent = tcpManager.stopMappingMode()
        if (!sent) {
            LogUtils.w("MapScreenStep4ViewModel", "stopMappingMode 发送失败")
        }
    }

    fun updateMapName(mapName: String) {
        _uiState.update { it.copy(mapName = mapName) }
    }

    fun prefillMapName(mapName: String?) {
        val normalized = mapName?.trim().orEmpty()
        if (normalized.isEmpty()) return
        _uiState.update { state ->
            if (state.mapName.isBlank()) {
                state.copy(mapName = normalized)
            } else {
                state
            }
        }
    }

    fun updateAreaAndHours(estimatedAreaM2: Float, estimatedDurationHours: Float) {
        _uiState.update {
            it.copy(
                estimatedAreaM2 = estimatedAreaM2,
                estimatedDurationHours = estimatedDurationHours
            )
        }
    }

    fun onSaveClick(mapId: String? = null, onSave: (Step4SaveMapDraft) -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (!state.canSave || MapBuildSessionStream.snapshotFor(mapId) == null) {
            ToastUtils.showError(state.regionError ?: state.planningError ?: "地图区域或路径数据尚未完整")
            return
        }
        if (_uiState.value.mapName.isEmpty()) {
            ToastUtils.show("地图名称不得为空")
            return
        }
        if (_uiState.value.isSaving) return
        val draft = createSaveMapDraft(mapId)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            MapSaveStream.reset()
            val sent = tcpManager.requestSaveMap(draft.mapId, draft.mapName)
            if (!sent) {
                ToastUtils.show("地图保存请求发送失败")
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }

            val response = withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
                MapSaveStream.payload.filterNotNull().first()
            }
            if (response == null) {
                ToastUtils.show("地图保存超时，请重试")
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }
            if (!response.isSuccess) {
                ToastUtils.show(response.message.ifBlank { "地图保存失败" })
                _uiState.update { it.copy(isSaving = false) }
                return@launch
            }

            val persistSuccess = todoPersistSaveMapDraft(draft)
            _uiState.update { it.copy(isSaving = false) }
            if (persistSuccess) {
                val stopResult = if (response.mappingStopped) "建图节点已停止" else "建图停止未确认"
                val localizationResult = if (response.localizationStarted) "定位模式已启动" else "定位模式需单独启动"
                val revision = response.assetRevision.takeIf { it.isNotBlank() }
                    ?.let { "（revision ${it.take(12)}）" }
                    .orEmpty()
                val residuals = response.residualNodes.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "；残留节点：")
                    .orEmpty()
                ToastUtils.show("地图已保存$revision；$stopResult；$localizationResult$residuals")
                // 设备保存和本地持久化都成功后结束建图会话，防止下次建图继承旧图片和角度。
                MapBuildSessionStream.clear()
                onSave(draft)
            }
        }
    }

    private fun createSaveMapDraft(mapId: String? = null): Step4SaveMapDraft {
        val now = System.currentTimeMillis()
        return _uiState.value.toSaveDraft(mapId?.takeIf { it.isNotBlank() } ?: "map_${now}")
    }

    private fun decodeImageSize(bytes: ByteArray?): Pair<Int, Int>? {
        if (bytes == null) return null
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * 插入数据库保存数据
     */
    private suspend fun todoPersistSaveMapDraft(draft: Step4SaveMapDraft): Boolean {
        return runCatching {
            val now = System.currentTimeMillis()
            mapDataSource.insert(
                MapEntity(
                    mapId = draft.mapId,
                    mapName = draft.mapName,
                    mapTime = now,
                    mapArea = draft.estimatedAreaM2,
                    mapTimeConsuming = draft.estimatedDurationHours
                )
            )
            true
        }.onSuccess {
            LogUtils.d("MapScreenStep4ViewModel", "insert mapTask success: $draft")
        }.onFailure { throwable ->
            LogUtils.e("MapScreenStep4ViewModel", "insert mapTask failed, ${throwable.message}")
            ToastUtils.show("设备地图已保存，但 APP 本地地图记录保存失败")
        }.getOrDefault(false)
    }
}

/** 提前订阅并严格关联本轮请求，不按任务或版本过滤有效空结果。取消页面时一并取消等待。 */
internal suspend fun awaitStep4PathPointPlan(
    results: Flow<TaskPathPayload>,
    send: (requestId: String) -> Boolean,
    timeoutMs: Long = MAP_TCP_REQUEST_TIMEOUT_MS
): Pair<Boolean, TaskPathPayload?> = coroutineScope {
    val requestId = UUID.randomUUID().toString()
    val reply = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(timeoutMs) { results.first { it.requestId == requestId } }
    }
    val sent = send(requestId)
    sent to if (sent) reply.await() else { reply.cancel(); null }
}
