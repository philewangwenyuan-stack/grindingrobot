package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class ProjectedTaskPathRun(val points: List<Offset>, val color: Color)

/** One immutable path snapshot per mounted overlay, not an unbounded task/version cache. */
internal class TaskPathProjectionCache {
    private var source: TaskPathPayload? = null
    private var metadata: MapGeo? = null
    private var mapSize: Pair<Int, Int>? = null
    private var decodedSize: Pair<Int, Int>? = null
    private var scoped = false
    private var projected: List<ProjectedTaskPathRun> = emptyList()

    fun get(path: TaskPathPayload, geo: MapGeo, mapImageSize: Pair<Int, Int>?,
            bitmapSize: Pair<Int, Int>, colorByScope: Boolean): List<ProjectedTaskPathRun> {
        // Reference identity avoids walking large data-class lists on every pose update.
        // A replacement with the same version still invalidates: legacy devices can reuse versions.
        if (source === path && metadata?.sameProjectionAs(geo) == true && mapSize == mapImageSize &&
            decodedSize == bitmapSize && scoped == colorByScope) return projected
        val project = taskPathBitmapProjector(geo, mapImageSize, bitmapSize)
        val segments = if (colorByScope) taskPathRenderSegments(path)
            else listOf(TaskPathRenderSegment(path.points, PlannedTaskPathColor))
        val result = mutableListOf<ProjectedTaskPathRun>()
        segments.forEach { segment ->
            var run = mutableListOf<Offset>()
            fun flush() {
                if (run.isNotEmpty()) result.add(ProjectedTaskPathRun(run, segment.color))
                run = mutableListOf()
            }
            segment.points.forEach { point ->
                val pixel = project(point)
                if (pixel == null) flush() else run.add(pixel)
            }
            flush()
        }
        source = path
        metadata = geo
        mapSize = mapImageSize
        decodedSize = bitmapSize
        scoped = colorByScope
        projected = result
        return result
    }
}

private fun MapGeo.sameProjectionAs(other: MapGeo): Boolean =
    mapWidth == other.mapWidth && mapHeight == other.mapHeight && resolution == other.resolution &&
        originX == other.originX && originY == other.originY && headingDeg == other.headingDeg &&
        mapVersion == other.mapVersion

/** Same projection as taskPathPointToBitmapPoint; compute map constants/trig once per snapshot. */
private fun taskPathBitmapProjector(geo: MapGeo, mapImageSize: Pair<Int, Int>?,
                                    bitmapSize: Pair<Int, Int>): (TaskPathPointPayload) -> Offset? {
    val mapSize = mapImageSize ?: (geo.mapWidth to geo.mapHeight)
    if (geo.resolution <= 0f || geo.mapWidth <= 0 || geo.mapHeight <= 0 ||
        mapSize.first <= 0 || mapSize.second <= 0 || bitmapSize.first <= 0 || bitmapSize.second <= 0) {
        return { null }
    }
    val radians = geo.headingDeg * PI / 180.0
    val cosine = cos(radians)
    val sine = sin(radians)
    return { point ->
        val dx = point.x.toDouble() - geo.originX
        val dy = point.y.toDouble() - geo.originY
        val x = (cosine * dx + sine * dy) / geo.resolution
        val y = geo.mapHeight - (-sine * dx + cosine * dy) / geo.resolution
        if (!x.isFinite() || !y.isFinite() || x !in 0.0..mapSize.first.toDouble() ||
            y !in 0.0..mapSize.second.toDouble()) null
        else Offset((x * bitmapSize.first / mapSize.first).toFloat(),
            (y * bitmapSize.second / mapSize.second).toFloat())
    }
}

internal class TaskPathDrawingCache {
    private val projection = TaskPathProjectionCache()
    private var projected: List<ProjectedTaskPathRun>? = null
    private var drawing = emptyList<TaskPathStroke>()

    fun get(path: TaskPathPayload, geo: MapGeo, mapImageSize: Pair<Int, Int>?,
            bitmapSize: Pair<Int, Int>, colorByScope: Boolean): List<TaskPathStroke> {
        val next = projection.get(path, geo, mapImageSize, bitmapSize, colorByScope)
        if (next === projected) return drawing
        drawing = next.map { run ->
            TaskPathStroke(run.color, Path().apply {
                if (run.points.size > 1) {
                    moveTo(run.points[0].x, run.points[0].y)
                    for (i in 1 until run.points.size) lineTo(run.points[i].x, run.points[i].y)
                }
            }, if (run.points.size == 1) run.points else emptyList())
        }
        projected = next
        return drawing
    }
}

internal class TaskPathStroke(val color: Color, val path: Path, val isolated: List<Offset>)

/** Fit/rotate/pan cached bitmap-space paths with a matrix, never by transforming all vertices again. */
internal fun DrawScope.drawCachedTaskPath(drawing: List<TaskPathStroke>, bitmapSize: Pair<Int, Int>,
                                          rotationDeg: Float, viewportScale: Float, viewportOffset: Offset) {
    if (bitmapSize.first <= 0 || bitmapSize.second <= 0 || size.width <= 0f || size.height <= 0f ||
        !viewportScale.isFinite() || viewportScale <= 0f || !rotationDeg.isFinite()) return
    val fit = minOf(size.width / bitmapSize.first, size.height / bitmapSize.second)
    withTransform({
        translate(viewportOffset.x, viewportOffset.y)
        scale(viewportScale, viewportScale, pivot = Offset.Zero)
        rotate(rotationDeg, pivot = center)
        translate((size.width - bitmapSize.first * fit) / 2f, (size.height - bitmapSize.second * fit) / 2f)
        scale(fit, fit, pivot = Offset.Zero)
    }) {
        // Keep the screen stroke thin when the map is zoomed in.
        val width = PlannedTaskPathStrokeWidthPx / (fit * viewportScale)
        drawing.forEach { stroke ->
            drawPath(stroke.path, stroke.color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (stroke.isolated.isNotEmpty()) drawPoints(stroke.isolated, PointMode.Points,
                stroke.color, strokeWidth = width * 2f, cap = StrokeCap.Round)
        }
    }
}
