package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import java.time.Instant
import java.time.ZoneId

internal data class DeviceStatistics(
    val todayAreaM2: Double,
    val totalAreaM2: Double,
    val todayWorkSeconds: Long,
    val totalWorkSeconds: Long
)

/**
 * Same source as the history/statistics pages: actual executed area, never planned area.
 * The protocol exposes elapsed execution time (including pauses), not motor-on work time.
 * Historical area belongs to its execution start date; duration is clipped at local midnight.
 */
internal fun summarizeDeviceStatistics(
    source: List<TaskExecutionHistoryRecordPayload>,
    nowSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault()
): DeviceStatistics {
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val dayStart = today.atStartOfDay(zone).toEpochSecond()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
    val records = source.distinctBy {
        it.executionId.ifBlank { "${it.mapId}/${it.taskId}/${it.startedAt}" }
    }
    fun area(record: TaskExecutionHistoryRecordPayload): Double =
        record.executedAreaM2.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
    fun executionEnd(record: TaskExecutionHistoryRecordPayload): Long {
        val ongoing = record.finalState.uppercase().removePrefix("TASK_STATE_") in
            setOf("RUNNING", "PAUSED", "PLANNING")
        return when {
            record.finishedAt > 0L -> minOf(record.finishedAt, nowSeconds)
            ongoing -> nowSeconds
            else -> 0L
        }
    }
    return DeviceStatistics(
        todayAreaM2 = records.filter { it.startedAt in dayStart until dayEnd }.sumOf(::area),
        totalAreaM2 = records.sumOf(::area),
        todayWorkSeconds = records.sumOf { record ->
            val end = executionEnd(record)
            if (record.startedAt <= 0L || end < record.startedAt) 0L
            else (minOf(end, dayEnd) - maxOf(record.startedAt, dayStart)).coerceAtLeast(0L)
        },
        totalWorkSeconds = records.sumOf { record ->
            val end = executionEnd(record)
            if (record.startedAt <= 0L || end < record.startedAt) 0L
            else end - record.startedAt
        }
    )
}

internal fun formatStatisticsDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0L) / 60L
    return if (minutes >= 60L) "${minutes / 60L}小时${minutes % 60L}分" else "${minutes}分"
}
