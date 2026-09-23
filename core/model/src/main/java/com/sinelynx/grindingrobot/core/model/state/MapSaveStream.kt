package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapSavePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val mapId: String,
    val mapYamlPath: String,
    val mapImagePath: String,
    val navigationMapReloaded: Boolean,
    val assetRevision: String = "",
    val lifecycleState: String = "",
    val mappingStopped: Boolean = false,
    val localizationStarted: Boolean = false,
    val residualNodes: List<String> = emptyList()
)

object MapSaveStream {
    private val _payload = MutableStateFlow<MapSavePayload?>(null)
    val payload: StateFlow<MapSavePayload?> = _payload.asStateFlow()

    fun publish(payload: MapSavePayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
