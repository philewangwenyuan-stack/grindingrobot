package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapEditStream
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import kotlinx.coroutines.Job
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sl_link.SlLink
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

data class WorkspaceItem(
    val id: Long,
    val regionId: String,
    val title: String,
    val draft: WorkspaceDraft,
    val obstacleRegionIds: List<String> = emptyList()
)

// 同时定义界面切换顺序和保存值；负号表示扫描轴的反方向，不是页面旋转角。
private val WorkspaceScanDirections = listOf("X", "-X", "Y", "-Y")

/**
 * 前后箭头沿同一列表循环：向前 X → -X → Y → -Y → X，向后为其逆序。
 * 未识别的旧值统一回到 X。这里只计算草稿值，真正下发在保存工作区时进行。
 */
internal fun adjacentWorkspaceScanDirection(current: String, forward: Boolean): String {
    val index = WorkspaceScanDirections.indexOf(current)
    if (index < 0) return "X"
    val offset = if (forward) 1 else -1
    // 先加列表长度，避免从第一个元素后退时 Kotlin 的负余数产生非法下标。
    return WorkspaceScanDirections[(index + offset + WorkspaceScanDirections.size) % WorkspaceScanDirections.size]
}

internal fun upsertWorkspaceItem(
    workspaces: List<WorkspaceItem>,
    savedItem: WorkspaceItem
): List<WorkspaceItem> {
    val existingIndex = workspaces.indexOfFirst { it.regionId == savedItem.regionId }
    if (existingIndex < 0) return workspaces + savedItem
    return workspaces.toMutableList().apply { this[existingIndex] = savedItem }
}

enum class WorkspacePanelMode {
    LIST,
    ADD,
    BOUNDARY_EDITOR,
    FORBIDDEN_EDITOR,
    ROBOT_SETTINGS_EDITOR,
    PROCESS_SETTINGS_EDITOR
}

enum class BoundaryDrawMode {
    RECTANGLE,
    POLYGON
}

enum class ForbiddenDrawMode {
    RECTANGLE,
    CIRCLE
}

enum class ProcessType {
    ROUGH,
    FINE,
    POLISH
}

enum class GrindingParameter(val label: String, val processType: ProcessType) {
    GRIT_30("#30", ProcessType.ROUGH),
    GRIT_50("#50", ProcessType.ROUGH),
    GRIT_100("#100", ProcessType.ROUGH),
    GRIT_200("#200", ProcessType.FINE),
    GRIT_400("#400", ProcessType.FINE),
    GRIT_800("#800", ProcessType.POLISH),
    GRIT_1500("#1500", ProcessType.POLISH),
    GRIT_2000("#2000", ProcessType.POLISH),
    GRIT_3000("#3000", ProcessType.POLISH);

    companion object {
        fun optionsFor(type: ProcessType): List<GrindingParameter> = entries.filter {
            it.processType == type
        }
    }
}

enum class ConcreteStrength {
    C25,
    C30,
    C35
}

internal data class ProcessPreset(
    val speedMetersPerSecond: Double,
    val pressureKg: Double,
    val discSpeedRpm: Int
)

internal fun processPreset(
    parameter: GrindingParameter,
    strength: ConcreteStrength
): ProcessPreset {
    val sharedParameter = when (parameter) {
        GrindingParameter.GRIT_50 -> GrindingParameter.GRIT_30
        GrindingParameter.GRIT_1500 -> GrindingParameter.GRIT_800
        GrindingParameter.GRIT_3000 -> GrindingParameter.GRIT_2000
        else -> parameter
    }
    return PROCESS_PRESETS.getValue(sharedParameter to strength)
}

private val PROCESS_PRESETS = mapOf(
    (GrindingParameter.GRIT_30 to ConcreteStrength.C25) to ProcessPreset(0.03, 345.0, 925),
    (GrindingParameter.GRIT_30 to ConcreteStrength.C30) to ProcessPreset(0.03, 345.0, 1000),
    (GrindingParameter.GRIT_30 to ConcreteStrength.C35) to ProcessPreset(0.02, 428.0, 1050),
    (GrindingParameter.GRIT_100 to ConcreteStrength.C25) to ProcessPreset(0.07, 345.0, 1250),
    (GrindingParameter.GRIT_100 to ConcreteStrength.C30) to ProcessPreset(0.05, 345.0, 1300),
    (GrindingParameter.GRIT_100 to ConcreteStrength.C35) to ProcessPreset(0.05, 345.0, 1350),
    (GrindingParameter.GRIT_200 to ConcreteStrength.C25) to ProcessPreset(0.07, 262.0, 1400),
    (GrindingParameter.GRIT_200 to ConcreteStrength.C30) to ProcessPreset(0.07, 345.0, 1450),
    (GrindingParameter.GRIT_200 to ConcreteStrength.C35) to ProcessPreset(0.05, 345.0, 1475),
    (GrindingParameter.GRIT_400 to ConcreteStrength.C25) to ProcessPreset(0.09, 262.0, 1500),
    (GrindingParameter.GRIT_400 to ConcreteStrength.C30) to ProcessPreset(0.07, 262.0, 1500),
    (GrindingParameter.GRIT_400 to ConcreteStrength.C35) to ProcessPreset(0.07, 345.0, 1500),
    (GrindingParameter.GRIT_800 to ConcreteStrength.C25) to ProcessPreset(0.09, 262.0, 1500),
    (GrindingParameter.GRIT_800 to ConcreteStrength.C30) to ProcessPreset(0.09, 262.0, 1500),
    (GrindingParameter.GRIT_800 to ConcreteStrength.C35) to ProcessPreset(0.07, 262.0, 1500),
    (GrindingParameter.GRIT_2000 to ConcreteStrength.C25) to ProcessPreset(0.12, 262.0, 1500),
    (GrindingParameter.GRIT_2000 to ConcreteStrength.C30) to ProcessPreset(0.12, 262.0, 1500),
    (GrindingParameter.GRIT_2000 to ConcreteStrength.C35) to ProcessPreset(0.10, 262.0, 1500)
)

internal fun WorkspaceDraft.withProcessSelection(
    type: ProcessType = processType,
    parameter: GrindingParameter = grindingParameter,
    strength: ConcreteStrength = concreteStrength
): WorkspaceDraft {
    val validParameter = parameter.takeIf { it.processType == type }
        ?: GrindingParameter.optionsFor(type).first()
    val preset = processPreset(validParameter, strength)
    return copy(
        processType = type,
        grindingParameter = validParameter,
        concreteStrength = strength,
        processSpeed = preset.speedMetersPerSecond,
        processPressure = preset.pressureKg,
        discSpeedRpm = preset.discSpeedRpm
    )
}

internal fun WorkspaceDraft.withProcessSpeed(value: Double): WorkspaceDraft = copy(
    processSpeed = round(value.coerceIn(0.0, 0.2) * 100.0) / 100.0
)

internal fun WorkspaceDraft.withProcessSpeedInput(input: String): WorkspaceDraft =
    input.toDoubleOrNull()?.let { withProcessSpeed(it) } ?: this

internal fun WorkspaceDraft.withProcessPressure(value: Double): WorkspaceDraft = copy(
    processPressure = value.coerceIn(1.0, 477.0)
)

internal fun WorkspaceDraft.withProcessPressureInput(input: String): WorkspaceDraft =
    input.toDoubleOrNull()?.let { withProcessPressure(it) } ?: this

internal fun WorkspaceDraft.withDiscSpeed(value: Int): WorkspaceDraft = copy(
    discSpeedRpm = value.coerceIn(0, 1500)
)

internal fun WorkspaceDraft.withDiscSpeedInput(input: String): WorkspaceDraft =
    input.toIntOrNull()?.let { withDiscSpeed(it) } ?: this

data class WorkspaceDraft(
    val areaCode: String = "001",
    val boundaryConfigured: Boolean = false,
    val boundaryDrawMode: BoundaryDrawMode = BoundaryDrawMode.RECTANGLE,
    val boundaryRectCornersM: List<Pair<Float, Float>> = emptyList(),
    val boundaryPolygonPointsM: List<Pair<Float, Float>> = emptyList(),
    val forbiddenConfigured: Boolean = false,
    val forbiddenDrawMode: ForbiddenDrawMode = ForbiddenDrawMode.RECTANGLE,
    val forbiddenRectanglesM: List<List<Pair<Float, Float>>> = emptyList(),
    val forbiddenCirclesM: List<Pair<Pair<Float, Float>, Float>> = emptyList(),
    val robotWidth: Double = 1.0,
    val robotLength: Double = 1.5,
    val robotPathSpacing: Double = 1.0,
    val robotCoverage: Int = 0,
    val processType: ProcessType = ProcessType.ROUGH,
    val grindingParameter: GrindingParameter = GrindingParameter.GRIT_30,
    val concreteStrength: ConcreteStrength = ConcreteStrength.C25,
    val processSpeed: Double = 0.03,
    val processPressure: Double = 345.0,
    val discSpeedRpm: Int = 925,
    val startConfigured: Boolean = false,
    val endConfigured: Boolean = false,
    val startPointM: Pair<Float, Float>? = null,
    val endPointM: Pair<Float, Float>? = null,
    val pathSpacing: Int = 1,
    val scanDirection: String = "X"
)

internal fun WorkspaceDraft.resetBoundaryForReconfiguration(): WorkspaceDraft = copy(
    boundaryConfigured = false,
    boundaryDrawMode = BoundaryDrawMode.RECTANGLE,
    boundaryRectCornersM = emptyList(),
    boundaryPolygonPointsM = emptyList()
)

internal fun WorkspaceDraft.resetForbiddenForReconfiguration(): WorkspaceDraft = copy(
    forbiddenConfigured = false,
    forbiddenDrawMode = ForbiddenDrawMode.RECTANGLE,
    forbiddenRectanglesM = emptyList(),
    forbiddenCirclesM = emptyList()
)

data class MapScreenStep3UiState(
    val mapImageBytes: ByteArray? = null,
    val isMapLoading: Boolean = false,
    val mapLoadError: String? = null,
    val mapImageSize: Pair<Int, Int>? = null,
    val bitmapDecodeSize: Pair<Int, Int>? = null,
    val mapGeo: MapGeo? = null,
    val robotPose: DevicePosePayload? = null,
    val workspaces: List<WorkspaceItem> = listOf(
    ),
    val panelMode: WorkspacePanelMode = WorkspacePanelMode.LIST,
    val workspaceDraft: WorkspaceDraft = WorkspaceDraft(),
    val editingWorkspaceRegionId: String? = null,
    val pendingWorkspaceRegionId: String? = null,
    val pendingObstacleRegionIds: List<String> = emptyList(),
    val isWorkspaceSaving: Boolean = false,
    val latestScanDirection: String = "X",
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val robotFootprint: List<AppState.FootprintPoint> = emptyList(),
    val previewWorkRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewObstacleRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewEraseRegions: List<MapPreviewRegionItem> = emptyList()
)

@HiltViewModel
class MapScreenStep3ViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapScreenStep3UiState())
    val uiState: StateFlow<MapScreenStep3UiState> = _uiState.asStateFlow()

    private var nextWorkspaceId = 1L
    private var nextProtocolRegionSequence = System.currentTimeMillis()
    private var currentMapId: String? = null
    private var loadedSessionId: Long? = null
    private var loadJob: Job? = null

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
            appState.mapSettings
                .filterNotNull()
                .collect { settings ->
                    if (
                        _uiState.value.panelMode == WorkspacePanelMode.ADD &&
                        _uiState.value.editingWorkspaceRegionId == null
                    ) {
                        applyDefaultPathSpacing(settings.defaultPathSpacing)
                    }
                    if (
                        _uiState.value.panelMode == WorkspacePanelMode.ROBOT_SETTINGS_EDITOR &&
                        _uiState.value.editingWorkspaceRegionId == null
                    ) {
                        applyRobotSettingsFromMap(settings)
                    }
                }
        }
        viewModelScope.launch {
            DeviceStatusStream.pose.collect { pose ->
                _uiState.update { it.copy(robotPose = pose) }
            }
        }
    }

    fun onAddWorkspaceScreenEnter() {
        if (_uiState.value.editingWorkspaceRegionId == null) {
            appState.mapSettings.value?.let { settings ->
                applyDefaultPathSpacing(settings.defaultPathSpacing)
            }
        }
        tcpManager.requestSettingRead(readChassis = true, readMap = true)
    }

    /** 从冻结帧恢复底图，仅查询最新区域；同会话保留编辑面板，跨 Step1 会话重置草稿状态。 */
    fun loadMapData(mapId: String?) {
        currentMapId = mapId
        loadJob?.cancel()
        val snapshot = MapBuildSessionStream.snapshotFor(mapId)
        if (snapshot == null) {
            _uiState.update { it.copy(isMapLoading = false, mapImageBytes = null, mapGeo = null,
                mapLoadError = "地图缓存不存在，请返回第一步重新获取") }
            return
        }
        val frame = snapshot.frame
        val newSession = loadedSessionId != snapshot.sessionId
        loadedSessionId = snapshot.sessionId
        _uiState.update {
            val state = if (newSession) MapScreenStep3UiState(
                robotPose = it.robotPose, robotWidth = it.robotWidth, robotLength = it.robotLength,
                robotFootprint = it.robotFootprint
            ) else it
            state.copy(mapImageBytes = frame.imageBytes, mapImageSize = frame.mapWidth to frame.mapHeight,
                bitmapDecodeSize = decodeImageSize(frame.imageBytes), mapGeo = frame.toMapGeo(),
                isMapLoading = true, mapLoadError = null)
        }
        loadJob = viewModelScope.launch {
            val payload = tcpManager.loadMapRegions(mapId)
            if (payload == null || !payload.isSuccess) {
                val error = payload?.message?.ifBlank { null } ?: "区域信息获取失败，请重试"
                _uiState.update { it.copy(isMapLoading = false, mapLoadError = error) }
                ToastUtils.showError(error)
                return@launch
            }
            // 列表中的服务端几何更新，不覆盖单独维护的 workspaceDraft；起终点坐标按响应原样保存。
            val existing = _uiState.value.workspaces.associateBy { it.regionId }
            val workspaces = payload.workRegions.filter { it.region.enabled }.mapIndexed { index, info ->
                val region = info.region
                val previous = existing[region.regionId]
                val draft = (previous?.draft ?: createDefaultWorkspaceDraft()).copy(
                    areaCode = region.regionName.removePrefix("wr_").ifBlank { (index + 1).toString().padStart(3, '0') },
                    boundaryConfigured = region.points.size >= 3, boundaryDrawMode = BoundaryDrawMode.POLYGON,
                    boundaryRectCornersM = emptyList(),
                    boundaryPolygonPointsM = region.points.map { it.x to it.y },
                    startConfigured = info.startPose != null, endConfigured = info.endPose != null,
                    startPointM = info.startPose?.let { it.x to it.y }, endPointM = info.endPose?.let { it.x to it.y }
                )
                previous?.copy(draft = draft) ?: WorkspaceItem(
                    id = nextWorkspaceId++, regionId = region.regionId,
                    title = region.regionName.ifBlank { "区域${region.regionId}" }, draft = draft)
            }
            _uiState.update { it.copy(isMapLoading = false, workspaces = workspaces,
                previewWorkRegions = payload.workRegions.map { info -> info.region.toUiItem() },
                previewObstacleRegions = payload.obstacleRegions.map { region -> region.toUiItem() },
                previewEraseRegions = payload.eraseRegions.map { region -> region.toUiItem() }) }
        }
    }

    fun cancelMapLoading() { loadJob?.cancel() }

    /** 取消建图时同时通知板端停止 Super-LIO 建图节点。 */
    fun onCancelBuild() {
        cancelMapLoading()
        val sent = tcpManager.stopMappingMode()
        if (!sent) {
            LogUtils.w("MapScreenStep3ViewModel", "stopMappingMode 发送失败")
        }
    }

    fun addWorkspace() {
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.ADD,
                workspaceDraft = createDefaultWorkspaceDraft(state.latestScanDirection),
                editingWorkspaceRegionId = null,
                pendingWorkspaceRegionId = null,
                pendingObstacleRegionIds = emptyList()
            )
        }
    }

    fun editWorkspace(regionId: String): WorkspaceDraft? {
        val item = _uiState.value.workspaces.firstOrNull { it.regionId == regionId } ?: return null
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.ADD,
                workspaceDraft = item.draft,
                editingWorkspaceRegionId = item.regionId,
                pendingWorkspaceRegionId = null,
                pendingObstacleRegionIds = emptyList()
            )
        }
        return item.draft
    }

    fun clearWorkspaces() {
        _uiState.update { it.copy(workspaces = emptyList()) }
    }

    suspend fun deleteAllWorkspaces(): Set<String> {
        val regionIds = _uiState.value.workspaces.map { it.regionId }
        val deletedRegionIds = mutableSetOf<String>()
        regionIds.forEach { regionId ->
            if (deleteWorkspaceAwaitAck(regionId)) {
                deletedRegionIds += regionId
            }
        }
        loadMapData(currentMapId)
        return deletedRegionIds
    }

    suspend fun deleteWorkspace(regionId: String): Boolean {
        val deleted = deleteWorkspaceAwaitAck(regionId)
        loadMapData(currentMapId)
        return deleted
    }

    private suspend fun deleteWorkspaceAwaitAck(regionId: String): Boolean {
        val item = _uiState.value.workspaces.firstOrNull { it.regionId == regionId }
        item?.obstacleRegionIds.orEmpty().forEach { obstacleRegionId ->
            val deleted = sendMapEditAwaitAck {
                tcpManager.sendMapEditDeleteObstacle(
                    editId = newRegionId("edit"),
                    regionId = obstacleRegionId,
                    mapId = currentMapId
                )
            }
            if (!deleted) return false
        }
        val deleted = sendMapEditAwaitAck {
            tcpManager.sendMapEditDeleteWorkspace(currentMapId, regionId)
        }
        if (!deleted) {
            LogUtils.w("sendMapEditDeleteWorkspace 响应超时: mapId=$currentMapId, regionId=$regionId")
            return false
        }
        _uiState.update { state ->
            state.copy(workspaces = state.workspaces.filterNot { it.regionId == regionId })
        }
        return true
    }

    fun cancelAddWorkspace() {
        _uiState.update {
            it.copy(
                panelMode = WorkspacePanelMode.LIST,
                editingWorkspaceRegionId = null,
                pendingWorkspaceRegionId = null,
                pendingObstacleRegionIds = emptyList(),
                workspaceDraft = createDefaultWorkspaceDraft(it.latestScanDirection)
            )
        }
    }

    suspend fun saveWorkspace(): String? {
        val currentState = _uiState.value
        if (MapBuildSessionStream.snapshotFor(currentMapId) == null) {
            ToastUtils.showError("地图缓存不存在，请返回第一步重新获取")
            return null
        }
        if (currentState.isMapLoading || currentState.mapLoadError != null) {
            ToastUtils.showError(currentState.mapLoadError ?: "请等待地图区域加载完成")
            return null
        }
        val draft = currentState.workspaceDraft
        val missingSettings = getMissingRequiredWorkspaceSettings(draft)
        if (missingSettings.isNotEmpty()) {
            ToastUtils.showWarningDark("请先设置：${missingSettings.joinToString("、")}")
            return null
        }
        LogUtils.d(
            "MapScreenStep3ViewModel",
            buildString {
                append("Save workspace params: ")
                append("areaCode=${draft.areaCode}; ")
                append("boundaryRectCornersM=${draft.boundaryRectCornersM}; ")
                append("boundaryPolygonPointsM=${draft.boundaryPolygonPointsM}; ")
                append("forbiddenRectanglesM=${draft.forbiddenRectanglesM}; ")
                append("forbiddenCirclesM=${draft.forbiddenCirclesM}; ")
                append("startPointM=${draft.startPointM}; ")
                append("endPointM=${draft.endPointM}; ")
                append("pathSpacing=${draft.pathSpacing}; ")
                append("scanDirection=${draft.scanDirection}; ")
                append(
                    "processType=${draft.processType}, grindingParameter=${draft.grindingParameter.label}, " +
                        "concreteStrength=${draft.concreteStrength}, processSpeed=${draft.processSpeed}, " +
                        "processPressure=${draft.processPressure}, discSpeedRpm=${draft.discSpeedRpm}; "
                )
                append("robotWidth=${draft.robotWidth}, robotLength=${draft.robotLength}, robotPathSpacing=${draft.robotPathSpacing}, robotCoverage=${draft.robotCoverage}")
            }
        )
        val editingItem = currentState.editingWorkspaceRegionId?.let { regionId ->
            currentState.workspaces.firstOrNull { it.regionId == regionId }
        }
        val regionId = editingItem?.regionId
            ?: currentState.pendingWorkspaceRegionId
            ?: newRegionId("work_region")
        val existingObstacleRegionIds = editingItem?.obstacleRegionIds.orEmpty()
        val obstacleRegionIds = draft.forbiddenRectanglesM.indices.map { index ->
            existingObstacleRegionIds.getOrNull(index)
                ?: currentState.pendingObstacleRegionIds.getOrNull(index)
                ?: newRegionId("obstacle_region")
        }
        _uiState.update {
            it.copy(
                isWorkspaceSaving = true,
                pendingWorkspaceRegionId = regionId,
                pendingObstacleRegionIds = obstacleRegionIds
            )
        }
        val savedObstacleIds = sendWorkspaceConfigAwaitAck(
            draft = draft,
            regionId = regionId,
            existingObstacleRegionIds = existingObstacleRegionIds,
            nextObstacleRegionIds = obstacleRegionIds
        )
        if (savedObstacleIds == null) {
            _uiState.update { it.copy(isWorkspaceSaving = false) }
            ToastUtils.showError("工作区保存失败，请重试")
            return null
        }
        val savedItem = WorkspaceItem(
            id = editingItem?.id ?: nextWorkspaceId,
            regionId = regionId,
            title = "区域编号 ${draft.areaCode}",
            draft = draft,
            obstacleRegionIds = savedObstacleIds
        )
        if (editingItem == null) nextWorkspaceId += 1
        _uiState.update { state ->
            val nextWorkspaces = upsertWorkspaceItem(state.workspaces, savedItem)
            state.copy(
                workspaces = nextWorkspaces,
                panelMode = WorkspacePanelMode.LIST,
                latestScanDirection = draft.scanDirection,
                workspaceDraft = createDefaultWorkspaceDraft(draft.scanDirection),
                editingWorkspaceRegionId = null,
                pendingWorkspaceRegionId = null,
                pendingObstacleRegionIds = emptyList(),
                isWorkspaceSaving = false
            )
        }
        // 编辑确认完成后重新查询，后续显示以服务端最终区域为准，底图快照保持不变。
        loadMapData(currentMapId)
        return regionId
    }

    fun replaceForbiddenGeometry(
        rectangles: List<List<Pair<Float, Float>>>,
        circles: List<Pair<Pair<Float, Float>, Float>>
    ) {
        val worldRectangles = rectangles.mapNotNull { rectangle ->
            rectangle.mapNotNull(::bitmapToWorld).takeIf { it.size == 4 }
        }
        val worldCircles = circles.mapNotNull { (center, radius) ->
            val worldCenter = bitmapToWorld(center) ?: return@mapNotNull null
            val worldRadius = bitmapRadiusToWorldMeters(radius) ?: return@mapNotNull null
            worldCenter to worldRadius
        }
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    forbiddenConfigured = worldRectangles.isNotEmpty() || worldCircles.isNotEmpty(),
                    forbiddenRectanglesM = worldRectangles,
                    forbiddenCirclesM = worldCircles
                )
            )
        }
    }

    private fun getMissingRequiredWorkspaceSettings(draft: WorkspaceDraft): List<String> {
        val boundaryReady = draft.boundaryConfigured && when (draft.boundaryDrawMode) {
            BoundaryDrawMode.RECTANGLE -> draft.boundaryRectCornersM.size == 4
            BoundaryDrawMode.POLYGON -> draft.boundaryPolygonPointsM.size >= 3
        }
        return buildList {
            if (!boundaryReady) add("区域边界点")
        }
    }

    fun openBoundaryEditor() {
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.BOUNDARY_EDITOR,
                workspaceDraft = state.workspaceDraft.resetBoundaryForReconfiguration()
            )
        }
    }

    fun closeBoundaryEditor() {
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.ADD) }
    }

    fun setBoundaryDrawMode(mode: BoundaryDrawMode) {
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.BOUNDARY_EDITOR,
                workspaceDraft = state.workspaceDraft.copy(
                    boundaryDrawMode = mode
                )
            )
        }
    }

    fun confirmBoundaryCorners(
        topLeft: Pair<Float, Float>,
        topRight: Pair<Float, Float>,
        bottomRight: Pair<Float, Float>,
        bottomLeft: Pair<Float, Float>
    ) {
        val worldLt = bitmapToWorld(topLeft)
        val worldRt = bitmapToWorld(topRight)
        val worldRb = bitmapToWorld(bottomRight)
        val worldLb = bitmapToWorld(bottomLeft)
        fun formatPoint(point: Pair<Float, Float>?): String {
            if (point == null) return "(N/A)"
            val x = String.format("%.2f", point.first)
            val y = String.format("%.2f", point.second)
            return "($x, $y)"
        }
        val msg = buildString {
            append("Boundary corners confirmed (world meters): ")
            append("LT=${formatPoint(worldLt)}, ")
            append("RT=${formatPoint(worldRt)}, ")
            append("RB=${formatPoint(worldRb)}, ")
            append("LB=${formatPoint(worldLb)}")
        }
        LogUtils.d("MapScreenStep3ViewModel", msg)
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    boundaryConfigured = true,
                    boundaryRectCornersM = listOfNotNull(worldLt, worldRt, worldRb, worldLb),
                    boundaryPolygonPointsM = emptyList()
                )
            )
        }
    }

    fun confirmBoundaryPolygonPoints(points: List<Pair<Float, Float>>) {
        fun formatPoint(point: Pair<Float, Float>?): String {
            if (point == null) return "(N/A)"
            val x = String.format("%.2f", point.first)
            val y = String.format("%.2f", point.second)
            return "($x, $y)"
        }
        val worldPoints = points.map { bitmapToWorld(it) }
        val formatted = worldPoints.joinToString(separator = ", ") { formatPoint(it) }
        LogUtils.d("MapScreenStep3ViewModel", "Boundary polygon points confirmed (world meters): $formatted")
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    boundaryConfigured = true,
                    boundaryRectCornersM = emptyList(),
                    boundaryPolygonPointsM = worldPoints.filterNotNull()
                )
            )
        }
    }

    fun confirmForbiddenRectangleCorners(
        topLeft: Pair<Float, Float>,
        topRight: Pair<Float, Float>,
        bottomRight: Pair<Float, Float>,
        bottomLeft: Pair<Float, Float>
    ) {
        val worldLt = bitmapToWorld(topLeft)
        val worldRt = bitmapToWorld(topRight)
        val worldRb = bitmapToWorld(bottomRight)
        val worldLb = bitmapToWorld(bottomLeft)
        fun formatPoint(point: Pair<Float, Float>?): String {
            if (point == null) return "(N/A)"
            val x = String.format("%.2f", point.first)
            val y = String.format("%.2f", point.second)
            return "($x, $y)"
        }
        val msg = buildString {
            append("Forbidden rectangle confirmed (world meters): ")
            append("LT=${formatPoint(worldLt)}, ")
            append("RT=${formatPoint(worldRt)}, ")
            append("RB=${formatPoint(worldRb)}, ")
            append("LB=${formatPoint(worldLb)}")
        }
        LogUtils.d("MapScreenStep3ViewModel", msg)
        _uiState.update { state ->
            val worldRect = listOfNotNull(worldLt, worldRt, worldRb, worldLb)
            val updatedRectangles: List<List<Pair<Float, Float>>> = if (worldRect.size == 4) {
                state.workspaceDraft.forbiddenRectanglesM + listOf(worldRect)
            } else {
                state.workspaceDraft.forbiddenRectanglesM
            }
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    forbiddenConfigured = true,
                    forbiddenRectanglesM = updatedRectangles
                )
            )
        }
        todoIntegrateForbiddenRectangle(topLeft, topRight, bottomRight, bottomLeft)
    }

    fun confirmForbiddenCircle(center: Pair<Float, Float>, radius: Float) {
        val worldCenter = bitmapToWorld(center)
        val radiusM = bitmapRadiusToWorldMeters(radius)
        val centerX = String.format("%.2f", worldCenter?.first ?: Float.NaN)
        val centerY = String.format("%.2f", worldCenter?.second ?: Float.NaN)
        val radiusText = String.format("%.2f", radiusM ?: Float.NaN)
        LogUtils.d(
            "MapScreenStep3ViewModel",
            "Forbidden circle confirmed (world meters): center=($centerX, $centerY), radius=$radiusText"
        )
        _uiState.update { state ->
            val nextCircles = if (worldCenter != null && radiusM != null) {
                state.workspaceDraft.forbiddenCirclesM + (worldCenter to radiusM)
            } else {
                state.workspaceDraft.forbiddenCirclesM
            }
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    forbiddenConfigured = true,
                    forbiddenCirclesM = nextCircles
                )
            )
        }
        todoIntegrateForbiddenCircle(center, radius)
    }

    fun confirmStartPoint(point: Pair<Float, Float>) {
        val x = String.format("%.2f", point.first)
        val y = String.format("%.2f", point.second)
        LogUtils.d("MapScreenStep3ViewModel", "Start point confirmed (origin at top-left): ($x, $y)")
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(startPointM = bitmapToWorld(point)))
        }
        todoIntegrateStartPoint(point)
    }

    fun confirmEndPoint(point: Pair<Float, Float>) {
        val x = String.format("%.2f", point.first)
        val y = String.format("%.2f", point.second)
        LogUtils.d("MapScreenStep3ViewModel", "End point confirmed (origin at top-left): ($x, $y)")
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(endPointM = bitmapToWorld(point)))
        }
        todoIntegrateEndPoint(point)
    }

    fun bitmapPointToWorld(point: Pair<Float, Float>): Pair<Float, Float>? {
        return bitmapToWorld(point)
    }

    /** 世界坐标转未旋转的位图坐标；页面再统一施加显示旋转、适配缩放及手势变换。 */
    fun worldPointToBitmap(point: Pair<Float, Float>): Pair<Float, Float>? {
        val state = _uiState.value
        val geo = state.mapGeo ?: return null
        val mapSize = state.mapImageSize ?: return null
        val bmp = state.bitmapDecodeSize ?: return null
        val dx = point.first - geo.originX.toFloat()
        val dy = point.second - geo.originY.toFloat()
        val rad = Math.toRadians(geo.headingDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val xm = c * dx + s * dy
        val ym = -s * dx + c * dy
        val tcpX = xm / geo.resolution
        val tcpY = geo.mapHeight - ym / geo.resolution
        return (tcpX * bmp.first / mapSize.first.toFloat()) to
            (tcpY * bmp.second / mapSize.second.toFloat())
    }

    fun worldRadiusToBitmap(radiusMeters: Float): Float? {
        val state = _uiState.value
        val geo = state.mapGeo ?: return null
        val mapSize = state.mapImageSize ?: return null
        val bmp = state.bitmapDecodeSize ?: return null
        if (geo.resolution <= 0f || mapSize.first <= 0) return null
        val tcpRadius = radiusMeters / geo.resolution
        return tcpRadius * bmp.first / mapSize.first.toFloat()
    }

    private fun todoIntegrateForbiddenRectangle(
        topLeft: Pair<Float, Float>,
        topRight: Pair<Float, Float>,
        bottomRight: Pair<Float, Float>,
        bottomLeft: Pair<Float, Float>
    ) {
        // TODO: 接入后端/协议层禁区矩形参数上报
        LogUtils.d(
            "MapScreenStep3ViewModel",
            "TODO integrate forbidden rectangle payload: LT=$topLeft RT=$topRight RB=$bottomRight LB=$bottomLeft"
        )
    }

    private fun todoIntegrateForbiddenCircle(center: Pair<Float, Float>, radius: Float) {
        // TODO：: 接入后端/协议层禁区圆形参数上报
        LogUtils.d(
            "MapScreenStep3ViewModel",
            "TODO integrate forbidden circle payload: center=$center radius=$radius"
        )
    }

    private fun todoIntegrateStartPoint(point: Pair<Float, Float>) {
        // TODO: 接入后端/协议层起点坐标上报
        LogUtils.d("MapScreenStep3ViewModel", "TODO integrate start point payload: point=$point")
    }

    private fun todoIntegrateEndPoint(point: Pair<Float, Float>) {
        // TODO: 接入后端/协议层终点坐标上报
        LogUtils.d("MapScreenStep3ViewModel", "TODO integrate end point payload: point=$point")
    }

    fun openForbiddenEditor() {
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.FORBIDDEN_EDITOR,
                workspaceDraft = state.workspaceDraft.resetForbiddenForReconfiguration()
            )
        }
    }

    fun closeForbiddenEditor() {
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.ADD) }
    }

    fun setForbiddenDrawMode(mode: ForbiddenDrawMode) {
        _uiState.update { state ->
            state.copy(
                panelMode = WorkspacePanelMode.FORBIDDEN_EDITOR,
                workspaceDraft = state.workspaceDraft.copy(
                    forbiddenDrawMode = mode
                )
            )
        }
    }

    fun openRobotSettingsEditor() {
        if (_uiState.value.editingWorkspaceRegionId == null) {
            appState.mapSettings.value?.let { settings ->
                applyRobotSettingsFromMap(settings)
            }
        }
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.ROBOT_SETTINGS_EDITOR) }
    }

    fun closeRobotSettingsEditor() {
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.ADD) }
    }

    fun decreaseRobotWidth() {
        _uiState.update { state ->
            val next = (state.workspaceDraft.robotWidth - 0.1).coerceAtLeast(0.0)
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotWidth = formatOneDecimal(next)))
        }
    }

    fun increaseRobotWidth() {
        _uiState.update { state ->
            val next = state.workspaceDraft.robotWidth + 0.1
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotWidth = formatOneDecimal(next)))
        }
    }

    fun updateRobotWidth(input: String) {
        val parsed = input.toDoubleOrNull() ?: return
        val normalized = formatOneDecimal(parsed.coerceAtLeast(0.0))
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotWidth = normalized))
        }
    }

    fun decreaseRobotLength() {
        _uiState.update { state ->
            val next = (state.workspaceDraft.robotLength - 0.1).coerceAtLeast(0.0)
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotLength = formatOneDecimal(next)))
        }
    }

    fun increaseRobotLength() {
        _uiState.update { state ->
            val next = state.workspaceDraft.robotLength + 0.1
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotLength = formatOneDecimal(next)))
        }
    }

    fun updateRobotLength(input: String) {
        val parsed = input.toDoubleOrNull() ?: return
        val normalized = formatOneDecimal(parsed.coerceAtLeast(0.0))
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotLength = normalized))
        }
    }

    fun decreaseRobotPathSpacing() {
        _uiState.update { state ->
            val next = (state.workspaceDraft.robotPathSpacing - 0.1).coerceAtLeast(0.0)
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotPathSpacing = formatOneDecimal(next)))
        }
    }

    fun increaseRobotPathSpacing() {
        _uiState.update { state ->
            val next = state.workspaceDraft.robotPathSpacing + 0.1
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotPathSpacing = formatOneDecimal(next)))
        }
    }

    fun updateRobotPathSpacing(input: String) {
        val parsed = input.toDoubleOrNull() ?: return
        val normalized = formatOneDecimal(parsed.coerceAtLeast(0.0))
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotPathSpacing = normalized))
        }
    }

    fun decreaseRobotCoverage() {
        _uiState.update { state ->
            val next = (state.workspaceDraft.robotCoverage - 1).coerceAtLeast(0)
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotCoverage = next))
        }
    }

    fun increaseRobotCoverage() {
        _uiState.update { state ->
            val next = (state.workspaceDraft.robotCoverage + 1).coerceAtMost(100)
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotCoverage = next))
        }
    }

    fun updateRobotCoverage(input: String) {
        val parsed = input.toIntOrNull() ?: return
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(robotCoverage = parsed.coerceIn(0, 100)))
        }
    }

    fun saveRobotSettings() {
        val draft = _uiState.value.workspaceDraft
        val overlapRatio = draft.robotCoverage.coerceIn(0, 100) / 100f
        LogUtils.d(
            "MapScreenStep3ViewModel",
            "Robot settings saved: width=${String.format("%.1f", draft.robotWidth)}, " +
                "length=${String.format("%.1f", draft.robotLength)}, " +
                "pathSpacing=${String.format("%.1f", draft.robotPathSpacing)}, " +
                "coverage=${draft.robotCoverage}, overlapRatio=$overlapRatio"
        )
        val settings = SlLink.MapSettings.newBuilder()
            .setVehicleLength(draft.robotLength.toFloat())
            .setVehicleWidth(draft.robotWidth.toFloat())
            .setDefaultPathSpacing(draft.robotPathSpacing.toFloat())
            .setOverlapRatio(overlapRatio)
            .build()
        val sent = tcpManager.requestSettingWrite(mapSettings = settings)
        if (!sent) {
            LogUtils.w("requestSettingWrite mapSettings 发送失败")
        }
    }

    fun openProcessSettingsEditor() {
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.PROCESS_SETTINGS_EDITOR) }
    }

    fun closeProcessSettingsEditor() {
        _uiState.update { it.copy(panelMode = WorkspacePanelMode.ADD) }
    }

    fun setProcessType(type: ProcessType) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.withProcessSelection(type = type))
        }
    }

    fun setGrindingParameter(parameter: GrindingParameter) {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessSelection(parameter = parameter)
            )
        }
    }

    fun setConcreteStrength(strength: ConcreteStrength) {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessSelection(strength = strength)
            )
        }
    }

    fun decreaseProcessSpeed() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessSpeed(
                    state.workspaceDraft.processSpeed - 0.01
                )
            )
        }
    }

    fun increaseProcessSpeed() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessSpeed(
                    state.workspaceDraft.processSpeed + 0.01
                )
            )
        }
    }

    fun updateProcessSpeed(input: String) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.withProcessSpeedInput(input))
        }
    }

    fun decreaseProcessPressure() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessPressure(
                    state.workspaceDraft.processPressure - 1.0
                )
            )
        }
    }

    fun increaseProcessPressure() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withProcessPressure(
                    state.workspaceDraft.processPressure + 1.0
                )
            )
        }
    }

    fun updateProcessPressure(input: String) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.withProcessPressureInput(input))
        }
    }

    fun decreaseDiscSpeed() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withDiscSpeed(
                    state.workspaceDraft.discSpeedRpm - 100
                )
            )
        }
    }

    fun increaseDiscSpeed() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.withDiscSpeed(
                    state.workspaceDraft.discSpeedRpm + 100
                )
            )
        }
    }

    fun updateDiscSpeed(input: String) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.withDiscSpeedInput(input))
        }
    }

    fun saveProcessSettings() {
        val draft = _uiState.value.workspaceDraft
        val speedText = String.format("%.2f", draft.processSpeed)
        val pressureText = String.format("%.0f", draft.processPressure)
        LogUtils.d(
            "MapScreenStep3ViewModel",
            "Process settings saved: type=${draft.processType}, " +
                "parameter=${draft.grindingParameter.label}, strength=${draft.concreteStrength}, " +
                "speed=${speedText}m/s, pressure=${pressureText}kg, " +
                "discSpeed=${draft.discSpeedRpm}rpm"
        )
        val settings = buildProcessChassisSettings(draft)
        val sent = tcpManager.requestSettingWrite(chassisSettings = settings)
        if (!sent) {
            LogUtils.w("requestSettingWrite chassisSettings 发送失败")
        }
    }

    fun updateAreaCode(areaCode: String) {
        val normalized = areaCode.filter(Char::isDigit).take(3)
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    areaCode = normalized.ifEmpty { "001" }
                )
            )
        }
    }

    fun setBoundaryConfigured(configured: Boolean) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(boundaryConfigured = configured))
        }
    }

    fun setForbiddenConfigured(configured: Boolean) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(forbiddenConfigured = configured))
        }
    }

    fun setStartConfigured(configured: Boolean) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(startConfigured = configured))
        }
    }

    fun setEndConfigured(configured: Boolean) {
        _uiState.update { state ->
            state.copy(workspaceDraft = state.workspaceDraft.copy(endConfigured = configured))
        }
    }

    fun decreasePathSpacing() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    pathSpacing = (state.workspaceDraft.pathSpacing - 1).coerceAtLeast(1)
                )
            )
        }
    }

    fun increasePathSpacing() {
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    pathSpacing = (state.workspaceDraft.pathSpacing + 1).coerceAtMost(99)
                )
            )
        }
    }

    fun previousScanDirection() {
        changeScanDirection(forward = false)
    }

    fun nextScanDirection() {
        changeScanDirection(forward = true)
    }

    /** 只更新本地编辑态；同步最近选择与当前草稿，避免新建草稿时丢失刚选择的扫描方向。 */
    private fun changeScanDirection(forward: Boolean) {
        _uiState.update { state ->
            val nextDirection = adjacentWorkspaceScanDirection(state.workspaceDraft.scanDirection, forward)
            state.copy(
                latestScanDirection = nextDirection,
                workspaceDraft = state.workspaceDraft.copy(
                    scanDirection = nextDirection
                )
            )
        }
    }

    private suspend fun sendWorkspaceConfigAwaitAck(
        draft: WorkspaceDraft,
        regionId: String,
        existingObstacleRegionIds: List<String>,
        nextObstacleRegionIds: List<String>
    ): List<String>? {
        val boundaryPoints = when (draft.boundaryDrawMode) {
            BoundaryDrawMode.RECTANGLE -> draft.boundaryRectCornersM
            BoundaryDrawMode.POLYGON -> draft.boundaryPolygonPointsM
        }
        val workspaceSaved = sendMapEditAwaitAck {
            tcpManager.sendMapEditAddWorkspace(
                editId = newRegionId("edit"),
                areaCode = draft.areaCode,
                regionId = regionId,
                mapId = currentMapId,
                points = boundaryPoints,
                startPose = draft.startPointM,
                endPose = draft.endPointM,
                // 通过 0x0509 的 region.global_direction 保存，包含负号，不另行换算。
                globalDirection = draft.scanDirection
            )
        }
        if (!workspaceSaved) return null

        draft.forbiddenRectanglesM.forEachIndexed { index, rectangle ->
            val obstacleRegionId = nextObstacleRegionIds[index]
            val saved = sendMapEditAwaitAck {
                tcpManager.sendMapEditAddObstacle(
                    editId = newRegionId("edit"),
                    regionId = obstacleRegionId,
                    mapId = currentMapId,
                    points = rectangle
                )
            }
            if (!saved) return null
        }
        existingObstacleRegionIds.drop(nextObstacleRegionIds.size).forEach { obstacleRegionId ->
            val deleted = sendMapEditAwaitAck {
                tcpManager.sendMapEditDeleteObstacle(
                    editId = newRegionId("edit"),
                    regionId = obstacleRegionId,
                    mapId = currentMapId
                )
            }
            if (!deleted) return null
        }
        return nextObstacleRegionIds
    }

    private suspend fun sendMapEditAwaitAck(send: () -> Boolean): Boolean {
        MapEditStream.reset()
        if (!send()) return false
        val payload = withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
            MapEditStream.payload.filterNotNull().first()
        }
        return payload?.isSuccess == true
    }

    private fun newRegionId(prefix: String): String {
        nextProtocolRegionSequence = maxOf(nextProtocolRegionSequence + 1, System.currentTimeMillis())
        return "${prefix}_$nextProtocolRegionSequence"
    }

    private fun formatOneDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
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

    /** 输入已由页面撤销手势和显示旋转；这里仅反算图片尺寸比例、分辨率、地图朝向和原点。 */
    private fun bitmapToWorld(point: Pair<Float, Float>): Pair<Float, Float>? {
        val state = _uiState.value
        val geo = state.mapGeo ?: return null
        val mapSize = state.mapImageSize ?: return null
        val bmp = state.bitmapDecodeSize ?: return null
        val tcp = bitmapPxToTcpPx(point.first, point.second, bmp.first, bmp.second, mapSize.first, mapSize.second)
        return tcpPixelToWorld(tcp.first, tcp.second, geo)
    }

    private fun bitmapRadiusToWorldMeters(radiusBitmapPx: Float): Float? {
        val state = _uiState.value
        val geo = state.mapGeo ?: return null
        val mapSize = state.mapImageSize ?: return null
        val bmp = state.bitmapDecodeSize ?: return null
        val radiusTcpPx = radiusBitmapPx * mapSize.first / bmp.first.toFloat()
        return radiusTcpPx * geo.resolution
    }

    private fun bitmapPxToTcpPx(
        x: Float,
        y: Float,
        bmpW: Int,
        bmpH: Int,
        mapW: Int,
        mapH: Int
    ): Pair<Float, Float> {
        return (x * mapW / bmpW.toFloat()) to (y * mapH / bmpH.toFloat())
    }

    private fun tcpPixelToWorld(px: Float, py: Float, geo: MapGeo): Pair<Float, Float> {
        val xm = px * geo.resolution
        val ym = (geo.mapHeight - py) * geo.resolution
        val rad = Math.toRadians(geo.headingDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val wx = (geo.originX + c * xm - s * ym).toFloat()
        val wy = (geo.originY + s * xm + c * ym).toFloat()
        return wx to wy
    }

    private fun createDefaultWorkspaceDraft(scanDirection: String = "X"): WorkspaceDraft {
        return WorkspaceDraft(
            areaCode = nextWorkspaceId.toString().padStart(3, '0'),
            scanDirection = scanDirection
        )
    }

    private fun applyDefaultPathSpacing(defaultPathSpacing: Float) {
        val spacing = defaultPathSpacing.roundToInt().coerceIn(1, 99)
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(pathSpacing = spacing)
            )
        }
    }

    private fun applyRobotSettingsFromMap(settings: SlLink.MapSettings) {
        val coverage = if (settings.overlapRatio <= 1f) {
            (settings.overlapRatio * 100f).roundToInt()
        } else {
            settings.overlapRatio.roundToInt()
        }.coerceIn(0, 100)
        _uiState.update { state ->
            state.copy(
                workspaceDraft = state.workspaceDraft.copy(
                    robotWidth = formatOneDecimal(settings.vehicleWidth.toDouble()),
                    robotLength = formatOneDecimal(settings.vehicleLength.toDouble()),
                    robotPathSpacing = formatOneDecimal(settings.defaultPathSpacing.toDouble()),
                    robotCoverage = coverage
                )
            )
        }
    }

}

internal fun buildProcessChassisSettings(draft: WorkspaceDraft): SlLink.ChassisSettings =
    SlLink.ChassisSettings.newBuilder()
        .setRunSpeed(draft.processSpeed.toFloat())
        .setDiscSpeedRpm(draft.discSpeedRpm)
        .build()


