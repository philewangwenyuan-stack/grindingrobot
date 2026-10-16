package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.tcp.DeviceCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class EmergencyReleaseUiState(
    val pending: Boolean = false,
    val title: String? = null,
    val message: String? = null,
    val feedback: String? = null,
    val released: Boolean = false
)

/** Only device confirmation completes release; a new emergency invalidates the waiting UI. */
internal class EmergencyReleaseController(
    private val scope: CoroutineScope,
    private val execute: suspend () -> DeviceCommandResult,
    private val publish: (EmergencyReleaseUiState) -> Unit
) {
    private var generation = 0L
    private var pending = false
    private var job: Job? = null

    fun request() {
        if (pending) return
        pending = true
        val requestGeneration = ++generation
        publish(EmergencyReleaseUiState(pending = true, feedback = "解除急停：等待确认"))
        job = scope.launch {
            try {
                val result = execute()
                if (requestGeneration == generation) publish(emergencyReleaseOutcome(result))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestGeneration == generation) publish(EmergencyReleaseUiState(
                    title = "解除急停未确认", message = "请求失败：${error.message.orEmpty()}", feedback = "解除急停：请求失败"
                ))
            } finally {
                if (requestGeneration == generation) pending = false
            }
        }
    }

    fun cancelForEmergencyStop() {
        generation++
        job?.cancel()
        job = null
        pending = false
        publish(EmergencyReleaseUiState())
    }
}

internal fun emergencyReleaseOutcome(result: DeviceCommandResult): EmergencyReleaseUiState = when (result) {
    is DeviceCommandResult.Confirmed -> EmergencyReleaseUiState(
        title = "解除急停请求已确认",
        message = "设备保持停止，需要重新点击启动。" + result.message.takeIf { it.isNotBlank() }?.let { "\n\n板端返回：$it" }.orEmpty(),
        feedback = "解除急停：已确认",
        released = true
    )
    is DeviceCommandResult.Rejected -> EmergencyReleaseUiState(
        title = "解除急停被拒绝", message = result.message.ifBlank { "板端拒绝解除，请检查实体急停和停车反馈。" }, feedback = "解除急停：被拒绝"
    )
    DeviceCommandResult.SendFailed -> EmergencyReleaseUiState(
        title = "解除急停发送失败", message = "无法发送请求，请检查 APP 与工控机的连接。", feedback = "解除急停：发送失败"
    )
    DeviceCommandResult.TimedOut -> EmergencyReleaseUiState(
        title = "解除急停未收到确认", message = "未收到板端回复，解除结果未知，请核对设备状态。不会自动重试或启动任务。", feedback = "解除急停：未确认"
    )
}
