package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PathPlanPayload(
    val resultValue: Int,
    val message: String,
    val requestId: String,
    val taskId: String,
    val totalWorkAreaM2: Float,
    val estimatedTimeS: Float,
    val mapVersion: Int,
    val pathVersion: Int,
    val pathPointCount: Int,
    val pathLengthM: Float,
    val planned: Boolean,
    val pathChunked: Boolean,
    val previewImageBytes: ByteArray?,
    val previewFormat: String,
    val width: Int,
    val height: Int,
    val resolution: Float,
    val originX: Double?,
    val originY: Double?,
    val headingDeg: Float?,
    val frameId: String,
    val previewScaleX: Float,
    val previewScaleY: Float,
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
)

object PathPlanStream {
    private val _payload = MutableStateFlow<PathPlanPayload?>(null)
    val payload: StateFlow<PathPlanPayload?> = _payload.asStateFlow()

    fun publish(payload: PathPlanPayload) {
        _payload.value = payload
    }

    fun reset() {
        _payload.value = null
    }
}

