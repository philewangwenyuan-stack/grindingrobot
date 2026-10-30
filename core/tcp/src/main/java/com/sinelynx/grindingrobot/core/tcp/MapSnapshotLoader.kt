package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapRequestOutcome
import com.sinelynx.grindingrobot.core.model.state.MapRequestResultPayload
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owns one snapshot attempt at a time, including failure events and cancellation. */
internal class MapSnapshotLoader(
    private val frames: Flow<MapImagePayload>,
    private val results: Flow<MapRequestResultPayload>,
    private val send: (String, Long) -> Boolean,
    private val cancel: (Long) -> Unit,
    private val nextId: () -> Long = {
        (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
    },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private sealed interface Event {
        data class Frame(val value: MapImagePayload) : Event
        data class Result(val value: MapRequestResultPayload) : Event
    }

    private sealed interface Attempt {
        data class Ready(val frame: MapImagePayload) : Attempt
        data class NotReady(val result: MapRequestResultPayload) : Attempt
    }

    suspend fun load(mapId: String, timeoutMs: Long): MapImagePayload =
        withTimeoutOrNull(timeoutMs) {
            repeat(3) { retry ->
                when (val result = attempt(mapId, nextId())) {
                    is Attempt.Ready -> return@withTimeoutOrNull result.frame
                    is Attempt.NotReady -> {
                        if (retry == 2) throw IOException(
                            result.result.message.ifBlank { "设备地图尚未就绪，请稍后重试" })
                        pause(result.result.retryAfterMs.toLong().coerceIn(250L, 2_000L))
                    }
                }
            }
            error("Snapshot attempts exhausted")
        } ?: throw IOException("地图加载超时，请重试")

    private suspend fun attempt(mapId: String, requestId: Long): Attempt = coroutineScope {
        require(requestId != 0L)
        val events = Channel<Event>(Channel.BUFFERED)
        // Both collectors must be subscribed before a synchronous/fast response arrives.
        val frameJob = launch(start = CoroutineStart.UNDISPATCHED) {
            frames.collect { if (it.requestId == requestId) events.send(Event.Frame(it)) }
        }
        val resultJob = launch(start = CoroutineStart.UNDISPATCHED) {
            results.collect { if (it.requestId == requestId) events.send(Event.Result(it)) }
        }
        try {
            if (!send(mapId, requestId)) throw IOException("地图请求发送失败或通道忙，请重试")
            while (true) {
                when (val event = events.receive()) {
                    is Event.Frame -> return@coroutineScope Attempt.Ready(event.value)
                    is Event.Result -> when (event.value.outcome) {
                        MapRequestOutcome.READY -> Unit // READY is metadata, not a complete image.
                        MapRequestOutcome.NOT_READY -> return@coroutineScope Attempt.NotReady(event.value)
                        MapRequestOutcome.NOT_FOUND -> throw IOException(
                            event.value.message.ifBlank { "找不到保存的地图，请刷新地图列表" })
                        MapRequestOutcome.ERROR -> throw IOException(
                            event.value.message.ifBlank { "地图加载失败，请重试" })
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Snapshot receive loop exited")
        } finally {
            frameJob.cancel()
            resultJob.cancel()
            events.cancel()
            // Cancels only this ID, never a newer request owned by another page.
            cancel(requestId)
        }
    }
}
