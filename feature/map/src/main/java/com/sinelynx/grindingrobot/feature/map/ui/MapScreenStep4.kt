package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4UiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4ViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.Step4SaveMapDraft
import com.sinelynx.grindingrobot.feature.map.viewmodel.Step4LegacyPreviewUiState

@Composable
fun MapScreenStep4(
    viewModel: MapScreenStep4ViewModel,
    mapId: String? = null,
    initialMapName: String? = null,
    scanDirection: String = "X",
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onSave: (Step4SaveMapDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val legacyPreview by viewModel.legacyPreviewUiState.collectAsState()
    DisposableEffect(viewModel, mapId) {
        viewModel.prefillMapName(initialMapName)
        viewModel.loadMapData(mapId)
        onDispose { viewModel.cancelMapLoading() }
    }
    val mapImageBitmap = remember(uiState.mapImageBytes) {
        uiState.mapImageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(12.dp)
    ) {
        val sideWidth = workflowSideWidth(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            MapWorkflowHeader(4, viewModel::emergencyStop)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkflowGuidePanel("预览与保存", "双指缩放地图\n拖动查看细节\n\n确认工作区后保存", Modifier.width(sideWidth).fillMaxHeight())
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("workflow_map"),
                    color = Color(0xFFF6F8FB),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp
                ) {
                    if (uiState.isPlanning) {
                        MapDataLoadingPage(text = "路径规划中...")
                    } else if (mapImageBitmap != null) {
                        WorkflowMapPreview(uiState, mapImageBitmap, mapId)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)),
                                        radius = 900f
                                    )
                                )
                        )
                    }
                }

                WorkflowSavePanel(uiState, viewModel::updateMapName, Modifier.width(sideWidth).fillMaxHeight())
            }

            Spacer(modifier = Modifier.height(8.dp))
            BottomStep4Actions(
                canSave = uiState.canSave,
                onCancelBuild = {
                    viewModel.onCancelBuild()
                    onCancelBuild()
                },
                onPreviousStep = onPreviousStep,
                onSaveClick = {
                    viewModel.onSaveClick(mapId, onSave)
                }
            )
        }

        if (legacyPreview.isVisible) {
            Step4LegacyPreviewDialog(
                preview = legacyPreview, currentState = uiState,
                onDismiss = viewModel::closeLegacyPreview,
                onRetry = { viewModel.openLegacyPreview(mapId) }
            )
        }

        if (uiState.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 5.dp,
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun Step4LegacyPreviewDialog(
    preview: Step4LegacyPreviewUiState,
    currentState: MapScreenStep4UiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp), color = Color(0xFFF6F8FB)
        ) {
            Column(Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "测试：旧版规划图",
                        Modifier.weight(1f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onRetry,
                        enabled = currentState.canPreviewLegacyPlan && !preview.isLoading
                    ) {
                        Text("重试")
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                preview.warning?.let { Text(it, color = Color(0xFFAD6800), fontSize = 14.sp) }
                Box(Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))) {
                    Step4LegacyPreviewMap(preview, currentState)
                }
            }
        }
    }
}

@Composable
internal fun Step4LegacyPreviewMap(
    preview: Step4LegacyPreviewUiState,
    currentState: MapScreenStep4UiState
) {
    val bitmap = remember(preview.imageBytes) {
        preview.imageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            preview.isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("旧版路径规划中...")
            }

            preview.error != null -> Text(preview.error, color = Color(0xFFC62828))
            bitmap == null -> Text("旧版规划图片解码失败", color = Color(0xFFC62828))
            else -> {
                // 沿用旧 Step4 的居中等比适配和响应总角，不额外乘预览比例，也不使用新版缓存的几何。
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap, contentDescription = "旧版规划地图图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = preview.rotationDeg }
                    )
                    preview.mapGeo?.let { geo ->
                        // 仅附加实时机器人；图片内已有设备绘制内容，不再叠加 APP 的路径、区域或起终点。
                        FittedMapRobotMarker(
                            pose = currentState.robotPose,
                            geo = geo,
                            mapImageSize = geo.mapWidth to geo.mapHeight,
                            bitmapSize = preview.bitmapSize ?: (bitmap.width to bitmap.height),
                            robotWidth = currentState.robotWidth,
                            robotLength = currentState.robotLength,
                            robotFootprint = currentState.robotFootprint,
                            mapRotationDeg = preview.rotationDeg
                        )
                    }
                }
            }
        }
    }
}

/** null/非有限值留空与真实 0 区分；负值沿用“--”显示约定。 */
internal fun formatStep4Number(value: Float?): String {
    if (value == null || !value.isFinite()) return ""
    if (value < 0f) return "--"
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        "%.2f".format(value)
    }
}

@Composable
internal fun Step4MapOverlays(
    state: MapScreenStep4UiState,
    bitmapSize: Pair<Int, Int>,
    rotationDeg: Float
) {
    val geo = state.mapGeo ?: return
    val logicalSize = state.mapImageSize ?: return
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier
        .fillMaxSize()
        .onSizeChanged { viewport = it }) {
        // 区域顶点与起终点共用同一投影，坐标差异应先检查输入数据，不能给某类标记补额外缩放。
        fun project(point: MapPreviewPointItem): Offset? =
            worldPointToBitmapOffset(point, geo, logicalSize, bitmapSize)
                ?.let {
                    taskBitmapPointToFittedPoint(
                        it,
                        Size(viewport.width.toFloat(), viewport.height.toFloat()),
                        bitmapSize,
                        rotationDeg
                    )
                }
        Canvas(Modifier.fillMaxSize()) {
            drawMapRegionOverlays(
                state.previewEraseRegions,
                MapRegionOverlayKind.Erase,
                ::project,
                drawBorder = false
            )
            drawMapRegionOverlays(
                state.previewWorkRegions, MapRegionOverlayKind.Work, ::project,
                workFillColor = Color.Transparent
            )
            drawMapRegionOverlays(
                state.previewObstacleRegions,
                MapRegionOverlayKind.Obstacle,
                ::project
            )
        }
        FittedMapRobotMarker(
            pose = state.robotPose,
            geo = geo,
            mapImageSize = logicalSize,
            bitmapSize = bitmapSize,
            robotWidth = state.robotWidth,
            robotLength = state.robotLength,
            robotFootprint = state.robotFootprint,
            mapRotationDeg = rotationDeg
        )
        Canvas(Modifier.fillMaxSize()) {
            state.plannedPath?.let { path ->
                drawFittedTaskPath(
                    path,
                    geo,
                    logicalSize,
                    bitmapSize,
                    rotationDeg,
                    colorByScope = true
                )
            }
            // 路径之上先画全部起点、再画全部终点，保证跨工作区重合时终点也位于最上层。
            val regions = state.workRegions.filter { it.region.enabled }
            // 圆点半径使用屏幕 dp；中心位置使用地图投影，两者不共享地图缩放倍率。
            val markerRadius = 4.dp.toPx()
            regions.forEach { region ->
                region.startPose?.let { pose -> project(MapPreviewPointItem(pose.x, pose.y)) }
                    ?.let { center ->
                        drawCircle(
                            color = Color(0xFF33C461),
                            radius = markerRadius,
                            center = center
                        )
                    }
            }
            regions.forEach { region ->
                region.endPose?.let { pose -> project(MapPreviewPointItem(pose.x, pose.y)) }
                    ?.let { center ->
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = markerRadius,
                            center = center
                        )
                    }
            }
        }
    }
}

@Composable
private fun SaveMapRow(
    label: String,
    enabled: Boolean,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6D737D),
            fontWeight = FontWeight.Medium
        )
        BasicTextField(
            value = value,
            onValueChange = {
                if (enabled) onValueChange(it)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFF202937),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Color(0xFF00A0E9)),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) Color.White else Color(0xFFF3F4F6))
                .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BottomStep4Actions(
    canSave: Boolean,
    onCancelBuild: () -> Unit,
    onPreviousStep: () -> Unit,
    onSaveClick: () -> Unit,
) {
    MapWorkflowActions(onCancelBuild, onPreviousStep, onSaveClick, save = true, enabled = canSave)
}


@Composable
internal fun WorkflowSavePanel(uiState: MapScreenStep4UiState, onNameChange: (String) -> Unit, modifier: Modifier) {
    Surface(modifier, color = Color(0xFFFEFEFF), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "保存地图",
                            fontSize = 20.sp,
                            color = Color(0xFF202937),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PanelDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        SaveMapRow(
                            label = "地图名称",
                            enabled = true,
                            value = uiState.mapName,
                            onValueChange = onNameChange
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SaveMapRow(
                            label = "预计面积(m²)",
                            enabled = false,
                            value = formatStep4Number(uiState.estimatedAreaM2),
                            onValueChange = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SaveMapRow(
                            label = "预计耗时(h)",
                            enabled = false,
                            value = formatStep4Number(uiState.estimatedDurationHours),
                            onValueChange = {}
                        )
                        /*Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.openLegacyPreview(mapId) },
                            enabled = uiState.canPreviewLegacyPlan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("测试：旧版规划图")
                        }*/
                    }
    }
}
