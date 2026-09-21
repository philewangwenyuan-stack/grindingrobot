package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 任务控制结果（LOWER -> APP，MSG 0x0503 TaskCommandResponse，COMP_SCHEDULER）。
 */
data class TaskCommandResponsePayload(
    val resultValue: Int,
    val message: String,
    val taskId: String
)

data class TaskConfigResponsePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val taskId: String,
    val taskName: String = ""
)

/**
 * 任务状态、进度与当前位置（LOWER -> APP，MSG 0x0504 TaskStatusReport，COMP_SCHEDULER）。
 */
data class TaskStatusReportPayload(
    val taskId: String,
    val stateValue: Int,
    val progress: Float,
    val mapVersion: Int,
    val message: String,
    val positionX: Float?,
    val positionY: Float?,
    val headingDeg: Float?,
    val replanRequested: Boolean,
    val pathPointCount: Int,
    val pathVersion: Int,
    val remainWorkArea: Float,
    val totalWorkArea: Float,
    val remainTimeS: Float,
    val currentRegionId: String,
    val currentRegionRepeatIndex: Int,
    val currentRegionRepeatTotal: Int
)

data class TaskResultRegionPayload(
    val regionId: String,
    val regionName: String,
    val targetRepeat: Int,
    val executedRepeat: Int,
    val completed: Boolean,
    val unfinishedReason: String
)

data class TaskResultResponsePayload(
    val isSuccess: Boolean,
    val message: String,
    val mapId: String,
    val taskId: String,
    val finalStateValue: Int,
    val allCompleted: Boolean,
    val stopReason: String,
    val pathVersion: Int,
    val imageFormat: String,
    //实际只需要这个字段
    val imageBytes: ByteArray?,
    val imageWidth: Int,
    val imageHeight: Int,
    val finishedAt: Long,
    val selectedWorkRegionIds: List<String>,
    val regionResults: List<TaskResultRegionPayload>
)

data class TaskExecutionHistoryRecordPayload(
    val executionId: String,
    val mapId: String,
    val taskId: String,
    val finalState: String,
    val stopReason: String,
    val startedAt: Long,
    val finishedAt: Long,
    val plannedAreaM2: Float,
    val executedAreaM2: Float,
    val progress: Float,
    val pathVersion: Int,
    val allCompleted: Boolean,
    val taskName: String = ""
)

data class TaskExecutionHistoryPayload(
    val isSuccess: Boolean,
    val message: String,
    val mapId: String = "",
    val taskId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val totalRecordCount: Int = 0,
    val records: List<TaskExecutionHistoryRecordPayload> = emptyList()
)

data class TaskExecutionDeletePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val executionId: String,
    val taskId: String,
    val mapId: String,
    val deleted: Boolean,
    val executionFilesDeleted: Boolean
)

data class TaskTrajectoryPointPayload(
    val index: Int,
    val offsetMs: Int,
    val xMeters: Float,
    val yMeters: Float,
    val headingDeg: Float,
    val linearSpeedMps: Float,
    val angularSpeedRadps: Float,
    val discSpeedRpm: Int,
    val speedAvailable: Boolean,
    val discEnabled: Boolean,
    val taskStateValue: Int
)

data class TaskTrajectoryMapPayload(
    val available: Boolean = false,
    val message: String = "",
    val version: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val resolution: Float = 0f,
    val originX: Float = 0f,
    val originY: Float = 0f,
    val originHeadingDeg: Float = 0f,
    val frameId: String = "",
    val imageFormat: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val previewScaleX: Float = 0f,
    val previewScaleY: Float = 0f,
    val imageBytes: ByteArray = byteArrayOf(),
    val alignmentYawDeg: Float = 0f,
    val appRotationDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
)

data class TaskTrajectoryPagePayload(
    val isSuccess: Boolean,
    val message: String,
    val executionId: String = "",
    val taskId: String = "",
    val mapId: String = "",
    val totalPointCount: Int = 0,
    val returnedPointCount: Int = 0,
    val startIndex: Int = 0,
    val nextIndex: Int = 0,
    val hasMore: Boolean = false,
    val startedAtMs: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val sampleStep: Int = 1,
    val points: List<TaskTrajectoryPointPayload> = emptyList(),
    val map: TaskTrajectoryMapPayload = TaskTrajectoryMapPayload()
)

/**
 * 调度器任务相关下行数据，供界面或其它模块订阅。
 */
object TaskSchedulerStream {
    private val _taskConfigResponse = MutableStateFlow<TaskConfigResponsePayload?>(null)
    val taskConfigResponse: StateFlow<TaskConfigResponsePayload?> = _taskConfigResponse.asStateFlow()

    private val _taskCommandResponse = MutableStateFlow<TaskCommandResponsePayload?>(null)
    val taskCommandResponse: StateFlow<TaskCommandResponsePayload?> = _taskCommandResponse.asStateFlow()

    private val _taskStatusReport = MutableStateFlow<TaskStatusReportPayload?>(null)
    val taskStatusReport: StateFlow<TaskStatusReportPayload?> = _taskStatusReport.asStateFlow()

    private val _taskResultResponse = MutableStateFlow<TaskResultResponsePayload?>(null)
    val taskResultResponse: StateFlow<TaskResultResponsePayload?> = _taskResultResponse.asStateFlow()

    private val _taskExecutionHistory = MutableStateFlow<TaskExecutionHistoryPayload?>(null)
    val taskExecutionHistory: StateFlow<TaskExecutionHistoryPayload?> =
        _taskExecutionHistory.asStateFlow()

    private val _taskExecutionDelete = MutableStateFlow<TaskExecutionDeletePayload?>(null)
    val taskExecutionDelete: StateFlow<TaskExecutionDeletePayload?> =
        _taskExecutionDelete.asStateFlow()

    private val _taskTrajectoryPage = MutableStateFlow<TaskTrajectoryPagePayload?>(null)
    val taskTrajectoryPage: StateFlow<TaskTrajectoryPagePayload?> =
        _taskTrajectoryPage.asStateFlow()

    fun publishTaskConfigResponse(payload: TaskConfigResponsePayload) {
        _taskConfigResponse.value = payload
    }

    fun publishTaskCommandResponse(payload: TaskCommandResponsePayload) {
        _taskCommandResponse.value = payload
    }

    fun publishTaskStatusReport(payload: TaskStatusReportPayload) {
        _taskStatusReport.value = payload
    }

    fun publishTaskResultResponse(payload: TaskResultResponsePayload) {
        _taskResultResponse.value = payload
    }

    fun publishTaskExecutionHistory(payload: TaskExecutionHistoryPayload) {
        _taskExecutionHistory.value = payload
    }

    fun publishTaskExecutionDelete(payload: TaskExecutionDeletePayload) {
        _taskExecutionDelete.value = payload
    }

    fun publishTaskTrajectoryPage(payload: TaskTrajectoryPagePayload) {
        _taskTrajectoryPage.value = payload
    }

    fun resetTaskConfigResponse() {
        _taskConfigResponse.value = null
    }

    fun resetTaskCommandResponse() {
        _taskCommandResponse.value = null
    }

    fun resetTaskResultResponse() {
        _taskResultResponse.value = null
    }

    fun resetTaskExecutionHistory() {
        _taskExecutionHistory.value = null
    }

    fun resetTaskExecutionDelete() {
        _taskExecutionDelete.value = null
    }

    fun resetTaskTrajectoryPage() {
        _taskTrajectoryPage.value = null
    }

    fun reset() {
        _taskConfigResponse.value = null
        _taskCommandResponse.value = null
        _taskStatusReport.value = null
        _taskResultResponse.value = null
        _taskExecutionHistory.value = null
        _taskExecutionDelete.value = null
        _taskTrajectoryPage.value = null
    }
}
