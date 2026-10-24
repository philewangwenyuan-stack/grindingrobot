package com.sinelynx.grindingrobot.feature.common.component

import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinelynx.grindingrobot.feature.common.R
import com.sinelynx.grindingrobot.core.designsystem.R as DesignR
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveState
import com.sinelynx.grindingrobot.feature.common.control.TravelDirection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DriveBlue = Color(0xFF00A0E9)
private val DriveInk = Color(0xFF202937)
private val DriveMuted = Color(0xFF6D737D)

/** Stop both on lifecycle exit and transient window focus loss; restore availability, never motion. */
@Composable
fun ManualDriveLifecycle(onResume: () -> Unit, onStop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val resume by rememberUpdatedState(onResume)
    val stop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) stop()
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        }
        lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            lifecycle.removeObserver(observer)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            stop()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DriveParameterSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    endpointLabels: Pair<String, String> = "${range.start}" to "${range.endInclusive}",
    compact: Boolean = false,
) {
    val feedback = manualFeedback()
    val finish = { onValueChangeFinished(); feedback(ManualFeedback.Tick) }
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        if (compact) {
            Text(label, Modifier.align(Alignment.CenterHorizontally), color = DriveInk,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, Modifier.align(Alignment.CenterHorizontally), color = DriveInk,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DriveInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(valueText, color = DriveInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!compact) SliderStepButton(false, value > range.start) {
                onValueChange((value - step).coerceIn(range)); finish()
            }
            Slider(value = value.coerceIn(range), onValueChange = onValueChange,
                valueRange = range, onValueChangeFinished = finish,
                modifier = Modifier.weight(1f).padding(horizontal = if (compact) 0.dp else 6.dp).testTag("slider_$label"),
                thumb = {
                    Box(Modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFD9E4EE), CircleShape))
                },
                colors = SliderDefaults.colors(thumbColor = DriveBlue, activeTrackColor = DriveBlue,
                    inactiveTrackColor = Color(0xFFD6E2EB)))
            if (!compact) SliderStepButton(true, value < range.endInclusive) {
                onValueChange((value + step).coerceIn(range)); finish()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 0.dp else 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(endpointLabels.first, color = DriveMuted, fontSize = 11.sp)
            Text(endpointLabels.second, color = DriveMuted, fontSize = 11.sp)
        }
        if (compact) Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            SliderStepButton(false, value > range.start) {
                onValueChange((value - step).coerceIn(range)); finish()
            }
            SliderStepButton(true, value < range.endInclusive) {
                onValueChange((value + step).coerceIn(range)); finish()
            }
        }
    }
}

@Composable
private fun SliderStepButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp),
        color = Color(0xFFF2F6FA), shape = RoundedCornerShape(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(if (plus) R.drawable.ic_change_plus else R.drawable.ic_change_minus),
                contentDescription = if (plus) "增加" else "减少",
                tint = Color.Unspecified, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun ManualDriveSettings(
    state: ManualDriveState,
    onSpeedChange: (Float) -> Unit,
    onTurnChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    discRpm: Int? = null,
    onDiscChange: (Float) -> Unit = {},
    onDiscFinished: () -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DriveParameterSlider("行走速度", state.speedMps, String.format(Locale.US, "%.2f m/s", state.speedMps),
            0.02f..0.15f, 0.01f, onSpeedChange, Modifier.weight(1f),
            endpointLabels = "0.02 m/s" to "0.15 m/s")
        DriveParameterSlider("转向力度", state.turnPercent.toFloat(), "${state.turnPercent}%",
            1f..100f, 1f, onTurnChange, Modifier.weight(1f), endpointLabels = "1%" to "100%")
        if (discRpm != null) DriveParameterSlider("磨盘转数", discRpm.toFloat(), "$discRpm rpm",
            0f..1500f, 10f, onDiscChange, Modifier.weight(1f), onDiscFinished,
            "0" to "1500 rpm")
    }
}

@Composable
fun ManualRemoteControlContent(
    state: ManualDriveState,
    discRpm: Int,
    onTravel: (TravelDirection) -> Unit,
    onSteering: (Float, Int) -> Unit,
    onSteeringCancel: (Int) -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTurnChange: (Float) -> Unit,
    onDiscChange: (Float) -> Unit,
    onDiscFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Color(0xFFF6F8FB), RoundedCornerShape(18.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ManualDriveSettings(state, onSpeedChange, onTurnChange, discRpm = discRpm,
            onDiscChange = onDiscChange, onDiscFinished = onDiscFinished)
        HorizontalDivider(color = Color(0xFFDCE5ED))
        ManualDriveDeck(state, onTravel, onSteering, onSteeringCancel, onStop,
            Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun ManualDriveDeck(
    state: ManualDriveState,
    onTravel: (TravelDirection) -> Unit,
    onSteering: (Float, Int) -> Unit,
    onSteeringCancel: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = manualFeedback()
    BoxWithConstraints(modifier.background(Color(0xFFF6F8FB), RoundedCornerShape(18.dp))) {
        val compact = maxHeight < 270.dp
        val dialSize = (maxHeight - if (compact) 40.dp else 90.dp).coerceIn(120.dp, 300.dp)
            .coerceAtMost(maxWidth * 0.30f)
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ManualDriveTravelPad(state, onTravel, Modifier.weight(1f), dialSize, compact)
            Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 18.dp)) {
                val directionText = when (state.direction) {
                    TravelDirection.FORWARD -> "前进中"
                    TravelDirection.BACKWARD -> "后退中"
                    TravelDirection.STOPPED -> if (state.steering != 0f) "原地转向" else "已停止"
                }
                Text(if (state.enabled) directionText else "控制未就绪", color = if (state.enabled) DriveBlue else DriveMuted,
                    fontSize = if (compact) 21.sp else 34.sp, fontWeight = FontWeight.Bold)
                if (state.direction != TravelDirection.STOPPED && state.steering != 0f) {
                    Text("转向减速 ${abs(state.steering * 100).roundToInt()}%", color = DriveBlue,
                        fontSize = if (compact) 14.sp else 20.sp, fontWeight = FontWeight.Medium)
                }
                Text(String.format(Locale.US, "行走指令  %.3f m/s", state.commandedSpeedMps),
                    color = DriveMuted, fontSize = if (compact) 13.sp else 18.sp)
                Surface(onClick = { onStop(); if (state.enabled) feedback(ManualFeedback.Stop) }, color = Color(0xFFD82B2A), shape = RoundedCornerShape(50),
                    modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth()
                        .height(if (compact) 54.dp else 80.dp).testTag("manual_stop")) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_task_stop), null, tint = Color.White,
                            modifier = Modifier.size(if (compact) 22.dp else 28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("停止行走", color = Color.White, fontSize = if (compact) 20.sp else 27.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                if (!compact) Text("停止后需重新点击前进或后退", color = DriveMuted, fontSize = 12.sp,
                    textAlign = TextAlign.Center)
            }
            ManualDriveSteeringPad(state, onSteering, onSteeringCancel, Modifier.weight(1f), dialSize, compact)
        }
    }
}

/** Reusable travel control so mapping can place it beside the map without changing input semantics. */
@Composable
fun ManualDriveTravelPad(
    state: ManualDriveState,
    onTravel: (TravelDirection) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 168.dp,
    compact: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
        Text("前后行走", color = DriveInk, fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 22.sp)
        Surface(shape = RoundedCornerShape(50), color = Color(0xFFEAF0F6),
            modifier = Modifier.width(dialSize * 0.72f).height(dialSize), shadowElevation = 3.dp) {
            Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TravelButton(TravelDirection.FORWARD, state, onTravel, Modifier.weight(1f), compact)
                TravelButton(TravelDirection.BACKWARD, state, onTravel, Modifier.weight(1f), compact)
            }
        }
        Text("点击保持，反向先停", color = DriveMuted, fontSize = if (compact) 10.sp else 13.sp)
    }
}

@Composable
fun ManualDriveSteeringPad(
    state: ManualDriveState,
    onSteering: (Float, Int) -> Unit,
    onSteeringCancel: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = 168.dp,
    compact: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
        Text("左右转向", color = DriveInk, fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 22.sp)
        HorizontalDriveJoystick(state, onSteering, onSteeringCancel, Modifier.size(dialSize))
        Text(if (compact) "转得越多，行走越慢" else "推满只转向，回中恢复原速度",
            color = DriveMuted, fontSize = if (compact) 10.sp else 13.sp, textAlign = TextAlign.Center)
    }
}

/** Compact footer: status stays above the stop target, leaving the map its full row height. */
@Composable
fun ManualDriveFooter(
    state: ManualDriveState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = manualFeedback()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val direction = when (state.direction) {
            TravelDirection.FORWARD -> "前进中"
            TravelDirection.BACKWARD -> "后退中"
            TravelDirection.STOPPED -> if (state.steering != 0f) "原地转向" else "已停止"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.enabled) direction else "控制未就绪",
                color = if (state.enabled) DriveBlue else DriveMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (state.direction != TravelDirection.STOPPED && state.steering != 0f) {
                Text("转向减速 ${abs(state.steering * 100).roundToInt()}%", color = DriveBlue, fontSize = 13.sp)
            }
            Text(String.format(Locale.US, "行走指令  %.3f m/s", state.commandedSpeedMps),
                color = DriveMuted, fontSize = 12.sp)
        }
        Surface(onClick = { onStop(); if (state.enabled) feedback(ManualFeedback.Stop) }, color = Color(0xFFD82B2A), shape = RoundedCornerShape(50),
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(48.dp).testTag("manual_stop")) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_task_stop), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("停止行走", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TravelButton(
    direction: TravelDirection, state: ManualDriveState, onTravel: (TravelDirection) -> Unit,
    modifier: Modifier, compact: Boolean,
) {
    val selected = state.direction == direction
    val feedback = manualFeedback()
    Surface(onClick = {
        onTravel(direction)
        feedback(if (state.direction != TravelDirection.STOPPED && state.direction != direction)
            ManualFeedback.Stop else ManualFeedback.Tick)
    }, enabled = state.enabled,
        modifier = modifier.fillMaxWidth().testTag(if (direction == TravelDirection.FORWARD) "travel_forward" else "travel_backward"),
        shape = RoundedCornerShape(32.dp), color = if (selected) Color(0xFFD5EFFF) else Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val tint = if (!state.enabled) Color(0xFFABB5C1) else if (selected) DriveBlue else DriveMuted
            Icon(painterResource(DesignR.drawable.ic_arrow_down), null, tint = tint,
                modifier = Modifier.size(if (compact) 24.dp else 38.dp)
                    .rotate(if (direction == TravelDirection.FORWARD) 180f else 0f))
            Text(if (direction == TravelDirection.FORWARD) "前进" else "后退", color = tint,
                fontSize = if (compact) 19.sp else 27.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HorizontalDriveJoystick(
    state: ManualDriveState,
    onSteering: (Float, Int) -> Unit,
    onCancel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = manualFeedback()
    val feedbackTracker = remember(state.inputEpoch) { SteeringFeedbackTracker() }
    fun applySteering(value: Float, epoch: Int) {
        onSteering(value, epoch)
        if (feedbackTracker.update(value)) feedback(ManualFeedback.Tick)
    }
    val density = LocalDensity.current
    val steer by rememberUpdatedState(::applySteering)
    val cancel by rememberUpdatedState(onCancel)
    BoxWithConstraints(modifier) {
        val knobSize = (maxWidth * 0.29f).coerceIn(44.dp, 84.dp)
        val travelPx = with(density) { ((maxWidth - 24.dp - knobSize) / 2).toPx() }
        val centerPx = with(density) { maxWidth.toPx() / 2 }
        val labelInset = maxHeight * 0.17f
        val scaleInset = maxWidth * 0.18f
        val epoch = state.inputEpoch
        Box(Modifier.fillMaxSize().shadow(3.dp, CircleShape).background(Color(0xFFF0F5FA), CircleShape)
            .border(2.dp, Color.White, CircleShape).testTag("steering_joystick")
            .semantics {
                contentDescription = "左右转向摇杆"
                progressBarRangeInfo = ProgressBarRangeInfo(state.steering, -1f..1f)
                setProgress { value -> if (state.enabled) { steer(value, epoch); true } else false }
            }
            .pointerInput(epoch, state.enabled, travelPx) {
                if (!state.enabled) return@pointerInput
                fun applyX(x: Float) = steer(((x - centerPx) / travelPx).coerceIn(-1f, 1f), epoch)
                detectDragGestures(
                    onDragStart = { applyX(it.x) },
                    onDragEnd = { steer(0f, epoch) },
                    onDragCancel = { cancel(epoch) },
                    onDrag = { change, _ -> change.consume(); applyX(change.position.x) },
                )
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(knobSize)
                .background(Color(0xFFD7EDFB), RoundedCornerShape(50))) {
                Row(Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(DesignR.drawable.ic_arrow_right), null, tint = DriveMuted,
                        modifier = Modifier.size(18.dp).rotate(180f))
                    Icon(painterResource(DesignR.drawable.ic_arrow_right), null, tint = DriveMuted, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.offset { IntOffset((state.steering * travelPx).roundToInt(), 0) }
                .size(knobSize).shadow(5.dp, CircleShape).background(Color.White, CircleShape)
                .border(2.dp, Color(0xFFE1EAF1), CircleShape))
            Text("${abs(state.steering * 100).roundToInt()}%", color = DriveBlue,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = labelInset))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = scaleInset)
                .padding(bottom = labelInset), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−100%", color = DriveMuted, fontSize = 10.sp)
                Text("0", color = DriveMuted, fontSize = 10.sp)
                Text("+100%", color = DriveMuted, fontSize = 10.sp)
            }
        }
    }
}
