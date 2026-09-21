package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.core.model.state.TaskPolygonPointConfig
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapWorkspaceMetricsItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.StartGrindingPlanPreviewUiState
import java.text.SimpleDateFormat
import com.sinelynx.grindingrobot.feature.map.viewmodel.RelocalizationSettingsLoadState
import com.sinelynx.grindingrobot.feature.map.viewmodel.StartGrindingSessionUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4UiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.Step4LegacyPreviewUiState
import java.time.LocalTime
import java.util.Date
import java.util.Locale

@Composable
internal fun StartGrindingScreen(
    workspaces: List<MapWorkspaceMetricsItem>,
    session: StartGrindingSessionUiState,
    legacyPreview: Step4LegacyPreviewUiState,
    onOpenLegacyPreview: () -> Unit,
    onCloseLegacyPreview: () -> Unit,
    onRetryMap: () -> Unit,
    onLeavePreview: () -> Unit,
    robotPose: DevicePosePayload?,
    mapPreview: MapPreviewUiState?,
    existingTaskNames: List<String>,
    robotWidth: Double? = null,
    robotLength: Double? = null,
    isRelocalizationDialogVisible: Boolean,
    isRelocalizationSuccessful: Boolean,
    relocalizationRawStatus: String,
    relocalizationRunSpeed: Float,
    relocalizationTurnSpeed: Int,
    relocalizationTurnCount: Int,
    relocalizationSettingsLoadState: RelocalizationSettingsLoadState,
    relocalizationSettingsError: String,
    onRadarMapSync: () -> Unit,
    onRadarRelocalization: (Float, Float, Float) -> Unit,
    onStartRelocalization: () -> Unit,
    onDismissRelocalization: () -> Unit,
    onRetryRelocalizationSettings: () -> Unit,
    onRelocalizationRunSpeedDecrease: () -> Unit,
    onRelocalizationRunSpeedIncrease: () -> Unit,
    onRelocalizationTurnSpeedDecrease: () -> Unit,
    onRelocalizationTurnSpeedIncrease: () -> Unit,
    onRelocalizationTurnCountDecrease: () -> Unit,
    onRelocalizationTurnCountIncrease: () -> Unit,
    onRelocalizationTurnCountChange: (Float) -> Unit,
    onRelocalizationTurnCountChangeFinished: () -> Unit,
    onRelocalizationCommandStart: (DirectionCommand) -> Unit,
    onRelocalizationCommandEnd: (DirectionCommand) -> Unit,
    onRelocalizationPositionChanged: (JoystickPosition) -> Unit,
    onDismiss: () -> Unit,
    onRequestPlanPreview: (String, String, Map<String, Int>, List<TaskObstacleRegionConfig>) -> Unit,
    onStart: (String, String, Map<String, Int>, List<TaskObstacleRegionConfig>) -> Unit
) {
    val mapPreview = session.mapPreview
    val planPreview = session.plan
    val taskId = remember { "task_${System.currentTimeMillis()}" }
    var taskName by remember {
        mutableStateOf(generateDefaultTaskName(existingTaskNames))
    }
    val obstacleRegionSequence = remember { LocalTime.now().toSecondOfDay().toLong() }
    var step by remember { mutableStateOf(StartTaskStep.Workspace) }
    var obstacleMode by remember { mutableStateOf(TaskObstacleMode.Rectangle) }
    var obstacleShapes by remember { mutableStateOf<List<TaskObstacleShape>>(emptyList()) }
    var editingObstacleIndex by remember { mutableStateOf<Int?>(null) }
    var mapPanelSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedWorkspaces = remember { mutableStateMapOf<String, Boolean>() }
    var selectionInitialized by remember { mutableStateOf(false) }
    val passCounts = remember { mutableStateMapOf<String, Int>() }
    val manuallyChangedPassCounts = remember { mutableStateMapOf<String, Boolean>() }
    val mapPreviewBitmap = remember(mapPreview?.imageBytes) {
        mapPreview?.imageBytes?.let(::decodeMapPreviewBitmap)
    }

    LaunchedEffect(Unit) {
        onStartRelocalization()
    }

    DisposableEffect(Unit) {
        onDispose(onDismissRelocalization)
    }

    fun currentSelectedRegionRepeats(): Map<String, Int> {
        return workspaces
            .filter { selectedWorkspaces[it.regionId] == true }
            .associate { workspace ->
                workspace.regionId to (
                        passCounts[workspace.regionId] ?: workspace.repeat.coerceAtLeast(1)
                        ).coerceAtLeast(1)
            }
    }

    fun requestCurrentPlan(trigger: String) {
        val obstacles = calculateObstacleRegions(
            shapes = obstacleShapes,
            mapPreview = mapPreview,
            bitmapWidth = mapPreviewBitmap?.width ?: 0,
            bitmapHeight = mapPreviewBitmap?.height ?: 0,
            panelSize = mapPanelSize,
            obstacleRegionSequence = obstacleRegionSequence
        )
        logStartGrindingObstacleDiagnostics(
            trigger = trigger,
            taskId = taskId,
            shapes = obstacleShapes,
            obstacles = obstacles,
            mapPreview = mapPreview,
            bitmapWidth = mapPreviewBitmap?.width ?: 0,
            bitmapHeight = mapPreviewBitmap?.height ?: 0,
            panelSize = mapPanelSize
        )
        onRequestPlanPreview(taskId, taskName, currentSelectedRegionRepeats(), obstacles)
    }

    LaunchedEffect(step) {
        if (step == StartTaskStep.Preview) {
            requestCurrentPlan(trigger = "obstacle_next")
        }
    }

    // 前三步只切换本地编辑视图；仅真正退出 Preview 时取消本轮规划及旧图等待。
    DisposableEffect(step) {
        onDispose {
            if (step == StartTaskStep.Preview) onLeavePreview()
        }
    }

    LaunchedEffect(workspaces) {
        val workspaceIds = workspaces.map { it.regionId }.toSet()
        selectedWorkspaces.keys.toList()
            .filterNot { it in workspaceIds }
            .forEach { id ->
                selectedWorkspaces.remove(id)
                passCounts.remove(id)
                manuallyChangedPassCounts.remove(id)
            }
        workspaces.forEach { workspace ->
            if (!selectedWorkspaces.containsKey(workspace.regionId)) {
                selectedWorkspaces[workspace.regionId] = !selectionInitialized
            }
            if (manuallyChangedPassCounts[workspace.regionId] != true) {
                passCounts[workspace.regionId] = workspace.repeat.coerceAtLeast(1)
            }
        }
        if (workspaces.isNotEmpty()) selectionInitialized = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            StartTaskStepPill(
                step = step,
                modifier = Modifier.align(Alignment.TopStart)
            )
            DeviceStatusBar(modifier = Modifier.align(Alignment.TopEnd))

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 58.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TaskMapPanel(
                    step = step,
                    selectedWorkspaces = selectedWorkspaces,
                    workspaces = workspaces,
                    mapPreview = mapPreview,
                    isMapPreviewLoading = session.isMapLoading || session.isRegionsLoading,
                    mapLoadError = session.mapError ?: session.regionError,
                    interactionEnabled = session.canContinue,
                    workRegionPoints = session.regions?.workRegions.orEmpty(),
                    onRetry = {
                        if (step == StartTaskStep.Preview) {
                            requestCurrentPlan(trigger = "preview_retry")
                        } else {
                            onRetryMap()
                        }
                    },
                    mapPreviewBitmap = mapPreviewBitmap,
                    planPreview = planPreview,
                    robotPose = robotPose,
                    robotWidth = robotWidth,
                    robotLength = robotLength,
                    obstacleShapes = obstacleShapes,
                    editingObstacleIndex = editingObstacleIndex,
                    onWorkspaceClick = { regionId ->
                        selectedWorkspaces[regionId] = !(selectedWorkspaces[regionId] ?: false)
                    },
                    onObstacleClick = { tap, bounds, minimumSize ->
                        val newIndex = obstacleShapes.size
                        val newShape = createDefaultObstacleShape(
                            mode = obstacleMode,
                            tap = tap,
                            bounds = bounds,
                            minimumSize = minimumSize
                        )
                        obstacleShapes = obstacleShapes + newShape
                        editingObstacleIndex = newIndex
                    },
                    onObstacleCancel = {
                        editingObstacleIndex?.let { index ->
                            obstacleShapes = obstacleShapes.filterIndexed { itemIndex, _ ->
                                itemIndex != index
                            }
                        }
                        editingObstacleIndex = null
                    },
                    onObstacleConfirm = {
                        editingObstacleIndex = null
                    },
                    onObstacleChange = { candidate ->
                        editingObstacleIndex?.let { index ->
                            obstacleShapes = obstacleShapes.mapIndexed { itemIndex, shape ->
                                if (itemIndex == index) candidate else shape
                            }
                        }
                    },
                    onPanelSizeChanged = { mapPanelSize = it },
                    modifier = Modifier.size(width = 560.dp, height = 426.dp)
                )
                when (step) {
                    StartTaskStep.Workspace -> WorkspaceSelectionPanel(
                        workspaces = workspaces,
                        selectedWorkspaces = selectedWorkspaces,
                        onToggle = { id ->
                            selectedWorkspaces[id] = !(selectedWorkspaces[id] ?: false)
                        },
                        modifier = Modifier.size(width = 336.dp, height = 426.dp)
                    )

                    StartTaskStep.TaskParams -> TaskParamsPanel(
                        workspaces = workspaces,
                        selectedWorkspaces = selectedWorkspaces,
                        passCounts = passCounts,
                        onDecrease = { id ->
                            val current = passCounts[id] ?: 1
                            val next = (current - 1).coerceAtLeast(1)
                            passCounts[id] = next
                            manuallyChangedPassCounts[id] = true
                        },
                        onIncrease = { id ->
                            val current = passCounts[id] ?: 1
                            val next = (current + 1).coerceAtMost(99)
                            passCounts[id] = next
                            manuallyChangedPassCounts[id] = true
                        },
                        modifier = Modifier.size(width = 336.dp, height = 428.dp)
                    )

                    StartTaskStep.Obstacle -> ObstaclePlanningPanel(
                        selectedMode = obstacleMode,
                        onSelectMode = { mode ->
                            obstacleMode = mode
                            editingObstacleIndex = null
                        },
                        modifier = Modifier.size(width = 336.dp, height = 426.dp)
                    )

                    StartTaskStep.Preview -> TaskPreviewPanel(
                        workspaces = workspaces,
                        selectedWorkspaces = selectedWorkspaces,
                        passCounts = passCounts,
                        planPreview = planPreview,
                        taskName = taskName,
                        onTaskNameChange = { taskName = it },
                        canPreviewLegacyPlan = session.canPreviewLegacyPlan &&
                                planPreview?.regionRepeats == currentSelectedRegionRepeats(),
                        onPreviewLegacyPlan = onOpenLegacyPreview,
                        modifier = Modifier.size(width = 336.dp, height = 428.dp)
                    )
                }
            }

            TaskBottomActions(
                step = step,
                onCancel = onDismiss,
                onPrevious = {
                    step = when (step) {
                        StartTaskStep.Workspace -> StartTaskStep.Workspace
                        StartTaskStep.TaskParams -> StartTaskStep.Workspace
                        StartTaskStep.Obstacle -> StartTaskStep.TaskParams
                        StartTaskStep.Preview -> StartTaskStep.Obstacle
                    }
                },
                canProceed = session.canContinue && currentSelectedRegionRepeats().isNotEmpty(),
                canStart = session.canStart && planPreview?.regionRepeats == currentSelectedRegionRepeats(),
                onNext = next@{
                    if (!session.canContinue || currentSelectedRegionRepeats().isEmpty()) return@next
                    step = when (step) {
                        StartTaskStep.Workspace -> StartTaskStep.TaskParams
                        StartTaskStep.TaskParams -> StartTaskStep.Obstacle
                        StartTaskStep.Obstacle -> StartTaskStep.Preview
                        StartTaskStep.Preview -> StartTaskStep.Preview
                    }
                },
                onStart = start@{
                    if (!session.canStart || planPreview?.regionRepeats != currentSelectedRegionRepeats()) return@start
                    val obstacles = calculateObstacleRegions(
                        shapes = obstacleShapes,
                        mapPreview = mapPreview,
                        bitmapWidth = mapPreviewBitmap?.width ?: 0,
                        bitmapHeight = mapPreviewBitmap?.height ?: 0,
                        panelSize = mapPanelSize,
                        obstacleRegionSequence = obstacleRegionSequence
                    )
                    onStart(taskId, taskName, currentSelectedRegionRepeats(), obstacles)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            )
        }
    }

    if (step == StartTaskStep.Preview && legacyPreview.isVisible) {
        StartGrindingLegacyPreviewDialog(
            preview = legacyPreview,
            canRetry = session.canPreviewLegacyPlan && planPreview?.regionRepeats == currentSelectedRegionRepeats(),
            robotPose = robotPose, robotWidth = robotWidth, robotLength = robotLength,
            onDismiss = onCloseLegacyPreview, onRetry = onOpenLegacyPreview
        )
    }

    if (isRelocalizationDialogVisible) {
        RelocalizationDialog(
            mapPreview = mapPreview,
            robotPose = robotPose,
            isSuccessful = isRelocalizationSuccessful,
            rawStatus = relocalizationRawStatus,
            runSpeed = relocalizationRunSpeed,
            turnSpeed = relocalizationTurnSpeed,
            turnCount = relocalizationTurnCount,
            settingsLoadState = relocalizationSettingsLoadState,
            settingsError = relocalizationSettingsError,
            onRadarMapSync = onRadarMapSync,
            onRadarRelocalization = onRadarRelocalization,
            onDismiss = onDismissRelocalization,
            onRetrySettings = onRetryRelocalizationSettings,
            onRunSpeedDecrease = onRelocalizationRunSpeedDecrease,
            onRunSpeedIncrease = onRelocalizationRunSpeedIncrease,
            onTurnSpeedDecrease = onRelocalizationTurnSpeedDecrease,
            onTurnSpeedIncrease = onRelocalizationTurnSpeedIncrease,
            onTurnCountDecrease = onRelocalizationTurnCountDecrease,
            onTurnCountIncrease = onRelocalizationTurnCountIncrease,
            onTurnCountChange = onRelocalizationTurnCountChange,
            onTurnCountChangeFinished = onRelocalizationTurnCountChangeFinished,
            onCommandStart = onRelocalizationCommandStart,
            onCommandEnd = onRelocalizationCommandEnd,
            onPositionChanged = onRelocalizationPositionChanged
        )
    }
}

/** 仅把实时机器人和操作门禁适配给 Step4 弹窗，不传入研磨底图或新版路径。 */
@Composable
internal fun StartGrindingLegacyPreviewDialog(
    preview: Step4LegacyPreviewUiState,
    canRetry: Boolean,
    robotPose: DevicePosePayload?,
    robotWidth: Double?,
    robotLength: Double?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Step4LegacyPreviewDialog(
        preview = preview,
        currentState = MapScreenStep4UiState(
            robotPose = robotPose, robotWidth = robotWidth,
            robotLength = robotLength, isPlanning = !canRetry
        ),
        onDismiss = onDismiss, onRetry = onRetry
    )
}

@Composable
private fun StartTaskStepPill(
    step: StartTaskStep,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(1000.dp))
            .clip(RoundedCornerShape(1000.dp))
            .background(Color(0xFFE5EAF1))
            .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(1000.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF00A0E9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_task_start),
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
        }
        StepPillText("1.选择工作区", active = true)
        StepPillText(">", active = step != StartTaskStep.Workspace)
        StepPillText("任务参数", active = step != StartTaskStep.Workspace)
        StepPillText(">", active = step == StartTaskStep.Obstacle || step == StartTaskStep.Preview)
        StepPillText(
            "规划避障区",
            active = step == StartTaskStep.Obstacle || step == StartTaskStep.Preview
        )
        StepPillText(">", active = step == StartTaskStep.Preview)
        StepPillText("预览", active = step == StartTaskStep.Preview)
    }
}

@Composable
private fun StepPillText(text: String, active: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = if (active) Color(0xFF00A0E9) else Color(0xFF9DA3AF),
        fontWeight = FontWeight.Medium
    )
}

private fun generateDefaultTaskName(existingNames: List<String>): String {
    val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val todayStr = dateFormat.format(Date())
    if (todayStr !in existingNames) {
        return todayStr
    }
    for (i in 1..99999) {
        val candidate = "$todayStr($i)"
        if (candidate !in existingNames) {
            return candidate
        }
    }
    return todayStr
}

internal fun calculateObstacleRegions(
    shapes: List<TaskObstacleShape>,
    mapPreview: MapPreviewUiState?,
    bitmapWidth: Int,
    bitmapHeight: Int,
    panelSize: IntSize,
    obstacleRegionSequence: Long
): List<TaskObstacleRegionConfig> {
    if (shapes.isEmpty()) return emptyList()
    mapPreview ?: return emptyList()
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || panelSize == IntSize.Zero) return emptyList()

    // 任务预览必须消费两个后端角度分量，公式与建图各步骤一致。
    val totalRotation = mapPreview.alignmentYawDeg + mapPreview.rotationAlignmentDeltaDeg
    val canvasSize = Size(panelSize.width.toFloat(), panelSize.height.toFloat())
    return shapes.mapIndexedNotNull { index, shape ->
        val rect = shape.rect
        val corners = listOf(
            Offset(rect.left, rect.top),
            Offset(rect.right, rect.top),
            Offset(rect.right, rect.bottom),
            Offset(rect.left, rect.bottom)
        )
        val worldPoints = corners.mapNotNull { point ->
            canvasPointToMapWorld(
                point = point,
                canvasSize = canvasSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                mapWidth = mapPreview.mapWidth,
                mapHeight = mapPreview.mapHeight,
                resolution = mapPreview.resolution,
                originX = mapPreview.originX,
                originY = mapPreview.originY,
                headingDeg = mapPreview.headingDeg,
                rotationDeg = totalRotation
            )
        }
        worldPoints.takeIf { it.size == corners.size }?.let { points ->
            val obstacleRegionId = "obstacle_region_${obstacleRegionSequence + index}"
            TaskObstacleRegionConfig(
                regionId = obstacleRegionId,
                name = obstacleRegionId,
                points = points.map { (x, y) -> TaskPolygonPointConfig(x = x, y = y) }
            )
        }
    }
}

private fun logStartGrindingObstacleDiagnostics(
    trigger: String,
    taskId: String,
    shapes: List<TaskObstacleShape>,
    obstacles: List<TaskObstacleRegionConfig>,
    mapPreview: MapPreviewUiState?,
    bitmapWidth: Int,
    bitmapHeight: Int,
    panelSize: IntSize
) {
    val tag = "StartGrindingObstacle"
    if (mapPreview == null) {
        LogUtils.d(tag, "trigger=$trigger taskId=$taskId mapPreview=null shapes=${shapes.size}")
        return
    }
    val totalRotation = mapPreview.alignmentYawDeg + mapPreview.rotationAlignmentDeltaDeg
    val canvasSize = Size(panelSize.width.toFloat(), panelSize.height.toFloat())
    LogUtils.d(
        tag,
        "trigger=$trigger taskId=$taskId shapes=${shapes.size} converted=${obstacles.size} " +
            "map=${mapPreview.mapWidth}x${mapPreview.mapHeight} bitmap=${bitmapWidth}x$bitmapHeight " +
            "panel=${panelSize.width}x${panelSize.height} resolution=${mapPreview.resolution} " +
            "origin=(${mapPreview.originX},${mapPreview.originY}) headingDeg=${mapPreview.headingDeg} " +
            "alignmentYawDeg=${mapPreview.alignmentYawDeg} " +
            "rotationAlignmentDeltaDeg=${mapPreview.rotationAlignmentDeltaDeg} totalRotationDeg=$totalRotation"
    )
    shapes.forEachIndexed { index, shape ->
        val rect = shape.rect
        val canvasCorners = listOf(
            Offset(rect.left, rect.top),
            Offset(rect.right, rect.top),
            Offset(rect.right, rect.bottom),
            Offset(rect.left, rect.bottom)
        )
        val currentWorld = canvasCorners.map { point ->
            canvasPointToMapWorld(
                point = point,
                canvasSize = canvasSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                mapWidth = mapPreview.mapWidth,
                mapHeight = mapPreview.mapHeight,
                resolution = mapPreview.resolution,
                originX = mapPreview.originX,
                originY = mapPreview.originY,
                rotationDeg = totalRotation,
                headingDeg = mapPreview.headingDeg
            )
        }
        val legacyWorld = canvasCorners.map { point ->
            canvasPointToMapWorld(
                point = point,
                canvasSize = canvasSize,
                bitmapWidth = bitmapWidth,
                bitmapHeight = bitmapHeight,
                mapWidth = mapPreview.mapWidth,
                mapHeight = mapPreview.mapHeight,
                resolution = mapPreview.resolution,
                originX = mapPreview.originX,
                originY = mapPreview.originY,
                rotationDeg = totalRotation,
                headingDeg = 0f
            )
        }
        val insideMap = legacyWorld.map { world ->
            if (world == null || mapPreview.resolution <= 0f) {
                false
            } else {
                val mapPixelX = (world.first - mapPreview.originX) / mapPreview.resolution
                val mapPixelYFromBottom = (world.second - mapPreview.originY) / mapPreview.resolution
                mapPixelX in 0.0..mapPreview.mapWidth.toDouble() &&
                    mapPixelYFromBottom in 0.0..mapPreview.mapHeight.toDouble()
            }
        }
        LogUtils.d(
            tag,
            "trigger=$trigger taskId=$taskId obstacle[$index] mode=${shape.mode} " +
                "canvasCorners=$canvasCorners legacyHeading0World=$legacyWorld " +
                "currentWorld=$currentWorld insideMap=$insideMap"
        )
    }
}
