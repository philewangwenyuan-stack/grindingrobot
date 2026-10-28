package com.sinelynx.grindingrobot.core.tcp

import sl_link.SlLink

/** Routing uses the command, not just its shared message ID. */
internal object ChannelRouting {
    fun control(frame: ByteArray): Boolean {
        if (frame.size < 18) return false
        return when (id(frame)) {
            0x0401 -> true
            0x0502 -> runCatching {
                val request = SlLink.TaskCommand.parseFrom(frame.copyOfRange(15, frame.size - 3))
                request.command == SlLink.TaskCommandType.TASK_CMD_STOP ||
                    request.command == SlLink.TaskCommandType.TASK_CMD_PAUSE
            }.getOrDefault(false)
            else -> false
        }
    }
    fun id(frame: ByteArray): Int = word(frame, 11)
    fun seq(frame: ByteArray): Int = word(frame, 4)
    private fun word(frame: ByteArray, at: Int): Int =
        (frame[at].toInt() and 255) or ((frame[at + 1].toInt() and 255) shl 8)
}
