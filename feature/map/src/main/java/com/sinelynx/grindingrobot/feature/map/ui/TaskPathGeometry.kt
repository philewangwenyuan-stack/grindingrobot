package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val PlannedTaskPathColor = Color(0xFF00FF00)
internal val ConnectingTaskPathColor = Color(0xFF1976D2)
private const val PlannedTaskPathStrokeWidthPx = 1.5f

internal data class TaskPathRenderSegment(val points: List<TaskPathPointPayload>, val color: Color)

/** 将实际点拆成可独立绘制的折线：有效摘要优先，其余按相邻 scope 回退；段之间不补连接线。 */
internal fun taskPathRenderSegments(path: TaskPathPayload): List<TaskPathRenderSegment> {
    val rendered = mutableListOf<TaskPathRenderSegment>()
    // 标记已被摘要覆盖的数组位置；未覆盖点可回退，但不能跨已覆盖区段连线。
    val claimed = BooleanArray(path.points.size)

    fun appendRuns(scope: String? = null, include: (Int, TaskPathPointPayload) -> Boolean) {
        var run = mutableListOf<TaskPathPointPayload>()
        var runColor = PlannedTaskPathColor
        var runScope: String? = null
        fun flush() {
            if (run.isNotEmpty()) rendered.add(TaskPathRenderSegment(run.toList(), runColor))
            run = mutableListOf()
        }
        path.points.forEachIndexed { position, point ->
            if (!include(position, point) || !point.x.isFinite() || !point.y.isFinite()) {
                flush()
            } else {
                val pointScope = scope ?: point.pathScope
                val color = if (pointScope == "between_regions")
                    ConnectingTaskPathColor else PlannedTaskPathColor
                // 即使两个 scope 最终都显示红色，也保留分组边界，不按颜色相同擅自合段。
                if (pointScope != runScope) flush()
                runScope = pointScope
                runColor = color
                run.add(point)
            }
        }
        flush()
    }

    path.segments.forEach { segment ->
        if (segment.startPointIndex < 0 || segment.endPointIndex < segment.startPointIndex) return@forEach
        val range = segment.startPointIndex..segment.endPointIndex
        fun matches(position: Int, point: TaskPathPointPayload) =
            (if (point.index >= 0) point.index else position) in range
        if (path.points.withIndex().none { matches(it.index, it.value) &&
                it.value.x.isFinite() && it.value.y.isFinite() }) return@forEach
        val scope = segment.pathScope.takeIf { it == "within_region" || it == "between_regions" }
        appendRuns(scope) { position, point ->
            matches(position, point).also { if (it) claimed[position] = true }
        }
    }
    // 缺失/不可用摘要及未覆盖点按点序回退；已覆盖点、无效坐标均构成断点，避免虚构跨段连线。
    appendRuns { position, _ -> !claimed[position] }
    return rendered
}

/** 路径 x/y 已在原始 map 坐标系；忽略 row/col，也不重复施加路径响应的 alignment_yaw。 */
fun taskPathPointToBitmapPoint(
    point: TaskPathPointPayload,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>?,
    bitmapSize: Pair<Int, Int>?
): Offset? {
    val mapSize = mapImageSize ?: (geo.mapWidth to geo.mapHeight)
    val decodedSize = bitmapSize ?: mapSize
    if (
        geo.resolution <= 0f || geo.mapWidth <= 0 || geo.mapHeight <= 0 ||
        mapSize.first <= 0 || mapSize.second <= 0 || decodedSize.first <= 0 || decodedSize.second <= 0
    ) {
        return null
    }
    val radians = geo.headingDeg * PI / 180.0
    val dx = point.x.toDouble() - geo.originX
    val dy = point.y.toDouble() - geo.originY
    val localX = cos(radians) * dx + sin(radians) * dy
    val localY = -sin(radians) * dx + cos(radians) * dy
    val mapPixelX = localX / geo.resolution
    val mapPixelY = geo.mapHeight - localY / geo.resolution
    if (!mapPixelX.isFinite() || !mapPixelY.isFinite()) return null
    if (mapPixelX !in 0.0..mapSize.first.toDouble() || mapPixelY !in 0.0..mapSize.second.toDouble()) {
        return null
    }
    return Offset(
        x = (mapPixelX * decodedSize.first / mapSize.first).toFloat(),
        y = (mapPixelY * decodedSize.second / mapSize.second).toFloat()
    )
}

/** 与 Image 的居中等比适配一致，再绕画布中心旋转；手势缩放和平移由调用方最后施加。 */
fun taskBitmapPointToFittedPoint(
    point: Offset,
    canvasSize: Size,
    bitmapSize: Pair<Int, Int>,
    rotationDeg: Float
): Offset? {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f || bitmapSize.first <= 0 || bitmapSize.second <= 0) {
        return null
    }
    val scale = minOf(
        canvasSize.width / bitmapSize.first,
        canvasSize.height / bitmapSize.second
    )
    val imageSize = Size(bitmapSize.first * scale, bitmapSize.second * scale)
    val imageTopLeft = Offset(
        x = (canvasSize.width - imageSize.width) / 2f,
        y = (canvasSize.height - imageSize.height) / 2f
    )
    return Offset(
        x = imageTopLeft.x + point.x * scale,
        y = imageTopLeft.y + point.y * scale
    ).rotateTaskPointAround(
        center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
        rotationDeg = rotationDeg
    )
}

@Composable
fun BoxScope.FittedTaskPathOverlay(
    path: TaskPathPayload?,
    geo: MapGeo?,
    mapImageSize: Pair<Int, Int>?,
    bitmapSize: Pair<Int, Int>?,
    mapRotationDeg: Float,
    viewportScale: Float = 1f,
    viewportOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
    colorByScope: Boolean = false
) {
    if (path == null || geo == null || bitmapSize == null) return
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier.fillMaxSize().onSizeChanged { viewportSize = it })
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawFittedTaskPath(
            path = path,
            geo = geo,
            mapImageSize = mapImageSize,
            bitmapSize = bitmapSize,
            mapRotationDeg = mapRotationDeg,
            viewportScale = viewportScale,
            viewportOffset = viewportOffset,
            colorByScope = colorByScope
        )
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFittedTaskPath(
    path: TaskPathPayload,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>?,
    bitmapSize: Pair<Int, Int>,
    mapRotationDeg: Float,
    viewportScale: Float = 1f,
    viewportOffset: Offset = Offset.Zero,
    colorByScope: Boolean = false
) {
    // 新建图 Step4 启用分段配色；默认分支保留其他页面原有的单色路径行为。
    val renderSegments = if (colorByScope) taskPathRenderSegments(path)
        else listOf(TaskPathRenderSegment(path.points, PlannedTaskPathColor))
    renderSegments.forEach { renderSegment ->
        var previous: Offset? = null
        var segmentPointCount = 0
        fun drawIsolatedPointIfNeeded() {
            if (segmentPointCount == 1 && previous != null) {
                drawCircle(
                    color = renderSegment.color,
                    radius = PlannedTaskPathStrokeWidthPx * viewportScale,
                    center = previous!!
                )
            }
        }
        renderSegment.points.forEach { point ->
            val fitted = taskPathPointToBitmapPoint(point, geo, mapImageSize, bitmapSize)
                ?.let { taskBitmapPointToFittedPoint(it, size, bitmapSize, mapRotationDeg) }
                ?.let { Offset(it.x * viewportScale + viewportOffset.x, it.y * viewportScale + viewportOffset.y) }
            if (fitted == null) {
                // 越界或无效点不仅不绘制，还必须清空前点，否则会连过不可显示的路径区间。
                drawIsolatedPointIfNeeded()
                previous = null
                segmentPointCount = 0
            } else {
                previous?.let { start ->
                    drawLine(
                        color = renderSegment.color,
                        start = start,
                        end = fitted,
                        strokeWidth = PlannedTaskPathStrokeWidthPx * viewportScale,
                        cap = StrokeCap.Round
                    )
                }
                previous = fitted
                segmentPointCount++
            }
        }
        drawIsolatedPointIfNeeded()
    }
}

private fun Offset.rotateTaskPointAround(center: Offset, rotationDeg: Float): Offset {
    if (rotationDeg == 0f) return this
    val radians = rotationDeg * PI / 180.0
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = (center.x + dx * cos(radians) - dy * sin(radians)).toFloat(),
        y = (center.y + dx * sin(radians) + dy * cos(radians)).toFloat()
    )
}
