package com.sinelynx.grindingrobot.feature.common.control

import com.sinelynx.grindingrobot.core.model.state.ManualTaskGate

data class ManualTakeoverState(val taskId: String? = null, val pending: Boolean = false) {
    val visible: Boolean get() = taskId != null
}

/** UI-thread owner. A confirmation never resumes the gesture that opened it. */
class ManualTakeoverCoordinator(
    private val snapshot: () -> ManualTaskGate,
    private val stopTask: suspend (String) -> Boolean,
    private val stopMotion: () -> Unit,
    private val changed: (ManualTakeoverState) -> Unit,
) {
    var state = ManualTakeoverState()
        private set
    private var generation = 0
    private var approvedTaskId: String? = null
    private var lastActiveTaskId: String? = null

    private fun publish(value: ManualTakeoverState) { state = value; changed(value) }

    fun needsConfirmation(): Boolean {
        val task = snapshot()
        return task.known && task.requiresTakeover && task.taskId != approvedTaskId
    }

    fun canPrepareManual(): Boolean {
        val task = snapshot()
        return task.known && !state.visible &&
            (!task.requiresTakeover || (task.taskId.isNotBlank() && task.taskId == approvedTaskId))
    }

    fun request(): Boolean {
        val task = snapshot()
        if (!task.known || !task.requiresTakeover || task.taskId.isBlank()) return false
        stopMotion()
        publish(ManualTakeoverState(task.taskId))
        return true
    }

    fun windowBlurred() {
        // Own Dialog blurs its host too. Always zero motion, retain confirmation.
        stopMotion()
    }

    fun observeTask() {
        val task = snapshot()
        val activeId = if (task.known && task.requiresTakeover) task.taskId else null
        if (!task.known || (activeId != null && activeId != lastActiveTaskId)) cancel()
        if (activeId == null) approvedTaskId = null
        lastActiveTaskId = activeId
    }

    fun cancel() {
        generation++
        approvedTaskId = null
        stopMotion()
        publish(ManualTakeoverState())
    }

    /** null means superseded/cancelled; false means a current takeover failed. */
    suspend fun confirm(): Boolean? {
        val id = state.taskId ?: return null
        if (state.pending) return null
        val task = snapshot()
        if (!task.known || (task.requiresTakeover && task.taskId != id)) {
            cancel(); return null
        }
        val token = generation
        publish(state.copy(pending = true))
        val accepted = if (task.requiresTakeover) stopTask(id) else true
        if (token != generation) return null
        val latest = snapshot()
        if (!latest.known || (latest.requiresTakeover && latest.taskId != id)) {
            cancel(); return null
        }
        if (!accepted) { cancel(); return false }
        approvedTaskId = if (latest.requiresTakeover) id else null
        stopMotion()
        publish(ManualTakeoverState())
        return true
    }
}
