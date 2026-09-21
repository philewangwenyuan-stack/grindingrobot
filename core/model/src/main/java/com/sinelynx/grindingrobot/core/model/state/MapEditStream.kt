package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapEditPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val mapVersion: Int
)

object MapEditStream {
    private val _payload = MutableStateFlow<MapEditPayload?>(null)
    val payload: StateFlow<MapEditPayload?> = _payload.asStateFlow()

    fun publish(payload: MapEditPayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
