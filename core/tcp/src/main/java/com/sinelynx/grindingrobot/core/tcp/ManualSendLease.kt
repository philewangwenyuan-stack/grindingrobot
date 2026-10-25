package com.sinelynx.grindingrobot.core.tcp

import java.util.concurrent.atomic.AtomicLong

/** Latest movement plus a stop barrier that survives a subsequent fresh press. */
internal class ManualSendLease {
    private val revision = AtomicLong()
    private val stopEpoch = AtomicLong()
    data class Ticket(val revision: Long, val epoch: Long, val deadlineMs: Long, val stop: Boolean)
    @Synchronized fun issue(nowMs: Long, stop: Boolean): Ticket {
        if (stop) stopEpoch.incrementAndGet()
        return Ticket(revision.incrementAndGet(), stopEpoch.get(),
            if (stop) Long.MAX_VALUE else nowMs + 500L, stop)
    }
    @Synchronized fun valid(ticket: Ticket, nowMs: Long) = ticket.epoch == stopEpoch.get() &&
        (ticket.stop || ticket.revision == revision.get()) && nowMs <= ticket.deadlineMs
    @Synchronized fun cancel() { revision.incrementAndGet(); stopEpoch.incrementAndGet() }
}
