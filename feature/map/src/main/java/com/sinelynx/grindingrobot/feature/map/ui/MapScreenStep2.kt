package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
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
    val mapImageBitmap = rememberWorkflowBitmap(uiState.mapImageBytes)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(12.dp)
    ) {
        val sideWidth = workflowSideWidth(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            MapWorkflowHeader(2, viewModel::emergencyStop)
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = sideWidth + 8.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("workflow_map"),
                    color = Color(0xFFF6F8FB),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    if (uiState.isMapLoading && mapImageBitmap == null) {
                        MapDataLoadingPage()
                    } else {
                        MapEditingPanel(
                        mapId = mapId, mapImageBitmap = mapImageBitmap,
                        toolMode = uiState.toolMode,
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
                        onCreateArea = viewModel::createAreaAt,
                        onCreateRectangle = viewModel::createAreaFromAlignedCorners,
                        onBrushStroke = viewModel::addBrushStroke,
                        onConfirmArea = viewModel::confirmActiveArea,
                            onRemoveArea = viewModel::removeActiveArea
                        )
                    }
                }

                if (mapImageBitmap != null) {
                    Column(
                        modifier = Modifier.align(Alignment.TopStart).width(sideWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        listOf(EditToolMode.Gesture to "移动", EditToolMode.Erase to "矩形擦除",
                            EditToolMode.Brush to "涂抹擦除").forEach { (mode, title) ->
                            TextButton(onClick = {
                                when (mode) {
                                    EditToolMode.Gesture -> { viewModel.selectGestureMode(); onMoveModeClick() }
                                    EditToolMode.Erase -> { viewModel.selectEraseMode(); onEraseModeClick() }
                                    EditToolMode.Brush -> viewModel.selectBrushMode()
                                }
                            }) { Text(title, color = if (uiState.toolMode == mode) Color(0xFF00A0E9) else Color(0xFF647080)) }
                        }
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
                        enabled = true,
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

            Spacer(modifier = Modifier.height(8.dp))
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
                Text("正在提交擦除区域 ${uiState.submittedRegions}/${uiState.submittingRegions}…", color = Color.White)
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
internal fun ModeToolButton(
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
internal fun MapEditingPanel(
    mapId: String?,
    mapImageBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    toolMode: EditToolMode,
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
    onCreateArea: (Offset) -> Unit,
    onCreateRectangle: (List<Offset>) -> Unit = {},
    onBrushStroke: (List<List<Offset>>) -> Unit = {},
    onConfirmArea: (List<Offset>) -> Unit,
    onRemoveArea: () -> Unit
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val workflow = rememberWorkflowViewport(mapId)
    val imageSize = mapImageBitmap?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    val transform = workflow.view.toTransform(viewportSize, imageSize, alignmentYaw)
    val zoom = transform.scale
    val pan = transform.offset
    val mapWidthPx = imageSize.width.toFloat().coerceAtLeast(1f)
    val mapHeightPx = imageSize.height.toFloat().coerceAtLeast(1f)
    val contentCenter = Offset(mapWidthPx / 2, mapHeightPx / 2)
    val baseScale = if (viewportSize.width > 0 && viewportSize.height > 0)
        minOf(viewportSize.width / mapWidthPx, viewportSize.height / mapHeightPx) else 1f
    val baseOffset = Offset((viewportSize.width - mapWidthPx * baseScale) / 2,
        (viewportSize.height - mapHeightPx * baseScale) / 2)
    fun toAlignedContentPoint(screen: Offset) = ((screen - pan) / zoom - baseOffset) / baseScale
    fun toContentPoint(screen: Offset) = rotatePoint(toAlignedContentPoint(screen), contentCenter, -alignmentYaw)
    fun alignedContentToScreenPoint(point: Offset) = (point * baseScale + baseOffset) * zoom + pan
    fun toScreenPoint(point: Offset) = alignedContentToScreenPoint(rotatePoint(point, contentCenter, alignmentYaw))
    val activeArea = eraseAreas.lastOrNull { it.id == activeAreaId }
    var editableAlignedAreaRect by remember(activeArea?.id) { mutableStateOf(activeArea?.let {
        if (it.alignedCorners.size == 4) boundsOfPoints(it.alignedCorners)
        else rotatedRectBounds(Rect(it.left, it.top, it.right, it.bottom), contentCenter, alignmentYaw)
    }) }
    var brushSizeDp by remember { mutableStateOf(32f) }
    var liveBrush by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var brushCursor by remember(mapImageBitmap, toolMode) { mutableStateOf<Offset?>(null) }
    val bounds = Rect(0f, 0f, mapWidthPx, mapHeightPx)
    val density = LocalDensity.current
    val regionCache = remember { MapRegionPathCache() }
    // Stable bitmap-space paths: viewport changes affect only the Canvas matrix.
    val confirmedPath = remember(eraseAreas, alignmentYaw, imageSize) {
        Path().apply {
            eraseAreas.filter { it.confirmed }.forEach { area ->
                val polygons = area.bitmapPolygons.ifEmpty {
                    listOf(if (area.alignedCorners.size == 4) area.alignedCorners.map {
                        rotatePoint(it, contentCenter, -alignmentYaw)
                    } else rectCorners(Rect(area.left, area.top, area.right, area.bottom)))
                }
                polygons.forEach { addPolygon(it) }
            }
        }
    }
    val liveBrushPath = remember(liveBrush) { Path().apply { liveBrush.forEach { addPolygon(it) } } }
    val latest by rememberUpdatedState(EraseEditorCallbacks(
        mode = toolMode, activeRect = editableAlignedAreaRect,
        screenToAligned = ::toAlignedContentPoint, screenToBitmap = ::toContentPoint,
        onRect = { editableAlignedAreaRect = it },
        onNewRectangle = { onCreateRectangle(rectCorners(it)) },
        onCancelNewRectangle = { editableAlignedAreaRect = null }, onTap = { point ->
            val content = toContentPoint(point)
            if (showDebugTap) onDebugTapBitmap(content)
            if (toolMode == EditToolMode.Erase && activeAreaId == null && bounds.contains(content)) onCreateArea(content)
        }, onTransform = { centroid, delta, factor -> workflow.gesture(viewportSize, imageSize, alignmentYaw, centroid, delta, factor) },
        onBrushPreview = { liveBrush = it }, onBrush = onBrushStroke,
        bounds = bounds, alignedBounds = rotatedMapBounds(mapWidthPx, mapHeightPx, alignmentYaw),
        totalScale = baseScale * zoom, brushRadius = with(density) { brushSizeDp.dp.toPx() } / (2 * baseScale * zoom),
        handleRadius = with(density) { 24.dp.toPx() } / (baseScale * zoom),
        onBrushCursor = { brushCursor = it }
    ))
    Box(Modifier.fillMaxSize().onSizeChanged { viewportSize = it }.clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFDDE5EE))
        .workflowViewportSemantics(workflow, viewportSize, imageSize, alignmentYaw)
        .pointerInput(mapImageBitmap, toolMode) { detectEraseEditorGestures { latest } }) {
        Canvas(Modifier.fillMaxSize()) {
            if (mapImageBitmap == null) return@Canvas
            withTransform({
                translate(pan.x, pan.y); scale(zoom, zoom, Offset.Zero)
                translate(baseOffset.x, baseOffset.y); scale(baseScale, baseScale, Offset.Zero)
                rotate(alignmentYaw, contentCenter)
            }) {
                drawImage(mapImageBitmap)
                val geo = mapGeo
                val logical = mapImageSize
                val decoded = bitmapDecodeSize ?: (imageSize.width to imageSize.height)
                if (geo != null && logical != null) {
                    val mapper: (com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem) -> Offset? = {
                        worldPointToBitmapOffset(it, geo, logical, decoded)
                    }
                    val projectionKey = Triple(geo, logical, decoded)
                    drawMapRegionOverlays(previewEraseRegions, MapRegionOverlayKind.Erase, mapper,
                        baseScale * zoom, drawBorder = false, cache = regionCache, projectionKey = projectionKey)
                    drawMapRegionOverlays(previewWorkRegions, MapRegionOverlayKind.Work, mapper,
                        baseScale * zoom, workFillColor = Color.Transparent, cache = regionCache, projectionKey = projectionKey)
                    drawMapRegionOverlays(previewObstacleRegions, MapRegionOverlayKind.Obstacle, mapper,
                        baseScale * zoom, cache = regionCache, projectionKey = projectionKey)
                }
                clipRect(0f, 0f, mapWidthPx, mapHeightPx) {
                    drawPath(confirmedPath, Color.White)
                    drawPath(liveBrushPath, Color.White)
                }
            }
            // Screen-fixed alignment ruler stays horizontal/vertical while the map rotates.
            val ruler = Path().apply {
                val spacing = 40.dp.toPx()
                var x = size.width / 2 % spacing
                while (x <= size.width) { moveTo(x, 0f); lineTo(x, size.height); x += spacing }
                var y = size.height / 2 % spacing
                while (y <= size.height) { moveTo(0f, y); lineTo(size.width, y); y += spacing }
            }
            drawPath(ruler, Color(0x6600A0E9), style = Stroke(1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
            if (toolMode == EditToolMode.Erase) editableAlignedAreaRect?.let { rect ->
                val topLeft = alignedContentToScreenPoint(rect.topLeft)
                val sizePx = androidx.compose.ui.geometry.Size(rect.width * baseScale * zoom, rect.height * baseScale * zoom)
                drawRect(Color(0x5539B54A), topLeft, sizePx)
                drawRect(Color(0xFF3AB54A), topLeft, sizePx,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 12.dp.toPx()))))
                val center = alignedContentToScreenPoint(rect.bottomRight)
                val r = 17.dp.toPx()
                drawCircle(Color(0xFF2D9CDB), r, center)
                // Two opposite arrowheads, like the supplied resize handle.
                val arrow = Path().apply {
                    moveTo(center.x - r * .55f, center.y - r * .55f)
                    lineTo(center.x + r * .55f, center.y + r * .55f)
                    moveTo(center.x - r * .55f, center.y - r * .12f)
                    lineTo(center.x - r * .55f, center.y - r * .55f)
                    lineTo(center.x - r * .12f, center.y - r * .55f)
                    moveTo(center.x + r * .55f, center.y + r * .12f)
                    lineTo(center.x + r * .55f, center.y + r * .55f)
                    lineTo(center.x + r * .12f, center.y + r * .55f)
                }
                drawPath(arrow, Color.White, style = Stroke(3.dp.toPx()))
            }
            if (toolMode == EditToolMode.Brush) brushCursor?.let { center ->
                // The eraser diameter is fixed in screen dp; draw outside the map matrix.
                val radius = brushSizeDp.dp.toPx() / 2
                drawCircle(Color.White, radius, center, style = Stroke(3.dp.toPx()))
                drawCircle(Color(0xFF00A0E9), radius, center, style = Stroke(1.5.dp.toPx()))
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

        if (toolMode == EditToolMode.Erase) editableAlignedAreaRect?.let { rect ->
            fun buttonPosition(point: Offset): IntOffset {
                val half = with(density) { 16.dp.toPx() }
                val screen = alignedContentToScreenPoint(point)
                return IntOffset((screen.x - half).coerceIn(0f, (viewportSize.width - 2 * half).coerceAtLeast(0f)).toInt(),
                    (screen.y - half).coerceIn(0f, (viewportSize.height - 2 * half).coerceAtLeast(0f)).toInt())
            }
            HandleButton("✕", Color(0xFFE53935), onRemoveArea, Modifier.offset { buttonPosition(rect.topLeft) })
            HandleButton("✓", Color(0xFF3AB54A), { onConfirmArea(rectCorners(rect)) },
                Modifier.offset { buttonPosition(rect.topRight) })
        }
        if (toolMode == EditToolMode.Brush) {
            Row(Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color.White, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp).width(260.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("笔宽 ${brushSizeDp.toInt()}", fontSize = 12.sp)
                Slider(brushSizeDp, { brushSizeDp = it }, valueRange = 12f..80f, modifier = Modifier.weight(1f))
            }
        }
        Text("双指缩放、移动地图", fontSize = 12.sp, color = Color(0xFF647080),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                .background(Color.White.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(8.dp))
        WorkflowZoomControl(workflow, Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

internal fun Path.addPolygon(points: List<Offset>) {
    if (points.size < 3) return
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
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
internal fun UndoRedoDock(
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
    MapWorkflowActions(onCancelBuild, onPreviousStep, onNextStep)
}

/**
 * Step2 地图对齐角控制器。
 * alignmentYaw 使用 [-180, 180] 度；向上拖动为正方向，完整轨道对应 360 度。
 */
@Composable
internal fun MapRotationController(
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


