package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一帧完整地图及其协议元数据。mapWidth/mapHeight 为响应声明尺寸，实际图片尺寸需解码获取。
 * resolution 用于米与地图像素换算；previewScaleX/Y 原样保留，不代表页面手势缩放倍率。
 */
data class MapImagePayload(
    val imageBytes: ByteArray,
    val mapWidth: Int,
    val mapHeight: Int,
    val resolution: Float,
    val originX: Double,
    val originY: Double,
    val headingDeg: Float,
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f,
    val mapId: Int = 0,
    val mapVersion: Int = 0,
    val frameId: String = "map",
    val previewScaleX: Float = 1f,
    val previewScaleY: Float = 1f,
    val appRotationDeg: Float = 0f
) {
    // StateFlow 依赖相等性判断更新：图片按内容比较，几何或显示角变化也必须通知页面。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapImagePayload) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (mapWidth != other.mapWidth) return false
        if (mapHeight != other.mapHeight) return false
        if (resolution != other.resolution) return false
        if (originX != other.originX) return false
        if (originY != other.originY) return false
        if (headingDeg != other.headingDeg) return false
        if (alignmentYawDeg != other.alignmentYawDeg) return false
        if (rotationAlignmentDeltaDeg != other.rotationAlignmentDeltaDeg) return false
        if (mapId != other.mapId || mapVersion != other.mapVersion || frameId != other.frameId) return false
        if (previewScaleX != other.previewScaleX || previewScaleY != other.previewScaleY) return false
        if (appRotationDeg != other.appRotationDeg) return false
        return true
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mapWidth
        result = 31 * result + mapHeight
        result = 31 * result + resolution.hashCode()
        result = 31 * result + originX.hashCode()
        result = 31 * result + originY.hashCode()
        result = 31 * result + headingDeg.hashCode()
        result = 31 * result + alignmentYawDeg.hashCode()
        result = 31 * result + rotationAlignmentDeltaDeg.hashCode()
        result = 31 * result + mapId
        result = 31 * result + mapVersion
        result = 31 * result + frameId.hashCode()
        result = 31 * result + previewScaleX.hashCode()
        result = 31 * result + previewScaleY.hashCode()
        result = 31 * result + appRotationDeg.hashCode()
        return result
    }
}

object MapImageStream {
    // 完整帧事件不重放、不按内容去重，单次请求即使得到相同图片也能结束等待。
    private val _frames = MutableSharedFlow<MapImagePayload>(extraBufferCapacity = 16)
    val frames = _frames.asSharedFlow()
    /** 重进 Step1 时同时清空旧图片和元数据，避免新帧到来前冻结上一次会话的数据。 */
    fun reset() {
        _payload.value = null
        _mapImageBytes.value = null
    }
    private val _mapImageBytes = MutableStateFlow<ByteArray?>(null)
    val mapImageBytes: StateFlow<ByteArray?> = _mapImageBytes.asStateFlow()
    private val _payload = MutableStateFlow<MapImagePayload?>(null)
    val payload: StateFlow<MapImagePayload?> = _payload.asStateFlow()

    fun publish(bytes: ByteArray) {
        _mapImageBytes.value = bytes
    }

    fun publish(payload: MapImagePayload) {
        _frames.tryEmit(payload)
        _payload.value = payload
        _mapImageBytes.value = payload.imageBytes
    }
}
