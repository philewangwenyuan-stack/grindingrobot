package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * 地图材料与加载反馈分开保存：某一接口失败不能清空其他已经成功的图层。
 * loading 表示正在等待该接口；error 表示本轮失败，用户重试时只重置失败/未完成部分。
 * 这里不保存小车位置或任务进度，加载状态变化不会暂停设备状态上报。
 */
data class DeviceTaskMapState(
    val snapshot: TaskMapDisplaySnapshot? = null,
    val loadingMap: Boolean = false,
    val loadingRegions: Boolean = false,
    val loadingPath: Boolean = false,
    // 等待开始研磨流程提交候选，不是正在请求地图，也不是等待机器人开始运行。
    val waitingForHandoff: Boolean = false,
    val mapError: String? = null,
    val regionError: String? = null,
    val pathError: String? = null
) {
    // 信息不完整是常驻提示，不是网络错误；重发公共区域请求也不能恢复任务临时禁区。
    val informationWarning: String? get() = snapshot?.takeUnless { it.hasTaskConfiguration }?.let {
        if (it.selectedRegionIds.isEmpty()) "任务显示信息不完整：选区和临时禁区缓存不可用"
        else "任务显示信息不完整：临时禁区缓存不可用"
    }
}

/**
 * 设备页地图加载的单一决策入口，只获取显示数据，不导入地图、不发送任务控制、不定时轮询。
 *
 * 建议阅读顺序：
 * 1. onTask 接收当前任务/路径版本，setVisible 接收页面可见性；缓存变化也会进入 reconcile。
 * 2. reconcile 优先等待交接、接收缓存，然后计算缺失部分，最多启动一个加载 job。
 * 3. loadPath / loadMap / loadRegions 各自更新材料和错误；request 保证先订阅再发送。
 * 4. cancelWait 取消当前等待，generation 防止旧流程收尾时清理新流程的状态。
 *
 * 刷新粒度：换任务重新初始化；路径版本变化只使路径尝试失效；底图和区域是任务快照。
 * map_version、replan_requested 不参与此处调度。实时位置与轨迹由 DeviceStatusViewModel
 * 独立处理，即使缓存命中或加载失败也继续更新。
 * 所有响应流/发送函数/解码器都可注入，测试不需要 Android 位图或真实 TCP 连接。
 */
internal class DeviceTaskMapController(
    private val scope: CoroutineScope,
    private val cache: TaskMapDisplayCache,
    private val frames: Flow<MapImagePayload>,
    private val regions: Flow<MapRegionPointPayload>,
    private val paths: Flow<TaskPathPayload>,
    private val sendMap: (String) -> Boolean,
    private val sendRegions: (String) -> Boolean,
    private val sendPath: (mapId: String, taskId: String, requestId: String) -> Boolean,
    private val decodeSize: (ByteArray) -> Pair<Int, Int>?,
    private val timeoutMs: Long = 30_000L
) {
    private val _state = MutableStateFlow(DeviceTaskMapState())
    val state = _state.asStateFlow()
    // 任务身份来自 0x0504，不从某个规划响应的辅助字段推断正在执行哪个任务。
    private var taskId = ""
    // 上报的路径版本，用于决定是否重新请求；不是本地请求编号，也不作为 JSON 合法性校验。
    private var version = 0
    private var visible = false
    // 本地等待轮次编号，取消时递增；与设备 path_version、协议 request_id 都不同。
    private var generation = 0L
    private var job: Job? = null
    // “已尝试”不等于“成功”：失败后保持标记，避免每条位置上报都重发同一失败请求。
    private var mapAttempted = false
    private var regionsAttempted = false
    // null 表示尚未尝试/需要重试；非 null 记录本轮请求对应的上报版本，失败后也保留。
    private var pathAttemptedVersion: Int? = null
    // 已接收/写回的快照，用内容相等性消除缓存流回声，不是另一个长期历史缓存。
    private var adopted: TaskMapDisplaySnapshot? = null
    private val cacheJob = scope.launch { cache.state.collect { reconcile() } }

    /** 由状态上报驱动；重复上报同一任务/版本只重新检查条件，不代表必须重新发请求。 */
    fun onTask(taskId: String?, pathVersion: Int) {
        val next = taskId.orEmpty()
        if (next != this.taskId) {
            cancelWait()
            if (this.taskId.isNotEmpty()) cache.clearTask(this.taskId)
            this.taskId = next
            mapAttempted = false
            regionsAttempted = false
            pathAttemptedVersion = null
            adopted = null
            _state.value = DeviceTaskMapState(snapshot = next.takeIf { it.isNotEmpty() }?.let(::TaskMapDisplaySnapshot))
        } else if (version != pathVersion) {
            // 保留底图、区域和选区；仅解除路径去重。未完成的加载由 cancelWait 标记为可恢复。
            cancelWait()
            pathAttemptedVersion = null
        }
        version = pathVersion
        reconcile()
    }

    /** 页面隐藏/APP 进入后台时取消等待，保留材料；再次显示后按最新任务和版本补齐。 */
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (!value) cancelWait() else reconcile()
    }

    /** 保留成功材料；失败项和被取消的在途项允许重发，不把重试变成整张地图重新加载。 */
    fun retry() {
        cancelWait()
        if (state.value.mapError != null) mapAttempted = false
        if (state.value.regionError != null) regionsAttempted = false
        if (state.value.pathError != null) pathAttemptedVersion = null
        reconcile()
    }

    /** ViewModel 销毁时释放本控制器订阅，不删除可供后续设备页接收的任务缓存。 */
    fun close() {
        visible = false
        cancelWait()
        cacheJob.cancel()
    }

    /**
     * 只取消 APP 等待，不代表设备已取消规划或停止发送回包。
     * 先增加 generation，再取消 job；旧协程 finally 不得清除新协程的 loading 状态。
     * 路径用 request_id 严格关联；无关联字段的 MapChunk 仍有 loadMap 注释说明的协议限制。
     */
    private fun cancelWait() {
        generation++
        job?.cancel()
        job = null
        // 未完成的请求允许下次可见时重发；失败请求只在重试或版本变化后重发。
        if (state.value.loadingMap) mapAttempted = false
        if (state.value.loadingRegions) regionsAttempted = false
        if (state.value.loadingPath) pathAttemptedVersion = null
        _state.update { it.copy(loadingMap = false, loadingRegions = false, loadingPath = false) }
    }

    /**
     * 统一调度点：缓存通知、状态上报、页面可见性和手动重试都只进入这里。
     * 顺序很重要：先处理交接与缓存，再判断可见性/在途 job，最后计算实际缺失项。
     * 这样隐藏页面仍可接收快照，但不会因此发送请求；完整缓存命中则直接结束。
     */
    private fun reconcile() {
        if (taskId.isEmpty()) return
        val cached = cache.state.value
        // 启动过程中 Preview 可能即将关闭；等待候选提交/撤销，避免与交接重复拉取相同数据。
        if (cached.pending?.matches(taskId) == true) {
            cancelWait()
            _state.update { it.copy(waitingForHandoff = true) }
            return
        }
        _state.update { it.copy(waitingForHandoff = false) }
        // StateFlow 会合并内容相等的快照，不能用对象引用判断“新交接”，否则空路径会触发重复请求。
        cached.active?.takeIf { it.taskId == taskId && it != adopted }?.let { snapshot ->
            cancelWait()
            adopted = snapshot
            _state.value = DeviceTaskMapState(snapshot = snapshot)
            mapAttempted = snapshot.frame != null && snapshot.bitmapSize != null
            regionsAttempted = snapshot.regions?.isSuccess == true
            pathAttemptedVersion = null
        }
        val snapshot = state.value.snapshot ?: return
        val cachedPath = snapshot.path
        // 上报未给出正版本时可使用已有可绘制路径；正版本已知且不一致时才补取新路径。
        // 此处只是缓存选择规则，不会丢弃本轮 request_id 匹配的版本 0 失败响应。
        if (cachedPath?.hasRenderablePoints == true &&
            (version <= 0 || cachedPath.pathVersion == version)) pathAttemptedVersion = version
        if (!visible || job != null) return
        val needPath = pathAttemptedVersion != version
        val needMap = (snapshot.frame == null || snapshot.bitmapSize == null) && !mapAttempted
        val needRegions = snapshot.regions == null && !regionsAttempted
        if (!needMap && !needRegions && !needPath) return
        val round = generation
        val requestedTask = taskId
        val requestedVersion = version
        // 先赋值再启动，避免同步响应/缓存通知重新进入 reconcile 时误以为没有在途 job。
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // 冷启动先拿路径响应中的地图 ID；失败仍继续加载当前底图及公共区域。
                if (needPath) loadPath(requestedTask, requestedVersion)
                if (round != generation) return@launch
                if (needMap) loadMap()
                if (round != generation) return@launch
                if (needRegions) loadRegions()
            } finally {
                // 任务切换或隐藏后，旧协程可能才执行到这里；不能把新 job 的状态清掉。
                if (round == generation) {
                    job = null
                    _state.update { it.copy(loadingMap = false, loadingRegions = false, loadingPath = false) }
                }
            }
        }
        job?.start()
    }

    /** 同时更新页面与共享快照；先记住 adopted，避免自身写回被误认为另一轮缓存交接。 */
    private fun updateSnapshot(transform: (TaskMapDisplaySnapshot) -> TaskMapDisplaySnapshot) {
        val snapshot = state.value.snapshot ?: return
        val next = transform(snapshot)
        _state.update { it.copy(snapshot = next) }
        adopted = next
        cache.update(next)
    }

    /**
     * 每次请求生成独立 ID，只等待本轮结果流，不读取历史成功路径流来结束等待。
     * requestedVersion 用于抑制重复请求；响应是否可显示仍按实际有限坐标判断。
     * 底图尚未获得时，可用响应 mapId 为后续地图/区域请求确定目标地图。
     */
    private suspend fun loadPath(task: String, requestedVersion: Int) {
        val round = generation
        pathAttemptedVersion = requestedVersion
        _state.update { it.copy(loadingPath = true, pathError = null) }
        // 已知路径失效时只清理规划线，不清理地图、区域、实际轨迹和小车。
        updateSnapshot { it.copy(path = null) }
        try {
            val requestId = UUID.randomUUID().toString()
            val mapId = state.value.snapshot?.mapId ?: "LIVE_MAP"
            val path = request(paths, { sendPath(mapId, task, requestId) },
                "路径请求发送失败", "路径规划未返回数据") { it.requestId == requestId }
            updateSnapshot { current -> current.copy(
                mapId = if (current.frame == null) path.mapId.ifBlank { current.mapId } else current.mapId,
                path = path.takeIf { it.hasRenderablePoints },
                // 有任务配置时以用户选区为准；冷启动从路径恢复，失败/无标识时保留已恢复选区。
                // 否则一次空路径就会连带让原本可见的工作区和起终点消失。
                selectedRegionIds = if (current.hasTaskConfiguration) current.selectedRegionIds
                    else path.taskRegionIds().ifEmpty { current.selectedRegionIds }
            ) }
            if (!path.hasRenderablePoints) _state.update { it.copy(pathError = "路径规划未返回数据") }
        } catch (cancelled: CancellationException) {
            // 取消是页面生命周期行为，不显示成请求失败，也不能继续执行后续加载阶段。
            throw cancelled
        } catch (failure: Exception) {
            _state.update { it.copy(pathError = failure.message ?: "路径规划未返回数据") }
        } finally { if (round == generation) _state.update { it.copy(loadingPath = false) } }
    }

    /** 获取一个可解码且具备投影几何的完整帧，然后复制字节冻结；不持续订阅后续地图帧。 */
    private suspend fun loadMap() {
        val round = generation
        mapAttempted = true
        _state.update { it.copy(loadingMap = true, mapError = null) }
        try {
            val mapId = state.value.snapshot?.mapId ?: "LIVE_MAP"
            // MapChunk 无 request_id，且数字 map_id 不能直接与这里的字符串地图 ID 对应。
            // 新订阅可排除历史重放，取消可隔离旧协程；跨请求重叠回包仍需设备提供关联标识才能严格区分。
            val frame = request(frames, { sendMap(mapId) }, "地图请求发送失败", "地图加载超时，请重试")
            val size = decodeSize(frame.imageBytes) ?: throw LoadFailure("地图图片无法解码，请重试")
            if (!frame.hasDisplayGeometry() || size.first <= 0 || size.second <= 0)
                throw LoadFailure("地图几何信息不可用，请重试")
            updateSnapshot { it.copy(frame = frame.copy(imageBytes = frame.imageBytes.copyOf()), bitmapSize = size) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _state.update { it.copy(mapError = failure.message ?: "地图加载失败，请重试") }
        } finally { if (round == generation) _state.update { it.copy(loadingMap = false) } }
    }

    /**
     * 查询地图公共区域，保留完整响应，显示层负责选区过滤；这里不覆盖任务临时禁区。
     * 指定地图时按字符串 mapId 匹配；LIVE_MAP 表示设备当前地图，其响应可能返回实际 ID。
     */
    private suspend fun loadRegions() {
        val round = generation
        regionsAttempted = true
        _state.update { it.copy(loadingRegions = true, regionError = null) }
        try {
            val mapId = state.value.snapshot?.mapId ?: "LIVE_MAP"
            val response = request(regions, { sendRegions(mapId) }, "区域请求发送失败", "区域加载超时，请重试") {
                mapId == "LIVE_MAP" || it.mapId == mapId
            }
            if (!response.isSuccess) throw LoadFailure(response.message.ifBlank { "区域加载失败，请重试" })
            updateSnapshot { it.copy(regions = response) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _state.update { it.copy(regionError = failure.message ?: "区域加载失败，请重试") }
        } finally { if (round == generation) _state.update { it.copy(loadingRegions = false) } }
    }

    /**
     * 通用单轮等待：UNDISPATCHED 使 first 的订阅在 send 之前建立，避免漏掉同步返回。
     * 发送失败立刻取消订阅；超时转成对应接口的错误；外部取消继续向上传播。
     * accept 由接口决定（路径 request_id、区域 mapId）；不清空任何全局共享流。
     */
    private suspend fun <T> request(
        responses: Flow<T>, send: () -> Boolean, sendError: String, timeoutError: String,
        accept: (T) -> Boolean = { true }
    ): T = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeoutMs) { responses.first(accept) }
        }
        if (!send()) { response.cancel(); throw LoadFailure(sendError) }
        response.await() ?: throw LoadFailure(timeoutError)
    }

    private class LoadFailure(message: String) : Exception(message)
}

/** 冷启动只能恢复路径明确提及的工作区，不推断所有地图工作区都参与当前任务。 */
internal fun TaskPathPayload.taskRegionIds(): Set<String> = buildSet {
    points.forEach { if (it.regionId.isNotBlank()) add(it.regionId) }
    segments.forEach { segment ->
        listOf(segment.regionId, segment.fromRegionId, segment.toRegionId).filter { it.isNotBlank() }.forEach(::add)
    }
}

/** 仅检查显示投影必需的几何，避免除零/非有限坐标；不是路径规划结果的业务校验。 */
internal fun MapImagePayload.hasDisplayGeometry(): Boolean =
    mapWidth > 0 && mapHeight > 0 && resolution.isFinite() && resolution > 0 &&
        originX.isFinite() && originY.isFinite() && headingDeg.isFinite() &&
        alignmentYawDeg.isFinite() && rotationAlignmentDeltaDeg.isFinite()
