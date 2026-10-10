package com.sinelynx.grindingrobot.core.model.state

/** Device-confirmed setpoints; telemetry remains separate from these requested values. */
data class TaskRuntimeSettingsPayload(
    val taskId: String,
    val runSpeed: Float?,
    val discSpeedRpm: Int?,
    val maxRunSpeed: Float,
    val maxDiscSpeedRpm: Int,
    val temporary: Boolean
)
