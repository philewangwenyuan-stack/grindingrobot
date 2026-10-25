package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.sinelynx.grindingrobot.feature.device.viewmodel.*
import com.sinelynx.grindingrobot.feature.map.ui.GRINDING_DISC_DIAMETER_M

/** One active session/map cache; sealed geometry is compiled once, the live tail is bounded. */
internal class TaskTrajectoryDrawingCache {
    private data class Projection(val width: Int, val height: Int, val resolution: Float,
        val originX: Double, val originY: Double, val heading: Float, val bitmapWidth: Int, val bitmapHeight: Int)
    private var projection: Projection? = null
    private var session: Any? = null
    private var completed: TaskTrajectoryChunk? = null
    private val strokes = ArrayList<TrajectoryStroke>()
    private var tail: TaskTrajectoryRun? = null
    private var tailStroke: TrajectoryStroke? = null
    var projectedVertexCount: Long = 0; private set
    var builtRunCount: Long = 0; private set
    val drawRunCount: Int get() = strokes.size + if (tailStroke == null) 0 else 1

    fun update(scene: TaskTrajectorySnapshot, map: TaskMapTransformUiState, width: Int, height: Int) {
        val key = Projection(map.width, map.height, map.resolution, map.originX, map.originY, map.headingDeg, width, height)
        if (session !== scene.session || projection != key) {
            session = scene.session; projection = key
            completed = null; strokes.clear(); tail = null; tailStroke = null
        }
        if (completed !== scene.completed) {
            val additions = ArrayList<TaskTrajectoryChunk>()
            var cursor = scene.completed
            while (cursor != null && cursor !== completed) { additions.add(cursor); cursor = cursor.previous }
            if (cursor !== completed) {
                // A historical snapshot or discontinuous scene replaces the cache, never mixes sessions.
                strokes.clear(); additions.clear(); cursor = scene.completed
                while (cursor != null) { additions.add(cursor); cursor = cursor.previous }
            }
            for (index in additions.lastIndex downTo 0) strokes.add(compile(additions[index].run, map, width, height))
            completed = scene.completed
        }
        if (tail !== scene.tail) {
            tail = scene.tail
            tailStroke = tail?.let { compile(it, map, width, height) }
        }
    }

    private fun compile(run: TaskTrajectoryRun, map: TaskMapTransformUiState, width: Int, height: Int): TrajectoryStroke {
        builtRunCount++
        val path = Path()
        val isolated = ArrayList<Offset>()
        var last: Offset? = null
        var count = 0
        fun finish() { if (count == 1) last?.let(isolated::add); count = 0; last = null }
        for (index in 0 until run.vertexCount) {
            projectedVertexCount++
            val point = TaskPoseUiItem(run.xy[index * 2], run.xy[index * 2 + 1], 0f, run.colorArgb)
                .toTaskBitmapPoint(map, width, height)
            if (point == null) { finish(); continue }
            if (count == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            last = point; count++
        }
        finish()
        return TrajectoryStroke(path, isolated, Color(run.colorArgb))
    }

    fun draw(scope: DrawScope, map: TaskMapTransformUiState, bitmapWidth: Int, bitmapHeight: Int,
             zoom: Float = 1f, pan: Offset = Offset.Zero) = with(scope) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || map.width <= 0 || map.height <= 0 ||
            !map.resolution.isFinite() || map.resolution <= 0f || !map.totalRotationDeg.isFinite() ||
            !zoom.isFinite() || zoom <= 0f || !pan.x.isFinite() || !pan.y.isFinite()) return@with
        val fit = minOf(size.width / bitmapWidth, size.height / bitmapHeight)
        if (!fit.isFinite() || fit <= 0f) return@with
        val style = Stroke(GRINDING_DISC_DIAMETER_M * bitmapWidth / map.width / map.resolution,
            cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({
            translate(pan.x, pan.y)
            scale(zoom, zoom, pivot = Offset.Zero)
            rotate(map.totalRotationDeg, pivot = center)
            translate((size.width - bitmapWidth * fit) / 2f, (size.height - bitmapHeight * fit) / 2f)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            fun paint(stroke: TrajectoryStroke) {
                drawPath(stroke.path, stroke.color, style = style)
                // A single initial/isolated footprint is a disc; sampled points are never painted individually.
                stroke.isolated.forEach { drawCircle(stroke.color, style.width / 2f, it) }
            }
            strokes.forEach(::paint)
            tailStroke?.let(::paint)
        }
    }
}

private class TrajectoryStroke(val path: Path, val isolated: List<Offset>, val color: Color)
