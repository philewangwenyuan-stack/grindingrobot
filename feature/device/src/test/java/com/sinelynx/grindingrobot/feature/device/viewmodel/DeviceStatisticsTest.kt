package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.DeviceWorkTimeStatisticsPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DeviceStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun seconds(value: String) = Instant.parse(value).epochSecond
    private val now = seconds("2026-10-26T04:00:00Z")
    private fun counter(today: Long = 600, total: Long = 4200, incomplete: Int = 0) =
        DeviceWorkTimeStatisticsPayload(true, total, today, "2026-10-26", incomplete)

    @Test fun `empty or deleted history does not reset independent counters`() {
        assertEquals(DeviceStatistics(0.0, 0.0, 600L, 4200L),
            summarizeDeviceStatistics(emptyList(), now, zone, counter()))
    }

    @Test fun `deleting todays history changes area but neither time counter`() {
        val record = record("today", "2026-10-26T01:00:00Z", "2026-10-26T02:00:00Z", 35f)
        val before = summarizeDeviceStatistics(listOf(record), now, zone, counter())
        val after = summarizeDeviceStatistics(emptyList(), now, zone, counter())
        assertEquals(35.0, before.todayAreaM2, 0.001)
        assertEquals(0.0, after.todayAreaM2, 0.001)
        assertEquals(before.todayWorkSeconds, after.todayWorkSeconds)
        assertEquals(before.totalWorkSeconds, after.totalWorkSeconds)
    }

    @Test fun `area uses executed values and deduplicates IDs without affecting time`() {
        val today = record("today", "2026-10-26T01:00:00Z", "2026-10-26T02:00:00Z", 35f)
        val older = record("older", "2026-10-24T01:00:00Z", "2026-10-24T02:00:00Z", 80f)
        val summary = summarizeDeviceStatistics(listOf(today, today, older), now, zone, counter())
        assertEquals(35.0, summary.todayAreaM2, 0.001)
        assertEquals(115.0, summary.totalAreaM2, 0.001)
        assertEquals(600L, summary.todayWorkSeconds)
        assertEquals(4200L, summary.totalWorkSeconds)
    }

    @Test fun `today and lifetime can legitimately be equal`() {
        val summary = summarizeDeviceStatistics(emptyList(), now, zone, counter(today = 600, total = 600))
        assertEquals(600L, summary.todayWorkSeconds)
        assertEquals(summary.todayWorkSeconds, summary.totalWorkSeconds)
    }

    @Test fun `orphan history never creates timers and missing baseline stays marked after deletion`() {
        val orphan = record("orphan", "2026-10-24T03:00:00Z", "2026-10-24T03:00:00Z", 0f)
            .copy(finishedAt = 0L, finalState = "RUNNING")
        val baseline = counter(today = 0, total = 3175, incomplete = 2)
        val before = summarizeDeviceStatistics(listOf(orphan), now, zone, baseline)
        val after = summarizeDeviceStatistics(emptyList(), now + 3600, zone, baseline)
        assertEquals(before, after)
        assertEquals(0L, before.todayWorkSeconds)
        assertEquals(3175L, before.totalWorkSeconds)
        assertEquals(2, before.unsettledRecordCount)
        assertEquals("≥52分", formatStatisticsDuration(before.totalWorkSeconds, true))
    }

    @Test fun `old board does not silently substitute history sum for lifetime time`() {
        val record = record("old", "2026-10-26T01:00:00Z", "2026-10-26T02:00:00Z", 5f)
        val summary = summarizeDeviceStatistics(listOf(record), now, zone)
        assertNull(summary.todayWorkSeconds)
        assertNull(summary.totalWorkSeconds)
        assertEquals("—", formatStatisticsDuration(summary.totalWorkSeconds))
        assertEquals(5.0, summary.totalAreaM2, 0.001)
    }

    @Test fun `unavailable counters do not display false zero or stale history time`() {
        val summary = summarizeDeviceStatistics(emptyList(), now, zone, counter().copy(available = false))
        assertNull(summary.todayWorkSeconds)
        assertNull(summary.totalWorkSeconds)
    }

    @Test fun `board handles midnight and APP never extrapolates an old live sample`() {
        val overnight = record("night", "2026-10-25T15:00:00Z", "2026-10-25T17:00:00Z", 20f)
        val authoritative = counter(today = 3600, total = 7200)
        val summary = summarizeDeviceStatistics(listOf(overnight), now, zone, authoritative)
        assertEquals(3600L, summary.todayWorkSeconds)
        assertEquals(7200L, summary.totalWorkSeconds)
        assertEquals(summary, summarizeDeviceStatistics(listOf(overnight), now + 3600, zone, authoritative))
        assertEquals(0.0, summary.todayAreaM2, 0.001)
    }

    @Test fun `bad area cannot change independently persisted time`() {
        val invalid = record("bad", "2026-10-26T01:00:00Z", "2026-10-26T02:00:00Z", Float.NaN)
        assertEquals(DeviceStatistics(0.0, 0.0, 600L, 4200L),
            summarizeDeviceStatistics(listOf(invalid), now, zone, counter()))
    }

    @Test fun `repeated task executions with no execution ID have separate area`() {
        val first = record("", "2026-10-26T01:00:00Z", "2026-10-26T02:00:00Z", 5f)
        val second = record("", "2026-10-26T02:00:00Z", "2026-10-26T03:00:00Z", 7f)
        assertEquals(12.0, summarizeDeviceStatistics(listOf(first, second), now, zone, counter()).totalAreaM2, 0.001)
    }

    @Test fun `formats known unknown and lower bound durations`() {
        assertEquals("0分", formatStatisticsDuration(0L))
        assertEquals("3小时26分", formatStatisticsDuration(12360L))
        assertEquals("—", formatStatisticsDuration(null, true))
        assertEquals("≥52分", formatStatisticsDuration(3175L, true))
    }

    @Test fun `stopping a task preserves independent statistics`() {
        val stopped = DeviceStatusUiState(taskState = DeviceTaskUiState.RUNNING,
            todayAreaText = "268.50m²", totalAreaText = "12458.70m²",
            todayWorkTimeText = "3小时26分", totalWorkTimeText = "120小时15分").afterTaskStopped()
        assertEquals(DeviceTaskUiState.IDLE, stopped.taskState)
        assertEquals("268.50m²", stopped.todayAreaText)
        assertEquals("12458.70m²", stopped.totalAreaText)
        assertEquals("3小时26分", stopped.todayWorkTimeText)
        assertEquals("120小时15分", stopped.totalWorkTimeText)
    }

    private fun record(id: String, start: String, end: String, area: Float) =
        TaskExecutionHistoryRecordPayload(executionId = id, mapId = "map", taskId = "task",
            finalState = "COMPLETED", stopReason = "", startedAt = seconds(start), finishedAt = seconds(end),
            plannedAreaM2 = 1000f, executedAreaM2 = area, progress = 1f, pathVersion = 1, allCompleted = true)
}
