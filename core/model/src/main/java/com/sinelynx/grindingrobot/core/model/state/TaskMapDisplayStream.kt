package com.sinelynx.grindingrobot.core.model.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 开始研磨与设备页之间交接的“显示材料”，生命周期不依赖 Preview 是否仍然打开。
 *
 * 阅读链路：StartGrindingSessionController.exportTaskDisplay 导出材料，MapHomeViewModel
 * 在启动流程中 stage/commit，DeviceTaskMapController 按当前任务接收或补齐缺失材料。
 * 此模型也承载冷启动时逐步加载的结果，所以底图、区域、路径允许分别为空。
 *
 * 不包含运行状态、进度和实时位置；这些始终来自设备上报，不能由快照推断。
 * 只保存于内存，不是地图编辑缓存或任务执行凭证，APP 重启后需要重新获取显示数据。
 */
data class TaskMapDisplaySnapshot(
    // 业务任务 ID 与请求中的字符串地图 ID；后者不是 MapImagePayload.mapId 的数字编号。
    val taskId: String,
    val mapId: String = "LIVE_MAP",
    // frame 保留原始地图几何；bitmapSize 是解码后的图片尺寸，二者可能不同，投影需要同时使用。
    val frame: MapImagePayload? = null,
    val bitmapSize: Pair<Int, Int>? = null,
    // 保留完整区域响应（包括暂不绘制的裁剪区）；绘制时才按 selectedRegionIds 过滤工作区。
    val regions: MapRegionPointPayload? = null,
    val selectedRegionIds: Set<String> = emptySet(),
    // 本轮任务提交的临时禁区，公共区域查询无法恢复这些数据；空列表也可能是合法配置。
    val temporaryObstacles: List<TaskObstacleRegionConfig> = emptyList(),
    val path: TaskPathPayload? = null,
    // true 表示选区/临时禁区来自本轮配置；false 表示冷启动恢复，不能把公共区域当作任务配置。
    // 这是数据来源标记，不表示底图、路径已经加载完成，也不表示任务已启动。
    val hasTaskConfiguration: Boolean = false
) {
    /**
     * 复制图片字节及可变集合容器，避免源页面清理草稿或调用方改写数组影响设备页。
     * 集合内的点、姿态、分段均为只读值对象，可以共享；无需复制 Bitmap 或持有页面对象。
     */
    fun detachedCopy(): TaskMapDisplaySnapshot = copy(
        frame = frame?.let { it.copy(imageBytes = it.imageBytes.copyOf()) },
        regions = regions?.let { value ->
            value.copy(
                workRegions = value.workRegions.map { it.copy(region = it.region.detachedCopy()) },
                obstacleRegions = value.obstacleRegions.map { it.detachedCopy() },
                eraseRegions = value.eraseRegions.map { it.detachedCopy() },
                cropRegion = value.cropRegion?.detachedCopy()
            )
        },
        selectedRegionIds = selectedRegionIds.toSet(),
        temporaryObstacles = temporaryObstacles.map { it.copy(points = it.points.toList()) },
        path = path?.let { it.copy(points = it.points.toList(), segments = it.segments.toList()) }
    )
}

private fun MapPreviewRegionPayload.detachedCopy() = copy(points = points.toList())

/** 尚在启动确认流程中的候选；token 标识一次交接尝试，而不是协议 request_id。 */
data class PendingTaskMapDisplay(val token: String, val inputTaskId: String, val snapshot: TaskMapDisplaySnapshot) {
    // 同时识别页面输入 ID 与预览配置确认后的 ID，覆盖启动过程中上报使用任一 ID 的情况。
    fun matches(taskId: String) = taskId == inputTaskId || taskId == snapshot.taskId
}

/** active 是可消费的显示快照；pending 只用于阻止设备页在交接中抢先补发请求。 */
data class TaskMapDisplayCacheState(
    val active: TaskMapDisplaySnapshot? = null,
    val pending: PendingTaskMapDisplay? = null
)

/**
 * 最多保留一份已交接快照和一份启动候选，不按历史任务无限累计。
 *
 * 状态流：stage 创建 pending → commit 转为 active；失败/取消则 discard 撤销 pending。
 * 设备页补齐数据调用 update；任务切换或结束调用 clearTask，仅清理匹配的 active。
 * 修改入口同步执行，避免“读取当前候选 → 校验 token → 写回”被其他线程交错打断。
 *
 * 注意：commit 只说明 APP 可以交接显示数据，不替代 TASK_STATE_RUNNING 等设备状态。
 */
class TaskMapDisplayCache {
    private val _state = MutableStateFlow(TaskMapDisplayCacheState())
    val state = _state.asStateFlow()

    /** 关闭编辑弹窗之前调用；替换候选但保留已有 active，直到新启动流程提交结果。 */
    @Synchronized fun stage(inputTaskId: String, snapshot: TaskMapDisplaySnapshot): String {
        val token = UUID.randomUUID().toString()
        _state.value = _state.value.copy(pending = PendingTaskMapDisplay(token, inputTaskId, snapshot.detachedCopy()))
        return token
    }

    /** 只提交仍有效的候选；旧启动协程即使晚完成，也不能提交后来创建的候选。 */
    @Synchronized fun commit(token: String, confirmedTaskId: String) {
        val pending = _state.value.pending?.takeIf { it.token == token } ?: return
        val snapshot = pending.snapshot
        _state.value = TaskMapDisplayCacheState(active = snapshot.copy(
            taskId = confirmedTaskId,
            // 最终配置 ID 改变时可以复用地图/配置，但不能把旧任务路径伪装成新任务路径。
            path = snapshot.path.takeIf { snapshot.taskId == confirmedTaskId }
        ))
    }

    /** 可在完成回调中无条件调用；成功 commit 后 pending 已消失，此操作不会删除 active。 */
    @Synchronized fun discard(token: String) {
        if (_state.value.pending?.token == token) _state.value = _state.value.copy(pending = null)
    }

    /** 设备页写回补齐的显示材料；启动交接中或已有其他任务的 active 时不接受覆盖。 */
    @Synchronized fun update(snapshot: TaskMapDisplaySnapshot) {
        if (_state.value.pending != null) return
        // 旧任务的后台响应不能覆盖刚提交的新任务交接，即使 0x0504 尚未切换任务 ID。
        if (_state.value.active?.taskId?.let { it != snapshot.taskId } == true) return
        _state.value = _state.value.copy(active = snapshot)
    }

    /** 只清理对应任务的正式缓存，不撤销启动候选，也不清空其他页面的共享响应流。 */
    @Synchronized fun clearTask(taskId: String) {
        if (_state.value.active?.taskId == taskId) _state.value = _state.value.copy(active = null)
    }
}

/** APP 内跨 ViewModel 共享的入口；独立于 MapBuildSessionStream 和首页预览缓存。 */
object TaskMapDisplayStream {
    val cache = TaskMapDisplayCache()
}
