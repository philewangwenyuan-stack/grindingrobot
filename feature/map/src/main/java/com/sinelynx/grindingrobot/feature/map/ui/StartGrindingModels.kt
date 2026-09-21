package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal enum class StartTaskStep {
    Workspace,
    TaskParams,
    Obstacle,
    Preview
}

/** 缺失与真实零值分开显示；保持研磨预览原有的一位小数格式。 */
internal fun formatStartGrindingMetric(value: Float?): String = when {
    value == null || !value.isFinite() -> ""
    value < 0f -> "--"
    else -> "%.1f".format(value)
}

internal enum class TaskObstacleMode {
    Rectangle,
    Circle
}

internal data class TaskObstacleShape(
    val mode: TaskObstacleMode,
    val center: Offset,
    val width: Float,
    val height: Float,
    val radius: Float
) {
    val rect: Rect
        get() = Rect(
            left = center.x - width / 2f,
            top = center.y - height / 2f,
            right = center.x + width / 2f,
            bottom = center.y + height / 2f
        )
}
