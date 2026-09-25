package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.EraseRectArea
import com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewRegionItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep2ViewModel

@Composable
fun MapScreenStep2(
    viewModel: MapScreenStep2ViewModel,
    mapId: String? = null,
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onMoveModeClick: () -> Unit = {},
    onEraseModeClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDebugInfo by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(viewModel, mapId) {
        viewModel.loadMapData(mapId)
        onDispose { viewModel.cancelPendingOperations() }
    }
    val mapImageBitmap = remember(uiState.mapImageBytes) {
        uiState.mapImageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
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
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Step2TopBar()
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    color = Color(0xFFF6F8FB),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    if (uiState.isMapLoading && mapImageBitmap == null) {
                        MapDataLoadingPage()
                    } else {
                        MapEditingPanel(
                        mapImageBitmap = mapImageBitmap,
                        toolMode = uiState.toolMode,
                        zoom = uiState.zoom,
                        pan = uiState.pan,
                        alignmentYaw = uiState.alignmentYaw,
                        mapGeo = uiState.mapGeo,
                        mapImageSize = uiState.mapImageSize,
                        bitmapDecodeSize = uiState.bitmapDecodeSize,
                        robotPose = uiState.robotPose,
                        robotWidth = uiState.robotWidth,
                        robotLength = uiState.robotLength,
                        robotFootprint = uiState.robotFootprint,
                        eraseAreas = uiState.eraseAreas,
                        previewWorkRegions = uiState.previewWorkRegions,
                        previewObstacleRegions = uiState.previewObstacleRegions,
                        previewEraseRegions = uiState.previewEraseRegions,
                        activeAreaId = uiState.activeAreaId,
                        showDebugTap = showDebugInfo,
                        onDebugTapBitmap = viewModel::reportDebugTapBitmapPixel,
                        onTransform = viewModel::updateTransform,
                        onCreateArea = viewModel::createAreaAt,
                        onConfirmArea = viewModel::confirmActiveArea,
                            onRemoveArea = viewModel::removeActiveArea
                        )
                    }
                }

                if (mapImageBitmap != null) {
                    Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        ModeToolButton(
                        selected = uiState.toolMode == EditToolMode.Gesture,
                        selectedIcon = R.drawable.ic_gesture_selected,
                        unselectedIcon = R.drawable.ic_gesture_unselcted,
                        contentDescription = "手势模式",
                        onClick = {
                            viewModel.selectGestureMode()
                            onMoveModeClick()
                        }
                        )
                        ModeToolButton(
                        selected = uiState.toolMode == EditToolMode.Erase,
                        selectedIcon = R.drawable.ic_eraser_selected,
                        unselectedIcon = R.drawable.ic_eraser_unselected,
                        contentDescription = "橡皮擦模式",
                        onClick = {
                            viewModel.selectEraseMode()
                            onEraseModeClick()
                        }
                        )
                    }
                }

                /*if (mapImageBitmap != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 14.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Debug",
                            fontSize = 12.sp,
                            color = Color(0xFF202937)
                        )
                        Switch(
                            checked = showDebugInfo,
                            onCheckedChange = {
                                showDebugInfo = it
                                if (!it) viewModel.clearDebugTap()
                            }
                        )
                    }
                }*/

                if (mapImageBitmap != null) {
                    UndoRedoDock(
                        canRedo = uiState.canRedo,
                        canUndo = uiState.canUndo,
                        onRedo = viewModel::redoStroke,
                        onUndo = {
                            viewModel.undoStroke()
                            onUndoClick()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 20.dp)
                    )
                }

                if (mapImageBitmap != null) {
                    MapRotationController(
                        alignmentYaw = uiState.alignmentYaw,
                        enabled = uiState.toolMode != EditToolMode.Erase,
                        onYawChange = viewModel::updateAlignmentYaw,
                        onAdjust = viewModel::adjustAlignmentYaw,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 120.dp)
                    )
                }

                if (showDebugInfo && mapImageBitmap != null) {
                    DebugInfoPanel(
                        totalStrokeCount = uiState.eraseAreas.size,
                        undoWindowCount = uiState.undoAreas.size,
                        redoWindowCount = uiState.redoAreas.size,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        debugTapWorldM = uiState.debugTapWorldM,
                        debugTapHint = uiState.debugTapHint,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            BottomStep2Actions(
                onCancelBuild = {
                    viewModel.onCancelBuild()
                    onCancelBuild()
                },
                onPreviousStep = onPreviousStep,
                onNextStep = {
                    // 回调由 ViewModel 在全部擦除确认后触发，不能在点击时直接跳转。
                    viewModel.sendEditedDataToEmbeddedOnNextStep(onNextStep)
                }
            )
        }
        if (uiState.isSubmitting) {
            // 提交使用点击时的编辑快照；遮罩阻止等待确认期间继续修改或重复导航。
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)).clickable {},
                contentAlignment = Alignment.Center
            ) {
                Text("正在提交地图编辑…", color = Color.White)
            }
        }
    }
}

@Composable
private fun DebugInfoPanel(
    totalStrokeCount: Int,
    undoWindowCount: Int,
    redoWindowCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    debugTapWorldM: Offset?,
    debugTapHint: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCCFFFFFF))
            .border(1.dp, Color(0x22000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = "总擦除笔数: $totalStrokeCount", fontSize = 11.sp, color = Color(0xFF202937))
        Text(text = "Undo窗口(<=20): $undoWindowCount", fontSize = 11.sp, color = Color(0xFF202937))
        Text(text = "Redo窗口(<=20): $redoWindowCount", fontSize = 11.sp, color = Color(0xFF202937))
        Text(text = "canUndo=$canUndo, canRedo=$canRedo", fontSize = 11.sp, color = Color(0xFF202937))
        if (debugTapWorldM != null) {
            val w = debugTapWorldM
            Text(
                text = "点击世界坐标: x=${"%.3f".format(w.x)} m, y=${"%.3f".format(w.y)} m",
                fontSize = 11.sp,
                color = Color(0xFF00A0E9),
                fontWeight = FontWeight.Medium
            )
        } else if (debugTapHint != null) {
            Text(text = "点击: $debugTapHint", fontSize = 11.sp, color = Color(0xFF9DA3AF))
        } else {
            Text(text = "点击地图查看世界坐标 (m)", fontSize = 11.sp, color = Color(0xFF9DA3AF))
        }
    }
}

@Composable
private fun Step2TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(1000.dp))
                .background(Color(0xFFE5EAF1))
                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(1000.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF00A0E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚙", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            StepText(text = "1.扫描地图", active = true)
            StepText(text = ">", active = true)
            StepText(text = "2.编辑地图", active = true)
            StepText(text = ">", active = false)
            StepText(text = "3.划分工作区", active = false)
            StepText(text = ">", active = false)
            StepText(text = "4.保存地图", active = false)
        }

        DeviceStatusBar()
    }
}

@Composable
private fun StepText(text: String, active: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color(0xFF00A0E9) else Color(0xFF9DA3AF)
    )
}

@Composable
private fun ModeToolButton(
    selected: Boolean,
    selectedIcon: Int,
    unselectedIcon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) Color(0xFF00A0E9) else Color.White
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = if (selected) selectedIcon else unselectedIcon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MapEditingPanel(
    mapImageBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    toolMode: EditToolMode,
    zoom: Float,
    pan: Offset,
    alignmentYaw: Float,
    mapGeo: MapGeo?,
    mapImageSize: Pair<Int, Int>?,
    bitmapDecodeSize: Pair<Int, Int>?,
    robotPose: com.sinelynx.grindingrobot.core.model.state.DevicePosePayload?,
    robotWidth: Double?,
    robotLength: Double?,
    robotFootprint: List<com.sinelynx.grindingrobot.core.data.state.AppState.FootprintPoint>,
    eraseAreas: List<EraseRectArea>,
    previewWorkRegions: List<MapPreviewRegionItem>,
    previewObstacleRegions: List<MapPreviewRegionItem>,
    previewEraseRegions: List<MapPreviewRegionItem>,
    activeAreaId: Long?,
    showDebugTap: Boolean,
    onDebugTapBitmap: (Offset) -> Unit,
    onTransform: (Float, Offset) -> Unit,
    onCreateArea: (Offset) -> Unit,
    onConfirmArea: (List<Offset>) -> Unit,
    onRemoveArea: () -> Unit
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var baseScale by remember { mutableStateOf(1f) }
    var baseOffset by remember { mutableStateOf(Offset.Zero) }

    val minZoom = 0.5f
    val maxZoom = 3f

    val mapWidthPx = mapImageBitmap?.width?.toFloat() ?: 1f
    val mapHeightPx = mapImageBitmap?.height?.toFloat() ?: 1f
    val contentCenter = Offset(mapWidthPx / 2f, mapHeightPx / 2f)

    // Screen -> 原始 Content：依次撤销 pan/zoom、适配变换和地图旋转。
    fun toContentPoint(screen: Offset): Offset {
        val pointAfterPanZoom = (screen - pan) / zoom
        val rawContent = (pointAfterPanZoom - baseOffset) / baseScale
        return rotatePoint(rawContent, contentCenter, -alignmentYaw)
    }
    // Screen -> 已对齐 Content：确认擦除区时沿用现有下发链要求的 alignedCorners。
    fun toAlignedContentPoint(screen: Offset): Offset {
        val pointAfterPanZoom = (screen - pan) / zoom
        return (pointAfterPanZoom - baseOffset) / baseScale
    }
    // 已对齐 Content -> Screen：该坐标已经包含旋转效果，因此这里不能再次应用 alignmentYaw。
    fun alignedContentToScreenPoint(alignedContent: Offset): Offset {
        val pointBeforePanZoom = Offset(
            x = alignedContent.x * baseScale + baseOffset.x,
            y = alignedContent.y * baseScale + baseOffset.y
        )
        return Offset(
            x = pointBeforePanZoom.x * zoom + pan.x,
            y = pointBeforePanZoom.y * zoom + pan.y
        )
    }
    // 原始 Content -> Screen：先绕图片中心应用地图对齐角，再应用适配和手势变换。
    fun toScreenPoint(content: Offset): Offset {
        val rotatedContent = rotatePoint(content, contentCenter, alignmentYaw)
        val p = Offset(
            x = rotatedContent.x * baseScale + baseOffset.x,
            y = rotatedContent.y * baseScale + baseOffset.y
        )
        return Offset(x = p.x * zoom + pan.x, y = p.y * zoom + pan.y)
    }
    val activeArea = eraseAreas.lastOrNull { it.id == activeAreaId }
    val initialAlignedAreaRect = activeArea?.let { area ->
        if (area.alignedCorners.size == 4) {
            boundsOfPoints(area.alignedCorners)
        } else {
            rotatedRectBounds(
                rect = Rect(area.left, area.top, area.right, area.bottom),
                center = contentCenter,
                angleDeg = alignmentYaw
            )
        }
    }
    var editableAlignedAreaRect by remember(activeArea?.id) {
        mutableStateOf(initialAlignedAreaRect)
    }
    var moveGestureStartRect by remember(activeArea?.id) { mutableStateOf<Rect?>(null) }
    var resizeGestureStartRect by remember(activeArea?.id) { mutableStateOf<Rect?>(null) }
    val currentEditableAlignedAreaRect by rememberUpdatedState(editableAlignedAreaRect)
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentAlignmentYaw by rememberUpdatedState(alignmentYaw)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)),
                    radius = 900f
                )
            )
            .pointerInput(toolMode, mapImageBitmap, viewportSize) {
                if (toolMode == EditToolMode.Gesture) {
                    val bmp = mapImageBitmap ?: return@pointerInput
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val vw = viewportSize.width.toFloat()
                        val vh = viewportSize.height.toFloat()
                        if (vw <= 0f || vh <= 0f) return@detectTransformGestures
                        val bs = minOf(vw / bmp.width, vh / bmp.height)
                        val bo = Offset(
                            x = (vw - bmp.width * bs) / 2f,
                            y = (vh - bmp.height * bs) / 2f
                        )
                        val oldZoom = currentZoom
                        val oldPan = currentPan
                        val newZoom = (oldZoom * zoomChange).coerceIn(minZoom, maxZoom)
                        val zoomRatio = if (oldZoom == 0f) 1f else newZoom / oldZoom
                        var newPan = centroid - (centroid - oldPan) * zoomRatio + panChange
                        newPan = clampMapPanToRotatedBounds(
                            pan = newPan,
                            zoom = newZoom,
                            canvasW = viewportSize.width,
                            canvasH = viewportSize.height,
                            baseOffset = bo,
                            baseScale = bs,
                            imgW = bmp.width.toFloat(),
                            imgH = bmp.height.toFloat(),
                            alignmentYaw = currentAlignmentYaw
                        )
                        onTransform(newZoom, newPan)
                    }
                }
            }
            .pointerInput(showDebugTap, toolMode, mapImageBitmap, pan, zoom, baseScale, baseOffset) {
                val bmp = mapImageBitmap ?: return@pointerInput
                if (!showDebugTap && toolMode != EditToolMode.Erase) return@pointerInput
                detectTapGestures { tap ->
                    val content = toContentPoint(tap)
                    if (showDebugTap) {
                        onDebugTapBitmap(content)
                    }
                    if (toolMode == EditToolMode.Erase) {
                        onCreateArea(content)
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .align(Alignment.Center)
                .pointerInput(Unit) {}
                .background(Color.Transparent)
                .then(
                    Modifier
                )
        ) {
            if (mapImageBitmap == null) return@Canvas

            baseScale = minOf(
                size.width / mapImageBitmap.width.toFloat(),
                size.height / mapImageBitmap.height.toFloat()
            )
            baseOffset = Offset(
                (size.width - mapImageBitmap.width * baseScale) / 2f,
                (size.height - mapImageBitmap.height * baseScale) / 2f
            )

            withTransform({
                translate(left = pan.x, top = pan.y)
                scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
                translate(left = baseOffset.x, top = baseOffset.y)
                scale(scaleX = baseScale, scaleY = baseScale, pivot = Offset.Zero)
                // 必须与 toScreenPoint 使用相同的中心和正角度，保证图片与交互坐标重合。
                rotate(degrees = alignmentYaw, pivot = contentCenter)
            }) {
                drawImage(mapImageBitmap)
                val geo = mapGeo
                val logicalSize = mapImageSize
                val decodedSize = bitmapDecodeSize ?: (mapImageBitmap.width to mapImageBitmap.height)
                if (geo != null && logicalSize != null) {
                    val mapper: (com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem) -> Offset? = { point ->
                        worldPointToBitmapOffset(point, geo, logicalSize, decodedSize)
                    }
                    val contentScale = baseScale * zoom
                    drawMapRegionOverlays(
                        regions = previewEraseRegions,
                        kind = MapRegionOverlayKind.Erase,
                        pointMapper = mapper,
                        contentScale = contentScale
                    )
                    drawMapRegionOverlays(
                        regions = previewWorkRegions,
                        kind = MapRegionOverlayKind.Work,
                        pointMapper = mapper,
                        contentScale = contentScale,
                        workFillColor = Color.Transparent
                    )
                    drawMapRegionOverlays(
                        regions = previewObstacleRegions,
                        kind = MapRegionOverlayKind.Obstacle,
                        pointMapper = mapper,
                        contentScale = contentScale
                    )
                }
            }

            fun screenPathFor(area: EraseRectArea): Path {
                val corners = if (area.alignedCorners.size == 4) {
                    area.alignedCorners.map(::alignedContentToScreenPoint)
                } else {
                    listOf(
                        Offset(area.left, area.top),
                        Offset(area.right, area.top),
                        Offset(area.right, area.bottom),
                        Offset(area.left, area.bottom)
                    ).map(::toScreenPoint)
                }
                return Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    lineTo(corners[3].x, corners[3].y)
                    close()
                }
            }

            eraseAreas.forEach { area ->
                if (area.confirmed) {
                    drawPath(
                        path = screenPathFor(area),
                        color = Color.Transparent,
                        blendMode = BlendMode.Clear
                    )
                }
            }

            val gridSpacing = 40.dp.toPx()
            val gridStrokeWidth = 1.dp.toPx()
            val gridPathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx())
            )
            val gridColor = Color(0x4D00A0E9)

            var gridX = 0f
            while (gridX <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(gridX, 0f),
                    end = Offset(gridX, size.height),
                    strokeWidth = gridStrokeWidth,
                    pathEffect = gridPathEffect
                )
                gridX += gridSpacing
            }

            var gridY = 0f
            while (gridY <= size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gridY),
                    end = Offset(size.width, gridY),
                    strokeWidth = gridStrokeWidth,
                    pathEffect = gridPathEffect
                )
                gridY += gridSpacing
            }

            if (toolMode == EditToolMode.Erase && editableAlignedAreaRect != null) {
                val activeScreenBounds = requireNotNull(
                    boundsOfPoints(
                        rectCorners(editableAlignedAreaRect!!).map(::alignedContentToScreenPoint)
                    )
                )
                val activePath = Path().apply {
                    moveTo(activeScreenBounds.left, activeScreenBounds.top)
                    lineTo(activeScreenBounds.right, activeScreenBounds.top)
                    lineTo(activeScreenBounds.right, activeScreenBounds.bottom)
                    lineTo(activeScreenBounds.left, activeScreenBounds.bottom)
                    close()
                }
                drawPath(
                    path = activePath,
                    color = Color(0x5539B54A)
                )
                drawPath(
                    path = activePath,
                    color = Color(0xFF3AB54A),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12.dp.toPx(), 12.dp.toPx())
                        )
                    )
                )
            }
        }

        robotPoseToBitmapPoint(
            pose = robotPose,
            geo = mapGeo,
            mapImageSize = mapImageSize,
            bitmapSize = bitmapDecodeSize ?: mapImageBitmap?.let { it.width to it.height }
        )?.let { bitmapPoint ->
            val geo = mapGeo
            val logicalSize = mapImageSize ?: geo?.let { it.mapWidth to it.mapHeight }
            val decodedSize = bitmapDecodeSize ?: mapImageBitmap?.let { it.width to it.height }
            if (robotFootprint.size >= 3 && geo != null && geo.resolution > 0f &&
                logicalSize != null && decodedSize != null && logicalSize.first > 0 && logicalSize.second > 0) {
                FootprintRobotMarker(
                    center = toScreenPoint(bitmapPoint),
                    headingDeg = (robotPose?.headingDeg ?: 0f) - geo.headingDeg - alignmentYaw,
                    footprint = robotFootprint,
                    pixelsPerMeterX = baseScale * zoom * decodedSize.first / logicalSize.first / geo.resolution,
                    pixelsPerMeterY = baseScale * zoom * decodedSize.second / logicalSize.second / geo.resolution
                )
            } else {
            val (drawWidth, drawLength) = remember(zoom, robotWidth, robotLength) {
                val defaultSize = 40.dp
                if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
                    val baseWidth: androidx.compose.ui.unit.Dp
                    val baseLength: androidx.compose.ui.unit.Dp
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
            RobotPoseIcon(
                center = toScreenPoint(bitmapPoint),
                // 图标中心已随地图旋转；朝向减去地图角，避免视觉上重复旋转。
                headingDeg = (robotPose?.headingDeg ?: 0f) - alignmentYaw,
                width = drawWidth,
                length = drawLength
            )
            }
        }

        if (
            toolMode == EditToolMode.Erase &&
            activeArea != null &&
            editableAlignedAreaRect != null &&
            mapImageBitmap != null
        ) {
            val density = LocalDensity.current
            val activeAlignedRect = editableAlignedAreaRect!!
            val mapAlignedBounds = rotatedMapBounds(mapWidthPx, mapHeightPx, alignmentYaw)
            val currentMapAlignedBounds by rememberUpdatedState(mapAlignedBounds)
            val activeScreenBounds = requireNotNull(
                boundsOfPoints(rectCorners(activeAlignedRect).map(::alignedContentToScreenPoint))
            )
            val handleHalfPx = with(density) { 16.dp.toPx() }

            // 命中层在一次拖动期间固定在起始位置；擦除框预览独立更新，避免局部坐标系
            // 随组件移动而抵消连续 dragAmount。
            val moveHitRect = moveGestureStartRect ?: activeAlignedRect
            val moveHitScreenBounds = requireNotNull(
                boundsOfPoints(rectCorners(moveHitRect).map(::alignedContentToScreenPoint))
            )
            val moveHitWidthDp = with(density) {
                moveHitScreenBounds.width.coerceAtLeast(1f).toDp()
            }
            val moveHitHeightDp = with(density) {
                moveHitScreenBounds.height.coerceAtLeast(1f).toDp()
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            moveHitScreenBounds.left.toInt(),
                            moveHitScreenBounds.top.toInt()
                        )
                    }
                    .size(width = moveHitWidthDp, height = moveHitHeightDp)
                    .pointerInput(
                        activeArea.id,
                        zoom,
                        baseScale,
                        mapAlignedBounds
                    ) {
                        var accumulatedScreenDelta = Offset.Zero
                        var gestureStartRect: Rect? = null
                        detectDragGestures(
                            onDragStart = {
                                val startRect = currentEditableAlignedAreaRect
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = startRect
                                moveGestureStartRect = startRect
                            },
                            onDragEnd = {
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = null
                                moveGestureStartRect = null
                            },
                            onDragCancel = {
                                gestureStartRect?.let { editableAlignedAreaRect = it }
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = null
                                moveGestureStartRect = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val startRect = gestureStartRect
                            if (startRect != null) {
                                accumulatedScreenDelta += dragAmount
                                val requestedAlignedDelta = screenDeltaToAlignedContent(
                                    screenDelta = accumulatedScreenDelta,
                                    zoom = zoom,
                                    baseScale = baseScale
                                )
                                editableAlignedAreaRect = moveEraseRectWithinBounds(
                                    rect = startRect,
                                    requestedDelta = requestedAlignedDelta,
                                    bounds = currentMapAlignedBounds
                                )
                            }
                        }
                    }
            )

            HandleButton(
                symbol = "✕",
                bg = Color(0xFFE53935),
                onClick = onRemoveArea,
                modifier = Modifier.offset {
                    IntOffset(
                        (activeScreenBounds.left - handleHalfPx).toInt(),
                        (activeScreenBounds.top - handleHalfPx).toInt()
                    )
                }
            )
            HandleButton(
                symbol = "✓",
                bg = Color(0xFF3AB54A),
                onClick = {
                    onConfirmArea(
                        rectCorners(activeScreenBounds).map(::toAlignedContentPoint)
                    )
                },
                modifier = Modifier.offset {
                    IntOffset(
                        (activeScreenBounds.right - handleHalfPx).toInt(),
                        (activeScreenBounds.top - handleHalfPx).toInt()
                    )
                }
            )

            val minWidthPx = mapImageSize?.first
                ?.takeIf { it > 0 }
                ?.let { 24f * mapWidthPx / it.toFloat() }
                ?: 24f
            val minHeightPx = mapImageSize?.second
                ?.takeIf { it > 0 }
                ?.let { 24f * mapHeightPx / it.toFloat() }
                ?: 24f
            val resizeHitRect = resizeGestureStartRect ?: activeAlignedRect
            val resizeHitScreenCorner = alignedContentToScreenPoint(
                Offset(resizeHitRect.right, resizeHitRect.bottom)
            )
            val activeResizeScreenCorner = alignedContentToScreenPoint(
                Offset(activeAlignedRect.right, activeAlignedRect.bottom)
            )
            val resizeVisualDelta = activeResizeScreenCorner - resizeHitScreenCorner
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (resizeHitScreenCorner.x - handleHalfPx).toInt(),
                            (resizeHitScreenCorner.y - handleHalfPx).toInt()
                        )
                    }
                    .size(32.dp)
                    .pointerInput(
                        activeArea.id,
                        zoom,
                        baseScale,
                        mapAlignedBounds,
                        minWidthPx,
                        minHeightPx
                    ) {
                        var accumulatedScreenDelta = Offset.Zero
                        var gestureStartRect: Rect? = null
                        detectDragGestures(
                            onDragStart = {
                                val startRect = currentEditableAlignedAreaRect
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = startRect
                                resizeGestureStartRect = startRect
                            },
                            onDragEnd = {
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = null
                                resizeGestureStartRect = null
                            },
                            onDragCancel = {
                                gestureStartRect?.let { editableAlignedAreaRect = it }
                                accumulatedScreenDelta = Offset.Zero
                                gestureStartRect = null
                                resizeGestureStartRect = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val startRect = gestureStartRect
                            if (startRect != null) {
                                accumulatedScreenDelta += dragAmount
                                val requestedAlignedDelta = screenDeltaToAlignedContent(
                                    screenDelta = accumulatedScreenDelta,
                                    zoom = zoom,
                                    baseScale = baseScale
                                )
                                editableAlignedAreaRect = resizeEraseRectBottomRight(
                                    rect = startRect,
                                    requestedDelta = requestedAlignedDelta,
                                    bounds = currentMapAlignedBounds,
                                    minimumWidth = minWidthPx,
                                    minimumHeight = minHeightPx
                                )
                            }
                        }
                    }
            ) {
                HandleButton(
                    symbol = "↘",
                    bg = Color(0xFF2D9CDB),
                    onClick = {},
                    modifier = Modifier.graphicsLayer {
                        translationX = resizeVisualDelta.x
                        translationY = resizeVisualDelta.y
                    }
                )
            }
        }
    }
}

@Composable
private fun HandleButton(
    symbol: String,
    bg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UndoRedoDock(
    canRedo: Boolean,
    canUndo: Boolean,
    onRedo: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0x14000000), RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DockIconButton(
            icon = "↪",
            enabled = canRedo,
            onClick = onRedo,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(1.dp)
                .fillMaxWidth()
                .background(Color(0x14000000))
        )
        DockIconButton(
            icon = "↩",
            enabled = canUndo,
            onClick = onUndo,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun DockIconButton(
    icon: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            color = if (enabled) Color(0xFF202937) else Color(0xFFBCC3CF),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BottomStep2Actions(
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepActionButton(
            text = "✕  取消建图",
            background = Color(0xFFD82B2A),
            contentColor = Color.White,
            onClick = onCancelBuild
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepActionButton(
                text = "◀  上一步",
                background = Color(0xFFF6F8FB),
                contentColor = Color(0xFF202937),
                onClick = onPreviousStep
            )
            StepActionButton(
                text = "▶  下一步",
                background = Color(0xFF00A0E9),
                contentColor = Color.White,
                onClick = onNextStep
            )
        }
    }
}

@Composable
private fun StepActionButton(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0x1A000000),
                    blurRadius = 4f
                )
            )
        )
    }
}

/**
 * Step2 地图对齐角控制器。
 * alignmentYaw 使用 [-180, 180] 度；向上拖动为正方向，完整轨道对应 360 度。
 */
@Composable
private fun MapRotationController(
    alignmentYaw: Float,
    enabled: Boolean,
    onYawChange: (Float) -> Unit,
    onAdjust: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYaw by rememberUpdatedState(alignmentYaw)

    Column(
        modifier = modifier
            .width(48.dp)
            .height(200.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
            }
            .pointerInput(enabled) {
                if (!enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0x14000000), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // + 号微调按钮，每次 +1 度
        DockIconButton(
            icon = "+",
            enabled = enabled,
            onClick = { onAdjust(1f) }
        )

        // 纵向拖动滑块的滑动区域
        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // 滑轨中线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFE5EAF1))
            )

            var trackHeight by remember { mutableStateOf(1f) }
            var dragStartYaw by remember { mutableStateOf(0f) }
            var accumulatedDragY by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { trackHeight = it.height.toFloat() }
                    .pointerInput(enabled, trackHeight) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                dragStartYaw = currentYaw
                                accumulatedDragY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            accumulatedDragY += dragAmount.y
                            // y 坐标变化量转换成角度。向上是负值，故取反使向上滑动为正旋转。
                            // 拖拽比例 = -accumulatedDragY / trackHeight
                            // 对应的角度变动量 = 比例 * 360f
                            val deltaYaw = -(accumulatedDragY / trackHeight) * 360f
                            onYawChange((dragStartYaw + deltaYaw).coerceIn(-180f, 180f))
                        }
                    }
            ) {
                // 计算滑块位置
                // alignmentYaw: -180 到 180
                // 对应 ratio: 0 到 1
                val thumbHeight = 32.dp
                val density = LocalDensity.current
                val thumbHeightPx = with(density) { thumbHeight.toPx() }

                val ratio = (alignmentYaw + 180f) / 360f
                val offsetPx = (1f - ratio) * (trackHeight - thumbHeightPx)
                val offsetDp = with(density) { offsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = offsetDp)
                        .size(width = 20.dp, height = thumbHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF202937))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 1.5.dp)
                                    .background(Color(0x80FFFFFF))
                            )
                        }
                    }
                }
            }
        }

        // - 号微调按钮，每次 -1 度
        DockIconButton(
            icon = "-",
            enabled = enabled,
            onClick = { onAdjust(-1f) }
        )
    }
}


