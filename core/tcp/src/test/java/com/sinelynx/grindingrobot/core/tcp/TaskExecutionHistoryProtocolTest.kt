package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class TaskExecutionHistoryProtocolTest {
    @Test
    fun historyNameSurvivesSplitInsideChineseCharacter() {
        val prefix = """{"result":"success","records":[{"task_name":"""".encodeToByteArray()
        val json = prefix + "车间研磨\"}]}".encodeToByteArray()
        val split = prefix.size + 1
        val assembler = TaskExecutionHistoryChunkAssembler()
        assembler.append(1, 2, 1, 0, 0, json.copyOfRange(split, json.size))
        val complete = assembler.append(0, 2, 1, 0, 0, json.copyOfRange(0, split))
            as TaskExecutionHistoryChunkResult.Complete
        assertEquals("车间研磨", TaskExecutionHistoryParser.parse(complete.data).records.single().taskName)
    }

    @Test
    fun requestUsesExpectedMessageAndFilters() {
        val request = buildTaskExecutionHistoryRequest(
            mapId = "map_001",
            taskId = "task_001",
            startTime = 1780001000L,
            endTime = 1780005000L,
            maxChunkSize = 2048
        )
        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildTaskExecutionHistoryRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x0530, frame.msgId.toInt())
        val decoded = SlLink.TaskExecutionHistoryRequest.parseFrom(frame.payload)
        assertEquals("map_001", decoded.mapId)
        assertEquals("task_001", decoded.taskId)
        assertEquals(1780001000L, decoded.startTime)
        assertEquals(1780005000L, decoded.endTime)
        assertEquals(2048, decoded.maxChunkSize)
    }

    @Test
    fun assemblerJoinsOutOfOrderUtf8BytesAndReplacesDuplicates() {
        val assembler = TaskExecutionHistoryChunkAssembler()
        val json = "{\"message\":\"任务历史\"}".encodeToByteArray()
        val firstEnd = json.size / 3
        val secondEnd = json.size * 2 / 3
        val chunks = listOf(
            json.copyOfRange(0, firstEnd),
            json.copyOfRange(firstEnd, secondEnd),
            json.copyOfRange(secondEnd, json.size)
        )

        assertTrue(assembler.append(2, 3, 1, 0, 0, chunks[2]) is TaskExecutionHistoryChunkResult.Pending)
        assertTrue(assembler.append(0, 3, 1, 0, 0, "wrong".encodeToByteArray()) is TaskExecutionHistoryChunkResult.Pending)
        assertTrue(assembler.append(0, 3, 1, 0, 0, chunks[0]) is TaskExecutionHistoryChunkResult.Pending)
        val result = assembler.append(1, 3, 1, 0, 0, chunks[1])

        assertTrue(result is TaskExecutionHistoryChunkResult.Complete)
        assertArrayEquals(json, (result as TaskExecutionHistoryChunkResult.Complete).data)
    }

    @Test
    fun assemblerRejectsInvalidChunksAndNeverReturnsPartialData() {
        val assembler = TaskExecutionHistoryChunkAssembler()
        assertTrue(assembler.append(-1, 2, 1, 0, 0, byteArrayOf()) is TaskExecutionHistoryChunkResult.Invalid)
        assertTrue(assembler.append(0, 0, 1, 0, 0, byteArrayOf()) is TaskExecutionHistoryChunkResult.Invalid)
        assertTrue(assembler.append(0, 2, 1, 0, 0, byteArrayOf(1)) is TaskExecutionHistoryChunkResult.Pending)
    }

    @Test
    fun parserMapsHistoryRecordsAndPreservesServerOrder() {
        val json = """
            {
              "result":"success",
              "message":"task_execution_history_ready",
              "map_id":"",
              "task_id":"",
              "start_time":0,
              "end_time":0,
              "total_record_count":2,
              "records":[
                {
                  "execution_id":"newer",
                  "map_id":"map_002",
                  "task_id":"task_003",
                  "task_name":"车间研磨",
                  "final_state":"COMPLETED",
                  "stop_reason":"completed",
                  "started_at":1780005000,
                  "finished_at":1780005600,
                  "planned_area_m2":50.0,
                  "executed_area_m2":50.0,
                  "progress":1.0,
                  "path_version":5,
                  "all_completed":true
                },
                {
                  "execution_id":"older",
                  "map_id":"map_001",
                  "task_id":"task_001",
                  "final_state":"ERROR",
                  "stop_reason":"navigation_error",
                  "started_at":1780003000,
                  "finished_at":1780003200,
                  "planned_area_m2":30.0,
                  "executed_area_m2":5.0,
                  "progress":0.1667,
                  "path_version":3,
                  "all_completed":false
                }
              ]
            }
        """.trimIndent()

        val payload = TaskExecutionHistoryParser.parse(json.encodeToByteArray())

        assertTrue(payload.isSuccess)
        assertEquals(2, payload.totalRecordCount)
        assertEquals(listOf("newer", "older"), payload.records.map { it.executionId })
        assertEquals(listOf("车间研磨", ""), payload.records.map { it.taskName })
        assertEquals(50f, payload.records.first().executedAreaM2, 0f)
        assertEquals(1780005000L, payload.records.first().startedAt)
        assertTrue(payload.records.first().allCompleted)
        assertFalse(payload.records.last().allCompleted)
    }

    @Test(expected = Exception::class)
    fun parserRejectsMalformedJson() {
        TaskExecutionHistoryParser.parse("not-json".encodeToByteArray())
    }
}
