package com.sinelynx.grindingrobot.core.tcp

import kotlinx.coroutines.CompletableDeferred
import com.sinelynx.grindingrobot.core.model.state.TaskRuntimeSettingsPayload

sealed interface DeviceCommandResult {
    data class Confirmed(val message: String, val runtimeSettings: TaskRuntimeSettingsPayload? = null) : DeviceCommandResult
    data class Rejected(val message: String) : DeviceCommandResult
    data object SendFailed : DeviceCommandResult
    data object TimedOut : DeviceCommandResult
}

internal data class CommandReply(
    val resultValue: Int, val message: String,
    val runtimeSettings: TaskRuntimeSettingsPayload? = null
)

/** Register before sending; never infer an action from taskId when several requests are pending. */
internal class CommandReplyTracker {
    data class Ticket(
        val seq: Int,
        val targetId: Int,
        val taskId: String,
        val reply: CompletableDeferred<CommandReply> = CompletableDeferred()
    )

    private val pending = mutableMapOf<Int, Ticket>()
    private val ambiguousUnsequenced = mutableSetOf<Pair<Int, String>>()

    @Synchronized
    fun register(frame: ByteArray, taskId: String): Ticket {
        // SL-Link header: little-endian seq at 4..5, destination at 9.
        val seq = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        check(seq !in pending) { "SL-Link sequence still pending: $seq" }
        val ticket = Ticket(seq, frame[9].toInt() and 0xff, taskId)
        if (pending.values.any { it.targetId == ticket.targetId && it.taskId == taskId }) {
            ambiguousUnsequenced.add(ticket.targetId to taskId)
        }
        pending[seq] = ticket
        return ticket
    }

    @Synchronized
    fun complete(
        ackSeq: Int,
        sourceId: Int,
        taskId: String?,
        reply: CommandReply,
        allowUnsequenced: Boolean = true,
        requireExactAck: Boolean = false
    ): Ticket? {
        val exact = pending[ackSeq]?.takeIf {
            it.targetId == sourceId && (taskId == null || taskId.isBlank() || it.taskId == taskId)
        }
        // Some older firmware leaves ackSeq at zero. Only a unique matching request is safe.
        // Sequence zero is valid too; when ambiguous, wait for authoritative status instead.
        val candidates = pending.values.filter {
            it.targetId == sourceId && (taskId == null || it.taskId == taskId)
        }
        val ticket = if (requireExactAck) exact else if (ackSeq == 0) {
            val candidate = exact ?: candidates.singleOrNull()
            if (!allowUnsequenced || candidates.size > 1 || candidate?.let {
                    (it.targetId to it.taskId) in ambiguousUnsequenced
                } == true) null else candidate
        } else exact
        ticket ?: return null
        pending.remove(ticket.seq)
        ticket.reply.complete(reply)
        return ticket
    }

    @Synchronized
    fun remove(ticket: Ticket) {
        if (pending[ticket.seq] === ticket) {
            // A late ack=0 reply from a timed-out request must not confirm its retry.
            if (!ticket.reply.isCompleted) ambiguousUnsequenced.add(ticket.targetId to ticket.taskId)
            pending.remove(ticket.seq)
        }
    }

    @Synchronized
    fun resetConnection() {
        val tickets = pending.values.toList()
        pending.clear()
        ambiguousUnsequenced.clear()
        tickets.forEach { it.reply.complete(CommandReply(-1, "连接已断开")) }
    }
}
