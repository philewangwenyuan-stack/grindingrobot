package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RadarRelocalizationStatusPayload(
    val rawStatus: String,
    val timestampNs: Long,
    val lifecycleState: String = "",
    val detail: String = "",
    val mapId: String = "",
    val mapRevision: String = "",
    val goodFrames: Int = 0,
    val requiredFrames: Int = 0,
    val registrationQualityValid: Boolean = false,
    val registrationFitness: Float = Float.NaN,
    val registrationInlierRatio: Float = Float.NaN,
    val residualNodes: List<String> = emptyList()
)

object RadarRelocalizationStatusStream {
    private val _responses = MutableSharedFlow<RadarRelocalizationStatusPayload>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<RadarRelocalizationStatusPayload> = _responses.asSharedFlow()

    fun publish(payload: RadarRelocalizationStatusPayload) {
        _responses.tryEmit(payload)
    }
}
