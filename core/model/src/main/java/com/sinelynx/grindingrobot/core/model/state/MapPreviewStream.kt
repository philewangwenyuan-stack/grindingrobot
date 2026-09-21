package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapPreviewPointPayload(
    val x: Float,
    val y: Float
)

data class MapPreviewRegionPayload(
    val regionId: String,
    val regionName: String,
    val points: List<MapPreviewPointPayload>,
    val enabled: Boolean = true
)

/**
 * 地图预览（0x0508 MapPreviewResponse）完整载荷：图像字节 + 地理元数据。
 * 与 [MapImageStream]（地图快照分片组包）分流，避免互相覆盖。
 */
class MapPreviewPayload(
    val imageBytes: ByteArray,
    val mapWidth: Int,
    val mapHeight: Int,
    val resolution: Float,
    val originX: Double,
    val originY: Double,
    val headingDeg: Float,
    val mapVersion: Int,
    val previewScaleX: Float,
    val previewScaleY: Float,
    val workRegions: List<MapPreviewRegionPayload>,
    val obstacleRegions: List<MapPreviewRegionPayload> = emptyList(),
    val eraseRegions: List<MapPreviewRegionPayload> = emptyList(),
    val isSuccess: Boolean = true,
    val message: String = "",
    val alignmentYawDeg: Float = 0f,
    val rotationAlignmentDeltaDeg: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapPreviewPayload) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (mapWidth != other.mapWidth) return false
        if (mapHeight != other.mapHeight) return false
        if (resolution != other.resolution) return false
        if (originX != other.originX) return false
        if (originY != other.originY) return false
        if (headingDeg != other.headingDeg) return false
        if (mapVersion != other.mapVersion) return false
        if (previewScaleX != other.previewScaleX) return false
        if (previewScaleY != other.previewScaleY) return false
        if (workRegions != other.workRegions) return false
        if (obstacleRegions != other.obstacleRegions) return false
        if (eraseRegions != other.eraseRegions) return false
        if (isSuccess != other.isSuccess) return false
        if (message != other.message) return false
        if (alignmentYawDeg != other.alignmentYawDeg) return false
        if (rotationAlignmentDeltaDeg != other.rotationAlignmentDeltaDeg) return false
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
        result = 31 * result + mapVersion
        result = 31 * result + previewScaleX.hashCode()
        result = 31 * result + previewScaleY.hashCode()
        result = 31 * result + workRegions.hashCode()
        result = 31 * result + obstacleRegions.hashCode()
        result = 31 * result + eraseRegions.hashCode()
        result = 31 * result + isSuccess.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + alignmentYawDeg.hashCode()
        result = 31 * result + rotationAlignmentDeltaDeg.hashCode()
        return result
    }
}

object MapPreviewStream {
    private val _payload = MutableStateFlow<MapPreviewPayload?>(null)
    val payload: StateFlow<MapPreviewPayload?> = _payload.asStateFlow()

    fun publish(payload: MapPreviewPayload) {
        _payload.value = payload
    }

    /** 发起新一次预览请求前清空，便于 [first] 只等待本轮响应。 */
    fun reset() {
        _payload.value = null
    }
}
