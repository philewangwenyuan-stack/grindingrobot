package com.sinelynx.grindingrobot.navigation.routes

import kotlinx.serialization.Serializable

/**
 * 地图编辑流程路由
 */
object MapRoutes {
    const val LIVE_MAP_ID = "LIVE_MAP"

    @Serializable
    data object Home

    @Serializable
    data class Step1(
        val mapId: String? = null,
        val mapName: String? = null
    )

    @Serializable
    data class Step2(
        val mapId: String? = null,
        val mapName: String? = null
    )

    @Serializable
    data class Step3(
        val mapId: String? = null,
        val mapName: String? = null
    )

    @Serializable
    data class Step4(
        val mapId: String? = null,
        val mapName: String? = null,
        val scanDirection: String = "X"
    )

    @Serializable
    data class TaskReplay(
        val executionId: String = "",
        val taskId: String = ""
    )
}

