package com.sinelynx.grindingrobot.feature.common.component

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView

enum class ManualFeedback { Tick, Stop }

/** Tests can record events without vibrating. Native feedback respects system settings. */
val LocalManualFeedback = compositionLocalOf<((ManualFeedback) -> Unit)?> { null }
val LocalManualFeedbackAllowed = compositionLocalOf<() -> Boolean> { { true } }

@Composable
internal fun manualFeedback(): (ManualFeedback) -> Unit {
    val override = LocalManualFeedback.current
    val allowed = LocalManualFeedbackAllowed.current
    val view = LocalView.current
    return { event ->
        if (allowed()) {
            if (override != null) override(event)
            else view.performHapticFeedback(if (event == ManualFeedback.Stop)
                HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/** Input events only: holding an endpoint or rendering a device update never repeats feedback. */
internal class SteeringFeedbackTracker {
    private var previous = 0f
    fun update(value: Float): Boolean {
        val boundary = when {
            value <= -1f -> -1
            value >= 1f -> 1
            kotlin.math.abs(value) <= 0.02f -> 0
            else -> 2
        }
        val old = when {
            previous <= -1f -> -1
            previous >= 1f -> 1
            kotlin.math.abs(previous) <= 0.02f -> 0
            else -> 2
        }
        previous = value
        return boundary != 2 && boundary != old
    }
}
