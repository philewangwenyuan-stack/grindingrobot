package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.tcp.ConnectionState
import com.sinelynx.grindingrobot.core.tcp.DeviceCommandResult
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveController
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveState
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveTarget
import com.sinelynx.grindingrobot.feature.common.control.TravelDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sl_link.SlLink
import kotlin.math.roundToInt

data class RemoteSettingsUiState(
    val drive: ManualDriveState = ManualDriveState(),
    val turnCount: Int = 800,
    val showAutoModeJoystickDialog: Boolean = false,
)

@HiltViewModel
class RemoteSettingsViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteSettingsUiState())
    val uiState = _uiState.asStateFlow()
    private var screenActive = false
    private var pendingTravel: TravelDirection? = null
    private var pendingSteering = 0f
    private var manualOverrideGranted = false
    private var manualReadyTaskId: String? = null
    private var lastWorkMode: Int? = null
    private val controller = ManualDriveController(
        scope = viewModelScope,
        canControl = { screenActive && tcpManager.isConnected() &&
            appState.runtimeTaskState.value.emergencyStopLatched != true },
        send = ::sendTarget,
        onStateChanged = { value -> _uiState.update { it.copy(drive = value) } },
        prepare = ::stopActiveTaskBeforeManualControl,
        onPreparationFailed = {
            manualOverrideGranted = false
            ToastUtils.showError("切换手动模式失败")
        },
    )

    init {
        viewModelScope.launch {
            combine(tcpManager.getConnectionStateFlow(), appState.runtimeTaskState) { connection, runtime ->
                connection to runtime
            }.collect { (connection, runtime) ->
                val enteringAuto = runtime.workMode == SlLink.WorkMode.WORK_MODE_AUTO.number &&
                    lastWorkMode != runtime.workMode
                lastWorkMode = runtime.workMode
                if (enteringAuto || connection != ConnectionState.CONNECTED || runtime.emergencyStopLatched == true) {
                    stopControl()
                    manualOverrideGranted = false
                    manualReadyTaskId = null
                }
                controller.refreshAvailability()
            }
        }
        viewModelScope.launch {
            TaskSchedulerStream.taskStatusReport.collect { report ->
                val id = report?.taskId?.trim().orEmpty()
                if (id.isNotBlank() && manualReadyTaskId != null && manualReadyTaskId != id) {
                    stopControl()
                    manualOverrideGranted = false
                }
                if (id.isBlank() || (manualReadyTaskId != null && manualReadyTaskId != id)) {
                    manualReadyTaskId = null
                }
            }
        }
        viewModelScope.launch {
            appState.chassisSettings.collect { settings ->
                settings ?: return@collect
                _uiState.update { it.copy(turnCount = settings.discSpeedRpm.coerceIn(0, 1500)) }
            }
        }
    }

    fun onScreenEnter() {
        if (!screenActive) tcpManager.requestSettingRead(true, null)
        screenActive = true
        controller.refreshAvailability()
    }

    fun onScreenExit() {
        screenActive = false
        stopControl()
        manualOverrideGranted = false
        controller.refreshAvailability()
    }

    fun acceptsManualFeedback(): Boolean = controller.state.enabled && !needsAutoConfirmation()

    fun onTravelClick(direction: TravelDirection) {
        if (!controller.state.enabled) return
        if (needsAutoConfirmation()) {
            pendingTravel = direction
            _uiState.update { it.copy(showAutoModeJoystickDialog = true) }
        } else controller.travel(direction)
    }

    fun onSteeringChanged(value: Float, epoch: Int) {
        if (epoch != controller.state.inputEpoch || !controller.state.enabled) return
        if (needsAutoConfirmation()) {
            pendingSteering = value.coerceIn(-1f, 1f)
            if (value != 0f) _uiState.update { it.copy(showAutoModeJoystickDialog = true) }
        } else controller.steer(value, epoch)
    }

    fun onSteeringCancelled(epoch: Int) {
        if (epoch == controller.state.inputEpoch) stopControl()
    }

    fun stopControl() {
        pendingTravel = null
        pendingSteering = 0f
        _uiState.update { it.copy(showAutoModeJoystickDialog = false) }
        controller.stop()
    }

    fun updateRunSpeed(value: Float) = controller.setSpeed(value)
    fun updateTurnSpeed(value: Float) = controller.setTurnPercent(value)
    fun updateTurnCountDraft(value: Float) {
        if (value.isFinite()) _uiState.update {
            it.copy(turnCount = ((value / 10).roundToInt() * 10).coerceIn(0, 1500))
        }
    }

    fun commitTurnCount() {
        // Preserve the existing enable rule, including at zero RPM, and the read task settings.
        if (!tcpManager.requestDiscSettingsWrite(_uiState.value.turnCount, enabled = true)) {
            ToastUtils.showError("磨盘设置未发送，请等待底盘参数读取完成后重试")
        }
    }

    fun confirmAutoModeJoystickDialog() {
        val direction = pendingTravel
        val steering = pendingSteering
        pendingTravel = null
        pendingSteering = 0f
        _uiState.update { it.copy(showAutoModeJoystickDialog = false) }
        if (!screenActive || !tcpManager.isConnected() || appState.runtimeTaskState.value.emergencyStopLatched == true) return
        manualOverrideGranted = true
        direction?.let(controller::travel)
        if (steering != 0f) controller.steer(steering, controller.state.inputEpoch)
    }

    fun cancelAutoModeJoystickDialog() = stopControl()

    private fun needsAutoConfirmation() =
        appState.runtimeTaskState.value.workMode == SlLink.WorkMode.WORK_MODE_AUTO.number && !manualOverrideGranted

    private fun sendTarget(target: ManualDriveTarget): Boolean = tcpManager.sendManualDriveCommand(
        target.remoteX, target.remoteY, target.speedRatio, target.maxSpeedMps, target.maxTurnSpeedRatio)

    private suspend fun stopActiveTaskBeforeManualControl(): Boolean {
        val id = TaskSchedulerStream.taskStatusReport.value?.taskId?.trim().orEmpty()
        if (id.isBlank()) return appState.runtimeTaskState.value.workMode != SlLink.WorkMode.WORK_MODE_AUTO.number
        if (manualReadyTaskId == id) return true
        val result = tcpManager.executeTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_STOP, taskId = id, timeoutMs = 5_000L)
        if (result !is DeviceCommandResult.Confirmed) return false
        manualReadyTaskId = id
        return true
    }

    override fun onCleared() {
        onScreenExit()
        super.onCleared()
    }
}
