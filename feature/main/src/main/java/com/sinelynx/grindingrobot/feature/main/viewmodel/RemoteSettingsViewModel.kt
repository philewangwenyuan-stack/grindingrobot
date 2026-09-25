package com.sinelynx.grindingrobot.feature.main.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.map.ui.DirectionCommand
import com.sinelynx.grindingrobot.feature.map.ui.JoystickPosition
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sl_link.SlLink
import kotlin.math.abs
import kotlin.math.roundToInt

data class RemoteSettingsUiState(
    val runSpeed: Float = 0.1f,
    val turnSpeed: Int = 70,
    val turnCount: Int = 800,
    val spinDirection: SpinDirection = SpinDirection.Forward,
    val currentCommand: DirectionCommand = DirectionCommand.Stop,
    val lastPosition: JoystickPosition = JoystickPosition(0f, 0f, 0f, 0f, 0f),
    val mapImageBytes: ByteArray? = null,
    val mapGeo: MapGeo? = null,
    val mapImageSize: Pair<Int, Int>? = null,
    val robotPose: DevicePosePayload? = null,
    val showAutoModeJoystickDialog: Boolean = false,
    val showCameraOverlay: Boolean = false,
    val videoStreamUrl: String? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val robotFootprint: List<AppState.FootprintPoint> = emptyList()
)

enum class SpinDirection {
    Forward,
    Reverse
}

@HiltViewModel
class RemoteSettingsViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteSettingsUiState())
    val uiState: StateFlow<RemoteSettingsUiState> = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var manualControlJob: Job? = null
    private var continuousPositionJob: Job? = null
    private var lastRobotControlSendAtMs: Long = 0L
    private var pendingManualPosition: JoystickPosition? = null
    private var manualReadyTaskId: String? = null
    private var positionChangeVersion: Long = 0L
    private var lastObservedWorkMode: Int? = null

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
            MapImageStream.mapImageBytes.collect { bytes ->
                _uiState.update { it.copy(mapImageBytes = bytes) }
            }
        }
        viewModelScope.launch {
            MapImageStream.payload.collect { payload ->
                payload ?: return@collect
                _uiState.update {
                    it.copy(
                        mapImageBytes = payload.imageBytes,
                        mapGeo = MapGeo(
                            mapWidth = payload.mapWidth,
                            mapHeight = payload.mapHeight,
                            resolution = payload.resolution,
                            originX = payload.originX,
                            originY = payload.originY,
                            headingDeg = payload.headingDeg,
                            mapVersion = 0,
                            alignmentYawDeg = payload.alignmentYawDeg,
                            rotationAlignmentDeltaDeg = payload.rotationAlignmentDeltaDeg
                        ),
                        mapImageSize = payload.mapWidth to payload.mapHeight
                    )
                }
            }
        }
        viewModelScope.launch {
            DeviceStatusStream.pose.collect { pose ->
                _uiState.update { it.copy(robotPose = pose) }
            }
        }
        viewModelScope.launch {
            TaskSchedulerStream.taskStatusReport.collect { report ->
                val reportedTaskId = report?.taskId?.trim().orEmpty()
                val readyTaskId = manualReadyTaskId
                if (reportedTaskId.isBlank() ||
                    (readyTaskId != null && reportedTaskId != readyTaskId)
                ) {
                    manualReadyTaskId = null
                }
            }
        }
        viewModelScope.launch {
            appState.runtimeTaskState.collect { runtime ->
                val autoMode = SlLink.WorkMode.WORK_MODE_AUTO.number
                val enteredAutoMode = runtime.workMode == autoMode &&
                    lastObservedWorkMode != null &&
                    lastObservedWorkMode != autoMode
                if (enteredAutoMode) {
                    manualReadyTaskId = null
                }
                lastObservedWorkMode = runtime.workMode
                if (runtime.workMode != SlLink.WorkMode.WORK_MODE_AUTO.number) {
                    _uiState.update { it.copy(showAutoModeJoystickDialog = false) }
                }
            }
        }
        viewModelScope.launch {
            appState.videoStreamInfo.collect { info ->
                _uiState.update {
                    it.copy(
                        videoStreamUrl = info?.streamUrl?.takeIf { url -> url.isNotBlank() }
                    )
                }
            }
        }
        viewModelScope.launch {
            appState.chassisSettings.collect { settings ->
                settings ?: return@collect
                _uiState.update {
                    it.copy(
                        runSpeed = normalizeRunSpeed(settings.runSpeed),
                        turnCount = settings.discSpeedRpm.coerceIn(MIN_TURN_COUNT, MAX_TURN_COUNT)
                    )
                }
            }
        }
        startMapSnapshotLoop()
    }

    /** TCP 由首页连接；此处仅按需拉取地图快照。 */
    private fun startMapSnapshotLoop() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            while (isActive) {
                if (tcpManager.isConnected()) {
                    tcpManager.requestMapSnapshot()
                }
                delay(MAP_SNAPSHOT_INTERVAL_MS)
            }
        }
    }

    fun onScreenEnter() {
        appState.chassisSettings.value?.let { settings ->
            _uiState.update {
                it.copy(
                    runSpeed = normalizeRunSpeed(settings.runSpeed),
                    turnCount = settings.discSpeedRpm.coerceIn(MIN_TURN_COUNT, MAX_TURN_COUNT)
                )
            }
        }
        tcpManager.requestSettingRead(true, null)
    }

    fun increaseRunSpeed() {
        _uiState.update { it.copy(runSpeed = normalizeRunSpeed(it.runSpeed + RUN_SPEED_STEP)) }
    }

    fun decreaseRunSpeed() {
        _uiState.update { it.copy(runSpeed = normalizeRunSpeed(it.runSpeed - RUN_SPEED_STEP)) }
    }

    fun commitRunSpeed() {
        val state = _uiState.value
        setSettingWrite(state.runSpeed, state.turnSpeed.toFloat(), state.turnCount)
    }

    fun increaseTurnSpeed() {
        _uiState.update { it.copy(turnSpeed = (it.turnSpeed + 5).coerceAtMost(100)) }
        val current = _uiState.value.turnSpeed
        setSettingWrite(_uiState.value.runSpeed.toFloat(), current.toFloat(), _uiState.value.turnCount)
    }

    fun decreaseTurnSpeed() {
        _uiState.update { it.copy(turnSpeed = (it.turnSpeed - 5).coerceAtLeast(1)) }
        val current = _uiState.value.turnSpeed
        setSettingWrite(_uiState.value.runSpeed.toFloat(), current.toFloat(), _uiState.value.turnCount)
    }

    fun increaseTurnCount() {
        _uiState.update { it.copy(turnCount = (it.turnCount + 10).coerceAtMost(1500)) }
        val current = _uiState.value.turnCount
        setSettingWrite(_uiState.value.runSpeed.toFloat(), _uiState.value.turnSpeed.toFloat(), current)
    }

    fun decreaseTurnCount() {
        _uiState.update { it.copy(turnCount = (it.turnCount - 10).coerceAtLeast(MIN_TURN_COUNT)) }
        val current = _uiState.value.turnCount
        setSettingWrite(_uiState.value.runSpeed.toFloat(), _uiState.value.turnSpeed.toFloat(), current)
    }

    fun updateTurnCountDraft(value: Float) {
        _uiState.update { state ->
            state.copy(turnCount = normalizeTurnCount(value))
        }
    }

    fun commitTurnCount() {
        val state = _uiState.value
        setSettingWrite(state.runSpeed, state.turnSpeed.toFloat(), state.turnCount)
    }

    fun selectSpinDirection(direction: SpinDirection) {
        _uiState.update { it.copy(spinDirection = direction) }
    }

    fun onCommandStart(command: DirectionCommand) {
        if (command == DirectionCommand.Stop) {
            _uiState.update { it.copy(currentCommand = command) }
            return
        }
        if (isAutoMode()) {
            _uiState.update { it.copy(showAutoModeJoystickDialog = true) }
            return
        }
        _uiState.update { it.copy(currentCommand = command) }
    }

    fun onCommandEnd(command: DirectionCommand) {
        _uiState.update { state ->
            if (state.currentCommand == command) state.copy(currentCommand = DirectionCommand.Stop) else state
        }
    }

    fun onPositionChanged(position: JoystickPosition) {
        val previousPosition = _uiState.value.lastPosition
        if (!position.hasRealJoystickInput()) {
            _uiState.update { it.copy(lastPosition = position) }
            stopContinuousPositionSend()
            if (previousPosition.hasRealJoystickInput() && !isAutoMode()) {
                lastRobotControlSendAtMs = SystemClock.elapsedRealtime()
                requestManualControl(position)
            }
            return
        }
        val positionChanged = !position.hasSameControlPosition(previousPosition)
        _uiState.update { it.copy(lastPosition = position) }
        if (isAutoMode()) {
            pendingManualPosition = position
            stopContinuousPositionSend()
            if (!_uiState.value.showAutoModeJoystickDialog) {
                _uiState.update { it.copy(showAutoModeJoystickDialog = true) }
            }
            return
        }
        if (positionChanged || continuousPositionJob?.isActive != true) {
            positionChangeVersion += 1
            startContinuousPositionSend(positionChangeVersion)
        }
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastRobotControlSendAtMs < ROBOT_CONTROL_SEND_INTERVAL_MS) {
            return
        }
        lastRobotControlSendAtMs = nowMs
        requestManualControl(position)
    }

    fun confirmAutoModeJoystickDialog() {
        _uiState.update { it.copy(showAutoModeJoystickDialog = false) }
        val position = pendingManualPosition ?: _uiState.value.lastPosition
        if (position.hasRealJoystickInput()) {
            positionChangeVersion += 1
            startContinuousPositionSend(positionChangeVersion)
            requestManualControl(position)
        }
    }

    fun cancelAutoModeJoystickDialog() {
        pendingManualPosition = null
        stopContinuousPositionSend()
        _uiState.update { it.copy(showAutoModeJoystickDialog = false) }
    }

    private fun startContinuousPositionSend(version: Long) {
        continuousPositionJob?.cancel()
        continuousPositionJob = viewModelScope.launch {
            delay(ROBOT_CONTROL_SEND_INTERVAL_MS)
            while (isActive && version == positionChangeVersion) {
                val position = _uiState.value.lastPosition
                if (!position.hasRealJoystickInput() || isAutoMode()) {
                    break
                }
                requestManualControl(position)
                delay(ROBOT_CONTROL_SEND_INTERVAL_MS)
            }
        }
    }

    private fun stopContinuousPositionSend() {
        continuousPositionJob?.cancel()
        continuousPositionJob = null
        positionChangeVersion += 1
    }

    private fun requestManualControl(position: JoystickPosition) {
        pendingManualPosition = position
        if (manualControlJob?.isActive == true) return
        manualControlJob = viewModelScope.launch {
            if (!stopActiveTaskBeforeManualControl()) {
                pendingManualPosition = null
                return@launch
            }
            val targetPosition = pendingManualPosition ?: return@launch
            pendingManualPosition = null
            sendManualControlCommand(targetPosition)
        }
    }

    private fun sendManualControlCommand(position: JoystickPosition) {
        LogUtils.d("RemoteSettingsViewModel", "sendManualControlCommand: $position")
        val state = _uiState.value
        val speedRatio = (position.distanceRatio * 100).roundToInt() / 100f
        tcpManager.sendRobotControlCommand(
            position.normalizedX,
            position.normalizedY,
            speedRatio,
            state.turnSpeed.toFloat()
        )
    }

    private suspend fun stopActiveTaskBeforeManualControl(): Boolean {
        val taskStatus = TaskSchedulerStream.taskStatusReport.value ?: return true
//        if (!taskStatus.needsStopBeforeManualControl()) return true
        val taskId = taskStatus.taskId.trim()
        if (taskId.isBlank()) return true
        if (manualReadyTaskId == taskId) return true

        TaskSchedulerStream.resetTaskCommandResponse()
        val sent = tcpManager.sendTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_STOP,
            taskId = taskId
        )
        if (!sent) {
            ToastUtils.showError(MANUAL_SWITCH_FAILED_MESSAGE)
            return false
        }

        val response = withTimeoutOrNull(STOP_TASK_RESPONSE_TIMEOUT_MS) {
            TaskSchedulerStream.taskCommandResponse
                .filterNotNull()
                .first()
        }
        val success = response?.resultValue == SlLink.ResultCode.RESULT_SUCCESS_VALUE
        if (!success) {
            ToastUtils.showError(MANUAL_SWITCH_FAILED_MESSAGE)
            return false
        }
        manualReadyTaskId = taskId
        return true
    }

    fun onCameraClick() {
        _uiState.update { current ->
            val opening = !current.showCameraOverlay
            if (!opening) {
                appState.clearVideoStreamInfo()
            }
            current.copy(
                showCameraOverlay = opening,
                videoStreamUrl = if (opening) {
                    "rtsp://${AppState.DEFAULT_TCP_HOST}:8554/left"
                } else {
                    null
                }
            )
        }
    }

    private fun isAutoMode(): Boolean {
        return appState.runtimeTaskState.value.workMode == SlLink.WorkMode.WORK_MODE_AUTO.number
    }

    private fun JoystickPosition.hasRealJoystickInput(): Boolean {
        return abs(normalizedX) > JOYSTICK_INPUT_THRESHOLD ||
            abs(normalizedY) > JOYSTICK_INPUT_THRESHOLD ||
            distanceRatio > JOYSTICK_INPUT_THRESHOLD
    }

    private fun JoystickPosition.hasSameControlPosition(other: JoystickPosition): Boolean {
        return abs(normalizedX - other.normalizedX) <= JOYSTICK_POSITION_CHANGE_THRESHOLD &&
            abs(normalizedY - other.normalizedY) <= JOYSTICK_POSITION_CHANGE_THRESHOLD
    }

    override fun onCleared() {
        requestJob?.cancel()
        manualControlJob?.cancel()
        continuousPositionJob?.cancel()
        super.onCleared()
    }

    private fun setSettingWrite(speed: Float, turningSpeed: Float, speedRm: Int) {
        val setting = SlLink.ChassisSettings.newBuilder()
        setting.setRunSpeed(speed)
        setting.setDiscEnabled(true)
        setting.setDiscSpeedRpm(speedRm)
        tcpManager.requestSettingWrite(setting.build())
    }

    private fun normalizeRunSpeed(speed: Float): Float {
        return (speed / RUN_SPEED_STEP).roundToInt()
            .times(RUN_SPEED_STEP)
            .coerceIn(MIN_RUN_SPEED, MAX_RUN_SPEED)
    }

    private fun normalizeTurnCount(value: Float): Int {
        return (value / TURN_COUNT_STEP).roundToInt()
            .times(TURN_COUNT_STEP)
            .coerceIn(MIN_TURN_COUNT, MAX_TURN_COUNT)
    }

    companion object {
        private const val MAP_SNAPSHOT_INTERVAL_MS = 30_000L
        private const val ROBOT_CONTROL_SEND_INTERVAL_MS = 200L
        private const val STOP_TASK_RESPONSE_TIMEOUT_MS = 5_000L
        private const val JOYSTICK_INPUT_THRESHOLD = 0.01f
        private const val JOYSTICK_POSITION_CHANGE_THRESHOLD = 0.0001f
        private const val MANUAL_SWITCH_FAILED_MESSAGE = "切换手动模式失败"
        private const val MIN_RUN_SPEED = 0f
        private const val MAX_RUN_SPEED = 0.15f
        private const val RUN_SPEED_STEP = 0.01f
        private const val MIN_TURN_COUNT = 0
        private const val MAX_TURN_COUNT = 1500
        private const val TURN_COUNT_STEP = 10
    }
}
