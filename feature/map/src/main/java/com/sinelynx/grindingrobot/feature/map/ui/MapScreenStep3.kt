package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.BoundaryDrawMode
import com.sinelynx.grindingrobot.feature.map.viewmodel.ConcreteStrength
import com.sinelynx.grindingrobot.feature.map.viewmodel.ForbiddenDrawMode
import com.sinelynx.grindingrobot.feature.map.viewmodel.GrindingParameter
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep3ViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.ProcessType
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspacePanelMode
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspaceDraft
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspaceItem
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import com.sinelynx.grindingrobot.core.designsystem.R as DesignR

private data class ForbiddenShape(
    val id: Long,
    val rect: Rect? = null,
    val circleCenter: Offset? = null,
    val circleRadius: Float = 0f,
    val rotationDeg: Float = 0f
)

private data class DragGestureCallbacks(
    val onStart: () -> Unit,
    val onDrag: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit
)

private data class RotationGestureCallbacks(
    val onStart: () -> Unit,
    val onRotate: (Float) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit
)

private const val MIN_FORBIDDEN_WORLD_SIZE_METERS = 0.2f

internal data class ForbiddenMinimumContentSize(
    val rectangleWidth: Float,
    val rectangleHeight: Float,
    val circleRadius: Float
)

internal fun calculateForbiddenMinimumContentSize(
    bitmapWidth: Int,
    bitmapHeight: Int,
    mapWidth: Int,
    mapHeight: Int,
    resolution: Float,
    rectangleRotationDeg: Float
): ForbiddenMinimumContentSize? {
    if (
        bitmapWidth <= 0 || bitmapHeight <= 0 ||
        mapWidth <= 0 || mapHeight <= 0 || resolution <= 0f
    ) {
        return null
    }

    val metersPerContentX = mapWidth * resolution / bitmapWidth.toFloat()
    val metersPerContentY = mapHeight * resolution / bitmapHeight.toFloat()
    val radians = rectangleRotationDeg * PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    val metersPerRectangleWidth = sqrt(
        cosine * cosine * metersPerContentX * metersPerContentX +
                sine * sine * metersPerContentY * metersPerContentY
    )
    val metersPerRectangleHeight = sqrt(
        sine * sine * metersPerContentX * metersPerContentX +
                cosine * cosine * metersPerContentY * metersPerContentY
    )
    val minimumCircleDiameter = max(
        MIN_FORBIDDEN_WORLD_SIZE_METERS / metersPerContentX,
        MIN_FORBIDDEN_WORLD_SIZE_METERS / metersPerContentY
    )
    return ForbiddenMinimumContentSize(
        rectangleWidth = MIN_FORBIDDEN_WORLD_SIZE_METERS / metersPerRectangleWidth,
        rectangleHeight = MIN_FORBIDDEN_WORLD_SIZE_METERS / metersPerRectangleHeight,
        circleRadius = minimumCircleDiameter / 2f
    )
}

internal fun resizeForbiddenRectangle(
    rect: Rect,
    contentDelta: Offset,
    bounds: Rect,
    minimumSize: ForbiddenMinimumContentSize
): Rect? {
    val minRight = rect.left + minimumSize.rectangleWidth
    val minBottom = rect.top + minimumSize.rectangleHeight
    if (bounds.right < minRight || bounds.bottom < minBottom) return null
    return Rect(
        left = rect.left,
        top = rect.top,
        right = (rect.right + contentDelta.x).coerceIn(minRight, bounds.right),
        bottom = (rect.bottom + contentDelta.y).coerceIn(minBottom, bounds.bottom)
    )
}

internal fun resizeForbiddenCircleRadius(
    currentRadius: Float,
    deltaRadius: Float,
    maximumRadius: Float,
    minimumRadius: Float
): Float {
    return (currentRadius + deltaRadius).coerceIn(
        minimumRadius,
        maximumRadius.coerceAtLeast(minimumRadius)
    )
}

private data class SavedWorkspaceGeometry(
    val regionId: String,
    val boundaryPoints: List<Offset>,
    val startPoint: Offset?,
    val endPoint: Offset?,
    val forbiddenShapes: List<ForbiddenShape>
)

private data class EdgeMetricLabel(
    val center: Offset,
    val text: String
)

internal data class ForbiddenWorldMetrics(
    val lengthMeters: Float? = null,
    val widthMeters: Float? = null,
    val diameterMeters: Float? = null,
    val areaSquareMeters: Float
)

private enum class PendingAnchorPlacement {
    START,
    END
}

private fun normalizeDegrees(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

private fun Offset.rotateAround(center: Offset, degrees: Float): Offset {
    val radians = degrees * PI.toFloat() / 180f
    val dx = x - center.x
    val dy = y - center.y
    val cosValue = cos(radians)
    val sinValue = sin(radians)
    return Offset(
        x = center.x + dx * cosValue - dy * sinValue,
        y = center.y + dx * sinValue + dy * cosValue
    )
}

private fun rotatedRectCorners(
    topLeft: Offset,
    width: Float,
    height: Float,
    rotationDeg: Float
): List<Offset> {
    val center = Offset(topLeft.x + width / 2f, topLeft.y + height / 2f)
    return listOf(
        topLeft,
        Offset(topLeft.x + width, topLeft.y),
        Offset(topLeft.x + width, topLeft.y + height),
        Offset(topLeft.x, topLeft.y + height)
    ).map { it.rotateAround(center, rotationDeg) }
}

private fun rectFromCorners(corners: List<Offset>): Rect {
    val minX = corners.minOf { it.x }
    val minY = corners.minOf { it.y }
    val maxX = corners.maxOf { it.x }
    val maxY = corners.maxOf { it.y }
    return Rect(left = minX, top = minY, right = maxX, bottom = maxY)
}

private fun rotationFromHandleDrag(
    center: Offset,
    handle: Offset,
    width: Float,
    height: Float
): Float {
    val pointerAngle = atan2(handle.y - center.y, handle.x - center.x) * 180f / PI.toFloat()
    val bottomLeftBaseAngle = atan2(height / 2f, -width / 2f) * 180f / PI.toFloat()
    return normalizeDegrees(pointerAngle - bottomLeftBaseAngle)
}

private fun pathFromCorners(corners: List<Offset>): Path = Path().apply {
    if (corners.isEmpty()) return@apply
    moveTo(corners[0].x, corners[0].y)
    corners.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

@Composable
fun MapScreenStep3(
    viewModel: MapScreenStep3ViewModel,
    mapId: String? = null,
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel, mapId) {
        viewModel.loadMapData(mapId)
        onDispose { viewModel.cancelMapLoading() }
    }
    val mapImageBitmap = remember(uiState.mapImageBytes) {
        uiState.mapImageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    var mapPanelSize by remember { mutableStateOf(IntSize.Zero) }
    var showBoundaryRect by remember { mutableStateOf(true) }
    var boundaryTopLeft by remember { mutableStateOf(Offset(120f, 120f)) }
    var boundarySize by remember { mutableStateOf(IntSize(160, 160)) }
    var boundaryRotationDeg by remember { mutableStateOf(0f) }
    var polygonPoints by remember { mutableStateOf(listOf<Offset>()) }
    var confirmedBoundaryRectCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var confirmedBoundaryPolygon by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var savedWorkspaceGeometries by remember {
        mutableStateOf<List<SavedWorkspaceGeometry>>(
            emptyList()
        )
    }
    var forbiddenShapes by remember { mutableStateOf<List<ForbiddenShape>>(emptyList()) }
    var editingForbiddenId by remember { mutableStateOf<Long?>(null) }
    var nextForbiddenId by remember { mutableStateOf(1L) }
    var pendingForbiddenMode by remember { mutableStateOf<ForbiddenDrawMode?>(null) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var pendingAnchorPlacement by remember { mutableStateOf<PendingAnchorPlacement?>(null) }
    var mapZoom by remember { mutableStateOf(1f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }
    val currentMapZoom by rememberUpdatedState(mapZoom)
    val currentMapPan by rememberUpdatedState(mapPan)
    var baseScale by remember { mutableStateOf(1f) }
    var baseOffset by remember { mutableStateOf(Offset.Zero) }
    var lastTappedWorldPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var boundaryMoveStartTopLeft by remember { mutableStateOf<Offset?>(null) }
    var boundaryResizeStartTopLeft by remember { mutableStateOf<Offset?>(null) }
    var boundaryResizeStartSize by remember { mutableStateOf<IntSize?>(null) }
    var boundaryRotateStartDeg by remember { mutableStateOf<Float?>(null) }
    var forbiddenMoveStartShape by remember { mutableStateOf<ForbiddenShape?>(null) }
    var forbiddenResizeStartShape by remember { mutableStateOf<ForbiddenShape?>(null) }
    var forbiddenRotateStartShape by remember { mutableStateOf<ForbiddenShape?>(null) }
    var polygonDragStartPoints by remember { mutableStateOf<List<Offset>?>(null) }

    // 新建工作区在屏幕上默认保持水平，因此用负地图角抵消 Canvas 的统一旋转。
    fun initialBoundaryRotationDeg(): Float {
        val totalMapRotation =
            (uiState.mapGeo?.alignmentYawDeg ?: 0f) +
                    (uiState.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
        return normalizeDegrees(-totalMapRotation)
    }

    fun contentToBitmap(point: Offset): Offset {
        val bmp = mapImageBitmap ?: return point
        if (bmp.width <= 0 || bmp.height <= 0) return point
        return Offset(
            x = point.x.coerceIn(0f, bmp.width.toFloat()),
            y = point.y.coerceIn(0f, bmp.height.toFloat())
        )
    }

    fun contentPairToBitmapPair(point: Pair<Float, Float>): Pair<Float, Float> {
        val converted = contentToBitmap(Offset(point.first, point.second))
        return converted.x to converted.y
    }

    fun updateLastTapWorldPoint(contentPoint: Offset) {
        val bitmapPoint = contentPairToBitmapPair(contentPoint.x to contentPoint.y)
        lastTappedWorldPoint = viewModel.bitmapPointToWorld(bitmapPoint)
    }

    // Screen -> 原始 Content：按渲染的逆序撤销 pan/zoom、适配变换和地图总旋转。
    fun toContentPoint(screen: Offset): Offset {
        val bmp = mapImageBitmap ?: return screen
        val p = (screen - mapPan) / mapZoom
        val raw = (p - baseOffset) / baseScale
        val center = Offset(bmp.width / 2f, bmp.height / 2f)
        val totalRotation =
            (uiState.mapGeo?.alignmentYawDeg ?: 0f) + (uiState.mapGeo?.rotationAlignmentDeltaDeg
                ?: 0f)
        return raw.rotateAround(center, -totalRotation)
    }

    // 原始 Content -> Screen：地图总旋转必须同时包含基础对齐角和后端修正增量。
    fun toScreenPoint(content: Offset): Offset {
        val bmp = mapImageBitmap ?: return content
        val center = Offset(bmp.width / 2f, bmp.height / 2f)
        val totalRotation =
            (uiState.mapGeo?.alignmentYawDeg ?: 0f) + (uiState.mapGeo?.rotationAlignmentDeltaDeg
                ?: 0f)
        val rotated = content.rotateAround(center, totalRotation)
        val p = Offset(
            x = rotated.x * baseScale + baseOffset.x,
            y = rotated.y * baseScale + baseOffset.y
        )
        return Offset(x = p.x * mapZoom + mapPan.x, y = p.y * mapZoom + mapPan.y)
    }

    fun toScreenRect(content: Rect): Rect {
        return boundsOfPoints(
            listOf(
                content.topLeft,
                Offset(content.right, content.top),
                Offset(content.right, content.bottom),
                Offset(content.left, content.bottom)
            ).map(::toScreenPoint)
        ) ?: Rect.Zero
    }

    fun mapContentBoundsOrNull(): Rect? {
        val bmp = mapImageBitmap ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        return Rect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
    }

    fun mapImageScreenRectOrNull(): Rect? {
        val bounds = mapContentBoundsOrNull() ?: return null
        return toScreenRect(bounds)
    }

    fun placeInitialBoundaryRect() {
        val contentScale = (mapZoom * baseScale).coerceAtLeast(0.0001f)
        val mapScreenBounds = mapImageScreenRectOrNull()
        val robotScreenCenter = robotPoseToBitmapPoint(
            pose = uiState.robotPose,
            geo = uiState.mapGeo,
            mapImageSize = uiState.mapImageSize,
            bitmapSize = uiState.bitmapDecodeSize ?: mapImageBitmap?.let { it.width to it.height }
        )?.let(::toScreenPoint)
        val placement = mapScreenBounds?.let { bounds ->
            calculateInitialBoundaryRectPlacement(
                preferredScreenCenter = robotScreenCenter,
                mapScreenBounds = bounds,
                contentScale = contentScale
            )
        }

        boundaryRotationDeg = initialBoundaryRotationDeg()
        if (placement == null) {
            boundaryTopLeft = Offset(120f, 120f)
            boundarySize = IntSize(
                width = (160f / contentScale).roundToInt().coerceAtLeast(1),
                height = (160f / contentScale).roundToInt().coerceAtLeast(1)
            )
            return
        }

        val contentCenter = toContentPoint(placement.screenRect.center)
        boundarySize = placement.contentSize
        boundaryTopLeft = Offset(
            x = contentCenter.x - placement.contentSize.width / 2f,
            y = contentCenter.y - placement.contentSize.height / 2f
        )
    }

    fun isTapInsideVisibleMap(tapOffset: Offset): Boolean {
        val bounds = mapContentBoundsOrNull() ?: return false
        val contentPoint = toContentPoint(tapOffset)
        return contentPoint.x in bounds.left..bounds.right &&
                contentPoint.y in bounds.top..bounds.bottom
    }

    fun clampPointToRect(point: Offset, rect: Rect): Offset = Offset(
        x = point.x.coerceIn(rect.left, rect.right),
        y = point.y.coerceIn(rect.top, rect.bottom)
    )

    fun clampRectToMap(rect: Rect): Rect {
        val mapBounds = mapContentBoundsOrNull() ?: return rect
        return Rect(
            left = rect.left.coerceIn(mapBounds.left, mapBounds.right),
            top = rect.top.coerceIn(mapBounds.top, mapBounds.bottom),
            right = rect.right.coerceIn(mapBounds.left, mapBounds.right),
            bottom = rect.bottom.coerceIn(mapBounds.top, mapBounds.bottom)
        )
    }

    fun currentWorkspaceBounds(): Rect? {
        if (confirmedBoundaryRectCorners.size == 4) {
            return clampRectToMap(rectFromCorners(confirmedBoundaryRectCorners))
        }
        if (confirmedBoundaryPolygon.size >= 3) {
            val minX = confirmedBoundaryPolygon.minOf { it.x }
            val maxX = confirmedBoundaryPolygon.maxOf { it.x }
            val minY = confirmedBoundaryPolygon.minOf { it.y }
            val maxY = confirmedBoundaryPolygon.maxOf { it.y }
            return clampRectToMap(Rect(left = minX, top = minY, right = maxX, bottom = maxY))
        }
        if (showBoundaryRect) {
            return clampRectToMap(
                Rect(
                    left = boundaryTopLeft.x,
                    top = boundaryTopLeft.y,
                    right = boundaryTopLeft.x + boundarySize.width,
                    bottom = boundaryTopLeft.y + boundarySize.height
                )
            )
        }
        return null
    }

    fun currentWorkspaceScreenBounds(): Rect? {
        val contentPoints = when {
            confirmedBoundaryRectCorners.size == 4 -> confirmedBoundaryRectCorners
            confirmedBoundaryPolygon.size >= 3 -> confirmedBoundaryPolygon
            showBoundaryRect -> rotatedRectCorners(
                topLeft = boundaryTopLeft,
                width = boundarySize.width.toFloat(),
                height = boundarySize.height.toFloat(),
                rotationDeg = boundaryRotationDeg
            )

            else -> return null
        }
        return boundsOfPoints(contentPoints.map(::toScreenPoint))
    }

    fun contentPointToWorld(point: Offset): Pair<Float, Float>? {
        return viewModel.bitmapPointToWorld(contentPairToBitmapPair(point.x to point.y))
    }

    fun pointsToWorld(points: List<Offset>): List<Pair<Float, Float>>? {
        val world = points.map { contentPointToWorld(it) }
        if (world.any { it == null }) return null
        return world.filterNotNull()
    }

    fun metersBetween(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    fun polygonAreaM2(points: List<Pair<Float, Float>>): Float {
        if (points.size < 3) return 0f
        var sum = 0f
        for (i in points.indices) {
            val next = (i + 1) % points.size
            sum += points[i].first * points[next].second - points[next].first * points[i].second
        }
        return abs(sum) / 2f
    }

    fun currentConfirmedBoundaryPoints(): List<Offset> {
        return when {
            confirmedBoundaryRectCorners.size == 4 -> confirmedBoundaryRectCorners
            confirmedBoundaryPolygon.size >= 3 -> confirmedBoundaryPolygon
            else -> emptyList()
        }
    }

    fun currentWorkspaceGeometryOrNull(regionId: String): SavedWorkspaceGeometry? {
        val draft = uiState.workspaceDraft
        val boundaryPoints = currentConfirmedBoundaryPoints()
        val boundaryReady = draft.boundaryConfigured && when (draft.boundaryDrawMode) {
            BoundaryDrawMode.RECTANGLE -> boundaryPoints.size == 4
            BoundaryDrawMode.POLYGON -> boundaryPoints.size >= 3
        }
        if (!boundaryReady) {
            return null
        }
        return SavedWorkspaceGeometry(
            regionId = regionId,
            boundaryPoints = boundaryPoints,
            startPoint = startPoint,
            endPoint = endPoint,
            forbiddenShapes = forbiddenShapes
        )
    }

    fun clearCurrentWorkspaceGeometry() {
        confirmedBoundaryRectCorners = emptyList()
        confirmedBoundaryPolygon = emptyList()
        polygonPoints = emptyList()
        startPoint = null
        endPoint = null
        forbiddenShapes = emptyList()
        editingForbiddenId = null
        pendingForbiddenMode = null
        pendingAnchorPlacement = null
        showBoundaryRect = true
        boundaryRotationDeg = initialBoundaryRotationDeg()
        boundaryMoveStartTopLeft = null
        boundaryResizeStartTopLeft = null
        boundaryResizeStartSize = null
        boundaryRotateStartDeg = null
        forbiddenMoveStartShape = null
        forbiddenResizeStartShape = null
        forbiddenRotateStartShape = null
        polygonDragStartPoints = null
    }

    fun loadWorkspaceGeometry(regionId: String, draft: WorkspaceDraft) {
        val saved = savedWorkspaceGeometries.firstOrNull { it.regionId == regionId }
        if (saved != null) {
            if (draft.boundaryDrawMode == BoundaryDrawMode.RECTANGLE) {
                confirmedBoundaryRectCorners = saved.boundaryPoints
                confirmedBoundaryPolygon = emptyList()
            } else {
                confirmedBoundaryRectCorners = emptyList()
                confirmedBoundaryPolygon = saved.boundaryPoints
                polygonPoints = saved.boundaryPoints
            }
            startPoint = saved.startPoint
            endPoint = saved.endPoint
            forbiddenShapes = saved.forbiddenShapes
            nextForbiddenId = (forbiddenShapes.maxOfOrNull { it.id } ?: 0L) + 1L
            return
        }

        fun worldPointsToContent(points: List<Pair<Float, Float>>): List<Offset> =
            points.mapNotNull(viewModel::worldPointToBitmap).map { Offset(it.first, it.second) }

        val boundary = when (draft.boundaryDrawMode) {
            BoundaryDrawMode.RECTANGLE -> worldPointsToContent(draft.boundaryRectCornersM)
            BoundaryDrawMode.POLYGON -> worldPointsToContent(draft.boundaryPolygonPointsM)
        }
        if (draft.boundaryDrawMode == BoundaryDrawMode.RECTANGLE) {
            confirmedBoundaryRectCorners = boundary
            confirmedBoundaryPolygon = emptyList()
        } else {
            confirmedBoundaryRectCorners = emptyList()
            confirmedBoundaryPolygon = boundary
            polygonPoints = boundary
        }
        startPoint = draft.startPointM?.let(viewModel::worldPointToBitmap)
            ?.let { Offset(it.first, it.second) }
        endPoint =
            draft.endPointM?.let(viewModel::worldPointToBitmap)?.let { Offset(it.first, it.second) }
        val rectangles = draft.forbiddenRectanglesM.mapIndexedNotNull { index, rectangle ->
            val corners = worldPointsToContent(rectangle)
            if (corners.size != 4) return@mapIndexedNotNull null
            val center = Offset(
                corners.map { it.x }.average().toFloat(),
                corners.map { it.y }.average().toFloat()
            )
            val width = (corners[1] - corners[0]).getDistance()
            val height = (corners[3] - corners[0]).getDistance()
            ForbiddenShape(
                id = index + 1L,
                rect = Rect(
                    center.x - width / 2f,
                    center.y - height / 2f,
                    center.x + width / 2f,
                    center.y + height / 2f
                ),
                rotationDeg = normalizeDegrees(
                    atan2(
                        corners[1].y - corners[0].y,
                        corners[1].x - corners[0].x
                    ) * 180f / PI.toFloat()
                )
            )
        }
        val circles = draft.forbiddenCirclesM.mapIndexedNotNull { index, (worldCenter, radiusM) ->
            val center = viewModel.worldPointToBitmap(worldCenter) ?: return@mapIndexedNotNull null
            val radius = viewModel.worldRadiusToBitmap(radiusM) ?: return@mapIndexedNotNull null
            ForbiddenShape(
                id = rectangles.size + index + 1L,
                circleCenter = Offset(center.first, center.second),
                circleRadius = radius
            )
        }
        forbiddenShapes = rectangles + circles
        nextForbiddenId = (forbiddenShapes.maxOfOrNull { it.id } ?: 0L) + 1L
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
            Step3TopBar()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp)),
                    color = Color(0xFFF6F8FB),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { mapPanelSize = it }
                            .pointerInput(mapImageBitmap, mapPanelSize) {
                                val bmp = mapImageBitmap ?: return@pointerInput
                                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                    val oldZoom = currentMapZoom
                                    val oldPan = currentMapPan
                                    val newZoom = (oldZoom * zoomChange).coerceIn(0.5f, 3f)
                                    val zoomRatio = if (oldZoom == 0f) 1f else newZoom / oldZoom
                                    mapZoom = newZoom
                                    mapPan = centroid - (centroid - oldPan) * zoomRatio + panChange
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val bmp = mapImageBitmap
                            if (bmp == null) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)),
                                        radius = 900f
                                    ),
                                    size = size
                                )
                                return@Canvas
                            }
                            baseScale = min(
                                size.width / bmp.width.toFloat(),
                                size.height / bmp.height.toFloat()
                            )
                            baseOffset = Offset(
                                x = (size.width - bmp.width * baseScale) / 2f,
                                y = (size.height - bmp.height * baseScale) / 2f
                            )
                            // 两个协议字段只在这里合成一次；底图和全部 Content 几何共用该旋转。
                            val totalRotation = (uiState.mapGeo?.alignmentYawDeg
                                ?: 0f) + (uiState.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
                            withTransform({
                                translate(left = mapPan.x, top = mapPan.y)
                                scale(scaleX = mapZoom, scaleY = mapZoom, pivot = Offset.Zero)
                                translate(left = baseOffset.x, top = baseOffset.y)
                                scale(scaleX = baseScale, scaleY = baseScale, pivot = Offset.Zero)
                                rotate(
                                    degrees = totalRotation,
                                    pivot = Offset(bmp.width / 2f, bmp.height / 2f)
                                )
                            }) {
                                drawImage(bmp)
                                val geo = uiState.mapGeo
                                val logicalSize = uiState.mapImageSize
                                val decodedSize =
                                    uiState.bitmapDecodeSize ?: (bmp.width to bmp.height)
                                if (geo != null && logicalSize != null) {
                                    // 本地草稿和接口区域可能同 ID：优先显示本地编辑几何，避免刷新后重复绘制或盖住草稿。
                                    val locallyRenderedWorkIds = savedWorkspaceGeometries
                                        .mapTo(mutableSetOf()) { it.regionId }
                                        .apply { uiState.editingWorkspaceRegionId?.let(::add) }
                                    val locallyRenderedObstacleIds = uiState.workspaces
                                        .flatMapTo(mutableSetOf()) { it.obstacleRegionIds }
                                    val mapper: (com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem) -> Offset? =
                                        { point ->
                                            worldPointToBitmapOffset(
                                                point,
                                                geo,
                                                logicalSize,
                                                decodedSize
                                            )
                                        }
                                    val contentScale = baseScale * mapZoom
                                    drawMapRegionOverlays(
                                        regions = uiState.previewEraseRegions,
                                        kind = MapRegionOverlayKind.Erase,
                                        pointMapper = mapper,
                                        contentScale = contentScale
                                    )
                                    drawMapRegionOverlays(
                                        regions = uiState.previewWorkRegions.filterNot {
                                            it.regionId in locallyRenderedWorkIds
                                        },
                                        kind = MapRegionOverlayKind.Work,
                                        pointMapper = mapper,
                                        contentScale = contentScale,
                                        // 工作区只画边界，保留冻结底图中的内容；不改变擦除区和禁区的填充规则。
                                        workFillColor = Color.Transparent
                                    )
                                    drawMapRegionOverlays(
                                        regions = uiState.previewObstacleRegions.filterNot {
                                            it.regionId in locallyRenderedObstacleIds
                                        },
                                        kind = MapRegionOverlayKind.Obstacle,
                                        pointMapper = mapper,
                                        contentScale = contentScale
                                    )
                                }
                                savedWorkspaceGeometries
                                    .filterNot { it.regionId == uiState.editingWorkspaceRegionId }
                                    .forEach { workspace ->
                                        if (workspace.boundaryPoints.size >= 3) {
                                            val path = pathFromCorners(workspace.boundaryPoints)
                                            drawPath(
                                                path = path,
                                                color = MapWorkBorderColor,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 2.dp.toPx() / baseScale / mapZoom
                                                )
                                            )
                                        }
                                        workspace.forbiddenShapes.forEach { shape ->
                                            shape.rect?.let { rect ->
                                                val path = pathFromCorners(
                                                    rotatedRectCorners(
                                                        rect.topLeft,
                                                        rect.width,
                                                        rect.height,
                                                        shape.rotationDeg
                                                    )
                                                )
                                                drawConfirmedObstaclePath(
                                                    path = path,
                                                    contentScale = baseScale * mapZoom
                                                )
                                            }
                                            shape.circleCenter?.let { center ->
                                                if (shape.circleRadius > 0f) {
                                                    drawConfirmedObstaclePath(
                                                        path = Path().apply {
                                                            addOval(
                                                                Rect(
                                                                    left = center.x - shape.circleRadius,
                                                                    top = center.y - shape.circleRadius,
                                                                    right = center.x + shape.circleRadius,
                                                                    bottom = center.y + shape.circleRadius
                                                                )
                                                            )
                                                        },
                                                        contentScale = baseScale * mapZoom
                                                    )
                                                }
                                            }
                                        }
                                    }
                                if (confirmedBoundaryRectCorners.size == 4) {
                                    val rectPath = pathFromCorners(confirmedBoundaryRectCorners)
                                    drawPath(path = rectPath, color = Color(0x223AB54A))
                                    drawPath(
                                        path = rectPath,
                                        color = Color(0xFF24BE1C),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2f / baseScale / mapZoom,
                                            pathEffect = PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(
                                                    10f / baseScale / mapZoom,
                                                    8f / baseScale / mapZoom
                                                )
                                            )
                                        )
                                    )
                                }
                                if (confirmedBoundaryPolygon.size >= 3) {
                                    val polygon = Path().apply {
                                        moveTo(
                                            confirmedBoundaryPolygon.first().x,
                                            confirmedBoundaryPolygon.first().y
                                        )
                                        confirmedBoundaryPolygon.drop(1)
                                            .forEach { lineTo(it.x, it.y) }
                                        close()
                                    }
                                    drawPath(path = polygon, color = Color(0x223AB54A))
                                    drawPath(
                                        path = polygon,
                                        color = Color(0xFF24BE1C),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 2f / baseScale / mapZoom,
                                            pathEffect = PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(
                                                    10f / baseScale / mapZoom,
                                                    8f / baseScale / mapZoom
                                                )
                                            )
                                        )
                                    )
                                }
                                forbiddenShapes.forEach { shape ->
                                    val rect = shape.rect
                                    if (rect != null) {
                                        val corners = rotatedRectCorners(
                                            topLeft = rect.topLeft,
                                            width = rect.width,
                                            height = rect.height,
                                            rotationDeg = shape.rotationDeg
                                        )
                                        val path = pathFromCorners(corners)
                                        drawPath(path = path, color = Color(0x33D82B2A))
                                        drawPath(
                                            path = path,
                                            color = Color(0xFFD82B2A),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 2f / baseScale / mapZoom,
                                                pathEffect = PathEffect.dashPathEffect(
                                                    intervals = floatArrayOf(
                                                        10f / baseScale / mapZoom,
                                                        8f / baseScale / mapZoom
                                                    )
                                                )
                                            )
                                        )
                                    }
                                    val center = shape.circleCenter
                                    if (center != null && shape.circleRadius > 0f) {
                                        drawCircle(
                                            color = Color(0x33D82B2A),
                                            radius = shape.circleRadius,
                                            center = center
                                        )
                                        drawCircle(
                                            color = Color(0xFFD82B2A),
                                            radius = shape.circleRadius,
                                            center = center,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 2f / baseScale / mapZoom,
                                                pathEffect = PathEffect.dashPathEffect(
                                                    intervals = floatArrayOf(
                                                        10f / baseScale / mapZoom,
                                                        8f / baseScale / mapZoom
                                                    )
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (uiState.isMapLoading && mapImageBitmap == null) {
                            MapDataLoadingPage()
                        }
                        robotPoseToBitmapPoint(
                            pose = uiState.robotPose,
                            geo = uiState.mapGeo,
                            mapImageSize = uiState.mapImageSize,
                            bitmapSize = uiState.bitmapDecodeSize
                                ?: mapImageBitmap?.let { it.width to it.height }
                        )?.let { bitmapPoint ->
                            val robotWidth = uiState.robotWidth
                            val robotLength = uiState.robotLength
                            val (drawWidth, drawLength) = remember(
                                mapZoom,
                                robotWidth,
                                robotLength
                            ) {
                                val defaultSize = 40.dp
                                if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
                                    val baseWidth: androidx.compose.ui.unit.Dp
                                    val baseLength: androidx.compose.ui.unit.Dp
                                    if (robotLength >= robotWidth) {
                                        baseLength = defaultSize
                                        baseWidth =
                                            defaultSize * (robotWidth / robotLength).toFloat()
                                    } else {
                                        baseWidth = defaultSize
                                        baseLength =
                                            defaultSize * (robotLength / robotWidth).toFloat()
                                    }
                                    val drawW = (baseWidth * mapZoom).coerceIn(16.dp, 100.dp)
                                    val drawL = (baseLength * mapZoom).coerceIn(16.dp, 100.dp)
                                    drawW to drawL
                                } else {
                                    val drawW = (defaultSize * mapZoom).coerceIn(16.dp, 100.dp)
                                    drawW to drawW
                                }
                            }
                            val totalMapRotation =
                                (uiState.mapGeo?.alignmentYawDeg ?: 0f) +
                                        (uiState.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
                            RobotPoseIcon(
                                center = toScreenPoint(bitmapPoint),
                                headingDeg = (uiState.robotPose?.headingDeg
                                    ?: 0f) - totalMapRotation,
                                width = drawWidth,
                                length = drawLength
                            )
                        }
                        savedWorkspaceGeometries
                            .filterNot { it.regionId == uiState.editingWorkspaceRegionId }
                            .forEach { workspace ->
                                workspace.startPoint?.let { point ->
                                    AnchorPointMarker(
                                        text = "s",
                                        color = Color(0xFF33C461),
                                        center = toScreenPoint(point)
                                    )
                                }
                                workspace.endPoint?.let { point ->
                                    AnchorPointMarker(
                                        text = "e",
                                        color = Color(0xFF33C461),
                                        center = toScreenPoint(point)
                                    )
                                }
                            }
                        startPoint?.let { point ->
                            AnchorPointMarker(
                                text = "s",
                                color = Color(0xFF33C461),
                                center = toScreenPoint(point)
                            )
                        }
                        endPoint?.let { point ->
                            AnchorPointMarker(
                                text = "e",
                                color = Color(0xFF33C461),
                                center = toScreenPoint(point)
                            )
                        }
                        val workspaceBounds = currentWorkspaceBounds()
                        val workspaceScreenBounds = currentWorkspaceScreenBounds()
                        val shouldPlaceForbiddenByTap =
                            uiState.panelMode == WorkspacePanelMode.FORBIDDEN_EDITOR &&
                                    pendingForbiddenMode != null &&
                                    workspaceScreenBounds != null
                        if (shouldPlaceForbiddenByTap) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(
                                        pendingForbiddenMode,
                                        workspaceScreenBounds,
                                        nextForbiddenId
                                    ) {
                                        detectTapGestures { tapOffset ->
                                            val mode =
                                                pendingForbiddenMode ?: return@detectTapGestures
                                            val screenBounds = workspaceScreenBounds
                                                ?: return@detectTapGestures
                                            val contentScale =
                                                (mapZoom * baseScale).coerceAtLeast(0.0001f)
                                            val clampedScreenTap = clampPointToBounds(
                                                point = tapOffset,
                                                bounds = screenBounds
                                            )
                                            updateLastTapWorldPoint(toContentPoint(clampedScreenTap))
                                            val newShape = when (mode) {
                                                ForbiddenDrawMode.RECTANGLE -> {
                                                    val rotationDeg = initialBoundaryRotationDeg()
                                                    val minimumSize =
                                                        calculateForbiddenMinimumContentSize(
                                                            bitmapWidth = mapImageBitmap?.width
                                                                ?: 0,
                                                            bitmapHeight = mapImageBitmap?.height
                                                                ?: 0,
                                                            mapWidth = uiState.mapGeo?.mapWidth
                                                                ?: 0,
                                                            mapHeight = uiState.mapGeo?.mapHeight
                                                                ?: 0,
                                                            resolution = uiState.mapGeo?.resolution
                                                                ?: 0f,
                                                            rectangleRotationDeg = rotationDeg
                                                        ) ?: return@detectTapGestures
                                                    val maximumWidth =
                                                        screenBounds.width / contentScale
                                                    val maximumHeight =
                                                        screenBounds.height / contentScale
                                                    if (
                                                        maximumWidth < minimumSize.rectangleWidth ||
                                                        maximumHeight < minimumSize.rectangleHeight
                                                    ) {
                                                        return@detectTapGestures
                                                    }
                                                    val width = max(
                                                        (screenBounds.width * 0.55f) / 3f / contentScale,
                                                        minimumSize.rectangleWidth
                                                    ).coerceAtMost(maximumWidth)
                                                    val height = max(
                                                        (screenBounds.height * 0.45f) / 3f / contentScale,
                                                        minimumSize.rectangleHeight
                                                    ).coerceAtMost(maximumHeight)
                                                    val screenWidth = width * contentScale
                                                    val screenHeight = height * contentScale
                                                    val screenCenter = Offset(
                                                        x = clampedScreenTap.x.coerceIn(
                                                            screenBounds.left + screenWidth / 2f,
                                                            screenBounds.right - screenWidth / 2f
                                                        ),
                                                        y = clampedScreenTap.y.coerceIn(
                                                            screenBounds.top + screenHeight / 2f,
                                                            screenBounds.bottom - screenHeight / 2f
                                                        )
                                                    )
                                                    val contentCenter = toContentPoint(screenCenter)
                                                    ForbiddenShape(
                                                        id = nextForbiddenId,
                                                        rect = Rect(
                                                            left = contentCenter.x - width / 2f,
                                                            top = contentCenter.y - height / 2f,
                                                            right = contentCenter.x + width / 2f,
                                                            bottom = contentCenter.y + height / 2f
                                                        ),
                                                        // 与工作区一致，抵消 Canvas 的地图旋转，使新建禁区在屏幕上默认保持水平。
                                                        rotationDeg = rotationDeg
                                                    )
                                                }

                                                ForbiddenDrawMode.CIRCLE -> {
                                                    val minimumSize =
                                                        calculateForbiddenMinimumContentSize(
                                                            bitmapWidth = mapImageBitmap?.width
                                                                ?: 0,
                                                            bitmapHeight = mapImageBitmap?.height
                                                                ?: 0,
                                                            mapWidth = uiState.mapGeo?.mapWidth
                                                                ?: 0,
                                                            mapHeight = uiState.mapGeo?.mapHeight
                                                                ?: 0,
                                                            resolution = uiState.mapGeo?.resolution
                                                                ?: 0f,
                                                            rectangleRotationDeg = 0f
                                                        ) ?: return@detectTapGestures
                                                    val maximumRadius = min(
                                                        screenBounds.width,
                                                        screenBounds.height
                                                    ) / 2f / contentScale
                                                    if (maximumRadius < minimumSize.circleRadius) {
                                                        return@detectTapGestures
                                                    }
                                                    val radius = max(
                                                        (min(
                                                            screenBounds.width,
                                                            screenBounds.height
                                                        ) * 0.22f) /
                                                                3f / contentScale,
                                                        minimumSize.circleRadius
                                                    ).coerceAtMost(maximumRadius)
                                                    val screenRadius = radius * contentScale
                                                    val screenCenter = Offset(
                                                        x = clampedScreenTap.x.coerceIn(
                                                            screenBounds.left + screenRadius,
                                                            screenBounds.right - screenRadius
                                                        ),
                                                        y = clampedScreenTap.y.coerceIn(
                                                            screenBounds.top + screenRadius,
                                                            screenBounds.bottom - screenRadius
                                                        )
                                                    )
                                                    ForbiddenShape(
                                                        id = nextForbiddenId,
                                                        circleCenter = toContentPoint(screenCenter),
                                                        circleRadius = radius
                                                    )
                                                }
                                            }
                                            forbiddenShapes = forbiddenShapes + newShape
                                            editingForbiddenId = newShape.id
                                            nextForbiddenId += 1
                                            pendingForbiddenMode = null
                                        }
                                    }
                            )
                        }
                        val shouldPlaceAnchorByTap =
                            uiState.panelMode == WorkspacePanelMode.ADD &&
                                    pendingAnchorPlacement != null
                        if (shouldPlaceAnchorByTap) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(pendingAnchorPlacement, workspaceBounds) {
                                        detectTapGestures { tapOffset ->
                                            if (!isTapInsideVisibleMap(tapOffset)) return@detectTapGestures
                                            val placement =
                                                pendingAnchorPlacement ?: return@detectTapGestures
                                            val mapBounds =
                                                mapContentBoundsOrNull() ?: return@detectTapGestures
                                            val contentTap = toContentPoint(tapOffset)
                                            val bounds = workspaceBounds ?: mapBounds
                                            val workspaceInMap = Rect(
                                                left = bounds.left.coerceIn(
                                                    mapBounds.left,
                                                    mapBounds.right
                                                ),
                                                top = bounds.top.coerceIn(
                                                    mapBounds.top,
                                                    mapBounds.bottom
                                                ),
                                                right = bounds.right.coerceIn(
                                                    mapBounds.left,
                                                    mapBounds.right
                                                ),
                                                bottom = bounds.bottom.coerceIn(
                                                    mapBounds.top,
                                                    mapBounds.bottom
                                                )
                                            )
                                            val clamped = Offset(
                                                x = contentTap.x.coerceIn(
                                                    workspaceInMap.left,
                                                    workspaceInMap.right
                                                ),
                                                y = contentTap.y.coerceIn(
                                                    workspaceInMap.top,
                                                    workspaceInMap.bottom
                                                )
                                            )
                                            updateLastTapWorldPoint(clamped)
                                            when (placement) {
                                                PendingAnchorPlacement.START -> {
                                                    startPoint = clamped
                                                    viewModel.setStartConfigured(true)
                                                    viewModel.confirmStartPoint(
                                                        contentPairToBitmapPair(clamped.x to clamped.y)
                                                    )
                                                }

                                                PendingAnchorPlacement.END -> {
                                                    endPoint = clamped
                                                    viewModel.setEndConfigured(true)
                                                    viewModel.confirmEndPoint(
                                                        contentPairToBitmapPair(clamped.x to clamped.y)
                                                    )
                                                }
                                            }
                                            pendingAnchorPlacement = null
                                        }
                                    }
                            )
                        }
                        val editingShape = editingForbiddenId?.let { id ->
                            forbiddenShapes.firstOrNull { it.id == id }
                        }
                        editingShape?.rect?.let { rect ->
                            val totalRotationDeg =
                                (uiState.mapGeo?.alignmentYawDeg ?: 0f) +
                                        (uiState.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
                            val contentCorners = rotatedRectCorners(
                                topLeft = rect.topLeft,
                                width = rect.width,
                                height = rect.height,
                                rotationDeg = editingShape.rotationDeg
                            )
                            val screenCorners = contentCorners.map(::toScreenPoint)
                            ForbiddenRectHandles(
                                corners = screenCorners,
                                rectWidth = rect.width * baseScale * mapZoom,
                                rectHeight = rect.height * baseScale * mapZoom,
                                onClose = {
                                    val id = editingForbiddenId ?: return@ForbiddenRectHandles
                                    forbiddenShapes = forbiddenShapes.filterNot { it.id == id }
                                    editingForbiddenId = null
                                    forbiddenMoveStartShape = null
                                    forbiddenResizeStartShape = null
                                    forbiddenRotateStartShape = null
                                },
                                onConfirm = {
                                    viewModel.confirmForbiddenRectangleCorners(
                                        topLeft = contentPairToBitmapPair(contentCorners[0].x to contentCorners[0].y),
                                        topRight = contentPairToBitmapPair(contentCorners[1].x to contentCorners[1].y),
                                        bottomRight = contentPairToBitmapPair(contentCorners[2].x to contentCorners[2].y),
                                        bottomLeft = contentPairToBitmapPair(contentCorners[3].x to contentCorners[3].y)
                                    )
                                    editingForbiddenId = null
                                },
                                moveGesture = DragGestureCallbacks(
                                    onStart = {
                                        val id = editingForbiddenId
                                        forbiddenMoveStartShape =
                                            forbiddenShapes.firstOrNull { it.id == id }
                                    },
                                    onDrag = onDrag@{ totalDelta ->
                                        val startShape = forbiddenMoveStartShape ?: return@onDrag
                                        val startRect = startShape.rect ?: return@onDrag
                                        val parentScreenBounds = currentWorkspaceScreenBounds()
                                            ?: return@onDrag
                                        val currentScreenBounds = boundsOfPoints(
                                            rotatedRectCorners(
                                                topLeft = startRect.topLeft,
                                                width = startRect.width,
                                                height = startRect.height,
                                                rotationDeg = startShape.rotationDeg
                                            ).map(::toScreenPoint)
                                        ) ?: return@onDrag
                                        val acceptedDelta = clampDragDeltaToBounds(
                                            childBounds = currentScreenBounds,
                                            parentBounds = parentScreenBounds,
                                            requestedDelta = totalDelta
                                        )
                                        val contentDelta = screenDeltaToContentDelta(
                                            screenDelta = acceptedDelta,
                                            totalRotationDeg = totalRotationDeg,
                                            contentScale = (mapZoom * baseScale).coerceAtLeast(
                                                0.0001f
                                            )
                                        )
                                        forbiddenShapes = forbiddenShapes.map { shape ->
                                            if (shape.id == startShape.id) {
                                                shape.copy(
                                                    rect = Rect(
                                                        left = startRect.left + contentDelta.x,
                                                        top = startRect.top + contentDelta.y,
                                                        right = startRect.right + contentDelta.x,
                                                        bottom = startRect.bottom + contentDelta.y
                                                    )
                                                )
                                            } else {
                                                shape
                                            }
                                        }
                                    },
                                    onEnd = { forbiddenMoveStartShape = null },
                                    onCancel = {
                                        forbiddenMoveStartShape?.let { startShape ->
                                            forbiddenShapes = forbiddenShapes.map { shape ->
                                                if (shape.id == startShape.id) startShape else shape
                                            }
                                        }
                                        forbiddenMoveStartShape = null
                                    }
                                ),
                                resizeGesture = DragGestureCallbacks(
                                    onStart = {
                                        val id = editingForbiddenId
                                        forbiddenResizeStartShape =
                                            forbiddenShapes.firstOrNull { it.id == id }
                                    },
                                    onDrag = onDrag@{ totalDelta ->
                                        val startShape = forbiddenResizeStartShape ?: return@onDrag
                                        val startRect = startShape.rect ?: return@onDrag
                                        val parentScreenBounds = currentWorkspaceScreenBounds()
                                            ?: return@onDrag
                                        val minimumSize = calculateForbiddenMinimumContentSize(
                                            bitmapWidth = mapImageBitmap?.width ?: 0,
                                            bitmapHeight = mapImageBitmap?.height ?: 0,
                                            mapWidth = uiState.mapGeo?.mapWidth ?: 0,
                                            mapHeight = uiState.mapGeo?.mapHeight ?: 0,
                                            resolution = uiState.mapGeo?.resolution ?: 0f,
                                            rectangleRotationDeg = startShape.rotationDeg
                                        ) ?: return@onDrag
                                        val startContentCorners = rotatedRectCorners(
                                            topLeft = startRect.topLeft,
                                            width = startRect.width,
                                            height = startRect.height,
                                            rotationDeg = startShape.rotationDeg
                                        )
                                        val startScreenCorners =
                                            startContentCorners.map(::toScreenPoint)

                                        fun topLeftForSize(size: Size): Offset =
                                            topLeftForAnchoredRotatedRect(
                                                anchoredTopLeftCorner = startContentCorners[0],
                                                size = size,
                                                rotationDeg = startShape.rotationDeg
                                            )

                                        val contentDelta = projectScreenDeltaOntoRectAxes(
                                            screenDelta = totalDelta,
                                            screenCorners = startScreenCorners,
                                            contentScale = (mapZoom * baseScale).coerceAtLeast(
                                                0.0001f
                                            )
                                        )
                                        val nextSize = clampRectSizeToBounds(
                                            startSize = Size(startRect.width, startRect.height),
                                            requestedDelta = contentDelta,
                                            minimumSize = Size(
                                                minimumSize.rectangleWidth,
                                                minimumSize.rectangleHeight
                                            ),
                                            bounds = parentScreenBounds
                                        ) { candidateSize ->
                                            boundsOfPoints(
                                                rotatedRectCorners(
                                                    topLeft = topLeftForSize(candidateSize),
                                                    width = candidateSize.width,
                                                    height = candidateSize.height,
                                                    rotationDeg = startShape.rotationDeg
                                                ).map(::toScreenPoint)
                                            ) ?: Rect.Zero
                                        }
                                        val nextTopLeft = topLeftForSize(nextSize)
                                        val nextRect = Rect(
                                            left = nextTopLeft.x,
                                            top = nextTopLeft.y,
                                            right = nextTopLeft.x + nextSize.width,
                                            bottom = nextTopLeft.y + nextSize.height
                                        )
                                        forbiddenShapes = forbiddenShapes.map { shape ->
                                            if (shape.id == startShape.id) shape.copy(rect = nextRect) else shape
                                        }
                                    },
                                    onEnd = { forbiddenResizeStartShape = null },
                                    onCancel = {
                                        forbiddenResizeStartShape?.let { startShape ->
                                            forbiddenShapes = forbiddenShapes.map { shape ->
                                                if (shape.id == startShape.id) startShape else shape
                                            }
                                        }
                                        forbiddenResizeStartShape = null
                                    }
                                ),
                                rotationGesture = RotationGestureCallbacks(
                                    onStart = {
                                        val id = editingForbiddenId
                                        forbiddenRotateStartShape =
                                            forbiddenShapes.firstOrNull { it.id == id }
                                    },
                                    onRotate = onRotate@{ screenRotation ->
                                        val startShape =
                                            forbiddenRotateStartShape ?: return@onRotate
                                        val startRect = startShape.rect ?: return@onRotate
                                        val parentScreenBounds = currentWorkspaceScreenBounds()
                                            ?: return@onRotate
                                        val requestedRotation = normalizeDegrees(
                                            screenRotation - totalRotationDeg
                                        )
                                        val nextRotation = clampRotationToBounds(
                                            startRotationDeg = startShape.rotationDeg,
                                            requestedRotationDeg = requestedRotation,
                                            bounds = parentScreenBounds
                                        ) { candidateRotation ->
                                            boundsOfPoints(
                                                rotatedRectCorners(
                                                    topLeft = startRect.topLeft,
                                                    width = startRect.width,
                                                    height = startRect.height,
                                                    rotationDeg = candidateRotation
                                                ).map(::toScreenPoint)
                                            ) ?: Rect.Zero
                                        }
                                        forbiddenShapes = forbiddenShapes.map { shape ->
                                            if (shape.id == startShape.id) {
                                                shape.copy(rotationDeg = nextRotation)
                                            } else {
                                                shape
                                            }
                                        }
                                    },
                                    onEnd = { forbiddenRotateStartShape = null },
                                    onCancel = {
                                        forbiddenRotateStartShape?.let { startShape ->
                                            forbiddenShapes = forbiddenShapes.map { shape ->
                                                if (shape.id == startShape.id) startShape else shape
                                            }
                                        }
                                        forbiddenRotateStartShape = null
                                    }
                                )
                            )
                        }
                        editingShape?.circleCenter?.let { center ->
                            if (editingShape.circleRadius > 0f) {
                                val screenCenter = toScreenPoint(center)
                                ForbiddenCircleHandles(
                                    center = screenCenter,
                                    radius = editingShape.circleRadius * baseScale * mapZoom,
                                    onClose = {
                                        val id = editingForbiddenId ?: return@ForbiddenCircleHandles
                                        forbiddenShapes = forbiddenShapes.filterNot { it.id == id }
                                        editingForbiddenId = null
                                        forbiddenMoveStartShape = null
                                        forbiddenResizeStartShape = null
                                    },
                                    onConfirm = {
                                        viewModel.confirmForbiddenCircle(
                                            center = contentPairToBitmapPair(center.x to center.y),
                                            radius = editingShape.circleRadius.coerceAtLeast(0f)
                                        )
                                        editingForbiddenId = null
                                    },
                                    moveGesture = DragGestureCallbacks(
                                        onStart = {
                                            val id = editingForbiddenId
                                            forbiddenMoveStartShape =
                                                forbiddenShapes.firstOrNull { it.id == id }
                                        },
                                        onDrag = onDrag@{ totalDelta ->
                                            val startShape =
                                                forbiddenMoveStartShape ?: return@onDrag
                                            val startCenter =
                                                startShape.circleCenter ?: return@onDrag
                                            val parentScreenBounds = currentWorkspaceScreenBounds()
                                                ?: return@onDrag
                                            val contentScale =
                                                (mapZoom * baseScale).coerceAtLeast(0.0001f)
                                            val currentScreenCenter = toScreenPoint(startCenter)
                                            val screenRadius =
                                                startShape.circleRadius * contentScale
                                            val currentScreenBounds = Rect(
                                                left = currentScreenCenter.x - screenRadius,
                                                top = currentScreenCenter.y - screenRadius,
                                                right = currentScreenCenter.x + screenRadius,
                                                bottom = currentScreenCenter.y + screenRadius
                                            )
                                            val acceptedDelta = clampDragDeltaToBounds(
                                                childBounds = currentScreenBounds,
                                                parentBounds = parentScreenBounds,
                                                requestedDelta = totalDelta
                                            )
                                            val contentDelta = screenDeltaToContentDelta(
                                                screenDelta = acceptedDelta,
                                                totalRotationDeg = (uiState.mapGeo?.alignmentYawDeg
                                                    ?: 0f) +
                                                        (uiState.mapGeo?.rotationAlignmentDeltaDeg
                                                            ?: 0f),
                                                contentScale = contentScale
                                            )
                                            val nextCenter = Offset(
                                                x = startCenter.x + contentDelta.x,
                                                y = startCenter.y + contentDelta.y
                                            )
                                            forbiddenShapes = forbiddenShapes.map { item ->
                                                if (item.id == startShape.id) {
                                                    item.copy(circleCenter = nextCenter)
                                                } else {
                                                    item
                                                }
                                            }
                                        },
                                        onEnd = { forbiddenMoveStartShape = null },
                                        onCancel = {
                                            forbiddenMoveStartShape?.let { startShape ->
                                                forbiddenShapes = forbiddenShapes.map { shape ->
                                                    if (shape.id == startShape.id) startShape else shape
                                                }
                                            }
                                            forbiddenMoveStartShape = null
                                        }
                                    ),
                                    resizeGesture = DragGestureCallbacks(
                                        onStart = {
                                            val id = editingForbiddenId
                                            forbiddenResizeStartShape =
                                                forbiddenShapes.firstOrNull { it.id == id }
                                        },
                                        onDrag = onDrag@{ totalDelta ->
                                            val startShape =
                                                forbiddenResizeStartShape ?: return@onDrag
                                            val startCenter =
                                                startShape.circleCenter ?: return@onDrag
                                            val parentScreenBounds = currentWorkspaceScreenBounds()
                                                ?: return@onDrag
                                            val minimumSize = calculateForbiddenMinimumContentSize(
                                                bitmapWidth = mapImageBitmap?.width ?: 0,
                                                bitmapHeight = mapImageBitmap?.height ?: 0,
                                                mapWidth = uiState.mapGeo?.mapWidth ?: 0,
                                                mapHeight = uiState.mapGeo?.mapHeight ?: 0,
                                                resolution = uiState.mapGeo?.resolution ?: 0f,
                                                rectangleRotationDeg = 0f
                                            ) ?: return@onDrag
                                            val contentScale =
                                                (mapZoom * baseScale).coerceAtLeast(0.0001f)
                                            val startScreenCenter = toScreenPoint(startCenter)
                                            val deltaRadius = radialScreenDeltaToContentDelta(
                                                screenDelta = totalDelta,
                                                center = startScreenCenter,
                                                handleCenter = startScreenCenter + Offset(
                                                    0.85f,
                                                    0.35f
                                                ),
                                                contentScale = contentScale
                                            )
                                            val maximumRadius = min(
                                                min(
                                                    startScreenCenter.x - parentScreenBounds.left,
                                                    parentScreenBounds.right - startScreenCenter.x
                                                ),
                                                min(
                                                    startScreenCenter.y - parentScreenBounds.top,
                                                    parentScreenBounds.bottom - startScreenCenter.y
                                                )
                                            ) / contentScale
                                            val newRadius = resizeForbiddenCircleRadius(
                                                currentRadius = startShape.circleRadius,
                                                deltaRadius = deltaRadius,
                                                maximumRadius = maximumRadius,
                                                minimumRadius = minimumSize.circleRadius
                                            )
                                            forbiddenShapes = forbiddenShapes.map { item ->
                                                if (item.id == startShape.id) {
                                                    item.copy(circleRadius = newRadius)
                                                } else {
                                                    item
                                                }
                                            }
                                        },
                                        onEnd = { forbiddenResizeStartShape = null },
                                        onCancel = {
                                            forbiddenResizeStartShape?.let { startShape ->
                                                forbiddenShapes = forbiddenShapes.map { shape ->
                                                    if (shape.id == startShape.id) startShape else shape
                                                }
                                            }
                                            forbiddenResizeStartShape = null
                                        }
                                    )
                                )
                            }
                        }

                        val forbiddenMetricText = editingShape?.let metric@{ shape ->
                            shape.rect?.let rectangle@{ rect ->
                                val worldCorners = pointsToWorld(
                                    rotatedRectCorners(
                                        topLeft = rect.topLeft,
                                        width = rect.width,
                                        height = rect.height,
                                        rotationDeg = shape.rotationDeg
                                    )
                                ) ?: return@rectangle null
                                calculateForbiddenRectangleMetrics(worldCorners)?.let(::formatForbiddenMetrics)
                            } ?: shape.circleCenter?.let circle@{ center ->
                                if (shape.circleRadius <= 0f) return@circle null
                                val worldCenter = contentPointToWorld(center) ?: return@circle null
                                val worldEdge = contentPointToWorld(
                                    center + Offset(shape.circleRadius, 0f)
                                ) ?: return@circle null
                                calculateForbiddenCircleMetrics(
                                    radiusMeters = metersBetween(worldCenter, worldEdge)
                                )?.let(::formatForbiddenMetrics)
                            } ?: return@metric null
                        }
                        forbiddenMetricText?.let { text ->
                            RegionMetricInfoBar(
                                text = text,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            )
                        }

                        val enableBoundaryRectOverlay =
                            uiState.panelMode == WorkspacePanelMode.BOUNDARY_EDITOR &&
                                    uiState.workspaceDraft.boundaryDrawMode == BoundaryDrawMode.RECTANGLE &&
                                    showBoundaryRect
                        val enableBoundaryPolygonOverlay =
                            uiState.panelMode == WorkspacePanelMode.BOUNDARY_EDITOR &&
                                    uiState.workspaceDraft.boundaryDrawMode == BoundaryDrawMode.POLYGON

                        if (enableBoundaryPolygonOverlay) {
                            val polygonScreenPoints = polygonPoints.map { toScreenPoint(it) }
                            val polygonWorldPoints = pointsToWorld(polygonPoints)
                            val polygonEdgeLabels = if (
                                polygonWorldPoints != null &&
                                polygonWorldPoints.size == polygonScreenPoints.size &&
                                polygonWorldPoints.size >= 2
                            ) {
                                buildList {
                                    for (i in 0 until polygonWorldPoints.lastIndex) {
                                        val center = midpoint(
                                            polygonScreenPoints[i],
                                            polygonScreenPoints[i + 1]
                                        )
                                        val lengthM = metersBetween(
                                            polygonWorldPoints[i],
                                            polygonWorldPoints[i + 1]
                                        )
                                        add(
                                            EdgeMetricLabel(
                                                center = center,
                                                text = formatMeters(lengthM)
                                            )
                                        )
                                    }
                                    if (polygonWorldPoints.size >= 3) {
                                        val last = polygonWorldPoints.lastIndex
                                        val closeCenter = midpoint(
                                            polygonScreenPoints[last],
                                            polygonScreenPoints[0]
                                        )
                                        val closeLen = metersBetween(
                                            polygonWorldPoints[last],
                                            polygonWorldPoints[0]
                                        )
                                        add(
                                            EdgeMetricLabel(
                                                center = closeCenter,
                                                text = formatMeters(closeLen)
                                            )
                                        )
                                    }
                                }
                            } else {
                                emptyList()
                            }
                            val polygonAreaLabel = if (
                                polygonWorldPoints != null && polygonWorldPoints.size >= 3
                            ) {
                                val areaCenter = polygonCentroid(polygonScreenPoints)
                                EdgeMetricLabel(
                                    center = areaCenter,
                                    text = formatArea(polygonAreaM2(polygonWorldPoints))
                                )
                            } else {
                                null
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(mapPanelSize, polygonPoints) {
                                        detectTapGestures { tapOffset ->
                                            val mapScreenBounds = mapImageScreenRectOrNull()
                                                ?: return@detectTapGestures
                                            val clampedScreenPoint = clampPointToBounds(
                                                point = tapOffset,
                                                bounds = mapScreenBounds
                                            )
                                            if (clampedScreenPoint != tapOffset) return@detectTapGestures
                                            val contentPoint = toContentPoint(clampedScreenPoint)
                                            updateLastTapWorldPoint(contentPoint)
                                            polygonPoints = polygonPoints + contentPoint
                                        }
                                    }
                            )
                            BoundaryPolygonOverlay(
                                points = polygonScreenPoints,
                                edgeLabels = polygonEdgeLabels,
                                areaLabel = polygonAreaLabel,
                                onClose = { polygonPoints = emptyList() },
                                onConfirm = {
                                    confirmedBoundaryPolygon = polygonPoints
                                    confirmedBoundaryRectCorners = emptyList()
                                    val pointsToConfirm =
                                        polygonPoints.map { contentPairToBitmapPair(it.x to it.y) }
                                    viewModel.confirmBoundaryPolygonPoints(pointsToConfirm)
                                    polygonPoints = emptyList()
                                },
                                pointGesture = { pointIndex ->
                                    DragGestureCallbacks(
                                        onStart = {
                                            polygonDragStartPoints = polygonPoints
                                        },
                                        onDrag = onDrag@{ totalDelta ->
                                            val startPoints =
                                                polygonDragStartPoints ?: return@onDrag
                                            val startContentPoint =
                                                startPoints.getOrNull(pointIndex)
                                                    ?: return@onDrag
                                            val mapScreenBounds = mapImageScreenRectOrNull()
                                                ?: return@onDrag
                                            val targetScreenPoint = clampPointToBounds(
                                                point = toScreenPoint(startContentPoint) + totalDelta,
                                                bounds = mapScreenBounds
                                            )
                                            val nextContentPoint = toContentPoint(targetScreenPoint)
                                            polygonPoints = startPoints.mapIndexed { index, point ->
                                                if (index == pointIndex) nextContentPoint else point
                                            }
                                        },
                                        onEnd = { polygonDragStartPoints = null },
                                        onCancel = {
                                            polygonDragStartPoints?.let { polygonPoints = it }
                                            polygonDragStartPoints = null
                                        }
                                    )
                                }
                            )
                        }

                        if (enableBoundaryRectOverlay && mapPanelSize.width > 0 && mapPanelSize.height > 0) {
                            val totalRotationDeg =
                                (uiState.mapGeo?.alignmentYawDeg ?: 0f) +
                                        (uiState.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
                            val rectContentCorners = rotatedRectCorners(
                                topLeft = boundaryTopLeft,
                                width = boundarySize.width.toFloat(),
                                height = boundarySize.height.toFloat(),
                                rotationDeg = boundaryRotationDeg
                            )
                            val rectWorldCorners = pointsToWorld(
                                rectContentCorners
                            )
                            val topLengthText = rectWorldCorners?.let {
                                formatMeters(metersBetween(it[0], it[1]))
                            }
                            val leftLengthText = rectWorldCorners?.let {
                                formatMeters(metersBetween(it[0], it[3]))
                            }
                            val areaText = rectWorldCorners?.let { corners ->
                                formatArea(polygonAreaM2(corners))
                            }
                            val boundaryScreenCorners = rectContentCorners.map(::toScreenPoint)
                            BoundaryRectOverlay(
                                corners = boundaryScreenCorners,
                                rectWidth = boundarySize.width * baseScale * mapZoom,
                                rectHeight = boundarySize.height * baseScale * mapZoom,
                                topLengthText = topLengthText,
                                leftLengthText = leftLengthText,
                                areaText = areaText,
                                moveGesture = DragGestureCallbacks(
                                    onStart = { boundaryMoveStartTopLeft = boundaryTopLeft },
                                    onDrag = onDrag@{ totalDelta ->
                                        val startTopLeft = boundaryMoveStartTopLeft ?: return@onDrag
                                        val mapScreenBounds =
                                            mapImageScreenRectOrNull() ?: return@onDrag
                                        val startScreenBounds = boundsOfPoints(
                                            rotatedRectCorners(
                                                topLeft = startTopLeft,
                                                width = boundarySize.width.toFloat(),
                                                height = boundarySize.height.toFloat(),
                                                rotationDeg = boundaryRotationDeg
                                            ).map(::toScreenPoint)
                                        ) ?: return@onDrag
                                        val acceptedDelta = clampDragDeltaToBounds(
                                            childBounds = startScreenBounds,
                                            parentBounds = mapScreenBounds,
                                            requestedDelta = totalDelta
                                        )
                                        boundaryTopLeft = startTopLeft + screenDeltaToContentDelta(
                                            screenDelta = acceptedDelta,
                                            totalRotationDeg = totalRotationDeg,
                                            contentScale = (mapZoom * baseScale).coerceAtLeast(
                                                0.0001f
                                            )
                                        )
                                    },
                                    onEnd = { boundaryMoveStartTopLeft = null },
                                    onCancel = {
                                        boundaryMoveStartTopLeft?.let { boundaryTopLeft = it }
                                        boundaryMoveStartTopLeft = null
                                    }
                                ),
                                resizeGesture = DragGestureCallbacks(
                                    onStart = {
                                        boundaryResizeStartTopLeft = boundaryTopLeft
                                        boundaryResizeStartSize = boundarySize
                                    },
                                    onDrag = onDrag@{ totalDelta ->
                                        val startSize = boundaryResizeStartSize ?: return@onDrag
                                        val startTopLeft =
                                            boundaryResizeStartTopLeft ?: return@onDrag
                                        val mapScreenBounds =
                                            mapImageScreenRectOrNull() ?: return@onDrag
                                        val startContentCorners = rotatedRectCorners(
                                            topLeft = startTopLeft,
                                            width = startSize.width.toFloat(),
                                            height = startSize.height.toFloat(),
                                            rotationDeg = boundaryRotationDeg
                                        )
                                        val startScreenCorners =
                                            startContentCorners.map(::toScreenPoint)

                                        fun topLeftForSize(size: Size): Offset =
                                            topLeftForAnchoredRotatedRect(
                                                anchoredTopLeftCorner = startContentCorners[0],
                                                size = size,
                                                rotationDeg = boundaryRotationDeg
                                            )

                                        val contentDelta = projectScreenDeltaOntoRectAxes(
                                            screenDelta = totalDelta,
                                            screenCorners = startScreenCorners,
                                            contentScale = (mapZoom * baseScale).coerceAtLeast(
                                                0.0001f
                                            )
                                        )
                                        val nextSize = clampRectSizeToBounds(
                                            startSize = Size(
                                                startSize.width.toFloat(),
                                                startSize.height.toFloat()
                                            ),
                                            requestedDelta = contentDelta,
                                            minimumSize = Size(1f, 1f),
                                            bounds = mapScreenBounds
                                        ) { candidateSize ->
                                            boundsOfPoints(
                                                rotatedRectCorners(
                                                    topLeft = topLeftForSize(candidateSize),
                                                    width = candidateSize.width,
                                                    height = candidateSize.height,
                                                    rotationDeg = boundaryRotationDeg
                                                ).map(::toScreenPoint)
                                            ) ?: Rect.Zero
                                        }
                                        boundaryTopLeft = topLeftForSize(nextSize)
                                        boundarySize = IntSize(
                                            nextSize.width.roundToInt().coerceAtLeast(1),
                                            nextSize.height.roundToInt().coerceAtLeast(1)
                                        )
                                    },
                                    onEnd = {
                                        boundaryResizeStartTopLeft = null
                                        boundaryResizeStartSize = null
                                    },
                                    onCancel = {
                                        boundaryResizeStartTopLeft?.let { boundaryTopLeft = it }
                                        boundaryResizeStartSize?.let { boundarySize = it }
                                        boundaryResizeStartTopLeft = null
                                        boundaryResizeStartSize = null
                                    }
                                ),
                                rotationGesture = RotationGestureCallbacks(
                                    onStart = { boundaryRotateStartDeg = boundaryRotationDeg },
                                    onRotate = onRotate@{ screenRotation ->
                                        val startRotation =
                                            boundaryRotateStartDeg ?: return@onRotate
                                        val mapScreenBounds =
                                            mapImageScreenRectOrNull() ?: return@onRotate
                                        val requestedRotation = normalizeDegrees(
                                            screenRotation - totalRotationDeg
                                        )
                                        boundaryRotationDeg = clampRotationToBounds(
                                            startRotationDeg = startRotation,
                                            requestedRotationDeg = requestedRotation,
                                            bounds = mapScreenBounds
                                        ) { candidateRotation ->
                                            boundsOfPoints(
                                                rotatedRectCorners(
                                                    topLeft = boundaryTopLeft,
                                                    width = boundarySize.width.toFloat(),
                                                    height = boundarySize.height.toFloat(),
                                                    rotationDeg = candidateRotation
                                                ).map(::toScreenPoint)
                                            ) ?: Rect.Zero
                                        }
                                    },
                                    onEnd = { boundaryRotateStartDeg = null },
                                    onCancel = {
                                        boundaryRotateStartDeg?.let { boundaryRotationDeg = it }
                                        boundaryRotateStartDeg = null
                                    }
                                ),
                                onClose = {
                                    showBoundaryRect = false
                                    boundaryMoveStartTopLeft = null
                                    boundaryResizeStartTopLeft = null
                                    boundaryResizeStartSize = null
                                    boundaryRotateStartDeg = null
                                },
                                onConfirm = {
                                    val corners = rotatedRectCorners(
                                        topLeft = boundaryTopLeft,
                                        width = boundarySize.width.toFloat(),
                                        height = boundarySize.height.toFloat(),
                                        rotationDeg = boundaryRotationDeg
                                    )
                                    confirmedBoundaryRectCorners = corners
                                    confirmedBoundaryPolygon = emptyList()
                                    showBoundaryRect = false
                                    viewModel.confirmBoundaryCorners(
                                        topLeft = contentPairToBitmapPair(corners[0].x to corners[0].y),
                                        topRight = contentPairToBitmapPair(corners[1].x to corners[1].y),
                                        bottomRight = contentPairToBitmapPair(corners[2].x to corners[2].y),
                                        bottomLeft = contentPairToBitmapPair(corners[3].x to corners[3].y)
                                    )
                                }
                            )
                        }

                        val enableGenericTapCapture =
                            uiState.panelMode != WorkspacePanelMode.BOUNDARY_EDITOR &&
                                    uiState.panelMode != WorkspacePanelMode.FORBIDDEN_EDITOR &&
                                    pendingForbiddenMode == null &&
                                    pendingAnchorPlacement == null &&
                                    !enableBoundaryPolygonOverlay
                        if (enableGenericTapCapture) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(
                                        mapZoom,
                                        mapPan,
                                        baseScale,
                                        baseOffset,
                                        mapImageBitmap
                                    ) {
                                        detectTapGestures { tapOffset ->
                                            if (!isTapInsideVisibleMap(tapOffset)) return@detectTapGestures
                                            val mapBounds =
                                                mapContentBoundsOrNull() ?: return@detectTapGestures
                                            val contentTap = toContentPoint(tapOffset)
                                            val clampedTap = clampPointToRect(contentTap, mapBounds)
                                            updateLastTapWorldPoint(clampedTap)
                                        }
                                    }
                            )
                        }

                        lastTappedWorldPoint?.let { world ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                                color = Color(0xCC202937),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = String.format(
                                        "世界坐标: (%.3f, %.3f) m",
                                        world.first,
                                        world.second
                                    ),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                WorkspacePanel(
                    panelMode = uiState.panelMode,
                    workspaces = uiState.workspaces,
                    workspaceDraft = uiState.workspaceDraft,
                    isEditingWorkspace = uiState.editingWorkspaceRegionId != null,
                    isWorkspaceSaving = uiState.isWorkspaceSaving,
                    onAddWorkspace = {
                        clearCurrentWorkspaceGeometry()
                        viewModel.addWorkspace()
                    },
                    onEditWorkspace = { regionId ->
                        val draft = viewModel.editWorkspace(regionId)
                        if (draft != null) loadWorkspaceGeometry(regionId, draft)
                    },
                    onCancelAddWorkspace = {
                        clearCurrentWorkspaceGeometry()
                        viewModel.cancelAddWorkspace()
                    },
                    onSaveWorkspace = {
                        val rectanglePoints = forbiddenShapes.mapNotNull { shape ->
                            shape.rect?.let { rect ->
                                rotatedRectCorners(
                                    rect.topLeft,
                                    rect.width,
                                    rect.height,
                                    shape.rotationDeg
                                )
                                    .map { it.x to it.y }
                            }
                        }
                        val circles = forbiddenShapes.mapNotNull { shape ->
                            shape.circleCenter?.let { center -> (center.x to center.y) to shape.circleRadius }
                        }
                        viewModel.replaceForbiddenGeometry(rectanglePoints, circles)
                        val geometrySnapshot = currentWorkspaceGeometryOrNull("pending")
                        coroutineScope.launch {
                            val savedRegionId = viewModel.saveWorkspace()
                            val geometry =
                                savedRegionId?.let { geometrySnapshot?.copy(regionId = it) }
                            if (savedRegionId != null && geometry != null) {
                                savedWorkspaceGeometries =
                                    savedWorkspaceGeometries.filterNot { it.regionId == savedRegionId } + geometry
                                clearCurrentWorkspaceGeometry()
                            }
                        }
                    },
                    onOpenBoundaryEditor = {
                        confirmedBoundaryRectCorners = emptyList()
                        confirmedBoundaryPolygon = emptyList()
                        polygonPoints = emptyList()
                        showBoundaryRect = true
                        placeInitialBoundaryRect()
                        viewModel.openBoundaryEditor()
                    },
                    onCloseBoundaryEditor = viewModel::closeBoundaryEditor,
                    onSelectBoundaryDrawMode = { mode ->
                        pendingAnchorPlacement = null
                        showBoundaryRect = true
                        if (mode == BoundaryDrawMode.POLYGON) {
                            polygonPoints = emptyList()
                        } else if (uiState.workspaceDraft.boundaryDrawMode != BoundaryDrawMode.RECTANGLE) {
                            placeInitialBoundaryRect()
                        }
                        viewModel.setBoundaryDrawMode(mode)
                    },
                    onOpenForbiddenEditor = {
                        pendingAnchorPlacement = null
                        forbiddenShapes = emptyList()
                        editingForbiddenId = null
                        nextForbiddenId = 1L
                        // Entering the editor means the previous forbidden regions are replaced.
                        pendingForbiddenMode = ForbiddenDrawMode.RECTANGLE
                        viewModel.openForbiddenEditor()
                    },
                    onCloseForbiddenEditor = {
                        pendingAnchorPlacement = null
                        pendingForbiddenMode = null
                        val editingId = editingForbiddenId
                        if (editingId != null) {
                            // Drop the in-progress forbidden shape when leaving editor without confirm.
                            forbiddenShapes = forbiddenShapes.filterNot { it.id == editingId }
                            editingForbiddenId = null
                        }
                        viewModel.closeForbiddenEditor()
                    },
                    onSelectForbiddenDrawMode = { mode ->
                        pendingAnchorPlacement = null
                        pendingForbiddenMode = mode
                        viewModel.setForbiddenDrawMode(mode)
                    },
                    onCloseRobotSettingsEditor = viewModel::closeRobotSettingsEditor,
                    onOpenProcessSettingsEditor = viewModel::openProcessSettingsEditor,
                    onCloseProcessSettingsEditor = viewModel::closeProcessSettingsEditor,
                    onDecreaseRobotWidth = viewModel::decreaseRobotWidth,
                    onIncreaseRobotWidth = viewModel::increaseRobotWidth,
                    onRobotWidthChange = viewModel::updateRobotWidth,
                    onDecreaseRobotLength = viewModel::decreaseRobotLength,
                    onIncreaseRobotLength = viewModel::increaseRobotLength,
                    onRobotLengthChange = viewModel::updateRobotLength,
                    onDecreaseRobotPathSpacing = viewModel::decreaseRobotPathSpacing,
                    onIncreaseRobotPathSpacing = viewModel::increaseRobotPathSpacing,
                    onRobotPathSpacingChange = viewModel::updateRobotPathSpacing,
                    onDecreaseRobotCoverage = viewModel::decreaseRobotCoverage,
                    onIncreaseRobotCoverage = viewModel::increaseRobotCoverage,
                    onRobotCoverageChange = viewModel::updateRobotCoverage,
                    onSaveRobotSettings = {
                        viewModel.saveRobotSettings()
                        viewModel.closeRobotSettingsEditor()
                    },
                    onClearWorkspaces = {
                        coroutineScope.launch {
                            val deletedRegionIds = viewModel.deleteAllWorkspaces()
                            savedWorkspaceGeometries =
                                savedWorkspaceGeometries.filterNot { it.regionId in deletedRegionIds }
                            clearCurrentWorkspaceGeometry()
                        }
                    },
                    onDeleteWorkspace = { regionId ->
                        coroutineScope.launch {
                            if (viewModel.deleteWorkspace(regionId)) {
                                savedWorkspaceGeometries =
                                    savedWorkspaceGeometries.filterNot { it.regionId == regionId }
                            }
                        }
                    },
                    onAreaCodeChange = viewModel::updateAreaCode,
                    onToggleBoundaryConfigured = viewModel::setBoundaryConfigured,
                    onToggleForbiddenConfigured = viewModel::setForbiddenConfigured,
                    onToggleStartConfigured = { configured ->
                        viewModel.setStartConfigured(configured)
                        if (!configured) {
                            startPoint = null
                            if (pendingAnchorPlacement == PendingAnchorPlacement.START) {
                                pendingAnchorPlacement = null
                            }
                        }
                    },
                    onToggleEndConfigured = { configured ->
                        viewModel.setEndConfigured(configured)
                        if (!configured) {
                            endPoint = null
                            if (pendingAnchorPlacement == PendingAnchorPlacement.END) {
                                pendingAnchorPlacement = null
                            }
                        }
                    },
                    onPlaceStartPoint = { pendingAnchorPlacement = PendingAnchorPlacement.START },
                    onPlaceEndPoint = { pendingAnchorPlacement = PendingAnchorPlacement.END },
                    onAddWorkspaceScreenEnter = viewModel::onAddWorkspaceScreenEnter,
                    onDecreasePathSpacing = viewModel::decreasePathSpacing,
                    onIncreasePathSpacing = viewModel::increasePathSpacing,
                    onPreviousScanDirection = viewModel::previousScanDirection,
                    onNextScanDirection = viewModel::nextScanDirection,
                    onOpenRobotSettings = viewModel::openRobotSettingsEditor,
                    onOpenProcessSettings = viewModel::openProcessSettingsEditor,
                    onSelectProcessType = viewModel::setProcessType,
                    onSelectGrindingParameter = viewModel::setGrindingParameter,
                    onSelectConcreteStrength = viewModel::setConcreteStrength,
                    onDecreaseProcessSpeed = viewModel::decreaseProcessSpeed,
                    onIncreaseProcessSpeed = viewModel::increaseProcessSpeed,
                    onProcessSpeedChange = viewModel::updateProcessSpeed,
                    onDecreaseProcessPressure = viewModel::decreaseProcessPressure,
                    onIncreaseProcessPressure = viewModel::increaseProcessPressure,
                    onProcessPressureChange = viewModel::updateProcessPressure,
                    onDecreaseDiscSpeed = viewModel::decreaseDiscSpeed,
                    onIncreaseDiscSpeed = viewModel::increaseDiscSpeed,
                    onDiscSpeedChange = viewModel::updateDiscSpeed,
                    onSaveProcessSettings = {
                        viewModel.saveProcessSettings()
                        viewModel.closeProcessSettingsEditor()
                    },
                    modifier = Modifier
                        .width(336.dp)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            uiState.mapLoadError?.let { error ->
                Text(
                    error,
                    color = Color.Red,
                    modifier = Modifier.clickable { viewModel.loadMapData(mapId) })
            }
            BottomStep3Actions(
                onCancelBuild = {
                    viewModel.onCancelBuild()
                    onCancelBuild()
                },
                onPreviousStep = onPreviousStep,
                onNextStep = {
                    when {
                        // 区域加载失败或底图缺失时留在当前页，不让 Step4 使用不完整的建图上下文。
                        uiState.isMapLoading || uiState.mapLoadError != null || uiState.mapImageBytes == null -> {
                            ToastUtils.showError(uiState.mapLoadError ?: "请等待地图区域加载完成")
                        }

                        uiState.panelMode == WorkspacePanelMode.ADD -> {
                            ToastUtils.showWarningDark("请先保存工作区")
                        }

                        uiState.workspaces.isEmpty() -> {
                            ToastUtils.showWarningDark("请先新增工作区")
                        }

                        else -> {
                            onNextStep(uiState.latestScanDirection)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun AnchorPointMarker(
    text: String,
    color: Color,
    center: Offset
) {
    val markerSize = 12.dp
    val markerHalfPx = with(LocalDensity.current) { (markerSize / 2).toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - markerHalfPx).roundToInt(),
                    (center.y - markerHalfPx).roundToInt()
                )
            }
            .size(markerSize)
            .clip(RoundedCornerShape(1000.dp))
            .background(Color.White)
            .border(1.dp, color, RoundedCornerShape(1000.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier.offset(y = (-0.4).dp)
        )
    }
}

@Composable
private fun BoundaryRectOverlay(
    corners: List<Offset>,
    rectWidth: Float,
    rectHeight: Float,
    topLengthText: String?,
    leftLengthText: String?,
    areaText: String?,
    moveGesture: DragGestureCallbacks,
    resizeGesture: DragGestureCallbacks,
    rotationGesture: RotationGestureCallbacks,
    onClose: () -> Unit,
    onConfirm: () -> Unit
) {
    if (corners.size != 4) return
    // Compose 命中区保持屏幕轴对齐；真实旋转矩形仍由 corners 表示，不能用该包围框持久化。
    val gestureBounds = rectFromCorners(corners)
    val center = midpoint(corners[0], corners[2])
    val path = pathFromCorners(corners)
    val screenTopLeft = Offset(gestureBounds.left, gestureBounds.top)
    val screenTopRight = Offset(gestureBounds.right, gestureBounds.top)
    val screenBottomRight = Offset(gestureBounds.right, gestureBounds.bottom)
    val screenBottomLeft = Offset(gestureBounds.left, gestureBounds.bottom)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawPath(path = path, color = Color(0x223AB54A))
        drawPath(
            path = path,
            color = Color(0xFF24BE1C),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(10.dp.toPx(), 8.dp.toPx())
                )
            )
        )
    }
    StableDragTarget(bounds = gestureBounds, gesture = moveGesture)

    topLengthText?.let { text ->
        MetricLabel(
            text = text,
            color = Color(0xFF202937),
            center = Offset((corners[0].x + corners[1].x) / 2f, (corners[0].y + corners[1].y) / 2f)
        )
    }
    leftLengthText?.let { text ->
        MetricLabel(
            text = text,
            color = Color(0xFF202937),
            center = Offset((corners[0].x + corners[3].x) / 2f, (corners[0].y + corners[3].y) / 2f)
        )
    }
    areaText?.let { text ->
        MetricLabel(
            text = text,
            color = Color(0xFF00A0E9),
            center = center
        )
    }

    BoundaryHandleButton(
        symbol = "✕",
        bg = Color(0xFFE53935),
        modifier = Modifier.offset {
            IntOffset(
                (screenTopLeft.x - 16f).roundToInt(),
                (screenTopLeft.y - 16f).roundToInt()
            )
        },
        onClick = onClose
    )
    BoundaryHandleButton(
        symbol = "✓",
        bg = Color(0xFF3AB54A),
        modifier = Modifier.offset {
            IntOffset(
                (screenTopRight.x - 16f).roundToInt(),
                (screenTopRight.y - 16f).roundToInt()
            )
        },
        onClick = onConfirm
    )
    BoundaryZoomHandleButton(
        handleCenter = screenBottomRight,
        gesture = resizeGesture
    )
    BoundaryRotateHandleButton(
        center = center,
        handleCenter = screenBottomLeft,
        // 可见按钮在包围框角上，旋转计算必须回到真实矩形角，避免起拖时角度跳变。
        rotationReferenceHandleCenter = corners[3],
        rectWidth = rectWidth,
        rectHeight = rectHeight,
        gesture = rotationGesture
    )
}

@Composable
private fun BoundaryPolygonOverlay(
    points: List<Offset>,
    edgeLabels: List<EdgeMetricLabel>,
    areaLabel: EdgeMetricLabel?,
    pointGesture: (Int) -> DragGestureCallbacks,
    onClose: () -> Unit,
    onConfirm: () -> Unit
) {
    val density = LocalDensity.current
    val pointHitRadiusPx = with(density) { 16.dp.toPx() }
    val controlOffsetPx = with(density) { 28.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size > 1) {
            for (i in 0 until points.lastIndex) {
                drawLine(
                    color = Color(0xFF24BE1C),
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10.dp.toPx(), 8.dp.toPx())
                    )
                )
            }
        }

        if (points.size >= 3) {
            val polygon = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path = polygon,
                color = Color(0x223AB54A)
            )
            drawPath(
                path = polygon,
                color = Color(0xFF24BE1C),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(10.dp.toPx(), 8.dp.toPx())
                    )
                )
            )
        }

        points.forEach { point ->
            drawCircle(
                color = Color(0xFF00A0E9),
                radius = 8.dp.toPx(),
                center = point
            )
        }
    }

    edgeLabels.forEach { label ->
        MetricLabel(
            text = label.text,
            color = Color(0xFF202937),
            center = label.center
        )
    }
    areaLabel?.let { label ->
        MetricLabel(
            text = label.text,
            color = Color(0xFF00A0E9),
            center = label.center
        )
    }

    points.forEachIndexed { index, point ->
        StableDragTarget(
            bounds = Rect(
                point.x - pointHitRadiusPx,
                point.y - pointHitRadiusPx,
                point.x + pointHitRadiusPx,
                point.y + pointHitRadiusPx
            ),
            gesture = pointGesture(index),
            clipToCircle = true,
            zIndex = 3f
        )
    }

    if (points.isNotEmpty()) {
        val first = points.first()
        BoundaryHandleButton(
            symbol = "✕",
            bg = Color(0xFFE53935),
            modifier = Modifier.offset {
                IntOffset(
                    (first.x - controlOffsetPx - 16f).roundToInt(),
                    (first.y - controlOffsetPx - 16f).roundToInt()
                )
            },
            onClick = onClose
        )
    }

    if (points.size >= 2) {
        val last = points.last()
        BoundaryHandleButton(
            symbol = "✓",
            bg = Color(0xFF3AB54A),
            modifier = Modifier.offset {
                IntOffset(
                    (last.x + controlOffsetPx - 16f).roundToInt(),
                    (last.y - controlOffsetPx - 16f).roundToInt()
                )
            },
            onClick = onConfirm
        )
    }
}

@Composable
private fun MetricLabel(
    text: String,
    color: Color,
    center: Offset
) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.offset {
            IntOffset(
                x = center.x.roundToInt() - 36,
                y = center.y.roundToInt() - 10
            )
        }
    )
}

@Composable
private fun RegionMetricInfoBar(
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

private fun midpoint(a: Offset, b: Offset): Offset {
    return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

private fun formatMeters(value: Float): String = String.format("%.2fm", value)

private fun formatArea(value: Float): String = String.format("%.2fm2", value)

internal fun calculateForbiddenRectangleMetrics(
    worldCorners: List<Pair<Float, Float>>
): ForbiddenWorldMetrics? {
    if (worldCorners.size != 4) return null
    return ForbiddenWorldMetrics(
        lengthMeters = worldDistance(worldCorners[0], worldCorners[1]),
        widthMeters = worldDistance(worldCorners[0], worldCorners[3]),
        areaSquareMeters = worldPolygonArea(worldCorners)
    )
}

internal fun calculateForbiddenCircleMetrics(radiusMeters: Float): ForbiddenWorldMetrics? {
    if (!radiusMeters.isFinite() || radiusMeters < 0f) return null
    return ForbiddenWorldMetrics(
        diameterMeters = radiusMeters * 2f,
        areaSquareMeters = PI.toFloat() * radiusMeters * radiusMeters
    )
}

private fun formatForbiddenMetrics(metrics: ForbiddenWorldMetrics): String {
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

private fun worldDistance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}

private fun worldPolygonArea(points: List<Pair<Float, Float>>): Float {
    if (points.size < 3) return 0f
    var sum = 0f
    for (index in points.indices) {
        val next = (index + 1) % points.size
        sum += points[index].first * points[next].second -
                points[next].first * points[index].second
    }
    return abs(sum) / 2f
}

private fun polygonCentroid(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    if (points.size < 3) {
        val sx = points.sumOf { it.x.toDouble() } / points.size
        val sy = points.sumOf { it.y.toDouble() } / points.size
        return Offset(sx.toFloat(), sy.toFloat())
    }
    var signedArea = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in points.indices) {
        val j = (i + 1) % points.size
        val cross = points[i].x * points[j].y - points[j].x * points[i].y
        signedArea += cross
        cx += (points[i].x + points[j].x) * cross
        cy += (points[i].y + points[j].y) * cross
    }
    signedArea *= 0.5
    if (abs(signedArea) < 1e-5) {
        val sx = points.sumOf { it.x.toDouble() } / points.size
        val sy = points.sumOf { it.y.toDouble() } / points.size
        return Offset(sx.toFloat(), sy.toFloat())
    }
    cx /= (6.0 * signedArea)
    cy /= (6.0 * signedArea)
    return Offset(cx.toFloat(), cy.toFloat())
}

@Composable
private fun ForbiddenRectHandles(
    corners: List<Offset>,
    rectWidth: Float,
    rectHeight: Float,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    moveGesture: DragGestureCallbacks,
    resizeGesture: DragGestureCallbacks,
    rotationGesture: RotationGestureCallbacks
) {
    val density = LocalDensity.current
    if (corners.size != 4) return
    // 手势 Box 使用屏幕包围框；确认和旋转仍以传入的真实矩形四角为准。
    val gestureBounds = rectFromCorners(corners)
    val center = midpoint(corners[0], corners[2])
    val minHandleSize = 22.dp
    val maxHandleSize = 32.dp
    val handleGap = 4.dp
    val handleSize = with(density) {
        min(gestureBounds.width, gestureBounds.height)
            .toDp()
            .coerceIn(minHandleSize, maxHandleSize)
    }
    val handleSizePx = with(density) { handleSize.toPx() }
    val handleHalfPx = handleSizePx / 2f
    val minHandleSizePx = with(density) { minHandleSize.toPx() }
    val handleGapPx = with(density) { handleGap.toPx() }
    val moveOutsideHorizontally = gestureBounds.width < minHandleSizePx
    val moveOutsideVertically = gestureBounds.height < minHandleSizePx
    val leftHandleX = if (moveOutsideHorizontally) {
        gestureBounds.left - handleGapPx - handleSizePx
    } else {
        gestureBounds.left - handleHalfPx
    }
    val rightHandleX = if (moveOutsideHorizontally) {
        gestureBounds.right + handleGapPx
    } else {
        gestureBounds.right - handleHalfPx
    }
    val topHandleY = if (moveOutsideVertically) {
        gestureBounds.top - handleGapPx - handleSizePx
    } else {
        gestureBounds.top - handleHalfPx
    }
    val bottomHandleY = if (moveOutsideVertically) {
        gestureBounds.bottom + handleGapPx
    } else {
        gestureBounds.bottom - handleHalfPx
    }
    val resizeHandleCenter = Offset(
        x = rightHandleX + handleHalfPx,
        y = bottomHandleY + handleHalfPx
    )
    val rotateHandleCenter = Offset(
        x = leftHandleX + handleHalfPx,
        y = bottomHandleY + handleHalfPx
    )
    StableDragTarget(bounds = gestureBounds, gesture = moveGesture)
    BoundaryHandleButton(
        symbol = "✕",
        bg = Color(0xFFD82B2A),
        size = handleSize,
        modifier = Modifier.offset {
            IntOffset(leftHandleX.roundToInt(), topHandleY.roundToInt())
        },
        onClick = onClose
    )
    BoundaryHandleButton(
        symbol = "✓",
        bg = Color(0xFF3AB54A),
        size = handleSize,
        modifier = Modifier.offset {
            IntOffset(rightHandleX.roundToInt(), topHandleY.roundToInt())
        },
        onClick = onConfirm
    )
    BoundaryZoomHandleButton(
        handleCenter = resizeHandleCenter,
        gesture = resizeGesture,
        size = handleSize
    )
    BoundaryRotateHandleButton(
        center = center,
        handleCenter = rotateHandleCenter,
        // 修正“包围框左下角”与“真实矩形左下角”之间的屏幕偏移。
        rotationReferenceHandleCenter = corners[3],
        rectWidth = rectWidth,
        rectHeight = rectHeight,
        gesture = rotationGesture,
        size = handleSize
    )
}

@Composable
private fun StableDragTarget(
    bounds: Rect,
    gesture: DragGestureCallbacks,
    clipToCircle: Boolean = false,
    zIndex: Float = 0f
) {
    val density = LocalDensity.current
    val currentBounds by rememberUpdatedState(bounds)
    val currentGesture by rememberUpdatedState(gesture)
    var gestureStartBounds by remember { mutableStateOf<Rect?>(null) }
    val hitBounds = gestureStartBounds ?: bounds
    val width = with(density) { hitBounds.width.coerceAtLeast(1f).toDp() }
    val height = with(density) { hitBounds.height.coerceAtLeast(1f).toDp() }
    val clipModifier = if (clipToCircle) Modifier.clip(CircleShape) else Modifier
    Box(
        modifier = Modifier
            .offset {
                IntOffset(hitBounds.left.roundToInt(), hitBounds.top.roundToInt())
            }
            .zIndex(zIndex)
            .size(width = width, height = height)
            .then(clipModifier)
            .pointerInput(Unit) {
                var accumulatedDelta = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        accumulatedDelta = Offset.Zero
                        gestureStartBounds = currentBounds
                        currentGesture.onStart()
                    },
                    onDragEnd = {
                        currentGesture.onEnd()
                        accumulatedDelta = Offset.Zero
                        gestureStartBounds = null
                    },
                    onDragCancel = {
                        currentGesture.onCancel()
                        accumulatedDelta = Offset.Zero
                        gestureStartBounds = null
                    }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDelta += dragAmount
                    currentGesture.onDrag(accumulatedDelta)
                }
            }
    )
}

@Composable
private fun ForbiddenCircleHandles(
    center: Offset,
    radius: Float,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    moveGesture: DragGestureCallbacks,
    resizeGesture: DragGestureCallbacks
) {
    val density = LocalDensity.current
    val diameterDp = with(density) { (radius * 2f).toDp() }
    val handleSize = diameterDp.coerceIn(22.dp, 32.dp)
    val handleSizePx = with(density) { handleSize.toPx() }
    val handleHalfPx = handleSizePx / 2f
    val handleGapPx = with(density) { 4.dp.toPx() }
    val diagonalDirectionLength = sqrt(0.85f * 0.85f + 0.35f * 0.35f)
    val radialDistance = radius + handleHalfPx + handleGapPx
    val closeCenter = center + Offset(
        x = -0.85f / diagonalDirectionLength * radialDistance,
        y = 0.35f / diagonalDirectionLength * radialDistance
    )
    val confirmCenter = center + Offset(x = 0f, y = -radialDistance)
    val resizeCenter = center + Offset(
        x = 0.85f / diagonalDirectionLength * radialDistance,
        y = 0.35f / diagonalDirectionLength * radialDistance
    )

    StableDragTarget(
        bounds = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius),
        gesture = moveGesture,
        clipToCircle = true
    )

    BoundaryHandleButton(
        symbol = "✕",
        bg = Color(0xFFD82B2A),
        size = handleSize,
        modifier = Modifier.offset {
            IntOffset(
                (closeCenter.x - handleHalfPx).roundToInt(),
                (closeCenter.y - handleHalfPx).roundToInt()
            )
        },
        onClick = onClose
    )
    BoundaryHandleButton(
        symbol = "✓",
        bg = Color(0xFF3AB54A),
        size = handleSize,
        modifier = Modifier.offset {
            IntOffset(
                (confirmCenter.x - handleHalfPx).roundToInt(),
                (confirmCenter.y - handleHalfPx).roundToInt()
            )
        },
        onClick = onConfirm
    )
    BoundaryZoomHandleButton(
        handleCenter = resizeCenter,
        gesture = resizeGesture,
        size = handleSize
    )
}

@Composable
private fun BoundaryHandleButton(
    symbol: String,
    bg: Color,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val fontSize = (14f * size.value / 32f).coerceIn(10f, 14f).sp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}

@Composable
private fun BoundaryZoomHandleButton(
    handleCenter: Offset,
    gesture: DragGestureCallbacks,
    size: Dp = 32.dp
) {
    val density = LocalDensity.current
    val handleHalfPx = with(density) { size.toPx() / 2f }
    val origin = Offset(handleCenter.x - handleHalfPx, handleCenter.y - handleHalfPx)
    val currentOrigin by rememberUpdatedState(origin)
    val currentGesture by rememberUpdatedState(gesture)
    var gestureStartOrigin by remember { mutableStateOf<Offset?>(null) }
    val hitOrigin = gestureStartOrigin ?: origin
    val visualDelta = origin - hitOrigin
    Box(
        modifier = Modifier
            .offset { IntOffset(hitOrigin.x.roundToInt(), hitOrigin.y.roundToInt()) }
            .zIndex(2f)
            .size(size)
            .pointerInput(Unit) {
                var accumulatedDelta = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        accumulatedDelta = Offset.Zero
                        gestureStartOrigin = currentOrigin
                        currentGesture.onStart()
                    },
                    onDragEnd = {
                        currentGesture.onEnd()
                        accumulatedDelta = Offset.Zero
                        gestureStartOrigin = null
                    },
                    onDragCancel = {
                        currentGesture.onCancel()
                        accumulatedDelta = Offset.Zero
                        gestureStartOrigin = null
                    }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDelta += dragAmount
                    currentGesture.onDrag(accumulatedDelta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = DesignR.drawable.ic_rectangle_zoom),
            contentDescription = "缩放",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationX = visualDelta.x
                    translationY = visualDelta.y
                }
                .clip(CircleShape)
        )
    }
}

@Composable
private fun BoundaryRotateHandleButton(
    center: Offset,
    handleCenter: Offset,
    rotationReferenceHandleCenter: Offset = handleCenter,
    rectWidth: Float,
    rectHeight: Float,
    gesture: RotationGestureCallbacks,
    size: Dp = 32.dp
) {
    val density = LocalDensity.current
    val handleHalfPx = with(density) { size.toPx() / 2f }
    val origin = Offset(handleCenter.x - handleHalfPx, handleCenter.y - handleHalfPx)
    val currentOrigin by rememberUpdatedState(origin)
    val currentCenter by rememberUpdatedState(center)
    val currentRotationReferenceHandleCenter by rememberUpdatedState(rotationReferenceHandleCenter)
    val currentRectWidth by rememberUpdatedState(rectWidth)
    val currentRectHeight by rememberUpdatedState(rectHeight)
    val currentGesture by rememberUpdatedState(gesture)
    var gestureStartOrigin by remember { mutableStateOf<Offset?>(null) }
    val hitOrigin = gestureStartOrigin ?: origin
    val visualDelta = origin - hitOrigin
    Box(
        modifier = Modifier
            .offset { IntOffset(hitOrigin.x.roundToInt(), hitOrigin.y.roundToInt()) }
            .size(size)
            .pointerInput(Unit) {
                var startOrigin = Offset.Zero
                var startCenter = Offset.Zero
                var startTouchPoint = Offset.Zero
                var startReferenceCenter = Offset.Zero
                var startWidth = 0f
                var startHeight = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { localTouchPoint ->
                        startOrigin = currentOrigin
                        startCenter = currentCenter
                        startTouchPoint = startOrigin + localTouchPoint
                        startReferenceCenter = currentRotationReferenceHandleCenter
                        startWidth = currentRectWidth
                        startHeight = currentRectHeight
                        gestureStartOrigin = startOrigin
                        currentGesture.onStart()
                    },
                    onDragEnd = {
                        currentGesture.onEnd()
                        gestureStartOrigin = null
                    },
                    onDragCancel = {
                        currentGesture.onCancel()
                        gestureStartOrigin = null
                    }
                ) { change, _ ->
                    change.consume()
                    val pointerPoint = startOrigin + change.position
                    val dragPoint = startReferenceCenter + (pointerPoint - startTouchPoint)
                    currentGesture.onRotate(
                        rotationFromHandleDrag(
                            center = startCenter,
                            handle = dragPoint,
                            width = startWidth,
                            height = startHeight
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = DesignR.drawable.ic_edit_rotate),
            contentDescription = "旋转",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .graphicsLayer {
                    translationX = visualDelta.x
                    translationY = visualDelta.y
                }
        )
    }
}

@Composable
private fun Step3TopBar() {
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
                Text(
                    text = "⚙",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Step3PathText(
                text = "1.扫描地图",
                active = true
            )
            Step3PathText(text = ">", active = true)
            Step3PathText(
                text = "2.编辑地图",
                active = true
            )
            Step3PathText(text = ">", active = true)
            Step3PathText(
                text = "3.划分工作区",
                active = true
            )
            Step3PathText(text = ">", active = false)
            Step3PathText(
                text = "4.保存地图",
                active = false
            )
        }

        DeviceStatusBar()
    }
}

@Composable
private fun Step3PathText(text: String, active: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color(0xFF00A0E9) else Color(0xFF9DA3AF)
    )
}

@Composable
private fun WorkspacePanel(
    panelMode: WorkspacePanelMode,
    workspaces: List<WorkspaceItem>,
    workspaceDraft: WorkspaceDraft,
    isEditingWorkspace: Boolean,
    isWorkspaceSaving: Boolean,
    onAddWorkspace: () -> Unit,
    onEditWorkspace: (String) -> Unit,
    onCancelAddWorkspace: () -> Unit,
    onSaveWorkspace: () -> Unit,
    onOpenBoundaryEditor: () -> Unit,
    onCloseBoundaryEditor: () -> Unit,
    onSelectBoundaryDrawMode: (BoundaryDrawMode) -> Unit,
    onOpenForbiddenEditor: () -> Unit,
    onCloseForbiddenEditor: () -> Unit,
    onSelectForbiddenDrawMode: (ForbiddenDrawMode) -> Unit,
    onCloseRobotSettingsEditor: () -> Unit,
    onOpenProcessSettingsEditor: () -> Unit,
    onCloseProcessSettingsEditor: () -> Unit,
    onDecreaseRobotWidth: () -> Unit,
    onIncreaseRobotWidth: () -> Unit,
    onRobotWidthChange: (String) -> Unit,
    onDecreaseRobotLength: () -> Unit,
    onIncreaseRobotLength: () -> Unit,
    onRobotLengthChange: (String) -> Unit,
    onDecreaseRobotPathSpacing: () -> Unit,
    onIncreaseRobotPathSpacing: () -> Unit,
    onRobotPathSpacingChange: (String) -> Unit,
    onDecreaseRobotCoverage: () -> Unit,
    onIncreaseRobotCoverage: () -> Unit,
    onRobotCoverageChange: (String) -> Unit,
    onSaveRobotSettings: () -> Unit,
    onClearWorkspaces: () -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onAreaCodeChange: (String) -> Unit,
    onToggleBoundaryConfigured: (Boolean) -> Unit,
    onToggleForbiddenConfigured: (Boolean) -> Unit,
    onToggleStartConfigured: (Boolean) -> Unit,
    onToggleEndConfigured: (Boolean) -> Unit,
    onPlaceStartPoint: () -> Unit,
    onPlaceEndPoint: () -> Unit,
    onAddWorkspaceScreenEnter: () -> Unit,
    onDecreasePathSpacing: () -> Unit,
    onIncreasePathSpacing: () -> Unit,
    onPreviousScanDirection: () -> Unit,
    onNextScanDirection: () -> Unit,
    onOpenRobotSettings: () -> Unit,
    onOpenProcessSettings: () -> Unit,
    onSelectProcessType: (ProcessType) -> Unit,
    onSelectGrindingParameter: (GrindingParameter) -> Unit,
    onSelectConcreteStrength: (ConcreteStrength) -> Unit,
    onDecreaseProcessSpeed: () -> Unit,
    onIncreaseProcessSpeed: () -> Unit,
    onProcessSpeedChange: (String) -> Unit,
    onDecreaseProcessPressure: () -> Unit,
    onIncreaseProcessPressure: () -> Unit,
    onProcessPressureChange: (String) -> Unit,
    onDecreaseDiscSpeed: () -> Unit,
    onIncreaseDiscSpeed: () -> Unit,
    onDiscSpeedChange: (String) -> Unit,
    onSaveProcessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFEFEFF),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        when (panelMode) {
            WorkspacePanelMode.LIST -> WorkspaceListPanel(
                workspaces = workspaces,
                onAddWorkspace = onAddWorkspace,
                onClearWorkspaces = onClearWorkspaces,
                onDeleteWorkspace = onDeleteWorkspace,
                onEditWorkspace = onEditWorkspace
            )

            WorkspacePanelMode.ADD -> AddWorkspaceScreen(
                draft = workspaceDraft,
                isEditing = isEditingWorkspace,
                isSaving = isWorkspaceSaving,
                onEnter = onAddWorkspaceScreenEnter,
                onBack = onCancelAddWorkspace,
                onSave = onSaveWorkspace,
                onOpenBoundaryEditor = onOpenBoundaryEditor,
                onOpenForbiddenEditor = onOpenForbiddenEditor,
                onAreaCodeChange = onAreaCodeChange,
                onPlaceStartPoint = onPlaceStartPoint,
                onPlaceEndPoint = onPlaceEndPoint,
                onDecreasePathSpacing = onDecreasePathSpacing,
                onIncreasePathSpacing = onIncreasePathSpacing,
                onPreviousScanDirection = onPreviousScanDirection,
                onNextScanDirection = onNextScanDirection,
                onOpenRobotSettings = onOpenRobotSettings,
                onOpenProcessSettings = onOpenProcessSettings
            )

            WorkspacePanelMode.BOUNDARY_EDITOR -> BoundaryEditorScreen(
                selectedMode = workspaceDraft.boundaryDrawMode,
                onBack = onCloseBoundaryEditor,
                onSelectMode = onSelectBoundaryDrawMode
            )

            WorkspacePanelMode.FORBIDDEN_EDITOR -> ForbiddenEditorScreen(
                selectedMode = workspaceDraft.forbiddenDrawMode,
                onBack = onCloseForbiddenEditor,
                onSelectMode = onSelectForbiddenDrawMode
            )

            WorkspacePanelMode.ROBOT_SETTINGS_EDITOR -> MapRobotSettingsScreen(
                draft = workspaceDraft,
                onBack = onCloseRobotSettingsEditor,
                onDecreaseWidth = onDecreaseRobotWidth,
                onIncreaseWidth = onIncreaseRobotWidth,
                onWidthChange = onRobotWidthChange,
                onDecreaseLength = onDecreaseRobotLength,
                onIncreaseLength = onIncreaseRobotLength,
                onLengthChange = onRobotLengthChange,
                onDecreasePathSpacing = onDecreaseRobotPathSpacing,
                onIncreasePathSpacing = onIncreaseRobotPathSpacing,
                onPathSpacingChange = onRobotPathSpacingChange,
                onDecreaseCoverage = onDecreaseRobotCoverage,
                onIncreaseCoverage = onIncreaseRobotCoverage,
                onCoverageChange = onRobotCoverageChange,
                onSave = onSaveRobotSettings
            )

            WorkspacePanelMode.PROCESS_SETTINGS_EDITOR -> ProcessSettingsScreen(
                draft = workspaceDraft,
                onBack = onCloseProcessSettingsEditor,
                onSelectProcessType = onSelectProcessType,
                onSelectGrindingParameter = onSelectGrindingParameter,
                onSelectConcreteStrength = onSelectConcreteStrength,
                onDecreaseSpeed = onDecreaseProcessSpeed,
                onIncreaseSpeed = onIncreaseProcessSpeed,
                onSpeedChange = onProcessSpeedChange,
                onDecreasePressure = onDecreaseProcessPressure,
                onIncreasePressure = onIncreaseProcessPressure,
                onPressureChange = onProcessPressureChange,
                onDecreaseDiscSpeed = onDecreaseDiscSpeed,
                onIncreaseDiscSpeed = onIncreaseDiscSpeed,
                onDiscSpeedChange = onDiscSpeedChange,
                onSave = onSaveProcessSettings
            )
        }
    }
}

@Composable
private fun WorkspaceListPanel(
    workspaces: List<WorkspaceItem>,
    onAddWorkspace: () -> Unit,
    onClearWorkspaces: () -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onEditWorkspace: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "工作区",
                fontSize = 32.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(1000.dp))
                    .background(Color(0xFFF3F4F6))
                    .clickable(onClick = onClearWorkspaces)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "清空重置",
                    fontSize = 12.sp,
                    color = Color(0xFF9DA3AF)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        PanelDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "路径规划参数",
            fontSize = 12.sp,
            color = Color(0xFF9DA3AF),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "工作区",
                fontSize = 14.sp,
                color = Color(0xFF6D737D),
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00A0E9))
                    .clickable(onClick = onAddWorkspace),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (workspaces.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 56.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_workspace_empty),
                        contentDescription = "暂无工作区数据",
                        modifier = Modifier.size(90.dp)
                    )
                    Text(
                        text = "暂无数据",
                        fontSize = 16.sp,
                        color = Color(0xFF9DA3AF),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                workspaces.forEach { item ->
                    WorkspaceItemRow(
                        item = item,
                        onClick = { onEditWorkspace(item.regionId) },
                        onDelete = { onDeleteWorkspace(item.regionId) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun PanelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14000000))
    )
}

@Composable
private fun WorkspaceItemRow(
    item: WorkspaceItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            fontSize = 14.sp,
            color = Color(0xFF202937),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(id = R.drawable.ic_workspace_del),
            contentDescription = "删除工作区",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun BottomStep3Actions(
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Step3ActionButton(
            text = "✕  取消建图",
            background = Color(0xFFD82B2A),
            contentColor = Color.White,
            onClick = onCancelBuild
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Step3ActionButton(
                text = "◀  上一步",
                background = Color(0xFFF6F8FB),
                contentColor = Color(0xFF202937),
                onClick = onPreviousStep
            )
            Step3ActionButton(
                text = "▶  下一步",
                background = Color(0xFF00A0E9),
                contentColor = Color.White,
                onClick = onNextStep
            )
        }
    }
}

@Composable
private fun Step3ActionButton(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
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


