package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DevicePosePayload(
    val x: Float,
    val y: Float,
    val headingDeg: Float
)

object DeviceStatusStream {
    private val _pose = MutableStateFlow<DevicePosePayload?>(null)
    val pose: StateFlow<DevicePosePayload?> = _pose.asStateFlow()

    fun publishPose(pose: DevicePosePayload) {
        _pose.value = pose
    }

    fun reset() {
        _pose.value = null
    }
}
