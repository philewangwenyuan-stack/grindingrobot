package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * 车辆校准状态实体类
 * 对应协议消息 MSG_ID: 0x0404
 *
 * @param result 校准结果 0-成功 1-数据无效 2-存储错误
 * @param message 状态消息
 * @author Dreamj
 */
@Serializable
data class VehicleCalibrationStatus(

    /**
     * 校准结果
     * 0-成功 (CAL_SUCCESS)
     * 1-数据无效 (CAL_FAIL_INVALID_DATA)
     * 2-存储错误 (CAL_FAIL_STORAGE_ERROR)
     */
    val result: Int = 0,

    /**
     * 状态消息
     */
    val message: String = ""
)
