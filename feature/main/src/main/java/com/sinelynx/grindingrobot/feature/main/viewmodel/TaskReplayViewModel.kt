package com.sinelynx.grindingrobot.feature.main.viewmodel

import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.common.base.viewmodel.BaseViewModel
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPagePayload
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

internal const val TASK_REPLAY_PATH_TIMEOUT_MS = 180_000L

data class TaskReplayUiState(
    val executionId: String = "",
    val taskId: String = "",
    val mapId: String = "",
    val points: List<TrajectoryPoint> = emptyList(),
    val plannedPath: TaskPathPayload? = null,
    val mapFrame: MapImagePayload? = null,
    val bitmapSize: Pair<Int, Int>? = null,
    val robotWidth: Double? = null,
    val robotLength: Double? = null,
    val totalPointCount: Int = 0,
    val startedAtMs: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isMapLoading: Boolean = false,
    val mapError: String? = null,
    val isPathLoading: Boolean = false,
    val pathError: String? = null,
    val isRobotSettingsLoading: Boolean = false,
    val robotSettingsError: String? = null
)

internal fun mergeTrajectoryPoints(
    current: List<TrajectoryPoint>,
    page: TaskTrajectoryPagePayload
): List<TrajectoryPoint> = (current + page.points.map { point ->
    TrajectoryPoint(
        index = point.index,
        offsetMs = point.offsetMs,
        x = point.xMeters,
        y = point.yMeters,
        headingDeg = point.headingDeg,
        speed = point.linearSpeedMps,
        angularSpeedRadps = point.angularSpeedRadps,
        discSpeedRpm = point.discSpeedRpm,
        speedAvailable = point.speedAvailable,
        discEnabled = point.discEnabled,
        taskStateValue = point.taskStateValue
    )
}).associateBy { it.index }.toSortedMap().values.toList()

internal fun validatedNextTrajectoryIndex(page: TaskTrajectoryPagePayload): Int? {
    if (!page.hasMore) return null
    return page.nextIndex.takeIf { it > page.startIndex }
}

internal fun replayPlannedPathError(sent: Boolean, path: TaskPathPayload?): String? = when {
    !sent -> "规划路径请求发送失败"
    path == null -> "规划路径加载超时"
    !path.hasRenderablePoints -> path.message.ifBlank { "规划路径未返回数据" }
    else -> null
}

internal fun TaskPathPayload.matchesReplayPathRequest(requestId: String): Boolean =
    this.requestId == requestId

@HiltViewModel
class TaskReplayViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    navigator: AppNavigator,
    private val replayAppState: AppState
) : BaseViewModel(navigator, replayAppState) {
    private val _uiState = MutableStateFlow(TaskReplayUiState())
    val uiState: StateFlow<TaskReplayUiState> = _uiState.asStateFlow()

    private var pageTimeoutJob: Job? = null
    private var mapTimeoutJob: Job? = null
    private var settingsTimeoutJob: Job? = null
    private var pathRequestJob: Job? = null
    private var pendingStartIndex = 0
    private var loadedKey: Pair<String, String>? = null
    private var requestedMapId: String? = null
    private var requestedPathKey: Pair<String, String>? = null
    private var mapRequestActive = false

    init {
        viewModelScope.launch {
            TaskSchedulerStream.taskTrajectoryPage
                .filterNotNull()
                .collect(::applyTrajectoryPage)
        }
        viewModelScope.launch {
            MapImageStream.frames.collect(::applyMapFrame)
        }
        viewModelScope.launch {
            replayAppState.robotSettings.collect { settings ->
                val width = settings?.robotWidth?.takeIf { it.isFinite() && it > 0.0 }
                val length = settings?.robotLength?.takeIf { it.isFinite() && it > 0.0 }
                if (width != null && length != null) {
                    settingsTimeoutJob?.cancel()
                    _uiState.update {
                        it.copy(
                            robotWidth = width,
                            robotLength = length,
                            isRobotSettingsLoading = false,
                            robotSettingsError = null
                        )
                    }
                }
            }
        }
    }

    fun load(executionId: String, taskId: String) {
        val normalizedExecutionId = executionId.trim()
        val normalizedTaskId = taskId.trim()
        if (normalizedExecutionId.isEmpty() && normalizedTaskId.isEmpty()) {
            failTrajectory("任务执行标识为空")
            return
        }
        val key = normalizedExecutionId to normalizedTaskId
        if (loadedKey == key && _uiState.value.isLoading) return
        loadedKey = key
        pendingStartIndex = 0
        pageTimeoutJob?.cancel()
        mapTimeoutJob?.cancel()
        settingsTimeoutJob?.cancel()
        pathRequestJob?.cancel()
        requestedMapId = null
        requestedPathKey = null
        mapRequestActive = false
        val settings = replayAppState.robotSettings.value
        val robotWidth = settings?.robotWidth?.takeIf { it.isFinite() && it > 0.0 }
        val robotLength = settings?.robotLength?.takeIf { it.isFinite() && it > 0.0 }
        _uiState.value = TaskReplayUiState(
            executionId = normalizedExecutionId,
            taskId = normalizedTaskId,
            robotWidth = robotWidth,
            robotLength = robotLength,
            isLoading = true,
            isMapLoading = true,
            isPathLoading = true,
            isRobotSettingsLoading = robotWidth == null || robotLength == null
        )
        requestRobotSettingsIfNeeded(robotWidth == null || robotLength == null)
        requestPage(0)
    }

    private fun requestPage(startIndex: Int) {
        pendingStartIndex = startIndex
        TaskSchedulerStream.resetTaskTrajectoryPage()
        val sent = tcpManager.requestTaskTrajectory(
            executionId = _uiState.value.executionId.takeIf { it.isNotBlank() },
            taskId = _uiState.value.taskId.takeIf { it.isNotBlank() },
            startIndex = startIndex,
            maxPoints = MAX_POINTS,
            sampleStep = SAMPLE_STEP,
            maxChunkSize = MAX_CHUNK_SIZE
        )
        if (!sent) {
            failTrajectory("任务轨迹请求发送失败")
            return
        }
        pageTimeoutJob?.cancel()
        pageTimeoutJob = viewModelScope.launch {
            delay(PAGE_TIMEOUT_MS)
            failTrajectory("任务轨迹请求超时")
        }
    }

    private fun applyTrajectoryPage(page: TaskTrajectoryPagePayload) {
        val current = _uiState.value
        if (current.executionId.isNotBlank() &&
            page.executionId.isNotBlank() &&
            page.executionId != current.executionId
        ) return
        if (current.executionId.isBlank() && current.taskId.isNotBlank() &&
            page.taskId.isNotBlank() && page.taskId != current.taskId
        ) return
        if (page.startIndex != pendingStartIndex) return

        pageTimeoutJob?.cancel()
        if (!page.isSuccess) {
            failTrajectory(page.message.ifBlank { "任务轨迹加载失败" })
            return
        }

        val mergedPoints = mergeTrajectoryPoints(current.points, page)
        val resolvedTaskId = page.taskId.ifBlank { current.taskId }
        val resolvedMapId = page.mapId.ifBlank { current.mapId }
        _uiState.update {
            it.copy(
                executionId = page.executionId.ifBlank { it.executionId },
                taskId = resolvedTaskId,
                mapId = resolvedMapId,
                points = mergedPoints,
                totalPointCount = page.totalPointCount,
                startedAtMs = page.startedAtMs,
                isLoading = page.hasMore,
                errorMessage = null
            )
        }
        requestMapIfNeeded(resolvedMapId)
        requestPlannedPathIfNeeded(resolvedTaskId, resolvedMapId)

        val nextIndex = validatedNextTrajectoryIndex(page)
        if (!page.hasMore) {
            if (resolvedMapId.isBlank()) {
                _uiState.update {
                    it.copy(isMapLoading = false, mapError = "任务轨迹未返回地图标识")
                }
            }
            if (requestedPathKey == null) {
                val message = when {
                    resolvedTaskId.isBlank() -> "任务轨迹未返回任务标识，无法加载规划路径"
                    resolvedMapId.isBlank() -> "任务轨迹未返回地图标识，无法加载规划路径"
                    else -> null
                }
                if (message != null) {
                    _uiState.update { it.copy(isPathLoading = false, pathError = message) }
                }
            }
            return
        }
        if (nextIndex == null) {
            failTrajectory("任务轨迹分页索引无进展")
            return
        }
        requestPage(nextIndex)
    }

    private fun requestPlannedPathIfNeeded(taskId: String, mapId: String) {
        if (taskId.isBlank() || mapId.isBlank()) return
        val key = taskId to mapId
        if (requestedPathKey == key) return
        pathRequestJob?.cancel()
        requestedPathKey = key
        _uiState.update {
            it.copy(plannedPath = null, isPathLoading = true, pathError = null)
        }
        pathRequestJob = viewModelScope.launch {
            val requestId = UUID.randomUUID().toString()
            val reply = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(TASK_REPLAY_PATH_TIMEOUT_MS) {
                    TaskPathStream.results.first { it.matchesReplayPathRequest(requestId) }
                }
            }
            val sent = tcpManager.requestPathPointPlan(
                taskId = taskId,
                mapId = mapId,
                requestId = requestId,
                forceReplan = false
            )
            val path = if (sent) reply.await() else {
                reply.cancel()
                null
            }
            if (requestedPathKey != key) return@launch
            val error = replayPlannedPathError(sent, path)
            _uiState.update {
                it.copy(
                    plannedPath = path?.takeIf { payload -> payload.hasRenderablePoints },
                    isPathLoading = false,
                    pathError = error
                )
            }
        }
    }

    private fun requestMapIfNeeded(mapId: String) {
        if (mapId.isBlank() || requestedMapId == mapId) return
        requestedMapId = mapId
        mapRequestActive = true
        _uiState.update { it.copy(isMapLoading = true, mapError = null) }
        if (!tcpManager.requestMapSnapshot(mapId)) {
            mapRequestActive = false
            _uiState.update { it.copy(isMapLoading = false, mapError = "地图请求发送失败") }
            return
        }
        mapTimeoutJob?.cancel()
        mapTimeoutJob = viewModelScope.launch {
            delay(MAP_TIMEOUT_MS)
            if (mapRequestActive) {
                mapRequestActive = false
                _uiState.update { it.copy(isMapLoading = false, mapError = "地图加载超时，请重试") }
            }
        }
    }

    private fun applyMapFrame(frame: MapImagePayload) {
        if (!mapRequestActive) return
        val expectedNumericId = requestedMapId?.toIntOrNull()
        if (expectedNumericId != null && frame.mapId != expectedNumericId) return
        if (!frame.hasReplayDisplayGeometry()) {
            finishMapWithError("地图几何信息不可用")
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(frame.imageBytes, 0, frame.imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            finishMapWithError("地图图片无法解码")
            return
        }
        mapRequestActive = false
        mapTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                mapFrame = frame.copy(imageBytes = frame.imageBytes.copyOf()),
                bitmapSize = bounds.outWidth to bounds.outHeight,
                isMapLoading = false,
                mapError = null
            )
        }
    }

    private fun finishMapWithError(message: String) {
        mapRequestActive = false
        mapTimeoutJob?.cancel()
        _uiState.update { it.copy(isMapLoading = false, mapError = message) }
    }

    private fun requestRobotSettingsIfNeeded(waitForResult: Boolean) {
        if (!tcpManager.requestSettingRead(readChassis = null, readMap = true)) {
            if (waitForResult) {
                _uiState.update {
                    it.copy(
                        isRobotSettingsLoading = false,
                        robotSettingsError = "小车尺寸请求发送失败"
                    )
                }
            }
            return
        }
        if (!waitForResult) return
        settingsTimeoutJob = viewModelScope.launch {
            delay(SETTINGS_TIMEOUT_MS)
            if (_uiState.value.robotWidth == null || _uiState.value.robotLength == null) {
                _uiState.update {
                    it.copy(
                        isRobotSettingsLoading = false,
                        robotSettingsError = "小车尺寸加载超时"
                    )
                }
            }
        }
    }

    private fun failTrajectory(message: String) {
        pageTimeoutJob?.cancel()
        val mapWasRequested = requestedMapId != null
        val pathWasRequested = requestedPathKey != null
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                isMapLoading = if (mapWasRequested) it.isMapLoading else false,
                isPathLoading = if (pathWasRequested) it.isPathLoading else false
            )
        }
        ToastUtils.showError(message)
    }

    override fun onCleared() {
        pageTimeoutJob?.cancel()
        mapTimeoutJob?.cancel()
        settingsTimeoutJob?.cancel()
        pathRequestJob?.cancel()
        mapRequestActive = false
        TaskSchedulerStream.resetTaskTrajectoryPage()
        super.onCleared()
    }

    private companion object {
        const val MAX_POINTS = 14_400
        const val SAMPLE_STEP = 1
        const val MAX_CHUNK_SIZE = 4096
        const val PAGE_TIMEOUT_MS = 15_000L
        const val MAP_TIMEOUT_MS = 30_000L
        const val SETTINGS_TIMEOUT_MS = 10_000L
    }
}

private fun MapImagePayload.hasReplayDisplayGeometry(): Boolean =
    mapWidth > 0 && mapHeight > 0 && resolution.isFinite() && resolution > 0f &&
        originX.isFinite() && originY.isFinite() && headingDeg.isFinite() &&
        alignmentYawDeg.isFinite() && rotationAlignmentDeltaDeg.isFinite()
