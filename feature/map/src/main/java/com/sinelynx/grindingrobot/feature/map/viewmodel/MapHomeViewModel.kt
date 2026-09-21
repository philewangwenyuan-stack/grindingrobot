package com.sinelynx.grindingrobot.feature.map.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapCatalogStream
import com.sinelynx.grindingrobot.core.model.state.MapModeStream
import com.sinelynx.grindingrobot.core.model.state.MapDeleteStream
import com.sinelynx.grindingrobot.core.model.state.MapMetricsStream
import com.sinelynx.grindingrobot.core.model.state.MapPreviewStream
import com.sinelynx.grindingrobot.core.model.state.MapPreviewRegionPayload
import android.graphics.BitmapFactory
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.MapImportToRadarStream
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointStream
import com.sinelynx.grindingrobot.core.model.state.PathPlanStream
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import com.sinelynx.grindingrobot.core.model.state.TaskMapDisplayStream
import com.sinelynx.grindingrobot.core.model.state.RadarMapSyncResponseStream
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationResponseStream
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationStatusStream
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.core.model.state.TaskRegionRepeatConfig
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.feature.map.ui.DirectionCommand
import com.sinelynx.grindingrobot.feature.map.ui.JoystickPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sl_link.SlLink
import kotlin.math.roundToInt

data class MapHomeUiState(
    val maps: List<MapHomeListItem> = emptyList(),
    val metricsByMapId: Map<String, List<MapWorkspaceMetricsItem>> = emptyMap(),
    val previewByMapId: Map<String, MapPreviewUiState> = emptyMap(),
    val isPreviewLoading: Boolean = false,
    val grindingSession: StartGrindingSessionUiState = StartGrindingSessionUiState(),
    val grindingLegacyPreview: Step4LegacyPreviewUiState = Step4LegacyPreviewUiState(),
    val isMapCatalogLoading: Boolean = false,
    val isStartingMapping: Boolean = false,
    val mappingError: String? = null,
    val existingTaskNames: List<String> = emptyList(),
    val robotPose: DevicePosePayload? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val isRelocalizationDialogVisible: Boolean = false,
    val isRelocalizationSuccessful: Boolean = false,
    val relocalizationRawStatus: String = "",
    val relocalizationRunSpeed: Float = 0.15f,
    val relocalizationTurnSpeed: Int = 70,
    val relocalizationTurnCount: Int = 0,
    val relocalizationSettingsLoadState: RelocalizationSettingsLoadState =
        RelocalizationSettingsLoadState.Idle,
    val relocalizationSettingsError: String = "",
    val relocalizationCommand: DirectionCommand = DirectionCommand.Stop,
    val relocalizationPosition: JoystickPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f)
)

enum class MapModeStartEvent {
    Ready
}

internal fun isRelocalizationStatusSucceeded(rawStatus: String): Boolean =
    rawStatus == RADAR_RELOCALIZATION_SUCCEEDED_STATUS

internal data class PendingRadarResponseResult(
    val remainingCount: Int,
    val shouldShowSuccess: Boolean
)

internal fun consumePendingRadarResponse(
    pendingCount: Int,
    isDialogVisible: Boolean,
    isResponseSuccessful: Boolean
): PendingRadarResponseResult {
    if (!isDialogVisible || pendingCount <= 0) {
        return PendingRadarResponseResult(pendingCount.coerceAtLeast(0), false)
    }
    return PendingRadarResponseResult(
        remainingCount = pendingCount - 1,
        shouldShowSuccess = isResponseSuccessful
    )
}

internal fun canControlRelocalization(isDialogVisible: Boolean): Boolean = isDialogVisible

internal fun applyInitialRelocalizationChassisSettings(
    current: MapHomeUiState,
    settings: SlLink.ChassisSettings
): MapHomeUiState = current.copy(
    relocalizationRunSpeed = normalizeRelocalizationRunSpeed(settings.runSpeed),
    relocalizationTurnSpeed = (settings.maxTurnSpeedRatio * 100f).roundToInt()
        .coerceIn(MIN_RELOCALIZATION_TURN_SPEED, MAX_RELOCALIZATION_TURN_SPEED),
    relocalizationTurnCount = settings.discSpeedRpm.coerceIn(
        MIN_RELOCALIZATION_TURN_COUNT,
        MAX_RELOCALIZATION_TURN_COUNT
    ),
    relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Ready,
    relocalizationSettingsError = ""
)

internal fun consumeRelocalizationSettingsReadResponse(
    current: MapHomeUiState,
    response: AppState.SettingsReadResponseState
): MapHomeUiState {
    if (!current.isRelocalizationDialogVisible ||
        current.relocalizationSettingsLoadState != RelocalizationSettingsLoadState.Loading
    ) {
        return current
    }
    if (!response.isSuccess) {
        return current.copy(
            relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Error,
            relocalizationSettingsError = response.message.ifBlank {
                "底盘参数读取失败，请重试"
            }
        )
    }
    val settings = response.chassisSettings ?: return current.copy(
        relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Error,
        relocalizationSettingsError = "底盘参数响应不完整，请重试"
    )
    return applyInitialRelocalizationChassisSettings(current, settings)
}

enum class RelocalizationSettingsLoadState {
    Idle,
    Loading,
    Ready,
    Error
}

// 地图列表项
data class MapHomeListItem(
    val mapId: String,
    val mapName: String,
    val estimatedTimeS: Float?,
    val createTime: String?,
    val mapArea: Float?,
    val base64Image: String
)

data class MapWorkspaceMetricsItem(
    val regionId: String,
    val regionName: String,
    val repeat: Int,
    val areaM2: Float,
    val estimatedTimeH: Float
)

data class MapPreviewPointItem(
    val x: Float,
    val y: Float
)

data class MapPreviewRegionItem(
    val regionId: String,
    val regionName: String,
    val points: List<MapPreviewPointItem>,
    val enabled: Boolean = true
)

internal fun MapPreviewRegionPayload.toUiItem(): MapPreviewRegionItem {
    return MapPreviewRegionItem(
        regionId = regionId,
        regionName = regionName,
        points = points.map { point -> MapPreviewPointItem(x = point.x, y = point.y) },
        enabled = enabled
    )
}

data class StartGrindingPlanPreviewUiState(
    val estimatedAreaM2: Float? = null,
    val estimatedTimeH: Float? = null,
    val isPlanning: Boolean = false,
    val errorMessage: String? = null,
    val taskId: String = "",
    val regionRepeats: Map<String, Int> = emptyMap(),
    val plannedPath: TaskPathPayload? = null
)

data class MapPreviewUiState(
    val imageBytes: ByteArray,
    val mapWidth: Int,
    val mapHeight: Int,
    val resolution: Float,
    val originX: Double,
    val originY: Double,
    val headingDeg: Float,
    val previewScaleX: Float,
    val previewScaleY: Float,
    val workRegions: List<MapPreviewRegionItem>,
    val obstacleRegions: List<MapPreviewRegionItem> = emptyList(),
    val eraseRegions: List<MapPreviewRegionItem> = emptyList(),
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapPreviewUiState) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (mapWidth != other.mapWidth) return false
        if (mapHeight != other.mapHeight) return false
        if (resolution != other.resolution) return false
        if (originX != other.originX) return false
        if (originY != other.originY) return false
        if (headingDeg != other.headingDeg) return false
        if (previewScaleX != other.previewScaleX) return false
        if (previewScaleY != other.previewScaleY) return false
        if (workRegions != other.workRegions) return false
        if (obstacleRegions != other.obstacleRegions) return false
        if (eraseRegions != other.eraseRegions) return false
        if (alignmentYawDeg != other.alignmentYawDeg) return false
        if (rotationAlignmentDeltaDeg != other.rotationAlignmentDeltaDeg) return false
        return true
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mapWidth
        result = 31 * result + mapHeight
        result = 31 * result + resolution.hashCode()
        result = 31 * result + originX.hashCode()
        result = 31 * result + originY.hashCode()
        result = 31 * result + headingDeg.hashCode()
        result = 31 * result + previewScaleX.hashCode()
        result = 31 * result + previewScaleY.hashCode()
        result = 31 * result + workRegions.hashCode()
        result = 31 * result + obstacleRegions.hashCode()
        result = 31 * result + eraseRegions.hashCode()
        result = 31 * result + alignmentYawDeg.hashCode()
        result = 31 * result + rotationAlignmentDeltaDeg.hashCode()
        return result
    }
}

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapHomeUiState())
    val uiState: StateFlow<MapHomeUiState> = _uiState.asStateFlow()
    private val _mapModeEvents = MutableSharedFlow<MapModeStartEvent>(extraBufferCapacity = 1)
    val mapModeEvents: SharedFlow<MapModeStartEvent> = _mapModeEvents.asSharedFlow()
    private var mapCatalogTimeoutJob: Job? = null
    private var mapModeStartJob: Job? = null
    private var pendingPreviewMapId: String? = null
    private var mapPreviewTimeoutJob: Job? = null
    // 开始研磨弹窗拥有独立会话；首页的 previewByMapId 不参与底图冻结或设备页任务交接。
    // 此处只把协议流/发送函数接入控制器，具体等待顺序见 StartGrindingSessionController。
    private val grindingSession = StartGrindingSessionController(
        scope = viewModelScope,
        imports = MapImportToRadarStream.responses,
        frames = MapImageStream.frames,
        regions = MapRegionPointStream.responses,
        configs = TaskSchedulerStream.taskConfigResponse.filterNotNull(),
        paths = TaskPathStream.results,
        sendImport = { requestRadarMapImportOnce(it) },
        sendMap = { tcpManager.requestMapSnapshot(it) },
        sendRegions = { tcpManager.requestMapRegionPoints(it) },
        sendConfig = { taskId, taskName, mapId, repeats, obstacles ->
            tcpManager.requestTaskConfig(
                taskId = taskId, taskName = taskName, mapId = mapId,
                regionRepeats = repeats.toTaskRegionRepeatConfigs(), obstacles = obstacles
            )
        },
        prepareConfig = { TaskSchedulerStream.resetTaskConfigResponse() },
        sendPath = { mapId, taskId, requestId ->
            tcpManager.requestPathPointPlan(
                taskId = taskId, mapId = mapId,
                requestId = requestId, forceReplan = true
            )
        },
        legacyResponses = PathPlanStream.payload,
        sendLegacy = { mapId, taskId, requestId ->
            // 对比当前已确认的任务；不重新提交配置，也不让旧接口回传路径分片干扰新版结果流。
            tcpManager.requestPathPlan(
                mapId = mapId, taskId = taskId,
                returnPathChunks = false, requestId = requestId
            )
        },
        decodeLegacySize = { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                (bitmap.width to bitmap.height).also { bitmap.recycle() }
            }
        },
        decodeSize = { bytes ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            (options.outWidth to options.outHeight).takeIf { it.first > 0 && it.second > 0 }
        }
    )
    private var radarStatusPollingJob: Job? = null
    private var robotControlSendJob: Job? = null
    private var relocalizationSettingsReadJob: Job? = null
    private var pendingRadarMapSyncResponses: Int = 0
    private var pendingRadarRelocalizationResponses: Int = 0
    // 状态查询必须等上一条响应或超时后再发，避免板端队列堵塞时 APP 自己制造请求洪峰。
    private var radarStatusRequestInFlight = false
    private var radarStatusRequestSentAtMs = 0L
    // 同一张地图的导入请求只允许单飞；预览弹窗重复打开不会再次拉起导入流程。
    private var radarMapImportInFlightId: String? = null
    private var radarMapImportedId: String? = null
    private var radarMapImportJob: Job? = null

    init {
        viewModelScope.launch {
            grindingSession.state.collect { session -> _uiState.update { it.copy(grindingSession = session) } }
        }
        viewModelScope.launch {
            grindingSession.legacyPreview.collect { preview ->
                _uiState.update {
                    it.copy(
                        grindingLegacyPreview = preview
                    )
                }
            }
        }
        viewModelScope.launch {
            appState.robotSettings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        robotWidth = settings?.robotWidth,
                        robotLength = settings?.robotLength
                    )
                }
            }
        }
        viewModelScope.launch {
            RadarRelocalizationStatusStream.responses.collect { response ->
                radarStatusRequestInFlight = false
                val state = _uiState.value
                if (!state.isRelocalizationDialogVisible || state.isRelocalizationSuccessful) {
                    return@collect
                }
                if (isRelocalizationStatusSucceeded(response.rawStatus)) {
                    completeRelocalization()
                } else {
                    _uiState.update { it.copy(relocalizationRawStatus = response.rawStatus) }
                }
            }
        }
        viewModelScope.launch {
            RadarMapSyncResponseStream.responses.collect { response ->
                val result = consumePendingRadarResponse(
                    pendingCount = pendingRadarMapSyncResponses,
                    isDialogVisible = _uiState.value.isRelocalizationDialogVisible,
                    isResponseSuccessful = response.isSuccess
                )
                pendingRadarMapSyncResponses = result.remainingCount
                if (result.shouldShowSuccess) {
                    ToastUtils.showReplacingSuccess("同步指令已下发")
                }
            }
        }
        viewModelScope.launch {
            RadarRelocalizationResponseStream.responses.collect { response ->
                val result = consumePendingRadarResponse(
                    pendingCount = pendingRadarRelocalizationResponses,
                    isDialogVisible = _uiState.value.isRelocalizationDialogVisible,
                    isResponseSuccessful = response.isSuccess
                )
                pendingRadarRelocalizationResponses = result.remainingCount
                if (result.shouldShowSuccess) {
                    ToastUtils.showReplacingSuccess("重定位指令已下发")
                }
            }
        }
        viewModelScope.launch {
            DeviceStatusStream.pose.collect { pose ->
                _uiState.update { it.copy(robotPose = pose) }
            }
        }
        viewModelScope.launch {
            MapCatalogStream.payload.collect { payload ->
                payload ?: return@collect
                mapCatalogTimeoutJob?.cancel()
                if (!payload.isSuccess) {
                    _uiState.update { it.copy(isMapCatalogLoading = false) }
                    ToastUtils.show("获取任务列表失败")
                    return@collect
                }
                val items = payload.items.sortedByDescending { it.saveAt }.map { item ->
                    MapHomeListItem(
                        mapId = item.mapId,
                        mapName = item.name.ifBlank { "暂无名称" },
                        estimatedTimeS = item.estimatedTimeS,
                        createTime = item.createdAt,
                        mapArea = item.totalWorkAreaM2,
                        base64Image = item.base64Image
                    )
                }
                _uiState.update { it.copy(maps = items, isMapCatalogLoading = false) }
            }
        }
        viewModelScope.launch {
            MapMetricsStream.payload.collect { payload ->
                payload ?: return@collect
                if (!payload.isSuccess) {
                    ToastUtils.show(payload.message.ifBlank { "获取地图统计失败" })
                    return@collect
                }
                val metrics = payload.regions.map { region ->
                    MapWorkspaceMetricsItem(
                        regionId = region.regionId,
                        regionName = region.regionName.ifBlank { "区域${region.regionId}" },
                        repeat = region.repeat.coerceAtLeast(1),
                        areaM2 = region.areaM2,
                        estimatedTimeH = region.estimatedTimeH
                    )
                }
                _uiState.update { state ->
                    state.copy(metricsByMapId = state.metricsByMapId + (payload.mapId to metrics))
                }
            }
        }
        viewModelScope.launch {
            MapPreviewStream.payload.collect { payload ->
                payload ?: return@collect
                val mapId = pendingPreviewMapId ?: return@collect
                if (!_uiState.value.isPreviewLoading) return@collect
                if (payload.imageBytes.isEmpty()) {
                    pendingPreviewMapId = null
                    mapPreviewTimeoutJob?.cancel()
                    _uiState.update { state ->
                        state.copy(isPreviewLoading = false)
                    }
                    return@collect
                }
                mapPreviewTimeoutJob?.cancel()
                val preview = MapPreviewUiState(
                    imageBytes = payload.imageBytes,
                    mapWidth = payload.mapWidth,
                    mapHeight = payload.mapHeight,
                    resolution = payload.resolution,
                    originX = payload.originX,
                    originY = payload.originY,
                    headingDeg = payload.headingDeg,
                    previewScaleX = payload.previewScaleX,
                    previewScaleY = payload.previewScaleY,
                    workRegions = payload.workRegions.map { it.toUiItem() },
                    obstacleRegions = payload.obstacleRegions.map { it.toUiItem() },
                    eraseRegions = payload.eraseRegions.map { it.toUiItem() },
                    alignmentYawDeg = payload.alignmentYawDeg,
                    rotationAlignmentDeltaDeg = payload.rotationAlignmentDeltaDeg
                )
                _uiState.update { state ->
                    state.copy(
                        previewByMapId = state.previewByMapId + (mapId to preview),
                        isPreviewLoading = false
                    )
                }
                pendingPreviewMapId = null
            }
        }
        viewModelScope.launch {
            TaskSchedulerStream.taskResultResponse.collect { payload ->
                payload ?: return@collect
                val mapId = pendingPreviewMapId ?: payload.mapId.takeIf { it.isNotBlank() }
                ?: return@collect
                if (!_uiState.value.isPreviewLoading) return@collect
                val imageBytes = payload.imageBytes
                if (imageBytes == null || imageBytes.isEmpty()) {
                    pendingPreviewMapId = null
                    mapPreviewTimeoutJob?.cancel()
                    _uiState.update { state ->
                        state.copy(isPreviewLoading = false)
                    }
                    return@collect
                }
                mapPreviewTimeoutJob?.cancel()
                val preview = MapPreviewUiState(
                    imageBytes = imageBytes,
                    mapWidth = payload.imageWidth,
                    mapHeight = payload.imageHeight,
                    resolution = 1f,
                    originX = 0.0,
                    originY = 0.0,
                    headingDeg = 0f,
                    previewScaleX = 1f,
                    previewScaleY = 1f,
                    workRegions = emptyList()
                )
                _uiState.update { state ->
                    state.copy(
                        previewByMapId = state.previewByMapId + (mapId to preview),
                        isPreviewLoading = false
                    )
                }
                pendingPreviewMapId = null
            }
        }

    }

    fun startRelocalization() {
        stopRelocalizationJobs(sendStop = false)
        clearPendingRadarOperationResponses()
        _uiState.update {
            it.copy(
                isRelocalizationDialogVisible = true,
                isRelocalizationSuccessful = false,
                relocalizationRawStatus = "",
                relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Loading,
                relocalizationSettingsError = "",
                relocalizationCommand = DirectionCommand.Stop,
                relocalizationPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f)
            )
        }
        requestRelocalizationSettings()
        radarStatusPollingJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (!state.isRelocalizationDialogVisible || state.isRelocalizationSuccessful) break
                val now = System.currentTimeMillis()
                if (radarStatusRequestInFlight &&
                    now - radarStatusRequestSentAtMs >= RADAR_STATUS_RESPONSE_TIMEOUT_MS
                ) {
                    // 允许恢复，但任何时刻最多保留一条未确认的状态请求。
                    radarStatusRequestInFlight = false
                }
                if (!radarStatusRequestInFlight && tcpManager.requestRadarRelocalizationStatus()) {
                    radarStatusRequestInFlight = true
                    radarStatusRequestSentAtMs = now
                }
                delay(RADAR_STATUS_POLL_INTERVAL_MS)
            }
        }
    }

    fun retryRelocalizationSettingsRead() {
        if (!_uiState.value.isRelocalizationDialogVisible) return
        requestRelocalizationSettings()
    }

    private fun requestRelocalizationSettings() {
        relocalizationSettingsReadJob?.cancel()
        _uiState.update {
            it.copy(
                relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Loading,
                relocalizationSettingsError = ""
            )
        }
        val readJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val response = withTimeoutOrNull(RELOCALIZATION_SETTINGS_READ_TIMEOUT_MS) {
                appState.settingsReadResponses.first()
            }
            if (!_uiState.value.isRelocalizationDialogVisible) return@launch
            if (response == null) {
                showRelocalizationSettingsError("底盘参数读取超时，请重试")
            } else {
                _uiState.update { current ->
                    consumeRelocalizationSettingsReadResponse(current, response)
                }
            }
        }
        relocalizationSettingsReadJob = readJob
        if (!tcpManager.requestSettingRead(true, null)) {
            readJob.cancel()
            showRelocalizationSettingsError("底盘参数读取请求发送失败，请重试")
        }
    }

    private fun showRelocalizationSettingsError(message: String) {
        _uiState.update {
            it.copy(
                relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Error,
                relocalizationSettingsError = message
            )
        }
    }

    fun requestRelocalizationRadarMapSync() {
        if (!_uiState.value.isRelocalizationDialogVisible) return
        if (pendingRadarMapSyncResponses > 0) return
        val sent = tcpManager.requestRadarMapSync()
        if (sent) {
            pendingRadarMapSyncResponses++
        } else {
            LogUtils.w("MapHomeViewModel", "requestRadarMapSync 发送失败")
        }
    }

    fun requestRadarRelocalization(
        xMeters: Float,
        yMeters: Float,
        headingDegrees: Float
    ) {
        if (!_uiState.value.isRelocalizationDialogVisible) return
        if (pendingRadarRelocalizationResponses > 0) return
        val sent = tcpManager.requestRadarRelocalization(
            xMeters = xMeters,
            yMeters = yMeters,
            headingDegrees = headingDegrees
        )
        if (sent) {
            pendingRadarRelocalizationResponses++
            ToastUtils.show("初始位置已下发，正在重定位")
        } else {
            LogUtils.w("MapHomeViewModel", "requestRadarRelocalization 发送失败")
            ToastUtils.show("重定位请求发送失败")
        }
    }

    fun dismissRelocalization() {
        stopRelocalizationJobs(sendStop = true)
        clearPendingRadarOperationResponses()
        _uiState.update {
            it.copy(
                isRelocalizationDialogVisible = false,
                isRelocalizationSuccessful = false,
                relocalizationRawStatus = "",
                relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Idle,
                relocalizationSettingsError = "",
                relocalizationCommand = DirectionCommand.Stop,
                relocalizationPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f)
            )
        }
    }

    private fun completeRelocalization() {
        stopRelocalizationJobs(sendStop = true)
        _uiState.update {
            it.copy(
                isRelocalizationSuccessful = true,
                relocalizationRawStatus = "",
                relocalizationCommand = DirectionCommand.Stop,
                relocalizationPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f)
            )
        }
    }

    fun increaseRelocalizationRunSpeed() {
        updateRelocalizationRunSpeed(_uiState.value.relocalizationRunSpeed + RELOCALIZATION_RUN_SPEED_STEP)
    }

    fun decreaseRelocalizationRunSpeed() {
        updateRelocalizationRunSpeed(_uiState.value.relocalizationRunSpeed - RELOCALIZATION_RUN_SPEED_STEP)
    }

    private fun updateRelocalizationRunSpeed(value: Float) {
        if (!canControlRelocalization()) return
        val normalized = normalizeRelocalizationRunSpeed(value)
        _uiState.update { it.copy(relocalizationRunSpeed = normalized) }
    }

    fun increaseRelocalizationTurnSpeed() {
        if (!canControlRelocalization()) return
        val newSpeed = (_uiState.value.relocalizationTurnSpeed + RELOCALIZATION_TURN_SPEED_STEP)
            .coerceAtMost(MAX_RELOCALIZATION_TURN_SPEED)
        _uiState.update { it.copy(relocalizationTurnSpeed = newSpeed) }
        writeRelocalizationTurnSpeed(newSpeed)
    }

    fun decreaseRelocalizationTurnSpeed() {
        if (!canControlRelocalization()) return
        val newSpeed = (_uiState.value.relocalizationTurnSpeed - RELOCALIZATION_TURN_SPEED_STEP)
            .coerceAtLeast(MIN_RELOCALIZATION_TURN_SPEED)
        _uiState.update { it.copy(relocalizationTurnSpeed = newSpeed) }
        writeRelocalizationTurnSpeed(newSpeed)
    }

    private fun writeRelocalizationTurnSpeed(turnSpeedPercent: Int) {
        val maxTurnSpeedRatio = (turnSpeedPercent / 100f)
            .coerceIn(MIN_RELOCALIZATION_TURN_SPEED_RATIO, MAX_RELOCALIZATION_TURN_SPEED_RATIO)
        val settings = SlLink.ChassisSettings.newBuilder()
            .setMaxTurnSpeedRatio(maxTurnSpeedRatio)
            .build()
        LogUtils.d(
            "MapHomeViewModel",
            "requestSettingWrite ChassisSettings: maxTurnSpeedRatio=${settings.maxTurnSpeedRatio}"
        )
        val sent = tcpManager.requestSettingWrite(chassisSettings = settings)
        if (!sent) {
            LogUtils.w(
                "MapHomeViewModel",
                "requestSettingWrite relocalization maxTurnSpeedRatio 发送失败"
            )
        }
    }

    fun increaseRelocalizationTurnCount() {
        if (!canControlRelocalization()) return
        _uiState.update {
            it.copy(
                relocalizationTurnCount = (it.relocalizationTurnCount + RELOCALIZATION_TURN_COUNT_STEP)
                    .coerceAtMost(MAX_RELOCALIZATION_TURN_COUNT)
            )
        }
        writeRelocalizationSettings()
    }

    fun decreaseRelocalizationTurnCount() {
        if (!canControlRelocalization()) return
        _uiState.update {
            it.copy(
                relocalizationTurnCount = (it.relocalizationTurnCount - RELOCALIZATION_TURN_COUNT_STEP)
                    .coerceAtLeast(MIN_RELOCALIZATION_TURN_COUNT)
            )
        }
        writeRelocalizationSettings()
    }

    fun updateRelocalizationTurnCountDraft(value: Float) {
        if (!canControlRelocalization()) return
        val normalized = (value / RELOCALIZATION_TURN_COUNT_STEP).roundToInt()
            .times(RELOCALIZATION_TURN_COUNT_STEP)
            .coerceIn(MIN_RELOCALIZATION_TURN_COUNT, MAX_RELOCALIZATION_TURN_COUNT)
        _uiState.update { it.copy(relocalizationTurnCount = normalized) }
    }

    fun commitRelocalizationTurnCount() {
        if (canControlRelocalization()) writeRelocalizationSettings()
    }

    private fun writeRelocalizationSettings() {
        val state = _uiState.value
        val settings = SlLink.ChassisSettings.newBuilder()
            .setDiscEnabled(true)
            .setDiscSpeedRpm(state.relocalizationTurnCount)
            .build()
        LogUtils.d(
            "MapHomeViewModel",
            "requestSettingWrite ChassisSettings: discEnabled=${settings.discEnabled}, " +
                "discSpeedRpm=${settings.discSpeedRpm}"
        )
        tcpManager.requestSettingWrite(settings)
    }

    fun onRelocalizationCommandStart(command: DirectionCommand) {
        if (!canControlRelocalization()) return
        _uiState.update { it.copy(relocalizationCommand = command) }
    }

    fun onRelocalizationCommandEnd(command: DirectionCommand) {
        _uiState.update { state ->
            if (state.relocalizationCommand == command) {
                state.copy(relocalizationCommand = DirectionCommand.Stop)
            } else {
                state
            }
        }
    }

    fun onRelocalizationPositionChanged(position: JoystickPosition) {
        if (!canControlRelocalization()) return
        _uiState.update { it.copy(relocalizationPosition = position) }
        if (position.distanceRatio <= 0f) {
            stopRobotControlSend()
            sendRelocalizationPosition(position)
            return
        }
        sendRelocalizationPosition(position)
        if (robotControlSendJob?.isActive == true) return
        robotControlSendJob = viewModelScope.launch {
            while (isActive) {
                delay(ROBOT_CONTROL_SEND_INTERVAL_MS)
                val state = _uiState.value
                if (!canControlRelocalization() || state.relocalizationPosition.distanceRatio <= 0f) break
                sendRelocalizationPosition(state.relocalizationPosition)
            }
        }
    }

    private fun sendRelocalizationPosition(position: JoystickPosition) {
        val speedRatio = (position.distanceRatio * 100).roundToInt() / 100f
        tcpManager.sendRobotControlCommand(
            position.normalizedX,
            position.normalizedY,
            speedRatio,
            _uiState.value.relocalizationTurnSpeed.toFloat(),
            maxSpeedMps = _uiState.value.relocalizationRunSpeed
        )
    }

    private fun canControlRelocalization(): Boolean {
        val state = _uiState.value
        return canControlRelocalization(state.isRelocalizationDialogVisible) &&
            state.relocalizationSettingsLoadState == RelocalizationSettingsLoadState.Ready
    }

    private fun clearPendingRadarOperationResponses() {
        pendingRadarMapSyncResponses = 0
        pendingRadarRelocalizationResponses = 0
        radarStatusRequestInFlight = false
        radarStatusRequestSentAtMs = 0L
    }

    private fun stopRelocalizationJobs(sendStop: Boolean) {
        relocalizationSettingsReadJob?.cancel()
        relocalizationSettingsReadJob = null
        radarStatusPollingJob?.cancel()
        radarStatusPollingJob = null
        radarStatusRequestInFlight = false
        radarStatusRequestSentAtMs = 0L
        stopRobotControlSend()
        if (sendStop) {
            tcpManager.sendRobotControlCommand(
                0f,
                0f,
                0f,
                _uiState.value.relocalizationTurnSpeed.toFloat(),
                maxSpeedMps = _uiState.value.relocalizationRunSpeed
            )
        }
    }

    private fun stopRobotControlSend() {
        robotControlSendJob?.cancel()
        robotControlSendJob = null
    }

    fun requestMapCatalogOnEnter() {
        MapCatalogStream.reset()
        _uiState.update { it.copy(isMapCatalogLoading = true) }
        val sent = tcpManager.requestMapCatalog()
        if (!sent) {
            _uiState.update { it.copy(isMapCatalogLoading = false) }
            ToastUtils.show("获取任务列表失败")
            return
        }

        mapCatalogTimeoutJob?.cancel()
        mapCatalogTimeoutJob = viewModelScope.launch {
            delay(MAP_TCP_REQUEST_TIMEOUT_MS)
            if (_uiState.value.isMapCatalogLoading) {
                _uiState.update { it.copy(isMapCatalogLoading = false) }
                ToastUtils.show("获取任务列表失败")
            }
        }
    }

    /**
     * 新建地图前先让设备进入建图模式，收到 0x0513 成功响应后再通知页面导航。
     */
    fun startMappingMode() {
        // 先同步置位再启动协程，修复快速双击时两个协程都通过旧状态检查的问题。
        if (_uiState.value.isStartingMapping || mapModeStartJob?.isActive == true) return
        _uiState.update { it.copy(isStartingMapping = true, mappingError = null) }

        mapModeStartJob = viewModelScope.launch {
            MapModeStream.reset()

            val sent = tcpManager.requestMapMode(
                mode = SlLink.MapModeType.MAP_MODE_MAPPING,
                enabled = true,
                mapKind = 0
            )
            if (!sent) {
                finishMappingStart("建图模式请求发送失败")
                return@launch
            }

            val response = withTimeoutOrNull(MAP_MODE_REQUEST_TIMEOUT_MS) {
                MapModeStream.payload.filterNotNull().first()
            }
            if (response == null) {
                finishMappingStart("启动建图超时，请重试")
                return@launch
            }
            if (!response.isSuccess) {
                finishMappingStart(response.message.ifBlank { "启动建图失败" })
                return@launch
            }

            _uiState.update { it.copy(isStartingMapping = false, mappingError = null) }
            _mapModeEvents.emit(MapModeStartEvent.Ready)
        }
    }

    private fun finishMappingStart(message: String) {
        _uiState.update {
            it.copy(
                isStartingMapping = false,
                mappingError = message
            )
        }
        ToastUtils.show(message)
    }

    fun deleteMapByMapId(mapId: String) {
        viewModelScope.launch {
            MapDeleteStream.reset()
            val sent = tcpManager.requestDeleteMap(mapId)
            if (!sent) {
                ToastUtils.show("删除地图失败")
                return@launch
            }

            val response = withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
                MapDeleteStream.payload.filterNotNull().first()
            }
            if (response == null || !response.isSuccess && !response.localDeleted) {
                ToastUtils.show(response?.message?.ifBlank { "删除地图失败" } ?: "删除地图失败")
                return@launch
            }

            _uiState.update { state ->
                state.copy(maps = state.maps.filterNot { it.mapId == mapId })
            }
            if (response.remoteDeletePending) {
                ToastUtils.show("地图已在本地删除，云端删除将在后台重试")
            }
        }
    }

    fun requestMapMetrics(mapId: String) {
        MapMetricsStream.reset()
        tcpManager.requestMapMetrics(mapId)
    }

    fun requestMapPreview(mapId: String) {
        if (!mapId.isNullOrBlank()) {
            val importSent = requestRadarMapImportOnce(mapId)
            if (!importSent) {
                LogUtils.w("MapHomeViewModel", "requestMapImportToRadar 发送失败")
            }
        }
        pendingPreviewMapId = mapId
        MapPreviewStream.reset()
        mapPreviewTimeoutJob?.cancel()
        _uiState.update { state ->
            state.copy(
                previewByMapId = state.previewByMapId - mapId,
                isPreviewLoading = true
            )
        }
        val sent = tcpManager.requestMapPreview(mapId)
        if (!sent) {
            pendingPreviewMapId = null
            _uiState.update { state ->
                state.copy(isPreviewLoading = false)
            }
            return
        }
        mapPreviewTimeoutJob = viewModelScope.launch {
            delay(MAP_TCP_REQUEST_TIMEOUT_MS)
            pendingPreviewMapId = null
            _uiState.update { state ->
                state.copy(isPreviewLoading = false)
            }
        }
    }

    /**
     * 发送地图导入并在短时间内去重。返回 true 表示请求已发送或已有同地图请求在途，
     * 调用方可以继续等待共享的 MapImportToRadarStream 响应。
     */
    private fun requestRadarMapImportOnce(mapId: String): Boolean {
        if (mapId.isBlank()) return false
        if (radarMapImportedId == mapId || radarMapImportInFlightId == mapId) return true
        if (radarMapImportInFlightId != null) return false

        MapImportToRadarStream.reset()
        radarMapImportInFlightId = mapId
        radarMapImportJob?.cancel()
        radarMapImportJob = viewModelScope.launch {
            val response = withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
                MapImportToRadarStream.responses.first()
            }
            if (response?.isSuccess == true) radarMapImportedId = mapId
            if (radarMapImportInFlightId == mapId) radarMapImportInFlightId = null
        }
        if (!tcpManager.requestMapImportToRadar(mapId)) {
            radarMapImportJob?.cancel()
            radarMapImportJob = null
            radarMapImportInFlightId = null
            return false
        }
        return true
    }

    fun requestTaskResultPreview(mapId: String) {
        pendingPreviewMapId = mapId
        TaskSchedulerStream.resetTaskResultResponse()
        mapPreviewTimeoutJob?.cancel()
        _uiState.update { state ->
            state.copy(
                previewByMapId = state.previewByMapId - mapId,
                isPreviewLoading = true
            )
        }
        val sent = tcpManager.requestTaskResult(mapId = mapId, taskId = "")
        if (!sent) {
            pendingPreviewMapId = null
            _uiState.update { state ->
                state.copy(isPreviewLoading = false)
            }
            return
        }
        mapPreviewTimeoutJob = viewModelScope.launch {
            delay(MAP_TCP_REQUEST_TIMEOUT_MS)
            pendingPreviewMapId = null
            _uiState.update { state ->
                state.copy(isPreviewLoading = false)
            }
        }
    }

    fun dismissMapPreview() {
        pendingPreviewMapId = null
        mapPreviewTimeoutJob?.cancel()
        _uiState.update { state ->
            state.copy(isPreviewLoading = false)
        }
    }

    fun openStartGrinding(mapId: String) {
        grindingSession.open(mapId)
    }

    fun closeStartGrinding() {
        grindingSession.close()
    }

    fun retryStartGrindingMap() {
        grindingSession.retryMapOrRegions()
    }

    fun leaveStartGrindingPreview() {
        grindingSession.leavePreview()
    }

    fun openStartGrindingLegacyPreview() {
        grindingSession.openLegacyPreview()
    }

    fun closeStartGrindingLegacyPreview() {
        grindingSession.closeLegacyPreview()
    }

    fun requestStartGrindingPlanPreview(
        mapId: String,
        taskId: String,
        taskName: String,
        regionRepeats: Map<String, Int>,
        obstacles: List<TaskObstacleRegionConfig> = emptyList()
    ) {
        if (taskName.isBlank()) {
            ToastUtils.show("请输入任务名称")
            return
        }
        if (grindingSession.state.value.mapId == mapId) {
            grindingSession.requestPlan(taskId, taskName.trim(), regionRepeats, obstacles)
        }
    }

    fun sendGrindingTaskConfig(mapId: String, taskId: String, taskName: String, regionRepeats: Map<String, Int>) {
        if (taskName.isBlank()) {
            ToastUtils.show("请输入任务名称")
            return
        }
        if (regionRepeats.isEmpty()) {
            ToastUtils.show("请至少选择一个工作区")
            return
        }

        val sent = tcpManager.requestTaskConfig(
            taskId = taskId,
            taskName = taskName.trim(),
            mapId = mapId,
            regionRepeats = regionRepeats.toTaskRegionRepeatConfigs()
        )
        if (!sent) {
            ToastUtils.show("任务配置发送失败")
        }
    }

    /**
     * 用户点击“开始研磨”的入口：核对预览输入 → 导出并暂存候选 → 发送配置 → 等确认
     * → 发送开始指令 → 提交设备页显示缓存。保留原有先配置、后启动的执行链路。
     *
     * 返回 true 仅表示配置已发送，调用页面可以关闭弹窗；不代表设备已确认或已开始运行。
     * 因而候选必须在 return 之前建立，后续协程只使用其独立副本，不再依赖已清空的编辑会话。
     */
    fun startGrindingTask(
        mapId: String,
        taskId: String,
        taskName: String,
        regionRepeats: Map<String, Int>,
        obstacles: List<TaskObstacleRegionConfig> = emptyList()
    ): Boolean {
        if (taskName.isBlank()) {
            ToastUtils.show("请输入任务名称")
            return false
        }
        if (grindingSession.state.value.mapId != mapId ||
            !grindingSession.matchesPlan(taskId, regionRepeats, obstacles)
        ) {
            ToastUtils.show("请等待当前任务预览完成后再开始研磨")
            return false
        }
        if (regionRepeats.isEmpty()) {
            ToastUtils.show("请至少选择一个工作区")
            return false
        }

        // stage 让设备页知道同一任务正在交接，避免在 Preview 刚关闭时立即重复请求地图。
        val display =
            grindingSession.exportTaskDisplay(taskId, regionRepeats, obstacles) ?: return false
        val handoff = TaskMapDisplayStream.cache.stage(taskId, display)
        TaskSchedulerStream.resetTaskConfigResponse()
        val sent = tcpManager.requestTaskConfig(
            taskId = taskId,
            taskName = taskName.trim(),
            mapId = mapId,
            regionRepeats = regionRepeats.toTaskRegionRepeatConfigs(),
            obstacles = obstacles
        )
        if (!sent) {
            TaskMapDisplayStream.cache.discard(handoff)
            ToastUtils.show("任务配置发送失败")
            return false
        }

        // 使用 ViewModel 作用域而非弹窗作用域：界面关闭后仍需等配置确认并完成启动发送。
        val startJob = viewModelScope.launch {
            val configResponse = withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
                TaskSchedulerStream.taskConfigResponse
                    .filterNotNull()
                    .first { it.taskId.isBlank() || it.taskId == taskId }
            }
            if (configResponse == null) {
                ToastUtils.show("任务配置响应超时")
                return@launch
            }
            if (!configResponse.isSuccess) {
                ToastUtils.show(configResponse.message.ifBlank { "任务配置失败" })
                return@launch
            }

            // 后续启动与缓存都采用确认后的任务 ID，空 ID 沿用输入值，不伪造新的任务身份。
            val startTaskId = configResponse.taskId.ifBlank { taskId }
            val startSent = tcpManager.sendTaskCommand(
                taskType = SlLink.TaskCommandType.TASK_CMD_START,
                taskId = startTaskId
            )
            // 这里只交接显示数据，任务是否真正运行仍由 0x0504 上报决定。
            if (startSent) TaskMapDisplayStream.cache.commit(handoff, startTaskId)
            ToastUtils.show(if (startSent) "开始研磨指令已发送" else "开始研磨指令发送失败")
        }
        // 包含配置拒绝/超时、发送失败及 ViewModel 取消；成功提交后 discard 是空操作。
        // 只撤销当前 token，不能把后来一次启动创建的候选一并清掉。
        startJob.invokeOnCompletion { TaskMapDisplayStream.cache.discard(handoff) }

        ToastUtils.show("任务配置已发送")
        return sent
    }

    private fun Map<String, Int>.toTaskRegionRepeatConfigs(): List<TaskRegionRepeatConfig> {
        return map { (regionId, repeat) ->
            TaskRegionRepeatConfig(
                regionId = regionId,
                repeat = repeat.coerceAtLeast(1)
            )
        }
    }

    override fun onCleared() {
        stopRelocalizationJobs(sendStop = true)
        mapCatalogTimeoutJob?.cancel()
        mapPreviewTimeoutJob?.cancel()
        mapModeStartJob?.cancel()
        radarMapImportJob?.cancel()
        grindingSession.close()
        super.onCleared()
    }
}

private fun normalizeRelocalizationRunSpeed(value: Float): Float {
    return (value / RELOCALIZATION_RUN_SPEED_STEP).roundToInt()
        .times(RELOCALIZATION_RUN_SPEED_STEP)
        .coerceIn(MIN_RELOCALIZATION_RUN_SPEED, MAX_RELOCALIZATION_RUN_SPEED)
}

private const val RADAR_STATUS_POLL_INTERVAL_MS = 1_000L
private const val RADAR_STATUS_RESPONSE_TIMEOUT_MS = 3_000L
private const val RELOCALIZATION_SETTINGS_READ_TIMEOUT_MS = 5_000L
private const val ROBOT_CONTROL_SEND_INTERVAL_MS = 200L
private const val RADAR_RELOCALIZATION_SUCCEEDED_STATUS = "RelocalizationSucceed"
private const val MIN_RELOCALIZATION_RUN_SPEED = 0f
private const val MAX_RELOCALIZATION_RUN_SPEED = 0.15f
private const val RELOCALIZATION_RUN_SPEED_STEP = 0.05f
private const val MIN_RELOCALIZATION_TURN_SPEED = 1
private const val MAX_RELOCALIZATION_TURN_SPEED = 100
private const val RELOCALIZATION_TURN_SPEED_STEP = 5
private const val MIN_RELOCALIZATION_TURN_SPEED_RATIO = 0.01f
private const val MAX_RELOCALIZATION_TURN_SPEED_RATIO = 1f
private const val MIN_RELOCALIZATION_TURN_COUNT = 0
private const val MAX_RELOCALIZATION_TURN_COUNT = 1500
private const val RELOCALIZATION_TURN_COUNT_STEP = 10
