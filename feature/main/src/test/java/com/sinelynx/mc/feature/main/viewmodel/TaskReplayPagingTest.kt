package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPagePayload
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPointPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReplayPagingTest {
    @Test
    fun historyHasNoPendingReplanAndExplainsMissingArchive() {
        val state = TaskReplayUiState()
        assertEquals(false, state.isPathLoading)
        assertNull(state.plannedPath)
        assertEquals("该记录未提供归档规划路径，仅显示实际轨迹", state.pathError)
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
