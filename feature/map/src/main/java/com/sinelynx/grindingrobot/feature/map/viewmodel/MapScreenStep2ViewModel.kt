package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.core.model.state.MapEditStream
import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointPayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

enum class EditToolMode {
    Gesture,
    Erase
}

data class EraseRectArea(
    val id: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confirmed: Boolean,
    /** 地图旋转后的像素坐标，仅用于保持 UI 选区与本地擦除显示一致。 */
    val alignedCorners: List<Offset> = emptyList()
)

/** 擦除矩形四角在世界坐标系中的位置（米），y 轴向上。 */
data class EraseAreaWorldOutput(
    val id: Long,
    val leftBottomM: Offset,
    val rightBottomM: Offset,
    val rightTopM: Offset,
    val leftTopM: Offset
)

data class EditOutput(
    val confirmedAreas: List<EraseAreaWorldOutput> = emptyList()
)

/** 地图地理参数，用于像素↔米；建图流程来自冻结的 MapChunk。 */
data class MapGeo(
    val mapWidth: Int,
    val mapHeight: Int,
    val resolution: Float,
    val originX: Double,
    val originY: Double,
    val headingDeg: Float,
    val mapVersion: Int,
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
)

internal fun MapImagePayload.toMapGeo() = MapGeo(
    mapWidth, mapHeight, resolution, originX, originY, headingDeg, mapVersion,
    alignmentYawDeg, rotationAlignmentDeltaDeg
)

/** 先挂起等待再发送，避免快速响应丢失；空地图参数接收当前地图，发送失败/超时返回 null。 */
internal suspend fun TcpManager.loadMapRegions(mapId: String?): MapRegionPointPayload? = coroutineScope {
    val reply = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) {
            MapRegionPointStream.responses.first { mapId.isNullOrEmpty() || it.mapId == mapId }
        }
    }
    if (!requestMapRegionPoints(mapId)) { reply.cancel(); null } else reply.await()
}

data class MapScreenStep2UiState(
    val mapImageBytes: ByteArray? = null,
    val isMapLoading: Boolean = false,
    val mapLoadError: String? = null,
    val isSubmitting: Boolean = false,
    /** 协议中的逻辑像素宽高，用于边界与世界坐标换算 */
    val mapImageSize: Pair<Int, Int>? = null,
    /** 解码后的位图尺寸，可能与协议略有差异，用于缩放映射 */
    val bitmapDecodeSize: Pair<Int, Int>? = null,
    val mapGeo: MapGeo? = null,
    val toolMode: EditToolMode = EditToolMode.Gesture,
    val zoom: Float = 1f,
    val pan: Offset = Offset.Zero,
    val eraseAreas: List<EraseRectArea> = emptyList(),
    val activeAreaId: Long? = null,
    val undoAreas: List<EraseRectArea> = emptyList(),
    val redoAreas: List<EraseRectArea> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val editOutput: EditOutput = EditOutput(),
    val robotPose: DevicePosePayload? = null,
    /** Debug：最近一次点击在位图内对应的世界坐标（米），图外或未加载时为 null */
    val debugTapWorldM: Offset? = null,
    val debugTapHint: String? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val alignmentYaw: Float = 0f,
    val previewWorkRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewObstacleRegions: List<MapPreviewRegionItem> = emptyList(),
    val previewEraseRegions: List<MapPreviewRegionItem> = emptyList()
)

@HiltViewModel
class MapScreenStep2ViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: com.sinelynx.grindingrobot.core.data.state.AppState
) : ViewModel() {
    companion object {
        private const val MAX_HISTORY = 20
        private const val DEFAULT_AREA_WIDTH = 120f
        private const val DEFAULT_AREA_HEIGHT = 80f
    }

    private val _uiState = MutableStateFlow(MapScreenStep2UiState())
    val uiState: StateFlow<MapScreenStep2UiState> = _uiState.asStateFlow()
    private var nextAreaId = 1L
    private var currentMapId: String? = null
    private var loadedSessionId: Long? = null
    private var loadJob: Job? = null
    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            DeviceStatusStream.pose.collect { pose ->
                _uiState.update { it.copy(robotPose = pose) }
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
    }

    /** 只从会话恢复底图并刷新区域；缓存丢失时禁止回退到预览接口，以免编辑坐标基准变化。 */
    fun loadMapData(mapId: String? = null) {
        currentMapId = mapId
        loadJob?.cancel()
        val snapshot = MapBuildSessionStream.snapshotFor(mapId)
        if (snapshot == null) {
            _uiState.update { it.copy(isMapLoading = false, mapImageBytes = null, mapGeo = null,
                mapLoadError = "地图缓存不存在，请返回第一步重新获取") }
            return
        }
        val frame = snapshot.frame
        // 同会话返回页面保留未提交擦除和手动角度；新 Step1 会话才重置编辑态。
        val newSession = loadedSessionId != snapshot.sessionId
        loadedSessionId = snapshot.sessionId
        _uiState.update {
            val state = if (newSession) MapScreenStep2UiState(
                robotPose = it.robotPose, robotWidth = it.robotWidth, robotLength = it.robotLength
            ) else it
            state.copy(mapImageBytes = frame.imageBytes, mapImageSize = frame.mapWidth to frame.mapHeight,
                bitmapDecodeSize = decodeImageSize(frame.imageBytes), mapGeo = frame.toMapGeo(),
                alignmentYaw = if (newSession) frame.alignmentYawDeg + frame.rotationAlignmentDeltaDeg else state.alignmentYaw,
                isMapLoading = true, mapLoadError = null).refreshDerived()
        }
        loadJob = viewModelScope.launch {
            val regions = tcpManager.loadMapRegions(mapId)
            if (regions == null || !regions.isSuccess) {
                val error = regions?.message?.ifBlank { null } ?: "区域信息获取失败，请重试"
                _uiState.update { it.copy(isMapLoading = false, mapLoadError = error) }
                ToastUtils.showError(error)
                return@launch
            }
            _uiState.update { it.copy(isMapLoading = false,
                previewWorkRegions = regions.workRegions.map { region -> region.region.toUiItem() },
                previewObstacleRegions = regions.obstacleRegions.map { region -> region.toUiItem() },
                previewEraseRegions = regions.eraseRegions.map { region -> region.toUiItem() }) }
        }
    }

    fun cancelPendingOperations() {
        loadJob?.cancel()
        submitJob?.cancel()
        _uiState.update { it.copy(isSubmitting = false) }
    }

    /** 取消建图时同时通知板端停止 Super-LIO 建图节点。 */
    fun onCancelBuild() {
        cancelPendingOperations()
        val sent = tcpManager.stopMappingMode()
        if (!sent) {
            LogUtils.w("MapScreenStep2ViewModel", "stopMappingMode 发送失败")
        }
    }

    fun clearDebugTap() {
        _uiState.update { it.copy(debugTapWorldM = null, debugTapHint = null) }
    }

    /** 位图像素坐标（与画布逆变换一致）→ 世界坐标（米），用于 Debug 显示。 */
    fun reportDebugTapBitmapPixel(bitmapPx: Offset) {
        _uiState.update { state ->
            val geo = state.mapGeo
            val mapSize = state.mapImageSize
            val bmpPair = state.bitmapDecodeSize
            if (geo == null || mapSize == null || bmpPair == null) {
                return@update state.copy(
                    debugTapWorldM = null,
                    debugTapHint = "无地图/地理数据"
                )
            }
            val bmpW = bmpPair.first
            val bmpH = bmpPair.second
            if (bmpW <= 0 || bmpH <= 0) {
                return@update state.copy(debugTapWorldM = null, debugTapHint = "位图尺寸无效")
            }
            val inside = bitmapPx.x in 0f..bmpW.toFloat() && bitmapPx.y in 0f..bmpH.toFloat()
            if (!inside) {
                return@update state.copy(debugTapWorldM = null, debugTapHint = "点击在地图图像外")
            }
            val tcp = bitmapPxToTcpPx(bitmapPx, bmpW, bmpH, mapSize.first, mapSize.second)
            val world = tcpPixelToWorld(tcp.x, tcp.y, geo)
            state.copy(debugTapWorldM = world, debugTapHint = null)
        }
    }

    fun selectGestureMode() {
        _uiState.update { state ->
            state.copy(toolMode = EditToolMode.Gesture).refreshDerived()
        }
    }

    fun selectEraseMode() {
        _uiState.update { state ->
            state.copy(toolMode = EditToolMode.Erase).refreshDerived()
        }
    }

    fun updateTransform(zoom: Float, pan: Offset) {
        _uiState.update { it.copy(zoom = zoom, pan = pan) }
    }

    fun updateAlignmentYaw(yaw: Float) {
        _uiState.update {
            it.copy(alignmentYaw = yaw.coerceIn(-180f, 180f)).refreshDerived()
        }
    }

    fun adjustAlignmentYaw(delta: Float) {
        _uiState.update { state ->
            val newYaw = (state.alignmentYaw + delta).coerceIn(-180f, 180f)
            state.copy(alignmentYaw = newYaw).refreshDerived()
        }
    }

    fun createAreaAt(pointInBitmapPx: Offset) {
        _uiState.update { state ->
            val mapSize = state.mapImageSize ?: return@update state
            val bmpW = state.bitmapDecodeSize?.first ?: return@update state
            val bmpH = state.bitmapDecodeSize?.second ?: return@update state
            val tcpPoint =
                bitmapPxToTcpPx(pointInBitmapPx, bmpW, bmpH, mapSize.first, mapSize.second)
            val maxLeft = (mapSize.first.toFloat() - DEFAULT_AREA_WIDTH).coerceAtLeast(0f)
            val maxTop = (mapSize.second.toFloat() - DEFAULT_AREA_HEIGHT).coerceAtLeast(0f)
            val left = (tcpPoint.x - DEFAULT_AREA_WIDTH / 2f).coerceIn(0f, maxLeft)
            val top = (tcpPoint.y - DEFAULT_AREA_HEIGHT / 2f).coerceIn(0f, maxTop)
            val tcpRect = EraseRectArea(
                id = nextAreaId++,
                left = left,
                top = top,
                right = left + DEFAULT_AREA_WIDTH,
                bottom = top + DEFAULT_AREA_HEIGHT,
                confirmed = false
            )
            val area = tcpRectToBitmapRect(tcpRect, bmpW, bmpH, mapSize.first, mapSize.second)
            state.copy(
                eraseAreas = state.eraseAreas + area,
                activeAreaId = area.id,
                undoAreas = state.undoAreas,
                redoAreas = state.redoAreas
            ).refreshDerived()
        }
    }

    fun resizeActiveAreaTo(pointInBitmapPx: Offset) {
        _uiState.update { state ->
            val activeId = state.activeAreaId ?: return@update state
            val mapSize = state.mapImageSize ?: return@update state
            val bmpW = state.bitmapDecodeSize?.first ?: return@update state
            val bmpH = state.bitmapDecodeSize?.second ?: return@update state
            val index = state.eraseAreas.indexOfFirst { it.id == activeId }
            if (index < 0) return@update state

            val area = state.eraseAreas[index]
            val tcpArea = bitmapRectToTcpRect(area, bmpW, bmpH, mapSize.first, mapSize.second)
            val tcpPoint =
                bitmapPxToTcpPx(pointInBitmapPx, bmpW, bmpH, mapSize.first, mapSize.second)
            val minSizeTcp = 24f
            val newRight = tcpPoint.x.coerceIn(tcpArea.left + minSizeTcp, mapSize.first.toFloat())
            val newBottom = tcpPoint.y.coerceIn(tcpArea.top + minSizeTcp, mapSize.second.toFloat())
            val updatedTcp = tcpArea.copy(right = newRight, bottom = newBottom)
            val updatedArea =
                tcpRectToBitmapRect(updatedTcp, bmpW, bmpH, mapSize.first, mapSize.second)

            state.copy(
                eraseAreas = state.eraseAreas.toMutableList().also { it[index] = updatedArea }
            )
        }
    }

    fun moveActiveAreaBy(deltaInBitmapPx: Offset) {
        _uiState.update { state ->
            val activeId = state.activeAreaId ?: return@update state
            val mapSize = state.mapImageSize ?: return@update state
            val bmpW = state.bitmapDecodeSize?.first ?: return@update state
            val bmpH = state.bitmapDecodeSize?.second ?: return@update state
            val index = state.eraseAreas.indexOfFirst { it.id == activeId }
            if (index < 0) return@update state

            val area = state.eraseAreas[index]
            val tcpArea = bitmapRectToTcpRect(area, bmpW, bmpH, mapSize.first, mapSize.second)
            val deltaTcp = Offset(
                x = deltaInBitmapPx.x * mapSize.first / bmpW,
                y = deltaInBitmapPx.y * mapSize.second / bmpH
            )
            val width = tcpArea.right - tcpArea.left
            val height = tcpArea.bottom - tcpArea.top
            // UI 已经依据地图旋转后的可见父边界限制本次移动。这里不能再次按未旋转
            // Content 边界截断，否则松手时区域会跳回到旋转前的限制位置。
            val newLeft = tcpArea.left + deltaTcp.x
            val newTop = tcpArea.top + deltaTcp.y
            val movedTcp = tcpArea.copy(
                left = newLeft,
                top = newTop,
                right = newLeft + width,
                bottom = newTop + height
            )
            val updatedArea =
                tcpRectToBitmapRect(movedTcp, bmpW, bmpH, mapSize.first, mapSize.second)

            state.copy(
                eraseAreas = state.eraseAreas.toMutableList().also { it[index] = updatedArea }
            )
        }
    }

    fun confirmActiveArea(alignedCornersInBitmapPx: List<Offset>) {
        _uiState.update { state ->
            val activeId = state.activeAreaId ?: return@update state
            val index = state.eraseAreas.indexOfFirst { it.id == activeId }
            if (index < 0) return@update state
            if (alignedCornersInBitmapPx.size != 4) return@update state
            val updatedArea = state.eraseAreas[index].copy(
                confirmed = true,
                alignedCorners = alignedCornersInBitmapPx
            )
            state.copy(
                eraseAreas = state.eraseAreas.toMutableList().also { it[index] = updatedArea },
                activeAreaId = null,
                undoAreas = (state.undoAreas + updatedArea).takeLast(MAX_HISTORY),
                redoAreas = emptyList()
            ).refreshDerived()
        }
    }

    fun removeActiveArea() {
        _uiState.update { state ->
            val activeId = state.activeAreaId ?: return@update state
            state.copy(
                eraseAreas = state.eraseAreas.filterNot { it.id == activeId },
                undoAreas = state.undoAreas.filterNot { it.id == activeId },
                redoAreas = state.redoAreas.filterNot { it.id == activeId },
                activeAreaId = null
            ).refreshDerived()
        }
    }

    fun undoStroke() {
        _uiState.update { state ->
            if (state.undoAreas.isEmpty()) {
                state
            } else {
                val removed = state.undoAreas.last()
                state.copy(
                    eraseAreas = state.eraseAreas.filterNot { it.id == removed.id },
                    undoAreas = state.undoAreas.dropLast(1),
                    redoAreas = (state.redoAreas + removed).takeLast(MAX_HISTORY),
                    activeAreaId = null
                ).refreshDerived()
            }
        }
    }

    fun redoStroke() {
        _uiState.update { state ->
            if (state.redoAreas.isEmpty()) {
                state
            } else {
                val restored = state.redoAreas.last()
                state.copy(
                    eraseAreas = state.eraseAreas + restored,
                    undoAreas = (state.undoAreas + restored).takeLast(MAX_HISTORY),
                    redoAreas = state.redoAreas.dropLast(1),
                    activeAreaId = null
                ).refreshDerived()
            }
        }
    }

    /** 串行提交擦除并逐项等待确认，全部成功后才进入 Step3，防止其区域查询早于编辑生效。 */
    fun sendEditedDataToEmbeddedOnNextStep(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (state.isMapLoading || state.mapLoadError != null || MapBuildSessionStream.snapshotFor(currentMapId) == null) {
            ToastUtils.showError(state.mapLoadError ?: "请等待地图区域加载完成")
            return
        }
        _uiState.update { it.copy(isSubmitting = true) }
        submitJob = viewModelScope.launch {
            try {
                val batchId = System.currentTimeMillis()
                for (area in state.editOutput.confirmedAreas) {
                    MapEditStream.reset()
                    val reply = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(MAP_TCP_REQUEST_TIMEOUT_MS) { MapEditStream.payload.filterNotNull().first() }
                    }
                    val sent = tcpManager.sendMapEditClearRegion(
                        editId = "${batchId}_${area.id}", mapId = currentMapId,
                        points = listOf(area.leftTopM, area.rightTopM, area.rightBottomM, area.leftBottomM).map { it.x to it.y }
                    )
                    val response = if (sent) reply.await() else { reply.cancel(); null }
                    if (response?.isSuccess != true) {
                        ToastUtils.showError(response?.message?.ifBlank { null } ?: "擦除区域提交失败，请重试")
                        return@launch
                    }
                    // 已确认项移入服务端区域展示；重试或返回时不重复提交，也不再参与本地撤销。
                    _uiState.update { it.copy(eraseAreas = it.eraseAreas.filterNot { item -> item.id == area.id },
                        previewEraseRegions = it.previewEraseRegions + MapPreviewRegionItem(
                            regionId = "${batchId}_${area.id}", regionName = "",
                            points = listOf(area.leftTopM, area.rightTopM, area.rightBottomM, area.leftBottomM)
                                .map { point -> MapPreviewPointItem(point.x, point.y) }),
                        undoAreas = emptyList(), redoAreas = emptyList()).refreshDerived() }
                }
                if (!tcpManager.sendMapAlignment(currentMapId.orEmpty(), state.alignmentYaw)) {
                    ToastUtils.showError("地图对准角发送失败，请重试")
                    return@launch
                }
                // 图片字节不变，只同步会话显示总角，让 Step3/4 继续使用同一投影基准。
                MapBuildSessionStream.updateRotation(currentMapId, state.alignmentYaw)
                onSuccess()
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun MapScreenStep2UiState.refreshDerived(): MapScreenStep2UiState {
        val confirmedBitmap = eraseAreas.filter { it.confirmed }
        val geo = mapGeo
        val bmpW = bitmapDecodeSize?.first
        val bmpH = bitmapDecodeSize?.second
        val mapSize = mapImageSize
        val output = if (geo != null && bmpW != null && bmpH != null && mapSize != null) {
            EditOutput(
                confirmedAreas = confirmedBitmap.map {
                    bitmapAreaToWorldOutput(
                        area = it,
                        bmpW = bmpW,
                        bmpH = bmpH,
                        mapW = mapSize.first,
                        mapH = mapSize.second,
                        geo = geo,
                        alignmentYaw = alignmentYaw
                    )
                }
            )
        } else {
            EditOutput()
        }
        return copy(
            canUndo = undoAreas.isNotEmpty(),
            canRedo = redoAreas.isNotEmpty(),
            editOutput = output
        )
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

    private fun bitmapPxToTcpPx(
        p: Offset,
        bmpW: Int,
        bmpH: Int,
        mapW: Int,
        mapH: Int
    ): Offset = Offset(
        x = p.x * mapW / bmpW.toFloat(),
        y = p.y * mapH / bmpH.toFloat()
    )

    private fun bitmapRectToTcpRect(
        area: EraseRectArea,
        bmpW: Int,
        bmpH: Int,
        mapW: Int,
        mapH: Int
    ): EraseRectArea {
        val lt = bitmapPxToTcpPx(Offset(area.left, area.top), bmpW, bmpH, mapW, mapH)
        val rb = bitmapPxToTcpPx(Offset(area.right, area.bottom), bmpW, bmpH, mapW, mapH)
        return EraseRectArea(
            id = area.id,
            left = lt.x,
            top = lt.y,
            right = rb.x,
            bottom = rb.y,
            confirmed = area.confirmed
        )
    }

    private fun tcpRectToBitmapRect(
        tcp: EraseRectArea,
        bmpW: Int,
        bmpH: Int,
        mapW: Int,
        mapH: Int
    ): EraseRectArea {
        val lt = Offset(tcp.left * bmpW / mapW.toFloat(), tcp.top * bmpH / mapH.toFloat())
        val rb = Offset(tcp.right * bmpW / mapW.toFloat(), tcp.bottom * bmpH / mapH.toFloat())
        return EraseRectArea(
            id = tcp.id,
            left = lt.x,
            top = lt.y,
            right = rb.x,
            bottom = rb.y,
            confirmed = tcp.confirmed
        )
    }

    /**
     * 图片像素 (左上角为原点，y 向下) → 世界坐标 (米，origin 为地图左下角，y 向上)。
     * 先在地图局部系：x 向右、y 从底边向上，再绕 origin 旋转 [headingDeg]。
     */
    private fun tcpPixelToWorld(px: Float, py: Float, geo: MapGeo): Offset {
        val res = geo.resolution
        val h = geo.mapHeight
        val xm = px * res
        val ym = (h - py) * res
        val rad = Math.toRadians(geo.headingDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val ox = geo.originX
        val oy = geo.originY
        val wx = (ox + c * xm - s * ym).toFloat()
        val wy = (oy + s * xm + c * ym).toFloat()
        return Offset(wx, wy)
    }

    private fun tcpRectToWorldOutput(area: EraseRectArea, geo: MapGeo): EraseAreaWorldOutput {
        val lt = tcpPixelToWorld(area.left, area.top, geo)
        val rt = tcpPixelToWorld(area.right, area.top, geo)
        val rb = tcpPixelToWorld(area.right, area.bottom, geo)
        val lb = tcpPixelToWorld(area.left, area.bottom, geo)
        return EraseAreaWorldOutput(
            id = area.id,
            leftBottomM = lb,
            rightBottomM = rb,
            rightTopM = rt,
            leftTopM = lt
        )
    }

    private fun bitmapAreaToWorldOutput(
        area: EraseRectArea,
        bmpW: Int,
        bmpH: Int,
        mapW: Int,
        mapH: Int,
        geo: MapGeo,
        alignmentYaw: Float
    ): EraseAreaWorldOutput {
        // alignedCorners 属于“地图已旋转后的内容空间”，不能直接用于世界坐标换算。
        // 先绕图片中心反向旋转，恢复原始 Bitmap 坐标，再走 Bitmap -> TCP 像素 -> 世界坐标。
        val bitmapCorners = if (area.alignedCorners.size == 4) {
            val center = Offset(bmpW / 2f, bmpH / 2f)
            area.alignedCorners.map { alignedPoint ->
                rotateBitmapPoint(
                    point = alignedPoint,
                    center = center,
                    angleDeg = -alignmentYaw
                )
            }
        } else {
            listOf(
                Offset(area.left, area.top),
                Offset(area.right, area.top),
                Offset(area.right, area.bottom),
                Offset(area.left, area.bottom)
            )
        }
        val worldCorners = bitmapCorners.map { bitmapPoint ->
            val tcpPoint = bitmapPxToTcpPx(
                p = bitmapPoint,
                bmpW = bmpW,
                bmpH = bmpH,
                mapW = mapW,
                mapH = mapH
            )
            tcpPixelToWorld(tcpPoint.x, tcpPoint.y, geo)
        }
        return EraseAreaWorldOutput(
            id = area.id,
            leftTopM = worldCorners[0],
            rightTopM = worldCorners[1],
            rightBottomM = worldCorners[2],
            leftBottomM = worldCorners[3]
        )
    }

    private fun rotateBitmapPoint(point: Offset, center: Offset, angleDeg: Float): Offset {
        val radians = Math.toRadians(angleDeg.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val dx = point.x - center.x
        val dy = point.y - center.y
        return Offset(
            x = center.x + dx * cosValue - dy * sinValue,
            y = center.y + dx * sinValue + dy * cosValue
        )
    }
}
