package com.sinelynx.grindingrobot.core.data.state

import com.google.gson.Gson
import com.sinelynx.grindingrobot.core.data.di.ApplicationScope
import com.sinelynx.grindingrobot.core.data.repository.UserInfoStoreRepository
import com.sinelynx.grindingrobot.core.model.entity.Auth
import com.sinelynx.grindingrobot.core.model.entity.CoorData
import com.sinelynx.grindingrobot.core.model.entity.User
import com.sinelynx.grindingrobot.core.model.entity.WifiStatus
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.storage.MMKVKey
import com.sinelynx.grindingrobot.core.util.storage.MMKVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sl_link.SlLink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局应用状态管理类
 *
 * 该类负责管理整个应用的全局状态，包括：
 * - 用户登录状态
 * - 用户认证信息（token等）
 * - 用户个人信息
 * - GNSS连接状态
 *
 * 通过StateFlow提供响应式的状态管理，任何组件都可以订阅状态变化
 *
 * @param userInfoRepository 用户信息网络仓库
 * @param mmkvUtils MMKV工具类
 * @param applicationScope 应用级协程作用域
 * @author Dreamj
 */
@Singleton
class AppState @Inject constructor(
    private val userInfoStoreRepository: UserInfoStoreRepository,
    private val mmkvUtils: MMKVUtils,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    /** 相对 base_link 的米制坐标：X 向前，Y 向左。 */
    data class FootprintPoint(val x: Float, val y: Float)

    data class RobotSettingsState(
        val robotWidth: Double,
        val robotLength: Double,
        val footprint: List<FootprintPoint> = emptyList()
    )

    data class SettingsWriteResponseState(
        val isSuccess: Boolean,
        val message: String,
        val hasChassis: Boolean,
        val hasMap: Boolean,
        val hasRpp: Boolean = false,
        val rppApplied: Boolean = false,
        val rppSaved: Boolean = false,
        val geometryRequiresRestart: Boolean = false
    )

    data class SettingsReadResponseState(
        val isSuccess: Boolean,
        val message: String,
        val chassisSettings: SlLink.ChassisSettings?,
        val mapSettings: SlLink.MapSettings? = null,
        val rppSettings: SlLink.RppSettings? = null
    )

    data class RuntimeTaskState(
        val taskStarted: Boolean = false,
        val workMode: Int = 0,
        val leftWheelSpeedMps: Float = 0f,
        val rightWheelSpeedMps: Float = 0f,
        val discSpeedRpm: Int = 0,
        val discEnabled: Boolean = false,
        val localizationQualityAvailable: Boolean = false,
        val localizationQuality: Int = 0,
        val radarSystemStatusAvailable: Boolean = false,
        val radarSystemStatus: String = "",
        val collisionImminent: Boolean = false,
        val vehicleSpeedMps: Float = 0f
    )

    data class TcpEndpoint(
        val host: String,
        val port: Int
    ) {
        val address: String
            get() = "$host:$port"
    }

    /** 设备视频流信息（LOWER -> APP，MSG 0x050D VideoStreamInfoResponse） */
    data class VideoStreamInfo(
        val result: Int,
        val message: String,
        val streamUrl: String,
        val online: Boolean
    )

    // 用户登录状态
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // 用户ID
    private val _userId = MutableStateFlow(0L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    // 用户授权信息
    private val _auth = MutableStateFlow<Auth?>(null)
    val auth: StateFlow<Auth?> = _auth.asStateFlow()

    // 用户信息
    private val _userInfo = MutableStateFlow<User?>(null)
    val userInfo: StateFlow<User?> = _userInfo.asStateFlow()

    // WiFi状态
    private val _wifiStatus = MutableStateFlow<WifiStatus?>(null)
    val wifiStatus: StateFlow<WifiStatus?> = _wifiStatus.asStateFlow()

    // 系统网络类型状态（用于顶栏信号显示等）
    private val _networkType = MutableStateFlow(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()
    private val _networkSignalLevel = MutableStateFlow(0)
    val networkSignalLevel: StateFlow<Int> = _networkSignalLevel.asStateFlow()

    private val _coorData = MutableStateFlow<CoorData?>(null)
    val coorData: StateFlow<CoorData?> = _coorData.asStateFlow()

    /** TCP 连接使用的 IP 地址，默认空，优先从 MMKV 加载 */
    private val _tcpIpAddress = MutableStateFlow("")
    val tcpIpAddress: StateFlow<String> = _tcpIpAddress.asStateFlow()

    // 运行时任务状态（来自 SDK DeviceStatusReport）
    private val _runtimeTaskState = MutableStateFlow(RuntimeTaskState())
    val runtimeTaskState: StateFlow<RuntimeTaskState> = _runtimeTaskState.asStateFlow()

    private val _videoStreamInfo = MutableStateFlow<VideoStreamInfo?>(null)
    val videoStreamInfo: StateFlow<VideoStreamInfo?> = _videoStreamInfo.asStateFlow()

    //机器人设置
    private val _robotSettings = MutableStateFlow<RobotSettingsState?>(null)
    val robotSettings: StateFlow<RobotSettingsState?> = _robotSettings.asStateFlow()

    //底盘（磨盘）设置
    private val _chassisSettings = MutableStateFlow<SlLink.ChassisSettings?>(null)
    val chassisSettings: StateFlow<SlLink.ChassisSettings?> = _chassisSettings.asStateFlow()

    //地图相关设置
    private val _mapSettings = MutableStateFlow<SlLink.MapSettings?>(null)
    val mapSettings: StateFlow<SlLink.MapSettings?> = _mapSettings.asStateFlow()

    // RPP 导航参数
    private val _rppSettings = MutableStateFlow<SlLink.RppSettings?>(null)
    val rppSettings: StateFlow<SlLink.RppSettings?> = _rppSettings.asStateFlow()

    private val _settingsWriteResponses = MutableSharedFlow<SettingsWriteResponseState>(
        extraBufferCapacity = 1
    )
    val settingsWriteResponses: SharedFlow<SettingsWriteResponseState> =
        _settingsWriteResponses.asSharedFlow()

    private val _settingsReadResponses = MutableSharedFlow<SettingsReadResponseState>(
        extraBufferCapacity = 1
    )
    val settingsReadResponses: SharedFlow<SettingsReadResponseState> =
        _settingsReadResponses.asSharedFlow()

    fun updateVideoStreamInfo(info: VideoStreamInfo) {
        _videoStreamInfo.value = info
    }

    /** 关闭摄像头浮窗等场景：清空流地址，避免 UI 与播放器残留上次会话 */
    fun clearVideoStreamInfo() {
        _videoStreamInfo.value = null
    }

    fun updateRobotSettings(
        width: Double,
        length: Double,
        footprint: List<FootprintPoint> = emptyList()
    ) {
        _robotSettings.value = RobotSettingsState(
            robotWidth = width,
            robotLength = length,
            footprint = footprint.filter { it.x.isFinite() && it.y.isFinite() }
        )
    }

    fun updateMapSetting(settings: SlLink.MapSettings) {
        _mapSettings.value = settings
    }

    fun updateRppSettings(settings: SlLink.RppSettings) {
        _rppSettings.value = settings
    }

    fun updateClassicSettings(settings: SlLink.ChassisSettings) {
        _chassisSettings.value = settings
    }

    fun updateSettingsReadResponse(response: SettingsReadResponseState) {
        _settingsReadResponses.tryEmit(response)
    }

    fun updateSettingsWriteResponse(response: SettingsWriteResponseState) {
        _settingsWriteResponses.tryEmit(response)
    }

    /**
     * 设置ip地址和端口
     */
    fun setTcpIpAddress(ip: String, port: String) {
        val trimmedIp = ip.trim()
        val trimmedPort = port.trim()
        val trimmed = if (trimmedIp.isNotBlank() && trimmedPort.isNotBlank()) {
            "$trimmedIp:$trimmedPort"
        } else {
            ""
        }
        _tcpIpAddress.value = trimmed
        if (trimmed.isNotEmpty()) {
            mmkvUtils.putString(MMKVKey.TCP_IP_ADDRESS, trimmed)
        } else {
            mmkvUtils.remove(MMKVKey.TCP_IP_ADDRESS)
        }
    }

    fun setTcpIpAddress(address: String) {
        val endpoint = parseTcpEndpoint(address)
        if (address.isBlank()) {
            setTcpIpAddress("", "")
        } else {
            setTcpIpAddress(endpoint.host, endpoint.port.toString())
        }
    }

    fun setTcpEndpoint(endpoint: TcpEndpoint) {
        setTcpIpAddress(endpoint.host, endpoint.port.toString())
    }

    fun resolveTcpEndpoint(): TcpEndpoint {
        return parseTcpEndpoint(_tcpIpAddress.value)
    }

    fun parseTcpEndpoint(address: String): TcpEndpoint {
        val trimmed = address.trim()
        if (trimmed.isBlank()) {
            return TcpEndpoint(DEFAULT_TCP_HOST, MAP_PORT)
        }

        val separatorIndex = trimmed.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == trimmed.lastIndex) {
            return TcpEndpoint(trimmed, MAP_PORT)
        }

        val host = trimmed.substring(0, separatorIndex).trim()
        val port = trimmed.substring(separatorIndex + 1).trim().toIntOrNull()
        return TcpEndpoint(
            host = host.ifBlank { DEFAULT_TCP_HOST },
            port = port?.takeIf { it in 1..65535 } ?: MAP_PORT
        )
    }

    /**
     * 网络类型枚举
     */
    enum class NetworkType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET
    }
    /**
     * 初始化应用状态
     * 由外部调用而不是自动初始化
     *
     * @author Dreamj
     */
    fun initialize() {
        applicationScope.launch {
            // 从MMKV加载上次成功的 TCP IP 地址
            loadTcpIpAddress()
        }
    }


    /**
     * 从 MMKV 加载上次成功的 TCP 连接 IP 地址
     */
    private fun loadTcpIpAddress() {
        val savedIp = mmkvUtils.getString(MMKVKey.TCP_IP_ADDRESS, "")
        if (!savedIp.isNullOrBlank()) {
            LogUtils.d("AppState: load tcp ip from MMKV: $savedIp")
            _tcpIpAddress.value = savedIp
        }
    }



    /**
     * 更新用户登录状态
     *
     * @param auth 认证信息
     * @param user 用户信息
     * @author Dreamj
     */
    suspend fun updateUserState(auth: Auth, user: User) {
        // 保存到本地存储
        userInfoStoreRepository.saveUserInfo(user)

        // 更新内存中的状态
        _auth.value = auth
        _userInfo.value = user
        _userId.value = user.id
        _isLoggedIn.value = true
    }

    /**
     * 更新用户信息
     *
     * @param user 新的用户信息
     * @author Dreamj
     */
    suspend fun updateUserInfo(user: User) {
        // 保存到本地存储
        userInfoStoreRepository.saveUserInfo(user)

        // 更新内存中的状态
        _userInfo.value = user
        _userId.value = user.id
    }


    /**
     * 用户登出
     *
     * @author Dreamj
     */
    suspend fun logout() {
        userInfoStoreRepository.clearUserInfo()

        // 重置内存中的状态
        _isLoggedIn.value = false
        _auth.value = null
        _userInfo.value = null
        _userId.value = 0L
    }


    /**
     * 更新WiFi状态
     *
     * @param status WiFi状态信息
     * @author Dreamj
     */
    fun updateWifiStatus(status: WifiStatus) {
        LogUtils.d("updateWifiStatus"+ Gson().toJson(status))
        _wifiStatus.value = status
    }


    /**
     * 清除设备状态
     * 当TCP连接断开时调用，重置所有设备状态为未连接状态
     *
     * @author Dreamj
     */
    fun clearDeviceStatus() {
        LogUtils.d("clearDeviceStatus")
        _runtimeTaskState.value = RuntimeTaskState()
        DeviceStatusStream.reset()
        TaskSchedulerStream.reset()
        _videoStreamInfo.value = null
        _robotSettings.value = null
    }

    fun updateRuntimeTaskState(
        workMode: Int,
        leftWheelSpeedMps: Float,
        rightWheelSpeedMps: Float,
        discSpeedRpm: Int,
        discEnabled: Boolean,
        localizationQualityAvailable: Boolean,
        localizationQuality: Int,
        radarSystemStatusAvailable: Boolean,
        radarSystemStatus: String,
        collisionImminent: Boolean,
        vehicleSpeedMps: Float
    ) {
        val taskStarted = discEnabled || workMode == 2 || workMode == 3
        _runtimeTaskState.value = RuntimeTaskState(
            taskStarted = taskStarted,
            workMode = workMode,
            leftWheelSpeedMps = leftWheelSpeedMps,
            rightWheelSpeedMps = rightWheelSpeedMps,
            discSpeedRpm = discSpeedRpm,
            discEnabled = discEnabled,
            localizationQualityAvailable = localizationQualityAvailable,
            localizationQuality = localizationQuality,
            radarSystemStatusAvailable = radarSystemStatusAvailable,
            radarSystemStatus = radarSystemStatus,
            collisionImminent = collisionImminent,
            vehicleSpeedMps = vehicleSpeedMps
        )
    }

    /**
     * 更新当前系统网络类型
     * 由 NetworkStatusMonitor 等网络监听器调用
     */
    fun updateNetworkType(type: NetworkType ) {
        _networkType.value = type
    }

    fun updateNetworkSignalLevel(level: Int) {
        _networkSignalLevel.value = level.coerceIn(0, 4)
    }

    companion object {
        const val MAP_PORT = 8002
        const val DEFAULT_TCP_HOST = "192.168.11.2"
    }
}
