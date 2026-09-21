package com.sinelynx.grindingrobot.feature.device.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import android.graphics.BitmapFactory
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointStream
import com.sinelynx.grindingrobot.core.model.state.TaskMapDisplayStream
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import com.sinelynx.grindingrobot.core.model.state.TaskCommandResponsePayload
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sl_link.SlLink
import kotlin.math.roundToInt

/**
 * 设备页汇合两类独立状态：0x0504 的实时任务/位置数据，以及 taskMapController 的地图材料。
 * mapImageBytes/mapTransform/plannedPath 是提供给现有绘制组件的适配字段；taskMap 保留完整
 * 区域、选区、临时禁区和加载错误，不是另一套网络请求结果。
 */
data class DeviceStatusUiState(
    val taskState: DeviceTaskUiState = DeviceTaskUiState.IDLE,
    val currentTaskId: String = "",
    val mapAvailable: Boolean = false,
    val navigationStatus: String = "空闲",
    val navRadarStatus: String = "正常",
    val obstacleRadarStatus: String = "-",
    val localizationQualityAvailable: Boolean = false,
    val localizationQuality: Int = 0,
    val straightSpeedText: String = "0.0 m/s",
    val grindRpmText: String = "0 rpm",
    val totalTaskAreaText: String = "0.00m²",
    val doneTaskAreaText: String = "0.00m²",
    val remainTaskAreaText: String = "0.00m²",
    val todayAreaText: String = "-",
    val totalAreaText: String = "-",
    val remainTimeText: String = "未知",
    val mapImageBytes: ByteArray? = null,
    val mapTransform: TaskMapTransformUiState? = null,
    val plannedPath: TaskPathPayload? = null,
    val taskMap: DeviceTaskMapState = DeviceTaskMapState(),
    val reportedPathVersion: Int = 0,
    val trajectory: List<TaskPoseUiItem> = emptyList(),
    val showCameraOverlay: Boolean = false,
    val videoStreamUrl: String? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null
)

enum class DeviceTaskUiState {
    IDLE,
    READY,
    RUNNING,
    PAUSED
}

/** 一次设备上报的位置（米、度）；lineColorArgb 由当前研磨遍次决定，不是规划路径的 scope 色。 */
data class TaskPoseUiItem(
    val x: Float,
    val y: Float,
    val headingDeg: Float,
    val lineColorArgb: Int
)

/**
 * 将地图原始几何传给绘制层。width/height 是逻辑地图尺寸，不一定等于解码图片尺寸。
 * headingDeg 属于地图坐标朝向；totalRotationDeg 才是展示旋转，两者不可互相替代。
 * previewScaleX/Y 保留元数据，当前投影使用实际图片/逻辑地图尺寸比例，不再重复相乘。
 */
data class TaskMapTransformUiState(
    val width: Int,
    val height: Int,
    val resolution: Float,
    val originX: Double,
    val originY: Double,
    val headingDeg: Float,
    val previewScaleX: Float,
    val previewScaleY: Float,
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
) {
    val totalRotationDeg: Float
        get() = alignmentYawDeg + rotationAlignmentDeltaDeg
}

/**
 * 实际行驶轨迹独立于底图刷新：同任务追加位置，完整姿态/颜色重复才跳过，最多保留 maxPoints。
 * 换任务清空旧轨迹；本次没上报位置则保留已有轨迹，空闲或空任务 ID 则清空。
 */
internal fun nextTaskTrajectory(
    currentTaskId: String,
    reportTaskId: String,
    taskState: DeviceTaskUiState,
    currentTrajectory: List<TaskPoseUiItem>,
    pose: TaskPoseUiItem?,
    maxPoints: Int = 500
): List<TaskPoseUiItem> {
    if (taskState == DeviceTaskUiState.IDLE || reportTaskId.isBlank()) return emptyList()
    if (reportTaskId != currentTaskId) return pose?.let(::listOf).orEmpty()
    pose ?: return currentTrajectory
    if (currentTrajectory.lastOrNull() == pose) return currentTrajectory
    return (currentTrajectory + pose).takeLast(maxPoints)
}

internal fun DeviceStatusUiState.afterTaskStopped(): DeviceStatusUiState {
    return copy(
        taskState = DeviceTaskUiState.IDLE,
        navigationStatus = "空闲",
        plannedPath = null,
        reportedPathVersion = 0,
        trajectory = emptyList()
    )
}

/**
 * 设备页状态汇合入口。先读 taskStatusReport collector（任务、统计、轨迹），再读
 * taskMapController.state collector（底图/区域/规划线），即可看清实时数据与缓存的分工。
 * 前者只把任务身份和路径版本通知控制器；后者不覆盖机器人位置、进度或剩余时间。
 * 网络补取是否发生由控制器决策，页面可见性由 DeviceStatusScreen 生命周期回调传入。
 */
@HiltViewModel
class DeviceStatusViewModel @Inject constructor(
    private val appState: AppState,
    private val tcpManager: TcpManager
) : ViewModel() {

    private companion object {
        const val CLICK_DEBOUNCE_MS = 300L
        const val MAX_TRAJECTORY_POINTS = 500
    }

    private enum class PendingTaskCommandType {
        START,
        STOP,
        PAUSE,
        RESUME
    }

    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()
    private var lastCommandClickAtMs: Long = 0L
    private val pendingTaskCommands = mutableMapOf<String, PendingTaskCommandType>()
    // 只订阅新路径结果流，不让其他页面的历史成功路径提前结束本轮等待。
    private val taskMapController = DeviceTaskMapController(
        scope = viewModelScope, cache = TaskMapDisplayStream.cache,
        frames = MapImageStream.frames, regions = MapRegionPointStream.responses, paths = TaskPathStream.results,
        sendMap = { tcpManager.requestMapSnapshot(it) },
        sendRegions = { tcpManager.requestMapRegionPoints(it) },
        sendPath = { mapId, taskId, requestId ->
            // 状态展示不强制重规划；false 允许设备复用有效缓存，不意味着协议绝对只读。
            tcpManager.requestPathPointPlan(taskId = taskId, mapId = mapId, requestId = requestId, forceReplan = false)
        },
        decodeSize = { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                (bitmap.width to bitmap.height).also { bitmap.recycle() }
            }
        }
    )

    init {
        viewModelScope.launch {
            appState.runtimeTaskState.collect { runtime ->
                _uiState.update { current ->
                    current.copy(
                        navRadarStatus = runtime.radarSystemStatus
                            .takeIf { runtime.radarSystemStatusAvailable && it.isNotBlank() }
                            ?: "-",
                        obstacleRadarStatus = if (runtime.collisionImminent) "异常" else "正常",
                        straightSpeedText = "${runtime.vehicleSpeedMps} m/s",
                        grindRpmText = "${runtime.discSpeedRpm} rpm",
                        localizationQualityAvailable = runtime.localizationQualityAvailable,
                        localizationQuality = runtime.localizationQuality
                    )
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
            appState.robotSettings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        robotWidth = settings?.robotWidth,
                        robotLength = settings?.robotLength
                    )
                }
            }
        }

        viewModelScope.launch {
            TaskSchedulerStream.taskCommandResponse.collect { payload ->
                payload ?: return@collect
                handleTaskCommandResponse(payload)
            }
        }

        viewModelScope.launch {
            // 不受地图 loading 状态控制；收到状态上报就更新，实际频率由嵌入式决定。
            TaskSchedulerStream.taskStatusReport.collect { payload ->
                payload ?: return@collect
                val reportTaskId = payload.taskId
                val taskState = payload.toTaskUiState()
                _uiState.update { current ->
                    val doneTaskArea = payload.totalWorkArea - payload.remainWorkArea
                    val taskChanged = reportTaskId.isNotBlank() && reportTaskId != current.currentTaskId
                    val nextTrajectory = nextTaskTrajectory(
                        currentTaskId = current.currentTaskId,
                        reportTaskId = reportTaskId,
                        taskState = taskState,
                        currentTrajectory = current.trajectory,
                        pose = payload.toPoseItem(),
                        maxPoints = MAX_TRAJECTORY_POINTS
                    )
                    current.copy(
                        taskState = taskState,
                        currentTaskId = payload.taskId,
                        navigationStatus = if (taskState == DeviceTaskUiState.IDLE) "空闲" else "研磨中",
                        totalTaskAreaText = formatAreaM2(payload.totalWorkArea),
                        doneTaskAreaText = formatAreaM2(doneTaskArea),
                        remainTaskAreaText = formatAreaM2(payload.remainWorkArea),
                        remainTimeText = formatRemainTimeS(payload.remainTimeS),
                        mapImageBytes = if (taskChanged) null else current.mapImageBytes,
                        mapTransform = if (taskChanged) null else current.mapTransform,
                        mapAvailable = if (taskChanged) false else current.mapAvailable,
                        plannedPath = when {
                            taskState == DeviceTaskUiState.IDLE -> null
                            taskChanged -> null
                            else -> current.plannedPath
                        },
                        reportedPathVersion = if (taskState == DeviceTaskUiState.IDLE) 0 else payload.pathVersion,
                        trajectory = nextTrajectory
                    )
                }
                // 高频位置上报会多次到达这里，但相同任务/路径版本不会因此反复取图。
                taskMapController.onTask(
                    taskId = reportTaskId.takeIf { taskState != DeviceTaskUiState.IDLE },
                    pathVersion = payload.pathVersion
                )
            }
        }

        viewModelScope.launch {
            // 地图加载结果只更新显示材料。规划阶段面积/耗时不能覆盖上方实时任务统计。
            taskMapController.state.collect { map ->
                _uiState.update { current ->
                    val snapshot = map.snapshot
                    if (snapshot != null && snapshot.taskId != current.currentTaskId) return@update current
                    current.copy(
                        taskMap = map, mapImageBytes = snapshot?.frame?.imageBytes,
                        mapTransform = snapshot?.frame?.toTaskMapTransform(),
                        mapAvailable = snapshot?.frame != null && snapshot.bitmapSize != null,
                        plannedPath = snapshot?.path
                    )
                }
            }
        }
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

    fun onPauseTaskClick() {
        if (!canSendTaskCommand()) return
        val currentlyPaused = _uiState.value.taskState == DeviceTaskUiState.PAUSED
        val taskId = currentTaskIdOrNull() ?: run {
            ToastUtils.showError("当前任务ID为空")
            return
        }
        val commandType = if (currentlyPaused) {
            PendingTaskCommandType.RESUME
        } else {
            PendingTaskCommandType.PAUSE
        }
        val sent = tcpManager.sendTaskCommand(
            taskType = if (currentlyPaused) SlLink.TaskCommandType.TASK_CMD_RESUME else SlLink.TaskCommandType.TASK_CMD_PAUSE,
            taskId = taskId
        )
        if (sent) {
            pendingTaskCommands[taskId] = commandType
        }
    }

    fun onStartTaskClick() {
        if (!canSendTaskCommand()) return
        val taskId = currentTaskIdOrNull() ?: run {
            ToastUtils.showError("当前任务ID为空")
            return
        }
        val sent = tcpManager.sendTaskCommand(
            taskType = SlLink.TaskCommandType.TASK_CMD_START,
            taskId = taskId
        )
        if (sent) {
            pendingTaskCommands[taskId] = PendingTaskCommandType.START
        } else {
            ToastUtils.showError("开始任务失败")
        }
    }

    fun onStopTaskClick() {
        if (!canSendTaskCommand()) return
        val taskId = currentTaskIdOrNull() ?: run {
            ToastUtils.showError("当前任务ID为空")
            return
        }
        val sent = tcpManager.sendTaskCommand(SlLink.TaskCommandType.TASK_CMD_STOP, taskId = taskId)
        if (sent) {
            pendingTaskCommands[taskId] = PendingTaskCommandType.STOP
        } else {
            ToastUtils.showError("结束任务失败")
        }
    }

    private fun currentTaskIdOrNull(): String? {
        return _uiState.value.currentTaskId.takeIf { it.isNotBlank() }
    }

    /** 仅控制地图补取/等待，不开关任务状态上报 collector。 */
    fun setMapVisible(visible: Boolean) = taskMapController.setVisible(visible)

    /** 重试失败材料，不重置实时轨迹，不触发任何任务执行指令。 */
    fun retryTaskMap() = taskMapController.retry()

    override fun onCleared() {
        taskMapController.close()
        super.onCleared()
    }

    private fun canSendTaskCommand(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCommandClickAtMs < CLICK_DEBOUNCE_MS) return false
        lastCommandClickAtMs = now
        return true
    }

    private fun handleTaskCommandResponse(payload: TaskCommandResponsePayload) {
        val commandType = pendingTaskCommands.remove(payload.taskId) ?: return
        val success = payload.resultValue == SlLink.ResultCode.RESULT_SUCCESS_VALUE
        if (!success) {
            when (commandType) {
                PendingTaskCommandType.START -> ToastUtils.showError("开始任务失败")
                PendingTaskCommandType.STOP -> ToastUtils.showError("结束任务失败")
                else -> Unit
            }
            return
        }
        when (commandType) {
            PendingTaskCommandType.START -> {
                _uiState.update {
                    it.copy(
                        taskState = DeviceTaskUiState.RUNNING,
                        navigationStatus = "研磨中"
                    )
                }
            }
            PendingTaskCommandType.STOP -> {
                taskMapController.onTask(null, 0)
                TaskPathStream.reset(payload.taskId)
                _uiState.update {
                    it.afterTaskStopped()
                }
            }
            PendingTaskCommandType.PAUSE -> {
                _uiState.update {
                    it.copy(
                        taskState = DeviceTaskUiState.PAUSED,
                        navigationStatus = "研磨中"
                    )
                }
            }
            PendingTaskCommandType.RESUME -> {
                _uiState.update {
                    it.copy(
                        taskState = DeviceTaskUiState.RUNNING,
                        navigationStatus = "研磨中"
                    )
                }
            }
        }
    }

    private fun formatAreaM2(value: Float): String {
        return "%.2fm²".format(value.coerceAtLeast(0f))
    }

    private fun formatRemainTimeS(value: Float): String {
        return "${value.coerceAtLeast(0f).roundToInt()}秒"
    }

    private fun com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload.toPoseItem(): TaskPoseUiItem? {
        val x = positionX ?: return null
        val y = positionY ?: return null
        return TaskPoseUiItem(
            x = x,
            y = y,
            headingDeg = headingDeg ?: 0f,
            lineColorArgb = currentRegionRepeatIndex.toTrajectoryLineColorArgb()
        )
    }

    private fun Int.toTrajectoryLineColorArgb(): Int {
        return when (this) {
            in Int.MIN_VALUE..1 -> 0xFFFAE051.toInt()
            2 -> 0xFF54DD83.toInt()
            3 -> 0xFF41D3BF.toInt()
            4 -> 0xFF4881F4.toInt()
            5 -> 0xFF8A5AEF.toInt()
            6 -> 0xFFA755F4.toInt()
            else -> 0xFFE34544.toInt()
        }
    }

    private fun com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload.toTaskUiState(): DeviceTaskUiState {
        return when (stateValue) {
            SlLink.TaskState.TASK_STATE_READY_VALUE -> DeviceTaskUiState.READY
            SlLink.TaskState.TASK_STATE_PLANNING_VALUE,
            SlLink.TaskState.TASK_STATE_RUNNING_VALUE -> DeviceTaskUiState.RUNNING
            SlLink.TaskState.TASK_STATE_PAUSED_VALUE -> DeviceTaskUiState.PAUSED
            else -> DeviceTaskUiState.IDLE
        }
    }

    private fun MapImagePayload.toTaskMapTransform(): TaskMapTransformUiState? {
        if (!hasDisplayGeometry()) return null
        return TaskMapTransformUiState(
            width = mapWidth,
            height = mapHeight,
            resolution = resolution,
            originX = originX,
            originY = originY,
            headingDeg = headingDeg,
            previewScaleX = previewScaleX,
            previewScaleY = previewScaleY,
            alignmentYawDeg = alignmentYawDeg,
            rotationAlignmentDeltaDeg = rotationAlignmentDeltaDeg
        )
    }

}
