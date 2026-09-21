package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapImportToRadarPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String
)

object MapImportToRadarStream {
    // 保留旧状态接口；研磨会话先订阅事件再发送，避免复用上一次导入结果。
    private val _responses = MutableSharedFlow<MapImportToRadarPayload>(extraBufferCapacity = 16)
    val responses = _responses.asSharedFlow()
    private val _payload = MutableStateFlow<MapImportToRadarPayload?>(null)
    val payload: StateFlow<MapImportToRadarPayload?> = _payload.asStateFlow()

    fun publish(payload: MapImportToRadarPayload) {
        _responses.tryEmit(payload)
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}
