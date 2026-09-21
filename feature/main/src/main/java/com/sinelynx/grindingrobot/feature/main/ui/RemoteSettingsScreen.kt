package com.sinelynx.grindingrobot.feature.main.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.feature.common.component.CameraRtspOverlay
import com.sinelynx.grindingrobot.feature.main.viewmodel.RemoteSettingsUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.RemoteSettingsViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.SpinDirection
import com.sinelynx.grindingrobot.feature.main.ui.component.ConfirmOverlayDialog
import com.sinelynx.grindingrobot.feature.map.R as MapR
import com.sinelynx.grindingrobot.feature.map.ui.DirectionCommand
import com.sinelynx.grindingrobot.feature.map.ui.FittedMapRobotMarker
import com.sinelynx.grindingrobot.feature.map.ui.JoystickPosition
import com.sinelynx.grindingrobot.feature.map.ui.MapDataLoadingPage
import com.sinelynx.grindingrobot.feature.map.ui.VirtualDirectionPad
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 遥控设置：布局与交互参照com.sinelynx.grindingrobot.feature.map.ui.MapScreen]（左侧实时画面、右侧速度与虚拟摇杆）。
 */
@Composable
fun RemoteSettingsScreen(
    viewModel: RemoteSettingsViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.onScreenEnter()
    }
    val uiState by viewModel.uiState.collectAsState()
    RemoteSettingsContent(
        modifier = modifier,
        uiState = uiState,
        onRunMinus = viewModel::decreaseRunSpeed,
        onRunPlus = viewModel::increaseRunSpeed,
        onRunSpeedChangeFinished = viewModel::commitRunSpeed,
        onTurnMinus = viewModel::decreaseTurnSpeed,
        onTurnPlus = viewModel::increaseTurnSpeed,
        onTurnCountMinus = viewModel::decreaseTurnCount,
        onTurnCountPlus = viewModel::increaseTurnCount,
        onTurnCountChange = viewModel::updateTurnCountDraft,
        onTurnCountChangeFinished = viewModel::commitTurnCount,
        onSelectSpinDirection = viewModel::selectSpinDirection,
        onCommandStart = viewModel::onCommandStart,
        onCommandEnd = viewModel::onCommandEnd,
        onPositionChanged = viewModel::onPositionChanged,
        onCameraClick = viewModel::onCameraClick,
        onConfirmAutoModeJoystickDialog = viewModel::confirmAutoModeJoystickDialog,
        onCancelAutoModeJoystickDialog = viewModel::cancelAutoModeJoystickDialog
    )
}

@Composable
private fun RemoteSettingsContent(
    uiState: RemoteSettingsUiState,
    onRunMinus: () -> Unit,
    onRunPlus: () -> Unit,
    onRunSpeedChangeFinished: () -> Unit,
    onTurnMinus: () -> Unit,
    onTurnPlus: () -> Unit,
    onTurnCountMinus: () -> Unit,
    onTurnCountPlus: () -> Unit,
    onTurnCountChange: (Float) -> Unit,
    onTurnCountChangeFinished: () -> Unit,
    onSelectSpinDirection: (SpinDirection) -> Unit,
    onCommandStart: (DirectionCommand) -> Unit,
    onCommandEnd: (DirectionCommand) -> Unit,
    onPositionChanged: (JoystickPosition) -> Unit,
    onCameraClick: () -> Unit,
    onConfirmAutoModeJoystickDialog: () -> Unit,
    onCancelAutoModeJoystickDialog: () -> Unit,
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
            .padding(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RemotePreviewPanel(
                    modifier = Modifier.weight(1f),
                    imageBytes = uiState.mapImageBytes,
                    mapGeo = uiState.mapGeo,
                    mapImageSize = uiState.mapImageSize,
                    robotPose = uiState.robotPose,
                    cameraButtonActive = uiState.showCameraOverlay,
                    onCameraClick = onCameraClick,
                    robotWidth = uiState.robotWidth,
                    robotLength = uiState.robotLength
                )
                RemoteControlPanel(
                    modifier = Modifier.width(320.dp),
                    runSpeed = uiState.runSpeed,
                    turnSpeed = uiState.turnSpeed,
                    turnCount = uiState.turnCount,
                    spinDirection = uiState.spinDirection,
                    onRunMinus = onRunMinus,
                    onRunPlus = onRunPlus,
                    onRunSpeedChangeFinished = onRunSpeedChangeFinished,
                    onTurnMinus = onTurnMinus,
                    onTurnPlus = onTurnPlus,
                    onTurnCountMinus = onTurnCountMinus,
                    onTurnCountPlus = onTurnCountPlus,
                    onTurnCountChange = onTurnCountChange,
                    onTurnCountChangeFinished = onTurnCountChangeFinished,
                    onSelectSpinDirection = onSelectSpinDirection,
                    currentCommand = uiState.currentCommand,
                    lastPosition = uiState.lastPosition,
                    onCommandStart = onCommandStart,
                    onCommandEnd = onCommandEnd,
                    onPositionChanged = onPositionChanged
                )
            }
        }

        if (uiState.showAutoModeJoystickDialog) {
            ConfirmOverlayDialog(
                title = "确定退出自动驾驶?",
                message = "机器人将停止自动研磨，切换为手动操作。",
                confirmText = "确 定",
                cancelText = "取 消",
                onConfirm = onConfirmAutoModeJoystickDialog,
                onCancel = onCancelAutoModeJoystickDialog
            )
        }

        if (uiState.showCameraOverlay) {
            CameraRtspOverlay(
                streamUrl = uiState.videoStreamUrl,
                initialTopPadding = 16.dp,
                initialEndPadding = 14.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RemotePreviewPanel(
    imageBytes: ByteArray?,
    mapGeo: MapGeo?,
    mapImageSize: Pair<Int, Int>?,
    robotPose: DevicePosePayload?,
    cameraButtonActive: Boolean,
    onCameraClick: () -> Unit,
    robotWidth: Double?,
    robotLength: Double?,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { bytes ->
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
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDEE5EE))
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "机器人实时画面",
                    modifier = Modifier.fillMaxSize()
                )
                FittedMapRobotMarker(
                    pose = robotPose,
                    geo = mapGeo,
                    mapImageSize = mapImageSize,
                    bitmapSize = bitmap.width to bitmap.height,
                    robotWidth = robotWidth,
                    robotLength = robotLength
                )
            }
        } else {
            MapDataLoadingPage()
        }

        CameraButton(
            active = cameraButtonActive,
            onClick = onCameraClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }
}

@Composable
private fun RemoteControlPanel(
    modifier: Modifier,
    runSpeed: Float,
    turnSpeed: Int,
    turnCount: Int,
    spinDirection: SpinDirection,
    onRunMinus: () -> Unit,
    onRunPlus: () -> Unit,
    onRunSpeedChangeFinished: () -> Unit,
    onTurnMinus: () -> Unit,
    onTurnPlus: () -> Unit,
    onTurnCountMinus: () -> Unit,
    onTurnCountPlus: () -> Unit,
    onTurnCountChange: (Float) -> Unit,
    onTurnCountChangeFinished: () -> Unit,
    onSelectSpinDirection: (SpinDirection) -> Unit,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SpeedAdjustRow(
                    label = "直行速度(m/s)",
                    value = formatSpeedValue(runSpeed),
                    canDecrease = runSpeed > 0f,
                    canIncrease = runSpeed < 0.15f,
                    onMinus = onRunMinus,
                    onPlus = onRunPlus,
                    onValueChangeFinished = onRunSpeedChangeFinished
                )
//                SpeedAdjustRow(
//                    label = "转弯速度(%)",
//                    value = turnSpeed,
//                    canDecrease = turnSpeed > 1,
//                    canIncrease = turnSpeed < 100,
//                    onMinus = onTurnMinus,
//                    onPlus = onTurnPlus
//                )
                TurnCountSliderRow(
                    label = "转数",
                    value = turnCount,
                    canDecrease = turnCount > 0,
                    canIncrease = turnCount < 1500,
                    onMinus = onTurnCountMinus,
                    onPlus = onTurnCountPlus,
                    onValueChange = onTurnCountChange,
                    onValueChangeFinished = onTurnCountChangeFinished
                )
//                DirectionToggleRow(
//                    direction = spinDirection,
//                    onSelect = onSelectSpinDirection
//                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                VirtualDirectionPad(
                    size = 200.dp,
                    onCommandStart = onCommandStart,
                    onCommandEnd = onCommandEnd,
                    onPositionChanged = onPositionChanged
                )
            }

//            Text(
//                text = "方向: ${currentCommand.name}  偏移:${"%.0f".format(lastPosition.distanceRatio * 100)}%",
//                fontSize = 12.sp,
//                color = Color(0xFF6D737D),
//                modifier = Modifier.align(Alignment.CenterHorizontally)
//            )
        }
    }
}

@Composable
private fun DirectionToggleRow(
    direction: SpinDirection,
    onSelect: (SpinDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "方向",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6D737D)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F6))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DirectionToggleButton(
                text = "正转",
                selected = direction == SpinDirection.Forward,
                onClick = { onSelect(SpinDirection.Forward) }
            )
            DirectionToggleButton(
                text = "反转",
                selected = direction == SpinDirection.Reverse,
                onClick = { onSelect(SpinDirection.Reverse) }
            )
        }
    }
}

@Composable
private fun DirectionToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFF202937) else Color(0xFF9DA3AF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CameraButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 8.dp, spotColor = Color(0x1A000000), ambientColor = Color(0x1A000000))
            .size(48.dp)
            .clip(shape)
            .background(if (active) Color(0xFF00A0E9) else Color.White)
            .border(
                width = if (active) 0.dp else 1.dp,
                color = if (active) Color.Transparent else Color(0xFFF0F2F5),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = MapR.drawable.ic_robot_camera),
            contentDescription = "视频",
            tint = if (active) Color.White else Color(0xFF202937),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SpeedAdjustRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueChangeFinished: () -> Unit
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
            CircleTextButton(
                text = "-",
                enabled = canDecrease,
                repeatOnLongPress = true,
                onClick = onMinus,
                onInteractionFinished = onValueChangeFinished
            )
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = value, fontSize = 19.sp, color = Color(0xFF202937))
            }
            CircleTextButton(
                text = "+",
                enabled = canIncrease,
                repeatOnLongPress = true,
                onClick = onPlus,
                onInteractionFinished = onValueChangeFinished
            )
        }
    }
}

@Composable
private fun TurnCountSliderRow(
    label: String,
    value: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Text(text = value.toString(), fontSize = 19.sp, color = Color(0xFF202937))
                }
                CircleTextButton(text = "+", enabled = canIncrease, onClick = onPlus)
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            valueRange = 0f..1500f,
            steps = 139,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00A0E9),
                activeTrackColor = Color(0xFF00A0E9),
                inactiveTrackColor = Color(0xFFEFF1F3),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
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
    repeatOnLongPress: Boolean = false,
    onClick: () -> Unit,
    onInteractionFinished: () -> Unit = {}
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnInteractionFinished by rememberUpdatedState(onInteractionFinished)
    val interactionModifier = if (repeatOnLongPress) {
        Modifier
            .semantics {
                role = Role.Button
                if (enabled) {
                    semanticsOnClick {
                        currentOnClick()
                        currentOnInteractionFinished()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    while (true) {
                        awaitPointerEventScope {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        var valueChanged = false
                        val repeatJob = launch {
                            delay(LONG_PRESS_DELAY_MS)
                            while (isActive) {
                                valueChanged = true
                                currentOnClick()
                                delay(LONG_PRESS_REPEAT_INTERVAL_MS)
                            }
                        }
                        try {
                            val up = awaitPointerEventScope {
                                waitForUpOrCancellation()
                            }
                            repeatJob.cancelAndJoin()
                            if (up != null && !valueChanged) {
                                valueChanged = true
                                currentOnClick()
                            }
                        } finally {
                            repeatJob.cancel()
                            if (valueChanged) {
                                currentOnInteractionFinished()
                            }
                        }
                    }
                }
            }
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(interactionModifier)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = if (enabled) Color(0xFF202937) else Color(0xFFD6D9DE),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private const val LONG_PRESS_DELAY_MS = 500L
private const val LONG_PRESS_REPEAT_INTERVAL_MS = 100L

