package com.sinelynx.grindingrobot.navigation.routes

import kotlinx.serialization.Serializable

/**
 * 主模块路由
 *
 * @author Dreamj
 */
object MainRoutes {
    /**
     * 主框架路由
     *
     * 应用的主框架，包含底部导航栏
     *
     * @author Dreamj
     */
    @Serializable
    data object Main

    /**
     * 首页路由
     *
     * 应用首页，展示推荐商品和营销活动
     *
     * @author Dreamj
     */
    @Serializable
    data object Home


}
