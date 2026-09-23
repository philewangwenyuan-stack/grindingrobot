package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设备地图模式切换响应（0x0513）。
 *
 * 该响应是进入 LIVE_MAP 建图页面前的前置条件；调用方应先 reset，
 * 再发送 0x0512，避免消费到上一次模式切换的旧响应。
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
    val residualNodes: List<String> = emptyList()
)

object MapModeStream {
    private val _payload = MutableStateFlow<MapModeResponsePayload?>(null)
    val payload: StateFlow<MapModeResponsePayload?> = _payload.asStateFlow()

    fun publish(payload: MapModeResponsePayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
