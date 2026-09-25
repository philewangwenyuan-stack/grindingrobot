package com.sinelynx.grindingrobot.feature.map.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapRequestOutcome
import com.sinelynx.grindingrobot.core.model.state.MapRequestResultPayload
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import android.graphics.BitmapFactory
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.feature.map.ui.DirectionCommand
import com.sinelynx.grindingrobot.feature.map.ui.JoystickPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import sl_link.SlLink
import kotlin.math.roundToInt

data class MapUiState(
    val runSpeed: Float = 0.1f,
    val turnSpeed: Int = 70,
    val currentCommand: DirectionCommand = DirectionCommand.Stop,
    val lastPosition: JoystickPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f),
    val mapImageBytes: ByteArray? = null,
    val mapImageSize: Pair<Int, Int>? = null,
    val mapGeo: MapGeo? = null,
    val mapLoadMessage: String = "地图加载中...",
    val mapLoadFailed: Boolean = false,
    val robotPose: DevicePosePayload? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val robotFootprint: List<com.sinelynx.grindingrobot.core.data.state.AppState.FootprintPoint> = emptyList()
)

private sealed class MapTransferEvent {
    data class Result(val value: MapRequestResultPayload) : MapTransferEvent()
    data class Frame(val value: MapImagePayload) : MapTransferEvent()
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: com.sinelynx.grindingrobot.core.data.state.AppState
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var robotControlSendJob: Job? = null
    private var hasClearedCacheOnEnter = false
    // 离开 Step1 后立即关闭接收闸门；取消请求循环不代表网络中已发出的响应不会再到达。
    private var mapActive = false
    private var currentMapId: String? = null
    private var lastFrame: MapImagePayload? = null
    private var activeRequestId: Long? = null
    private val mapEvents = Channel<MapTransferEvent>(Channel.BUFFERED)

    init {
        if (appState.robotSettings.value == null) {
            tcpManager.requestSettingRead(readChassis = true, readMap = true)
        }
        viewModelScope.launch {
            MapImageStream.frames.collect { payload ->
                if (!mapActive || payload.requestId != activeRequestId) return@collect
                val changed = lastFrame != payload
                lastFrame = payload
                if (changed) {
                    _uiState.update {
                        it.copy(
                            mapImageBytes = payload.imageBytes,
                            mapImageSize = payload.mapWidth to payload.mapHeight,
                            mapLoadMessage = "",
                            mapGeo = MapGeo(
                                mapWidth = payload.mapWidth,
                                mapHeight = payload.mapHeight,
                                resolution = payload.resolution,
                                originX = payload.originX,
                                originY = payload.originY,
                                headingDeg = payload.headingDeg,
                                mapVersion = payload.mapVersion,
                                alignmentYawDeg = payload.alignmentYawDeg,
                                rotationAlignmentDeltaDeg = payload.rotationAlignmentDeltaDeg
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(mapLoadMessage = "") }
                }
                mapEvents.trySend(MapTransferEvent.Frame(payload))
            }
        }
        viewModelScope.launch {
            MapImageStream.results.collect { result ->
                if (mapActive && result.requestId == activeRequestId) {
                    mapEvents.trySend(MapTransferEvent.Result(result))
                }
            }
        }
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
                        robotLength = settings?.robotLength,
                        robotFootprint = settings?.footprint.orEmpty()
                    )
                }
            }
        }
    }

    /** 进入建图 Step1（MapScreen）时启动；TCP 由首页 [HomeViewModel] 连接。 */
    fun startMapSnapshotLoop(mapId: String? = null) {
        // 返回 Step1 也重新取帧，清除 StateFlow 的旧值，防止尚未收到新帧就再次“下一步”。
        stopMapSnapshotLoop()
        currentMapId = mapId
        lastFrame = null
        MapBuildSessionStream.begin()
        MapImageStream.reset()
        _uiState.update { it.copy(mapImageBytes = null, mapImageSize = null, mapGeo = null,
            mapLoadMessage = "地图加载中...", mapLoadFailed = false) }
        mapActive = true
        requestJob = viewModelScope.launch {
            var notReadyAttempts = 0
            while (isActive && mapActive) {
                if (!tcpManager.isConnected()) {
                    _uiState.update { it.copy(mapLoadMessage = "等待设备连接...") }
                    delay(500L)
                    continue
                }
                val requestId = ((UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                    .takeIf { it != 0L } ?: 1L)
                activeRequestId = requestId
                if (!tcpManager.requestMapSnapshot(mapId, requestId = requestId)) {
                    activeRequestId = null
                    _uiState.update { it.copy(mapLoadMessage = "地图请求发送失败，正在重试...") }
                    delay(500L)
                    continue
                }
                var nextDelayMs = MAP_SNAPSHOT_INTERVAL_MS
                var stopOnError = false
                val deadline = SystemClock.elapsedRealtime() + MAP_REQUEST_TIMEOUT_MS
                while (isActive && mapActive && activeRequestId == requestId) {
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) {
                        _uiState.update { it.copy(mapLoadMessage = "地图响应超时，正在重试...") }
                        nextDelayMs = 500L
                        break
                    }
                    val event = withTimeoutOrNull(remaining) { mapEvents.receive() }
                    if (event == null) {
                        _uiState.update { it.copy(mapLoadMessage = "地图响应超时，正在重试...") }
                        nextDelayMs = 500L
                        break
                    }
                    when (event) {
                        is MapTransferEvent.Frame -> if (event.value.requestId == requestId) {
                            notReadyAttempts = 0
                            break
                        }
                        is MapTransferEvent.Result -> {
                            val result = event.value
                            if (result.requestId != requestId) continue
                            when (result.outcome) {
                                MapRequestOutcome.READY -> _uiState.update {
                                    it.copy(mapLoadMessage = "正在接收地图...")
                                }
                                MapRequestOutcome.NOT_READY -> {
                                    notReadyAttempts++
                                    nextDelayMs = maxOf(result.retryAfterMs.toLong(),
                                        500L shl minOf(notReadyAttempts - 1, 2)).coerceIn(250L, 2_000L)
                                    _uiState.update { it.copy(mapLoadMessage = "等待地图生成...") }
                                    break
                                }
                                MapRequestOutcome.NOT_FOUND -> {
                                    _uiState.update { it.copy(mapLoadMessage = "找不到保存的地图", mapLoadFailed = true) }
                                    stopOnError = true
                                    break
                                }
                                MapRequestOutcome.ERROR -> {
                                    _uiState.update { it.copy(mapLoadMessage = "地图加载失败，请重新进入页面重试", mapLoadFailed = true) }
                                    stopOnError = true
                                    break
                                }
                            }
                        }
                    }
                }
                tcpManager.cancelMapSnapshotRequest(requestId)
                if (activeRequestId == requestId) activeRequestId = null
                if (stopOnError) break
                delay(nextDelayMs)
            }
        }
    }

    /** 离开 MapScreen 时停止周期请求。 */
    fun stopMapSnapshotLoop() {
        mapActive = false
        activeRequestId?.let(tcpManager::cancelMapSnapshotRequest)
        activeRequestId = null
        requestJob?.cancel()
        requestJob = null
        while (mapEvents.tryReceive().isSuccess) { /* Discard prior view/session events. */ }
    }

    fun requestLiveMapCacheClearOnNewMapEnter(mapId: String?) {
        if (hasClearedCacheOnEnter) return
        hasClearedCacheOnEnter = true
        val sent = tcpManager.requestLiveMapCacheClear()
        if (!sent) {
            LogUtils.w("MapViewModel", "requestLiveMapCacheClear 发送失败")
        }
    }

    fun requestRadarMapSync() {
        val sent = tcpManager.requestRadarMapSync()
        if (!sent) {
            LogUtils.w("MapViewModel", "requestRadarMapSync 发送失败")
        }
    }

    fun increaseRunSpeed() {
        val newSpeed = normalizeRunSpeed(_uiState.value.runSpeed + RUN_SPEED_STEP)
        _uiState.update { it.copy(runSpeed = newSpeed) }
        sendChassisSpeedWrite(newSpeed)
    }

    fun decreaseRunSpeed() {
        val newSpeed = normalizeRunSpeed(_uiState.value.runSpeed - RUN_SPEED_STEP)
        _uiState.update { it.copy(runSpeed = newSpeed) }
        sendChassisSpeedWrite(newSpeed)
    }

    private fun sendChassisSpeedWrite(speed: Float) {
        val settings = SlLink.ChassisSettings.newBuilder()
            .setRunSpeed(speed)
            .build()
        val sent = tcpManager.requestSettingWrite(chassisSettings = settings)
        if (!sent) {
            LogUtils.w("MapViewModel", "requestSettingWrite chassisSettings 发送失败")
        }
    }

    fun increaseTurnSpeed() {
        val newSpeed = (_uiState.value.turnSpeed + TURN_SPEED_STEP)
            .coerceAtMost(MAX_TURN_SPEED_PERCENT)
        _uiState.update { it.copy(turnSpeed = newSpeed) }
        sendChassisTurnSpeedWrite(newSpeed)
    }

    fun decreaseTurnSpeed() {
        val newSpeed = (_uiState.value.turnSpeed - TURN_SPEED_STEP)
            .coerceAtLeast(MIN_TURN_SPEED_PERCENT)
        _uiState.update { it.copy(turnSpeed = newSpeed) }
        sendChassisTurnSpeedWrite(newSpeed)
    }

    private fun sendChassisTurnSpeedWrite(turnSpeedPercent: Int) {
        val maxTurnSpeedRatio = (turnSpeedPercent / 100f)
            .coerceIn(MIN_TURN_SPEED_RATIO, MAX_TURN_SPEED_RATIO)
        val settings = SlLink.ChassisSettings.newBuilder()
            .setMaxTurnSpeedRatio(maxTurnSpeedRatio)
            .build()
        val sent = tcpManager.requestSettingWrite(chassisSettings = settings)
        if (!sent) {
            LogUtils.w("MapViewModel", "requestSettingWrite maxTurnSpeedRatio 发送失败")
        }
    }

    fun onCommandStart(command: DirectionCommand) {
        _uiState.update { it.copy(currentCommand = command) }
    }

    fun onCommandEnd(command: DirectionCommand) {
        _uiState.update { state ->
            if (state.currentCommand == command) state.copy(currentCommand = DirectionCommand.Stop) else state
        }
    }

    fun onPositionChanged(position: JoystickPosition) {
        _uiState.update { it.copy(lastPosition = position) }

        if (position.distanceRatio <= 0f) {
            stopContinuousRobotControlSend()
            sendRobotControlPosition(position)
            return
        }

        if (robotControlSendJob?.isActive == true) return

        sendRobotControlPosition(position)
        robotControlSendJob = viewModelScope.launch {
            while (isActive) {
                delay(ROBOT_CONTROL_SEND_INTERVAL_MS)
                val latestPosition = _uiState.value.lastPosition
                if (latestPosition.distanceRatio <= 0f) break
                sendRobotControlPosition(latestPosition)
            }
        }
    }

    private fun sendRobotControlPosition(position: JoystickPosition) {
        LogUtils.d("MapViewModel", "sendRobotControlPosition: $position")
        val speedRatio = (position.distanceRatio * 100).roundToInt() / 100f
        tcpManager.sendRobotControlCommand(
            position.normalizedX,
            position.normalizedY,
            speedRatio,
            100.0f
        )
    }

    private fun stopContinuousRobotControlSend() {
        robotControlSendJob?.cancel()
        robotControlSendJob = null
    }

    fun onCancelBuild() {
        MapBuildSessionStream.clear()
        hasClearedCacheOnEnter = false
        stopMapSnapshotLoop()
        val sent = tcpManager.stopMappingMode()
        if (!sent) {
            LogUtils.w("MapViewModel", "stopMappingMode 发送失败")
        }
    }

    /** 先冻结图片和几何，再停止实时更新；返回值是页面能否进入 Step2 的唯一导航条件。 */
    fun onNextStep(): Boolean {
        if (!mapActive) return false
        val frame = lastFrame
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (frame != null) BitmapFactory.decodeByteArray(frame.imageBytes, 0, frame.imageBytes.size, bounds)
        if (frame == null || bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            !MapBuildSessionStream.freeze(currentMapId, frame)) {
            ToastUtils.showError("请等待完整有效的地图帧")
            return false
        }
        stopMapSnapshotLoop()
        return true
    }

    override fun onCleared() {
        MapBuildSessionStream.clear()
        stopMapSnapshotLoop()
        stopContinuousRobotControlSend()
        super.onCleared()
    }

    private fun normalizeRunSpeed(speed: Float): Float {
        return (speed / RUN_SPEED_STEP).roundToInt()
            .times(RUN_SPEED_STEP)
            .coerceIn(MIN_RUN_SPEED, MAX_RUN_SPEED)
    }

    companion object {
        private const val MAP_SNAPSHOT_INTERVAL_MS = 6_000L
        private const val MAP_REQUEST_TIMEOUT_MS = 12_000L
        private const val ROBOT_CONTROL_SEND_INTERVAL_MS = 200L
        private const val MIN_RUN_SPEED = 0f
        private const val MAX_RUN_SPEED = 0.15f
        private const val RUN_SPEED_STEP = 0.05f
        private const val MIN_TURN_SPEED_PERCENT = 1
        private const val MAX_TURN_SPEED_PERCENT = 100
        private const val TURN_SPEED_STEP = 5
        private const val MIN_TURN_SPEED_RATIO = 0.01f
        private const val MAX_TURN_SPEED_RATIO = 1f
    }
}
