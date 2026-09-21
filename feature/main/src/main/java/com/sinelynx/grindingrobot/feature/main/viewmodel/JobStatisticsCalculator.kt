package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

internal fun datePickerDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun datePickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal data class JobStatisticsQuery(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val zone: ZoneId,
    val mapNames: Map<String, String>
) {
    val startSeconds = startDate.atStartOfDay(zone).toEpochSecond()
    val endSeconds = endDate.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1

    fun accepts(payload: TaskExecutionHistoryPayload): Boolean =
        payload.mapId.isBlank() && payload.taskId.isBlank() &&
            ((payload.startTime == startSeconds && payload.endTime == endSeconds) ||
                // Parser failures have no echoed query fields. Only the request owner consumes them.
                (!payload.isSuccess && payload.startTime == 0L && payload.endTime == 0L))
}

/** The transport has a single history chunk assembler, so callers must serialize requests. */
internal object TaskHistoryRequestGate {
    private var owner: Any? = null
    @Synchronized fun acquire(candidate: Any): Boolean {
        if (owner != null) return false
        owner = candidate
        return true
    }
    @Synchronized fun release(candidate: Any) {
        if (owner === candidate) owner = null
    }
}

internal fun calculateJobStatistics(
    query: JobStatisticsQuery,
    source: List<TaskExecutionHistoryRecordPayload>
): JobStatisticsResult {
    val seen = mutableSetOf<String>()
    val records = source.filter {
        it.mapId in query.mapNames && it.startedAt in query.startSeconds..query.endSeconds &&
            (it.executionId.isBlank() || seen.add(it.executionId))
    }.filter {
        // A finish timestamp also covers firmware-specific terminal state names.
        it.finishedAt > 0 || it.allCompleted ||
            it.finalState.uppercase() in setOf("COMPLETED", "ERROR", "FAILED", "STOPPED", "CANCELLED", "CANCELED", "ABORTED")
    }
    val months = generateSequence(YearMonth.from(query.startDate)) { it.plusMonths(1) }
        .takeWhile { it <= YearMonth.from(query.endDate) }.toList()
    fun duration(record: TaskExecutionHistoryRecordPayload): Double? =
        if (record.startedAt > 0 && record.finishedAt >= record.startedAt)
            (record.finishedAt - record.startedAt).toDouble() else null
    fun area(record: TaskExecutionHistoryRecordPayload): Double =
        record.executedAreaM2.takeIf { it.isFinite() && it >= 0 }?.toDouble() ?: 0.0
    fun mapName(id: String) = query.mapNames[id]?.takeIf { it.isNotBlank() } ?: id
    fun summarize(name: String, items: List<TaskExecutionHistoryRecordPayload>, names: String): TaskStatisticsItem {
        val seconds = items.sumOf { duration(it) ?: 0.0 }
        val grouped = items.groupBy { YearMonth.from(Instant.ofEpochSecond(it.startedAt).atZone(query.zone)) }
        return TaskStatisticsItem(
            taskName = name,
            taskCount = items.size,
            totalArea = items.sumOf(::area).toFloat(),
            totalDurationHours = (seconds / 3600).toFloat(),
            mapNames = names,
            utilizationRate = (seconds / (query.endSeconds - query.startSeconds + 1) * 100).toFloat(),
            areaChartData = months.map { month ->
                ChartDataPoint(month.toString(), grouped[month].orEmpty().sumOf(::area).toFloat())
            },
            durationChartData = months.map { month ->
                ChartDataPoint(month.toString(), (grouped[month].orEmpty().sumOf { duration(it) ?: 0.0 } / 3600).toFloat())
            },
            invalidDurationCount = items.count { duration(it) == null }
        )
    }
    val names = query.mapNames.keys.joinToString("、", transform = ::mapName)
    val all = summarize("全部任务", records, names)
    val tasks = records.groupBy { it.mapId to it.taskId }.toSortedMap(
        compareBy<Pair<String, String>> { it.first }.thenBy { it.second }
    ).map { (key, items) ->
        val taskName = items.filter { it.taskName.isNotBlank() }
            .maxByOrNull { it.startedAt }?.taskName?.trim() ?: "未命名任务"
        summarize("$taskName（${mapName(key.first)}）", items, mapName(key.first))
    }
    return JobStatisticsResult(
        dateRange = "${query.startDate} ~ ${query.endDate}",
        mapNames = names,
        tasks = listOf(all) + tasks,
        totalTaskCount = all.taskCount,
        totalArea = all.totalArea,
        totalDurationHours = all.totalDurationHours,
        totalMapNames = names,
        utilizationRate = all.utilizationRate
    )
}

internal fun jobStatisticsCsv(result: JobStatisticsResult): String = buildString {
    fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    appendLine("任务名称,月份,作业面积(m²),执行经过时长(小时),说明")
    result.tasks.forEach { task ->
        task.areaChartData.forEachIndexed { index, point ->
            val note = if (task.invalidDurationCount > 0)
                "时长不完整：该任务有${task.invalidDurationCount}条记录时间异常，时长仅汇总有效记录" else
                "时长为结束减开始，包含暂停；月份按执行开始时间归属"
            appendLine(listOf(
                task.taskName, point.label, point.value.toString(),
                (task.durationChartData.getOrNull(index)?.value ?: 0f).toString(), note
            ).joinToString(",") { cell(it) })
        }
    }
}
