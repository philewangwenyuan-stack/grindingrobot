package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapRegionMetricsPayload(
    val regionId: String,
    val regionName: String,
    val repeat: Int,
    val areaM2: Float,
    val estimatedTimeH: Float
)

class MapMetricsPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val mapId: String,
    val mapName: String,
    val regions: List<MapRegionMetricsPayload>
)

object MapMetricsStream {
    private val _payload = MutableStateFlow<MapMetricsPayload?>(null)
    val payload: StateFlow<MapMetricsPayload?> = _payload.asStateFlow()

    fun publish(payload: MapMetricsPayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
