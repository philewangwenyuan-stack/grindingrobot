package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

/** Convex, round-ended brush segment. Preview and protocol use the same polygon. */
internal fun eraseBrushSegment(start: Offset, end: Offset, radius: Float, bounds: Rect): List<Offset> {
    require(radius > 0f && radius.isFinite())
    val angle = atan2(end.y - start.y, end.x - start.x)
    val points = ArrayList<Offset>(14)
    for (i in 0..6) {
        val a = angle + PI.toFloat() / 2 + i * PI.toFloat() / 6
        points += start + Offset(cos(a), sin(a)) * radius
    }
    for (i in 0..6) {
        val a = angle - PI.toFloat() / 2 + i * PI.toFloat() / 6
        points += end + Offset(cos(a), sin(a)) * radius
    }
    // Clamp each convex polygon to the image rectangle (Sutherland–Hodgman).
    var clipped: List<Offset> = points
    for (edge in 0..3) {
        fun distance(p: Offset) = when (edge) {
            0 -> p.x - bounds.left; 1 -> bounds.right - p.x
            2 -> p.y - bounds.top; else -> bounds.bottom - p.y
        }
        val input = clipped
        if (input.isEmpty()) break
        clipped = buildList {
            var previous = input.last()
            for (point in input) {
                val d0 = distance(previous); val d1 = distance(point)
                if ((d0 >= 0) != (d1 >= 0)) add(previous + (point - previous) * (d0 / (d0 - d1)))
                if (d1 >= 0) add(point)
                previous = point
            }
        }
    }
    return clipped.distinct().takeIf { it.size >= 3 }.orEmpty()
}

/** Distance-bounded simplification once per stroke, never a point-count truncation. */
internal fun simplifyEraseStroke(points: List<Offset>, tolerance: Float): List<Offset> {
    if (points.size <= 2) return points
    val keep = BooleanArray(points.size).also { it[0] = true; it[it.lastIndex] = true }
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        val a = points[first]; val ab = points[last] - a
        val length2 = ab.x * ab.x + ab.y * ab.y
        var maxDistance2 = tolerance * tolerance
        var selected = -1
        for (i in first + 1 until last) {
            val ap = points[i] - a
            val t = if (length2 > 0f) ((ap.x * ab.x + ap.y * ab.y) / length2).coerceIn(0f, 1f) else 0f
            val d = points[i] - (a + ab * t)
            val distance2 = d.x * d.x + d.y * d.y
            if (distance2 > maxDistance2) { maxDistance2 = distance2; selected = i }
        }
        if (selected >= 0) { keep[selected] = true; stack.add(first to selected); stack.add(selected to last) }
    }
    return points.filterIndexed { index, _ -> keep[index] }
}
