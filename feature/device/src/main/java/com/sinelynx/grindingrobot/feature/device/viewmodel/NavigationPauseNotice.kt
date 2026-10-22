package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.data.state.AppState

data class NavigationPauseNotice(val title: String, val message: String)

/** One prompt per protection episode; acknowledgment never sends pause/resume/control commands. */
internal class NavigationPauseNoticeTracker {
    private var task = ""
    private var activeCauses = emptySet<String>()

    fun update(taskId: String, state: DeviceTaskUiState, runtime: AppState.RuntimeTaskState,
               hasCurrentReport: Boolean): NavigationPauseNotice? {
        if (taskId != task) {
            task = taskId
            activeCauses = emptySet()
        }
        if (!hasCurrentReport || taskId.isBlank() ||
            state !in setOf(DeviceTaskUiState.RUNNING, DeviceTaskUiState.PAUSED)) {
            activeCauses = emptySet()
            return null
        }
        val causes = buildSet {
            if (runtime.localizationPauseLatched == true) add("localization")
            if (runtime.collisionImminent) add("obstacle")
        }
        val hasNewCause = causes.any { it !in activeCauses }
        activeCauses = causes
        if (!hasNewCause) return null
        val details = buildList {
            if ("localization" in causes) add(
                "定位保护已触发，任务已暂停。请检查雷达定位，恢复正常后确认周围环境，再点击继续任务。"
            )
            if ("obstacle" in causes) add(
                "检测到障碍物，导航已触发避障停车。请检查并清除障碍物；若任务仍处于暂停状态，请确认安全后点击继续任务。"
            )
        }
        return NavigationPauseNotice(
            title = when {
                causes.size > 1 -> "导航暂停提示"
                "localization" in causes -> "定位保护暂停"
                else -> "避障停车提示"
            },
            message = details.joinToString("\n\n")
        )
    }
}
