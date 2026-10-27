package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset

/** Native geometric union. Holes are opened by cuts, never filled with a convex hull. */
internal fun mergeErasePolygons(polygons: List<List<Offset>>): List<List<Offset>> {
    if (polygons.size < 2) return polygons
    return runCatching {
        var paths = polygons.map { points -> Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }; close()
        } }
        while (paths.size > 1) paths = paths.chunked(2).map { pair ->
            if (pair.size == 1) pair[0] else Path().apply { check(op(pair[0], pair[1], Path.Op.UNION)) }
        }
        fun contours(path: Path): List<List<Offset>> {
            val data = path.approximate(0.05f)
            val output = mutableListOf<MutableList<Offset>>()
            for (i in data.indices step 3) {
                val p = Offset(data[i + 1], data[i + 2])
                if (i == 0 || (data[i] == data[i - 3] && p != Offset(data[i - 2], data[i - 1]))) output.add(mutableListOf())
                if (output.last().lastOrNull() != p) output.last().add(p)
            }
            return output.map { if (it.size > 1 && it.first() == it.last()) it.dropLast(1) else it }
                .filter { it.size >= 3 }
        }
        fun area(points: List<Offset>): Double = points.indices.sumOf { i ->
            val a = points[i]; val b = points[(i + 1) % points.size]
            a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        fun split(path: Path, depth: Int): List<List<Offset>> {
            val loops = contours(path)
            if (loops.isEmpty()) return emptyList()
            val outerSign = area(loops.maxBy { kotlin.math.abs(area(it)) })
            val hole = loops.firstOrNull { area(it) * outerSign < 0 }
            if (hole == null && loops.all { it.size <= 256 }) return loops
            check(depth < 16)
            val bounds = RectF().also { path.computeBounds(it, true) }
            val horizontal = hole != null || bounds.height() >= bounds.width()
            val cut = if (hole != null) (hole.minOf { it.y } + hole.maxOf { it.y }) / 2
                else if (horizontal) bounds.centerY() else bounds.centerX()
            val first = RectF(bounds); val second = RectF(bounds)
            if (horizontal) { first.bottom = cut; second.top = cut }
            else { first.right = cut; second.left = cut }
            return listOf(first, second).flatMap { box ->
                val clip = Path().apply { addRect(box, Path.Direction.CW) }
                val part = Path().apply { check(op(path, clip, Path.Op.INTERSECT)) }
                split(part, depth + 1)
            }
        }
        val merged = split(paths.single(), 0)
        // Prefer fewer requests; original polygons are exact and remain a safe fallback.
        if (merged.isNotEmpty() && merged.size < polygons.size) merged else polygons
    }.getOrElse { polygons }
}
