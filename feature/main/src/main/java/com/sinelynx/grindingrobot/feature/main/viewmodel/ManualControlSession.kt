package com.sinelynx.grindingrobot.feature.main.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Main-thread session: release bypasses preparation and invalidates its pending target. */
internal class ManualControlSession<T>(
    private val scope: CoroutineScope,
    private val prepare: suspend () -> Boolean,
    private val send: (T) -> Unit,
) {
    private var pending: T? = null
    private var preparation: Job? = null
    var hasRequestedControl: Boolean = false
        private set

    fun request(value: T, moving: Boolean) {
        if (!moving) {
            pending = null
            hasRequestedControl = false
            send(value)
            return
        }
        hasRequestedControl = true
        pending = value
        if (preparation?.isActive == true) return
        preparation = scope.launch {
            if (!prepare()) {
                pending = null
                hasRequestedControl = false
                return@launch
            }
            val target = pending ?: return@launch
            pending = null
            send(target)
        }
    }

    fun cancel() {
        pending = null
        hasRequestedControl = false
        preparation?.cancel()
    }
}
