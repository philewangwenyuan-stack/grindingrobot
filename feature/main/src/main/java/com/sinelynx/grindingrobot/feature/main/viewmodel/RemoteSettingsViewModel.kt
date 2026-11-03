package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.manualTaskGate
import com.sinelynx.grindingrobot.core.tcp.DeviceCommandResult
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveController
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveState
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveTarget
import com.sinelynx.grindingrobot.feature.common.control.TravelDirection
import com.sinelynx.grindingrobot.feature.common.control.ManualTakeoverCoordinator
import kotlinx.coroutines.CancellationException
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
    val manualTakeoverPending: Boolean = false,
)

@HiltViewModel
class RemoteSettingsViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteSettingsUiState())
    val uiState = _uiState.asStateFlow()
    private var screenActive = false
    private var windowFocused = false
    private val controller: ManualDriveController = ManualDriveController(
        scope = viewModelScope,
        canControl = { screenActive && windowFocused && tcpManager.isControlReady() &&
            appState.runtimeTaskState.value.emergencyStopLatched != true &&
            TaskSchedulerStream.taskStatusReport.value.manualTaskGate().known },
        send = ::sendTarget,
        onStateChanged = { value -> _uiState.update { it.copy(drive = value) } },
        prepare = { takeover.canPrepareManual() },
        onPreparationFailed = {
            ToastUtils.showError("切换手动模式失败")
        },
    )
    private val takeover: ManualTakeoverCoordinator = ManualTakeoverCoordinator(
        snapshot = { TaskSchedulerStream.taskStatusReport.value.manualTaskGate() },
        stopTask = { id -> tcpManager.executeTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_STOP, taskId = id,
            timeoutMs = 5_000L) is DeviceCommandResult.Confirmed },
        stopMotion = { controller.stop() },
        changed = { value -> _uiState.update { it.copy(
            showAutoModeJoystickDialog = value.visible, manualTakeoverPending = value.pending) } },
    )

    init {
        viewModelScope.launch {
            tcpManager.manualCancellation.collect { stopControl() }
        }
        viewModelScope.launch {
            combine(tcpManager.channelStatus, appState.runtimeTaskState,
                TaskSchedulerStream.taskStatusReport) { connection, runtime, report ->
                Triple(connection, runtime, report)
            }.collect { (connection, runtime, _) ->
                takeover.observeTask()
                if (!connection.controlReady || runtime.emergencyStopLatched == true) {
                    stopControl()
                }
                controller.refreshAvailability()
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
        windowFocused = true
        controller.refreshAvailability()
    }

    fun onScreenExit() {
        screenActive = false
        windowFocused = false
        stopControl()
        controller.refreshAvailability()
    }

    fun onWindowFocusLost() {
        windowFocused = false
        if (takeover.state.visible) takeover.windowBlurred() else stopControl()
        controller.refreshAvailability()
    }

    fun onWindowFocusGained() {
        windowFocused = true
        controller.refreshAvailability()
    }

    fun acceptsManualFeedback(): Boolean = controller.state.enabled && !needsAutoConfirmation()

    fun onTravelClick(direction: TravelDirection) {
        if (!controller.state.enabled) return
        if (needsAutoConfirmation()) {
            if (!takeover.request()) ToastUtils.showError("等待有效任务状态后重试")
        } else controller.travel(direction)
    }

    fun onSteeringChanged(value: Float, epoch: Int) {
        if (epoch != controller.state.inputEpoch || !controller.state.enabled) return
        if (needsAutoConfirmation()) {
            if (value != 0f && !takeover.request()) ToastUtils.showError("等待有效任务状态后重试")
        } else controller.steer(value, epoch)
    }

    fun onSteeringCancelled(epoch: Int) {
        if (epoch == controller.state.inputEpoch) {
            if (takeover.state.visible) takeover.windowBlurred() else stopControl()
        }
    }

    fun stopControl() {
        takeover.cancel()
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
        if (!screenActive || !tcpManager.isControlReady() || appState.runtimeTaskState.value.emergencyStopLatched == true) {
            stopControl(); return
        }
        viewModelScope.launch {
            val result = try { takeover.confirm() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { takeover.cancel(); false }
            if (result == true) ToastUtils.showSuccess("已切换手动，请重新操作遥控")
            else if (result == false) ToastUtils.showError("停止自动任务失败，请重试")
            controller.refreshAvailability()
        }
    }

    fun cancelAutoModeJoystickDialog() = stopControl()

    private fun needsAutoConfirmation() =
        takeover.needsConfirmation()

    private fun sendTarget(target: ManualDriveTarget): Boolean = tcpManager.sendManualDriveCommand(
        target.remoteX, target.remoteY, target.speedRatio, target.maxSpeedMps, target.maxTurnSpeedRatio)

    override fun onCleared() {
        onScreenExit()
        super.onCleared()
    }
}
