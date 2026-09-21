package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** x/y 按原始 map 坐标使用；NaN 占位保留断点，绘制时不能先过滤再连接两侧点。 */
data class TaskPathPointPayload(
    val x: Float,
    val y: Float,
    val yaw: Float = 0f,
    val index: Int = -1,
    val pathScope: String = "",
    val pathCategory: String = "",
    val regionId: String = "",
    val lapIndex: Int = 0
)

/** start/endPointIndex 是点 index 的闭区间，不要求等于 points 数组下标；pointCount 仅为摘要。 */
data class TaskPathSegmentPayload(
    val segmentIndex: Int,
    val pathScope: String,
    val pathCategory: String,
    val regionId: String,
    val lapIndex: Int,
    val fromRegionId: String,
    val toRegionId: String,
    val startPointIndex: Int,
    val endPointIndex: Int,
    val pointCount: Int
)

data class TaskPathPayload(
    val taskId: String,
    val pathVersion: Int,
    val frameId: String,
    val alignmentYawDeg: Float,
    val points: List<TaskPathPointPayload>,
    val segments: List<TaskPathSegmentPayload> = emptyList(),
    val planned: Boolean = true,
    val result: String = "success",
    val message: String = "",
    val pathPointCount: Int = points.size,
    val pathLengthM: Float = 0f,
    // 缺失统计与数值 0 含义不同：Step4 留空展示，仅在兼容旧数据库保存时转换为 0。
    val totalWorkAreaM2: Float? = null,
    val estimatedTimeS: Float? = null,
    // 请求关联只取响应外层 request_id，不能由路径 JSON 的辅助字段覆盖。
    val requestId: String = "",
    val mapId: String = "",
    val mapVersion: Int = 0
) {
    // 只判断是否存在有效坐标；实际是否落在底图范围内由绘制层的投影函数决定。
    val hasRenderablePoints: Boolean get() = points.any { it.x.isFinite() && it.y.isFinite() }
}

object TaskPathStream {
    // 本轮结果包含空路径/失败，不重放；Step4 等待此流，不读取旧的成功路径缓存。
    private val _results = MutableSharedFlow<TaskPathPayload>(extraBufferCapacity = 16)
    val results = _results.asSharedFlow()
    private val _pathsByTaskId = MutableStateFlow<Map<String, TaskPathPayload>>(emptyMap())
    val pathsByTaskId: StateFlow<Map<String, TaskPathPayload>> = _pathsByTaskId.asStateFlow()

    fun publish(payload: TaskPathPayload) {
        // 先通知本轮调用者：失败常携带版本 0，不能被下方旧页面缓存的版本过滤吞掉。
        _results.tryEmit(payload)
        if (!payload.hasRenderablePoints) return
        // 历史缓存只保留有有效点的较新版本，供其他页面继续沿用既有读取方式。
        val current = _pathsByTaskId.value[payload.taskId]
        if (current != null && current.pathVersion > payload.pathVersion) return
        _pathsByTaskId.value = _pathsByTaskId.value + (payload.taskId to payload)
    }

    fun reset(taskId: String) {
        _pathsByTaskId.value = _pathsByTaskId.value - taskId
    }

    fun resetAll() {
        _pathsByTaskId.value = emptyMap()
    }
}
