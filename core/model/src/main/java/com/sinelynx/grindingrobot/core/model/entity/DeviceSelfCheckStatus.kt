package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * 设备自检状态实体类
 * 整合所有硬件设备的自检结果
 *
 * @param esp32Healthy ESP32自检是否通过
 * @param gd32Healthy GD32自检是否通过
 * @param allHealthy 所有设备是否都通过自检
 * @param lastUpdateTime 最后更新时间戳
 * @author Dreamj
 */
@Serializable
data class DeviceSelfCheckStatus(

    /**
     * ESP32自检是否通过
     * true-通过 false-未通过或未检测
     */
    val esp32Healthy: Boolean = false,

    /**
     * GD32自检是否通过
     * true-通过 false-未通过或未检测
     */
    val gd32Healthy: Boolean = false,

    /**
     * 所有设备是否都通过自检
     * true-所有设备正常 false-有设备异常或未检测
     */
    val allHealthy: Boolean = false,

    /**
     * 最后更新时间戳（毫秒）
     */
    val lastUpdateTime: Long = 0L
) {
    companion object {
        /**
         * 创建默认状态（所有设备未检测）
         */
        fun createDefault() = DeviceSelfCheckStatus(
            esp32Healthy = false,
            gd32Healthy = false,
            allHealthy = false,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}
