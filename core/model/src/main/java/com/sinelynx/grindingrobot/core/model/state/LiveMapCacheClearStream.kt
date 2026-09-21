package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMapCacheClearPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String
)

object LiveMapCacheClearStream {
    private val _payload = MutableStateFlow<LiveMapCacheClearPayload?>(null)
    val payload: StateFlow<LiveMapCacheClearPayload?> = _payload.asStateFlow()

    fun publish(payload: LiveMapCacheClearPayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
