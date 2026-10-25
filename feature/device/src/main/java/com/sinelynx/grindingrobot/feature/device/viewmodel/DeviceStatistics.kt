package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import com.sinelynx.grindingrobot.core.model.state.DeviceWorkTimeStatisticsPayload
import java.time.Instant
import java.time.ZoneId

internal data class DeviceStatistics(
    val todayAreaM2: Double,
    val totalAreaM2: Double,
    val todayWorkSeconds: Long?,
    val totalWorkSeconds: Long?,
    val unsettledRecordCount: Int = 0
)

/**
 * Area still uses retained execution records. Time exclusively uses the independent
 * board counters (elapsed task time including pauses); missing counters are unknown.
 */
internal fun summarizeDeviceStatistics(
    source: List<TaskExecutionHistoryRecordPayload>,
    nowSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    workTimeStatistics: DeviceWorkTimeStatisticsPayload? = null
): DeviceStatistics {
    val today = Instant.ofEpochSecond(nowSeconds).atZone(zone).toLocalDate()
    val dayStart = today.atStartOfDay(zone).toEpochSecond()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
    val records = source.distinctBy {
        it.executionId.ifBlank { "${it.mapId}/${it.taskId}/${it.startedAt}" }
    }
    fun area(record: TaskExecutionHistoryRecordPayload): Double =
        record.executedAreaM2.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0
    val counter = workTimeStatistics?.takeIf { it.available }
    return DeviceStatistics(
        todayAreaM2 = records.filter { it.startedAt in dayStart until dayEnd }.sumOf(::area),
        totalAreaM2 = records.sumOf(::area),
        todayWorkSeconds = counter?.todayWorkSeconds,
        totalWorkSeconds = counter?.totalWorkSeconds,
        unsettledRecordCount = counter?.incompleteRecordCount ?: 0
    )
}

internal fun formatStatisticsDuration(seconds: Long?, isLowerBound: Boolean = false): String {
    if (seconds == null) return "—"
    val minutes = seconds.coerceAtLeast(0L) / 60L
    val text = if (minutes >= 60L) "${minutes / 60L}小时${minutes % 60L}分" else "${minutes}分"
    return if (isLowerBound) "≥$text" else text
}
