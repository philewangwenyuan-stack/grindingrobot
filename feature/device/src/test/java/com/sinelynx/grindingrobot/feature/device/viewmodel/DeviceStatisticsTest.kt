package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DeviceStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun seconds(value: String) = Instant.parse(value).epochSecond
    private val now = seconds("2026-10-19T04:00:00Z")

    @Test fun `empty successful history produces actual zero totals`() {
        assertEquals(DeviceStatistics(0.0, 0.0, 0L, 0L), summarizeDeviceStatistics(emptyList(), now, zone))
    }

    @Test fun `counts executed area across maps and deduplicates execution IDs`() {
        val today = record("today", "2026-10-19T01:00:00Z", "2026-10-19T02:00:00Z", 35f)
        val yesterday = record("yesterday", "2026-10-18T01:00:00Z", "2026-10-18T02:00:00Z", 80f)
        val summary = summarizeDeviceStatistics(listOf(today, today, yesterday), now, zone)
        assertEquals(35.0, summary.todayAreaM2, 0.001)
        assertEquals(115.0, summary.totalAreaM2, 0.001)
        assertEquals(3600L, summary.todayWorkSeconds)
        assertEquals(7200L, summary.totalWorkSeconds)
    }

    @Test fun `clips elapsed work time at local midnight`() {
        val overnight = record("night", "2026-10-18T15:00:00Z", "2026-10-18T17:00:00Z", 20f)
        val summary = summarizeDeviceStatistics(listOf(overnight), now, zone)
        assertEquals(3600L, summary.todayWorkSeconds)
        assertEquals(7200L, summary.totalWorkSeconds)
        // The existing statistics protocol assigns area to the execution start date.
        assertEquals(0.0, summary.todayAreaM2, 0.001)
        assertEquals(20.0, summary.totalAreaM2, 0.001)
    }

    @Test fun `active historical record uses current time and actual partial area`() {
        val active = record("active", "2026-10-19T03:00:00Z", "2026-10-19T03:00:00Z", 12f)
            .copy(finishedAt = 0L, finalState = "RUNNING")
        val summary = summarizeDeviceStatistics(listOf(active), now, zone)
        assertEquals(12.0, summary.todayAreaM2, 0.001)
        assertEquals(3600L, summary.todayWorkSeconds)
        assertEquals(3600L, summary.totalWorkSeconds)
    }

    @Test fun `bad counters and missing finish times do not create work`() {
        val invalid = record("bad", "2026-10-19T01:00:00Z", "2026-10-19T02:00:00Z", Float.NaN)
            .copy(finishedAt = 0L)
        assertEquals(DeviceStatistics(0.0, 0.0, 0L, 0L), summarizeDeviceStatistics(listOf(invalid), now, zone))
    }

    @Test fun `repeated task executions with no execution ID remain distinct`() {
        val first = record("", "2026-10-19T01:00:00Z", "2026-10-19T02:00:00Z", 5f)
        val second = record("", "2026-10-19T02:00:00Z", "2026-10-19T03:00:00Z", 7f)
        assertEquals(12.0, summarizeDeviceStatistics(listOf(first, second), now, zone).totalAreaM2, 0.001)
    }

    @Test fun `formats durations without long seconds strings`() {
        assertEquals("0分", formatStatisticsDuration(0L))
        assertEquals("3小时26分", formatStatisticsDuration(12360L))
    }

    @Test fun `stopping a task preserves daily and cumulative statistics`() {
        val stopped = DeviceStatusUiState(
            taskState = DeviceTaskUiState.RUNNING,
            todayAreaText = "268.50m²",
            totalAreaText = "12458.70m²",
            todayWorkTimeText = "3小时26分",
            totalWorkTimeText = "120小时15分"
        ).afterTaskStopped()
        assertEquals(DeviceTaskUiState.IDLE, stopped.taskState)
        assertEquals("268.50m²", stopped.todayAreaText)
        assertEquals("12458.70m²", stopped.totalAreaText)
        assertEquals("3小时26分", stopped.todayWorkTimeText)
        assertEquals("120小时15分", stopped.totalWorkTimeText)
    }

    private fun record(id: String, start: String, end: String, area: Float) =
        TaskExecutionHistoryRecordPayload(
            executionId = id, mapId = "map", taskId = "task", finalState = "COMPLETED",
            stopReason = "", startedAt = seconds(start), finishedAt = seconds(end),
            plannedAreaM2 = 1000f, executedAreaM2 = area, progress = 1f,
            pathVersion = 1, allCompleted = true
        )
}
