package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class InitialBoundaryRectPlacement(
    val screenRect: Rect,
    val contentSize: IntSize
)

internal fun calculateInitialBoundaryRectPlacement(
    preferredScreenCenter: Offset?,
    mapScreenBounds: Rect,
    contentScale: Float,
    preferredScreenSize: Size = Size(160f, 160f)
): InitialBoundaryRectPlacement? {
    if (
        !contentScale.isFinite() || contentScale <= 0f ||
        !mapScreenBounds.width.isFinite() || mapScreenBounds.width <= 0f ||
        !mapScreenBounds.height.isFinite() || mapScreenBounds.height <= 0f
    ) {
        return null
    }

    val maximumContentWidth = floor(mapScreenBounds.width / contentScale).toInt()
    val maximumContentHeight = floor(mapScreenBounds.height / contentScale).toInt()
    if (maximumContentWidth <= 0 || maximumContentHeight <= 0) return null

    val desiredContentWidth = maxOf(
        (preferredScreenSize.width / contentScale).roundToInt(),
        1
    )
    val desiredContentHeight = maxOf(
        (preferredScreenSize.height / contentScale).roundToInt(),
        1
    )
    val contentSize = IntSize(
        width = desiredContentWidth.coerceAtMost(maximumContentWidth),
        height = desiredContentHeight.coerceAtMost(maximumContentHeight)
    )
    val screenWidth = contentSize.width * contentScale
    val screenHeight = contentSize.height * contentScale
    val requestedCenter = preferredScreenCenter
        ?.takeIf { it.x.isFinite() && it.y.isFinite() }
        ?: mapScreenBounds.center
    val resolvedCenter = Offset(
        x = requestedCenter.x.coerceIn(
            mapScreenBounds.left + screenWidth / 2f,
            mapScreenBounds.right - screenWidth / 2f
        ),
        y = requestedCenter.y.coerceIn(
            mapScreenBounds.top + screenHeight / 2f,
            mapScreenBounds.bottom - screenHeight / 2f
        )
    )

    return InitialBoundaryRectPlacement(
        screenRect = Rect(
            left = resolvedCenter.x - screenWidth / 2f,
            top = resolvedCenter.y - screenHeight / 2f,
            right = resolvedCenter.x + screenWidth / 2f,
            bottom = resolvedCenter.y + screenHeight / 2f
        ),
        contentSize = contentSize
    )
}

internal fun screenDeltaToContentDelta(
    screenDelta: Offset,
    totalRotationDeg: Float,
    contentScale: Float
): Offset {
    if (contentScale <= 0f) return Offset.Zero
    val radians = -totalRotationDeg * PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    return Offset(
        x = (screenDelta.x * cosine - screenDelta.y * sine) / contentScale,
        y = (screenDelta.x * sine + screenDelta.y * cosine) / contentScale
    )
}

internal fun projectScreenDeltaOntoRectAxes(
    screenDelta: Offset,
    screenCorners: List<Offset>,
    contentScale: Float
): Offset {
    if (screenCorners.size != 4 || contentScale <= 0f) return Offset.Zero
    val widthAxis = (screenCorners[1] - screenCorners[0]).normalizedOrZero()
    val heightAxis = (screenCorners[3] - screenCorners[0]).normalizedOrZero()
    return Offset(
        x = screenDelta.dot(widthAxis) / contentScale,
        y = screenDelta.dot(heightAxis) / contentScale
    )
}

internal fun radialScreenDeltaToContentDelta(
    screenDelta: Offset,
    center: Offset,
    handleCenter: Offset,
    contentScale: Float
): Float {
    if (contentScale <= 0f) return 0f
    val radialAxis = (handleCenter - center).normalizedOrZero()
    return screenDelta.dot(radialAxis) / contentScale
}

internal fun topLeftForAnchoredRotatedRect(
    anchoredTopLeftCorner: Offset,
    size: Size,
    rotationDeg: Float
): Offset {
    val halfSize = Offset(size.width / 2f, size.height / 2f)
    val radians = rotationDeg * PI.toFloat() / 180f
    val rotatedHalfSize = Offset(
        x = halfSize.x * cos(radians) - halfSize.y * sin(radians),
        y = halfSize.x * sin(radians) + halfSize.y * cos(radians)
    )
    return anchoredTopLeftCorner - halfSize + rotatedHalfSize
}

internal fun clampPointToBounds(point: Offset, bounds: Rect): Offset = Offset(
    x = point.x.coerceIn(bounds.left, bounds.right),
    y = point.y.coerceIn(bounds.top, bounds.bottom)
)

internal fun isRectInsideBounds(rect: Rect, bounds: Rect, tolerance: Float = 0.01f): Boolean {
    return rect.left >= bounds.left - tolerance &&
        rect.top >= bounds.top - tolerance &&
        rect.right <= bounds.right + tolerance &&
        rect.bottom <= bounds.bottom + tolerance
}

internal fun clampRectSizeToBounds(
    startSize: Size,
    requestedDelta: Offset,
    minimumSize: Size,
    bounds: Rect,
    screenBoundsForSize: (Size) -> Rect
): Size {
    val targetSize = Size(
        width = (startSize.width + requestedDelta.x).coerceAtLeast(minimumSize.width),
        height = (startSize.height + requestedDelta.y).coerceAtLeast(minimumSize.height)
    )
    if (isRectInsideBounds(screenBoundsForSize(targetSize), bounds)) return targetSize

    var lower = 0f
    var upper = 1f
    var best = startSize
    repeat(24) {
        val fraction = (lower + upper) / 2f
        val candidate = Size(
            width = (startSize.width + (targetSize.width - startSize.width) * fraction)
                .coerceAtLeast(minimumSize.width),
            height = (startSize.height + (targetSize.height - startSize.height) * fraction)
                .coerceAtLeast(minimumSize.height)
        )
        if (isRectInsideBounds(screenBoundsForSize(candidate), bounds)) {
            best = candidate
            lower = fraction
        } else {
            upper = fraction
        }
    }
    return best
}

internal fun clampRotationToBounds(
    startRotationDeg: Float,
    requestedRotationDeg: Float,
    bounds: Rect,
    screenBoundsForRotation: (Float) -> Rect
): Float {
    val normalizedTarget = normalizeInteractionDegrees(requestedRotationDeg)
    val delta = shortestInteractionDegreeDelta(startRotationDeg, normalizedTarget)
    val normalizedStart = normalizeInteractionDegrees(startRotationDeg)
    if (abs(delta) <= 0.0001f) return normalizedStart

    val sampleCount = ceil(abs(delta) / 4f).toInt().coerceAtLeast(1)
    var lastValidFraction = 0f
    for (sampleIndex in 1..sampleCount) {
        val fraction = sampleIndex / sampleCount.toFloat()
        val candidate = normalizeInteractionDegrees(startRotationDeg + delta * fraction)
        if (!isRectInsideBounds(screenBoundsForRotation(candidate), bounds)) {
            var lower = lastValidFraction
            var upper = fraction
            var best = normalizeInteractionDegrees(startRotationDeg + delta * lower)
            repeat(24) {
                val middle = (lower + upper) / 2f
                val middleCandidate = normalizeInteractionDegrees(
                    startRotationDeg + delta * middle
                )
                if (isRectInsideBounds(screenBoundsForRotation(middleCandidate), bounds)) {
                    best = middleCandidate
                    lower = middle
                } else {
                    upper = middle
                }
            }
            return best
        }
        lastValidFraction = fraction
    }
    return normalizedTarget
}

private fun normalizeInteractionDegrees(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

private fun shortestInteractionDegreeDelta(from: Float, to: Float): Float {
    return (to - from + 540f) % 360f - 180f
}

private fun Offset.dot(other: Offset): Float = x * other.x + y * other.y

private fun Offset.normalizedOrZero(): Offset {
    val distance = getDistance()
    return if (distance <= 0.0001f) Offset.Zero else this / distance
}
