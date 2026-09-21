package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun rotatePoint(point: Offset, center: Offset, angleDeg: Float): Offset {
    val radians = Math.toRadians(angleDeg.toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Offset(
        x = center.x + dx * cosValue - dy * sinValue,
        y = center.y + dx * sinValue + dy * cosValue
    )
}

internal fun rectCorners(rect: Rect): List<Offset> = listOf(
    Offset(rect.left, rect.top),
    Offset(rect.right, rect.top),
    Offset(rect.right, rect.bottom),
    Offset(rect.left, rect.bottom)
)

internal fun rotatedRectBounds(rect: Rect, center: Offset, angleDeg: Float): Rect {
    return requireNotNull(boundsOfPoints(rectCorners(rect).map { rotatePoint(it, center, angleDeg) }))
}

internal fun rotatedMapBounds(bitmapWidth: Float, bitmapHeight: Float, angleDeg: Float): Rect {
    val mapRect = Rect(0f, 0f, bitmapWidth, bitmapHeight)
    return rotatedRectBounds(
        rect = mapRect,
        center = Offset(bitmapWidth / 2f, bitmapHeight / 2f),
        angleDeg = angleDeg
    )
}

/**
 * Limits pan using the map's axis-aligned screen bounds after rotation, fit and gesture zoom.
 * The two extrema work for both oversized and undersized content and allow exact edge alignment.
 */
internal fun clampMapPanToRotatedBounds(
    pan: Offset,
    zoom: Float,
    canvasW: Int,
    canvasH: Int,
    baseOffset: Offset,
    baseScale: Float,
    imgW: Float,
    imgH: Float,
    alignmentYaw: Float
): Offset {
    if (
        canvasW <= 0 || canvasH <= 0 ||
        zoom <= 0f || baseScale <= 0f || imgW <= 0f || imgH <= 0f
    ) {
        return pan
    }

    val rotatedBounds = rotatedMapBounds(imgW, imgH, alignmentYaw)
    val transformedLeft = zoom * (baseOffset.x + rotatedBounds.left * baseScale)
    val transformedTop = zoom * (baseOffset.y + rotatedBounds.top * baseScale)
    val transformedRight = zoom * (baseOffset.x + rotatedBounds.right * baseScale)
    val transformedBottom = zoom * (baseOffset.y + rotatedBounds.bottom * baseScale)

    val panAtLeftEdge = -transformedLeft
    val panAtRightEdge = canvasW.toFloat() - transformedRight
    val panAtTopEdge = -transformedTop
    val panAtBottomEdge = canvasH.toFloat() - transformedBottom

    return Offset(
        x = pan.x.coerceIn(
            min(panAtLeftEdge, panAtRightEdge),
            max(panAtLeftEdge, panAtRightEdge)
        ),
        y = pan.y.coerceIn(
            min(panAtTopEdge, panAtBottomEdge),
            max(panAtTopEdge, panAtBottomEdge)
        )
    )
}

internal fun screenDeltaToAlignedContent(
    screenDelta: Offset,
    zoom: Float,
    baseScale: Float
): Offset {
    val totalScale = zoom * baseScale
    if (totalScale <= 0f) return Offset.Zero
    return screenDelta / totalScale
}

internal fun moveEraseRectWithinBounds(
    rect: Rect,
    requestedDelta: Offset,
    bounds: Rect
): Rect {
    val acceptedDelta = clampDragDeltaToBounds(
        childBounds = rect,
        parentBounds = bounds,
        requestedDelta = requestedDelta
    )
    return Rect(
        left = rect.left + acceptedDelta.x,
        top = rect.top + acceptedDelta.y,
        right = rect.right + acceptedDelta.x,
        bottom = rect.bottom + acceptedDelta.y
    )
}

internal fun resizeEraseRectBottomRight(
    rect: Rect,
    requestedDelta: Offset,
    bounds: Rect,
    minimumWidth: Float,
    minimumHeight: Float
): Rect {
    val canResizeHorizontally = rect.left + minimumWidth <= bounds.right
    val canResizeVertically = rect.top + minimumHeight <= bounds.bottom
    val resizedRight = if (canResizeHorizontally) {
        (rect.right + requestedDelta.x).coerceIn(rect.left + minimumWidth, bounds.right)
    } else {
        rect.right
    }
    val resizedBottom = if (canResizeVertically) {
        (rect.bottom + requestedDelta.y).coerceIn(rect.top + minimumHeight, bounds.bottom)
    } else {
        rect.bottom
    }
    return Rect(
        left = rect.left,
        top = rect.top,
        right = resizedRight,
        bottom = resizedBottom
    )
}
