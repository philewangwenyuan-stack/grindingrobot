package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.LiveMapCacheClearPayload
import com.sinelynx.grindingrobot.core.model.state.LiveMapCacheClearStream
import com.sinelynx.grindingrobot.core.model.state.SystemCacheClearPayload
import com.sinelynx.grindingrobot.core.model.state.SystemCacheClearStream
import com.sinelynx.grindingrobot.core.model.state.MapImageStream
import com.sinelynx.grindingrobot.core.model.state.MapImagePayload
import com.sinelynx.grindingrobot.core.model.state.MapDeletePayload
import com.sinelynx.grindingrobot.core.model.state.MapDeleteStream
import com.sinelynx.grindingrobot.core.model.state.MapEditPayload
import com.sinelynx.grindingrobot.core.model.state.MapEditStream
import com.sinelynx.grindingrobot.core.model.state.MapMetricsPayload
import com.sinelynx.grindingrobot.core.model.state.MapMetricsStream
import com.sinelynx.grindingrobot.core.model.state.MapRegionMetricsPayload
import com.sinelynx.grindingrobot.core.model.state.MapModeResponsePayload
import com.sinelynx.grindingrobot.core.model.state.MapModeStream
import com.sinelynx.grindingrobot.core.model.state.MapCatalogItemPayload
import com.sinelynx.grindingrobot.core.model.state.MapCatalogPayload
import com.sinelynx.grindingrobot.core.model.state.MapCatalogStream
import com.sinelynx.grindingrobot.core.model.state.MapPreviewPayload
import com.sinelynx.grindingrobot.core.model.state.MapPreviewStream
import com.sinelynx.grindingrobot.core.model.state.MapSavePayload
import com.sinelynx.grindingrobot.core.model.state.MapSaveStream
import com.sinelynx.grindingrobot.core.model.state.PathPlanPayload
import com.sinelynx.grindingrobot.core.model.state.PathPlanStream
import com.sinelynx.grindingrobot.core.model.state.RadarSystemStatusPayload
import com.sinelynx.grindingrobot.core.model.state.RadarSystemStatusStream
import com.sinelynx.grindingrobot.core.model.state.RadarMapSyncResponsePayload
import com.sinelynx.grindingrobot.core.model.state.RadarMapSyncResponseStream
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationResponsePayload
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationResponseStream
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationStatusPayload
import com.sinelynx.grindingrobot.core.model.state.RadarRelocalizationStatusStream
import com.sinelynx.grindingrobot.core.model.state.TaskCommandResponsePayload
import com.sinelynx.grindingrobot.core.model.state.TaskConfigResponsePayload
import com.sinelynx.grindingrobot.core.model.state.TaskResultRegionPayload
import com.sinelynx.grindingrobot.core.model.state.TaskResultResponsePayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionHistoryPayload
import com.sinelynx.grindingrobot.core.model.state.TaskExecutionDeletePayload
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPagePayload
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.TaskStatusReportPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointPayload
import com.sinelynx.grindingrobot.core.model.state.MapRegionPointStream
import com.sinelynx.grindingrobot.core.model.state.MapRegionPosePayload
import com.sinelynx.grindingrobot.core.model.state.WorkRegionPointPayload
import com.sinelynx.grindingrobot.core.model.state.MapPreviewRegionPayload
import com.sinelynx.grindingrobot.core.model.state.MapPreviewPointPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathStream
import com.sinelynx.grindingrobot.core.model.state.MapImportToRadarPayload
import com.sinelynx.grindingrobot.core.model.state.MapImportToRadarStream
import com.sinelynx.grindingrobot.core.model.entity.WifiStatus
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import sl_link.SlFrame
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlLinkMonitor
import sl_link.SlMessageBuilder
import java.io.ByteArrayOutputStream
import java.util.TreeMap
import javax.inject.Inject

internal fun SlLink.TaskConfigResponse.toPayload() = TaskConfigResponsePayload(
    resultValue = resultValue,
    isSuccess = result == SlLink.ResultCode.RESULT_SUCCESS,
    message = message,
    taskId = taskId,
    taskName = taskName
)

internal fun SlLink.TaskExecutionDeleteResponse.toPayload() = TaskExecutionDeletePayload(
    resultValue = resultValue,
    isSuccess = result == SlLink.ResultCode.RESULT_SUCCESS && deleted,
    message = message,
    executionId = executionId,
    taskId = taskId,
    mapId = mapId,
    deleted = deleted,
    executionFilesDeleted = executionFilesDeleted
)

internal fun SlLink.SystemCacheClearResponse.toPayload() = SystemCacheClearPayload(
    resultValue = resultValue,
    isSuccess = result == SlLink.ResultCode.RESULT_SUCCESS,
    message = message,
    memoryCacheCleared = memoryCacheCleared,
    temporaryFilesCleared = temporaryFilesCleared,
    temporaryBytesReleased = temporaryBytesReleased,
    logFilesCleared = logFilesCleared,
    logBytesReleased = logBytesReleased,
    failedItems = failedItems
)

/**
 * SL-Link 协议管理??
 * 提供 SL-Link 协议的发送、接收和解析功能
 *
 * @param appState 全局应用状态管理器
 * @author Dreamj
 */
class SlLinkManager @Inject constructor(
    private val appState: AppState,
) {
    
    private val parser = SlFrameParser()
    private val monitor = SlLinkMonitor()
    private val mapChunkAssembler = mutableMapOf<Int, MapChunkAssembly>()
    private val taskPathChunkAssembler = TaskPathChunkAssembler()
    private val taskExecutionHistoryChunkAssembler = TaskExecutionHistoryChunkAssembler()
    private val taskTrajectoryChunkAssembler = TaskTrajectoryChunkAssembler()
    private var onRadarMapSyncResponse: (() -> Unit)? = null
    
    init {
        // 设置本设备的源ID (APP)
        SlMessageBuilder.setSourceId(0x01u)
    }

    fun setOnRadarMapSyncResponse(listener: (() -> Unit)?) {
        onRadarMapSyncResponse = listener
    }




    /**
     * 发送WiFi配置到ESP32
     *
     * @param ssid WiFi名称
     * @param password WiFi密码
     * @return 构建的帧数据
     * @author Dreamj
     */
    fun sendWifiConfig(ssid: String, password: String): ByteArray {
        // 待实?? 使用protobuf生成的类
        val builder = SlLink.WifiConfig.newBuilder()
        builder.setSsid(ssid)
        builder.setPassword(password)
        return SlMessageBuilder.buildWifiConfigRaw(builder.build().toByteArray(), 0x20u)
    }

    /**
     * 请求相机画面快照
     * @param serializedData 序列化数??
     * @param dstId 目标设备ID
     */
    fun requestCameraFrame(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildCameraFrameRequestRaw(serializedData, dstId)
    }

    /**
     * 请求地图快照
     * @param serializedData 序列化数??
     * @param dstId 目标设备ID
     */
    fun requestMap(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapRequestRaw(serializedData, dstId)
    }

    /** 请求切换地图模式，例如启动 LIVE_MAP 建图（APP -> LOWER，0x0512）。 */
    fun requestMapMode(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapModeRequestRaw(serializedData, dstId)
    }

    /**
     * 发送控制指??包含急停
     * @param serializedData 序列化数??
     * @param dstId 目标设备ID
     */
    fun sendControlCommand(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildControlCommandRaw(serializedData, dstId)
    }

    /**
     * 下发地图编辑操作
     * @param serializedData 序列化数??
     * @param dstId 目标设备ID
     */
    fun sendMapEditCommand(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapEditCommandRaw(serializedData, dstId)
    }

    /**
     * 请求地图工作区域数据
     */
    fun requestMapMetrics(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapMetricsRequestRaw(serializedData, dstId)
    }

    /**
     * 请求上次任务执行结果
     */
    fun requestTaskResult(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildTaskResultRequestRaw(serializedData, dstId)
    }

    fun requestTaskExecutionHistory(
        serializedData: ByteArray,
        dstId: UByte = 0x10u
    ): ByteArray {
        taskExecutionHistoryChunkAssembler.clear()
        return SlMessageBuilder.buildTaskExecutionHistoryRequestRaw(serializedData, dstId)
    }

    fun requestTaskTrajectory(
        serializedData: ByteArray,
        dstId: UByte = 0x10u
    ): ByteArray {
        taskTrajectoryChunkAssembler.clear()
        return SlMessageBuilder.buildTaskTrajectoryRequestRaw(serializedData, dstId)
    }

    fun requestTaskExecutionDelete(
        serializedData: ByteArray,
        dstId: UByte = 0x10u
    ): ByteArray = SlMessageBuilder.buildTaskExecutionDeleteRequestRaw(serializedData, dstId)

    fun requestSystemCacheClear(
        serializedData: ByteArray,
        dstId: UByte = 0x10u
    ): ByteArray = SlMessageBuilder.buildSystemCacheClearRequestRaw(serializedData, dstId)

    /**
     * 请求清除 LIVE_MAP 缓存（区域缓存/原始地图缓存）
     */
    fun requestLiveMapCacheClear(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildLiveMapCacheClearRequestRaw(serializedData, dstId)
    }

    /**
     * 请求清除雷达侧地图缓存/地图数据
     */
    fun requestRadarMapCacheClear(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildRadarMapCacheClearRequestRaw(serializedData, dstId)
    }

    /**
     * 按 map_id 将本地保存地图导入雷达
     */
    fun requestMapImportToRadar(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapImportToRadarRequestRaw(serializedData, dstId)
    }

    /**
     * 请求地图缩略图、元数据和编辑层
     * @param serializedData 序列化数据
     * @param dstId 目标设备ID
     */
    fun requestMapPreview(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapPreviewRequestRaw(serializedData, dstId)
    }

    fun requestMapRegionPoints(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray =
        SlMessageBuilder.buildMapRegionPointRequestRaw(serializedData, dstId)

    /**
     * 请求路径规划
     * @param serializedData 序列化数据
     * @param dstId 目标设备ID
     */
    fun requestPathPlan(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildPathPlanRequestRaw(serializedData, dstId)
    }

    fun requestPathPointPlan(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildPathPointPlanRequestRaw(serializedData, dstId)
    }

    /**
     * 请求保存地图
     */
    fun requestMapSave(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapSaveRequestRaw(serializedData, dstId)
    }

    /**
     * 请求删除地图
     */
    fun requestMapDelete(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapDeleteRequestRaw(serializedData, dstId)
    }

    /**
     * 请求地图列表
     */
    fun requestMapCatalog(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapCatalogRequestRaw(serializedData, dstId)
    }

    /** 仅负责封装 SL-Link 帧；rotationDeg 的业务含义在 TcpManager 组装 protobuf 时确定。 */
    fun requestMapAlignment(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildMapAlignmentRequestRaw(serializedData, dstId)
    }

    /** 查询雷达系统状态。 */
    fun requestRadarSystemStatus(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildRadarSystemStatusRequestRaw(serializedData, dstId)
    }

    /** 请求将当前地图同步到雷达。 */
    fun requestRadarMapSync(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildRadarMapSyncRequestRaw(serializedData, dstId)
    }

    /** 请求雷达执行重定位。 */
    fun requestRadarRelocalization(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildRadarRelocalizationRequestRaw(serializedData, dstId)
    }

    /** 查询雷达重定位执行状态。 */
    fun requestRadarRelocalizationStatus(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildRadarRelocalizationStatusRequestRaw(serializedData, dstId)
    }

    /**
     * 发送任务配置指令（APP -> LOWER）
     */
    fun requestTaskConfig(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildTaskConfigRaw(serializedData, dstId)
    }

    /**
     * 下发任务控制指令（APP -> LOWER，与 0x0503 TaskCommandResponse 对应数据
     */
    fun requestTaskCommand(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildTaskCommandRaw(serializedData, dstId)
    }

    /**
     * 参数读取 （APP -> LOWER)
     */
    fun requestSettingRead(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildSettingsReadRequestRaw(serializedData, dstId)
    }


    /**
     * 参数写入 （APP -> LOWER)
     */
    fun requestSettingWrite(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildSettingsWriteRequestRaw(serializedData, dstId)
    }

    /**
     * 请求视频流信息
     * @param serializedData 序列化数据
     * @param dstId 目标设备ID
     */
    fun requestVideoStreamInfo(serializedData: ByteArray, dstId: UByte = 0x10u): ByteArray {
        return SlMessageBuilder.buildVideoStreamInfoRequestRaw(serializedData, dstId)
    }


    /**
     * 处理从串口接收到的数据
     *
     * @param data 接收到的原始数据
      * @return 解析出的帧列表
     * @author Dreamj
     */
    fun handleReceivedData(
        data: ByteArray,
        arrivalMonotonicS: Double = -1.0
    ): List<SlFrame> {
//        val hex = data.joinToString(separator = " ") { byte -> "%02X".format(byte) }
//        LogUtils.d("handleReceivedData: $hex")
        val frames = parser.parse(data)
        for (frame in frames) {
            processFrame(frame, arrivalMonotonicS)
        }
        return frames
    }
    
    /**
     * 处理单个帧
     *
     * @param frame 解析出的帧
     * @author Dreamj
     */
    private fun processFrame(frame: SlFrame, arrivalMonotonicS: Double) {
        // 更新链路监控
        val lost = monitor.update(frame.srcId, frame.seq)
        if (lost > 0) {
            LogUtils.d("检测到 $lost 个丢失的数据包，来源设备: 0x${frame.srcId.toString(16)}")
        }
        when (frame.msgId.toInt()) {
            MSG_ID_WIFI_STATUS_REPORT -> handleWifiStatusReport(frame)
            MSG_ID_SETTINGS_READ_RESPONSE -> handleSettingsReadResponse(frame)
            MSG_ID_SETTINGS_WRITE_RESPONSE -> handleSettingsWriteResponse(frame)
            MSG_ID_DEVICE_STATUS_REPORT -> handleDeviceStatusReport(frame, arrivalMonotonicS)
            MSG_ID_CAMERA_FRAME_CHUNK -> handleCameraFrame(frame)
            MSG_ID_MAP_CHUNK -> handleMapChunk(frame)
            MSG_ID_MAP_MODE_RESPONSE -> handleMapModeResponse(frame)
            MSG_ID_CONTROL_COMMAND_RESPONSE -> handleControlCommandResponse(frame)
            MSG_ID_MAP_PREVIEW_RESPONSE -> handleMapPreviewResponse(frame)
            // 区域响应独立分发，建图页面可刷新区域而不更新已冻结的图片。
            0x052F -> runCatching {
                MapRegionPointStream.publish(parseMapRegionPoints(SlLink.MapRegionPointResponse.parseFrom(frame.payload)))
            }.onFailure { LogUtils.e("解析地图区域点响应失败: ${it.message}") }
            MSG_ID_MAP_EDIT_RESPONSE -> handleMapEditResponse(frame)
            MSG_ID_MAP_CATALOG_RESPONSE -> handleMapCatalogResponse(frame)
            MSG_ID_VIDEO_STREAM_INFO_RESPONSE -> handleVideoStreamInfoResponse(frame)
            MSG_ID_PATH_PLAN_RESPONSE -> handlePathPlanResponse(frame)
            MSG_ID_MAP_SAVE_RESPONSE -> handleMapSaveResponse(frame)
            MSG_ID_MAP_DELETE_RESPONSE -> handleMapDeleteResponse(frame)
            MSG_ID_TASK_CONFIG_RESPONSE -> handleTaskConfigResponse(frame)
            MSG_ID_TASK_COMMAND_RESPONSE -> handleTaskCommandResponse(frame)
            MSG_ID_TASK_STATUS_REPORT -> handleTaskStatusReport(frame)
            MSG_ID_PATH_POINT_PLAN_RESPONSE -> handlePathPointPlanResponse(frame)
            MSG_ID_MAP_METRICS_RESPONSE -> handleMapMetricsResponse(frame)
            MSG_ID_TASK_RESULT_RESPONSE -> handleTaskResultResponse(frame)
            MSG_ID_TASK_EXECUTION_HISTORY_CHUNK -> handleTaskExecutionHistoryChunk(frame)
            MSG_ID_TASK_EXECUTION_DELETE_RESPONSE -> handleTaskExecutionDeleteResponse(frame)
            MSG_ID_TASK_TRAJECTORY_CHUNK -> handleTaskTrajectoryChunk(frame)
            MSG_ID_SYSTEM_CACHE_CLEAR_RESPONSE -> handleSystemCacheClearResponse(frame)
            MSG_ID_LIVE_MAP_CACHE_CLEAR_RESPONSE -> handleLiveMapCacheClearResponse(frame)
            MSG_ID_RADAR_MAP_CACHE_CLEAR_RESPONSE -> handleRadarMapCacheClearResponse(frame)
            MSG_ID_MAP_IMPORT_TO_RADAR_RESPONSE -> handleMapImportToRadarResponse(frame)
            MSG_ID_RADAR_SYSTEM_STATUS_RESPONSE -> handleRadarSystemStatusResponse(frame)
            MSG_ID_RADAR_MAP_SYNC_RESPONSE -> handleRadarMapSyncResponse(frame)
            MSG_ID_RADAR_RELOCALIZATION_RESPONSE -> handleRadarRelocalizationResponse(frame)
            MSG_ID_RADAR_RELOCALIZATION_STATUS_RESPONSE -> handleRadarRelocalizationStatusResponse(frame)
        }
    }

    /** 处理地图模式切换响应（LOWER -> APP，0x0513）。 */
    private fun handleMapModeResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图模式响应payload为空")
                return
            }
            val response = SlLink.MapModeResponse.parseFrom(payload)
            LogUtils.d(
                "地图模式响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "mode=${response.mode}, " +
                    "enabled=${response.enabled}, " +
                    "mapKind=${response.mapKind}"
            )
            MapModeStream.publish(
                MapModeResponsePayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    modeValue = response.modeValue,
                    enabled = response.enabled,
                    mapKind = response.mapKind
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图模式响应失败: ${e.message}")
        }
    }

    private fun handleCameraFrame(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("相机分片payload为空")
                return
            }
            val chunk = SlLink.CameraFrameChunk.parseFrom(payload)
            LogUtils.d(
                "相机分片: " +
                    "frameId=${chunk.frameId}, " +
                    "chunk=${chunk.chunkIndex}/${chunk.totalChunks}, " +
                    "size=${chunk.data.size()}, " +
                    "width=${chunk.width}, " +
                    "height=${chunk.height}, " +
                    "codec=${chunk.codec}"
            )
        } catch (e: Exception) {
            LogUtils.e("解析相机分片失败: ${e.message}")
        }
    }

    private fun handlePathPointPlanResponse(frame: SlFrame) {
        try {
            if (frame.payload.isEmpty()) {
                LogUtils.w("任务路径分片payload为空")
                return
            }
            val chunk = SlLink.PathPointPlanResponse.parseFrom(frame.payload)
            val mergedBytes = taskPathChunkAssembler.append(
                taskId = chunk.taskId,
                pathVersion = chunk.pathVersion,
                chunkIndex = chunk.chunkIndex,
                totalChunks = chunk.totalChunks,
                data = chunk.data.toByteArray(),
                requestId = chunk.requestId
            ) ?: return
            // 即使完整 JSON 无法解码，也要发布解析器给出的空路径，让页面结束本轮等待。
            val payload = TaskPathJsonParser.parse(mergedBytes, chunk) {
                LogUtils.e("解析任务路径JSON失败: ${it.message}")
            }
            TaskPathStream.publish(payload)
            LogUtils.d(
                "任务路径组包完成: taskId=${payload.taskId}, " +
                    "pathVersion=${payload.pathVersion}, points=${payload.points.size}"
            )
        } catch (e: Exception) {
            LogUtils.e("解析任务路径分片失败: ${e.message}")
        }
    }

    /**
     * 处理地图分片
     *
     * 协议定义 (MSG_ID: 0x0305):
     * - mapId/utcTime/encoding/width/height/resolution/chunkIndex/totalChunks/data
     *
     * @param frame 解析出的帧
     */
    private fun handleMapChunk(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图分片payload为空")
                return
            }
            val chunk = SlLink.MapChunk.parseFrom(payload)
//            val byteArray = chunk.data.map { it.toByte() }.toByteArray()
//            MapImageStream.publish(byteArray)
            val mergedBytes = appendMapChunk(chunk)
            if (mergedBytes != null) {
                val origin = chunk.origin
                MapImageStream.publish(
                    MapImagePayload(
                        imageBytes = mergedBytes,
                        mapWidth = chunk.width,
                        mapHeight = chunk.height,
                        resolution = chunk.resolution,
                        originX = origin.x.toDouble(),
                        originY = origin.y.toDouble(),
                        headingDeg = origin.headingDeg,
                        alignmentYawDeg = chunk.alignmentYawDeg,
                        rotationAlignmentDeltaDeg = chunk.rotationAlignmentDeltaDeg,
                        mapId = chunk.mapId,
                        mapVersion = chunk.mapVersion,
                        frameId = chunk.frameId,
                        // 保留协议的预览比例和旋转信息；页面是否应用须遵循各自坐标变换，不能重复缩放。
                        previewScaleX = chunk.previewScaleX,
                        previewScaleY = chunk.previewScaleY,
                        appRotationDeg = chunk.appRotationDeg
                    )
                )
                LogUtils.d("地图快照组包完成: mapId=${chunk.mapId}, totalBytes=${mergedBytes.size}")
            }
            LogUtils.d(
                "地图分片: " +
                    "mapId=${chunk.mapId}, " +
                    "chunk=${chunk.chunkIndex}/${chunk.totalChunks}, " +
                    "orgin=${chunk.origin}, ",
                    "encoding=${chunk.encoding}, " +
                    "width=${chunk.width}, " +
                    "height=${chunk.height}, " +
                    "resolution=${chunk.resolution}"
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图分片失败: ${e.message}")
        }
    }

    private fun appendMapChunk(chunk: SlLink.MapChunk): ByteArray? {
        val mapId = chunk.mapId
        val chunkIndex = chunk.chunkIndex
        val totalChunks = chunk.totalChunks
        if (totalChunks <= 0) return null

        val assembly = mapChunkAssembler.getOrPut(mapId) {
            MapChunkAssembly(totalChunks = totalChunks)
        }

        // 设备若更换了总分片数，重置当前组包上下文
        if (assembly.totalChunks != totalChunks) {
            mapChunkAssembler[mapId] = MapChunkAssembly(totalChunks = totalChunks).also {
                it.chunks[chunkIndex] = chunk.data.toByteArray()
            }
            return tryBuildMapImage(mapId)
        }

        assembly.chunks[chunkIndex] = chunk.data.toByteArray()
        return tryBuildMapImage(mapId)
    }

    private fun tryBuildMapImage(mapId: Int): ByteArray? {
        val assembly = mapChunkAssembler[mapId] ?: return null
        if (assembly.chunks.size < assembly.totalChunks) return null

        val output = ByteArrayOutputStream()
        for (index in 0 until assembly.totalChunks) {
            val bytes = assembly.chunks[index] ?: return null
            output.write(bytes)
        }
        mapChunkAssembler.remove(mapId)
        return output.toByteArray()
    }

    /**
     * 处理控制指令响应
     *
     * 协议定义 (MSG_ID: 0x0402):
     * - result: 控制结果 (ResultCode)
     * - message: 结果描述
     *
     * @param frame 解析出的帧
     */
    private fun handleControlCommandResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("控制指令响应payload为空")
                return
            }

            val response = SlLink.ControlCommandResponse.parseFrom(payload)
            LogUtils.d(
                "控制指令响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}"
            )
        } catch (e: Exception) {
            LogUtils.e("解析控制指令响应失败: ${e.message}")
        }
    }

    /**
     * 处理地图缩略图响应
     *
     * 协议定义 (MSG_ID: 0x0508):
     * - result/message/mapVersion/width/height/resolution/origin/frameId/imageData/overlayJson
     *
     * @param frame 解析出的帧
     */
    private fun handleMapPreviewResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图缩略图响应payload为空")
                return
            }

            val response = SlLink.MapPreviewResponse.parseFrom(payload)
            LogUtils.d(
                "地图缩略图响应 ${response.overlayJson}"
            )

            val origin = response.origin
            val isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS
            val overlayRegions = if (isSuccess) {
                MapPreviewOverlayParser.parse(response.overlayJson)
            } else {
                MapPreviewOverlayRegions()
            }
            MapPreviewStream.publish(
                MapPreviewPayload(
                    imageBytes = if (isSuccess) response.imageData.toByteArray() else ByteArray(0),
                    mapWidth = response.width,
                    mapHeight = response.height,
                    resolution = response.resolution,
                    originX = origin.x.toDouble(),
                    originY = origin.y.toDouble(),
                    headingDeg = origin.headingDeg,
                    mapVersion = response.mapVersion,
                    previewScaleX = response.previewScaleX,
                    previewScaleY = response.previewScaleY,
                    workRegions = overlayRegions.workRegions,
                    obstacleRegions = overlayRegions.obstacleRegions,
                    eraseRegions = overlayRegions.eraseRegions,
                    isSuccess = isSuccess,
                    message = response.message,
                    alignmentYawDeg = response.alignmentYawDeg,
                    rotationAlignmentDeltaDeg = response.rotationAlignmentDeltaDeg
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图缩略图响应失败 ${e.message}")
        }
    }

    /**
     * 处理地图编辑响应
     *
     * 协议定义 (MSG_ID: 0x050A):
     * - result/message/mapVersion
     *
     * @param frame 解析出的帧
     */
    private fun handleMapEditResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图编辑响应payload为空")
                return
            }

            val response = SlLink.MapEditResponse.parseFrom(payload)
            LogUtils.d(
                "地图编辑响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "mapVersion=${response.mapVersion}"
            )
            MapEditStream.publish(
                MapEditPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    mapVersion = response.mapVersion
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图编辑响应失败: ${e.message}")
        }
    }

    /**
     * 处理视频流信息响应
     *
     * 协议定义 (MSG_ID: 0x050D):
     * - result/message/streamUrl/codec/width/height/online/utcTime
     *
     * @param frame 解析出的帧
     */
    private fun handleVideoStreamInfoResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("视频流信息响应payload为空")
                return
            }

            val response = SlLink.VideoStreamInfoResponse.parseFrom(payload)
            LogUtils.d(
                "视频流信息响应 " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "streamUrl=${response.streamUrl}, " +
                    "codec=${response.codec}, " +
                    "width=${response.width}, " +
                    "height=${response.height}, " +
                    "online=${response.online}, " +
                    "utcTime=${response.utcTime}"
            )
            appState.updateVideoStreamInfo(
                AppState.VideoStreamInfo(
                    result = response.resultValue,
                    message = response.message,
                    streamUrl = response.streamUrl,
                    online = response.online
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析视频流信息响应失败 ${e.message}")
        }
    }

    /**
     * 处理路径规划响应
     *
     * 协议定义 (MSG_ID: 0x050F):
     * - result/message/requestId/taskId/mapVersion/pathVersion/pathPointCount/pathLengthM/planned/pathChunked/previewImage/previewFormat
     *
     * @param frame 解析出的帧
     */
    private fun handlePathPlanResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("路径规划响应payload为空")
                return
            }

            val response = SlLink.PathPlanResponse.parseFrom(payload)
            LogUtils.d(
                "路径规划响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "requestId=${response.requestId}, " +
                    "taskId=${response.taskId}, " +
                    "total_work_area_m2=${response.totalWorkAreaM2}, " +
                    "estimated_time_s=${response.estimatedTimeS}, " +
                    "mapVersion=${response.mapVersion}, " +
                    "pathVersion=${response.pathVersion}, " +
                    "pathPointCount=${response.pathPointCount}, " +
                    "pathLengthM=${response.pathLengthM}, " +
                    "planned=${response.planned}, " +
                    "pathChunked=${response.pathChunked}, " +
                    "previewImageSize=${response.previewImage.size()}, " +
                    "previewFormat=${response.previewFormat}"
            )
            val origin = if (response.hasOrigin()) response.origin else null
            PathPlanStream.publish(
                PathPlanPayload(
                    resultValue = response.resultValue,
                    message = response.message,
                    requestId = response.requestId,
                    taskId = response.taskId,
                    totalWorkAreaM2 = response.totalWorkAreaM2,
                    estimatedTimeS = response.estimatedTimeS,
                    mapVersion = response.mapVersion,
                    pathVersion = response.pathVersion,
                    pathPointCount = response.pathPointCount,
                    pathLengthM = response.pathLengthM,
                    planned = response.planned,
                    pathChunked = response.pathChunked,
                    previewImageBytes = response.previewImage.toByteArray().takeIf { it.isNotEmpty() },
                    previewFormat = response.previewFormat,
                    width = response.width,
                    height = response.height,
                    resolution = response.resolution,
                    originX = origin?.x?.toDouble(),
                    originY = origin?.y?.toDouble(),
                    headingDeg = origin?.headingDeg,
                    frameId = response.frameId,
                    previewScaleX = response.previewScaleX,
                    previewScaleY = response.previewScaleY,
                    alignmentYawDeg = response.alignmentYawDeg,
                    rotationAlignmentDeltaDeg = response.rotationAlignmentDeltaDeg
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析路径规划响应失败: ${e.message}")
        }
    }

    /**
     * 处理任务控制结果（LOWER -> APP）
     *
     * 协议定义 (MSG_ID: 0x0503):
     * - result/message/task_id
     */
    private fun handleMapSaveResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图保存响应payload为空")
                return
            }
            val response = SlLink.MapSaveResponse.parseFrom(payload)
            LogUtils.d(
                "地图保存响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "mapId=${response.mapId}, " +
                    "mapYamlPath=${response.mapYamlPath}, " +
                    "mapImagePath=${response.mapImagePath}, " +
                    "navigationMapReloaded=${response.navigationMapReloaded}"
            )
            MapSaveStream.publish(
                MapSavePayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    mapId = response.mapId,
                    mapYamlPath = response.mapYamlPath,
                    mapImagePath = response.mapImagePath,
                    navigationMapReloaded = response.navigationMapReloaded
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图保存响应失败: ${e.message}")
        }
    }

    /**
     * 获取地图列表
     */
    private fun handleMapCatalogResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图目录响应payload为空")
                return
            }
            val response = SlLink.MapCatalogResponse.parseFrom(payload)
            LogUtils.d(
                "地图目录响应: " + response.itemsList
            )
            MapCatalogStream.publish(
                MapCatalogPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    totalCount = response.totalCount,
                    items = response.itemsList.map {
                        MapCatalogItemPayload(
                            mapId = it.mapId,
                            name = it.name,
                            sizeBytes = it.sizeBytes,
                            createdAt = it.createdAt,
                            totalWorkAreaM2 = it.totalWorkAreaM2,
                            estimatedTimeS = it.estimatedTimeS,
                            base64Image = it.thumbnailImageB64 ?: "",
                            saveAt = it.savedAt
                        )
                    }
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图目录响应失败: ${e.message}")
        }
    }

    private fun handleMapDeleteResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图删除响应payload为空")
                return
            }
            val response = SlLink.MapDeleteResponse.parseFrom(payload)
            LogUtils.d(
                "地图删除响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "deleted=${response.deleted}"
            )
            MapDeleteStream.publish(
                MapDeletePayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS && response.deleted,
                    message = response.message,
                    deleted = response.deleted,
                    mapId = response.mapId,
                    localDeleted = response.localDeleted,
                    remoteDeleted = response.remoteDeleted,
                    remoteDeletePending = response.remoteDeletePending
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图删除响应失败: ${e.message}")
        }
    }

    private fun handleTaskConfigResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("任务配置响应payload为空")
                return
            }

            val response = SlLink.TaskConfigResponse.parseFrom(payload)
            LogUtils.d(
                "任务配置响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "taskId=${response.taskId}"
            )
            TaskSchedulerStream.publishTaskConfigResponse(
                response.toPayload()
            )
        } catch (e: Exception) {
            LogUtils.e("解析任务配置响应失败: ${e.message}")
        }
    }

    /**
     * 处理地图统计响应
     *
     * 协议定义 (MSG_ID: 0x051B):
     * - result/message/map_id/map_name/region_metrics
     */
    private fun handleMapMetricsResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图响应payload为空")
                return
            }

            val response = SlLink.MapMetricsResponse.parseFrom(payload)
            LogUtils.d(
                "地图统计响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "mapId=${response.mapId}, " +
                    "mapName=${response.mapName}, " +
                    "regionMetrics=${response.regionMetricsList}"
            )

            if (response.regionMetricsCount > 0) {
                response.regionMetricsList.forEachIndexed { index, region ->
                    LogUtils.d(
                        "地图统计分区[$index]: " +
                            "regionId=${region.regionId}, " +
                            "regionName=${region.regionName}, " +
                            "repeat=${region.repeat}, " +
                            "areaM2=${region.areaM2}, " +
                            "estimatedTimeH=${region.estimatedTimeH}"
                    )
                }
            }

            MapMetricsStream.publish(
                MapMetricsPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    mapId = response.mapId,
                    mapName = response.mapName,
                    regions = response.regionMetricsList.map { region ->
                        MapRegionMetricsPayload(
                            regionId = region.regionId,
                            regionName = region.regionName,
                            repeat = region.repeat,
                            areaM2 = region.areaM2,
                            estimatedTimeH = region.estimatedTimeH
                        )
                    }
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图统计响应失败: ${e.message}")
        }
    }

    /**
     * 处理任务执行结果响应
     *
     * 协议定义 (MSG_ID: 0x051D): 主要用于展示地图预览区域，用于展示该地图上次执行的结果
     * - result/message/map_id/task_id/final_state/all_completed/stop_reason/path_version
     * - image_format/image_data/image_width/image_height/finished_at
     * - selected_work_region_ids/region_results
     */
    private fun handleTaskResultResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("任务结果响应payload为空")
                return
            }

            val response = SlLink.TaskResultResponse.parseFrom(payload)
            LogUtils.d(
                "任务结果响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "mapId=${response.mapId}, " +
                    "taskId=${response.taskId}, " +
                    "finalState=${response.finalState}, " +
                    "allCompleted=${response.allCompleted}, " +
                    "stopReason=${response.stopReason}, " +
                    "pathVersion=${response.pathVersion}, " +
                    "imageFormat=${response.imageFormat}, " +
                    "imageSize=${response.imageData.size()}, " +
                    "imageWidth=${response.imageWidth}, " +
                    "imageHeight=${response.imageHeight}, " +
                    "finishedAt=${response.finishedAt}, " +
                    "selectedWorkRegionIds=${response.selectedWorkRegionIdsList}, " +
                    "regionResults=${response.regionResultsList}"
            )

            TaskSchedulerStream.publishTaskResultResponse(
                TaskResultResponsePayload(
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    mapId = response.mapId,
                    taskId = response.taskId,
                    finalStateValue = response.finalStateValue,
                    allCompleted = response.allCompleted,
                    stopReason = response.stopReason,
                    pathVersion = response.pathVersion,
                    imageFormat = response.imageFormat,
                    imageBytes = response.imageData.takeIf { it.size() > 0 }?.toByteArray(),
                    imageWidth = response.imageWidth,
                    imageHeight = response.imageHeight,
                    finishedAt = response.finishedAt,
                    selectedWorkRegionIds = response.selectedWorkRegionIdsList,
                    regionResults = response.regionResultsList.map { region ->
                        TaskResultRegionPayload(
                            regionId = region.regionId,
                            regionName = region.regionName,
                            targetRepeat = region.targetRepeat,
                            executedRepeat = region.executedRepeat,
                            completed = region.completed,
                            unfinishedReason = region.unfinishedReason
                        )
                    }
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析任务结果响应失败: ${e.message}")
        }
    }

    private fun handleTaskExecutionHistoryChunk(frame: SlFrame) {
        try {
            val response = SlLink.TaskExecutionHistoryChunk.parseFrom(frame.payload)
            if (response.result != SlLink.ResultCode.RESULT_SUCCESS) {
                taskExecutionHistoryChunkAssembler.clear()
                TaskSchedulerStream.publishTaskExecutionHistory(
                    TaskExecutionHistoryPayload(
                        isSuccess = false,
                        message = response.message.ifBlank { "任务历史查询失败" },
                        startTime = response.startTime,
                        endTime = response.endTime,
                        totalRecordCount = response.totalRecordCount
                    )
                )
                return
            }
            when (val result = taskExecutionHistoryChunkAssembler.append(
                chunkIndex = response.chunkIndex,
                totalChunks = response.totalChunks,
                totalRecordCount = response.totalRecordCount,
                startTime = response.startTime,
                endTime = response.endTime,
                data = response.data.toByteArray()
            )) {
                TaskExecutionHistoryChunkResult.Pending -> Unit
                is TaskExecutionHistoryChunkResult.Invalid -> {
                    LogUtils.e(result.message)
                    TaskSchedulerStream.publishTaskExecutionHistory(
                        TaskExecutionHistoryPayload(isSuccess = false, message = result.message)
                    )
                }
                is TaskExecutionHistoryChunkResult.Complete -> {
                    val payload = TaskExecutionHistoryParser.parse(result.data)
                    TaskSchedulerStream.publishTaskExecutionHistory(payload)
                }
            }
        } catch (error: Exception) {
            taskExecutionHistoryChunkAssembler.clear()
            LogUtils.e("解析任务历史响应失败: ${error.message}")
            TaskSchedulerStream.publishTaskExecutionHistory(
                TaskExecutionHistoryPayload(
                    isSuccess = false,
                    message = error.message ?: "任务历史数据解析失败"
                )
            )
        }
    }

    private fun handleTaskTrajectoryChunk(frame: SlFrame) {
        try {
            val response = SlLink.TaskTrajectoryChunk.parseFrom(frame.payload)
            if (response.result != SlLink.ResultCode.RESULT_SUCCESS) {
                taskTrajectoryChunkAssembler.clear()
                TaskSchedulerStream.publishTaskTrajectoryPage(
                    TaskTrajectoryPagePayload(
                        isSuccess = false,
                        message = response.message.ifBlank { "任务轨迹查询失败" },
                        executionId = response.executionId,
                        taskId = response.taskId,
                        startIndex = response.startIndex
                    )
                )
                return
            }
            when (val result = taskTrajectoryChunkAssembler.append(response)) {
                TaskTrajectoryChunkResult.Pending -> Unit
                is TaskTrajectoryChunkResult.Invalid -> {
                    LogUtils.e(result.message)
                    TaskSchedulerStream.publishTaskTrajectoryPage(
                        TaskTrajectoryPagePayload(
                            isSuccess = false,
                            message = result.message,
                            executionId = response.executionId,
                            taskId = response.taskId,
                            startIndex = response.startIndex
                        )
                    )
                }
                is TaskTrajectoryChunkResult.Complete -> {
                    TaskSchedulerStream.publishTaskTrajectoryPage(result.payload)
                }
            }
        } catch (error: Exception) {
            taskTrajectoryChunkAssembler.clear()
            LogUtils.e("解析任务轨迹响应失败: ${error.message}")
            TaskSchedulerStream.publishTaskTrajectoryPage(
                TaskTrajectoryPagePayload(
                    isSuccess = false,
                    message = error.message ?: "任务轨迹数据解析失败"
                )
            )
        }
    }

    private fun handleTaskExecutionDeleteResponse(frame: SlFrame) {
        try {
            val response = SlLink.TaskExecutionDeleteResponse.parseFrom(frame.payload)
            TaskSchedulerStream.publishTaskExecutionDelete(response.toPayload())
        } catch (error: Exception) {
            LogUtils.e("解析任务执行记录删除响应失败: ${error.message}")
            TaskSchedulerStream.publishTaskExecutionDelete(
                TaskExecutionDeletePayload(
                    resultValue = SlLink.ResultCode.RESULT_FAILED_VALUE,
                    isSuccess = false,
                    message = error.message ?: "任务执行记录删除响应解析失败",
                    executionId = "",
                    taskId = "",
                    mapId = "",
                    deleted = false,
                    executionFilesDeleted = false
                )
            )
        }
    }

    private fun handleSystemCacheClearResponse(frame: SlFrame) {
        try {
            val response = SlLink.SystemCacheClearResponse.parseFrom(frame.payload)
            LogUtils.d(
                "系统缓存清理响应: result=${response.result}, " +
                    "memoryCache=${response.memoryCacheCleared}, " +
                    "temporaryFiles=${response.temporaryFilesCleared}, " +
                    "temporaryBytes=${response.temporaryBytesReleased}, " +
                    "logFiles=${response.logFilesCleared}, " +
                    "logBytes=${response.logBytesReleased}, failedItems=${response.failedItems}"
            )
            SystemCacheClearStream.publish(response.toPayload())
        } catch (error: Exception) {
            LogUtils.e("解析系统缓存清理响应失败: ${error.message}")
            SystemCacheClearStream.publish(
                SystemCacheClearPayload(
                    resultValue = SlLink.ResultCode.RESULT_FAILED_VALUE,
                    isSuccess = false,
                    message = error.message ?: "系统缓存清理响应解析失败",
                    memoryCacheCleared = false,
                    temporaryFilesCleared = 0,
                    temporaryBytesReleased = 0L,
                    logFilesCleared = 0,
                    logBytesReleased = 0L,
                    failedItems = 0
                )
            )
        }
    }

    /**
     * 处理 LIVE_MAP 缓存清理响应
     *
     * 协议定义 (MSG_ID: 0x051F):
     * - result/message
     */
    private fun handleLiveMapCacheClearResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("LIVE_MAP缓存清理响应payload为空")
                return
            }

            val response = SlLink.LiveMapCacheClearResponse.parseFrom(payload)
            LogUtils.d(
                "LIVE_MAP缓存清理响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}"
            )

            LiveMapCacheClearStream.publish(
                LiveMapCacheClearPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析LIVE_MAP缓存清理响应失败: ${e.message}")
        }
    }

    /**
     * 处理雷达侧地图缓存/地图数据清理响应
     *
     * 协议定义 (MSG_ID: 0x0521):
     * - result/message
     */
    private fun handleRadarMapCacheClearResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("雷达地图缓存清理响应payload为空")
                return
            }

            val response = SlLink.RadarMapCacheClearResponse.parseFrom(payload)
            LogUtils.d(
                "雷达地图缓存清理响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}"
            )
        } catch (e: Exception) {
            LogUtils.e("解析雷达地图缓存清理响应失败: ${e.message}")
        }
    }

    /**
     * 处理地图导入雷达响应
     * 协议定义 (MSG_ID: 0x0523):
     * - result/message
     */
    private fun handleMapImportToRadarResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("地图导入雷达响应payload为空")
                return
            }

            val response = SlLink.MapImportToRadarResponse.parseFrom(payload)
            LogUtils.d(
                "地图导入雷达响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}"
            )

            MapImportToRadarStream.publish(
                MapImportToRadarPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析地图导入雷达响应失败: ${e.message}")
        }
    }

    /** 处理雷达系统状态查询响应（MSG_ID: 0x0527）。 */
    private fun handleRadarSystemStatusResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("雷达系统状态查询响应payload为空")
                return
            }

            val response = SlLink.RadarSystemStatusResponse.parseFrom(payload)
            LogUtils.d(
                "雷达系统状态查询响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "available=${response.available}, " +
                    "status=${response.status}, " +
                    "timestampNs=${response.timestampNs}"
            )
            RadarSystemStatusStream.publish(
                RadarSystemStatusPayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    available = response.available,
                    status = response.status,
                    timestampNs = response.timestampNs
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析雷达系统状态查询响应失败: ${e.message}")
        }
    }

    /** 处理雷达地图同步响应（MSG_ID: 0x0529）。 */
    private fun handleRadarMapSyncResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("雷达地图同步响应payload为空")
                return
            }

            val response = SlLink.RadarMapSyncResponse.parseFrom(payload)
            LogUtils.d(
                "雷达地图同步响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "sent=${response.sent}"
            )
            RadarMapSyncResponseStream.publish(
                RadarMapSyncResponsePayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS && response.sent,
                    message = response.message,
                    sent = response.sent
                )
            )
            onRadarMapSyncResponse?.invoke()
        } catch (e: Exception) {
            LogUtils.e("解析雷达地图同步响应失败: ${e.message}")
        }
    }

    /** 处理雷达重定位响应（MSG_ID: 0x052B）。 */
    private fun handleRadarRelocalizationResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("雷达重定位响应payload为空")
                return
            }

            val response = SlLink.RadarRelocalizationResponse.parseFrom(payload)
            LogUtils.d(
                "雷达重定位响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "accepted=${response.accepted}, " +
                    "status=${response.status}"
            )
            RadarRelocalizationResponseStream.publish(
                RadarRelocalizationResponsePayload(
                    resultValue = response.resultValue,
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS && response.accepted,
                    message = response.message,
                    accepted = response.accepted,
                    status = response.status
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析雷达重定位响应失败: ${e.message}")
        }
    }

    /** 处理雷达重定位状态响应（MSG_ID: 0x052D）。 */
    private fun handleRadarRelocalizationStatusResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("雷达重定位状态响应payload为空")
                return
            }

            val response = SlLink.RadarRelocalizationStatusResponse.parseFrom(payload)
            /*LogUtils.d(
                "雷达重定位状态响应: " +
                    "rawStatus=${response.rawStatus}, " +
                    "timestampNs=${response.timestampNs}"
            )*/
            RadarRelocalizationStatusStream.publish(
                RadarRelocalizationStatusPayload(
                    rawStatus = response.rawStatus,
                    timestampNs = response.timestampNs
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析雷达重定位状态响应失败: ${e.message}")
        }
    }

    private fun handleTaskCommandResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("任务控制响应payload为空")
                return
            }
            val response = SlLink.TaskCommandResponse.parseFrom(payload)
            LogUtils.d(
                "任务控制响应: " +
                    "result=${response.result}, " +
                    "message=${response.message}, " +
                    "taskId=${response.taskId}"
            )
            TaskSchedulerStream.publishTaskCommandResponse(
                TaskCommandResponsePayload(
                    resultValue = response.resultValue,
                    message = response.message,
                    taskId = response.taskId
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析任务控制响应失败: ${e.message}")
        }
    }

    /**
     * 处理任务状态上报（LOWER -> APP）
     *
     * 协议定义 (MSG_ID: 0x0504):
     * - task_id/state/progress/map_version/message/position/replan_requested/path_point_count/path_version
     */
    private fun handleTaskStatusReport(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("任务状态上报payload为空")
                return
            }
            val report = SlLink.TaskStatusReport.parseFrom(payload)
            val pos = if (report.hasPosition()) report.position else null
//            LogUtils.d("任务状态上报 state=${report.state}")
            report.currentRegionId
            TaskSchedulerStream.publishTaskStatusReport(
                TaskStatusReportPayload(
                    taskId = report.taskId,
                    stateValue = report.stateValue,
                    progress = report.progress,
                    mapVersion = report.mapVersion,
                    message = report.message,
                    positionX = pos?.x,
                    positionY = pos?.y,
                    headingDeg = pos?.headingDeg,
                    replanRequested = report.replanRequested,
                    pathPointCount = report.pathPointCount,
                    pathVersion = report.pathVersion,
                    remainWorkArea = report.remainingWorkAreaM2,
                    totalWorkArea = report.totalWorkAreaM2,
                    remainTimeS = report.remainingTimeS,
                    currentRegionId = report.currentRegionId,
                    currentRegionRepeatIndex = report.currentRegionRepeatIndex,
                    currentRegionRepeatTotal = report.currentRegionRepeatTotal
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析任务状态上报失败 ${e.message}")
        }
    }

    /**
     * 处理读取参数响应
     *
     * 协议定义 (MSG_ID: 0x0204):
     * - result/chassis/map/message
     *
     * @param frame 解析出的帧
     */
    private fun handleSettingsReadResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("读取参数响应payload为空")
                return
            }
            val response = SlLink.SettingsReadResponse.parseFrom(payload)
            LogUtils.d("读取参数响应: " +
                "result=${response.result}, " +
                "message=${response.message}, " +
                    "hasChassis=${response.hasChassis()}, " +
                    "hasMap=${response.hasMap()}, " +
                    "hasRpp=${response.hasRpp()}," +
                "Chassis=${response.chassis}, " +
                "Map=${response.map}")
            if (response.result == SlLink.ResultCode.RESULT_SUCCESS) {
                //更新机器人设置页面
                if (response.hasMap()) {
                    val map = response.map
                    appState.updateRobotSettings(
                        width = map.vehicleWidth.toDouble(),
                        length = map.vehicleLength.toDouble()
                    )
                    appState.updateMapSetting(map)
                }
                if (response.hasChassis()) {
                    val chassis = response.chassis
                    appState.updateClassicSettings(
                        chassis
                    )
                }

                if (response.hasRpp()) {
                    appState.updateRppSettings(response.rpp)
                }

            }
            appState.updateSettingsReadResponse(
                AppState.SettingsReadResponseState(
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    chassisSettings = response.chassis.takeIf { response.hasChassis() },
                    mapSettings = response.map.takeIf { response.hasMap() },
                    rppSettings = response.rpp.takeIf { response.hasRpp() }
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析读取参数响应失败: ${e.message}")
        }
    }

    /**
     * 处理写入参数响应
     *
     * 协议定义 (MSG_ID: 0x0206):
     * - result/message/chassis/map
     *
     * @param frame 解析出的帧
     */
    private fun handleSettingsWriteResponse(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("写入参数响应payload为空")
                return
            }
            val response = SlLink.SettingsWriteResponse.parseFrom(payload)
            LogUtils.d(
                "写入参数响应: " +
                    "result=${response.result}, " +
                "message=${response.message}, " +
                    "hasChassis=${response.hasChassis()}, " +
                    "hasMap=${response.hasMap()}, " +
                    "hasRpp=${response.hasRpp()}, " +
                    "rppApplied=${response.rppApplied}, " +
                    "rppSaved=${response.rppSaved}, " +
                    "geometryRequiresRestart=${response.geometryRequiresRestart}"
            )
            if (response.result == SlLink.ResultCode.RESULT_SUCCESS) {
                if (response.hasMap()) {
                    val map = response.map
                    appState.updateRobotSettings(
                        width = map.vehicleWidth.toDouble(),
                        length = map.vehicleLength.toDouble()
                    )
                    appState.updateMapSetting(map)
                }
                if (response.hasRpp()) {
                    appState.updateRppSettings(response.rpp)
                }
            }
            appState.updateSettingsWriteResponse(
                AppState.SettingsWriteResponseState(
                    isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS,
                    message = response.message,
                    hasChassis = response.hasChassis(),
                    hasMap = response.hasMap(),
                    hasRpp = response.hasRpp(),
                    rppApplied = response.rppApplied,
                    rppSaved = response.rppSaved,
                    geometryRequiresRestart = response.geometryRequiresRestart
                )
            )
        } catch (e: Exception) {
            LogUtils.e("解析写入参数响应失败: ${e.message}")
        }
    }

    /**
     * 处理设备状态上报
     *
     * 协议定义 (MSG_ID: 0x0301):
     * - 1Hz运行状态上报
     *
     * @param frame 解析出的帧数据
     */
    private fun handleDeviceStatusReport(frame: SlFrame, arrivalMonotonicS: Double) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("设备状态上报payload为空")
                return
            }
            val report = SlLink.DeviceStatusReport.parseFrom(payload)
//            LogUtils.d(
//                "设备状态上报 " +
//                    "location=${report.position}, "
//            )
            appState.updateRuntimeTaskState(
                workMode = report.workModeValue,
                leftWheelSpeedMps = report.leftWheelSpeed,
                rightWheelSpeedMps = report.rightWheelSpeed,
                discSpeedRpm = report.discSpeedRpm,
                discEnabled = report.discEnabled,
                localizationQualityAvailable = report.localizationQualityAvailable,
                localizationQuality = report.localizationQuality,
                radarSystemStatusAvailable = report.radarSystemStatusAvailable,
                radarSystemStatus = report.radarSystemStatus,
                collisionImminent = report.collisionImminent,
                vehicleSpeedMps = report.vehicleSpeed
            )
            if (report.hasPosition()) {
                val position = report.position
                DeviceStatusStream.publishPose(
                    DevicePosePayload(
                        x = position.x,
                        y = position.y,
                        headingDeg = position.headingDeg
                    )
                )
            }
        } catch (e: Exception) {
            LogUtils.e("解析设备状态上报失?? ${e.message}")
        }
    }


    
    /**
     * 处理WiFi状态报告
     *
     * 协议定义 (MSG_ID: 0x0202):
     * - result: WiFi连接结果 (WifiResult)
     * - message: 状态消息
     *
     * @param frame 解析出的帧
     * @author Dreamj
     */
    private fun handleWifiStatusReport(frame: SlFrame) {
        try {
            val payload = frame.payload
            if (payload.isEmpty()) {
                LogUtils.w("WiFi状态报告payload为空")
                return
            }
            
            // 使用protobuf解析
            val response = SlLink.WifiStatusReport.parseFrom(payload)

            val result = response.result
            val message = response.message

            LogUtils.d("WiFi状态报告 " +
                "result=$result, " +
                "message=$message")

            // 转换为WifiStatus实体类并更新到AppState
            val wifiStatus = WifiStatus(
                result = result.number,
                message = message
            )

            // 更新到AppState，页面会自动响应更新
            appState.updateWifiStatus(wifiStatus)

        } catch (e: Exception) {
            LogUtils.e("解析WiFi状态报告失败 ${e.message}")
        }
    }



    /**
     * 获取链路质量统计信息
     *
     * @author Dreamj
     */
    fun logLinkStats() {
        monitor.logStats()
    }
    
    /**
     * 获取解析器实例
     *
     * @return SlFrameParser实例
     * @author Dreamj
     */
    fun getParser(): SlFrameParser = parser
    
    /**
     * 获取链路监控器
     *
     * @return SlLinkMonitor实例
     * @author Dreamj
     */
    fun getMonitor(): SlLinkMonitor = monitor
    
    companion object {
        //下发WiFi配网 APP -> LOWER
        private const val MSG_ID_WIFI_CONFIG = 0x0201
        //返回配网结果
        private const val MSG_ID_WIFI_STATUS_REPORT = 0x0202
        //读取底盘、地图参??APP -> LOWER
        private const val MSG_ID_SETTINGS_READ_REQUEST = 0x0203
        //返回底盘/地图参数 LOWER -> APP
        private const val MSG_ID_SETTINGS_READ_RESPONSE = 0x0204
        //写入底盘/地图参数 APP -> LOWER
        private const val MSG_ID_SETTINGS_WRITE_REQUEST = 0x0205
        //返回写入结果 LOWER -> APP
        private const val MSG_ID_SETTINGS_WRITE_RESPONSE = 0x0206
        //上报运行状??LOWER -> APP
        private const val MSG_ID_DEVICE_STATUS_REPORT = 0x0301
        //请求相机画面快照 APP -> LOWER
        private const val MSG_ID_CAMERA_FRAME_REQUEST = 0x0302
        //返回相机画面分片 LOWER -> APP
        private const val MSG_ID_CAMERA_FRAME_CHUNK = 0x0303
        //请求地图快照 APP -> LOWER
        private const val MSG_ID_MAP_REQUEST = 0x0304
        //返回地图分片 LOWER -> APP
        private const val MSG_ID_MAP_CHUNK = 0x0305
        //返回地图模式切换结果 LOWER -> APP
        private const val MSG_ID_MAP_MODE_RESPONSE = 0x0513
        //下发控制指令 APP -> LOWER
        private const val MSG_ID_CONTROL_COMMAND = 0x0401
        //返回控制结果 LOWER -> APP
        private const val MSG_ID_CONTROL_COMMAND_RESPONSE = 0x0402

        //返回任务配置结果
        private const val MSG_ID_TASK_CONFIG_RESPONSE = 0x0501

        //请求地图缩略图、元数据和编辑层 APP -> LOWER
        private const val MSG_ID_MAP_PREVIEW_REQUEST = 0x0507
        //返回地图缩略图与编辑??LOWER -> APP
        private const val MSG_ID_MAP_PREVIEW_RESPONSE = 0x0508
        //下发地图编辑操作 APP -> LOWER
        private const val MSG_ID_MAP_EDIT_COMMAND = 0x0509
        //返回地图编辑结果 LOWER -> APP
        private const val MSG_ID_MAP_EDIT_RESPONSE = 0x050A
        //请求视频流信息APP -> LOWER
        private const val MSG_ID_VIDEO_STREAM_INFO_REQUEST = 0x050C
        //返回视频流地址和在线状??LOWER -> APP
        private const val MSG_ID_VIDEO_STREAM_INFO_RESPONSE = 0x050D
        //请求立即执行路径规划 APP -> LOWER
        private const val MSG_ID_PATH_PLAN_REQUEST = 0x050E
        //返回规划结果摘要 LOWER -> APP
        private const val MSG_ID_PATH_PLAN_RESPONSE = 0x050F
        //返回地图保存结果 LOWER -> APP
        private const val MSG_ID_MAP_SAVE_RESPONSE = 0x0519
        //返回本地地图列表 LOWER -> APP
        private const val MSG_ID_MAP_CATALOG_RESPONSE = 0x0515
        //返回地图删除结果 LOWER -> APP
        private const val MSG_ID_MAP_DELETE_RESPONSE = 0x0517
        //返回任务控制结果 LOWER -> APP
        private const val MSG_ID_TASK_COMMAND_RESPONSE = 0x0503
        //上报任务状态、进度与当前位置 LOWER -> APP
        private const val MSG_ID_TASK_STATUS_REPORT = 0x0504
        private const val MSG_ID_PATH_POINT_PLAN_RESPONSE = 0x0506

        //  返回 map_id/name + 工作区域面积/耗时明细
        private const val MSG_ID_MAP_METRICS_RESPONSE = 0x051B
        // 返回任务结果图与区域执行结果明细 LOWER -> APP
        private const val MSG_ID_TASK_RESULT_RESPONSE = 0x051D
        // 任务执行历史分块 LOWER -> APP
        private const val MSG_ID_TASK_EXECUTION_HISTORY_CHUNK = 0x0531
        // 删除任务执行记录响应 LOWER -> APP
        private const val MSG_ID_TASK_EXECUTION_DELETE_RESPONSE = 0x0535
        // 任务执行轨迹分块 LOWER -> APP
        private const val MSG_ID_TASK_TRAJECTORY_CHUNK = 0x0533
        // 系统缓存清理响应 LOWER -> APP
        private const val MSG_ID_SYSTEM_CACHE_CLEAR_RESPONSE = 0x0537
        // 请求清除 LIVE_MAP 缓存 APP -> LOWER
        private const val MSG_ID_LIVE_MAP_CACHE_CLEAR_REQUEST = 0x051E
        // 返回 LIVE_MAP 缓存清理结果 LOWER -> APP
        private const val MSG_ID_LIVE_MAP_CACHE_CLEAR_RESPONSE = 0x051F
        // 请求清除雷达侧地图缓存/地图数据 APP -> LOWER
        private const val MSG_ID_RADAR_MAP_CACHE_CLEAR_REQUEST = 0x0520
        // 返回雷达清图指令下发结果 LOWER -> APP
        private const val MSG_ID_RADAR_MAP_CACHE_CLEAR_RESPONSE = 0x0521
        // 按 map_id 将本地保存地图导入雷达 APP -> LOWER
        private const val MSG_ID_MAP_IMPORT_TO_RADAR_REQUEST = 0x0522
        // 返回地图导入雷达结果 LOWER -> APP
        private const val MSG_ID_MAP_IMPORT_TO_RADAR_RESPONSE = 0x0523
        // 查询雷达系统状态 APP -> LOWER
        private const val MSG_ID_RADAR_SYSTEM_STATUS_REQUEST = 0x0526
        // 返回雷达系统状态 LOWER -> APP
        private const val MSG_ID_RADAR_SYSTEM_STATUS_RESPONSE = 0x0527
        // 请求同步当前地图到雷达 APP -> LOWER
        private const val MSG_ID_RADAR_MAP_SYNC_REQUEST = 0x0528
        // 返回雷达地图同步结果 LOWER -> APP
        private const val MSG_ID_RADAR_MAP_SYNC_RESPONSE = 0x0529
        // 返回雷达重定位请求受理结果 LOWER -> APP
        private const val MSG_ID_RADAR_RELOCALIZATION_RESPONSE = 0x052B
        // 返回雷达重定位执行状态 LOWER -> APP
        private const val MSG_ID_RADAR_RELOCALIZATION_STATUS_RESPONSE = 0x052D

    }
}

private data class MapChunkAssembly(
    val totalChunks: Int,
    val chunks: MutableMap<Int, ByteArray> = TreeMap()
)

/** 原样保留区域几何和接口提供的起终点，不在接收层按顶点、外接矩形或路径重新计算。 */
internal fun parseMapRegionPoints(response: SlLink.MapRegionPointResponse): MapRegionPointPayload {
    fun SlLink.PolygonRegion.region() = MapPreviewRegionPayload(
        regionId = regionId, regionName = name,
        points = pointsList.map { point ->
            require(point.x.isFinite() && point.y.isFinite()) { "区域顶点无效" }
            MapPreviewPointPayload(point.x, point.y)
        }, enabled = enabled
    )
    fun SlLink.Pose2D.pose(): MapRegionPosePayload {
        require(x.isFinite() && y.isFinite() && headingDeg.isFinite()) { "区域起终点无效" }
        return MapRegionPosePayload(x, y, headingDeg)
    }
    return MapRegionPointPayload(
        mapId = response.mapId, mapVersion = response.mapVersion,
        isSuccess = response.result == SlLink.ResultCode.RESULT_SUCCESS, message = response.message,
        workRegions = response.workRegionsList.filter { it.hasRegion() }.map {
            // 必须同时满足 available 和消息存在；单凭 hasPose 或坐标是否为 0 无法判断可用性。
            WorkRegionPointPayload(it.region.region(),
                if (it.startPoseAvailable && it.hasStartPose()) it.startPose.pose() else null,
                if (it.endPoseAvailable && it.hasEndPose()) it.endPose.pose() else null)
        },
        obstacleRegions = response.obstacleRegionsList.map { it.region() },
        eraseRegions = response.eraseRegionsList.map { it.region() },
        cropRegion = if (response.cropRegionAvailable && response.hasCropRegion()) response.cropRegion.region() else null
    )
}

