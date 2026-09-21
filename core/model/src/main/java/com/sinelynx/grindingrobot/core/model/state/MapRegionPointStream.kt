package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 区域接口原样返回的地图坐标；不从多边形外接矩形或规划路径首尾推导起终点。 */
data class MapRegionPosePayload(val x: Float, val y: Float, val headingDeg: Float)

/** 起终点为 null 表示接口未提供可用姿态；(0, 0) 本身不是“不可用”的标记。 */
data class WorkRegionPointPayload(
    val region: MapPreviewRegionPayload,
    val startPose: MapRegionPosePayload?,
    val endPose: MapRegionPosePayload?
)

data class MapRegionPointPayload(
    val mapId: String,
    val mapVersion: Int,
    val isSuccess: Boolean,
    val message: String,
    val workRegions: List<WorkRegionPointPayload>,
    val obstacleRegions: List<MapPreviewRegionPayload>,
    val eraseRegions: List<MapPreviewRegionPayload>,
    // 保留裁剪区供后续业务使用；当前建图页面不新增裁剪区图层。
    val cropRegion: MapPreviewRegionPayload?
)

/** 不重放历史响应；调用方必须先订阅再发请求，避免收到旧区域或漏掉立即返回的响应。 */
object MapRegionPointStream {
    private val _responses = MutableSharedFlow<MapRegionPointPayload>(extraBufferCapacity = 16)
    val responses = _responses.asSharedFlow()
    fun publish(payload: MapRegionPointPayload) { _responses.tryEmit(payload) }
}
