package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskRuntimeSettingsPayload
import java.util.Locale

enum class TaskRuntimeField(val label: String, val unit: String) {
    RUN_SPEED("直行速度", "m/s"), DISC_RPM("研磨转速", "rpm")
}

internal fun TaskRuntimeSettingsPayload.isUsableFor(taskId: String): Boolean =
    this.taskId == taskId && temporary && runSpeed?.let { it.isFinite() && it > 0f && it <= maxRunSpeed } == true &&
        maxRunSpeed.isFinite() && maxRunSpeed > 0f && discSpeedRpm?.let { it in 0..maxDiscSpeedRpm } == true &&
        maxDiscSpeedRpm in 1..32767

internal fun runtimeInputError(field: TaskRuntimeField, input: String, settings: TaskRuntimeSettingsPayload): String? =
    when (field) {
        TaskRuntimeField.RUN_SPEED -> if (input.toFloatOrNull()?.let { it.isFinite() && it > 0f && it <= settings.maxRunSpeed } == true) null
        else "请输入大于 0、不超过 ${settings.maxRunSpeed} m/s 的速度；停止请使用暂停按钮"
        TaskRuntimeField.DISC_RPM -> if (input.toIntOrNull()?.let { it in 0..settings.maxDiscSpeedRpm } == true) null
        else "请输入 0～${settings.maxDiscSpeedRpm} 的整数转速"
    }

internal fun runtimeInput(field: TaskRuntimeField, settings: TaskRuntimeSettingsPayload): String = when (field) {
    TaskRuntimeField.RUN_SPEED -> String.format(Locale.US, "%.3f", settings.runSpeed)
    TaskRuntimeField.DISC_RPM -> settings.discSpeedRpm.toString()
}

internal fun stepRuntimeInput(field: TaskRuntimeField, input: String, direction: Int, settings: TaskRuntimeSettingsPayload): String = when (field) {
    TaskRuntimeField.RUN_SPEED -> String.format(Locale.US, "%.3f", (
        (input.toFloatOrNull()?.takeIf { it.isFinite() } ?: settings.runSpeed!!) + direction * 0.01f
    ).coerceIn(minOf(0.01f, settings.maxRunSpeed), settings.maxRunSpeed))
    TaskRuntimeField.DISC_RPM -> ((input.toLongOrNull() ?: settings.discSpeedRpm!!.toLong())
        .coerceIn(0L, settings.maxDiscSpeedRpm.toLong()) + direction * 10L)
        .coerceIn(0L, settings.maxDiscSpeedRpm.toLong()).toString()
}
