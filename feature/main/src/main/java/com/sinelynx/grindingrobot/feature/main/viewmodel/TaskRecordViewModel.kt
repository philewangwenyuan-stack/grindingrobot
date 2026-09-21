package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.common.base.viewmodel.BaseViewModel
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.database.dao.MapDao
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionDeletePayload
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.navigation.AppNavigator
import com.sinelynx.grindingrobot.navigation.routes.MapRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrajectoryPoint(
    val index: Int,
    val offsetMs: Int,
    val x: Float,
    val y: Float,
    val headingDeg: Float,
    val speed: Float,
    val angularSpeedRadps: Float,
    val discSpeedRpm: Int,
    val speedAvailable: Boolean,
    val discEnabled: Boolean,
    val taskStateValue: Int
)

data class TaskRecordItem(
    val executionId: String,
    val taskId: String,
    val taskName: String,
    val mapId: String,
    val mapName: String,
    val startTime: Long,
    val area: Float,
    val base64Image: String = "",
    val regionRepeats: Map<String, Int> = emptyMap()
)

internal fun TaskExecutionHistoryRecordPayload.toTaskRecordItem(mapNames: Map<String, String>) =
    TaskRecordItem(
        executionId = executionId,
        taskId = taskId,
        taskName = taskName.trim().ifBlank { "未命名任务" },
        mapId = mapId,
        mapName = mapNames[mapId] ?: mapId.ifBlank { "未知地图" },
        startTime = startedAt.coerceIn(Long.MIN_VALUE / 1000L, Long.MAX_VALUE / 1000L) * 1000L,
        area = executedAreaM2
    )

data class TaskRecordUiState(
    val records: List<TaskRecordItem> = emptyList(),
    val isLoading: Boolean = false,
    val deletingExecutionId: String? = null
)

@HiltViewModel
class TaskRecordViewModel @Inject constructor(
    private val mapDao: MapDao,
    private val tcpManager: TcpManager,
    navigator: AppNavigator,
    appState: AppState
) : BaseViewModel(navigator, appState) {

    private val _uiState = MutableStateFlow(TaskRecordUiState())
    val uiState: StateFlow<TaskRecordUiState> = _uiState.asStateFlow()
    private var loadTimeoutJob: Job? = null
    private var deleteTimeoutJob: Job? = null

    init {
        observeTaskExecutionHistory()
        observeTaskExecutionDelete()
    }

    fun loadRecords() {
        if (_uiState.value.isLoading) return
        if (!TaskHistoryRequestGate.acquire(this)) {
            ToastUtils.showError("任务历史正在查询，请稍后重试")
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        TaskSchedulerStream.resetTaskExecutionHistory()
        if (!tcpManager.requestTaskExecutionHistory()) {
            loadTimeoutJob?.cancel()
            _uiState.update { it.copy(isLoading = false) }
            TaskHistoryRequestGate.release(this)
            ToastUtils.showError("任务记录请求发送失败")
            return
        }
        loadTimeoutJob?.cancel()
        loadTimeoutJob = viewModelScope.launch {
            delay(TASK_HISTORY_TIMEOUT_MS)
            if (_uiState.value.isLoading) {
                _uiState.update { it.copy(isLoading = false) }
                TaskHistoryRequestGate.release(this)
                ToastUtils.showError("任务记录请求超时")
            }
        }
    }

    private fun observeTaskExecutionHistory() {
        viewModelScope.launch {
            TaskSchedulerStream.taskExecutionHistory
                .filterNotNull()
                .collectLatest(::applyTaskExecutionHistory)
        }
    }

    fun openReplay(record: TaskRecordItem) {
        navigate(
            MapRoutes.TaskReplay(
                executionId = record.executionId,
                taskId = record.taskId
            )
        )
    }

    fun deleteRecord(record: TaskRecordItem) {
        val executionId = record.executionId
        if (executionId.isBlank()) {
            ToastUtils.showError("任务执行标识为空，无法删除")
            return
        }
        if (_uiState.value.deletingExecutionId != null) return

        _uiState.update { it.copy(deletingExecutionId = executionId) }
        TaskSchedulerStream.resetTaskExecutionDelete()
        if (!tcpManager.requestTaskExecutionDelete(executionId)) {
            finishDeleteWithError("任务记录删除请求发送失败")
            return
        }
        deleteTimeoutJob?.cancel()
        deleteTimeoutJob = viewModelScope.launch {
            delay(TASK_DELETE_TIMEOUT_MS)
            if (_uiState.value.deletingExecutionId == executionId) {
                finishDeleteWithError("任务记录删除请求超时")
            }
        }
    }

    private fun observeTaskExecutionDelete() {
        viewModelScope.launch {
            TaskSchedulerStream.taskExecutionDelete
                .filterNotNull()
                .collectLatest(::applyTaskExecutionDelete)
        }
    }

    private fun applyTaskExecutionDelete(payload: TaskExecutionDeletePayload) {
        val pendingExecutionId = _uiState.value.deletingExecutionId ?: return
        if (payload.executionId.isNotBlank() && payload.executionId != pendingExecutionId) return

        deleteTimeoutJob?.cancel()
        if (payload.isSuccess) {
            _uiState.update { current ->
                current.copy(
                    records = current.records.filterNot { it.executionId == pendingExecutionId },
                    deletingExecutionId = null
                )
            }
            ToastUtils.showSuccess(payload.message.ifBlank { "任务记录已删除" })
            return
        }

        val message = if (payload.resultValue == RESULT_BUSY_VALUE) {
            "当前任务正在执行，无法删除"
        } else {
            payload.message.ifBlank { "任务记录删除失败" }
        }
        finishDeleteWithError(message)
    }

    private fun finishDeleteWithError(message: String) {
        deleteTimeoutJob?.cancel()
        _uiState.update { it.copy(deletingExecutionId = null) }
        ToastUtils.showError(message)
    }

    private suspend fun applyTaskExecutionHistory(payload: TaskExecutionHistoryPayload) {
        if (!_uiState.value.isLoading) return
        if (payload.mapId.isNotBlank() || payload.taskId.isNotBlank() ||
            payload.startTime != 0L || payload.endTime != 0L) return
        TaskHistoryRequestGate.release(this)
        loadTimeoutJob?.cancel()
        if (!payload.isSuccess) {
            _uiState.update { it.copy(isLoading = false) }
            ToastUtils.showError(payload.message.ifBlank { "任务记录加载失败" })
            return
        }

        val mapNames = payload.records
            .map { it.mapId }
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { mapId ->
                mapDao.getMapByMapId(mapId)?.mapName?.takeIf { it.isNotBlank() } ?: mapId
            }
        val records = payload.records.map { record ->
            record.toTaskRecordItem(mapNames)
        }
        _uiState.update { current ->
            current.copy(records = records, isLoading = false)
        }
    }

    override fun onCleared() {
        TaskHistoryRequestGate.release(this)
        super.onCleared()
    }

    private companion object {
        const val TASK_HISTORY_TIMEOUT_MS = 180_000L
        const val TASK_DELETE_TIMEOUT_MS = 180_000L
        const val RESULT_BUSY_VALUE = 3
    }
}
