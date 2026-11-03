package com.sinelynx.grindingrobot.core.model.state

/** TaskState wire values from sl_link.proto; workMode=AUTO also includes READY. */
data class ManualTaskGate(val known: Boolean, val requiresTakeover: Boolean, val taskId: String = "") {
    val canDriveWithoutTakeover: Boolean get() = known && !requiresTakeover
}

fun TaskStatusReportPayload?.manualTaskGate(): ManualTaskGate {
    if (this == null) return ManualTaskGate(false, false)
    return when (stateValue) {
        0, 1, 5, 6, 7 -> ManualTaskGate(true, false) // IDLE, READY, COMPLETED, STOPPED, ERROR
        2, 3, 4 -> ManualTaskGate(true, true, taskId.trim()) // PLANNING, RUNNING, PAUSED
        else -> ManualTaskGate(false, false)
    }
}
