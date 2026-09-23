package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RadarMapSyncResponsePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val sent: Boolean
)

object RadarMapSyncResponseStream {
    private val _responses = MutableSharedFlow<RadarMapSyncResponsePayload>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<RadarMapSyncResponsePayload> = _responses.asSharedFlow()

    fun publish(payload: RadarMapSyncResponsePayload) {
        _responses.tryEmit(payload)
    }
}

data class RadarRelocalizationResponsePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val accepted: Boolean,
    val status: String,
    val lifecycleState: String = "",
    val mapId: String = "",
    val mapRevision: String = ""
)

object RadarRelocalizationResponseStream {
    private val _responses = MutableSharedFlow<RadarRelocalizationResponsePayload>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<RadarRelocalizationResponsePayload> = _responses.asSharedFlow()

    fun publish(payload: RadarRelocalizationResponsePayload) {
        _responses.tryEmit(payload)
    }
}
