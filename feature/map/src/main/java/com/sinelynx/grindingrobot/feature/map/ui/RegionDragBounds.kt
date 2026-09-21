package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal fun boundsOfPoints(points: List<Offset>): Rect? {
    if (points.isEmpty()) return null
    return Rect(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y }
    )
}

/**
 * Clamps a requested Screen-space translation so the child's visible bounds remain inside the
 * visible bounds of its parent. Keeping this calculation in Screen space makes it independent of
 * the map rotation; callers convert the accepted delta back to Content space afterwards.
 */
internal fun clampDragDeltaToBounds(
    childBounds: Rect,
    parentBounds: Rect,
    requestedDelta: Offset
): Offset {
    fun clampAxis(requested: Float, minimum: Float, maximum: Float): Float {
        if (minimum > maximum) return 0f
        return requested.coerceIn(minimum, maximum)
    }

    return Offset(
        x = clampAxis(
            requested = requestedDelta.x,
            minimum = parentBounds.left - childBounds.left,
            maximum = parentBounds.right - childBounds.right
        ),
        y = clampAxis(
            requested = requestedDelta.y,
            minimum = parentBounds.top - childBounds.top,
            maximum = parentBounds.bottom - childBounds.bottom
        )
    )
}
