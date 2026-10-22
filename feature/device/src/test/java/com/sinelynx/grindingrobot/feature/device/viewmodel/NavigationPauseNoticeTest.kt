package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.data.state.AppState
import org.junit.Assert.*
import org.junit.Test

class NavigationPauseNoticeTest {
    @Test fun manualPauseAndIdleDoNotProduceProtectionPrompts() {
        val tracker = NavigationPauseNoticeTracker()
        assertNull(tracker.update("task", DeviceTaskUiState.PAUSED, AppState.RuntimeTaskState(), true))
        assertNull(tracker.update("task", DeviceTaskUiState.IDLE,
            AppState.RuntimeTaskState(collisionImminent = true), true))
        assertNull(tracker.update("task", DeviceTaskUiState.RUNNING,
            AppState.RuntimeTaskState(localizationPauseLatched = true), false))
    }

    @Test fun confirmedLocalizationPauseIsShownOnceUntilItClears() {
        val tracker = NavigationPauseNoticeTracker()
        val failed = AppState.RuntimeTaskState(localizationReady = false, localizationPauseLatched = true)
        assertEquals("定位保护暂停", tracker.update("task", DeviceTaskUiState.RUNNING, failed, true)?.title)
        assertNull(tracker.update("task", DeviceTaskUiState.PAUSED, failed, true))
        assertNull(tracker.update("task", DeviceTaskUiState.PAUSED, failed.copy(localizationReady = true), true))
        tracker.update("task", DeviceTaskUiState.RUNNING, failed.copy(localizationPauseLatched = false), true)
        assertNotNull(tracker.update("task", DeviceTaskUiState.PAUSED, failed, true))
    }

    @Test fun obstacleStopCanPrecedeSchedulerPauseAndDoesNotRepeat() {
        val tracker = NavigationPauseNoticeTracker()
        val obstacle = AppState.RuntimeTaskState(collisionImminent = true)
        assertEquals("避障停车提示", tracker.update("task", DeviceTaskUiState.RUNNING, obstacle, true)?.title)
        assertNull(tracker.update("task", DeviceTaskUiState.PAUSED, obstacle, true))
        tracker.update("task", DeviceTaskUiState.RUNNING, obstacle.copy(collisionImminent = false), true)
        assertNotNull(tracker.update("task", DeviceTaskUiState.RUNNING, obstacle, true))
    }

    @Test fun newCauseOrDifferentTaskCanPromptAgain() {
        val tracker = NavigationPauseNoticeTracker()
        val obstacle = AppState.RuntimeTaskState(collisionImminent = true)
        tracker.update("task", DeviceTaskUiState.RUNNING, obstacle, true)
        val both = obstacle.copy(localizationPauseLatched = true)
        assertEquals("导航暂停提示", tracker.update("task", DeviceTaskUiState.PAUSED, both, true)?.title)
        assertNotNull(tracker.update("new-task", DeviceTaskUiState.RUNNING, both, true))
    }
}
