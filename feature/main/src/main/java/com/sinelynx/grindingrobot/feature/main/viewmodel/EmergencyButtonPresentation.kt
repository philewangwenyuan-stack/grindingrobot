package com.sinelynx.grindingrobot.feature.main.viewmodel

internal data class EmergencyButtonPresentation(
    val label: String,
    val releaseAction: Boolean = false,
    val enabled: Boolean = true
)

internal fun emergencyButtonPresentation(latched: Boolean?, stopping: Boolean, releasing: Boolean): EmergencyButtonPresentation = when {
    stopping -> EmergencyButtonPresentation("正在制动…", enabled = false)
    // Preserve the ability to reassert emergency stop while a release is being checked.
    releasing -> EmergencyButtonPresentation("紧急制动")
    latched == true -> EmergencyButtonPresentation("解除急停", releaseAction = true)
    else -> EmergencyButtonPresentation("紧急制动")
}
