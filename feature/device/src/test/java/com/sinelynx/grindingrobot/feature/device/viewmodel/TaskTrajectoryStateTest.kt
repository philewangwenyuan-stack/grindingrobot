package com.sinelynx.grindingrobot.feature.device.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备页实时轨迹纯状态测试，与底图缓存及规划路径加载独立。
 * 同任务追加、切换任务丢弃旧点、空闲/停止清理；重复点不追加，超过容量只保留最近点。
 * 验证列表更新，不验证地图请求次数或屏幕坐标；坐标链路见 TaskTrajectoryGeometryTest。
 */
class TaskTrajectoryStateTest {

    @Test
    fun sameTaskAppendsNewPose() {
        val first = pose(1f)
        val second = pose(2f)

        val result = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "task-a",
            taskState = DeviceTaskUiState.RUNNING,
            currentTrajectory = listOf(first),
            pose = second
        )

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun changedTaskDropsOldTrajectoryAndKeepsNewFirstPose() {
        val newFirstPose = pose(9f)

        val result = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "task-b",
            taskState = DeviceTaskUiState.RUNNING,
            currentTrajectory = listOf(pose(1f), pose(2f)),
            pose = newFirstPose
        )

        assertEquals(listOf(newFirstPose), result)
    }

    @Test
    fun blankTaskIdOrIdleStateClearsTrajectory() {
        val current = listOf(pose(1f))

        val blankTaskResult = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "",
            taskState = DeviceTaskUiState.RUNNING,
            currentTrajectory = current,
            pose = pose(2f)
        )
        val idleResult = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "task-a",
            taskState = DeviceTaskUiState.IDLE,
            currentTrajectory = current,
            pose = pose(2f)
        )

        assertTrue(blankTaskResult.isEmpty())
        assertTrue(idleResult.isEmpty())
    }

    @Test
    fun duplicatePoseIsNotAppendedAndMaximumSizeIsKept() {
        val first = pose(1f)
        val duplicateResult = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "task-a",
            taskState = DeviceTaskUiState.RUNNING,
            currentTrajectory = listOf(first),
            pose = first
        )
        val limitedResult = nextTaskTrajectory(
            currentTaskId = "task-a",
            reportTaskId = "task-a",
            taskState = DeviceTaskUiState.RUNNING,
            currentTrajectory = listOf(first, pose(2f)),
            pose = pose(3f),
            maxPoints = 2
        )

        assertEquals(listOf(first), duplicateResult)
        assertEquals(listOf(pose(2f), pose(3f)), limitedResult)
    }

    @Test
    fun successfulStopStateClearsTrajectoryImmediately() {
        val state = DeviceStatusUiState(
            taskState = DeviceTaskUiState.RUNNING,
            navigationStatus = "研磨中",
            trajectory = listOf(pose(1f))
        )

        val result = state.afterTaskStopped()

        assertEquals(DeviceTaskUiState.IDLE, result.taskState)
        assertEquals("空闲", result.navigationStatus)
        assertTrue(result.trajectory.isEmpty())
    }

    private fun pose(x: Float) = TaskPoseUiItem(
        x = x,
        y = 0f,
        headingDeg = 0f,
        lineColorArgb = 0
    )
}
