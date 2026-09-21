package com.sinelynx.grindingrobot.core.websocket

import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString

/**
 * WebSocket使用示例
 * 演示如何使用WebSocketManager
 *
 * @author Dreamj
 */
class WebSocketManager {
    private val webSocketService = WebSocketService()

    init {
        // 设置回调
        webSocketService.setCallback(object : WebSocketService.WebSocketCallback {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("WebSocket连接已打开")
                sendTextMessage("Hello Websocket Server")
            }

            override fun onMessage(text: String) {
                println("收到文本消息: $text")
//                sendTextMessage("Hello Websocket clent: $text")
            }

            override fun onMessage(bytes: ByteString) {
                println("收到二进制消息: ${bytes.hex()}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                println("WebSocket连接已关闭: $reason (code: $code)")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                println("WebSocket连接正在关闭: $reason (code: $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket连接失败: ${t.message}")
            }
        })
    }

    /**
     * 连接到WebSocket服务器
     *
     * @param url 服务器地址
     * @author Dreamj
     */
    fun connectToServer(url: String) {
        webSocketService.connect(url)
    }

    /**
     * 发送文本消息
     *
     * @param message 消息内容
     * @author Dreamj
     */
    fun sendTextMessage(message: String) {
        if (webSocketService.isConnected()) {
            val success = webSocketService.sendTextMessage(message)
            if (success) {
                println("文本消息发送成功: $message")
            } else {
                println("文本消息发送失败")
            }
        } else {
            println("WebSocket未连接，无法发送消息")
        }
    }

    /**
     * 发送二进制消息
     *
     * @param data 二进制数据
     * @author Dreamj
     */
    fun sendBinaryMessage(data: ByteString) {
        if (webSocketService.isConnected()) {
            val success = webSocketService.sendBinaryMessage(data)
            if (success) {
                println("二进制消息发送成功")
            } else {
                println("二进制消息发送失败")
            }
        } else {
            println("WebSocket未连接，无法发送消息")
        }
    }

    /**
     * 关闭连接
     *
     * @author Dreamj
     */
    fun closeConnection() {
        webSocketService.close()
    }

    /**
     * 获取连接状态
     *
     * @return 连接状态
     * @author Dreamj
     */
    fun getConnectionStatus(): String {
        return webSocketService.getConnectionStatus()
    }
}