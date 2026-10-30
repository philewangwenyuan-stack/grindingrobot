package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** History chunk assembly is shared, so background and foreground readers must serialize requests. */
object TaskHistoryRequestGate {
    private val mutex = Mutex()

    /** Wait in order; cancellation while queued or receiving cannot leave an abandoned owner. */
    suspend fun <T> withRequest(candidate: Any, block: suspend () -> T): T =
        mutex.withLock(candidate) { block() }
}
