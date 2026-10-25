package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import sl_link.SlLink

/** A single CPU worker, at most two waiting responses and 64MiB including the active JSON.
 * Submission never suspends the frame receiver. Overload ends that request with an explicit empty
 * result instead of silently dropping it. Reset fences even an already-running non-cancellable parse.
 */
internal class TaskPathDecodeQueue(
    scope: CoroutineScope,
    private val publish: (TaskPathPayload) -> Unit,
    private val decode: suspend (ByteArray, SlLink.PathPointPlanResponse) -> TaskPathPayload = { bytes, response ->
        TaskPathJsonParser.parse(bytes, response)
    },
    queueCapacity: Int = 2,
    private val maxPendingBytes: Long = 64L * 1024 * 1024
) {
    private data class Work(val generation: Long, val bytes: ByteArray, val response: SlLink.PathPointPlanResponse)
    private val lock = Any()
    private val queue = Channel<Work>(queueCapacity)
    private var generation = 0L
    private var pendingBytes = 0L
    private val worker = scope.launch {
        for (work in queue) {
            val payload = try {
                decode(work.bytes, work.response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                TaskPathJsonParser.emptyPayload(work.response, "路径数据解析失败，请重试")
            }
            synchronized(lock) {
                pendingBytes -= work.bytes.size
                if (work.generation == generation) publish(payload)
            }
        }
    }

    fun submit(bytes: ByteArray, response: SlLink.PathPointPlanResponse): Boolean = synchronized(lock) {
        // Do not retain a second copy of a potentially single-chunk JSON through its protobuf envelope.
        val metadata = response.toBuilder().clearData().build()
        if (bytes.size.toLong() <= maxPendingBytes - pendingBytes) {
            pendingBytes += bytes.size
            if (queue.trySend(Work(generation, bytes, metadata)).isSuccess) return@synchronized true
            pendingBytes -= bytes.size
        }
        publish(TaskPathJsonParser.emptyPayload(metadata, "路径数据处理繁忙，请重试"))
        false
    }

    fun reset() = synchronized(lock) {
        generation++
        while (true) {
            val work = queue.tryReceive().getOrNull() ?: break
            pendingBytes -= work.bytes.size
        }
    }

    fun close() {
        reset()
        queue.close()
        worker.cancel()
    }
}
