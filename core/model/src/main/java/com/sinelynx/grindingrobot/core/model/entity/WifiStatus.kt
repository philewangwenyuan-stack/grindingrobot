package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * WiFi状态实体类
 * 对应协议消息 MSG_ID: 0x0202
 *
 * @param result WiFi连接结果 0-等待中 1-成功 2-失败 3-超时
 * @param message 状态消息
 * @author Dreamj
 */
@Serializable
data class WifiStatus(

    /**
     * WiFi连接结果
     * 0-等待中 (WIFI_PENDING)
     * 1-成功 (WIFI_SUCCESS)
     * 2-失败 (WIFI_FAIL)
     * 3-超时 (WIFI_TIMEOUT)
     */
    val result: Int = 0,

    /**
     * 状态消息
     */
    val message: String = ""
)
