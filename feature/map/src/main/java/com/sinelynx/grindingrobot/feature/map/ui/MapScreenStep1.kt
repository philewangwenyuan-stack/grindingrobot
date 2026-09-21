package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapViewModel
import com.sinelynx.grindingrobot.core.designsystem.R as DesignR
import java.util.Locale

@Composable
fun MapScreenStep1(
    viewModel: MapViewModel,
    mapId: String? = null,
    onCancelBuild: () -> Unit = {},
    onNextStep: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel, mapId) {
        viewModel.requestLiveMapCacheClearOnNewMapEnter(mapId)
        viewModel.startMapSnapshotLoop(mapId)
        onDispose {
            viewModel.stopMapSnapshotLoop()
        }
    }
    MapScreenContent(
        modifier = modifier,
        uiState = uiState,
        onRunMinus = viewModel::decreaseRunSpeed,
        onRunPlus = viewModel::increaseRunSpeed,
        onTurnMinus = viewModel::decreaseTurnSpeed,
        onTurnPlus = viewModel::increaseTurnSpeed,
        onCommandStart = viewModel::onCommandStart,
        onCommandEnd = viewModel::onCommandEnd,
        onPositionChanged = viewModel::onPositionChanged,
        onRadarMapSync = viewModel::requestRadarMapSync,
        onCancelBuild = {
            viewModel.onCancelBuild()
            onCancelBuild()
        },
        onNextStep = {
            // 冻结完整可解码帧成功后才导航，避免后续步骤以空图或迟到帧作为底图。
            if (viewModel.onNextStep()) onNextStep()
        }
    )
}

@Composable
private fun MapScreenContent(
    uiState: MapUiState,
    onRunMinus: () -> Unit,
    onRunPlus: () -> Unit,
    onTurnMinus: () -> Unit,
    onTurnPlus: () -> Unit,
    onCommandStart: (DirectionCommand) -> Unit,
    onCommandEnd: (DirectionCommand) -> Unit,
    onPositionChanged: (JoystickPosition) -> Unit,
    onRadarMapSync: () -> Unit,
    onCancelBuild: () -> Unit,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TopBar()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MapPanel(
                    modifier = Modifier.weight(1f),
                    mapImageBytes = uiState.mapImageBytes,
                    mapImageSize = uiState.mapImageSize,
                    mapGeo = uiState.mapGeo,
                    robotPose = uiState.robotPose,
                    robotWidth = uiState.robotWidth,
                    robotLength = uiState.robotLength,
                    onRadarMapSync = onRadarMapSync
                )
                ControlPanel(
                    modifier = Modifier.width(320.dp),
                    runSpeed = uiState.runSpeed,
                    turnSpeed = uiState.turnSpeed,
                    onRunMinus = onRunMinus,
                    onRunPlus = onRunPlus,
                    onTurnMinus = onTurnMinus,
                    onTurnPlus = onTurnPlus,
                    currentCommand = uiState.currentCommand,
                    lastPosition = uiState.lastPosition,
                    onCommandStart = onCommandStart,
                    onCommandEnd = onCommandEnd,
                    onPositionChanged = onPositionChanged
                )
            }

            BottomActions(
                onCancelBuild = onCancelBuild,
                onNextStep = onNextStep
            )
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(1000.dp))
                .background(Color(0xFFE5EAF1))
                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(1000.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepBadge()
            StepText(text = "1.扫描地图", active = true)
            StepText(text = ">", active = false)
            StepText(text = "2.选择工作区", active = false)
            StepText(text = ">", active = false)
            StepText(text = "3.划分工作区", active = false)
            StepText(text = ">", active = false)
            StepText(text = "4.保存地图", active = false)
        }

        DeviceStatusBar()
    }
}

@Composable
private fun StepBadge() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Color(0xFF00A0E9), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "⚙", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepText(text: String, active: Boolean) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color(0xFF00A0E9) else Color(0xFF9DA3AF)
    )
}

@Composable
private fun MapPanel(
    mapImageBytes: ByteArray?,
    mapImageSize: Pair<Int, Int>?,
    mapGeo: com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo?,
    robotPose: com.sinelynx.grindingrobot.core.model.state.DevicePosePayload?,
    robotWidth: Double?,
    robotLength: Double?,
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
            ) {
                androidx.compose.foundation.Image(
                    bitmap = mapImageBitmap,
                    contentDescription = "雷达图",
                    modifier = Modifier.fillMaxSize()
                )
                FittedMapRobotMarker(
                    pose = robotPose,
                    geo = mapGeo,
                    mapImageSize = mapImageSize,
                    bitmapSize = mapImageBitmap.width to mapImageBitmap.height,
                    robotWidth = robotWidth,
                    robotLength = robotLength
                )
            }
        } else {
            MapDataLoadingPage()
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

@Composable
private fun RobotMarker(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF191C22), CircleShape)
        )
        Box(
            modifier = Modifier
                .padding(top = (-8).dp)
                .size(width = 20.dp, height = 36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFDF3736))
                .border(2.dp, Color(0xFF18181B), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(width = 12.dp, height = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF6F8FB))
            )
        }
    }
}

@Composable
private fun ControlPanel(
    modifier: Modifier,
    runSpeed: Float,
    turnSpeed: Int,
    onRunMinus: () -> Unit,
    onRunPlus: () -> Unit,
    onTurnMinus: () -> Unit,
    onTurnPlus: () -> Unit,
    currentCommand: DirectionCommand,
    lastPosition: JoystickPosition,
    onCommandStart: (DirectionCommand) -> Unit,
    onCommandEnd: (DirectionCommand) -> Unit,
    onPositionChanged: (JoystickPosition) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF6F8FB),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SpeedAdjustRow(
                label = "直行速度(m/s)",
                valueText = formatSpeedValue(runSpeed),
                canDecrease = runSpeed > 0f,
                canIncrease = runSpeed < 0.15f,
                onMinus = onRunMinus,
                onPlus = onRunPlus
            )
            SpeedAdjustRow(
                label = "转弯速度(%)",
                valueText = turnSpeed.toString(),
                canDecrease = turnSpeed > 1,
                canIncrease = turnSpeed < 100,
                onMinus = onTurnMinus,
                onPlus = onTurnPlus
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VirtualDirectionPad(
                    size = 200.dp,
                    onCommandStart = onCommandStart,
                    onCommandEnd = onCommandEnd,
                    onPositionChanged = onPositionChanged
                )
            }

            Text(
                text = "方向: ${currentCommand.name}  偏移:${"%.0f".format(lastPosition.distanceRatio * 100)}%",
                fontSize = 12.sp,
                color = Color(0xFF6D737D),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SpeedAdjustRow(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6D737D)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircleTextButton(text = "-", enabled = canDecrease, onClick = onMinus)
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = valueText, fontSize = 24.sp, color = Color(0xFF202937))
            }
            CircleTextButton(text = "+", enabled = canIncrease, onClick = onPlus)
        }
    }
}

private fun formatSpeedValue(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
        .trimEnd('0')
        .trimEnd('.')
}

@Composable
private fun CircleTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = if (enabled) Color(0xFF202937) else Color(0xFFD6D9DE),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun BottomActions(
    onCancelBuild: () -> Unit,
    onNextStep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            text = "✕  取消建图",
            background = Color(0xFFD82B2A),
            onClick = onCancelBuild
        )
        ActionButton(
            text = "▶  下一步",
            background = Color(0xFF00A0E9),
            onClick = onNextStep
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(
                shadow = Shadow(
                    color = Color(0x1A000000),
                    blurRadius = 4f
                )
            )
        )
    }
}


