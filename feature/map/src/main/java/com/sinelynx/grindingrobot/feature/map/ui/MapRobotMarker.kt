package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun robotPoseToBitmapPoint(
    pose: DevicePosePayload?,
    geo: MapGeo?,
    mapImageSize: Pair<Int, Int>?,
    bitmapSize: Pair<Int, Int>?
): Offset? {
    pose ?: return null
    geo ?: return null
    val mapSize = mapImageSize ?: (geo.mapWidth to geo.mapHeight)
    val bmpSize = bitmapSize ?: mapSize
    if (
        geo.resolution <= 0f ||
        geo.mapWidth <= 0 ||
        geo.mapHeight <= 0 ||
        mapSize.first <= 0 ||
        mapSize.second <= 0 ||
        bmpSize.first <= 0 ||
        bmpSize.second <= 0
    ) {
        return null
    }

    val rad = Math.toRadians(geo.headingDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    val dx = pose.x - geo.originX.toFloat()
    val dy = pose.y - geo.originY.toFloat()
    val localXM = c * dx + s * dy
    val localYM = -s * dx + c * dy
    val tcpX = localXM / geo.resolution
    val tcpY = geo.mapHeight - localYM / geo.resolution
    if (tcpX < 0f || tcpX > mapSize.first || tcpY < 0f || tcpY > mapSize.second) {
        return null
    }
    return Offset(
        x = tcpX * bmpSize.first / mapSize.first.toFloat(),
        y = tcpY * bmpSize.second / mapSize.second.toFloat()
    )
}

@Composable
fun BoxScope.FittedMapRobotMarker(
    pose: DevicePosePayload?,
    geo: MapGeo?,
    mapImageSize: Pair<Int, Int>?,
    bitmapSize: Pair<Int, Int>?,
    markerSize: Dp = 40.dp,
    viewportScale: Float = 1f,
    viewportOffset: Offset = Offset.Zero,
    robotWidth: Double? = null,
    robotLength: Double? = null,
    mapRotationDeg: Float = 0f
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
    )
    val bitmapPoint = robotPoseToBitmapPoint(pose, geo, mapImageSize, bitmapSize) ?: return
    val bmpSize = bitmapSize ?: mapImageSize ?: return
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || bmpSize.first <= 0 || bmpSize.second <= 0) {
        return
    }
    val scale = minOf(
        viewportSize.width / bmpSize.first.toFloat(),
        viewportSize.height / bmpSize.second.toFloat()
    )
    val imageOffset = Offset(
        x = (viewportSize.width - bmpSize.first * scale) / 2f,
        y = (viewportSize.height - bmpSize.second * scale) / 2f
    )
    val imageCenter = Offset(
        x = imageOffset.x + bmpSize.first * scale / 2f,
        y = imageOffset.y + bmpSize.second * scale / 2f
    )
    val rotatedPoint = Offset(
        x = imageOffset.x + bitmapPoint.x * scale,
        y = imageOffset.y + bitmapPoint.y * scale
    ).rotateAround(imageCenter, mapRotationDeg)

    val (drawWidth, drawLength) = remember(viewportScale, robotWidth, robotLength) {
        val defaultSize = markerSize
        if (robotWidth != null && robotWidth > 0.0 && robotLength != null && robotLength > 0.0) {
            val baseWidth: Dp
            val baseLength: Dp
            if (robotLength >= robotWidth) {
                baseLength = defaultSize
                baseWidth = defaultSize * (robotWidth / robotLength).toFloat()
            } else {
                baseWidth = defaultSize
                baseLength = defaultSize * (robotLength / robotWidth).toFloat()
            }
            val drawW = (baseWidth * viewportScale).coerceIn(16.dp, 100.dp)
            val drawL = (baseLength * viewportScale).coerceIn(16.dp, 100.dp)
            drawW to drawL
        } else {
            val drawW = (defaultSize * viewportScale).coerceIn(16.dp, 100.dp)
            drawW to drawW
        }
    }

    RobotPoseIcon(
        center = Offset(
            x = rotatedPoint.x * viewportScale + viewportOffset.x,
            y = rotatedPoint.y * viewportScale + viewportOffset.y
        ),
        // center 已按旋转后的地图定位；heading 再减地图角可保持机器人相对地图的真实朝向。
        headingDeg = (pose?.headingDeg ?: 0f) - mapRotationDeg,
        width = drawWidth,
        length = drawLength
    )
}

private fun Offset.rotateAround(center: Offset, rotationDeg: Float): Offset {
    if (rotationDeg == 0f) return this
    val radians = Math.toRadians(rotationDeg.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + dx * cosine - dy * sine,
        y = center.y + dx * sine + dy * cosine
    )
}

@Composable
fun BoxScope.RobotPoseIcon(
    center: Offset,
    headingDeg: Float,
    width: Dp,
    length: Dp
) {
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val lengthPx = with(density) { length.toPx() }
    Image(
        painter = painterResource(id = R.drawable.ic_robot),
        contentDescription = "机器人位置",
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (center.x - widthPx / 2f).roundToInt(),
                    y = (center.y - lengthPx / 2f).roundToInt()
                )
            }
            .size(width = width, height = length)
            .graphicsLayer(rotationZ = 90f - headingDeg)
    )
}

@Composable
fun BoxScope.RobotPoseIcon(
    center: Offset,
    headingDeg: Float,
    markerSize: Dp = 40.dp
) {
    RobotPoseIcon(
        center = center,
        headingDeg = headingDeg,
        width = markerSize,
        length = markerSize
    )
}
