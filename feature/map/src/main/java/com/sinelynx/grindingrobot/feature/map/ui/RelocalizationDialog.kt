package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.RelocalizationSettingsLoadState
import kotlin.math.cos
import kotlin.math.sin

private data class RelocalizationPose(
    val x: Float,
    val y: Float,
    val headingDeg: Float
)

/**
 * 初始位置重定位弹窗。
 *
 * 旧的速度、转数和虚拟摇杆参数暂时保留在函数签名中，保证已有页面调用方和状态链路兼容；
 * 新流程只通过地图选点和微调得到初始位姿，再发送 0x052A。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun RelocalizationDialog(
    mapPreview: MapPreviewUiState?,
    robotPose: DevicePosePayload?,
    isSuccessful: Boolean,
    rawStatus: String,
    runSpeed: Float,
    turnSpeed: Int,
    turnCount: Int,
    settingsLoadState: RelocalizationSettingsLoadState,
    settingsError: String,
    onRadarMapSync: () -> Unit,
    onRadarRelocalization: (Float, Float, Float) -> Unit,
    onDismiss: () -> Unit,
    onRetrySettings: () -> Unit,
    onRunSpeedDecrease: () -> Unit,
    onRunSpeedIncrease: () -> Unit,
    onTurnSpeedDecrease: () -> Unit,
    onTurnSpeedIncrease: () -> Unit,
    onTurnCountDecrease: () -> Unit,
    onTurnCountIncrease: () -> Unit,
    onTurnCountChange: (Float) -> Unit,
    onTurnCountChangeFinished: () -> Unit,
    onCommandStart: (DirectionCommand) -> Unit,
    onCommandEnd: (DirectionCommand) -> Unit,
    onPositionChanged: (JoystickPosition) -> Unit
) {
    val previewBitmap = remember(mapPreview?.imageBytes) {
        mapPreview?.imageBytes
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?.asImageBitmap()
    }
    // 只在弹窗打开/地图切换时从板端取一次初始位姿。
    // 重定位状态轮询会持续刷新 robotPose，但不能覆盖用户正在编辑的本地 pose。
    var pose by remember(mapPreview?.imageBytes, mapPreview?.mapWidth, mapPreview?.mapHeight) {
        mutableStateOf(defaultRelocalizationPose(mapPreview, robotPose))
    }
    var isEditing by remember { mutableStateOf(false) }
    var isConfirmed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFFF8FBFF))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "重定位",
                        color = Color(0xFF18202A),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    RelocalizationHeaderButton(
                        text = "同步地图",
                        onClick = onRadarMapSync
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB8BBC0))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "×",
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    RelocalizationMapPanel(
                        mapPreview = mapPreview,
                        previewBitmap = previewBitmap,
                        pose = pose,
                        isEditing = isEditing,
                        onPoseChanged = { pose = it },
                        modifier = Modifier
                            .weight(1.55f)
                            .fillMaxHeight()
                    )
                    RelocalizationWorkspacePanel(
                        pose = pose,
                        isEditing = isEditing,
                        isConfirmed = isConfirmed,
                        onStartEditing = {
                            isEditing = true
                            isConfirmed = false
                        },
                        onConfirmPosition = {
                            isEditing = false
                            isConfirmed = true
                        },
                        onResetPosition = {
                            isEditing = true
                            isConfirmed = false
                        },
                        onMove = { dx, dy ->
                            pose = pose.copy(x = pose.x + dx, y = pose.y + dy)
                        },
                        onRotate = { delta ->
                            pose = pose.copy(headingDeg = normalizeHeading(pose.headingDeg + delta))
                        },
                        onRelocalization = {
                            onRadarRelocalization(pose.x, pose.y, pose.headingDeg)
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                RelocalizationStatusFooter(
                    isSuccessful = isSuccessful,
                    rawStatus = rawStatus
                )
            }
        }
    }
}

@Composable
private fun RelocalizationMapPanel(
    mapPreview: MapPreviewUiState?,
    previewBitmap: ImageBitmap?,
    pose: RelocalizationPose,
    isEditing: Boolean,
    onPoseChanged: (RelocalizationPose) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val displayRotation = mapPreview?.let {
        it.alignmentYawDeg + it.rotationAlignmentDeltaDeg
    } ?: 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE6E9EC))
            .border(1.dp, Color(0xFFE2E7ED), RoundedCornerShape(18.dp))
            .onSizeChanged { viewportSize = it }
            .pointerInput(isEditing, mapPreview, previewBitmap, zoom, pan) {
                detectTapGestures { tap ->
                    if (!isEditing || mapPreview == null || previewBitmap == null) return@detectTapGestures
                    val world = viewportTapToWorld(
                        tap = tap,
                        viewportSize = Size(
                            viewportSize.width.toFloat(),
                            viewportSize.height.toFloat()
                        ),
                        bitmapWidth = previewBitmap.width,
                        bitmapHeight = previewBitmap.height,
                        preview = mapPreview,
                        rotationDeg = displayRotation,
                        zoom = zoom,
                        pan = pan
                    ) ?: return@detectTapGestures
                    onPoseChanged(pose.copy(x = world.first, y = world.second))
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                    pan += gesturePan
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "地图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = displayRotation }
                )
                if (mapPreview != null) {
                    val markerPoint = worldPoseToViewport(
                        pose = pose,
                        viewportSize = Size(
                            viewportSize.width.toFloat(),
                            viewportSize.height.toFloat()
                        ),
                        bitmapWidth = previewBitmap.width,
                        bitmapHeight = previewBitmap.height,
                        preview = mapPreview,
                        rotationDeg = displayRotation
                    )
                    if (markerPoint != null) {
                        RelocalizationArrow(
                            center = markerPoint,
                            headingDeg = pose.headingDeg,
                            mapRotationDeg = displayRotation
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "暂无地图，请先同步地图",
                        color = Color(0xFF707984),
                        fontSize = 18.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapZoomButton(text = "+") {
                zoom = (zoom + 0.25f).coerceAtMost(4f)
            }
            MapZoomButton(text = "−") {
                zoom = (zoom - 0.25f).coerceAtLeast(1f)
            }
        }

        if (isEditing && previewBitmap != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xEFFFFFFF))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "点击地图放置箭头，可用右侧按钮微调",
                    color = Color(0xFF3B4652),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RelocalizationWorkspacePanel(
    pose: RelocalizationPose,
    isEditing: Boolean,
    isConfirmed: Boolean,
    onStartEditing: () -> Unit,
    onConfirmPosition: () -> Unit,
    onResetPosition: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    onRelocalization: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E7ED), RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设置初始位置",
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF18202A),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE1E5EA))
        )
        Spacer(modifier = Modifier.height(18.dp))

        if (!isEditing && !isConfirmed) {
            Text(
                text = "先在地图上设置机器人初始位置",
                color = Color(0xFF66717D),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            PrimaryRelocalizationButton(
                text = "设置初始位置",
                onClick = onStartEditing
            )
        } else {
            Text(
                text = if (isConfirmed) "初始位置已确认" else "方向和位置可按步长微调",
                color = if (isConfirmed) Color(0xFF00A0E9) else Color(0xFF66717D),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "X %.2f m   Y %.2f m".format(pose.x, pose.y),
                color = Color(0xFF3B4652),
                fontSize = 14.sp
            )
            Text(
                text = "方向 %.1f°".format(pose.headingDeg),
                color = Color(0xFF3B4652),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (isEditing) {
                RelocalizationAdjustPad(
                    onMove = onMove,
                    onRotate = onRotate
                )
                Spacer(modifier = Modifier.height(18.dp))
                PrimaryRelocalizationButton(
                    text = "确定位置",
                    onClick = onConfirmPosition
                )
            } else {
                SecondaryRelocalizationButton(
                    text = "重新设置",
                    onClick = onResetPosition
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryRelocalizationButton(
                    text = "重定位",
                    onClick = onRelocalization
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "微调步长：0.05 m / 5°",
            color = Color(0xFF9AA2AB),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun RelocalizationAdjustPad(
    onMove: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AdjustButton(text = "↑") { onMove(0f, 0.05f) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdjustButton(text = "←") { onMove(-0.05f, 0f) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F8)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "位", color = Color(0xFF77828D), fontSize = 14.sp)
            }
            AdjustButton(text = "→") { onMove(0.05f, 0f) }
        }
        AdjustButton(text = "↓") { onMove(0f, -0.05f) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdjustButton(text = "↺") { onRotate(-5f) }
            AdjustButton(text = "↻") { onRotate(5f) }
        }
    }
}

@Composable
private fun AdjustButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEAF5FB))
            .border(1.dp, Color(0xFFB9E1F4), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF008FD1),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RelocalizationArrow(
    center: Offset,
    headingDeg: Float,
    mapRotationDeg: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        rotate(
            // heading=0 points along map +X (screen right), heading=90 points along
            // map +Y (screen up). The arrow itself is drawn from a screen-up base.
            degrees = relocalizationArrowRotationDeg(headingDeg, mapRotationDeg),
            pivot = center
        ) {
            val arrowLength = 18.dp.toPx()
            val headLength = 6.dp.toPx()
            val headHalfWidth = 4.dp.toPx()
            val green = Color(0xFF2FB344)
            val tip = center - Offset(0f, arrowLength)
            drawLine(
                color = green,
                start = center,
                end = tip,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            val arrowHead = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - headHalfWidth, tip.y + headLength)
                lineTo(tip.x + headHalfWidth, tip.y + headLength)
                close()
            }
            drawPath(arrowHead, color = green)
            // 中心只保留一个小点，不再绘制外圈或圆形底座。
            drawCircle(color = green, radius = 3.dp.toPx(), center = center)
        }
    }
}

internal fun relocalizationArrowRotationDeg(headingDeg: Float, mapRotationDeg: Float): Float =
    90f - headingDeg + mapRotationDeg

@Composable
private fun RelocalizationStatusFooter(
    isSuccessful: Boolean,
    rawStatus: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                isSuccessful -> "雷达定位成功"
                rawStatus.isNotBlank() -> "当前雷达状态：$rawStatus"
                else -> "请设置初始位置后再开始重定位"
            },
            color = if (isSuccessful) Color(0xFF00A0E9) else Color(0xFF69737E),
            fontSize = 14.sp,
            fontWeight = if (isSuccessful) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun RelocalizationHeaderButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF00A0E9))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PrimaryRelocalizationButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF00A0E9))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SecondaryRelocalizationButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFF00A0E9), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF008FD1),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MapZoomButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF26313D),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun defaultRelocalizationPose(
    preview: MapPreviewUiState?,
    robotPose: DevicePosePayload?
): RelocalizationPose {
    if (robotPose != null) {
        return RelocalizationPose(robotPose.x, robotPose.y, robotPose.headingDeg)
    }
    if (preview == null || preview.resolution <= 0f) {
        return RelocalizationPose(0f, 0f, 0f)
    }
    val localX = preview.mapWidth * preview.resolution / 2f
    val localY = preview.mapHeight * preview.resolution / 2f
    val radians = Math.toRadians(preview.headingDeg.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    return RelocalizationPose(
        x = (preview.originX + c * localX - s * localY).toFloat(),
        y = (preview.originY + s * localX + c * localY).toFloat(),
        headingDeg = 0f
    )
}

private fun worldPoseToViewport(
    pose: RelocalizationPose,
    viewportSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int,
    preview: MapPreviewUiState,
    rotationDeg: Float
): Offset? {
    val geo = preview.toRelocalizationMapGeo()
    val bitmapPoint = worldPointToBitmapOffset(
        point = MapPreviewPointItem(pose.x, pose.y),
        geo = geo,
        mapImageSize = geo.mapWidth to geo.mapHeight,
        bitmapSize = bitmapWidth to bitmapHeight
    ) ?: return null
    return taskBitmapPointToFittedPoint(
        point = bitmapPoint,
        canvasSize = viewportSize,
        bitmapSize = bitmapWidth to bitmapHeight,
        rotationDeg = rotationDeg
    )
}

private fun viewportTapToWorld(
    tap: Offset,
    viewportSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int,
    preview: MapPreviewUiState,
    rotationDeg: Float,
    zoom: Float,
    pan: Offset
): Pair<Float, Float>? {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f || zoom <= 0f) return null
    val center = viewportCenter(viewportSize)
    val basePoint = Offset(
        x = (tap.x - pan.x - center.x) / zoom + center.x,
        y = (tap.y - pan.y - center.y) / zoom + center.y
    )
    val unrotatedPoint = basePoint.rotateAround(center, -rotationDeg)
    val imageRect = fittedImageRect(viewportSize, bitmapWidth, bitmapHeight)
    if (!imageRect.contains(unrotatedPoint)) return null
    val normalizedX = ((unrotatedPoint.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
    val normalizedY = ((unrotatedPoint.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
    val bitmapX = normalizedX * bitmapWidth
    val bitmapY = normalizedY * bitmapHeight
    return bitmapToWorldPoint(bitmapX, bitmapY, preview, bitmapWidth, bitmapHeight)
}

private fun bitmapToWorldPoint(
    bitmapX: Float,
    bitmapY: Float,
    preview: MapPreviewUiState,
    bitmapWidth: Int,
    bitmapHeight: Int
): Pair<Float, Float>? {
    if (
        preview.resolution <= 0f ||
        preview.mapWidth <= 0 ||
        preview.mapHeight <= 0 ||
        bitmapWidth <= 0 ||
        bitmapHeight <= 0
    ) {
        return null
    }
    val localX = bitmapX / bitmapWidth.toFloat() * preview.mapWidth * preview.resolution
    val localY = (preview.mapHeight - bitmapY / bitmapHeight.toFloat() * preview.mapHeight) *
        preview.resolution
    val radians = Math.toRadians(preview.headingDeg.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    return (
        preview.originX + c * localX - s * localY
        ).toFloat() to (
        preview.originY + s * localX + c * localY
        ).toFloat()
}

private fun MapPreviewUiState.toRelocalizationMapGeo(): MapGeo = MapGeo(
    mapWidth = mapWidth,
    mapHeight = mapHeight,
    resolution = resolution,
    originX = originX,
    originY = originY,
    headingDeg = headingDeg,
    mapVersion = 0
)

private fun fittedImageRect(
    viewportSize: Size,
    bitmapWidth: Int,
    bitmapHeight: Int
): Rect {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return Rect(Offset.Zero, viewportSize)
    }
    val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
    val viewportAspect = viewportSize.width / viewportSize.height
    val width: Float
    val height: Float
    if (imageAspect > viewportAspect) {
        width = viewportSize.width
        height = width / imageAspect
    } else {
        height = viewportSize.height
        width = height * imageAspect
    }
    val left = (viewportSize.width - width) / 2f
    val top = (viewportSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun viewportCenter(size: Size): Offset {
    return Offset(size.width / 2f, size.height / 2f)
}

private fun Offset.rotateAround(
    center: Offset,
    rotationDeg: Float
): Offset {
    if (rotationDeg == 0f) return this
    val radians = Math.toRadians(rotationDeg.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val dx = x - center.x
    val dy = y - center.y
    return Offset(
        x = center.x + dx * c - dy * s,
        y = center.y + dx * s + dy * c
    )
}

private fun normalizeHeading(value: Float): Float {
    var heading = value % 360f
    if (heading > 180f) heading -= 360f
    if (heading <= -180f) heading += 360f
    return heading
}
