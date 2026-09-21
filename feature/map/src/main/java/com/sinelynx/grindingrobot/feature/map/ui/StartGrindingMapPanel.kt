package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import com.sinelynx.grindingrobot.core.model.state.WorkRegionPointPayload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapWorkspaceMetricsItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.StartGrindingPlanPreviewUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_MAP_VIEWPORT_SCALE = 1f
private const val MAX_MAP_VIEWPORT_SCALE = 4f
private const val MIN_OBSTACLE_WORLD_SIZE_METERS = 0.2f

internal data class ObstacleWorldMetrics(
    val lengthMeters: Float? = null,
    val widthMeters: Float? = null,
    val diameterMeters: Float? = null,
    val areaSquareMeters: Float
)

private data class MapViewportTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
) {
    fun screenToModel(point: Offset): Offset {
        return Offset(
            x = (point.x - offset.x) / scale,
            y = (point.y - offset.y) / scale
        )
    }

    fun modelToScreen(point: Offset): Offset {
        return Offset(
            x = point.x * scale + offset.x,
            y = point.y * scale + offset.y
        )
    }

    fun zoomAndPan(
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        canvasSize: Size,
        contentBounds: Rect
    ): MapViewportTransform {
        val nextScale = (scale * zoom).coerceIn(MIN_MAP_VIEWPORT_SCALE, MAX_MAP_VIEWPORT_SCALE)
        val scaledOffset = Offset(
            x = centroid.x - (centroid.x - offset.x) * (nextScale / scale),
            y = centroid.y - (centroid.y - offset.y) * (nextScale / scale)
        )
        val nextOffset = scaledOffset + pan
        return copy(
            scale = nextScale,
            offset = clampViewportOffset(nextOffset, nextScale, canvasSize, contentBounds)
        )
    }
}

@Composable
internal fun TaskMapPanel(
    step: StartTaskStep,
    selectedWorkspaces: SnapshotStateMap<String, Boolean>,
    workspaces: List<MapWorkspaceMetricsItem>,
    mapPreview: MapPreviewUiState?,
    isMapPreviewLoading: Boolean,
    mapLoadError: String?,
    interactionEnabled: Boolean,
    workRegionPoints: List<WorkRegionPointPayload>,
    onRetry: () -> Unit,
    mapPreviewBitmap: Bitmap?,
    planPreview: StartGrindingPlanPreviewUiState?,
    robotPose: DevicePosePayload?,
    robotWidth: Double? = null,
    robotLength: Double? = null,
    obstacleShapes: List<TaskObstacleShape>,
    editingObstacleIndex: Int?,
    onWorkspaceClick: (String) -> Unit,
    onObstacleClick: (Offset, Rect, Size) -> Unit,
    onObstacleCancel: () -> Unit,
    onObstacleConfirm: () -> Unit,
    onObstacleChange: (TaskObstacleShape) -> Unit,
    onPanelSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val workspacesById = workspaces.associateBy { it.regionId }
    val mapPreviewRotationDeg = mapPreview?.let {
        it.alignmentYawDeg + it.rotationAlignmentDeltaDeg
    } ?: 0f
    val activeMapRotationDeg = mapPreviewRotationDeg
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportTransform by remember { mutableStateOf(MapViewportTransform()) }
    LaunchedEffect(mapPreviewBitmap) {
        viewportTransform = MapViewportTransform()
    }
    Box(
        modifier = modifier
            .onSizeChanged {
                panelSize = it
                onPanelSizeChanged(it)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            .pointerInput(step, mapPreviewBitmap, mapPreviewRotationDeg, interactionEnabled) {
                if (interactionEnabled && step == StartTaskStep.Obstacle && mapPreviewBitmap != null) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val baseBounds = obstaclePlacementBounds(
                            canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                            bitmapWidth = mapPreviewBitmap.width,
                            bitmapHeight = mapPreviewBitmap.height,
                            rotationDeg = mapPreviewRotationDeg
                        )
                        viewportTransform = viewportTransform.zoomAndPan(
                            centroid = centroid,
                            pan = pan,
                            zoom = zoom,
                            canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                            contentBounds = baseBounds
                        )
                    }
                }
            }
            .pointerInput(step, editingObstacleIndex, mapPreviewRotationDeg, mapPreview, interactionEnabled) {
                detectTapGestures { tap ->
                    if (!interactionEnabled) return@detectTapGestures
                    when (step) {
                        StartTaskStep.Workspace -> {
                            val regionId = mapPreview?.takeIf { mapPreviewBitmap != null }
                                ?.findRegionIdAt(
                                    tap = tap,
                                     canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                     bitmapWidth = mapPreviewBitmap?.width ?: 0,
                                     bitmapHeight = mapPreviewBitmap?.height ?: 0,
                                     rotationDeg = mapPreviewRotationDeg
                                 )
                            regionId?.let(onWorkspaceClick)
                        }
                        StartTaskStep.Obstacle -> {
                            if (editingObstacleIndex != null) return@detectTapGestures
                            if (mapPreviewBitmap == null) return@detectTapGestures
                            val obstacleBounds = obstaclePlacementBounds(
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                bitmapWidth = mapPreviewBitmap.width,
                                bitmapHeight = mapPreviewBitmap.height,
                                rotationDeg = mapPreviewRotationDeg
                            )
                            val modelTap = viewportTransform.screenToModel(tap)
                            if (obstacleBounds.contains(modelTap)) {
                                val imageBounds = previewImageRect(
                                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                    bitmapWidth = mapPreviewBitmap.width,
                                    bitmapHeight = mapPreviewBitmap.height
                                )
                                val minimumSize = mapPreview?.let { preview ->
                                    calculateObstacleMinimumCanvasSize(
                                        bounds = imageBounds,
                                        mapWidth = preview.mapWidth,
                                        mapHeight = preview.mapHeight,
                                        resolution = preview.resolution,
                                        rotationDeg = mapPreviewRotationDeg
                                    )
                                }
                                minimumSize?.let { onObstacleClick(modelTap, obstacleBounds, it) }
                            }
                        }
                        StartTaskStep.TaskParams,
                        StartTaskStep.Preview -> Unit
                    }
                }
            }
    ) {
        val activeViewportTransform = if (step == StartTaskStep.Obstacle) {
            viewportTransform
        } else {
            MapViewportTransform()
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(activeViewportTransform.offset.x, activeViewportTransform.offset.y)
                scale(activeViewportTransform.scale, activeViewportTransform.scale, pivot = Offset.Zero)
            }) {
                // 底图及地图附着内容绕适配区域中心旋转；障碍物编辑框仍保持屏幕轴对齐。
                withTransform({
                    rotate(
                        degrees = activeMapRotationDeg,
                        pivot = Offset(size.width / 2f, size.height / 2f)
                    )
                }) {
                    if (mapPreview != null && mapPreviewBitmap != null) {
                        drawMapPreview(
                            preview = mapPreview,
                            bitmap = mapPreviewBitmap,
                            selectedWorkspaces = selectedWorkspaces,
                            workspacesById = workspacesById,
                            contentScale = activeViewportTransform.scale,
                            isPlanPreview = step == StartTaskStep.Preview
                        )
                    }
                }

                if ((step == StartTaskStep.Obstacle || step == StartTaskStep.Preview) && mapPreviewBitmap != null) {
                    obstacleShapes.forEachIndexed { index, shape ->
                        val obstacleStrokeWidth = 3f / activeViewportTransform.scale
                        val isEditing = index == editingObstacleIndex
                        when (shape.mode) {
                            TaskObstacleMode.Rectangle -> {
                                val rect = shape.rect
                                if (isEditing) {
                                    drawRoundRect(
                                        color = Color(0x33D82B2A),
                                        topLeft = rect.topLeft,
                                        size = Size(rect.width, rect.height),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                    drawRoundRect(
                                        color = MapObstacleBorderColor,
                                        topLeft = rect.topLeft,
                                        size = Size(rect.width, rect.height),
                                        cornerRadius = CornerRadius(4f, 4f),
                                        style = Stroke(width = obstacleStrokeWidth)
                                    )
                                } else {
                                    drawConfirmedObstaclePath(
                                        path = Path().apply { addRect(rect) },
                                        contentScale = activeViewportTransform.scale
                                    )
                                }
                            }
                            TaskObstacleMode.Circle -> {
                                if (isEditing) {
                                    drawCircle(Color(0x33D82B2A), radius = shape.radius, center = shape.center)
                                    drawCircle(
                                        color = MapObstacleBorderColor,
                                        radius = shape.radius,
                                        center = shape.center,
                                        style = Stroke(width = obstacleStrokeWidth)
                                    )
                                } else {
                                    drawConfirmedObstaclePath(
                                        path = Path().apply { addOval(shape.rect) },
                                        contentScale = activeViewportTransform.scale
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val markerGeo = mapPreview?.toMapGeo()
        val markerBitmapSize = mapPreviewBitmap?.let { it.width to it.height }
        FittedMapRobotMarker(
            pose = robotPose,
            geo = markerGeo,
            mapImageSize = markerGeo?.let { it.mapWidth to it.mapHeight },
            bitmapSize = markerBitmapSize,
            viewportScale = activeViewportTransform.scale,
            viewportOffset = activeViewportTransform.offset,
            robotWidth = robotWidth,
            robotLength = robotLength,
            mapRotationDeg = activeMapRotationDeg
        )

        // 机器人位于路径下方；起终点最后绘制，避免被禁区或路径遮挡。
        if (step == StartTaskStep.Preview && mapPreview != null && mapPreviewBitmap != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawStartGrindingPlanOverlays(
                    preview = mapPreview, bitmapSize = mapPreviewBitmap.width to mapPreviewBitmap.height,
                    plan = planPreview, workRegions = workRegionPoints,
                    selectedRegionIds = selectedWorkspaces.filterValues { it }.keys,
                    rotationDeg = activeMapRotationDeg
                )
            }
        }

        val error = mapLoadError ?: planPreview?.errorMessage.takeIf { step == StartTaskStep.Preview }
        if (isMapPreviewLoading || (step == StartTaskStep.Preview && planPreview?.isPlanning == true)) {
            MapDataLoadingPage(text = if (step == StartTaskStep.Preview) "路径规划中..." else "地图区域加载中...")
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xEEFFFFFF)).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = Color(0xFFD82B2A), fontSize = 16.sp)
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }

        val editingObstacle = editingObstacleIndex?.let(obstacleShapes::getOrNull)
        if (interactionEnabled && step == StartTaskStep.Obstacle && editingObstacle != null && mapPreviewBitmap != null) {
            val bounds = obstaclePlacementBounds(
                canvasSize = Size(panelSize.width.toFloat(), panelSize.height.toFloat()),
                bitmapWidth = mapPreviewBitmap.width,
                bitmapHeight = mapPreviewBitmap.height,
                rotationDeg = mapPreviewRotationDeg
            )
            val imageBounds = previewImageRect(
                canvasSize = Size(panelSize.width.toFloat(), panelSize.height.toFloat()),
                bitmapWidth = mapPreviewBitmap.width,
                bitmapHeight = mapPreviewBitmap.height
            )
            val minimumSize = mapPreview?.let { preview ->
                calculateObstacleMinimumCanvasSize(
                    bounds = imageBounds,
                    mapWidth = preview.mapWidth,
                    mapHeight = preview.mapHeight,
                    resolution = preview.resolution,
                    rotationDeg = mapPreviewRotationDeg
                )
            }
            minimumSize?.let {
                ObstacleFloatingHandles(
                    shape = editingObstacle,
                    bounds = bounds,
                    minimumSize = it,
                    viewportTransform = activeViewportTransform,
                    onCancel = onObstacleCancel,
                    onConfirm = onObstacleConfirm,
                    onShapeChange = onObstacleChange
                )
            }
            calculateObstacleWorldMetrics(
                shape = editingObstacle,
                mapPreview = mapPreview,
                bitmapWidth = mapPreviewBitmap.width,
                bitmapHeight = mapPreviewBitmap.height,
                panelSize = panelSize
            )?.let { metrics ->
                ObstacleMetricInfoBar(
                    text = formatObstacleMetrics(metrics),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }
    }
}

internal fun decodeMapPreviewBitmap(bytes: ByteArray): Bitmap? {
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMapPreview(
    preview: MapPreviewUiState,
    bitmap: Bitmap,
    selectedWorkspaces: SnapshotStateMap<String, Boolean>,
    workspacesById: Map<String, MapWorkspaceMetricsItem>,
    contentScale: Float,
    isPlanPreview: Boolean = false
) {
    drawRect(Color.White)
    val imageRect = previewImageRect(size, bitmap.width, bitmap.height)
    drawContext.canvas.nativeCanvas.drawBitmap(
        bitmap,
        null,
        RectF(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom),
        null
    )
    val mapper: (MapPreviewPointItem) -> Offset? = { point ->
        preview.worldToCanvas(point, size, bitmap.width, bitmap.height)
    }
    drawMapRegionOverlays(
        regions = preview.eraseRegions,
        kind = MapRegionOverlayKind.Erase,
        pointMapper = mapper,
        contentScale = contentScale,
        drawBorder = !isPlanPreview
    )
    drawMapRegionOverlays(
        regions = preview.workRegions.filter { !isPlanPreview || selectedWorkspaces[it.regionId] == true },
        kind = MapRegionOverlayKind.Work,
        workFillColor = Color.Transparent,
        pointMapper = mapper,
        contentScale = contentScale,
        selectedRegionIds = selectedWorkspaces.filterValues { it }.keys
    )
    drawMapRegionOverlays(
        regions = preview.obstacleRegions,
        kind = MapRegionOverlayKind.Obstacle,
        pointMapper = mapper,
        contentScale = contentScale
    )
    if (!isPlanPreview) preview.workRegions.forEach { region ->
        val canvasPoints = region.points.mapNotNull { point ->
            preview.worldToCanvas(point, size, bitmap.width, bitmap.height)
        }
        if (canvasPoints.size < 3) return@forEach
        val labelPoint = canvasPoints.reduce { acc, offset -> acc + offset } / canvasPoints.size.toFloat()
        val label = workspacesById[region.regionId]?.regionName?.ifBlank { "区域${region.regionId}" }
            ?: "区域${region.regionId}"
        drawContext.canvas.nativeCanvas.drawText(
            label,
            labelPoint.x - 18f,
            labelPoint.y + 6f,
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 14.sp.toPx()
                color = android.graphics.Color.rgb(32, 41, 55)
            }
        )
    }
}

/** 所有点使用冻结底图投影；不从路径端点推导区域锚点，也不叠加路径响应的对齐角。 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStartGrindingPlanOverlays(
    preview: MapPreviewUiState,
    bitmapSize: Pair<Int, Int>,
    plan: StartGrindingPlanPreviewUiState?,
    workRegions: List<WorkRegionPointPayload>,
    selectedRegionIds: Set<String>,
    rotationDeg: Float
) {
    val geo = preview.toMapGeo()
    val logicalSize = geo.mapWidth to geo.mapHeight
    plan?.plannedPath?.let {
        drawFittedTaskPath(it, geo, logicalSize, bitmapSize, rotationDeg, colorByScope = true)
    }
    val regions = workRegions.filter { it.region.enabled && it.region.regionId in selectedRegionIds }
    fun project(x: Float, y: Float): Offset? =
        worldPointToBitmapOffset(MapPreviewPointItem(x, y), geo, logicalSize, bitmapSize)
            ?.let { taskBitmapPointToFittedPoint(it, size, bitmapSize, rotationDeg) }
    val radius = 4.dp.toPx()
    regions.forEach { region ->
        region.startPose?.let { project(it.x, it.y) }?.let { drawCircle(Color(0xFF33C461), radius, it) }
    }
    regions.forEach { region ->
        region.endPose?.let { project(it.x, it.y) }?.let { drawCircle(Color(0xFFFF9800), radius, it) }
    }
}

private fun MapPreviewUiState.toMapGeo(): MapGeo {
    return MapGeo(
        mapWidth = mapWidth,
        mapHeight = mapHeight,
        resolution = resolution,
        originX = originX,
        originY = originY,
        headingDeg = headingDeg,
        mapVersion = 0
    )
}



private fun MapPreviewUiState.findRegionIdAt(
    tap: Offset,
    canvasSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int,
    rotationDeg: Float
): String? {
    // 工作区轮廓已经随底图旋转，命中测试前先把屏幕触点还原到未旋转 Canvas。
    val contentTap = tap.rotateAround(
        center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
        rotationDeg = -rotationDeg
    )
    return workRegions.firstOrNull { region ->
        val polygon = region.points.mapNotNull { worldToCanvas(it, canvasSize, bitmapWidth, bitmapHeight) }
        polygon.size >= 3 && polygon.containsPoint(contentTap)
    }?.regionId
}

private fun Offset.rotateAround(center: Offset, rotationDeg: Float): Offset {
    if (rotationDeg == 0f) return this
    val radians = Math.toRadians(rotationDeg.toDouble())
    val cosine = kotlin.math.cos(radians).toFloat()
    val sine = kotlin.math.sin(radians).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + dx * cosine - dy * sine,
        y = center.y + dx * sine + dy * cosine
    )
}

private fun MapPreviewUiState.worldToCanvas(
    point: MapPreviewPointItem,
    canvasSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int
): Offset? {
    if (resolution <= 0f || mapWidth <= 0 || mapHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return null
    }
    val imageRect = previewImageRect(canvasSize, bitmapWidth, bitmapHeight)
    val bitmapPoint = worldPointToBitmapOffset(
        point = point,
        geo = toMapGeo(),
        mapImageSize = mapWidth to mapHeight,
        bitmapSize = bitmapWidth to bitmapHeight
    ) ?: return null
    return Offset(
        x = imageRect.left + bitmapPoint.x / bitmapWidth.toFloat() * imageRect.width,
        y = imageRect.top + bitmapPoint.y / bitmapHeight.toFloat() * imageRect.height
    )
}

private fun previewImageRect(canvasSize: Size, bitmapWidth: Int, bitmapHeight: Int): Rect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0) {
        return Rect(Offset.Zero, canvasSize)
    }
    val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
    val canvasAspect = canvasSize.width / canvasSize.height
    val width: Float
    val height: Float
    if (imageAspect > canvasAspect) {
        width = canvasSize.width
        height = width / imageAspect
    } else {
        height = canvasSize.height
        width = height * imageAspect
    }
    val left = (canvasSize.width - width) / 2f
    val top = (canvasSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * 将障碍物编辑使用的面板坐标还原为未旋转地图的世界坐标。
 *
 * 变换顺序必须与显示严格相反：先撤销地图旋转，再撤销图片在面板中的居中和等比适配，
 * 最后按原始地图宽高、分辨率、原点朝向和原点换算。显示旋转在 Y 向下的 Canvas 坐标中完成，避免翻转
 * Y 轴后角度符号发生变化。
 */
internal fun canvasPointToMapWorld(
    point: Offset,
    canvasSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int,
    mapWidth: Int,
    mapHeight: Int,
    resolution: Float,
    originX: Double,
    originY: Double,
    rotationDeg: Float,
    headingDeg: Float = 0f
): Pair<Float, Float>? {
    if (
        canvasSize.width <= 0f || canvasSize.height <= 0f ||
        bitmapWidth <= 0 || bitmapHeight <= 0 ||
        mapWidth <= 0 || mapHeight <= 0 || resolution <= 0f
    ) {
        return null
    }
    val imageRect = previewImageRect(canvasSize, bitmapWidth, bitmapHeight)
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null

    val unrotatedPoint = point.rotateAround(imageRect.center, -rotationDeg)
    val normalizedX = (unrotatedPoint.x - imageRect.left) / imageRect.width
    val normalizedYFromTop = (unrotatedPoint.y - imageRect.top) / imageRect.height
    val mapPixelX = normalizedX * mapWidth
    val mapPixelYFromBottom = (1f - normalizedYFromTop) * mapHeight
    // 撤销页面变换后，还需施加地图原点朝向，与 worldPointToBitmapOffset 严格互逆。
    val localX = mapPixelX * resolution
    val localY = mapPixelYFromBottom * resolution
    val radians = Math.toRadians(headingDeg.toDouble())
    val cosine = kotlin.math.cos(radians)
    val sine = kotlin.math.sin(radians)
    return (originX + cosine * localX - sine * localY).toFloat() to
        (originY + sine * localX + cosine * localY).toFloat()
}

private fun clampViewportOffset(
    offset: Offset,
    scale: Float,
    canvasSize: Size,
    contentBounds: Rect
): Offset {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f || contentBounds == Rect.Zero) {
        return offset
    }
    val minVisibleX = min(64f, canvasSize.width / 3f)
    val minVisibleY = min(64f, canvasSize.height / 3f)
    val minOffsetX = minVisibleX - contentBounds.right * scale
    val maxOffsetX = canvasSize.width - minVisibleX - contentBounds.left * scale
    val minOffsetY = minVisibleY - contentBounds.bottom * scale
    val maxOffsetY = canvasSize.height - minVisibleY - contentBounds.top * scale
    return Offset(
        x = offset.x.coerceIn(minOffsetX, maxOffsetX),
        y = offset.y.coerceIn(minOffsetY, maxOffsetY)
    )
}

private fun List<Offset>.containsPoint(point: Offset): Boolean {
    var inside = false
    var previous = lastIndex
    indices.forEach { current ->
        val currentPoint = this[current]
        val previousPoint = this[previous]
        val intersects = (currentPoint.y > point.y) != (previousPoint.y > point.y) &&
            point.x < (previousPoint.x - currentPoint.x) *
            (point.y - currentPoint.y) / (previousPoint.y - currentPoint.y) + currentPoint.x
        if (intersects) inside = !inside
        previous = current
    }
    return inside
}

private fun obstaclePlacementBounds(
    canvasSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int,
    rotationDeg: Float
): Rect {
    return if (canvasSize.width <= 0f || canvasSize.height <= 0f) {
        Rect.Zero
    } else {
        startGrindingRotatedBounds(
            rect = previewImageRect(canvasSize, bitmapWidth, bitmapHeight),
            rotationCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
            rotationDeg = rotationDeg
        )
    }
}

internal fun createDefaultObstacleShape(
    mode: TaskObstacleMode,
    tap: Offset,
    bounds: Rect,
    minimumSize: Size
): TaskObstacleShape {
    return when (mode) {
        TaskObstacleMode.Rectangle -> {
            val width = (bounds.width * 0.55f / 3f)
                .coerceAtLeast(max(48f, minimumSize.width))
            val height = (bounds.height * 0.45f / 3f)
                .coerceAtLeast(max(42f, minimumSize.height))
            val halfW = width / 2f
            val halfH = height / 2f
            val center = Offset(
                x = tap.x.coerceIn(bounds.left + halfW, bounds.right - halfW),
                y = tap.y.coerceIn(bounds.top + halfH, bounds.bottom - halfH)
            )
            TaskObstacleShape(
                mode = mode,
                center = center,
                width = width,
                height = height,
                radius = 0f
            )
        }
        TaskObstacleMode.Circle -> {
            val minimumDiameter = max(minimumSize.width, minimumSize.height)
            val radius = (min(bounds.width, bounds.height) * 0.22f / 3f)
                .coerceAtLeast(max(28f, minimumDiameter / 2f))
            val center = Offset(
                x = tap.x.coerceIn(bounds.left + radius, bounds.right - radius),
                y = tap.y.coerceIn(bounds.top + radius, bounds.bottom - radius)
            )
            TaskObstacleShape(
                mode = mode,
                center = center,
                width = radius * 2f,
                height = radius * 2f,
                radius = radius
            )
        }
    }
}

internal fun calculateObstacleMinimumCanvasSize(
    bounds: Rect,
    mapWidth: Int,
    mapHeight: Int,
    resolution: Float,
    rotationDeg: Float
): Size? {
    if (
        bounds.width <= 0f || bounds.height <= 0f ||
        mapWidth <= 0 || mapHeight <= 0 || resolution <= 0f
    ) {
        return null
    }

    val mapMetersPerCanvasX = mapWidth * resolution / bounds.width
    val mapMetersPerCanvasY = mapHeight * resolution / bounds.height
    val radians = rotationDeg * PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    val worldMetersPerCanvasX = sqrt(
        cosine * cosine * mapMetersPerCanvasX * mapMetersPerCanvasX +
            sine * sine * mapMetersPerCanvasY * mapMetersPerCanvasY
    )
    val worldMetersPerCanvasY = sqrt(
        sine * sine * mapMetersPerCanvasX * mapMetersPerCanvasX +
            cosine * cosine * mapMetersPerCanvasY * mapMetersPerCanvasY
    )

    return Size(
        width = MIN_OBSTACLE_WORLD_SIZE_METERS / worldMetersPerCanvasX,
        height = MIN_OBSTACLE_WORLD_SIZE_METERS / worldMetersPerCanvasY
    )
}

internal fun calculateObstacleWorldMetrics(
    shape: TaskObstacleShape,
    mapPreview: MapPreviewUiState?,
    bitmapWidth: Int,
    bitmapHeight: Int,
    panelSize: IntSize
): ObstacleWorldMetrics? {
    mapPreview ?: return null
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || panelSize == IntSize.Zero) return null
    val canvasSize = Size(panelSize.width.toFloat(), panelSize.height.toFloat())
    val rotationDeg = mapPreview.alignmentYawDeg + mapPreview.rotationAlignmentDeltaDeg

    fun toWorld(point: Offset): Pair<Float, Float>? = canvasPointToMapWorld(
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
        rotationDeg = rotationDeg
    )

    return when (shape.mode) {
        TaskObstacleMode.Rectangle -> {
            val rect = shape.rect
            val corners = listOf(
                Offset(rect.left, rect.top),
                Offset(rect.right, rect.top),
                Offset(rect.right, rect.bottom),
                Offset(rect.left, rect.bottom)
            ).map { toWorld(it) }
            if (corners.any { it == null }) return null
            val worldCorners = corners.filterNotNull()
            ObstacleWorldMetrics(
                lengthMeters = obstacleWorldDistance(worldCorners[0], worldCorners[1]),
                widthMeters = obstacleWorldDistance(worldCorners[0], worldCorners[3]),
                areaSquareMeters = obstacleWorldPolygonArea(worldCorners)
            )
        }
        TaskObstacleMode.Circle -> {
            if (shape.radius <= 0f) return null
            val worldCenter = toWorld(shape.center) ?: return null
            val worldEdge = toWorld(shape.center + Offset(shape.radius, 0f)) ?: return null
            val radiusMeters = obstacleWorldDistance(worldCenter, worldEdge)
            ObstacleWorldMetrics(
                diameterMeters = radiusMeters * 2f,
                areaSquareMeters = PI.toFloat() * radiusMeters * radiusMeters
            )
        }
    }
}

@Composable
private fun ObstacleMetricInfoBar(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .zIndex(4f)
    ) {
        Text(
            text = text,
            color = Color(0xFF202937),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatObstacleMetrics(metrics: ObstacleWorldMetrics): String {
    return if (metrics.diameterMeters != null) {
        "直径 %.2f m｜面积 %.2f m²".format(
            metrics.diameterMeters,
            metrics.areaSquareMeters
        )
    } else {
        "长 %.2f m｜宽 %.2f m｜面积 %.2f m²".format(
            metrics.lengthMeters ?: 0f,
            metrics.widthMeters ?: 0f,
            metrics.areaSquareMeters
        )
    }
}

private fun obstacleWorldDistance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}

private fun obstacleWorldPolygonArea(points: List<Pair<Float, Float>>): Float {
    if (points.size < 3) return 0f
    var sum = 0f
    for (index in points.indices) {
        val next = (index + 1) % points.size
        sum += points[index].first * points[next].second -
            points[next].first * points[index].second
    }
    return kotlin.math.abs(sum) / 2f
}

@Composable
private fun ObstacleFloatingHandles(
    shape: TaskObstacleShape,
    bounds: Rect,
    minimumSize: Size,
    viewportTransform: MapViewportTransform,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onShapeChange: (TaskObstacleShape) -> Unit
) {
    val density = LocalDensity.current
    val minHandleSize = 22.dp
    val maxHandleSize = 32.dp
    val handleGap = 4.dp
    val screenShortSide = min(shape.width, shape.height) * viewportTransform.scale
    val handleSize = with(density) {
        screenShortSide.toDp().coerceIn(minHandleSize, maxHandleSize)
    }
    val handleSizePx = with(density) { handleSize.toPx() }
    val handleHalfPx = handleSizePx / 2f
    val minHandleSizePx = with(density) { minHandleSize.toPx() }
    val handleGapPx = with(density) { handleGap.toPx() }
    when (shape.mode) {
        TaskObstacleMode.Rectangle -> {
            val rect = shape.rect
            val screenTopLeft = viewportTransform.modelToScreen(rect.topLeft)
            val screenBottomRight = viewportTransform.modelToScreen(rect.bottomRight)
            val screenWidth = screenBottomRight.x - screenTopLeft.x
            val screenHeight = screenBottomRight.y - screenTopLeft.y
            val moveOutsideHorizontally = screenWidth < minHandleSizePx
            val moveOutsideVertically = screenHeight < minHandleSizePx
            val leftHandleX = if (moveOutsideHorizontally) {
                screenTopLeft.x - handleGapPx - handleSizePx
            } else {
                screenTopLeft.x - handleHalfPx
            }
            val rightHandleX = if (moveOutsideHorizontally) {
                screenBottomRight.x + handleGapPx
            } else {
                screenBottomRight.x - handleHalfPx
            }
            val topHandleY = if (moveOutsideVertically) {
                screenTopLeft.y - handleGapPx - handleSizePx
            } else {
                screenTopLeft.y - handleHalfPx
            }
            val bottomHandleY = if (moveOutsideVertically) {
                screenBottomRight.y + handleGapPx
            } else {
                screenBottomRight.y - handleHalfPx
            }
            ObstacleDragTarget(
                screenBounds = Rect(screenTopLeft, screenBottomRight),
                clipToCircle = false,
                shape = shape,
                bounds = bounds,
                viewportTransform = viewportTransform,
                onShapeChange = onShapeChange
            )
            CircleHandle(
                text = "×",
                color = Color(0xFFD82B2A),
                size = handleSize,
                modifier = Modifier.offset {
                    IntOffset(leftHandleX.roundToInt(), topHandleY.roundToInt())
                },
                onClick = onCancel
            )
            CircleHandle(
                text = "✓",
                color = Color(0xFF33C461),
                size = handleSize,
                modifier = Modifier.offset {
                    IntOffset(rightHandleX.roundToInt(), topHandleY.roundToInt())
                },
                onClick = onConfirm
            )
            ObstacleResizeHandle(
                text = "↘",
                color = Color(0xFF00A0E9),
                size = handleSize,
                origin = Offset(rightHandleX, bottomHandleY),
                shape = shape,
                bounds = bounds,
                minimumSize = minimumSize,
                viewportTransform = viewportTransform,
                onShapeChange = onShapeChange
            )
        }
        TaskObstacleMode.Circle -> {
            val screenCenter = viewportTransform.modelToScreen(shape.center)
            val screenRadius = shape.radius * viewportTransform.scale
            val diagonalDirectionLength = sqrt(0.85f * 0.85f + 0.35f * 0.35f)
            val radialDistance = screenRadius + handleHalfPx + handleGapPx
            val closeCenter = screenCenter + Offset(
                x = -0.85f / diagonalDirectionLength * radialDistance,
                y = 0.35f / diagonalDirectionLength * radialDistance
            )
            val confirmCenter = screenCenter + Offset(x = 0f, y = -radialDistance)
            val resizeCenter = screenCenter + Offset(
                x = 0.85f / diagonalDirectionLength * radialDistance,
                y = 0.35f / diagonalDirectionLength * radialDistance
            )
            ObstacleDragTarget(
                screenBounds = Rect(
                    left = screenCenter.x - screenRadius,
                    top = screenCenter.y - screenRadius,
                    right = screenCenter.x + screenRadius,
                    bottom = screenCenter.y + screenRadius
                ),
                clipToCircle = true,
                shape = shape,
                bounds = bounds,
                viewportTransform = viewportTransform,
                onShapeChange = onShapeChange
            )
            CircleHandle(
                text = "×",
                color = Color(0xFFD82B2A),
                size = handleSize,
                modifier = Modifier.offset {
                    IntOffset(
                        (closeCenter.x - handleHalfPx).roundToInt(),
                        (closeCenter.y - handleHalfPx).roundToInt()
                    )
                },
                onClick = onCancel
            )
            CircleHandle(
                text = "✓",
                color = Color(0xFF33C461),
                size = handleSize,
                modifier = Modifier.offset {
                    IntOffset(
                        (confirmCenter.x - handleHalfPx).roundToInt(),
                        (confirmCenter.y - handleHalfPx).roundToInt()
                    )
                },
                onClick = onConfirm
            )
            ObstacleResizeHandle(
                text = "↘",
                color = Color(0xFF00A0E9),
                size = handleSize,
                origin = Offset(
                    resizeCenter.x - handleHalfPx,
                    resizeCenter.y - handleHalfPx
                ),
                shape = shape,
                bounds = bounds,
                minimumSize = minimumSize,
                viewportTransform = viewportTransform,
                onShapeChange = onShapeChange
            )
        }
    }
}

@Composable
private fun ObstacleDragTarget(
    screenBounds: Rect,
    clipToCircle: Boolean,
    shape: TaskObstacleShape,
    bounds: Rect,
    viewportTransform: MapViewportTransform,
    onShapeChange: (TaskObstacleShape) -> Unit
) {
    val density = LocalDensity.current
    val currentScreenBounds by rememberUpdatedState(screenBounds)
    val currentShape by rememberUpdatedState(shape)
    val currentBounds by rememberUpdatedState(bounds)
    val currentViewportScale by rememberUpdatedState(viewportTransform.scale)
    val currentOnShapeChange by rememberUpdatedState(onShapeChange)
    var gestureStartScreenBounds by remember { mutableStateOf<Rect?>(null) }
    val hitBounds = gestureStartScreenBounds ?: screenBounds
    val hitWidth = with(density) { hitBounds.width.toDp() }
    val hitHeight = with(density) { hitBounds.height.toDp() }
    val clipModifier = if (clipToCircle) Modifier.clip(CircleShape) else Modifier
    Box(
        modifier = Modifier
            .offset {
                IntOffset(hitBounds.left.roundToInt(), hitBounds.top.roundToInt())
            }
            .size(width = hitWidth, height = hitHeight)
            .then(clipModifier)
            .zIndex(1f)
            .pointerInput(Unit) {
                var accumulatedScreenDelta = Offset.Zero
                var startShape = currentShape
                var startBounds = currentBounds
                var startViewportScale = currentViewportScale
                detectDragGestures(
                    onDragStart = {
                        accumulatedScreenDelta = Offset.Zero
                        startShape = currentShape
                        startBounds = currentBounds
                        startViewportScale = currentViewportScale
                        gestureStartScreenBounds = currentScreenBounds
                    },
                    onDragEnd = {
                        accumulatedScreenDelta = Offset.Zero
                        gestureStartScreenBounds = null
                    },
                    onDragCancel = {
                        accumulatedScreenDelta = Offset.Zero
                        gestureStartScreenBounds = null
                    }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedScreenDelta += dragAmount
                    currentOnShapeChange(
                        moveStartGrindingObstacleFromStart(
                            startShape = startShape,
                            accumulatedScreenDelta = accumulatedScreenDelta,
                            viewportScale = startViewportScale,
                            bounds = startBounds
                        )
                    )
                }
            }
    )
}

@Composable
private fun ObstacleResizeHandle(
    text: String,
    color: Color,
    size: Dp,
    origin: Offset,
    shape: TaskObstacleShape,
    bounds: Rect,
    minimumSize: Size,
    viewportTransform: MapViewportTransform,
    onShapeChange: (TaskObstacleShape) -> Unit
) {
    val currentOrigin by rememberUpdatedState(origin)
    val currentShape by rememberUpdatedState(shape)
    val currentBounds by rememberUpdatedState(bounds)
    val currentMinimumSize by rememberUpdatedState(minimumSize)
    val currentViewportScale by rememberUpdatedState(viewportTransform.scale)
    val currentOnShapeChange by rememberUpdatedState(onShapeChange)
    var gestureStartOrigin by remember { mutableStateOf<Offset?>(null) }
    val hitOrigin = gestureStartOrigin ?: origin
    val visualDelta = origin - hitOrigin
    Box(
        modifier = Modifier
            .offset { IntOffset(hitOrigin.x.roundToInt(), hitOrigin.y.roundToInt()) }
            .size(size)
            .zIndex(3f)
            .pointerInput(Unit) {
                var accumulatedScreenDelta = Offset.Zero
                var startShape = currentShape
                var startBounds = currentBounds
                var startMinimumSize = currentMinimumSize
                var startViewportScale = currentViewportScale
                detectDragGestures(
                    onDragStart = {
                        accumulatedScreenDelta = Offset.Zero
                        startShape = currentShape
                        startBounds = currentBounds
                        startMinimumSize = currentMinimumSize
                        startViewportScale = currentViewportScale
                        gestureStartOrigin = currentOrigin
                    },
                    onDragEnd = {
                        accumulatedScreenDelta = Offset.Zero
                        gestureStartOrigin = null
                    },
                    onDragCancel = {
                        accumulatedScreenDelta = Offset.Zero
                        gestureStartOrigin = null
                    }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedScreenDelta += dragAmount
                    currentOnShapeChange(
                        resizeStartGrindingObstacleFromStart(
                            startShape = startShape,
                            accumulatedScreenDelta = accumulatedScreenDelta,
                            viewportScale = startViewportScale,
                            bounds = startBounds,
                            minimumSize = startMinimumSize
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CircleHandle(
            text = text,
            color = color,
            size = size,
            modifier = Modifier.graphicsLayer {
                translationX = visualDelta.x
                translationY = visualDelta.y
            }
        )
    }
}

@Composable
private fun CircleHandle(
    text: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    val fontSize = (18f * size.value / 32f).coerceIn(12f, 18f).sp
    Box(
        modifier = modifier
            .zIndex(2f)
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}
