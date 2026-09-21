package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPagePayload
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPointPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReplayPagingTest {
    @Test
    fun plannedPathTimeoutIsThreeMinutes() {
        assertEquals(180_000L, TASK_REPLAY_PATH_TIMEOUT_MS)
    }

    @Test
    fun plannedPathErrorsDistinguishSendTimeoutEmptyAndValidResults() {
        val empty = TaskPathPayload(
            taskId = "task",
            pathVersion = 1,
            frameId = "map",
            alignmentYawDeg = 0f,
            points = emptyList()
        )
        val valid = empty.copy(points = listOf(TaskPathPointPayload(1f, 2f)))

        assertEquals("规划路径请求发送失败", replayPlannedPathError(sent = false, path = null))
        assertEquals("规划路径加载超时", replayPlannedPathError(sent = true, path = null))
        assertEquals("规划路径未返回数据", replayPlannedPathError(sent = true, path = empty))
        assertEquals("设备未保存路径", replayPlannedPathError(
            sent = true,
            path = empty.copy(message = "设备未保存路径")
        ))
        assertNull(replayPlannedPathError(sent = true, path = valid))
    }

    @Test
    fun plannedPathResponseOnlyMatchesCurrentRequestId() {
        val path = TaskPathPayload(
            taskId = "task",
            pathVersion = 1,
            frameId = "map",
            alignmentYawDeg = 0f,
            points = listOf(TaskPathPointPayload(1f, 2f)),
            requestId = "current-request"
        )

        assertEquals(true, path.matchesReplayPathRequest("current-request"))
        assertEquals(false, path.matchesReplayPathRequest("old-request"))
    }

    @Test
    fun pagesAreMergedByIndexSortedAndDuplicatesAreReplaced() {
        val current = listOf(trajectoryPoint(index = 2, x = 2f))
        val page = TaskTrajectoryPagePayload(
            isSuccess = true,
            message = "",
            points = listOf(
                payloadPoint(index = 3, x = 3f),
                payloadPoint(index = 2, x = 20f),
                payloadPoint(index = 1, x = 1f)
            )
        )

        val merged = mergeTrajectoryPoints(current, page)

        assertEquals(listOf(1, 2, 3), merged.map { it.index })
        assertEquals(20f, merged[1].x, 0f)
    }

    @Test
    fun nextIndexOnlyAdvancesWhenServerReportsMoreData() {
        assertEquals(
            100,
            validatedNextTrajectoryIndex(
                TaskTrajectoryPagePayload(
                    isSuccess = true,
                    message = "",
                    startIndex = 50,
                    nextIndex = 100,
                    hasMore = true
                )
            )
        )
        assertNull(
            validatedNextTrajectoryIndex(
                TaskTrajectoryPagePayload(
                    isSuccess = true,
                    message = "",
                    startIndex = 50,
                    nextIndex = 50,
                    hasMore = true
                )
            )
        )
        assertNull(
            validatedNextTrajectoryIndex(
                TaskTrajectoryPagePayload(
                    isSuccess = true,
                    message = "",
                    startIndex = 50,
                    nextIndex = 100,
                    hasMore = false
                )
            )
        )
    }

    private fun payloadPoint(index: Int, x: Float) = TaskTrajectoryPointPayload(
        index = index,
        offsetMs = index * 1000,
        xMeters = x,
        yMeters = 0f,
        headingDeg = 0f,
        linearSpeedMps = 0f,
        angularSpeedRadps = 0f,
        discSpeedRpm = 0,
        speedAvailable = true,
        discEnabled = false,
        taskStateValue = 0
    )

    private fun trajectoryPoint(index: Int, x: Float) = TrajectoryPoint(
        index = index,
        offsetMs = index * 1000,
        x = x,
        y = 0f,
        headingDeg = 0f,
        speed = 0f,
        angularSpeedRadps = 0f,
        discSpeedRpm = 0,
        speedAvailable = true,
        discEnabled = false,
        taskStateValue = 0
    )
}
