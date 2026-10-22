package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

internal enum class JoystickSnapAxis { NONE, HORIZONTAL, VERTICAL }

internal data class JoystickAxisSnap(val offset: Offset, val axis: JoystickSnapAxis)

/** Enter within 12 degrees of an axis; leave beyond 20 degrees to avoid boundary chatter. */
internal fun snapJoystickToAxis(
    rawOffset: Offset,
    previousAxis: JoystickSnapAxis,
    deadZonePx: Float
): JoystickAxisSnap {
    val radius = hypot(rawOffset.x, rawOffset.y)
    if (!radius.isFinite() || radius <= deadZonePx) {
        return JoystickAxisSnap(Offset.Zero, JoystickSnapAxis.NONE)
    }
    val x = abs(rawOffset.x)
    val y = abs(rawOffset.y)
    val axis = when {
        previousAxis == JoystickSnapAxis.VERTICAL && x <= y * AXIS_EXIT_TANGENT -> JoystickSnapAxis.VERTICAL
        previousAxis == JoystickSnapAxis.HORIZONTAL && y <= x * AXIS_EXIT_TANGENT -> JoystickSnapAxis.HORIZONTAL
        x <= y * AXIS_ENTER_TANGENT -> JoystickSnapAxis.VERTICAL
        y <= x * AXIS_ENTER_TANGENT -> JoystickSnapAxis.HORIZONTAL
        else -> JoystickSnapAxis.NONE
    }
    val offset = when (axis) {
        JoystickSnapAxis.VERTICAL -> Offset(0f, sign(rawOffset.y) * radius)
        JoystickSnapAxis.HORIZONTAL -> Offset(sign(rawOffset.x) * radius, 0f)
        JoystickSnapAxis.NONE -> rawOffset
    }
    return JoystickAxisSnap(offset, axis)
}

private const val AXIS_ENTER_TANGENT = 0.21255656f // tan(12 degrees)
private const val AXIS_EXIT_TANGENT = 0.36397023f // tan(20 degrees)
