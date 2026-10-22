package com.sinelynx.grindingrobot.core.model.state

/** History chunk assembly is shared, so background and foreground readers must serialize requests. */
object TaskHistoryRequestGate {
    private var owner: Any? = null

    @Synchronized fun acquire(candidate: Any): Boolean {
        if (owner != null) return false
        owner = candidate
        return true
    }

    @Synchronized fun release(candidate: Any) {
        if (owner === candidate) owner = null
    }
}
