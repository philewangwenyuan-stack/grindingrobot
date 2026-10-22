package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import java.util.Locale
import com.sinelynx.grindingrobot.feature.common.component.GlobalTopBar
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveFooter
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveLifecycle
import com.sinelynx.grindingrobot.feature.common.component.DriveParameterSlider
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveTravelPad
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveSteeringPad
import com.sinelynx.grindingrobot.feature.common.control.TravelDirection
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapViewModel
import com.sinelynx.grindingrobot.core.designsystem.R as DesignR

@Composable
fun MapScreenStep1(
    viewModel: MapViewModel,
    mapId: String? = null,
    onCancelBuild: () -> Unit = {},
    onNextStep: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    ManualDriveLifecycle(viewModel::onControlScreenEnter, viewModel::onControlScreenExit)
    DisposableEffect(viewModel, mapId) {
        viewModel.requestLiveMapCacheClearOnNewMapEnter(mapId)
        viewModel.startMapSnapshotLoop(mapId)
        onDispose {
            viewModel.onControlScreenExit()
            viewModel.stopMapSnapshotLoop()
        }
    }
    MapScreenContent(
        uiState = uiState,
        onRunChange = viewModel::updateRunSpeed,
        onTurnChange = viewModel::updateTurnSpeed,
        onTravel = viewModel::onTravelClick,
        onSteering = viewModel::onSteeringChanged,
        onSteeringCancel = viewModel::onSteeringCancelled,
        onStop = viewModel::stopControl,
        onEmergencyStop = viewModel::emergencyStop,
        onRadarMapSync = viewModel::requestRadarMapSync,
        onCancelBuild = { viewModel.onCancelBuild(); onCancelBuild() },
        onNextStep = { if (viewModel.onNextStep()) onNextStep() },
        modifier = modifier,
    )
}

@Composable
internal fun MapScreenContent(
    uiState: MapUiState,
    onRunChange: (Float) -> Unit,
    onTurnChange: (Float) -> Unit,
    onTravel: (TravelDirection) -> Unit,
    onSteering: (Float, Int) -> Unit,
    onSteeringCancel: (Int) -> Unit,
    onStop: () -> Unit,
    onEmergencyStop: () -> Unit,
    onRadarMapSync: () -> Unit,
    onCancelBuild: () -> Unit,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFEBEEF2)).padding(12.dp)) {
        val sideWidth = (maxWidth * 0.15f).coerceIn(132.dp, 172.dp)
        val dialSize = (sideWidth - 16.dp).coerceAtMost(168.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopBar(onEmergencyStop)
            Row(Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(sideWidth).fillMaxHeight()
                    .background(Color(0xFFF6F8FB), RoundedCornerShape(18.dp)).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DriveParameterSlider("行走速度", uiState.drive.speedMps,
                        String.format(Locale.US, "%.2f m/s", uiState.drive.speedMps),
                        0.02f..0.15f, 0.01f, onRunChange, Modifier.fillMaxWidth(),
                        endpointLabels = "0.02 m/s" to "0.15 m/s", compact = true)
                    Spacer(Modifier.weight(1f))
                    ManualDriveTravelPad(uiState.drive, onTravel, Modifier.fillMaxWidth(), dialSize)
                    Spacer(Modifier.weight(1f))
                }
                MapPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    mapImageBytes = uiState.mapImageBytes, mapImageSize = uiState.mapImageSize,
                    mapGeo = uiState.mapGeo, mapLoadMessage = uiState.mapLoadMessage,
                    mapLoadFailed = uiState.mapLoadFailed, robotPose = uiState.robotPose,
                    robotWidth = uiState.robotWidth, robotLength = uiState.robotLength,
                    robotFootprint = uiState.robotFootprint, onRadarMapSync = onRadarMapSync,
                )
                Column(Modifier.width(sideWidth).fillMaxHeight()
                    .background(Color(0xFFF6F8FB), RoundedCornerShape(18.dp)).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DriveParameterSlider("转向力度", uiState.drive.turnPercent.toFloat(), "${uiState.drive.turnPercent}%",
                        1f..100f, 1f, onTurnChange, Modifier.fillMaxWidth(),
                        endpointLabels = "1%" to "100%", compact = true)
                    Spacer(Modifier.weight(1f))
                    ManualDriveSteeringPad(uiState.drive, onSteering, onSteeringCancel, Modifier.fillMaxWidth(), dialSize)
                    Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MapActionButton("取消建图", false, onCancelBuild)
                ManualDriveFooter(uiState.drive, onStop, Modifier.weight(1f))
                MapActionButton("下一步", true, onNextStep)
            }
        }
    }
}

@Composable
private fun TopBar(onEmergencyStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlobalTopBar(
            onEmergencyClick = onEmergencyStop,
            title = {
                Text("扫描建图", color = Color(0xFF202937), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            },
            // Content has 12dp padding; match the global header's 24dp / 14dp screen inset.
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("1  扫描地图", "2  选择工作区", "3  划分工作区", "4  保存地图").forEachIndexed { index, text ->
                Text(text, color = if (index == 0) Color(0xFF00A0E9) else Color(0xFF9DA3AF),
                    fontSize = 13.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MapActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (primary) Color(0xFF00A0E9) else Color(0xFFE0E6ED),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.width(160.dp).height(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (primary) Color.White else Color(0xFF606C7C),
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapPanel(
    mapImageBytes: ByteArray?,
    mapImageSize: Pair<Int, Int>?,
    mapGeo: com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo?,
    mapLoadMessage: String,
    mapLoadFailed: Boolean,
    robotPose: com.sinelynx.grindingrobot.core.model.state.DevicePosePayload?,
    robotWidth: Double?,
    robotLength: Double?,
    robotFootprint: List<com.sinelynx.grindingrobot.core.data.state.AppState.FootprintPoint>,
    onRadarMapSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapImageBitmap = remember(mapImageBytes) {
        mapImageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    // LIVE_MAP frames replace the bitmap frequently; viewport state belongs to the screen.
    var viewport by remember { mutableStateOf(MapViewportTransform()) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val currentBitmap by rememberUpdatedState(mapImageBitmap)

    fun changeViewport(centroid: Offset, pan: Offset, zoom: Float) {
        val bitmap = currentBitmap ?: return
        val canvasSize = Size(viewportSize.width.toFloat(), viewportSize.height.toFloat())
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
        viewport = viewport.zoomAndPan(centroid, pan, zoom, canvasSize,
            previewImageRect(canvasSize, bitmap.width, bitmap.height))
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
    ) {
        if (mapImageBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDEE5EE))
                    .onSizeChanged { viewportSize = it }
                    .testTag("mapping_viewport")
                    .semantics {
                        stateDescription = String.format(Locale.US, "地图缩放 %.1f 倍", viewport.scale)
                        progressBarRangeInfo = ProgressBarRangeInfo(viewport.scale,
                            MIN_MAP_VIEWPORT_SCALE..MAX_MAP_VIEWPORT_SCALE)
                        setProgress { scale ->
                            if (scale.isFinite()) {
                                changeViewport(Offset(viewportSize.width / 2f, viewportSize.height / 2f),
                                    Offset.Zero, scale.coerceIn(MIN_MAP_VIEWPORT_SCALE, MAX_MAP_VIEWPORT_SCALE) / viewport.scale)
                                true
                            } else false
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            changeViewport(centroid, pan, zoom)
                        }
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = mapImageBitmap,
                    contentDescription = "雷达图",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = viewport.scale
                        scaleY = viewport.scale
                        translationX = viewport.offset.x
                        translationY = viewport.offset.y
                    }
                )
                FittedMapRobotMarker(
                    pose = robotPose,
                    geo = mapGeo,
                    mapImageSize = mapImageSize,
                    bitmapSize = mapImageBitmap.width to mapImageBitmap.height,
                    viewportScale = viewport.scale,
                    viewportOffset = viewport.offset,
                    robotWidth = robotWidth,
                    robotLength = robotLength,
                    robotFootprint = robotFootprint
                )
            }
        } else {
            MapDataLoadingPage(text = mapLoadMessage, showProgress = !mapLoadFailed)
        }

        if (mapImageBitmap != null) {
            TextButton(
                onClick = { viewport = MapViewportTransform() },
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)).testTag("mapping_zoom_reset"),
            ) {
                Text(String.format(Locale.US, "%.1f× · 复位", viewport.scale),
                    color = Color(0xFF202937), fontSize = 14.sp)
            }
        }

        Surface(
            onClick = onRadarMapSync,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(64.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = DesignR.drawable.ic_refresh),
                    contentDescription = "同步雷达地图",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

