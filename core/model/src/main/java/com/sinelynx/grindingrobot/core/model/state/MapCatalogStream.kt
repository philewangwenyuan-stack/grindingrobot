package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapCatalogItemPayload(
    val mapId: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: String,
    val totalWorkAreaM2: Float,
    val estimatedTimeS: Float,
    val base64Image: String,
    val saveAt: Long
)

class MapCatalogPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val totalCount: Int,
    val items: List<MapCatalogItemPayload>
)

object MapCatalogStream {
    private val _payload = MutableStateFlow<MapCatalogPayload?>(null)
    val payload: StateFlow<MapCatalogPayload?> = _payload.asStateFlow()

    fun publish(payload: MapCatalogPayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
