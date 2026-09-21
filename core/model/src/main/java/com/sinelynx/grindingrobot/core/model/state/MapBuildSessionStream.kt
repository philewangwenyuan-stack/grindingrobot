package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** sessionId 区分每次进入 Step1；requestedMapId 是页面查询参数，不等同于协议中的数值 mapId。 */
data class FrozenMapSnapshot(
    val sessionId: Long,
    val requestedMapId: String,
    val frame: MapImagePayload
)

/**
 * Step2–4 共用的会话底图，只允许 Step1 在“下一步”时主动冻结。
 * TCP 的实时帧只更新 MapImageStream，不会覆盖此快照；取消建图或保存完成后须显式清理。
 */
object MapBuildSessionStream {
    private var sessionId = 0L
    private val _snapshot = MutableStateFlow<FrozenMapSnapshot?>(null)
    val snapshot = _snapshot.asStateFlow()

    fun begin() {
        sessionId++
        clear()
    }

    /** 页面先确认图片可解码；这里校验投影所需几何，并复制字节以隔离后续帧的数据修改。 */
    fun freeze(mapId: String?, frame: MapImagePayload): Boolean {
        if (frame.imageBytes.isEmpty() || frame.mapWidth <= 0 || frame.mapHeight <= 0 ||
            !frame.resolution.isFinite() || frame.resolution <= 0f ||
            !frame.originX.isFinite() || !frame.originY.isFinite() || !frame.headingDeg.isFinite() ||
            !frame.alignmentYawDeg.isFinite() || !frame.rotationAlignmentDeltaDeg.isFinite()
        ) return false
        _snapshot.value = FrozenMapSnapshot(sessionId, mapId.orEmpty(), frame.copy(imageBytes = frame.imageBytes.copyOf()))
        return true
    }

    // null 与空字符串均表示当前地图，不能把其他地图页面的快照当作兜底数据。
    fun snapshotFor(mapId: String?): FrozenMapSnapshot? =
        _snapshot.value?.takeIf { it.requestedMapId == mapId.orEmpty() }

    /** 只更新显示角度，不旋转图片字节或修改原点；总角写入后清零增量，避免后续页面重复叠加。 */
    fun updateRotation(mapId: String?, totalRotationDeg: Float) {
        if (!totalRotationDeg.isFinite()) return
        val current = snapshotFor(mapId) ?: return
        _snapshot.value = current.copy(frame = current.frame.copy(
            alignmentYawDeg = totalRotationDeg,
            rotationAlignmentDeltaDeg = 0f
        ))
    }

    fun clear() { _snapshot.value = null }
}
