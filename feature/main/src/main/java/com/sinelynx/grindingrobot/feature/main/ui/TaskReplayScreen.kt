package com.sinelynx.grindingrobot.feature.main.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskReplayUiState
import com.sinelynx.grindingrobot.feature.map.ui.RobotPoseIcon
import com.sinelynx.grindingrobot.feature.map.ui.drawFittedTaskPath
import com.sinelynx.grindingrobot.feature.map.ui.taskBitmapPointToFittedPoint
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskReplayScreen(
    uiState: TaskReplayUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val points = uiState.points
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    val fraction = remember(currentIndex, points) {
        if (points.size > 1) {
            currentIndex.toFloat() / (points.size - 1)
        } else {
            0f
        }
    }
    var speedFactor by remember { mutableStateOf(1) }
    var isSpeedMenuExpanded by remember { mutableStateOf(false) }
    var mapViewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val mapFrame = uiState.mapFrame
    val mapBitmap = remember(mapFrame) {
        mapFrame?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val mapGeo = remember(mapFrame) {
        mapFrame?.let { frame ->
            MapGeo(
                mapWidth = frame.mapWidth,
                mapHeight = frame.mapHeight,
                resolution = frame.resolution,
                originX = frame.originX,
                originY = frame.originY,
                headingDeg = frame.headingDeg,
                mapVersion = frame.mapVersion,
                alignmentYawDeg = frame.alignmentYawDeg,
                rotationAlignmentDeltaDeg = frame.rotationAlignmentDeltaDeg
            )
        }
    }
    val totalMapRotation = mapGeo?.let {
        it.alignmentYawDeg + it.rotationAlignmentDeltaDeg
    } ?: 0f

    LaunchedEffect(points.size) {
        currentIndex = currentIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
        if (points.isEmpty()) isPlaying = false
    }

    // 播放动效定时控制器
    LaunchedEffect(isPlaying, speedFactor, points) {
        if (isPlaying && points.isNotEmpty()) {
            while (currentIndex < points.size - 1) {
                val delayTime = (250L / speedFactor).coerceAtLeast(16L)
                kotlinx.coroutines.delay(delayTime)
                currentIndex++
            }
            currentIndex = 0
            isPlaying = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 顶栏 (任务回溯标题与电量栏等)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "任务回溯",
                    fontSize = 20.sp,
                    color = Color(0xFF202937),
                    fontWeight = FontWeight.Bold
                )
                DeviceStatusBar()
            }

            // 2. 地图轨迹画布区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { mapViewportSize = it }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF6F8FB))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bitmap = mapBitmap ?: return@Canvas
                    val frame = mapFrame ?: return@Canvas
                    val geo = mapGeo ?: return@Canvas
                    val bitmapSize = uiState.bitmapSize ?: (bitmap.width to bitmap.height)
                    val logicalSize = frame.mapWidth to frame.mapHeight
                    val fitScale = minOf(
                        size.width / bitmap.width.toFloat(),
                        size.height / bitmap.height.toFloat()
                    )
                    val fitOffset = Offset(
                        x = (size.width - bitmap.width * fitScale) / 2f,
                        y = (size.height - bitmap.height * fitScale) / 2f
                    )

                    // 与 Step3 相同：底图和所有地图附着几何共用适配及总旋转。
                    withTransform({
                        translate(fitOffset.x, fitOffset.y)
                        scale(fitScale, fitScale, pivot = Offset.Zero)
                        rotate(
                            degrees = totalMapRotation,
                            pivot = Offset(bitmap.width / 2f, bitmap.height / 2f)
                        )
                    }) {
                        drawImage(bitmap)
                    }

                    // 与 Step4 一致，完整规划路径位于按回放进度增长的实际轨迹下方。
                    uiState.plannedPath?.let { path ->
                        drawFittedTaskPath(
                            path = path,
                            geo = geo,
                            mapImageSize = logicalSize,
                            bitmapSize = bitmapSize,
                            mapRotationDeg = totalMapRotation,
                            colorByScope = true
                        )
                    }

                    val routeWidthPx = trajectoryMetersToScreenPx(
                        meters = uiState.robotWidth,
                        geo = geo,
                        mapImageSize = logicalSize,
                        bitmapSize = bitmapSize,
                        canvasSize = size
                    )
                    val visibleSegmentCount = visibleTrajectorySegmentCount(
                        pointCount = points.size,
                        currentIndex = currentIndex
                    )
                    if (routeWidthPx != null && visibleSegmentCount > 0) {
                        for (index in 0 until visibleSegmentCount) {
                            val first = points[index]
                            val second = points[index + 1]
                            val start = trajectoryPointToBitmapOffset(first, geo, logicalSize, bitmapSize)
                                ?.let { taskBitmapPointToFittedPoint(it, size, bitmapSize, totalMapRotation) }
                            val end = trajectoryPointToBitmapOffset(second, geo, logicalSize, bitmapSize)
                                ?.let { taskBitmapPointToFittedPoint(it, size, bitmapSize, totalMapRotation) }
                            if (start == null || end == null) continue
                            drawLine(
                                color = Color(0xFF00A0E9).copy(alpha = 0.5f),
                                start = start,
                                end = end,
                                strokeWidth = routeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                val currentPoint = points.getOrNull(currentIndex)
                val frame = mapFrame
                val geo = mapGeo
                val bitmapSize = uiState.bitmapSize
                val viewportCanvasSize = Size(
                    mapViewportSize.width.toFloat(),
                    mapViewportSize.height.toFloat()
                )
                val robotCenter = if (
                    currentPoint != null && frame != null && geo != null && bitmapSize != null
                ) {
                    trajectoryPointToBitmapOffset(
                        currentPoint,
                        geo,
                        frame.mapWidth to frame.mapHeight,
                        bitmapSize
                    )?.let {
                        taskBitmapPointToFittedPoint(
                            it,
                            viewportCanvasSize,
                            bitmapSize,
                            totalMapRotation
                        )
                    }
                } else null
                val robotWidthPx = if (frame != null && geo != null && bitmapSize != null) {
                    trajectoryMetersToScreenPx(
                        uiState.robotWidth,
                        geo,
                        frame.mapWidth to frame.mapHeight,
                        bitmapSize,
                        viewportCanvasSize
                    )
                } else null
                val robotLengthPx = if (frame != null && geo != null && bitmapSize != null) {
                    trajectoryMetersToScreenPx(
                        uiState.robotLength,
                        geo,
                        frame.mapWidth to frame.mapHeight,
                        bitmapSize,
                        viewportCanvasSize
                    )
                } else null
                if (
                    currentPoint != null && robotCenter != null &&
                    robotWidthPx != null && robotLengthPx != null
                ) {
                    RobotPoseIcon(
                        center = robotCenter,
                        headingDeg = currentPoint.headingDeg - totalMapRotation,
                        width = with(density) { robotWidthPx.toDp() },
                        length = with(density) { robotLengthPx.toDp() }
                    )
                }

                val loadingText = when {
                    uiState.isMapLoading -> "地图加载中..."
                    uiState.isPathLoading -> "规划路径加载中..."
                    uiState.isRobotSettingsLoading -> "小车尺寸加载中..."
                    else -> null
                }
                val displayError = uiState.mapError ?: uiState.pathError ?:
                    uiState.robotSettingsError ?: uiState.errorMessage
                if (loadingText != null || displayError != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xEFFFFFFF))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (loadingText != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF00A0E9)
                            )
                        }
                        Text(
                            text = displayError ?: loadingText.orEmpty(),
                            color = if (displayError != null) Color(0xFFD82B2A) else Color(0xFF202937),
                            fontSize = 14.sp
                        )
                    }
                }
                // 右下角倍速选择浮动列表
                if (isSpeedMenuExpanded) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 85.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .width(64.dp)
                    ) {
                        listOf(1, 2, 5, 10, 20).forEach { factor ->
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    speedFactor = factor
                                    isSpeedMenuExpanded = false
                                }
                                .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${factor}X",
                                    color = if (speedFactor == factor) Color(0xFF00A0E9) else Color(
                                        0xFF202937
                                    ),
                                    fontSize = 14.sp,
                                    fontWeight = if (speedFactor == factor) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // 3. 底部播放控制条 (浮动胶囊)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A. 返回按钮
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 56.dp)
                        .clip(RoundedCornerShape(1000.dp))
                        .background(Color(0xFFF6F8FB))
                        .clickable(onClick = { onBack() })
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◀  返回",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF202937),
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0x1A000000),
                                blurRadius = 4f
                            )
                        )
                    )
                }

                // B. 进度条 Slider
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { currentIndex = it.toInt() },
                    valueRange = 0f..((points.size - 1).coerceAtLeast(1).toFloat()),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00A0E9),
                        activeTrackColor = Color(0xFF00A0E9),
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    ),
                    thumb = { _ ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.White, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFF00A0E9), shape = CircleShape)
                            )
                        }
                    },
                    track = { _ ->
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                        ) {
                            val width = size.width
                            val height = size.height
                            val thumbRadius = 21.dp.toPx()

                            val thumbCenterX = thumbRadius + fraction * (width - thumbRadius * 2)

                            // 1. 绘制背景灰色轨道 (从 0 延伸到整个宽度 width)
                            drawRoundRect(
                                color = Color(0xFFE2E8F0),
                                topLeft = Offset(0f, (height - 16.dp.toPx()) / 2),
                                size = Size(width, 16.dp.toPx()),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )

                            // 2. 绘制激活蓝色轨道 (从 0 延伸到当前滑块中心)
                            if (thumbCenterX > 0) {
                                drawRoundRect(
                                    color = Color(0xFF00A0E9),
                                    topLeft = Offset(0f, (height - 16.dp.toPx()) / 2),
                                    size = Size(thumbCenterX, 16.dp.toPx()),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                )
                            }
                        }
                    }
                )

                // C. 倍速显示药丸
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White)
                        .clickable { isSpeedMenuExpanded = !isSpeedMenuExpanded }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        text = "${speedFactor}X",
                        color = Color(0xFF202937),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // D. 播放/暂停大蓝色按钮
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A0E9))
                        .clickable {
                            if (points.isNotEmpty()) {
                                if (isPlaying) {
                                    isPlaying = false
                                } else {
                                    if (currentIndex >= points.lastIndex) {
                                        currentIndex = 0
                                    }
                                    isPlaying = true
                                }
                            }
                        }, contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        // 自定义绘制高保真白色暂停Icon (两条白色垂直线条)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .background(Color.White)
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .background(Color.White)
                            )
                        }
                    } else {
                        Text(
                            text = "▶",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFFFF)
                        )
                    }
                }
            }
        }
    }
}
