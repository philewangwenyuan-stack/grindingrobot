package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapHomeListItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapWorkspaceMetricsItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.RelocalizationSettingsLoadState
import com.sinelynx.grindingrobot.feature.map.viewmodel.StartGrindingSessionUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.Step4LegacyPreviewUiState

/**
 * 地图模块首页：仅展示地图列表与相关操作，不包含应用级顶栏与左侧模块切换。
 */
@Composable
fun MapHomeScreen(
    maps: List<MapHomeListItem>,
    metricsByMapId: Map<String, List<MapWorkspaceMetricsItem>>,
    previewByMapId: Map<String, MapPreviewUiState>,
    isPreviewLoading: Boolean,
    grindingSession: StartGrindingSessionUiState,
    grindingLegacyPreview: Step4LegacyPreviewUiState,
    isMapCatalogLoading: Boolean,
    isStartingMapping: Boolean,
    robotPose: DevicePosePayload?,
    existingTaskNames: List<String>,
    robotWidth: Double? = null,
    robotLength: Double? = null,
    isRelocalizationDialogVisible: Boolean,
    isRelocalizationSuccessful: Boolean,
    relocalizationRawStatus: String,
    relocalizationRunSpeed: Float,
    relocalizationTurnSpeed: Int,
    relocalizationTurnCount: Int,
    relocalizationSettingsLoadState: RelocalizationSettingsLoadState,
    relocalizationSettingsError: String,
    onEnter: () -> Unit,
    onAddMap: () -> Unit,
    onEditMap: (String, String) -> Unit,
    onDeleteMap: (String) -> Unit,
    onRequestMapMetrics: (String) -> Unit,
    onOpenStartGrinding: (String) -> Unit,
    onCloseStartGrinding: () -> Unit,
    onRetryStartGrindingMap: () -> Unit,
    onLeaveStartGrindingPreview: () -> Unit,
    onOpenStartGrindingLegacyPreview: () -> Unit,
    onCloseStartGrindingLegacyPreview: () -> Unit,
    onRequestTaskResultPreview: (String) -> Unit,
    onDismissMapPreview: () -> Unit,
    onRadarMapSync: () -> Unit,
    onRadarRelocalization: (Float, Float, Float) -> Unit,
    onStartRelocalization: (String) -> Unit,
    onDismissRelocalization: () -> Unit,
    onRetryRelocalizationSettings: () -> Unit,
    onRelocalizationRunSpeedDecrease: () -> Unit,
    onRelocalizationRunSpeedIncrease: () -> Unit,
    onRelocalizationTurnSpeedDecrease: () -> Unit,
    onRelocalizationTurnSpeedIncrease: () -> Unit,
    onRelocalizationTurnCountDecrease: () -> Unit,
    onRelocalizationTurnCountIncrease: () -> Unit,
    onRelocalizationTurnCountChange: (Float) -> Unit,
    onRelocalizationTurnCountChangeFinished: () -> Unit,
    onRelocalizationCommandStart: (DirectionCommand) -> Unit,
    onRelocalizationCommandEnd: (DirectionCommand) -> Unit,
    onRelocalizationPositionChanged: (JoystickPosition) -> Unit,
    onRequestPlanPreview: (String, String, String, Map<String, Int>, List<TaskObstacleRegionConfig>) -> Unit,
    onStartTaskConfig: (String, String, String, Map<String, Int>, List<TaskObstacleRegionConfig>) -> Boolean,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEnter()
    }
    var pendingDeleteMap by remember { mutableStateOf<MapHomeListItem?>(null) }
    var previewMap by remember { mutableStateOf<MapHomeListItem?>(null) }
    var startTaskMap by remember { mutableStateOf<MapHomeListItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        MapHeader(
            onAddMap = onAddMap,
            isStartingMapping = isStartingMapping
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isMapCatalogLoading) {
            MapCatalogLoadingState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (maps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyMapState()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                maps.forEach { map ->
                    MapItemRow(
                        map = map,
                        onDeleteClick = { pendingDeleteMap = map },
                        onThumbnailClick = {
                            onRequestTaskResultPreview(map.mapId)
                            previewMap = map
                        },
                        onEditClick = { onEditMap(map.mapId, map.mapName) },
                        onStartTaskClick = {
                            onRequestMapMetrics(map.mapId)
                            onOpenStartGrinding(map.mapId)
                            startTaskMap = map
                        }
                    )
                }
            }
        }
    }

    pendingDeleteMap?.let { target ->
        DeleteMapConfirmDialog(
            mapName = target.mapName.ifBlank { "暂无名称" },
            onDismiss = { pendingDeleteMap = null },
            onConfirm = {
                onDeleteMap(target.mapId)
                pendingDeleteMap = null
            }
        )
    }

    previewMap?.let { target ->
        MapPreviewDialog(
            mapName = target.mapName.ifBlank { "暂无名称" },
            mapPreview = previewByMapId[target.mapId],
            isLoading = isPreviewLoading,
            onDismiss = {
                previewMap = null
                onDismissMapPreview()
            }
        )
    }

    startTaskMap?.let { target ->
        DisposableEffect(target.mapId) { onDispose(onCloseStartGrinding) }
        val session = grindingSession.takeIf { it.mapId == target.mapId } ?: StartGrindingSessionUiState()
        StartGrindingScreen(
            workspaces = buildStartGrindingWorkspaces(
                mapPreview = session.mapPreview,
                metrics = metricsByMapId[target.mapId].orEmpty()
            ),
            session = session,
            legacyPreview = grindingLegacyPreview,
            onOpenLegacyPreview = onOpenStartGrindingLegacyPreview,
            onCloseLegacyPreview = onCloseStartGrindingLegacyPreview,
            onRetryMap = onRetryStartGrindingMap,
            onLeavePreview = onLeaveStartGrindingPreview,
            robotPose = robotPose,
            mapPreview = session.mapPreview,
            existingTaskNames = existingTaskNames,
            robotWidth = robotWidth,
            robotLength = robotLength,
            isRelocalizationDialogVisible = isRelocalizationDialogVisible,
            isRelocalizationSuccessful = isRelocalizationSuccessful,
            relocalizationRawStatus = relocalizationRawStatus,
            relocalizationRunSpeed = relocalizationRunSpeed,
            relocalizationTurnSpeed = relocalizationTurnSpeed,
            relocalizationTurnCount = relocalizationTurnCount,
            relocalizationSettingsLoadState = relocalizationSettingsLoadState,
            relocalizationSettingsError = relocalizationSettingsError,
            onRadarMapSync = onRadarMapSync,
            onRadarRelocalization = onRadarRelocalization,
            onStartRelocalization = { onStartRelocalization(target.mapId) },
            onDismissRelocalization = onDismissRelocalization,
            onRetryRelocalizationSettings = onRetryRelocalizationSettings,
            onRelocalizationRunSpeedDecrease = onRelocalizationRunSpeedDecrease,
            onRelocalizationRunSpeedIncrease = onRelocalizationRunSpeedIncrease,
            onRelocalizationTurnSpeedDecrease = onRelocalizationTurnSpeedDecrease,
            onRelocalizationTurnSpeedIncrease = onRelocalizationTurnSpeedIncrease,
            onRelocalizationTurnCountDecrease = onRelocalizationTurnCountDecrease,
            onRelocalizationTurnCountIncrease = onRelocalizationTurnCountIncrease,
            onRelocalizationTurnCountChange = onRelocalizationTurnCountChange,
            onRelocalizationTurnCountChangeFinished = onRelocalizationTurnCountChangeFinished,
            onRelocalizationCommandStart = onRelocalizationCommandStart,
            onRelocalizationCommandEnd = onRelocalizationCommandEnd,
            onRelocalizationPositionChanged = onRelocalizationPositionChanged,
            onDismiss = { startTaskMap = null },
            onRequestPlanPreview = { taskId, taskName, regionRepeats, obstacles ->
                onRequestPlanPreview(target.mapId, taskId, taskName, regionRepeats, obstacles)
            },
            onStart = { taskId, taskName, regionRepeats, obstacles ->
                if (onStartTaskConfig(target.mapId, taskId, taskName, regionRepeats, obstacles)) {
                    startTaskMap = null
                }
            }
        )
    }
}

private fun buildStartGrindingWorkspaces(
    mapPreview: MapPreviewUiState?,
    metrics: List<MapWorkspaceMetricsItem>
): List<MapWorkspaceMetricsItem> {
    val metricsByRegionId = metrics.associateBy { it.regionId }
    return mapPreview?.workRegions.orEmpty().map { region ->
        val matchedMetrics = metricsByRegionId[region.regionId]
        MapWorkspaceMetricsItem(
            regionId = region.regionId,
            regionName = region.regionName.ifBlank { matchedMetrics?.regionName.orEmpty() },
            repeat = matchedMetrics?.repeat?.coerceAtLeast(1) ?: 1,
            areaM2 = matchedMetrics?.areaM2 ?: 0f,
            estimatedTimeH = matchedMetrics?.estimatedTimeH ?: 0f
        )
    }
}

@Composable
private fun MapCatalogLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = Color(0xFF00A0E9),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载中...",
            color = Color(0xFF6D737D),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MapHeader(
    onAddMap: () -> Unit,
    isStartingMapping: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isStartingMapping) "正在启动建图" else "地图列表",
            fontSize = 20.sp,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isStartingMapping) Color(0xFF8BBED4) else Color(0xFF00A0E9))
                .clickable(
                    enabled = !isStartingMapping,
                    onClick = onAddMap
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isStartingMapping) "…" else "+",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyMapState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_workspace_empty),
            contentDescription = "暂无地图数据",
            colorFilter = ColorFilter.tint(Color(0xFFAFB8C4)),
            modifier = Modifier.size(90.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "暂无数据",
            fontSize = 16.sp,
            color = Color(0xFF9DA3AF)
        )
    }
}

private fun decodeBase64ThumbnailToImageBitmap(base64: String): ImageBitmap? {
    val trimmed = base64.trim()
    if (trimmed.isEmpty()) return null
    val payload = trimmed.substringAfter(",", trimmed)
    return runCatching {
        val bytes = Base64.decode(payload.trim(), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun MapItemRow(
    map: MapHomeListItem,
    onDeleteClick: () -> Unit,
    onThumbnailClick: () -> Unit,
    onEditClick: () -> Unit,
    onStartTaskClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F8FB))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnailBitmap = remember(map.base64Image) {
            decodeBase64ThumbnailToImageBitmap(map.base64Image)
        }
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFEDEDF0))
                .clickable(onClick = onThumbnailClick),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = "地图缩略图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_workspace_empty),
                    contentDescription = "地图缩略图",
                    colorFilter = ColorFilter.tint(Color(0xFFE2E7EE)),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = map.mapName.ifBlank { "暂无名称" },
                fontSize = 18.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoMetric(
                    iconRes = R.drawable.ic_home_map_time,
                    text = formatMapCreateDateYmd(map.createTime)
                )
                InfoMetric(
                    iconRes = R.drawable.ic_home_map_area,
                    text = map.mapArea?.let { "${"%.0f".format(it)}m²" } ?: "-"
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionIcon(
                iconRes = R.drawable.ic_workspace_del,
                tint = Color(0xFF9DA3AF),
                onClick = onDeleteClick
            )
            ActionIcon(
                iconRes = R.drawable.ic_workspace_edit,
                tint = Color(0xFF9DA3AF),
                onClick = onEditClick
            )
            ActionIcon(
                iconRes = R.drawable.ic_map_preview,
                tint = Color.Unspecified,
                onClick = onStartTaskClick
            )
        }
    }
}

@Composable
private fun InfoMetric(
    iconRes: Int?,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color(0xFF9DA3AF),
                modifier = Modifier.size(12.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9DA3AF))
            )
        }
        Text(
            text = text,
            color = Color(0xFF9DA3AF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionIcon(
    iconRes: Int,
    tint: Color,
    onClick: (() -> Unit)? = null
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(36.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    )
}

@Composable
private fun DeleteMapConfirmDialog(
    mapName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .size(width = 480.dp, height = 400.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(Color.White)
                .padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFFFFE2E2)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_warning),
                    contentDescription = "警告",
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "确定删除地图？",
                color = Color(0xFF202937),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = buildAnnotatedString {
                    append("确定要永久删除地图\"")
                    withStyle(SpanStyle(color = Color(0xFF00A0E9))) {
                        append(mapName)
                    }
                    append("\"吗?此操作无法撤销。")
                },
                textAlign = TextAlign.Center,
                color = Color(0xFF9DA3AF),
                fontSize = 20.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DialogActionButton(
                    text = "取 消",
                    background = Color(0xFFE5E7EB),
                    textColor = Color(0x80202937),
                    onClick = onDismiss
                )
                DialogActionButton(
                    text = "确 定",
                    background = Color(0xFFD82B2A),
                    textColor = Color.White,
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun MapPreviewDialog(
    mapName: String,
    mapPreview: MapPreviewUiState?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val previewBitmap = remember(mapPreview?.imageBytes) {
        mapPreview?.imageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(720.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(Color.White)
                    .padding(horizontal = 32.dp, vertical = 28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = mapName,
                        color = Color(0xFF202937),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_map_preview_close),
                        contentDescription = "关闭预览",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(onClick = onDismiss)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = Color(0xFF00A0E9),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "预览加载中...",
                                color = Color(0xFF6D737D),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "地图预览图",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_workspace_empty),
                            contentDescription = "地图预览图",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }
                    Image(
                        painter = painterResource(id = CommonR.drawable.ic_repeat_diagram),
                        contentDescription = "重复次数示例图",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 64.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/** 源串形如 `2020-01-01 00:00:00` 或 ISO，展示仅年月日。 */
private fun formatMapCreateDateYmd(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return "-"
    val datePart = s.substringBefore(' ').substringBefore('T')
    return datePart.ifBlank { "-" }
}


