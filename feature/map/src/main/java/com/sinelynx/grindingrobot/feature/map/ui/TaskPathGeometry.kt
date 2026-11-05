package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val PlannedTaskPathColor = Color(0xFF00FF00)
internal val ConnectingTaskPathColor = Color(0xFF1976D2)
internal const val PlannedTaskPathStrokeWidthPx = 1f

internal data class TaskPathRenderSegment(val points: List<TaskPathPointPayload>, val color: Color)

/** 将实际点拆成可独立绘制的折线：有效摘要优先，其余按相邻 scope 回退；段之间不补连接线。 */
internal fun taskPathRenderSegments(path: TaskPathPayload): List<TaskPathRenderSegment> {
    val rendered = mutableListOf<TaskPathRenderSegment>()
    // 标记已被摘要覆盖的数组位置；未覆盖点可回退，但不能跨已覆盖区段连线。
    val claimed = BooleanArray(path.points.size)

    fun appendRuns(positions: Iterable<Int>, scope: String? = null) {
        var run = mutableListOf<TaskPathPointPayload>()
        var runColor = PlannedTaskPathColor
        var runScope: String? = null
        fun flush() {
            if (run.isNotEmpty()) rendered.add(TaskPathRenderSegment(run.toList(), runColor))
            run = mutableListOf()
        }
        var previousPosition = -2
        positions.forEach { position ->
            val point = path.points[position]
            if (position != previousPosition + 1) flush()
            previousPosition = position
            if (!point.x.isFinite() || !point.y.isFinite()) {
                flush()
            } else {
                val pointScope = scope ?: point.pathScope
                val color = if (pointScope == "between_regions")
                    ConnectingTaskPathColor else PlannedTaskPathColor
                // 即使颜色相同，也保留 scope 边界，不擅自合段。
                if (pointScope != runScope) flush()
                runScope = pointScope
                runColor = color
                run.add(point)
            }
        }
        flush()
    }

    // 按有效 index 建一次查找表；摘要只访问命中的点，再恢复数组顺序。
    // 稀疏/乱序/重复 index 都合法，不能把 index 当数组下标，也不能跨数组间隙连线。
    val indexedPositions = if (path.segments.isNotEmpty())
        path.points.indices.sortedBy { path.points[it].index.takeIf { index -> index >= 0 } ?: it }
        else emptyList()
    fun pointIndex(position: Int) = path.points[position].index.takeIf { it >= 0 } ?: position
    fun lowerBound(index: Int): Int {
        var low = 0
        var high = indexedPositions.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (pointIndex(indexedPositions[middle]) < index) low = middle + 1 else high = middle
        }
        return low
    }
    path.segments.forEach { segment ->
        if (segment.startPointIndex < 0 || segment.endPointIndex < segment.startPointIndex) return@forEach
        val positions = mutableListOf<Int>()
        var cursor = lowerBound(segment.startPointIndex)
        while (cursor < indexedPositions.size && pointIndex(indexedPositions[cursor]) <= segment.endPointIndex) {
            positions.add(indexedPositions[cursor++])
        }
        if (positions.none { path.points[it].let { point -> point.x.isFinite() && point.y.isFinite() } }) return@forEach
        positions.sort()
        positions.forEach { claimed[it] = true }
        val scope = segment.pathScope.takeIf { it == "within_region" || it == "between_regions" }
        appendRuns(positions, scope)
    }
    // 缺失/不可用摘要及未覆盖点按点序回退；已覆盖点、无效坐标均构成断点，避免虚构跨段连线。
    appendRuns(path.points.indices.filter { !claimed[it] })
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
    // 每个图层只保留当前路径的几何；位姿、缩放、平移和显示旋转不触发逐点投影。
    val cache = remember { TaskPathDrawingCache() }
    val drawing = cache.get(path, geo, mapImageSize, bitmapSize, colorByScope)
    val firstDraw = remember(path.requestId, path.pathVersion) { booleanArrayOf(true) }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawCachedTaskPath(drawing, bitmapSize, mapRotationDeg, viewportScale, viewportOffset)
        if (firstDraw[0]) {
            firstDraw[0] = false
            com.sinelynx.grindingrobot.core.util.log.LogUtils.d("PlanTiming requestId=${path.requestId} stage=first_draw_submitted mono_ns=${System.nanoTime()} points=${path.points.size}")
        }
    }
}

/** Compatibility for one-shot drawing; production screens use the retained overlay above. */
@Deprecated("Use FittedTaskPathOverlay to retain geometry between frames")
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
    val drawing = TaskPathDrawingCache().get(path, geo, mapImageSize, bitmapSize, colorByScope)
    drawCachedTaskPath(drawing, bitmapSize, mapRotationDeg, viewportScale, viewportOffset)
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
