package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload
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

enum class HomeModuleTab(val label: String) {
    DeviceHome("设备首页"),
    Map("地图"),
    Remote("遥控设置"),
    TaskRecord("任务记录"),
    JobStatistics("作业统计"),
    Robot("机器人设置")
}

data class HomeUiState(
    val selectedTab: HomeModuleTab = HomeModuleTab.DeviceHome
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
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val endpoint = appState.resolveTcpEndpoint()
            val host = endpoint.host
            val port = endpoint.port
            val ok = runCatching {
                tcpManager.connectEndpoint(endpoint, forceReconnect = false)
            }.getOrDefault(false)
            if (ok) {
                LogUtils.d("HomeViewModel", "TCP 已连接: $host:$port")
                tcpManager.requestSettingRead(readChassis = true, readMap = true)
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
        //清空任务状态的flow
        TaskSchedulerStream.resetTaskCommandResponse()
        val sent = tcpManager.sendTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_STOP,
            taskId = taskId
        )
        if (!sent) {
            ToastUtils.showError("停止任务失败请重试")
            return false
        }

        val response = withTimeoutOrNull(STOP_TASK_RESPONSE_TIMEOUT_MS) {
            TaskSchedulerStream.taskCommandResponse
                .filterNotNull()
                .first { it.taskId == taskId }
        }
        val success = response?.resultValue == SlLink.ResultCode.RESULT_SUCCESS_VALUE
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
        tcpManager.sendEmergencyStop()
        LogUtils.d("HomeViewModel", "已发送急停命令")
    }
}
