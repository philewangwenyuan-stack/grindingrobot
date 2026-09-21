package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * TCP服务管理器
 * 用于管理TCP连接、发送数据、处理连接状态、心跳检测等
 *
 * @author Dreamj
 */
class TcpService {
    private var socket: Socket? = null
    private var outputStream: BufferedOutputStream? = null
    private var inputStream: BufferedInputStream? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectInterval = 5000L // 5秒重连间隔
    private var currentHost: String? = null
    private var currentPort: Int? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 连接状态相关
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // 心跳相关
    private var heartbeatJob: Job? = null
    private val heartbeatInterval = 1000L // 30秒心跳间隔
    private var heartbeatData: ByteArray = byteArrayOf(0x00) // 默认心跳数据
    
    // 接收数据相关
    private var receiveJob: Job? = null
    private var connectJob: Job? = null
    @Volatile
    private var connectionGeneration = 0
    
    // 连接超时时间
    private val connectTimeout = 30000 // 10秒
    private val readTimeout = 30000 // 10秒
    private val ipv4Regex = Regex(
        pattern = "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    )

    /**
     * TCP回调接口
     */
    interface TcpCallback {
        /**
         * 连接成功回调
         */
        fun onConnected()
        
        /**
         * 接收到数据回调
         * @param data 接收到的数据
         * @param arrivalMonotonicS host 侧观测到该批数据到达时的 monotonic 时间，单位秒
         */
        fun onDataReceived(data: ByteArray, arrivalMonotonicS: Double)
        
        /**
         * 连接断开回调
         * @param reason 断开原因
         */
        fun onDisconnected(reason: String)
        
        /**
         * 连接失败回调
         * @param error 错误信息
         */
        fun onFailure(error: Throwable)
        
        /**
         * 心跳发送回调
         */
        fun onHeartbeatSent()
    }

    private var callback: TcpCallback? = null

    /**
     * 设置TCP回调
     *
     * @param callback 回调接口实现
     */
    fun setCallback(callback: TcpCallback) {
        this.callback = callback
    }

    /**
     * 连接TCP服务器
     *
     * @param host 服务器地址
     * @param port 服务器端口
     */
    fun connect(host: String, port: Int) {
        if (isConnected) {
            return
        }

        cancelConnecting()
        closeResources()
        val generation = nextConnectionGeneration()
        currentHost = host
        currentPort = port
        _connectionState.value = ConnectionState.CONNECTING

        connectJob = scope.launch {
            try {
                val newSocket = Socket().apply {
                    soTimeout = readTimeout
                    keepAlive = true
                    tcpNoDelay = true
                }
                socket = newSocket
                
                newSocket.connect(InetSocketAddress(host, port), connectTimeout)
                if (!isCurrentGeneration(generation)) {
                    runCatching { newSocket.close() }
                    return@launch
                }
                
                outputStream = BufferedOutputStream(newSocket.getOutputStream())
                inputStream = BufferedInputStream(newSocket.getInputStream())
                
                isConnected = true
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED
                
                withContext(Dispatchers.Main) {
                    callback?.onConnected()
                }
                
                // 启动心跳
//                startHeartbeat()
                
                // 启动接收数据
                startReceiving()
                
            } catch (e: Exception) {
                if (e is CancellationException || !isCurrentGeneration(generation)) {
                    return@launch
                }
                isConnected = false
                _connectionState.value = ConnectionState.ERROR
                withContext(Dispatchers.Main) {
                    callback?.onFailure(e)
                }
                
                // 不再自动重连，由TcpManager控制
                // attemptReconnect()
            } finally {
                if (isCurrentGeneration(generation)) {
                    connectJob = null
                }
            }
        }
    }

    /**
     * 异步连接TCP服务器
     *
     * @param host 服务器地址
     * @param port 服务器端口
     * @return 连接是否成功
     */
    suspend fun connectAsync(host: String, port: Int): Boolean = suspendCoroutine { continuation ->
        var resumed = false
        fun resumeOnce(value: Boolean) {
            if (!resumed) {
                resumed = true
                continuation.resume(value)
            }
        }

        if (isConnected) {
            resumeOnce(true)
            return@suspendCoroutine
        }

        val normalizedHost = host.trim()
        val invalidReason = validateConnectParams(normalizedHost, port)
        if (invalidReason != null) {
            LogUtils.e("connectAsync参数校验失败: host=$host, port=$port, reason=$invalidReason")
            _connectionState.value = ConnectionState.ERROR
            scope.launch(Dispatchers.Main) {
                ToastUtils.show("TCP连接参数无效：$invalidReason")
                callback?.onFailure(IllegalArgumentException("Invalid TCP params: $invalidReason"))
            }
            resumeOnce(false)
            return@suspendCoroutine
        }

        cancelConnecting()
        closeResources()
        val generation = nextConnectionGeneration()
        currentHost = normalizedHost
        currentPort = port
        _connectionState.value = ConnectionState.CONNECTING

        connectJob = scope.launch {
            try {
                val newSocket = Socket().apply {
                    soTimeout = readTimeout
                    keepAlive = true
                    tcpNoDelay = true
                }
                socket = newSocket
                
                newSocket.connect(InetSocketAddress(normalizedHost, port), connectTimeout)
                if (!isCurrentGeneration(generation)) {
                    runCatching { newSocket.close() }
                    resumeOnce(false)
                    return@launch
                }
                
                outputStream = BufferedOutputStream(newSocket.getOutputStream())
                inputStream = BufferedInputStream(newSocket.getInputStream())
                
                isConnected = true
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED
                
                withContext(Dispatchers.Main) {
                    callback?.onConnected()
                }
                
                // 启动心跳
                startHeartbeat()
                
                // 启动接收数据
                startReceiving()
                
                resumeOnce(true)
                
            } catch (e: Exception) {
                if (e is CancellationException || !isCurrentGeneration(generation)) {
                    resumeOnce(false)
                    return@launch
                }
                isConnected = false
                _connectionState.value = ConnectionState.ERROR
                withContext(Dispatchers.Main) {
                    callback?.onFailure(e)
                }
                resumeOnce(false)
                
                // 不再自动重连，由TcpManager控制
                // attemptReconnect()
            } finally {
                if (isCurrentGeneration(generation)) {
                    connectJob = null
                }
            }
        }
    }

    private fun validateConnectParams(host: String, port: Int): String? {
        if (host.isBlank()) return "IP为空"
        if (!ipv4Regex.matches(host)) return "IP格式错误"
        if (port !in 1..65535) return "端口范围应为1-65535"
        return null
    }

    /**
     * 发送数据
     *
     * @param data 要发送的数据
     * @return 发送是否成功
     */
    fun sendData(data: ByteArray): Boolean {
        if (!isConnected || outputStream == null) return false
        // 必须在 launch 内捕获：write/flush 在协程里异步执行，外层 try 捕不到 IOException
        scope.launch {
            try {
                outputStream?.write(data)
                outputStream?.flush()
            } catch (e: IOException) {
                LogUtils.e("TcpService", "sendData failed: ${e.message}")
                handleDisconnection("Send failed: ${e.message}")
            }
        }
        return true
    }

    /**
     * 异步发送数据
     *
     * @param data 要发送的数据
     * @return 发送是否成功
     */
    suspend fun sendDataAsync(data: ByteArray): Boolean = suspendCoroutine { continuation ->
        if (isConnected && outputStream != null) {
            scope.launch {
                try {
                    outputStream?.write(data)
                    outputStream?.flush()
                    continuation.resume(true)
                } catch (e: IOException) {
                    continuation.resume(false)
                }
            }
        } else {
            continuation.resume(false)
        }
    }

    /**
     * 设置心跳数据
     *
     * @param data 心跳数据
     */
    fun setHeartbeatData(data: ByteArray) {
        val heartbeatHex = data.joinToString(" ") { byte -> "%02X".format(byte) }
        LogUtils.d("TcpService: 发送心跳 -> $heartbeatHex")
        this.heartbeatData = data
    }

    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(heartbeatInterval)
                try {
                    // Log heartbeat payload in hex for debugging
                    val heartbeatHex = heartbeatData.joinToString(" ") { byte -> "%02X".format(byte) }
//                    LogUtils.d("TcpService: 发送心跳 -> $heartbeatHex")
                    outputStream?.write(heartbeatData)
                    outputStream?.flush()
                    withContext(Dispatchers.Main) {
                        callback?.onHeartbeatSent()
                    }
                } catch (e: IOException) {
                    // 心跳发送失败，连接可能已断开
                    handleDisconnection("Heartbeat failed: ${e.message}")
                    break
                }
            }
        }
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * 启动接收数据
     */
    private fun startReceiving() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive && isConnected) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        val arrivalMonotonicS = android.os.SystemClock.elapsedRealtimeNanos() / 1e9
                        callback?.onDataReceived(data, arrivalMonotonicS)
                    } else if (bytesRead == -1) {
                        // 连接已断开
                        handleDisconnection("Connection closed by server")
                        break
                    }
                }
            } catch (e: IOException) {
                handleDisconnection("Read error: ${e.message}")
            }
        }
    }

    /**
     * 停止接收数据
     */
    private fun stopReceiving() {
        receiveJob?.cancel()
        receiveJob = null
    }

    /**
     * 处理断开连接
     */
    private fun handleDisconnection(reason: String) {
        LogUtils.d("TcpService: 连接已断开 -> $reason","isConnected:$isConnected")
        // 已处于断开则避免重复清理；原逻辑误写为 if (isConnected) return 会导致永不清理
        if (!isConnected) return

        isConnected = false
        _connectionState.value = ConnectionState.DISCONNECTED
        scope.launch(Dispatchers.Main) {
            callback?.onDisconnected(reason)
        }
        
        // 清理资源
        closeResources()
        
        // 不再自动重连，由TcpManager控制
        // attemptReconnect()
    }

    /**
     * 尝试重连
     */
    private fun attemptReconnect() {
        if (reconnectAttempts < maxReconnectAttempts && currentHost != null && currentPort != null) {
            reconnectAttempts++
            _connectionState.value = ConnectionState.CONNECTING
            scope.launch {
                delay(reconnectInterval)
                connect(currentHost!!, currentPort!!)
            }
        }
    }

    /**
     * 关闭连接
     */
    fun disconnect() {
        cancelConnecting()
        isConnected = false
        reconnectAttempts = maxReconnectAttempts // 阻止自动重连
        _connectionState.value = ConnectionState.DISCONNECTED
        
        stopHeartbeat()
        stopReceiving()
        closeResources()
        
        scope.launch(Dispatchers.Main) {
            callback?.onDisconnected("Manual disconnection")
        }
    }

    /**
     * 清理资源
     */
    private fun cancelConnecting() {
        connectionGeneration += 1
        connectJob?.cancel()
        connectJob = null
    }

    private fun nextConnectionGeneration(): Int {
        connectionGeneration += 1
        return connectionGeneration
    }

    private fun isCurrentGeneration(generation: Int): Boolean {
        return generation == connectionGeneration
    }

    private fun closeResources() {
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            // 忽略关闭时的异常
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    fun isConnected(): Boolean {
        return isConnected && socket?.isConnected == true && socket?.isClosed == false
    }

    /**
     * 获取当前连接状态
     *
     * @return 连接状态描述
     */
    fun getConnectionStatus(): String {
        return when {
            isConnected && socket?.isConnected == true -> "Connected"
            reconnectAttempts > 0 -> "Reconnecting (attempt $reconnectAttempts/$maxReconnectAttempts)"
            else -> "Disconnected"
        }
    }
    
    /**
     * 获取当前重连尝试次数
     *
     * @return 重连尝试次数
     */
    fun getReconnectAttempts(): Int {
        return reconnectAttempts
    }

    /**
     * 重置重连尝试次数
     */
    fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    /**
     * 手动重连
     */
    fun reconnect() {
        if (currentHost != null && currentPort != null) {
            disconnect()
            scope.launch {
                delay(1000) // 延迟1秒后重连
                connect(currentHost!!, currentPort!!)
            }
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        disconnect()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        currentHost = null
        currentPort = null
    }
}
