package com.sinelynx.grindingrobot.navigation.routes

import kotlinx.serialization.Serializable

/**
 * 应用信息模块路由
 *
 * @author Dreamj
 */
object AppInfoRoutes {
    /**
     * 应用信息主页面路由
     * 包含硬件状态和软件状态两个Tab页面
     *
     * @author Dreamj
     */
    @Serializable
    data object AppInfo
    
    /**
     * 蓝牙连接页面路由
     * 用于扫描和连接蓝牙设备
     *
     * @author Dreamj
     */
    @Serializable
    data object ConnectBlue
}
