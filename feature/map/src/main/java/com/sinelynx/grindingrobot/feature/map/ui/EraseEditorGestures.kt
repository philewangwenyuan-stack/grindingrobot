package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope

internal data class EraseEditorCallbacks(
    val mode: com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode,
    val activeRect: Rect?,
    val screenToAligned: (Offset) -> Offset,
    val screenToBitmap: (Offset) -> Offset,
    val onRect: (Rect) -> Unit,
    val onNewRectangle: (Rect) -> Unit,
    val onCancelNewRectangle: () -> Unit,
    val onTap: (Offset) -> Unit,
    val onTransform: (Offset, Offset, Float) -> Unit,
    val onBrushPreview: (List<List<Offset>>) -> Unit,
    val onBrush: (List<List<Offset>>) -> Unit,
    val bounds: Rect,
    val alignedBounds: Rect,
    val totalScale: Float,
    val brushRadius: Float,
    val handleRadius: Float,
    val onBrushCursor: (Offset?) -> Unit = {}
)

/** One recognizer owns editing and navigation. After a second finger, suppress editing until all lift. */
internal suspend fun PointerInputScope.detectEraseEditorGestures(current: () -> EraseEditorCallbacks) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val start = current()
        val rect = start.activeRect
        val alignedStart = start.screenToAligned(down.position)
        val resize = rect != null && (alignedStart - rect.bottomRight).getDistance() <= start.handleRadius
        val move = rect?.contains(alignedStart) == true && !resize
        val samples = ArrayList<Offset>()
        val preview = ArrayList<List<Offset>>()
        val brush = start.mode == com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode.Brush &&
            start.bounds.contains(start.screenToBitmap(down.position))
        if (brush) samples.add(start.screenToBitmap(down.position))
        var navigating = false
        var dragged = false
        var cancelled = false
        var lastScreen = down.position
        var newRectangle: Rect? = null
        try {
            if (brush) current().onBrushCursor(down.position)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    navigating = true
                    if (rect == null) { newRectangle = null; current().onCancelNewRectangle() }
                    samples.clear(); preview.clear(); current().onBrushPreview(emptyList())
                    current().onBrushCursor(null)
                    if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                        current().onTransform(event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom())
                    }
                    event.changes.forEach { it.consume() }
                } else if (!navigating) {
                    if (event.changes.any { it.isConsumed }) { cancelled = true; break }
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    val point = pointer.position
                    if (brush) current().onBrushCursor(if (pointer.pressed) point else null)
                    val delta = point - down.position
                    if (delta.getDistance() > viewConfiguration.touchSlop) dragged = true
                    if (dragged) {
                        when (start.mode) {
                            com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode.Gesture ->
                                current().onTransform(lastScreen, point - lastScreen, 1f)
                            com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode.Erase -> if (rect != null) {
                                val requested = delta / start.totalScale
                                if (resize) current().onRect(resizeEraseRectBottomRight(rect, requested,
                                    start.alignedBounds, 1f / start.totalScale, 1f / start.totalScale))
                                else if (move) current().onRect(moveEraseRectWithinBounds(rect, requested, start.alignedBounds))
                            } else if (start.bounds.contains(start.screenToBitmap(down.position))) {
                                newRectangle = drawEraseRect(alignedStart, start.screenToAligned(point), start.alignedBounds)
                                current().onRect(requireNotNull(newRectangle))
                            }
                            com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode.Brush -> if (brush) {
                                val bitmapPoint = start.screenToBitmap(point)
                                if ((bitmapPoint - samples.last()).getDistance() >= start.brushRadius / 4 || !pointer.pressed) {
                                    val polygon = eraseBrushSegment(samples.last(), bitmapPoint, start.brushRadius, start.bounds)
                                    samples.add(bitmapPoint)
                                    if (polygon.isNotEmpty()) preview.add(polygon)
                                    current().onBrushPreview(preview.toList())
                                }
                            }
                        }
                        pointer.consume()
                    }
                    lastScreen = point
                }
            } while (event.changes.any { it.pressed })
            if (!navigating && !cancelled) {
                if (brush) {
                    val simplified = simplifyEraseStroke(samples, start.brushRadius / 12)
                    val pairs = if (simplified.size == 1) listOf(simplified[0] to simplified[0]) else simplified.zipWithNext()
                    current().onBrush(pairs.map { (a, b) -> eraseBrushSegment(a, b, start.brushRadius, start.bounds) }
                        .filter { it.size >= 3 })
                } else if (newRectangle != null) {
                    val created = requireNotNull(newRectangle)
                    if (created.width * start.totalScale >= 4 && created.height * start.totalScale >= 4)
                        current().onNewRectangle(created)
                    else current().onCancelNewRectangle()
                } else if (!dragged) current().onTap(down.position)
            }
        } finally {
            current().onBrushCursor(null)
            current().onBrushPreview(emptyList())
            if (cancelled && rect == null) current().onCancelNewRectangle()
            // Keep the most recent valid rectangle when a gesture is cancelled or another finger arrives.
        }
    }
}
