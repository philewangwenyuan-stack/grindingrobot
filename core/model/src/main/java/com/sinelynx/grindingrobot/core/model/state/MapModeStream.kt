package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 设备地图模式切换响应（0x0513）。
 *
 * payload 保留最近状态；请求等待使用 responses，并按帧 ack_seq 匹配本次请求。
 */
data class MapModeResponsePayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val modeValue: Int,
    val enabled: Boolean,
    val mapKind: Int,
    val lifecycleState: String = "",
    val activeMapId: String = "",
    val activeMapRevision: String = "",
    val residualNodes: List<String> = emptyList(),
    val requestSeq: Int? = null
)

object MapModeStream {
    private val _payload = MutableStateFlow<MapModeResponsePayload?>(null)
    val payload: StateFlow<MapModeResponsePayload?> = _payload.asStateFlow()
    private val _responses = MutableSharedFlow<MapModeResponsePayload>(extraBufferCapacity = 32)
    val responses = _responses.asSharedFlow()

    fun publish(payload: MapModeResponsePayload) {
        _payload.value = payload
        _responses.tryEmit(payload)
    }

    fun reset() {
        _payload.value = null
    }
}
