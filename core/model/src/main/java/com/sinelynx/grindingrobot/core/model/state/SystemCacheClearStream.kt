package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SystemCacheClearPayload(
    val resultValue: Int,
    val isSuccess: Boolean,
    val message: String,
    val memoryCacheCleared: Boolean,
    val temporaryFilesCleared: Int,
    val temporaryBytesReleased: Long,
    val logFilesCleared: Int,
    val logBytesReleased: Long,
    val failedItems: Int
)

/** 系统缓存清理响应事件（LOWER -> APP，MSG 0x0537）。 */
object SystemCacheClearStream {
    private val _responses = MutableSharedFlow<SystemCacheClearPayload>(extraBufferCapacity = 16)
    val responses: SharedFlow<SystemCacheClearPayload> = _responses.asSharedFlow()

    fun publish(payload: SystemCacheClearPayload) {
        _responses.tryEmit(payload)
    }
}
