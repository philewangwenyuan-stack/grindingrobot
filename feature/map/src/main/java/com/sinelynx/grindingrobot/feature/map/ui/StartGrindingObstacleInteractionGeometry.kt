package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

internal val startGrindingCircleResizeDirection = Offset(0.85f, 0.35f)

internal fun startGrindingRotatedBounds(
    rect: Rect,
    rotationCenter: Offset,
    rotationDeg: Float
): Rect {
    val rotatedCorners = listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
        Offset(rect.left, rect.bottom)
    ).map { point ->
        rotateStartGrindingPoint(point, rotationCenter, rotationDeg)
    }
    return requireNotNull(boundsOfPoints(rotatedCorners))
}

internal fun startGrindingScreenDeltaToModel(
    screenDelta: Offset,
    viewportScale: Float
): Offset {
    return if (viewportScale > 0f) screenDelta / viewportScale else Offset.Zero
}

internal fun moveStartGrindingObstacleFromStart(
    startShape: TaskObstacleShape,
    accumulatedScreenDelta: Offset,
    viewportScale: Float,
    bounds: Rect
): TaskObstacleShape {
    val modelDelta = startGrindingScreenDeltaToModel(accumulatedScreenDelta, viewportScale)
    return startShape.moveBy(modelDelta, bounds)
}

private fun TaskObstacleShape.moveBy(
    dragAmount: Offset,
    bounds: Rect
): TaskObstacleShape {
    return when (mode) {
        TaskObstacleMode.Rectangle -> {
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            copy(
                center = Offset(
                    x = (center.x + dragAmount.x)
                        .coerceIn(bounds.left + halfWidth, bounds.right - halfWidth),
                    y = (center.y + dragAmount.y)
                        .coerceIn(bounds.top + halfHeight, bounds.bottom - halfHeight)
                )
            )
        }

        TaskObstacleMode.Circle -> copy(
            center = Offset(
                x = (center.x + dragAmount.x)
                    .coerceIn(bounds.left + radius, bounds.right - radius),
                y = (center.y + dragAmount.y)
                    .coerceIn(bounds.top + radius, bounds.bottom - radius)
            )
        )
    }
}

internal fun resizeStartGrindingObstacleFromStart(
    startShape: TaskObstacleShape,
    accumulatedScreenDelta: Offset,
    viewportScale: Float,
    bounds: Rect,
    minimumSize: Size,
    circleResizeDirection: Offset = startGrindingCircleResizeDirection
): TaskObstacleShape {
    return when (startShape.mode) {
        TaskObstacleMode.Rectangle -> resizeStartGrindingRectangleFromStart(
            startShape = startShape,
            modelDelta = startGrindingScreenDeltaToModel(accumulatedScreenDelta, viewportScale),
            bounds = bounds,
            minimumSize = minimumSize
        )

        TaskObstacleMode.Circle -> resizeStartGrindingCircleFromStart(
            startShape = startShape,
            accumulatedScreenDelta = accumulatedScreenDelta,
            viewportScale = viewportScale,
            bounds = bounds,
            minimumSize = minimumSize,
            resizeDirection = circleResizeDirection
        )
    }
}

private fun resizeStartGrindingRectangleFromStart(
    startShape: TaskObstacleShape,
    modelDelta: Offset,
    bounds: Rect,
    minimumSize: Size
): TaskObstacleShape {
    val startRect = startShape.rect
    val maximumWidth = (bounds.right - startRect.left).coerceAtLeast(0f)
    val maximumHeight = (bounds.bottom - startRect.top).coerceAtLeast(0f)
    val minimumWidth = min(minimumSize.width, maximumWidth)
    val minimumHeight = min(minimumSize.height, maximumHeight)
    val nextWidth = (startShape.width + modelDelta.x)
        .coerceIn(minimumWidth, maximumWidth)
    val nextHeight = (startShape.height + modelDelta.y)
        .coerceIn(minimumHeight, maximumHeight)
    return startShape.copy(
        center = Offset(
            x = startRect.left + nextWidth / 2f,
            y = startRect.top + nextHeight / 2f
        ),
        width = nextWidth,
        height = nextHeight
    )
}

private fun resizeStartGrindingCircleFromStart(
    startShape: TaskObstacleShape,
    accumulatedScreenDelta: Offset,
    viewportScale: Float,
    bounds: Rect,
    minimumSize: Size,
    resizeDirection: Offset
): TaskObstacleShape {
    val directionLength = resizeDirection.getDistance()
    val projectedScreenDelta = if (directionLength > 0.0001f) {
        accumulatedScreenDelta.x * resizeDirection.x / directionLength +
            accumulatedScreenDelta.y * resizeDirection.y / directionLength
    } else {
        0f
    }
    val radialDelta = if (viewportScale > 0f) projectedScreenDelta / viewportScale else 0f
    val minimumRadius = max(minimumSize.width, minimumSize.height) / 2f
    val maximumRadius = min(
        min(startShape.center.x - bounds.left, bounds.right - startShape.center.x),
        min(startShape.center.y - bounds.top, bounds.bottom - startShape.center.y)
    ).coerceAtLeast(0f)
    val effectiveMinimumRadius = min(minimumRadius, maximumRadius)
    val nextRadius = (startShape.radius + radialDelta)
        .coerceIn(effectiveMinimumRadius, maximumRadius)
    return startShape.copy(
        width = nextRadius * 2f,
        height = nextRadius * 2f,
        radius = nextRadius
    )
}

private fun rotateStartGrindingPoint(
    point: Offset,
    center: Offset,
    rotationDeg: Float
): Offset {
    if (rotationDeg == 0f) return point
    val radians = Math.toRadians(rotationDeg.toDouble())
    val cosine = kotlin.math.cos(radians).toFloat()
    val sine = kotlin.math.sin(radians).toFloat()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Offset(
        x = center.x + dx * cosine - dy * sine,
        y = center.y + dx * sine + dy * cosine
    )
}
