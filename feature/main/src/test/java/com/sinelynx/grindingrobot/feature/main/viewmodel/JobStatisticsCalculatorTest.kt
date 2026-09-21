package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class JobStatisticsCalculatorTest {
    @Test fun namesUseLatestNonBlankWithoutChangingIdentityOrTotals() {
        val records = listOf(
            record("new", start = query.startSeconds + 20).copy(taskName = "新名称"),
            record("old").copy(taskName = "旧名称"),
            record("blank", start = query.startSeconds + 40).copy(taskName = "  "),
            record("other").copy(taskId = "other", taskName = "新名称")
        )
        val result = calculateJobStatistics(query, records)
        assertEquals(3, result.tasks.size)
        assertEquals(4, result.totalTaskCount)
        assertEquals(20f, result.totalArea, 0f)
        assertEquals(120f / 3600, result.totalDurationHours, 0.00001f)
        assertTrue(result.tasks.drop(1).all { it.taskName == "新名称（地图A）" })
        assertFalse(jobStatisticsCsv(result).contains("旧名称"))
        assertTrue(jobStatisticsCsv(result).contains("新名称"))
        assertEquals("未命名任务（地图A）", calculateJobStatistics(query,
            listOf(record().copy(taskName = "  "))).tasks.last().taskName)
    }

    @Test fun recordListUsesExecutionNameAndRetainsIds() {
        val original = record().copy(taskName = "  执行时名称  ")
        val item = original.toTaskRecordItem(query.mapNames)
        assertEquals("执行时名称", item.taskName)
        assertEquals(original.taskId, item.taskId)
        assertEquals(original.executionId, item.executionId)
        assertEquals(original.startedAt * 1000, item.startTime)
        assertEquals("未命名任务", original.copy(taskName = " ").toTaskRecordItem(query.mapNames).taskName)
        assertEquals("未命名任务", record().toTaskRecordItem(query.mapNames).taskName)
    }

    private val zone = ZoneId.of("Asia/Shanghai")
    private val query = JobStatisticsQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), zone,
        linkedMapOf("a" to "地图A", "b" to "地图B"))
    private fun record(id: String = "1", map: String = "a", start: Long = query.startSeconds,
        seconds: Long = 30, state: String = "COMPLETED") = TaskExecutionHistoryRecordPayload(
        id, map, "task", state, "", start, start + seconds, 10f, 5f, 1f, 1, state == "COMPLETED"
    )

    @Test fun utcPickerDateUsesLocalDayBoundariesIncludingDst() {
        val day = LocalDate.of(2026, 3, 8)
        assertEquals(day, datePickerDate(datePickerMillis(day)))
        val dst = JobStatisticsQuery(day, day, ZoneId.of("America/New_York"), emptyMap())
        assertEquals(23 * 3600L, dst.endSeconds - dst.startSeconds + 1)
        assertEquals("2025-12-31T16:00:00Z", java.time.Instant.ofEpochSecond(query.startSeconds).toString())
    }

    @Test fun filtersMapsAndInclusiveDatesAndDeduplicatesExecutions() {
        val first = record()
        val result = calculateJobStatistics(query, listOf(first, first,
            record("2", "b", query.endSeconds), record("3", "other"),
            record("4", start = query.startSeconds - 1), record("5", start = query.endSeconds + 1)))
        assertEquals(2, result.totalTaskCount)
        assertEquals(10f, result.totalArea, 0.001f)
        assertEquals(3, result.tasks.size)
        assertEquals(1f / 60, result.totalDurationHours, 0.00001f)
        assertEquals(listOf(5f, 0f, 5f), result.tasks.first().areaChartData.map { it.value })
    }

    @Test fun blankIdsAreNotCollapsedAndFailureAndAbortCount() {
        val result = calculateJobStatistics(query, listOf(record("", state = "ERROR"),
            record("", state = "ABORTED")))
        assertEquals(2, result.totalTaskCount)
        assertEquals(10f, result.totalArea, 0.001f)
    }

    @Test fun invalidTimesAreMarkedAndActiveRecordsExcluded() {
        val result = calculateJobStatistics(query, listOf(record(), record("2", seconds = -1),
            record("3", state = "RUNNING").copy(finishedAt = 0),
            record("4", state = "ERROR").copy(finishedAt = 0)))
        assertEquals(3, result.totalTaskCount)
        assertEquals(2, result.tasks.first().invalidDurationCount)
        assertEquals(30f / 3600, result.totalDurationHours, 0.00001f)
        assertTrue(jobStatisticsCsv(result).contains("时长不完整"))
    }

    @Test fun emptyResultReplacesPreviousDataAndHasZeroMonths() {
        val result = calculateJobStatistics(query, emptyList())
        assertEquals(0, result.totalTaskCount)
        assertEquals(1, result.tasks.size)
        assertEquals(listOf(0f, 0f, 0f), result.tasks.first().durationChartData.map { it.value })
    }

    @Test fun responseMustMatchQueryButUnscopedParserFailureIsAccepted() {
        val response = TaskExecutionHistoryPayload(true, "", startTime = query.startSeconds, endTime = query.endSeconds)
        assertTrue(query.accepts(response))
        assertFalse(query.accepts(response.copy(startTime = 0)))
        assertFalse(query.accepts(response.copy(mapId = "a")))
        assertFalse(query.accepts(response.copy(taskId = "task")))
        assertTrue(query.accepts(TaskExecutionHistoryPayload(false, "解析失败")))
    }

    @Test fun onlyOwnerCanReleaseSharedRequestAndDuplicateClickCannotAcquire() {
        val owner = Any()
        val other = Any()
        try {
            assertTrue(TaskHistoryRequestGate.acquire(owner))
            assertFalse(TaskHistoryRequestGate.acquire(owner))
            TaskHistoryRequestGate.release(other)
            assertFalse(TaskHistoryRequestGate.acquire(other))
            TaskHistoryRequestGate.release(owner)
            assertTrue(TaskHistoryRequestGate.acquire(other))
        } finally {
            TaskHistoryRequestGate.release(owner)
            TaskHistoryRequestGate.release(other)
        }
    }

    @Test fun csvContainsSameSecondPrecisionTotalsAndEscapesNames() {
        val result = calculateJobStatistics(query.copy(mapNames = mapOf("a" to "A,\"B")), listOf(record()))
        val csv = jobStatisticsCsv(result)
        assertTrue(csv.contains("A,\"\"B"))
        assertTrue(csv.contains(result.tasks.first().durationChartData.first().value.toString()))
        assertEquals(7, csv.lineSequence().filter { it.isNotEmpty() }.count())
    }
}
