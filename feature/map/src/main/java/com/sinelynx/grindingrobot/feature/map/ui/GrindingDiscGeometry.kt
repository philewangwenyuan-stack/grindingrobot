package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.core.data.state.AppState
import kotlin.math.cos
import kotlin.math.sin

const val GRINDING_DISC_DIAMETER_M = 0.9f
const val GRINDING_DISC_RADIUS_M = GRINDING_DISC_DIAMETER_M / 2f

/** The disc's front edge is tangent to the robot's foremost +X edge, inside the body. */
fun grindingDiscLocalCenter(footprint: List<AppState.FootprintPoint>, robotLength: Double? = null): Offset? {
    if (footprint.size >= 3 && footprint.all { it.x.isFinite() && it.y.isFinite() }) {
        return Offset(
            footprint.maxOf { it.x } - GRINDING_DISC_RADIUS_M,
            (footprint.minOf { it.y } + footprint.maxOf { it.y }) / 2f
        )
    }
    val length = robotLength?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return Offset((length / 2.0).toFloat() - GRINDING_DISC_RADIUS_M, 0f)
}

/** Offset in world coordinates first; map heading, display rotation and zoom are applied afterwards. */
fun grindingDiscWorldCenter(
    x: Float, y: Float, headingDeg: Float,
    footprint: List<AppState.FootprintPoint>, robotLength: Double? = null
): Offset? {
    if (!x.isFinite() || !y.isFinite() || !headingDeg.isFinite()) return null
    val local = grindingDiscLocalCenter(footprint, robotLength) ?: return null
    val angle = Math.toRadians(headingDeg.toDouble())
    val c = cos(angle).toFloat()
    val s = sin(angle).toFloat()
    return Offset(x + local.x * c - local.y * s, y + local.x * s + local.y * c)
}
