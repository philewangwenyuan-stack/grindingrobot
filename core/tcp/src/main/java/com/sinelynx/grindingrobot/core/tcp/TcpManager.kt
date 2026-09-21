package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskObstacleRegionConfig
import com.sinelynx.grindingrobot.core.model.state.TaskRegionRepeatConfig
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sl_link.SlLink
import sl_link.SlLink.ControlCommand
import sl_link.SlLink.MapEditCommand
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

internal fun buildTaskConfig(
    taskId: String?,
    taskName: String,
    mapId: String,
    regionRepeats: List<TaskRegionRepeatConfig>,
    obstacles: List<TaskObstacleRegionConfig>
): SlLink.TaskConfig {
    require(taskName.isNotBlank()) { "请输入任务名称" }
    val build = SlLink.TaskConfig.newBuilder()
        .setTaskId(taskId.orEmpty())
        .setTaskName(taskName.trim())
        .setMapId(mapId)
    regionRepeats
        .filter { it.regionId.isNotBlank() }
        .forEach { region ->
            build.addSelectedWorkRegionIds(region.regionId)
            build.addRegionRepeats(
                SlLink.RegionRepeatItem.newBuilder()
                    .setRegionId(region.regionId)
                    .setRepeat(region.repeat.coerceAtLeast(1))
                    .build()
            )
        }
    obstacles.forEach { obstacle ->
        val regionBuilder = SlLink.PolygonRegion.newBuilder()
            .setRegionId(obstacle.regionId)
            .setName(obstacle.name)
            .setRegionType(SlLink.RegionType.REGION_TYPE_OBSTACLE)
            .setEnabled(true)
            .setClosed(true)
            .setPriority(10)
        obstacle.points.forEach { point ->
            regionBuilder.addPoints(
                SlLink.PolygonPoint.newBuilder()
                    .setX(point.x)
                    .setY(point.y)
                    .build()
            )
        }
        build.addObstacleRegions(regionBuilder.build())
    }
    return build.build()
}

internal fun buildTaskExecutionHistoryRequest(
    mapId: String? = null,
    taskId: String? = null,
    startTime: Long = 0L,
    endTime: Long = 0L,
    maxChunkSize: Int = 4096
): SlLink.TaskExecutionHistoryRequest = SlLink.TaskExecutionHistoryRequest.newBuilder()
    .setMapId(mapId.orEmpty())
    .setTaskId(taskId.orEmpty())
    .setStartTime(startTime)
    .setEndTime(endTime)
    .setMaxChunkSize(maxChunkSize.coerceAtLeast(1))
    .build()

internal fun buildTaskTrajectoryRequest(
    executionId: String? = null,
    taskId: String? = null,
    startTime: Long = 0L,
    endTime: Long = 0L,
    startIndex: Int = 0,
    maxPoints: Int = 14_400,
    sampleStep: Int = 1,
    maxChunkSize: Int = 4096
): SlLink.TaskTrajectoryRequest = SlLink.TaskTrajectoryRequest.newBuilder()
    .setExecutionId(executionId.orEmpty())
    .setTaskId(taskId.orEmpty())
    .setStartTime(startTime)
    .setEndTime(endTime)
    .setStartIndex(startIndex.coerceAtLeast(0))
    .setMaxPoints(maxPoints.coerceAtLeast(1))
    .setSampleStep(sampleStep.coerceAtLeast(1))
    .setMaxChunkSize(maxChunkSize.coerceAtLeast(1))
    .build()

internal fun buildTaskExecutionDeleteRequest(
    executionId: String
): SlLink.TaskExecutionDeleteRequest = SlLink.TaskExecutionDeleteRequest.newBuilder()
    .setExecutionId(executionId)
    .build()

internal fun buildSystemCacheClearRequest(
    clearMemoryCache: Boolean,
    clearTemporaryFiles: Boolean,
    clearLogs: Boolean
): SlLink.SystemCacheClearRequest? {
    if (!clearMemoryCache && !clearTemporaryFiles && !clearLogs) return null
    return SlLink.SystemCacheClearRequest.newBuilder()
        .setClearMemoryCache(clearMemoryCache)
        .setClearTemporaryFiles(clearTemporaryFiles)
        .setClearLogs(clearLogs)
        .build()
}

/**
 * 构造 Step3 保存工作区的 0x0509 MapEditCommand，不在此处发送网络请求。
 * 扫描方向写入 region.global_direction 字符串；X/-X/Y/-Y 的负号直接传给设备，不转换为角度。
 * 这不是 0x0505 路径请求的可选 global_direction 参数，两条链路分别负责区域配置和发起规划。
 * 独立为纯构造函数，方便用 Protobuf 编解码测试验证方向、区域 ID 和姿态均按原值传递。
 */
internal fun buildWorkspaceUpsertCommand(
    editId: String,
    mapId: String,
    regionId: String,
    areaCode: String,
    points: List<Pair<Float, Float>>,
    startPose: Pair<Float, Float>?,
    endPose: Pair<Float, Float>?,
    globalDirection: String
): MapEditCommand {
    // Locale.ROOT 保证协议字符串不受系统语言影响；只规范大小写，保留负号，未知值回退 X。
    val normalizedDirection = globalDirection.uppercase(java.util.Locale.ROOT)
        .takeIf { it in setOf("X", "-X", "Y", "-Y") } ?: "X"
    val region = SlLink.PolygonRegion.newBuilder()
        .setRegionId(regionId)
        .setName("wr_$areaCode")
        .setRegionType(SlLink.RegionType.REGION_TYPE_WORK)
        .setEnabled(true)
        .setClosed(true)
        .setPriority(10)
        .setGlobalDirection(normalizedDirection)
        .apply {
            points.forEach { (x, y) ->
                addPoints(SlLink.PolygonPoint.newBuilder().setX(x).setY(y))
            }
        }
        .build()
    return MapEditCommand.newBuilder()
        .setEditId(editId)
        .setMapId(mapId)
        .setOperation(SlLink.MapEditOperation.MAP_EDIT_OP_UPSERT_WORK_REGION)
        .setRegionName(region.name)
        .setRegion(region)
        .apply {
            startPose?.let { (x, y) ->
                setStartPose(SlLink.Pose2D.newBuilder().setX(x).setY(y).setHeadingDeg(0f))
            }
            endPose?.let { (x, y) ->
                setEndPose(SlLink.Pose2D.newBuilder().setX(x).setY(y).setHeadingDeg(0f))
            }
        }
        .build()
}

internal fun buildDeleteRegionCommand(
    editId: String,
    mapId: String,
    regionId: String,
    regionType: SlLink.RegionType
): MapEditCommand = MapEditCommand.newBuilder()
    .setEditId(editId)
    .setMapId(mapId)
    .setOperation(SlLink.MapEditOperation.MAP_EDIT_OP_DELETE_REGION)
    .setTargetRegionId(regionId)
    .setTargetRegionType(regionType)
    .build()

/**
 * TCP管理器
 * 提供给上层应用使用的TCP连接管理封装
 * 集成SL-Link协议解析，处理粘包、拆包等问题
 *
 * @param tcpService TCP服务
 * @param slLinkManager SL-Link协议管理器
 * @param appState 应用全局状态
 * @author Dreamj
 */
class TcpManager @Inject constructor(
    private val tcpService: TcpService,
    private val slLinkManager: SlLinkManager,
    private val appState: AppState
) {
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 重连任务Job
    private var reconnectJob: Job? = null

    // 重连延迟时间（5秒）
    private val reconnectDelayMs = 5000L

    // 记录最后一次连接参数
    private var lastHost: String? = null
    private var lastPort: Int? = null
    private var skipNextReconnect = false

    init {
        slLinkManager.setOnRadarMapSyncResponse {
            val sent = requestMapSnapshot()
            if (!sent) {
                LogUtils.w("雷达地图同步响应后请求地图发送失败")
            }
        }

        // 设置回调
        tcpService.setCallback(object : TcpService.TcpCallback {
            override fun onConnected() {
                LogUtils.d("TCP连接已建立")
                // 连接成功，取消重连任务
                cancelReconnect()
            }

            override fun onDataReceived(data: ByteArray, arrivalMonotonicS: Double) {
                val hexString = data.joinToString("") { "%02x".format(it) }
//                LogUtils.d("APP收到Hex: $hexString")
                // 使用SlLinkManager解析数据，自动处理粘包拆包
                try {
                    val frames = slLinkManager.handleReceivedData(data, arrivalMonotonicS)
//                    LogUtils.d("APP解析到 ${frames.size} 个完整帧")
                } catch (e: Exception) {
                    LogUtils.e("解析数据帧失败: ${e.message}")
                }
            }

            override fun onDisconnected(reason: String) {
                LogUtils.d("TCP连接已断开: $reason")
                // 清除设备状态，更新系统状态为连接错误
                appState.clearDeviceStatus()
                // 断开后启动重连任务
                if (skipReconnectOnce()) {
                    return
                }
                scheduleReconnect()
            }

            override fun onFailure(error: Throwable) {
                LogUtils.d("TCP连接失败: ${error.message}")
                // 清除设备状态，更新系统状态为连接错误
                appState.clearDeviceStatus()
                // 失败后启动重连任务
                scheduleReconnect()
            }

            override fun onHeartbeatSent() {
//                LogUtils.d("心跳已发送")
            }
        })
    }

    /**
     * 连接到TCP服务器
     *
     * @param host 服务器地址
     * @param port 服务器端口
     */
    fun connect(host: String, port: Int) {
        // 记录连接参数
        lastHost = host
        lastPort = port
        // 取消之前的重连任务
        cancelReconnect()
        // 连接
        tcpService.connect(host, port)
    }

    /**
     * 异步连接TCP服务器
     * 
     * @param host 服务器地址
     * @param port 服务器端口
     * @return 连接是否成功
     */
    suspend fun connectAsync(host: String, port: Int): Boolean {
        // 记录连接参数
        lastHost = host
        lastPort = port
        // 取消之前的重连任务
        cancelReconnect()

//        setHeartbeatData(slLinkManager.buildTestFrame(0xFFu)) 心跳由嵌入式主动发送
        return tcpService.connectAsync(host, port)
    }

    suspend fun connectConfiguredEndpoint(forceReconnect: Boolean = true): Boolean {
        return connectEndpoint(appState.resolveTcpEndpoint(), forceReconnect)
    }

    suspend fun switchTcpEndpoint(host: String, port: Int): Boolean {
        return switchTcpEndpoint(AppState.TcpEndpoint(host, port))
    }

    suspend fun switchTcpEndpoint(endpoint: AppState.TcpEndpoint): Boolean {
        appState.setTcpEndpoint(endpoint)
        return connectEndpoint(appState.resolveTcpEndpoint(), forceReconnect = true)
    }

    suspend fun connectEndpoint(
        endpoint: AppState.TcpEndpoint,
        forceReconnect: Boolean = true
    ): Boolean {
        val host = endpoint.host.trim()
        val port = endpoint.port
        val isSameEndpoint = lastHost == host && lastPort == port
        if (!forceReconnect && isSameEndpoint && tcpService.isConnected()) {
            return true
        }

        cancelReconnect()
        lastHost = host
        lastPort = port

        if (tcpService.isConnected() || forceReconnect) {
            skipNextReconnect = true
            tcpService.disconnect()
        }

        return tcpService.connectAsync(host, port)
    }

    /**
     * 获取连接状态Flow
     */
    fun getConnectionStateFlow(): StateFlow<ConnectionState> {
        return tcpService.connectionState
    }

    /**
     * 发送数据
     *
     * @param data 要发送的数据
     * @return 发送是否成功
     */
    fun sendData(data: ByteArray): Boolean {
        return if (tcpService.isConnected()) {
            val success = tcpService.sendData(data)
            if (success) {
                val hex = data.joinToString(separator = " ") { byte -> "%02X".format(byte) }
                LogUtils.d("数据发送成功: $hex")
            } else {
                LogUtils.d("数据发送失败")
            }
            success
        } else {
            LogUtils.d("TCP未连接，无法发送数据")
            false
        }
    }

    /**
     * 发送文本消息
     *
     * @param message 文本消息
     * @return 发送是否成功
     */
    fun sendText(message: String): Boolean {
        return sendData(message.toByteArray(Charsets.UTF_8))
    }

    /**
     * 设置心跳数据
     *
     * @param data 心跳数据
     */
    fun setHeartbeatData(data: ByteArray) {
        tcpService.setHeartbeatData(data)
    }

    /**
     * 设置心跳文本
     *
     * @param message 心跳文本
     */
    fun setHeartbeatText(message: String) {
        setHeartbeatData(message.toByteArray(Charsets.UTF_8))
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        tcpService.disconnect()
    }

    /**
     * 获取连接状态
     *
     * @return 连接状态描述
     */
    fun getConnectionStatus(): String {
        return tcpService.getConnectionStatus()
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    fun isConnected(): Boolean {
        return tcpService.isConnected()
    }

    /**
     * 手动重连
     */
    fun reconnect() {
        tcpService.reconnect()
    }

    /**
     * 设置自定义回调
     *
     * @param callback 回调接口
     */
    fun setCallback(callback: TcpService.TcpCallback) {
        tcpService.setCallback(callback)
    }

    /**
     * 释放资源
     */
    fun release() {
        // 取消重连任务
        cancelReconnect()
        // 取消协程作用域
        scope.cancel()
        // 释放TCP服务
        tcpService.release()
    }

    /**
     * 请求地图快照
     */
    fun requestMapSnapshot(mapId: String? = null, dstId: UByte = 0x10u): Boolean {
        val builder = SlLink.MapRequest.newBuilder()
        builder.maxChunkSize = 4096
        if (!mapId.isNullOrBlank()) {
            builder.mapId = mapId
        }
        val frameData = slLinkManager.requestMap(builder.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 请求设备切换地图模式（0x0512）。
     * 建图入口应等待对应的 0x0513 成功响应后，再请求 LIVE_MAP 快照。
     */
    fun requestMapMode(
        mode: SlLink.MapModeType,
        enabled: Boolean,
        mapKind: Int = 0,
        dstId: UByte = 0x10u
    ): Boolean {
        val request = SlLink.MapModeRequest.newBuilder()
            .setMode(mode)
            .setEnabled(enabled)
            .setMapKind(mapKind)
            .build()
        val frameData = slLinkManager.requestMapMode(request.toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 停止当前建图模式（0x0512 enabled=false）。
     * 板端收到后负责停止/kill Super-LIO 建图节点及其关联进程。
     */
    fun stopMappingMode(dstId: UByte = 0x10u): Boolean {
        return requestMapMode(
            mode = SlLink.MapModeType.MAP_MODE_MAPPING,
            enabled = false,
            mapKind = 0,
            dstId = dstId
        )
    }

    /**
     * 擦除地图点云（清除障碍物）
     */
    fun sendMapEditClearRegion(
        editId: String,
        mapId: String? = null,
        points: List<Pair<Float, Float>>,
        dstId: UByte = 0x10u
    ): Boolean {
        val normalizedEditId = editId.ifBlank { System.currentTimeMillis().toString() }
        val regionId = "erase_region_$normalizedEditId"
        val polygonRegion = SlLink.PolygonRegion.newBuilder()
            .setRegionId(regionId)
            .setName(regionId)
            .setRegionType(SlLink.RegionType.REGION_TYPE_ERASE)
            .setEnabled(true)
            .setClosed(true)
            .setPriority(10)

        for (point in points) {
            polygonRegion.addPoints(
                SlLink.PolygonPoint.newBuilder().setX(point.first).setY(point.second)
            )
        }
        val cmd = MapEditCommand.newBuilder()
            .setEditId("edit_$normalizedEditId")
            .setMapId(mapId ?: "")
            .setOperation(SlLink.MapEditOperation.MAP_EDIT_OP_UPSERT_ERASE_REGION)
            .setRegionName(regionId)
            .setRegion(
                polygonRegion.build()
            )
            .build()

        // 通过你的 SL-LinkA 发送接口发出去：MSG_ID_MAP_EDIT_COMMAND (0x0509)
        val data = slLinkManager.sendMapEditCommand(cmd.toByteArray(), dstId)
        return sendData(data)
    }

    /**
     * 添加工作区
     */
    fun sendMapEditAddWorkspace(
        editId: String,
        areaCode: String,
        regionId: String? = null,
        mapId: String? = null,
        points: List<Pair<Float, Float>>,
        startPose: Pair<Float, Float>? = null,
        endPose: Pair<Float, Float>? = null,
        globalDirection: String = "X",
        dstId: UByte = 0x10u
    ): Boolean {
        val time = secondsElapsedToday()
        val normalizedAreaCode = areaCode.ifBlank { "wr_$time" }
        val normalizedRegionId = regionId?.takeIf { it.isNotBlank() } ?: "work_region_$time"
        val cmd = buildWorkspaceUpsertCommand(
            editId = editId.ifBlank { "edit_$time" },
            mapId = mapId.orEmpty(),
            regionId = normalizedRegionId,
            areaCode = normalizedAreaCode,
            points = points,
            startPose = startPose,
            endPose = endPose,
            globalDirection = globalDirection
        )


        // 通过你的 SL-LinkA 发送接口发出去：MSG_ID_MAP_EDIT_COMMAND (0x0509)
        val data = slLinkManager.sendMapEditCommand(cmd.toByteArray(), dstId)
        return sendData(data)
    }

    /**
     * 删除工作区
     */
    private fun secondsElapsedToday(): Long {
//        val calendar = Calendar.getInstance()
//        return calendar.get(Calendar.HOUR_OF_DAY) * 3600L +
//            calendar.get(Calendar.MINUTE) * 60L +
//            calendar.get(Calendar.SECOND)
        val now = LocalTime.now()
        return now.toSecondOfDay().toLong()
    }

    fun sendMapEditDeleteWorkspace(
        mapId: String?,
        regionId: String,
        dstId: UByte = 0x10u
    ): Boolean {
        val time = secondsElapsedToday()
        val del = buildDeleteRegionCommand(
            editId = "edit_$time",
            mapId = mapId.orEmpty(),
            regionId = regionId,
            regionType = SlLink.RegionType.REGION_TYPE_WORK
        )

        val data = slLinkManager.sendMapEditCommand(del.toByteArray(), dstId)
        return sendData(data)
    }

    /**
     * 添加禁区
     */
    fun sendMapEditAddObstacle(
        editId: String,
        regionId: String? = null,
        mapId: String? = null,
        points: List<Pair<Float, Float>>,
        dstId: UByte = 0x10u
    ): Boolean {
        val time = secondsElapsedToday()
        val normalizedRegionId = regionId?.takeIf { it.isNotBlank() } ?: "obstacle_region_$time"
        val polygonRegion = SlLink.PolygonRegion.newBuilder()
            .setRegionId(normalizedRegionId)
            .setName(normalizedRegionId)
            .setRegionType(SlLink.RegionType.REGION_TYPE_OBSTACLE)
            .setEnabled(true)
            .setClosed(true)
            .setPriority(10)

        for (point in points) {
            polygonRegion.addPoints(
                SlLink.PolygonPoint.newBuilder().setX(point.first).setY(point.second)
            )
        }
        val cmd = MapEditCommand.newBuilder()
            .setEditId(editId.ifBlank { "edit_$time" })
            .setMapId(mapId ?: "")
            .setOperation(SlLink.MapEditOperation.MAP_EDIT_OP_UPSERT_OBSTACLE_REGION)
            .setRegionName(normalizedRegionId)
            .setRegion(
                polygonRegion.build()
            )
            .build()


        // 通过你的 SL-LinkA 发送接口发出去：MSG_ID_MAP_EDIT_COMMAND (0x0509)
        val data = slLinkManager.sendMapEditCommand(cmd.toByteArray(), dstId)
        return sendData(data)
    }

    /**
     * 删除禁区
     */
    fun sendMapEditDeleteObstacle(
        editId: String,
        regionId: String,
        mapId: String? = null,
        dstId: UByte = 0x10u
    ): Boolean {
        val command = buildDeleteRegionCommand(
            editId = editId,
            mapId = mapId.orEmpty(),
            regionId = regionId,
            regionType = SlLink.RegionType.REGION_TYPE_OBSTACLE
        )
        val data = slLinkManager.sendMapEditCommand(command.toByteArray(), dstId)
        return sendData(data)
    }


    /**
     * 发送地图编辑数据
     */
    fun sendMapEditCommand(
        editId: String,
        mapId: String? = null,
        targetType: SlLink.RegionType,
        option: SlLink.MapEditOperation,
        points: List<Pair<Float, Float>>,
        dstId: UByte = 0x10u
    ): Boolean {
        val mapEditCommand = SlLink.MapEditCommand.newBuilder()
        mapEditCommand.setEditId(editId)
        mapEditCommand.setMapId(mapId ?: "")
        mapEditCommand.setRegionName(if (targetType == SlLink.RegionType.REGION_TYPE_WORK) "workspace" else "obstacle")
        mapEditCommand.setTargetRegionType(targetType)
        mapEditCommand.setOperation(option)
        val list = mutableListOf<SlLink.PolygonPoint>()
        for ((index, point) in list.withIndex()) {
            list.add(SlLink.PolygonPoint.newBuilder().setX(point.x).setY(point.y).build())
            mapEditCommand.setRegion(
                SlLink.PolygonRegion.newBuilder().setPoints(index, list[index]).build()
            )
        }

        val data = slLinkManager.sendMapEditCommand(mapEditCommand.build().toByteArray(), dstId)
        return sendData(data)
    }

    /**
     * 开始任务，请求工作区列表和预计耗时
     */
    fun requestMapMetrics(mapId: String, dstId: UByte = 0x10u): Boolean {
        val build = SlLink.MapMetricsRequest.newBuilder()
        build.setMapId(mapId)
        val frameData = slLinkManager.requestMapMetrics(build.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 请求上次任务执行结果，可按 mapId/taskId 过滤。
     */
    fun requestTaskResult(
        mapId: String? = null,
        taskId: String? = null,
        dstId: UByte = 0x10u
    ): Boolean {
        val build = SlLink.TaskResultRequest.newBuilder()
        mapId?.takeIf { it.isNotBlank() }?.let { build.setMapId(it) }
        taskId?.takeIf { it.isNotBlank() }?.let { build.setTaskId(it) }
        val frameData = slLinkManager.requestTaskResult(build.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /** 查询任务执行历史；时间单位为 Unix epoch 秒，0 表示不限制。 */
    fun requestTaskExecutionHistory(
        mapId: String? = null,
        taskId: String? = null,
        startTime: Long = 0L,
        endTime: Long = 0L,
        maxChunkSize: Int = 4096,
        dstId: UByte = 0x10u
    ): Boolean {
        val request = buildTaskExecutionHistoryRequest(
            mapId = mapId,
            taskId = taskId,
            startTime = startTime,
            endTime = endTime,
            maxChunkSize = maxChunkSize
        )
        val frameData = slLinkManager.requestTaskExecutionHistory(
            request.toByteArray(),
            dstId
        )
        return sendData(frameData)
    }

    /** 精确删除某一次任务执行记录及其关联文件。 */
    fun requestTaskExecutionDelete(
        executionId: String,
        dstId: UByte = 0x10u
    ): Boolean {
        if (executionId.isBlank()) return false
        val request = buildTaskExecutionDeleteRequest(executionId)
        val frameData = slLinkManager.requestTaskExecutionDelete(request.toByteArray(), dstId)
        return sendData(frameData)
    }

    /** 按选择项清理设备侧内存缓存、临时文件和运行日志。 */
    fun requestSystemCacheClear(
        clearMemoryCache: Boolean,
        clearTemporaryFiles: Boolean,
        clearLogs: Boolean,
        dstId: UByte = 0x10u
    ): Boolean {
        val request = buildSystemCacheClearRequest(
            clearMemoryCache = clearMemoryCache,
            clearTemporaryFiles = clearTemporaryFiles,
            clearLogs = clearLogs
        ) ?: return false
        val frameData = slLinkManager.requestSystemCacheClear(request.toByteArray(), dstId)
        return sendData(frameData)
    }

    /** 查询某轮任务轨迹；时间单位为 Unix epoch 秒，0 表示不限制。 */
    fun requestTaskTrajectory(
        executionId: String? = null,
        taskId: String? = null,
        startTime: Long = 0L,
        endTime: Long = 0L,
        startIndex: Int = 0,
        maxPoints: Int = 14_400,
        sampleStep: Int = 1,
        maxChunkSize: Int = 4096,
        dstId: UByte = 0x10u
    ): Boolean {
        if (executionId.isNullOrBlank() && taskId.isNullOrBlank()) return false
        val request = buildTaskTrajectoryRequest(
            executionId = executionId,
            taskId = taskId,
            startTime = startTime,
            endTime = endTime,
            startIndex = startIndex,
            maxPoints = maxPoints,
            sampleStep = sampleStep,
            maxChunkSize = maxChunkSize
        )
        val frameData = slLinkManager.requestTaskTrajectory(request.toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 清除地图缓存和雷达图缓存
     */
    fun requestLiveMapCacheClear(dstId: UByte = 0x10u): Boolean {
        val liveMapPayload = SlLink.LiveMapCacheClearRequest.newBuilder().build().toByteArray()
        val liveMapFrameData = slLinkManager.requestLiveMapCacheClear(liveMapPayload, dstId)
        val liveMapSent = sendData(liveMapFrameData)
        val radarMapSent = requestRadarMapCacheClear(dstId)
        return liveMapSent
    }

    fun requestRadarMapCacheClear(dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.RadarMapCacheClearRequest.newBuilder().build().toByteArray()
        val frameData = slLinkManager.requestRadarMapCacheClear(payload, dstId)
        return sendData(frameData)
    }

    /**
     * 按 map_id 将本地保存地图导入雷达（先清雷达地图缓存，导入后进入建图模式）
     */
    fun requestMapImportToRadar(mapId: String, dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.MapImportToRadarRequest.newBuilder()
            .setMapId(mapId)
            .build()
            .toByteArray()
        val frameData = slLinkManager.requestMapImportToRadar(payload, dstId)
        return sendData(frameData)
    }

    /**
     * 发送地图对齐偏航角。
     *
     * [rotationDeg] 是 Step2 当前显示使用的最终对齐角，调用方应保证已经合并后端的
     * alignmentYawDeg 与 rotationAlignmentDeltaDeg，避免在协议层再次叠加。
     */
    fun sendMapAlignment(mapId: String, rotationDeg: Float, dstId: UByte = 0x10u): Boolean {
        LogUtils.d("MapAlignment_Debug: TcpManager.sendMapAlignment 开始组包, mapId=$mapId, rotationDeg=$rotationDeg")
        val builder = SlLink.MapAlignmentRequest.newBuilder()
            .setMapId(mapId)
            .setRotationDeg(rotationDeg)
        val frameData = slLinkManager.requestMapAlignment(builder.build().toByteArray(), dstId)
        val success = sendData(frameData)
        LogUtils.d("MapAlignment_Debug: TcpManager.sendMapAlignment 物理发包结果=$success")
        return success
    }

    /** 查询雷达系统状态。 */
    fun requestRadarSystemStatus(dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.RadarSystemStatusRequest.newBuilder().build().toByteArray()
        val frameData = slLinkManager.requestRadarSystemStatus(payload, dstId)
        return sendData(frameData)
    }

    /** 请求将当前地图同步到雷达。 */
    fun requestRadarMapSync(dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.RadarMapSyncRequest.newBuilder().build().toByteArray()
        val frameData = slLinkManager.requestRadarMapSync(payload, dstId)
        return sendData(frameData)
    }

    /** 请求雷达执行重定位，并携带 ROS map 坐标系中的初始位姿。 */
    fun requestRadarRelocalization(
        xMeters: Float,
        yMeters: Float,
        headingDegrees: Float,
        dstId: UByte = 0x10u,
        positionVariance: Float = 0.25f,
        yawVariance: Float = 0.06853892f
    ): Boolean {
        val initialPose = SlLink.Pose2D.newBuilder()
            .setX(xMeters)
            .setY(yMeters)
            .setHeadingDeg(headingDegrees)
            .build()
        val covariance = SlLink.LocalizationCovariance.newBuilder()
            .setValid(true)
            .setXVariance(positionVariance)
            .setYVariance(positionVariance)
            .setYawVariance(yawVariance)
            .build()
        val payload = SlLink.RadarRelocalizationRequest.newBuilder()
            .setInitialPoseAvailable(true)
            .setInitialPose(initialPose)
            .setInitialPoseCovariance(covariance)
            .build()
            .toByteArray()
        val frameData = slLinkManager.requestRadarRelocalization(payload, dstId)
        return sendData(frameData)
    }

    /** 兼容旧调用方；新页面必须传入用户确认的初始位姿。 */
    fun requestRadarRelocalization(dstId: UByte = 0x10u): Boolean =
        requestRadarRelocalization(0f, 0f, 0f, dstId)

    /** 查询雷达重定位执行状态。 */
    fun requestRadarRelocalizationStatus(dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.RadarRelocalizationStatusRequest.newBuilder().build().toByteArray()
        val frameData = slLinkManager.requestRadarRelocalizationStatus(payload, dstId)
        return sendData(frameData)
    }

    fun requestMapPreview(mapId: String? = null, dstId: UByte = 0x10u): Boolean {
        val payload = mapId?.let {
            SlLink.MapPreviewRequest.newBuilder()
                .setMapId(it)
                .setIncludeOverlay(true)
                .build()
                .toByteArray()
        } ?: byteArrayOf()

        val frameData = slLinkManager.requestMapPreview(payload, dstId)

        return sendData(frameData)
    }

    /** 空 mapId 查询当前地图；只请求区域，Step2–4 的图片仍取会话缓存。 */
    fun requestMapRegionPoints(mapId: String? = null, dstId: UByte = 0x10u): Boolean {
        val payload = SlLink.MapRegionPointRequest.newBuilder().setMapId(mapId.orEmpty()).build()
        return sendData(slLinkManager.requestMapRegionPoints(payload.toByteArray(), dstId))
    }

    /**
     * 请求立即执行路径规划
     */
    fun requestPathPlan(
        mapId: String? = null,
        taskId: String? = null,
        maxChunkSize: Int = 2048,
        dstId: UByte = 0x10u,
        returnPathChunks: Boolean = true,
        requestId: String? = null
    ): Boolean {
        // 旧调用默认仍返回路径分片；Step4 对比弹窗关闭分片，并用 requestId 隔离重试响应。
        val request = buildPathPlanRequest(mapId, taskId, maxChunkSize, returnPathChunks, requestId)
        val frameData = slLinkManager.requestPathPlan(request.toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 任务显示规划路径（首页任务专用）
     */
    fun requestTaskPathPlan(taskId: String, mapId: String? = null, dstId: UByte = 0x10u): Boolean {
        return requestPathPlan(mapId = mapId, taskId = taskId, dstId = dstId)
    }

    /** 0x0505 只返回路径点；空任务不关联配置，空地图使用 LIVE_MAP。等待方须提前持有 requestId。 */
    fun requestPathPointPlan(
        taskId: String = "",
        maxChunkSize: Int = 4096,
        dstId: UByte = 0x10u,
        requestId: String = UUID.randomUUID().toString(),
        mapId: String? = null,
        forceReplan: Boolean = false
    ): Boolean {
        val payload = buildPathPointPlanRequest(
            taskId,
            maxChunkSize,
            requestId,
            mapId,
            forceReplan
        ).toByteArray()
        val frameData = slLinkManager.requestPathPointPlan(payload, dstId)
        return sendData(frameData)
    }

    /**
     * 请求保存地图
     */
    fun requestSaveMap(mapId: String, mapName: String, dstId: UByte = 0x10u): Boolean {
        val builder = SlLink.MapSaveRequest.newBuilder()
        builder.setMapId(mapId).setMapName(mapName)
        val frameData = slLinkManager.requestMapSave(builder.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 删除已保存的地图,根据mapId
     */
    fun requestDeleteMap(mapId: String, dstId: UByte = 0x10u): Boolean {
        val builder = SlLink.MapDeleteRequest.newBuilder()
        builder.setMapId(mapId)
        val frameData = slLinkManager.requestMapDelete(builder.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 请求任务地图列表
     */
    fun requestMapCatalog(dstId: UByte = 0x10u): Boolean {
        val frameData = slLinkManager.requestMapCatalog(byteArrayOf(), dstId)
        return sendData(frameData)
    }

    /**
     * 发送任务配置
     */
    fun requestTaskConfig(
        taskId: String? = "",
        taskName: String,
        mapId: String,
        regionRepeats: List<TaskRegionRepeatConfig> = emptyList(),
        obstacles: List<TaskObstacleRegionConfig> = emptyList(),
        dstId: UByte = 0x10u
    ): Boolean {
        if (taskName.isBlank()) return false
        val taskConfig = buildTaskConfig(taskId, taskName, mapId, regionRepeats, obstacles)
        val serializedObstacles = taskConfig.obstacleRegionsList.joinToString(
            prefix = "[",
            postfix = "]"
        ) { region ->
            val points = region.pointsList.joinToString(
                prefix = "[",
                postfix = "]"
            ) { point -> "(${point.x},${point.y})" }
            "{regionId=${region.regionId}, points=$points}"
        }
        LogUtils.d(
            "StartGrindingObstacleWire",
            "taskId=${taskConfig.taskId} mapId=${taskConfig.mapId} " +
                "obstacleCount=${taskConfig.obstacleRegionsCount} obstacles=$serializedObstacles"
        )
        val frameData = slLinkManager.requestTaskConfig(taskConfig.toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 发送任务控制指令（序列化 [sl_link.SlLink.TaskCommand]）
     */
    fun sendTaskCommand(
        taskType: SlLink.TaskCommandType,
        taskId: String? = "",
        dstId: UByte = 0x10u
    ): Boolean {
        val build = SlLink.TaskCommand.newBuilder()
        build.setCommand(taskType)
        build.setTaskId(taskId)
        val frameData = slLinkManager.requestTaskCommand(build.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 发送机器人控制命令
     * @param remoteX x方向坐标
     * @param remoteY y方向坐标
     * @param speed 速度
     * @param steeringSpeed 转向速度（正常速度百分比）
     * @param maxSpeedMps 本次控制指令的最大直行速度（m/s），0 保持旧版行为
     */
    fun sendRobotControlCommand(
        remoteX: Float,
        remoteY: Float,
        speed: Float,
        steeringSpeed: Float,
        maxSpeedMps: Float = 0f
    ): Boolean {
        val command = buildRobotControlCommand(remoteX, remoteY, speed, maxSpeedMps)
        val data = slLinkManager.sendControlCommand(command.toByteArray())
        return sendData(data)
    }

    /**
     * 发送紧急停止命令
     */
    fun sendEmergencyStop(dstId: UByte = 0x10u): Boolean {
        val estopOnCmd = ControlCommand.newBuilder()
            .setEmergencyStop(
                SlLink.EmergencyStopControl.newBuilder()
                    .setEnabled(true) // true=急停
                    .build()
            )
            .build()

        val frameData = slLinkManager.sendControlCommand(estopOnCmd.toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 请求视频流
     */
    fun requestVideoStreamInfo(dstId: UByte = 0x10u): Boolean {
        val frameData = slLinkManager.requestVideoStreamInfo(byteArrayOf(), dstId)
        return sendData(frameData)
    }

    /**
     * 读取参数设置
     * @param readChassis 是否读取底盘参数
     * @param readMap 是否读取地图参数
     */
    fun requestSettingRead(
        readChassis: Boolean?,
        readMap: Boolean?,
        readRpp: Boolean? = null,
        dstId: UByte = 0x10u
    ): Boolean {
        LogUtils.d("请求参数设置")
        val builder = SlLink.SettingsReadRequest.newBuilder()
        readChassis?.let {
            builder.setReadChassis(it)
        }
        readMap?.let {
            builder.setReadMap(it)
        }
        readRpp?.let {
            builder.setReadRpp(it)
        }
        val frameData = slLinkManager.requestSettingRead(builder.build().toByteArray(), dstId)
        return sendData(frameData)
    }

    /**
     * 写入参数设置
     */
    fun requestSettingWrite(
        chassisSettings: SlLink.ChassisSettings? = null,
        mapSettings: SlLink.MapSettings? = null,
        rppSettings: SlLink.RppSettings? = null,
        applyRppTemporarily: Boolean = false,
        saveRppDefault: Boolean = false,
        rppFieldMask: Long = 0L,
        dstId: UByte = 0x10u
    ): Boolean {
        val request = SlLink.SettingsWriteRequest.newBuilder()
        chassisSettings?.let { request.setChassis(it) }
        mapSettings?.let { request.setMap(it) }
        rppSettings?.let { request.setRpp(it) }
        if (applyRppTemporarily) request.setApplyRppTemporarily(true)
        if (saveRppDefault) request.setSaveRppDefault(true)
        if (rppFieldMask != 0L) request.setRppFieldMask(rppFieldMask)
        val serializedData = request.build().toByteArray()
        val frameData = slLinkManager.requestSettingWrite(serializedData, dstId)
        return sendData(frameData)
    }

    /**
     * 构建WiFi配置数据
     * 用于通过蓝牙发送WiFi配置
     *
     * @param ssid WiFi名称
     * @param password WiFi密码
     * @return 构建的数据包
     * @author Dreamj
     */
    fun buildWifiConfigData(ssid: String, password: String): ByteArray {
        return slLinkManager.sendWifiConfig(ssid, password)
    }

    /**
     * 获取链路质量统计
     *
     * @author Dreamj
     */
    fun logLinkStats() {
        slLinkManager.logLinkStats()
    }

    /**
     * 计划重连任务
     * 在连接失败或断开后，延迟5秒重新连接
     *
     * @author Dreamj
     */
    private fun scheduleReconnect() {
        // 先取消之前的重连任务
        cancelReconnect()

        // 检查是否有连接参数
        val host = lastHost
        val port = lastPort
        if (host == null || port == null) {
            LogUtils.w("没有保存的连接参数，无法重连")
            return
        }

        LogUtils.d("创建重连任务，将在${reconnectDelayMs / 1000}秒后重连到 $host:$port")

        reconnectJob = scope.launch {
            try {
                LogUtils.d("重连任务开始等待${reconnectDelayMs / 1000}秒...")
                delay(reconnectDelayMs)
                LogUtils.d("开始重连到 $host:$port")
                tcpService.connect(host, port)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    LogUtils.e("TCP重连异常: ${e.message}")
                }
            }
        }

        LogUtils.d("重连任务已创建: ${reconnectJob != null}")
    }

    /**
     * 取消重连任务
     *
     * @author Dreamj
     */
    private fun cancelReconnect() {
        reconnectJob?.let {
            it.cancel()
            LogUtils.d("取消重连任务")
        }
        reconnectJob = null
    }

    private fun skipReconnectOnce(): Boolean {
        if (!skipNextReconnect) return false
        skipNextReconnect = false
        LogUtils.d("TCP endpoint switch in progress, skip reconnect once")
        return true
    }
}

internal fun buildRobotControlCommand(
    remoteX: Float,
    remoteY: Float,
    speed: Float,
    maxSpeedMps: Float = 0f
): SlLink.ControlCommand = SlLink.ControlCommand.newBuilder()
    .setManualDrive(
        SlLink.ManualDriveControl.newBuilder()
            .setRemoteX(remoteX)
            .setRemoteY(remoteY)
            .setSpeedRatio(speed)
            .setMaxSpeedMps(maxSpeedMps)
    )
    .build()

/** 不指定端点或方向时保留设备默认值；请求 ID 在发送前生成，不能从返回的任务 ID 推断本轮结果。 */
internal fun buildPathPointPlanRequest(
    taskId: String = "",
    maxChunkSize: Int = 4096,
    requestId: String = UUID.randomUUID().toString(),
    mapId: String? = null,
    forceReplan: Boolean = false
): SlLink.PathPointPlanRequest = SlLink.PathPointPlanRequest.newBuilder()
    .setTaskId(taskId)
    .setMaxChunkSize(maxChunkSize.coerceIn(256, 4096))
    .setRequestId(requestId)
    .setMapId(mapId.orEmpty())
    .setForceReplan(forceReplan)
    .build()

/** 抽出纯构造函数以验证默认协议兼容性；测试预览不设置 forceReplan，也不下发任务执行指令。 */
internal fun buildPathPlanRequest(
    mapId: String? = null,
    taskId: String? = null,
    maxChunkSize: Int = 2048,
    returnPathChunks: Boolean = true,
    requestId: String? = null
): SlLink.PathPlanRequest = SlLink.PathPlanRequest.newBuilder()
    .setReturnPathChunks(returnPathChunks)
    .setMaxChunkSize(maxChunkSize.coerceAtLeast(1))
    .apply {
        if (!mapId.isNullOrEmpty()) setMapId(mapId)
        if (!taskId.isNullOrEmpty()) setTaskId(taskId)
        if (requestId != null) setRequestId(requestId)
    }.build()
