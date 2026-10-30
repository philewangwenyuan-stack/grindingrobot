package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskHistoryRequestGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

internal fun matchesTaskHistoryQuery(
    payload: TaskExecutionHistoryPayload,
    mapId: String = "",
    taskId: String = "",
    startTime: Long = 0L,
    endTime: Long = 0L
): Boolean =
    (payload.mapId == mapId && payload.taskId == taskId &&
        payload.startTime == startTime && payload.endTime == endTime) ||
        (!payload.isSuccess && payload.mapId.isBlank() && payload.taskId.isBlank() &&
            payload.startTime == 0L && payload.endTime == 0L)

/** One history assembler serves all screens. Own it only for the network request. */
internal suspend fun awaitTaskHistory(
    replies: Flow<TaskExecutionHistoryPayload?>,
    connection: StateFlow<ConnectionState>,
    timeoutMs: Long = 10_000L,
    accepts: (TaskExecutionHistoryPayload) -> Boolean,
    reset: () -> Unit,
    send: () -> Boolean
): TaskExecutionHistoryPayload {
    fun failure(message: String) = TaskExecutionHistoryPayload(false, message)
    return try {
        withTimeoutOrNull(timeoutMs) {
            TaskHistoryRequestGate.withRequest(Any()) {
                if (connection.value != ConnectionState.CONNECTED) {
                    return@withRequest failure("设备连接已断开")
                }
                reset()
                coroutineScope {
                    // Subscribe before sending, including when a reply is delivered immediately.
                    val response = async(start = CoroutineStart.UNDISPATCHED) {
                        replies.filterNotNull().first(accepts)
                    }
                    val disconnected = async(start = CoroutineStart.UNDISPATCHED) {
                        connection.first { it != ConnectionState.CONNECTED }
                        failure("设备连接已断开")
                    }
                    try {
                        if (!send()) failure("任务历史请求发送失败")
                        else select {
                            response.onAwait { it }
                            disconnected.onAwait { it }
                        }
                    } finally {
                        response.cancel()
                        disconnected.cancel()
                    }
                }
            }
        } ?: failure("任务历史查询超时，请重试")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failure(error.message ?: "任务历史查询失败")
    }
}
