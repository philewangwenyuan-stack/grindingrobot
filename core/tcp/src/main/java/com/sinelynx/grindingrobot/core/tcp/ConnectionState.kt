package com.sinelynx.grindingrobot.core.tcp

/**
 * TCP连接状态枚举
 *
 * @author Dreamj
 */
enum class ConnectionState {
    /**
     * 连接中
     */
    CONNECTING,
    
    /**
     * 已连接
     */
    CONNECTED,
    
    /**
     * 已断开
     */
    DISCONNECTED,
    
    /**
     * 错误状态
     */
    ERROR
}