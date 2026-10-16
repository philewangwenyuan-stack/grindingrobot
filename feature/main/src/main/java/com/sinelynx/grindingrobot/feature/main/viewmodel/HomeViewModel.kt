package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.tcp.DeviceCommandResult
import com.sinelynx.grindingrobot.core.tcp.ConnectionState
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sl_link.SlLink

enum class HomeModuleTab(val label: String) {
    DeviceHome("设备首页"),
    Map("地图"),
    Remote("遥控设置"),
    TaskRecord("任务记录"),
    JobStatistics("作业统计"),
    Robot("机器人设置")
}

data class HomeUiState(
    val selectedTab: HomeModuleTab = HomeModuleTab.DeviceHome,
    val emergencyStopFeedback: String? = null,
    val emergencyStopPending: Boolean = false,
    val emergencyStopLatched: Boolean? = null,
    val emergencyRelease: EmergencyReleaseUiState = EmergencyReleaseUiState()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState
) : ViewModel() {

    private companion object {
        const val STOP_TASK_RESPONSE_TIMEOUT_MS = 5_000L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    private var emergencyStopPending = false
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val emergencyRelease = EmergencyReleaseController(
        scope = viewModelScope,
        execute = { tcpManager.executeEmergencyRelease() },
        publish = { release ->
            _uiState.update { it.copy(
                emergencyRelease = release,
                emergencyStopLatched = if (release.released) false else it.emergencyStopLatched,
                emergencyStopFeedback = release.feedback ?: it.emergencyStopFeedback
            ) }
        }
    )

    init {
        viewModelScope.launch {
            appState.runtimeTaskState.collect { runtime ->
                runtime.emergencyStopLatched?.let { latched ->
                    _uiState.update { current -> current.copy(
                        emergencyStopLatched = latched,
                        emergencyStopFeedback = if (current.emergencyStopLatched != latched && !current.emergencyStopPending && !current.emergencyRelease.pending)
                            (if (latched) "已急停" else "状态正常") else current.emergencyStopFeedback
                    ) }
                }
            }
        }
        viewModelScope.launch {
            tcpManager.getConnectionStateFlow().collect { connection ->
                if (connection != ConnectionState.CONNECTED) {
                    _uiState.update { it.copy(emergencyStopLatched = null, emergencyStopFeedback = "连接中断，状态未知") }
                } else {
                    _uiState.update { if (it.emergencyStopLatched == null) it.copy(emergencyStopFeedback = "急停状态未上报") else it }
                }
            }
        }
        viewModelScope.launch {
            val endpoint = appState.resolveTcpEndpoint()
            val host = endpoint.host
            val port = endpoint.port
            val ok = runCatching {
                tcpManager.connectEndpoint(endpoint, forceReconnect = false)
            }.getOrDefault(false)
            if (ok) {
                LogUtils.d("HomeViewModel", "TCP 已连接: $host:$port")
            } else {
                LogUtils.w("HomeViewModel", "TCP 连接失败: $host:$port")
            }
        }
    }

    fun onTabSelected(tab: HomeModuleTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun disconnectTcp() {
        tcpManager.disconnect()
        LogUtils.d("HomeViewModel", "TCP 已主动断开")
    }

    fun disconnectTcpAfterStoppingTaskIfNeeded(onDisconnected: () -> Unit) {
        viewModelScope.launch {
            if (!stopActiveTaskSuccess()) return@launch
            disconnectTcp()
            onDisconnected()
        }
    }

    private suspend fun stopActiveTaskSuccess(): Boolean {
        //当任务状态为空时，认为没有任务正在运行，即停止成功
        val taskStatus = TaskSchedulerStream.taskStatusReport.value ?: return true
        //当任务状态不需要停止（非规划中、运行中、暂停中）时，认为停止成功
        if (!taskStatus.needsStopBeforeDisconnect()) return true

        val taskId = taskStatus.taskId.trim()
        //当任务id为空时，代表没有任务正在运行，即停止成功
        if (taskId.isBlank()) {
            return true
        }
        val result = tcpManager.executeTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_STOP,
            taskId = taskId,
            timeoutMs = STOP_TASK_RESPONSE_TIMEOUT_MS
        )
        val success = result is DeviceCommandResult.Confirmed
        if (!success) {
            ToastUtils.showError("停止任务失败请重试")
        }
        return success
    }

    private fun TaskStatusReportPayload.needsStopBeforeDisconnect(): Boolean {
        return stateValue == SlLink.TaskState.TASK_STATE_PLANNING_VALUE ||
            stateValue == SlLink.TaskState.TASK_STATE_RUNNING_VALUE ||
            stateValue == SlLink.TaskState.TASK_STATE_PAUSED_VALUE
    }

    fun setEmergencyStop() {
        if (emergencyStopPending) return
        emergencyRelease.cancelForEmergencyStop()
        emergencyStopPending = true
        _uiState.update { it.copy(emergencyStopPending = true, emergencyStopFeedback = "等待设备确认") }
        viewModelScope.launch {
            try {
                ToastUtils.show("急停：等待设备确认")
                when (val result = tcpManager.executeEmergencyStop()) {
                    is DeviceCommandResult.Confirmed -> {
                        _uiState.update { it.copy(emergencyStopLatched = true, emergencyStopFeedback = "急停已确认") }
                        ToastUtils.showSuccess("急停指令已确认")
                    }
                    is DeviceCommandResult.Rejected -> {
                        _uiState.update { it.copy(emergencyStopFeedback = "指令被拒绝") }
                        ToastUtils.showError("急停失败：${result.message}")
                    }
                    DeviceCommandResult.SendFailed -> {
                        _uiState.update { it.copy(emergencyStopFeedback = "发送失败") }
                        ToastUtils.showError("急停发送失败，请检查连接")
                    }
                    DeviceCommandResult.TimedOut -> {
                        _uiState.update { it.copy(emergencyStopFeedback = "未收到确认") }
                        ToastUtils.showError("急停未收到确认，请核对设备状态")
                    }
                }
            } finally {
                emergencyStopPending = false
                _uiState.update { it.copy(emergencyStopPending = false) }
            }
        }
    }

    fun releaseEmergencyStop() {
        if (!emergencyStopPending) emergencyRelease.request()
    }

    fun dismissEmergencyReleaseResult() {
        _uiState.update { it.copy(emergencyRelease = it.emergencyRelease.copy(title = null, message = null)) }
    }
}
