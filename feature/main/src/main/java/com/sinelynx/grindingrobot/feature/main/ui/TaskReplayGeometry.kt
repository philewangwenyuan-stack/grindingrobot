package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sinelynx.grindingrobot.feature.main.viewmodel.TrajectoryPoint
import com.sinelynx.grindingrobot.feature.map.ui.worldPointToBitmapOffset
import com.sinelynx.grindingrobot.feature.map.ui.grindingDiscWorldCenter
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Matches the task map's 1–8x zoom and rotated-image pan limits. */
internal fun clampReplayMapPan(
    viewport: Size,
    bitmapSize: Pair<Int, Int>,
    rotationDeg: Float,
    zoom: Float,
    pan: Offset
): Offset {
    if (zoom <= 1f || viewport.width <= 0f || viewport.height <= 0f ||
        bitmapSize.first <= 0 || bitmapSize.second <= 0) return Offset.Zero
    val fit = minOf(viewport.width / bitmapSize.first, viewport.height / bitmapSize.second)
    val width = bitmapSize.first * fit
    val height = bitmapSize.second * fit
    val angle = Math.toRadians(rotationDeg.toDouble())
    val rotatedWidth = (abs(cos(angle)) * width + abs(sin(angle)) * height).toFloat()
    val rotatedHeight = (abs(sin(angle)) * width + abs(cos(angle)) * height).toFloat()
    val left = (viewport.width - rotatedWidth) / 2f
    val top = (viewport.height - rotatedHeight) / 2f
    val right = left + rotatedWidth
    val bottom = top + rotatedHeight
    return Offset(
        pan.x.coerceIn(right * (1f - zoom), left * (1f - zoom)),
        pan.y.coerceIn(bottom * (1f - zoom), top * (1f - zoom))
    )
}

/** 当前回放点之前已经完成的轨迹线段数量。 */
internal fun visibleTrajectorySegmentCount(pointCount: Int, currentIndex: Int): Int {
    if (pointCount <= 1) return 0
    return currentIndex.coerceIn(0, pointCount - 1)
}

/** 与 Step2/Step3 共用同一条世界坐标到未旋转位图坐标的投影链。 */
internal fun trajectoryPointToBitmapOffset(
    point: TrajectoryPoint,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>,
    bitmapSize: Pair<Int, Int>
): Offset? = worldPointToBitmapOffset(
    point = MapPreviewPointItem(point.x, point.y),
    geo = geo,
    mapImageSize = mapImageSize,
    bitmapSize = bitmapSize
)?.takeIf {
    it.x.isFinite() && it.y.isFinite() &&
        it.x in 0f..bitmapSize.first.toFloat() &&
        it.y in 0f..bitmapSize.second.toFloat()
}

internal fun grindingTrajectoryPointToBitmapOffset(
    point: TrajectoryPoint,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>,
    bitmapSize: Pair<Int, Int>,
    footprint: List<AppState.FootprintPoint>,
    robotLength: Double?
): Offset? {
    val center = grindingDiscWorldCenter(point.x, point.y, point.headingDeg, footprint, robotLength) ?: return null
    return trajectoryPointToBitmapOffset(point.copy(x = center.x, y = center.y), geo, mapImageSize, bitmapSize)
}

/** 把真实米制长度映射为当前等比适配画布上的像素长度。 */
internal fun trajectoryMetersToScreenPx(
    meters: Double?,
    geo: MapGeo,
    mapImageSize: Pair<Int, Int>,
    bitmapSize: Pair<Int, Int>,
    canvasSize: Size
): Float? {
    if (
        meters == null || !meters.isFinite() || meters <= 0.0 ||
        geo.resolution <= 0f || mapImageSize.first <= 0 || mapImageSize.second <= 0 ||
        bitmapSize.first <= 0 || bitmapSize.second <= 0 ||
        canvasSize.width <= 0f || canvasSize.height <= 0f
    ) return null
    val bitmapPixels = meters.toFloat() / geo.resolution *
        bitmapSize.first / mapImageSize.first.toFloat()
    val fitScale = minOf(
        canvasSize.width / bitmapSize.first.toFloat(),
        canvasSize.height / bitmapSize.second.toFloat()
    )
    return (bitmapPixels * fitScale).takeIf { it.isFinite() && it > 0f }
}
