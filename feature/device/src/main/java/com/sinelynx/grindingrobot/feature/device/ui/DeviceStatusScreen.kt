package com.sinelynx.grindingrobot.feature.device.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinelynx.grindingrobot.feature.common.component.CameraRtspOverlay
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceStatusUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceStatusViewModel
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceTaskUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskMapTransformUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskPoseUiItem
import com.sinelynx.grindingrobot.feature.map.ui.FittedTaskPathOverlay
import com.sinelynx.grindingrobot.feature.map.ui.drawTaskDisplayAnchors
import com.sinelynx.grindingrobot.feature.map.ui.drawTaskDisplayRegions
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import com.sinelynx.grindingrobot.feature.map.R as MapR

@Composable
fun DeviceStatusScreen(
    viewModel: DeviceStatusViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // 页面生命周期只控制地图补取：不可见时取消等待，可见后检查任务/路径版本。
    // 实时状态订阅在 ViewModel 中独立运行，不因复用地图快照而停止接收 0x0504。
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setMapVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setMapVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setMapVisible(false)
        }
    }
    DeviceStatusContent(
        uiState = uiState,
        onCameraClick = viewModel::onCameraClick,
        onStartTaskClick = viewModel::onStartTaskClick,
        onStopTaskClick = viewModel::onStopTaskClick,
        onPauseTaskClick = viewModel::onPauseTaskClick,
        onRetryMap = viewModel::retryTaskMap,
        modifier = modifier
    )
}

@Composable
private fun DeviceStatusContent(
    uiState: DeviceStatusUiState,
    onCameraClick: () -> Unit,
    onStartTaskClick: () -> Unit,
    onStopTaskClick: () -> Unit,
    onPauseTaskClick: () -> Unit,
    onRetryMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.taskState != DeviceTaskUiState.IDLE) {
                    StatusCard(uiState = uiState)
                    TaskStatsCard(uiState = uiState)
                } else {
                    StatusCard(
                        uiState = uiState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ZoomableTaskPreviewPanel(
                uiState = uiState,
                onCameraClick = onCameraClick,
                onStartTaskClick = onStartTaskClick,
                onStopTaskClick = onStopTaskClick,
                onPauseTaskClick = onPauseTaskClick,
                onRetryMap = onRetryMap,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (uiState.showCameraOverlay) {
            CameraRtspOverlay(
                streamUrl = uiState.videoStreamUrl,
                initialTopPadding = 16.dp,
                initialEndPadding = 70.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
 private fun StatusCard(
    uiState: DeviceStatusUiState,
    modifier: Modifier = Modifier
 ) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusTitle(CommonR.drawable.ic_device_status, "状态")
        StatusRow("导航状态", uiState.navigationStatus, Color(0xFF00A0E9))
        StatusRow("地图", if (uiState.mapAvailable) "已加载" else "暂无地图")
        StatusRow("导航雷达", uiState.navRadarStatus, if (uiState.navRadarStatus == "正常") Color(0xFF33C461) else Color(0xFFD82B2A))
        StatusRow("避障雷达", uiState.obstacleRadarStatus, if (uiState.obstacleRadarStatus == "正常") Color(0xFF33C461) else Color(0xFFD82B2A))
        StatusRow("定位质量", uiState.localizationQuality.toString(), Color(0xFF33C461))
        StatusRow("直行速度", uiState.straightSpeedText)
        StatusRow("研磨转速", uiState.grindRpmText)
    }
}

@Composable
private fun TaskStatsCard(uiState: DeviceStatusUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
//        Text(text = "任务统计", color = Color(0x80202937), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        StatusTitle(CommonR.drawable.ic_task_info, "任务统计")
        StatusRow("当前任务总研磨面积", uiState.totalTaskAreaText)
        StatusRow("当前任务已研磨面积", uiState.doneTaskAreaText)
        StatusRow("当前任务剩余研磨面积", uiState.remainTaskAreaText)
        StatusRow("今日总研磨面积", uiState.todayAreaText)
        StatusRow("机器总研磨面积", uiState.totalAreaText)
        StatusRow("当前任务剩余时间", uiState.remainTimeText)
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color = Color(0xFF202937)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "$label:", color = Color(0x806D737D), fontSize = 12.sp)
        Text(text = value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusTitle(@DrawableRes icon: Int, title: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(text = title, color = Color(0xFF202937), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** 保留的旧非缩放面板；当前 DeviceStatusContent 的实际入口是 ZoomableTaskPreviewPanel。 */
@Composable
private fun TaskPreviewPanel(
    uiState: DeviceStatusUiState,
    onCameraClick: () -> Unit,
    onStartTaskClick: () -> Unit,
    onStopTaskClick: () -> Unit,
    onPauseTaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapBitmap = remember(uiState.mapImageBytes) {
        uiState.mapImageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
    ) {
        if (mapBitmap != null) {
            Image(
                bitmap = mapBitmap,
                contentDescription = "路径规划地图",
                alignment = Alignment.Center,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = uiState.mapTransform?.totalRotationDeg ?: 0f
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)), radius = 900f))
            )
        }

        if (uiState.mapTransform == null) {
            Image(
                painter = painterResource(MapR.drawable.ic_robot),
                contentDescription = "机器人图标",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 36.dp, height = 48.dp)
            )
        }

        TaskTrajectoryOverlay(
            trajectory = uiState.trajectory,
            mapTransform = uiState.mapTransform,
            bitmapWidth = mapBitmap?.width ?: 0,
            bitmapHeight = mapBitmap?.height ?: 0,
            robotWidth = uiState.robotWidth,
            robotLength = uiState.robotLength,
            modifier = Modifier.fillMaxSize()
        )

        val cameraButtonActive = uiState.showCameraOverlay
        val cameraButtonShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .shadow(elevation = 8.dp, spotColor = Color(0x1A000000), ambientColor = Color(0x1A000000))
                .size(48.dp)
                .clip(cameraButtonShape)
                .background(if (cameraButtonActive) Color(0xFF00A0E9) else Color.White)
                .border(
                    width = if (cameraButtonActive) 0.dp else 1.dp,
                    color = if (cameraButtonActive) Color.Transparent else Color(0xFFF0F2F5),
                    shape = cameraButtonShape
                )
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(MapR.drawable.ic_robot_camera),
                contentDescription = "机器人摄像头",
                tint = if (cameraButtonActive) Color.White else Color(0xFF202937),
                modifier = Modifier.size(20.dp)
            )
        }

        Image(
            painter = painterResource(CommonR.drawable.ic_repeat_diagram),
            contentDescription = "遍数示意图",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(width = 26.dp, height = 226.dp)
        )

        if (uiState.taskState != DeviceTaskUiState.IDLE && uiState.currentTaskId.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "结束任务",
                    iconRes = CommonR.drawable.ic_task_stop,
                    color = Color(0xFFD82B2A),
                    onClick = onStopTaskClick
                )
                ActionButton(
                    text = when (uiState.taskState) {
                        DeviceTaskUiState.READY -> "开始任务"
                        DeviceTaskUiState.PAUSED -> "继续研磨"
                        else -> "暂停任务"
                    },
                    iconRes = if (uiState.taskState == DeviceTaskUiState.READY || uiState.taskState == DeviceTaskUiState.PAUSED) {
                        CommonR.drawable.ic_task_resume
                    } else {
                        CommonR.drawable.ic_task_pause
                    },
                    color = if (uiState.taskState == DeviceTaskUiState.READY || uiState.taskState == DeviceTaskUiState.PAUSED) {
                        Color(0xFF00A0E9)
                    } else {
                        Color(0xFFF19F21)
                    },
                    onClick = if (uiState.taskState == DeviceTaskUiState.READY) onStartTaskClick else onPauseTaskClick
                )
            }
        }
    }
}

/**
 * 当前设备页地图入口：冻结的 MapRequest 底图上组合独立区域、规划路径和实时轨迹。
 *
 * trajectory 保存世界坐标（米），需减地图原点、撤销地图朝向，再从地图网格换算到实际图片。
 * 逻辑地图的 Y 轴向上，Bitmap 的 Y 轴向下；这里不能直接把世界 x/y 当图片坐标。
 * 底图是任务快照；位置随 0x0504 上报更新，缺少底图几何时不绘制移动机器人。
 *
 * 图层顺序由下方组合顺序决定：底图 → 擦除区 → 所选工作区 → 公共/临时禁区 →
 * 红蓝规划路径 → 全部起点 → 全部终点 → 实际行驶轨迹 → 小车。
 * 底图用 graphicsLayer 变换，叠加层使用最终屏幕坐标，不能再给这些叠加层套一次相同变换。
 */
@Composable
private fun ZoomableTaskPreviewPanel(
    uiState: DeviceStatusUiState,
    onCameraClick: () -> Unit,
    onStartTaskClick: () -> Unit,
    onStopTaskClick: () -> Unit,
    onPauseTaskClick: () -> Unit,
    onRetryMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapBitmap = remember(uiState.mapImageBytes) {
        uiState.mapImageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    var mapZoom by remember { mutableStateOf(1f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }
    val currentMapZoom by rememberUpdatedState(mapZoom)
    val currentMapPan by rememberUpdatedState(mapPan)
    val totalRotation = uiState.mapTransform?.totalRotationDeg ?: 0f
    val pathGeo = remember(uiState.mapTransform) {
        uiState.mapTransform?.let { transform ->
            MapGeo(
                mapWidth = transform.width,
                mapHeight = transform.height,
                resolution = transform.resolution,
                originX = transform.originX,
                originY = transform.originY,
                headingDeg = transform.headingDeg,
                mapVersion = 0,
                alignmentYawDeg = transform.alignmentYawDeg,
                rotationAlignmentDeltaDeg = transform.rotationAlignmentDeltaDeg
            )
        }
    }

    // 只有底图、几何或可用状态变化才复位视角；路径更新和连续位置上报不在 key 中。
    LaunchedEffect(uiState.mapImageBytes, uiState.mapTransform, uiState.mapAvailable) {
        mapZoom = 1f
        mapPan = Offset.Zero
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.mapAvailable, mapBitmap, uiState.mapTransform) {
                    if (!uiState.mapAvailable || mapBitmap == null) return@pointerInput
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val containerSize = Size(size.width.toFloat(), size.height.toFloat())
                        if (containerSize.width <= 0f || containerSize.height <= 0f) return@detectTransformGestures
                        val previousZoom = currentMapZoom
                        val previousPan = currentMapPan
                        val nextZoom = (previousZoom * zoomChange).coerceIn(MIN_TASK_MAP_ZOOM, MAX_TASK_MAP_ZOOM)
                        val zoomRatio = if (previousZoom == 0f) 1f else nextZoom / previousZoom
                        val proposedPan = centroid - (centroid - previousPan) * zoomRatio + panChange
                        val baseBounds = calculateImageBounds(
                            containerWidth = containerSize.width,
                            containerHeight = containerSize.height,
                            imageWidth = mapBitmap.width,
                            imageHeight = mapBitmap.height
                        ).rotatedBoundingBox(totalRotation)
                        mapZoom = nextZoom
                        mapPan = clampTaskMapPan(baseBounds, nextZoom, proposedPan)
                    }
                }
        ) {
            if (uiState.mapAvailable && mapBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = mapZoom
                            scaleY = mapZoom
                            translationX = mapPan.x
                            translationY = mapPan.y
                        }
                ) {
                    Image(
                        bitmap = mapBitmap,
                        contentDescription = "task map",
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = totalRotation
                            }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)), radius = 900f))
                )
            }

            // 本 Canvas 不挂缩放图层，区域函数内部完成 Fit、显示旋转、缩放和平移。
            if (pathGeo != null && mapBitmap != null) Canvas(Modifier.fillMaxSize()) {
                uiState.taskMap.snapshot?.let { snapshot ->
                    drawTaskDisplayRegions(snapshot, pathGeo, mapBitmap.width to mapBitmap.height,
                        totalRotation, mapZoom, mapPan)
                }
            }

            FittedTaskPathOverlay(
                path = uiState.plannedPath,
                geo = pathGeo,
                mapImageSize = uiState.mapTransform?.let { it.width to it.height },
                bitmapSize = mapBitmap?.let { it.width to it.height },
                mapRotationDeg = totalRotation,
                viewportScale = mapZoom,
                viewportOffset = mapPan,
                colorByScope = true
            )

            if (pathGeo != null && mapBitmap != null) Canvas(Modifier.fillMaxSize()) {
                uiState.taskMap.snapshot?.let { snapshot ->
                    drawTaskDisplayAnchors(snapshot, pathGeo, mapBitmap.width to mapBitmap.height,
                        totalRotation, mapZoom, mapPan)
                }
            }

            // 无几何时的居中图标仅为占位，不代表设备的实际位置。
            if (uiState.mapTransform == null) {
                CenterRobotMarker(
                    zoom = mapZoom,
                    pan = mapPan,
                    robotWidth = uiState.robotWidth,
                    robotLength = uiState.robotLength,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ZoomableTaskTrajectoryOverlay(
                trajectory = uiState.trajectory,
                mapTransform = uiState.mapTransform,
                bitmapWidth = mapBitmap?.width ?: 0,
                bitmapHeight = mapBitmap?.height ?: 0,
                zoom = mapZoom,
                pan = mapPan,
                robotWidth = uiState.robotWidth,
                robotLength = uiState.robotLength,
                modifier = Modifier.fillMaxSize()
            )
        }

        /*val mapState = uiState.taskMap
        // 三类加载错误各自展示；路径失败不遮掉已加载的底图、区域或实时机器人。
        val mapMessages = listOfNotNull(
            if (mapState.waitingForHandoff) "正在接收任务地图" else null,
            if (mapState.loadingMap) "地图加载中" else mapState.mapError,
            if (mapState.loadingRegions) "地图区域加载中" else mapState.regionError,
            if (mapState.loadingPath) "路径加载中" else mapState.pathError,
            mapState.informationWarning
        )
        if (mapMessages.isNotEmpty()) Column(
            Modifier.align(Alignment.TopStart).padding(14.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(10.dp)
        ) {
            mapMessages.forEach { Text(it, fontSize = 12.sp, color = Color(0xFF555555)) }
            if (mapState.mapError != null || mapState.regionError != null || mapState.pathError != null) {
                Text("重试加载", color = Color(0xFF1976D2), modifier = Modifier
                    .clickable(onClick = onRetryMap).padding(top = 6.dp))
            }
        }*/

        val cameraButtonActive = uiState.showCameraOverlay
        val cameraButtonShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .shadow(elevation = 8.dp, spotColor = Color(0x1A000000), ambientColor = Color(0x1A000000))
                .size(48.dp)
                .clip(cameraButtonShape)
                .background(if (cameraButtonActive) Color(0xFF00A0E9) else Color.White)
                .border(
                    width = if (cameraButtonActive) 0.dp else 1.dp,
                    color = if (cameraButtonActive) Color.Transparent else Color(0xFFF0F2F5),
                    shape = cameraButtonShape
                )
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(MapR.drawable.ic_robot_camera),
                contentDescription = "robot camera",
                tint = if (cameraButtonActive) Color.White else Color(0xFF202937),
                modifier = Modifier.size(20.dp)
            )
        }

        Image(
            painter = painterResource(CommonR.drawable.ic_repeat_diagram),
            contentDescription = "repeat diagram",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(width = 26.dp, height = 226.dp)
        )

        if (uiState.taskState != DeviceTaskUiState.IDLE && uiState.currentTaskId.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "结束任务",
                    iconRes = CommonR.drawable.ic_task_stop,
                    color = Color(0xFFD82B2A),
                    onClick = onStopTaskClick
                )
                ActionButton(
                    text = when (uiState.taskState) {
                        DeviceTaskUiState.READY -> "开始任务"
                        DeviceTaskUiState.PAUSED -> "继续研磨"
                        else -> "暂停任务"
                    },
                    iconRes = if (uiState.taskState == DeviceTaskUiState.READY || uiState.taskState == DeviceTaskUiState.PAUSED) {
                        CommonR.drawable.ic_task_resume
                    } else {
                        CommonR.drawable.ic_task_pause
                    },
                    color = if (uiState.taskState == DeviceTaskUiState.READY || uiState.taskState == DeviceTaskUiState.PAUSED) {
                        Color(0xFF00A0E9)
                    } else {
                        Color(0xFFF19F21)
                    },
                    onClick = if (uiState.taskState == DeviceTaskUiState.READY) onStartTaskClick else onPauseTaskClick
                )
            }
        }
    }
}

@Composable
private fun CenterRobotMarker(
    zoom: Float,
    pan: Offset,
    robotWidth: Double?,
    robotLength: Double?,
    modifier: Modifier = Modifier
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val (drawWidth, drawHeight) = remember(zoom, robotWidth, robotLength) {
        val defaultWidth = 36.dp
        val defaultHeight = 48.dp
        if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
            val baseWidth = defaultWidth
            val baseHeight = defaultWidth * (robotLength / robotWidth).toFloat()
            val drawW = (baseWidth * zoom).coerceIn(16.dp, 100.dp)
            val drawH = (baseHeight * zoom).coerceIn(16.dp, 100.dp)
            drawW to drawH
        } else {
            val drawW = (defaultWidth * zoom).coerceIn(16.dp, 100.dp)
            val drawH = (defaultHeight * zoom).coerceIn(16.dp, 100.dp)
            drawW to drawH
        }
    }
    val drawWidthPx = with(density) { drawWidth.toPx() }
    val drawHeightPx = with(density) { drawHeight.toPx() }
    Box(
        modifier = modifier.onSizeChanged { viewportSize = it }
    ) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@Box
        val center = Offset(
            x = viewportSize.width / 2f,
            y = viewportSize.height / 2f
        ).transformBy(zoom, pan)
        Image(
            painter = painterResource(MapR.drawable.ic_robot),
            contentDescription = "robot marker",
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (center.x - drawWidthPx / 2f).roundToInt(),
                        y = (center.y - drawHeightPx / 2f).roundToInt()
                    )
                }
                .size(width = drawWidth, height = drawHeight)
        )
    }
}

/**
 * 实际轨迹和机器人使用 0x0504 的位置，与 0x0506 的规划路径是两套数据。
 * 轨迹颜色沿用遍数规则，不能替换成规划路径的 within_region/between_regions 红蓝规则。
 * 先画轨迹线和采样点，再画机器人；机器人取最后一个可投影点，越界/无效点会被过滤。
 * 图标尺寸沿用配置长宽比和屏幕尺寸限制，不是地图中的实际占地轮廓。
 */
@Composable
private fun ZoomableTaskTrajectoryOverlay(
    trajectory: List<TaskPoseUiItem>,
    mapTransform: TaskMapTransformUiState?,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
    pan: Offset,
    robotWidth: Double?,
    robotLength: Double?,
    modifier: Modifier = Modifier
) {
    if (trajectory.isEmpty() || mapTransform == null || bitmapWidth <= 0 || bitmapHeight <= 0) return

    val bitmapTrajectory = remember(trajectory, mapTransform, bitmapWidth, bitmapHeight) {
        trajectory.mapNotNull { pose ->
            pose.toTaskBitmapPoint(mapTransform, bitmapWidth, bitmapHeight)?.let { offset ->
                TaskTrajectoryDrawPoint(
                    offset = offset,
                    lineColor = Color(pose.lineColorArgb),
                    headingDeg = pose.headingDeg
                )
            }
        }
    }
    val robotPoint = bitmapTrajectory.lastOrNull() ?: return
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.onSizeChanged { viewportSize = it }) {
        val mapBounds = remember(viewportSize, bitmapWidth, bitmapHeight) {
            if (viewportSize.width > 0 && viewportSize.height > 0) {
                calculateImageBounds(
                    containerWidth = viewportSize.width.toFloat(),
                    containerHeight = viewportSize.height.toFloat(),
                    imageWidth = bitmapWidth,
                    imageHeight = bitmapHeight
                )
            } else {
                null
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val finalMapBounds = mapBounds ?: calculateImageBounds(
                containerWidth = size.width,
                containerHeight = size.height,
                imageWidth = bitmapWidth,
                imageHeight = bitmapHeight
            )
            for (index in 1 until bitmapTrajectory.size) {
                drawLine(
                    color = bitmapTrajectory[index].lineColor,
                    start = bitmapTrajectory[index - 1].offset
                        .toTaskDrawOffset(finalMapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg)
                        .transformBy(zoom, pan),
                    end = bitmapTrajectory[index].offset
                        .toTaskDrawOffset(finalMapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg)
                        .transformBy(zoom, pan),
                    strokeWidth = 4f * zoom
                )
            }
            bitmapTrajectory.forEach { point ->
                val center = point.offset
                    .toTaskDrawOffset(finalMapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg)
                    .transformBy(zoom, pan)
                drawCircle(
                    color = Color.White,
                    radius = 5f * zoom,
                    center = center
                )
                drawCircle(
                    color = point.lineColor,
                    radius = 5f * zoom,
                    center = center,
                    style = Stroke(width = 2f * zoom)
                )
            }
        }

        if (viewportSize.width <= 0 || viewportSize.height <= 0 || mapBounds == null) return@Box
        val robotCenter = robotPoint.offset
            .toTaskDrawOffset(mapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg)
            .transformBy(zoom, pan)

        val (drawWidth, drawLength) = remember(zoom, robotWidth, robotLength) {
            val defaultSize = 42.dp
            if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
                val baseWidth: Dp
                val baseLength: Dp
                if (robotLength >= robotWidth) {
                    baseLength = defaultSize
                    baseWidth = defaultSize * (robotWidth / robotLength).toFloat()
                } else {
                    baseWidth = defaultSize
                    baseLength = defaultSize * (robotLength / robotWidth).toFloat()
                }
                val drawW = (baseWidth * zoom).coerceIn(16.dp, 100.dp)
                val drawL = (baseLength * zoom).coerceIn(16.dp, 100.dp)
                drawW to drawL
            } else {
                val drawW = (defaultSize * zoom).coerceIn(16.dp, 100.dp)
                drawW to drawW
            }
        }
        val drawWidthPx = with(density) { drawWidth.toPx() }
        val drawLengthPx = with(density) { drawLength.toPx() }

        Image(
            painter = painterResource(MapR.drawable.ic_robot),
            contentDescription = "robot current position",
            modifier = Modifier
                .size(width = drawWidth, height = drawLength)
                .offset {
                    IntOffset(
                        x = (robotCenter.x - drawWidthPx / 2f).roundToInt(),
                        y = (robotCenter.y - drawLengthPx / 2f).roundToInt()
                    )
                }
                .graphicsLayer {
                    rotationZ = taskRobotRotationDeg(robotPoint.headingDeg, mapTransform)
                }
        )
    }
}

@Composable
private fun TaskTrajectoryOverlay(
    trajectory: List<TaskPoseUiItem>,
    mapTransform: TaskMapTransformUiState?,
    bitmapWidth: Int,
    bitmapHeight: Int,
    robotWidth: Double?,
    robotLength: Double?,
    modifier: Modifier = Modifier
) {
    if (trajectory.isEmpty() || mapTransform == null || bitmapWidth <= 0 || bitmapHeight <= 0) return

    val bitmapTrajectory = remember(trajectory, mapTransform, bitmapWidth, bitmapHeight) {
        trajectory.mapNotNull { pose ->
            pose.toTaskBitmapPoint(mapTransform, bitmapWidth, bitmapHeight)?.let { offset ->
                TaskTrajectoryDrawPoint(
                    offset = offset,
                    lineColor = Color(pose.lineColorArgb),
                    headingDeg = pose.headingDeg
                )
            }
        }
    }
    val robotPoint = bitmapTrajectory.lastOrNull() ?: return
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val mapBounds = remember(bitmapWidth, bitmapHeight, maxWidth, maxHeight) {
            calculateImageBounds(
                containerWidth = maxWidth.value * density.density,
                containerHeight = maxHeight.value * density.density,
                imageWidth = bitmapWidth,
                imageHeight = bitmapHeight
            )
        }

        val (drawWidth, drawLength) = remember(robotWidth, robotLength) {
            val defaultSize = 42.dp
            if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
                val baseWidth: Dp
                val baseLength: Dp
                if (robotLength >= robotWidth) {
                    baseLength = defaultSize
                    baseWidth = defaultSize * (robotWidth / robotLength).toFloat()
                } else {
                    baseWidth = defaultSize
                    baseLength = defaultSize * (robotLength / robotWidth).toFloat()
                }
                baseWidth to baseLength
            } else {
                defaultSize to defaultSize
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val finalMapBounds = mapBounds
            for (index in 1 until bitmapTrajectory.size) {
                drawLine(
                    color = bitmapTrajectory[index].lineColor,
                    start = bitmapTrajectory[index - 1].offset
                        .toTaskDrawOffset(finalMapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg),
                    end = bitmapTrajectory[index].offset
                        .toTaskDrawOffset(finalMapBounds, bitmapWidth, bitmapHeight, mapTransform.totalRotationDeg),
                    strokeWidth = 4f
                )
            }
            bitmapTrajectory.forEach { point ->
                val center = point.offset.toTaskDrawOffset(
                    finalMapBounds,
                    bitmapWidth,
                    bitmapHeight,
                    mapTransform.totalRotationDeg
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = center
                )
                drawCircle(
                    color = point.lineColor,
                    radius = 5f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        val robotCenter = robotPoint.offset.toTaskDrawOffset(
            mapBounds,
            bitmapWidth,
            bitmapHeight,
            mapTransform.totalRotationDeg
        )
        val drawX = robotCenter.x / density.density
        val drawY = robotCenter.y / density.density
        Image(
            painter = painterResource(MapR.drawable.ic_robot),
            contentDescription = "机器人当前位置",
            modifier = Modifier
                .size(width = drawWidth, height = drawLength)
                .offset(
                    x = (drawX - drawWidth.value / 2f).dp,
                    y = (drawY - drawLength.value / 2f).dp
                )
                .graphicsLayer {
                    rotationZ = taskRobotRotationDeg(robotPoint.headingDeg, mapTransform)
                }
        )
    }
}

private data class TaskTrajectoryDrawPoint(
    val offset: Offset,
    val lineColor: Color,
    val headingDeg: Float
)

internal data class MapDrawBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private const val MIN_TASK_MAP_ZOOM = 1f
private const val MAX_TASK_MAP_ZOOM = 4f

internal fun calculateImageBounds(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int
): MapDrawBounds {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return MapDrawBounds(left = 0f, top = 0f, width = containerWidth, height = containerHeight)
    }
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val containerAspect = containerWidth / containerHeight
    val drawWidth: Float
    val drawHeight: Float
    if (containerAspect > imageAspect) {
        drawHeight = containerHeight
        drawWidth = drawHeight * imageAspect
    } else {
        drawWidth = containerWidth
        drawHeight = drawWidth / imageAspect
    }
    return MapDrawBounds(
        left = (containerWidth - drawWidth) / 2f,
        top = (containerHeight - drawHeight) / 2f,
        width = drawWidth,
        height = drawHeight
    )
}

private fun clampTaskMapPan(bounds: MapDrawBounds, zoom: Float, pan: Offset): Offset {
    if (zoom <= MIN_TASK_MAP_ZOOM) return Offset.Zero
    val minX = bounds.right - bounds.right * zoom
    val maxX = bounds.left - bounds.left * zoom
    val minY = bounds.bottom - bounds.bottom * zoom
    val maxY = bounds.top - bounds.top * zoom
    return Offset(
        x = pan.x.coerceIn(minX, maxX),
        y = pan.y.coerceIn(minY, maxY)
    )
}

private val MapDrawBounds.right: Float
    get() = left + width

private val MapDrawBounds.bottom: Float
    get() = top + height

private val MapDrawBounds.center: Offset
    get() = Offset(left + width / 2f, top + height / 2f)

private fun MapDrawBounds.rotatedBoundingBox(rotationDeg: Float): MapDrawBounds {
    if (rotationDeg == 0f) return this
    val pivot = center
    val corners = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(right, bottom),
        Offset(left, bottom)
    ).map { it.rotateAround(pivot, rotationDeg) }
    val minX = corners.minOf { it.x }
    val maxX = corners.maxOf { it.x }
    val minY = corners.minOf { it.y }
    val maxY = corners.maxOf { it.y }
    return MapDrawBounds(
        left = minX,
        top = minY,
        width = maxX - minX,
        height = maxY - minY
    )
}

internal fun Offset.transformBy(zoom: Float, pan: Offset): Offset {
    return Offset(
        x = x * zoom + pan.x,
        y = y * zoom + pan.y
    )
}

/**
 * 世界航向先减地图 headingDeg，再转换到屏幕顺时针角度，最后加页面显示旋转。
 * 图标素材默认朝上，因此需 90 度基准偏移；这里不再使用路径 alignment_yaw。
 */
internal fun taskRobotRotationDeg(headingDeg: Float, transform: TaskMapTransformUiState): Float =
    90f - (headingDeg - transform.headingDeg) + transform.totalRotationDeg

/**
 * 世界坐标 → 原始网格 → 实际 Bitmap 像素，仅处理地图几何，不处理容器 Fit 或用户手势。
 * 先减原点并撤销 headingDeg，再除 resolution、翻转 Y；最后分别乘图片宽高与网格宽高的比值。
 * 宽高比例可能不同，不能只乘单个缩放数，也不能额外叠加 previewScaleX/Y。
 * 本轨迹投影会丢弃无效/地图范围外的点；后续 toTaskDrawOffset 才负责页面适配和显示旋转。
 */
internal fun TaskPoseUiItem.toTaskBitmapPoint(
    transform: TaskMapTransformUiState,
    bitmapWidth: Int,
    bitmapHeight: Int
): Offset? {
    if (
        transform.width <= 0 || transform.height <= 0 || transform.resolution <= 0f ||
        bitmapWidth <= 0 || bitmapHeight <= 0
    ) {
        return null
    }
    val theta = transform.headingDeg * PI / 180.0
    val dx = x.toDouble() - transform.originX
    val dy = y.toDouble() - transform.originY
    val mapX = dx * cos(theta) + dy * sin(theta)
    val mapY = -dx * sin(theta) + dy * cos(theta)
    val mapPixelX = mapX / transform.resolution
    val mapPixelYFromTop = transform.height - mapY / transform.resolution
    if (!mapPixelX.isFinite() || !mapPixelYFromTop.isFinite()) return null
    if (mapPixelX !in 0.0..transform.width.toDouble() || mapPixelYFromTop !in 0.0..transform.height.toDouble()) {
        return null
    }
    return Offset(
        x = (mapPixelX * bitmapWidth / transform.width).toFloat(),
        y = (mapPixelYFromTop * bitmapHeight / transform.height).toFloat()
    )
}

/** Bitmap 像素 → 居中等比适配区域 → 围绕适配区域中心旋转；之后调用 transformBy 施加手势。 */
internal fun Offset.toTaskDrawOffset(
    bounds: MapDrawBounds,
    bitmapWidth: Int,
    bitmapHeight: Int,
    rotationDeg: Float = 0f
): Offset {
    val point = Offset(
        x = bounds.left + x / bitmapWidth * bounds.width,
        y = bounds.top + y / bitmapHeight * bounds.height
    )
    return point.rotateAround(bounds.center, rotationDeg)
}

private fun Offset.rotateAround(center: Offset, rotationDeg: Float): Offset {
    if (rotationDeg == 0f) return this
    val radians = Math.toRadians(rotationDeg.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + dx * cosine - dy * sine,
        y = center.y + dx * sine + dy * cosine
    )
}

@Composable
private fun ActionButton(
    text: String,
    @DrawableRes iconRes: Int = CommonR.drawable.ic_task_pause,
    color: Color,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Text(text = text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}


