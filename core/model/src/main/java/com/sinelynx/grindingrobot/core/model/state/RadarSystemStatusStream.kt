package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RadarSystemStatusPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val available: Boolean,
    val status: String,
    val timestampNs: Long
)

object RadarSystemStatusStream {
    private val _responses = MutableSharedFlow<RadarSystemStatusPayload>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<RadarSystemStatusPayload> = _responses.asSharedFlow()

    fun publish(payload: RadarSystemStatusPayload) {
        _responses.tryEmit(payload)
    }
}
