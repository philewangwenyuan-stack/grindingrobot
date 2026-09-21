package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.designsystem.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

enum class DirectionCommand {
    Up, Down, Left, Right, Stop
}

data class JoystickPosition(
    val xPx: Float,
    val yPx: Float,
    val normalizedX: Float,
    val normalizedY: Float,
    val distanceRatio: Float
)

@Composable
fun VirtualDirectionPad(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onCommandStart: (DirectionCommand) -> Unit = {},
    onCommandEnd: (DirectionCommand) -> Unit = {},
    onPositionChanged: (JoystickPosition) -> Unit = {}
) {
    val density = LocalDensity.current
    val thumbSize = 56.dp
    val sizePx = with(density) { size.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    // Allow the thumb to reach the outer edge: center radius - thumb radius.
    val maxThumbDistancePx = (sizePx - thumbSizePx) / 2f
    val deadZonePx = with(density) { 8.dp.toPx() }
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var activeCommand by remember { mutableStateOf(DirectionCommand.Stop) }
    var lastEmittedNormalized by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var edgeDwellAnchor by remember { mutableStateOf<Offset?>(null) }
    val currentOnCommandStart by rememberUpdatedState(onCommandStart)
    val currentOnCommandEnd by rememberUpdatedState(onCommandEnd)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)

    fun resolveDirection(offset: Offset): DirectionCommand {
        val distance = hypot(offset.x, offset.y)
        if (distance <= deadZonePx) return DirectionCommand.Stop
        return if (abs(offset.x) > abs(offset.y)) {
            if (offset.x > 0f) DirectionCommand.Right else DirectionCommand.Left
        } else {
            if (offset.y > 0f) DirectionCommand.Down else DirectionCommand.Up
        }
    }

    fun updateDirection(newDirection: DirectionCommand) {
        if (newDirection == activeCommand) return
        currentOnCommandEnd(activeCommand)
        activeCommand = newDirection
        currentOnCommandStart(newDirection)
    }

    fun emitPosition(offset: Offset) {
        val normalizedX = roundTo2Decimals((offset.x / maxThumbDistancePx).coerceIn(-1f, 1f))
        val normalizedY = roundTo2Decimals((offset.y / maxThumbDistancePx).coerceIn(-1f, 1f))
        val normalizedPair = normalizedX to normalizedY
        if (normalizedPair == lastEmittedNormalized) return

        lastEmittedNormalized = normalizedPair
        val distanceRatio = (hypot(offset.x, offset.y) / maxThumbDistancePx).coerceIn(0f, 1f)
        currentOnPositionChanged(
            JoystickPosition(
                xPx = offset.x,
                yPx = offset.y,
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                distanceRatio = distanceRatio
            )
        )
    }

    fun normalizedDistance(first: Offset, second: Offset): Float {
        return hypot(first.x - second.x, first.y - second.y) / max(maxThumbDistancePx, 0.0001f)
    }

    fun updateEdgeDwell(offset: Offset) {
        if (isLocked) return
        val distanceRatio = hypot(offset.x, offset.y) / max(maxThumbDistancePx, 0.0001f)
        if (distanceRatio < EDGE_LOCK_DISTANCE_RATIO) {
            edgeDwellAnchor = null
            return
        }

        val anchor = edgeDwellAnchor
        if (anchor == null || normalizedDistance(anchor, offset) > EDGE_LOCK_STABILITY_TOLERANCE) {
            edgeDwellAnchor = offset
        }
    }

    fun applyOffset(candidate: Offset) {
        val distance = hypot(candidate.x, candidate.y)
        thumbOffset = if (distance <= maxThumbDistancePx) {
            candidate
        } else {
            val ratio = maxThumbDistancePx / max(distance, 0.0001f)
            Offset(candidate.x * ratio, candidate.y * ratio)
        }
        updateDirection(resolveDirection(thumbOffset))
        emitPosition(thumbOffset)
        updateEdgeDwell(thumbOffset)
    }

    fun resetToCenter() {
        isLocked = false
        edgeDwellAnchor = null
        thumbOffset = Offset.Zero
        updateDirection(DirectionCommand.Stop)
        emitPosition(Offset.Zero)
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag(VIRTUAL_DIRECTION_PAD_TEST_TAG),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = Color(0x22000000),
                    ambientColor = Color(0x22000000)
                )
                .background(Color(0xFFF6F8FB), CircleShape)
                .border(1.dp, Color(0xFFFFFFFF), CircleShape)
                .pointerInput(sizePx, maxThumbDistancePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val wasLockedOnDown = isLocked
                        val center = Offset(sizePx / 2f, sizePx / 2f)
                        if (wasLockedOnDown) {
                            isLocked = false
                            edgeDwellAnchor = null
                            applyOffset(down.position - center)
                        }

                        try {
                            val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            } ?: return@awaitEachGesture

                            applyOffset(dragStart.position - center)

                            drag(dragStart.id) { change ->
                                change.consume()
                                if (!isLocked) {
                                    val dragAmount = change.position - change.previousPosition
                                    applyOffset(thumbOffset + dragAmount)
                                }
                            }
                        } finally {
                            if (!isLocked) {
                                resetToCenter()
                            }
                        }
                    }
                }
        )
        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset {
                    IntOffset(
                        x = thumbOffset.x.roundToInt(),
                        y = thumbOffset.y.roundToInt()
                    )
                }
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = Color(0x1A000000),
                    ambientColor = Color(0x1A000000)
                )
        ) {
            Image(
                painter = painterResource(id = com.sinelynx.grindingrobot.feature.map.R.drawable.ic_virtual_control),
                contentDescription = "unConnectNtrip"
            )
        }


    }

    LaunchedEffect(Unit) {
        currentOnCommandStart(DirectionCommand.Stop)
        emitPosition(Offset.Zero)
    }
    LaunchedEffect(edgeDwellAnchor, isLocked) {
        val anchor = edgeDwellAnchor ?: return@LaunchedEffect
        if (isLocked) return@LaunchedEffect
        delay(EDGE_LOCK_DWELL_MS)
        val stillAtEdge = hypot(thumbOffset.x, thumbOffset.y) /
            max(maxThumbDistancePx, 0.0001f) >= EDGE_LOCK_DISTANCE_RATIO
        if (
            !isLocked &&
            stillAtEdge &&
            edgeDwellAnchor == anchor &&
            normalizedDistance(anchor, thumbOffset) <= EDGE_LOCK_STABILITY_TOLERANCE
        ) {
            isLocked = true
            edgeDwellAnchor = null
        }
    }
    LaunchedEffect(activeCommand) {
        if (activeCommand == DirectionCommand.Stop) {
            currentOnCommandEnd(DirectionCommand.Stop)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (activeCommand != DirectionCommand.Stop) {
                currentOnCommandEnd(activeCommand)
                currentOnCommandStart(DirectionCommand.Stop)
            }
            if (lastEmittedNormalized != (0f to 0f)) {
                currentOnPositionChanged(
                    JoystickPosition(
                        xPx = 0f,
                        yPx = 0f,
                        normalizedX = 0f,
                        normalizedY = 0f,
                        distanceRatio = 0f
                    )
                )
            }
        }
    }
}

private const val EDGE_LOCK_DWELL_MS = 1_000L
private const val EDGE_LOCK_DISTANCE_RATIO = 0.99f
private const val EDGE_LOCK_STABILITY_TOLERANCE = 0.02f
internal const val VIRTUAL_DIRECTION_PAD_TEST_TAG = "VirtualDirectionPad"

private fun roundTo2Decimals(value: Float): Float {
    return round(value * 100f) / 100f
}


