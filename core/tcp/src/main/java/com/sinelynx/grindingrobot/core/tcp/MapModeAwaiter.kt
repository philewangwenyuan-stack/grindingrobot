package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapModeResponsePayload
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Subscribe before sending and accept failures as well as successful starts. */
internal suspend fun awaitMapMode(
    responses: Flow<MapModeResponsePayload>,
    connections: Flow<ConnectionState>,
    timeoutMs: Long,
    send: ((Int) -> Unit) -> Boolean,
): MapModeResponsePayload = withTimeoutOrNull(timeoutMs) {
    coroutineScope {
        val expectedSeq = AtomicInteger(-1)
        val events = Channel<MapModeResponsePayload>(Channel.BUFFERED)
        val replies = launch(start = CoroutineStart.UNDISPATCHED) {
            responses.collect { response ->
                if (response.requestSeq == expectedSeq.get()) events.send(response)
            }
        }
        val connection = launch(start = CoroutineStart.UNDISPATCHED) {
            connections.collect {
                if (it != ConnectionState.CONNECTED) events.close(IOException("数据连接已断开，请重试"))
            }
        }
        try {
            if (!send { expectedSeq.set(it) }) throw IOException("地图模式请求发送失败")
            events.receive()
        } finally {
            replies.cancel()
            connection.cancel()
            events.cancel()
        }
    }
} ?: throw IOException("地图模式切换超时，请重试")
