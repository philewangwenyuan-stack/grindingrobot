package com.sinelynx.grindingrobot.core.websocket

import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * WebSocket管理器
 * 用于管理WebSocket连接、发送消息、处理连接状态等
 *
 * @author Dreamj
 */
class WebSocketService {
    private var webSocket: WebSocket? = null
    private var listener: WebSocketListener? = null
    private val client: OkHttpClient
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectInterval = 5000L // 5秒重连间隔
    private var currentUrl: String? = null
    private var scope = CoroutineScope(Dispatchers.IO)

    // 回调接口
    interface WebSocketCallback {
        fun onOpen(webSocket: WebSocket, response: Response)
        fun onMessage(text: String)
        fun onMessage(bytes: ByteString)
        fun onClosed(webSocket: WebSocket, code: Int, reason: String)
        fun onClosing(webSocket: WebSocket, code: Int, reason: String)
        fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?)
    }

    private var callback: WebSocketCallback? = null

    init {
        client = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 设置WebSocket回调
     *
     * @param callback 回调接口实现
     * @author Dreamj
     */
    fun setCallback(callback: WebSocketCallback) {
        this.callback = callback
    }

    /**
     * 连接WebSocket
     *
     * @param url WebSocket服务器地址
     * @author Dreamj
     */
    fun connect(url: String) {
        if (isConnected) {
            return
        }

        currentUrl = url
        val request = Request.Builder()
            .url(url)
            .build()

        listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                this@WebSocketService.webSocket = webSocket
                callback?.onOpen(webSocket, response)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                callback?.onMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                callback?.onMessage(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                callback?.onClosing(webSocket, code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                this@WebSocketService.webSocket = null
                callback?.onClosed(webSocket, code, reason)
                
                // 尝试重连
                if (reconnectAttempts < maxReconnectAttempts && currentUrl != null) {
                    reconnectAttempts++
                    scope.launch {
                        delay(reconnectInterval)
                        connect(currentUrl!!)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                this@WebSocketService.webSocket = null
                callback?.onFailure(webSocket, t, response)
                
                // 尝试重连
                if (reconnectAttempts < maxReconnectAttempts && currentUrl != null) {
                    reconnectAttempts++
                    scope.launch {
                        delay(reconnectInterval)
                        connect(currentUrl!!)
                    }
                }
            }
        }

        // 确保listener不为null再调用newWebSocket
        listener?.let {
            webSocket = client.newWebSocket(request, it)
        }
    }

    /**
     * 异步连接WebSocket
     *
     * @param url WebSocket服务器地址
     * @return 连接是否成功
     * @author Dreamj
     */
    suspend fun connectAsync(url: String): Boolean = suspendCoroutine { continuation ->
        if (isConnected) {
            continuation.resume(true)
            return@suspendCoroutine
        }

        currentUrl = url
        val request = Request.Builder()
            .url(url)
            .build()

        val tempListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                this@WebSocketService.webSocket = webSocket
                callback?.onOpen(webSocket, response)
                continuation.resume(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                this@WebSocketService.webSocket = null
                callback?.onFailure(webSocket, t, response)
                continuation.resume(false)
            }
        }

        client.newWebSocket(request, tempListener)
    }

    /**
     * 发送文本消息
     *
     * @param message 要发送的文本消息
     * @return 发送是否成功
     * @author Dreamj
     */
    fun sendTextMessage(message: String): Boolean {
        return if (isConnected && webSocket != null) {
            webSocket?.send(message) ?: false
        } else {
            false
        }
    }

    /**
     * 异步发送文本消息
     *
     * @param message 要发送的文本消息
     * @return 发送是否成功
     * @author Dreamj
     */
    suspend fun sendTextMessageAsync(message: String): Boolean = suspendCoroutine { continuation ->
        if (isConnected && webSocket != null) {
            val result = webSocket?.send(message) ?: false
            continuation.resume(result)
        } else {
            continuation.resume(false)
        }
    }

    /**
     * 发送二进制消息
     *
     * @param bytes 要发送的二进制数据
     * @return 发送是否成功
     * @author Dreamj
     */
    fun sendBinaryMessage(bytes: ByteString): Boolean {
        return if (isConnected && webSocket != null) {
            webSocket?.send(bytes) ?: false
        } else {
            false
        }
    }

    /**
     * 异步发送二进制消息
     *
     * @param bytes 要发送的二进制数据
     * @return 发送是否成功
     * @author Dreamj
     */
    suspend fun sendBinaryMessageAsync(bytes: ByteString): Boolean = suspendCoroutine { continuation ->
        if (isConnected && webSocket != null) {
            val result = webSocket?.send(bytes) ?: false
            continuation.resume(result)
        } else {
            continuation.resume(false)
        }
    }

    /**
     * 关闭连接
     *
     * @param code 关闭码
     * @param reason 关闭原因
     * @author Dreamj
     */
    fun close(code: Int = 1000, reason: String = "Normal closure") {
        webSocket?.close(code, reason)
        isConnected = false
        webSocket = null
        listener = null
        currentUrl = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     * @author Dreamj
     */
    fun isConnected(): Boolean {
        return isConnected
    }

    /**
     * 获取当前连接状态
     *
     * @return 连接状态
     * @author Dreamj
     */
    fun getConnectionStatus(): String {
        return if (isConnected) "Connected" else "Disconnected"
    }

    /**
     * 获取当前重连尝试次数
     *
     * @return 重连尝试次数
     * @author Dreamj
     */
    fun getReconnectAttempts(): Int {
        return reconnectAttempts
    }

    /**
     * 重置重连尝试次数
     *
     * @author Dreamj
     */
    fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    /**
     * 手动重连
     *
     * @author Dreamj
     */
    fun reconnect() {
        if (currentUrl != null) {
            scope.launch {
                delay(1000) // 延迟1秒后重连
                connect(currentUrl!!)
            }
        }
    }
}