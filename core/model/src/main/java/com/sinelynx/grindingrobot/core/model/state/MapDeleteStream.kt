package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapDeletePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val deleted: Boolean,
    val mapId: String,
    val localDeleted: Boolean = false,
    val remoteDeleted: Boolean = false,
    val remoteDeletePending: Boolean = false
)

object MapDeleteStream {
    private val _payload = MutableStateFlow<MapDeletePayload?>(null)
    val payload: StateFlow<MapDeletePayload?> = _payload.asStateFlow()

    fun publish(payload: MapDeletePayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}

