package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryRecordPayload
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal object TaskExecutionHistoryParser {
    fun parse(bytes: ByteArray): TaskExecutionHistoryPayload {
        val json = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        val root = Json.parseToJsonElement(json) as? JsonObject
            ?: error("任务历史JSON不是对象")
        val result = root.string("result").orEmpty()
        val records = root.array("records").mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            TaskExecutionHistoryRecordPayload(
                executionId = record.string("execution_id").orEmpty(),
                mapId = record.string("map_id").orEmpty(),
                taskId = record.string("task_id").orEmpty(),
                taskName = record.string("task_name").orEmpty(),
                finalState = record.string("final_state").orEmpty(),
                stopReason = record.string("stop_reason").orEmpty(),
                startedAt = record.long("started_at") ?: 0L,
                finishedAt = record.long("finished_at") ?: 0L,
                plannedAreaM2 = record.float("planned_area_m2") ?: 0f,
                executedAreaM2 = record.float("executed_area_m2") ?: 0f,
                progress = record.float("progress") ?: 0f,
                pathVersion = record.int("path_version") ?: 0,
                allCompleted = record.primitive("all_completed")?.booleanOrNull ?: false
            )
        }
        return TaskExecutionHistoryPayload(
            isSuccess = result.equals("success", ignoreCase = true) ||
                result.equals("RESULT_SUCCESS", ignoreCase = true),
            message = root.string("message").orEmpty(),
            mapId = root.string("map_id").orEmpty(),
            taskId = root.string("task_id").orEmpty(),
            startTime = root.long("start_time") ?: 0L,
            endTime = root.long("end_time") ?: 0L,
            totalRecordCount = root.int("total_record_count") ?: records.size,
            records = records
        )
    }

    private fun JsonObject.primitive(key: String) = this[key] as? JsonPrimitive
    private fun JsonObject.string(key: String) = primitive(key)?.contentOrNull
    private fun JsonObject.long(key: String) = string(key)?.toLongOrNull()
    private fun JsonObject.int(key: String) = string(key)?.toIntOrNull()
    private fun JsonObject.float(key: String) = string(key)?.toFloatOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
}

internal sealed interface TaskExecutionHistoryChunkResult {
    data object Pending : TaskExecutionHistoryChunkResult
    data class Complete(val data: ByteArray) : TaskExecutionHistoryChunkResult
    data class Invalid(val message: String) : TaskExecutionHistoryChunkResult
}

internal class TaskExecutionHistoryChunkAssembler {
    private var assembly: Assembly? = null

    fun append(
        chunkIndex: Int,
        totalChunks: Int,
        totalRecordCount: Int,
        startTime: Long,
        endTime: Long,
        data: ByteArray
    ): TaskExecutionHistoryChunkResult {
        if (totalChunks <= 0) {
            clear()
            return TaskExecutionHistoryChunkResult.Invalid("任务历史分块总数无效")
        }
        if (chunkIndex !in 0 until totalChunks) {
            clear()
            return TaskExecutionHistoryChunkResult.Invalid("任务历史分块序号无效")
        }
        val current = assembly?.takeIf {
            it.totalChunks == totalChunks &&
                it.totalRecordCount == totalRecordCount &&
                it.startTime == startTime &&
                it.endTime == endTime
        } ?: Assembly(totalChunks, totalRecordCount, startTime, endTime).also { assembly = it }
        current.chunks[chunkIndex] = data.copyOf()
        if (current.chunks.size < totalChunks) return TaskExecutionHistoryChunkResult.Pending

        val output = java.io.ByteArrayOutputStream()
        for (index in 0 until totalChunks) {
            output.write(current.chunks[index] ?: return TaskExecutionHistoryChunkResult.Pending)
        }
        clear()
        return TaskExecutionHistoryChunkResult.Complete(output.toByteArray())
    }

    fun clear() {
        assembly = null
    }

    private data class Assembly(
        val totalChunks: Int,
        val totalRecordCount: Int,
        val startTime: Long,
        val endTime: Long,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    )
}
