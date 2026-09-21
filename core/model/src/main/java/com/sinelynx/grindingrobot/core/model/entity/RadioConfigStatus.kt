package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * 无线电配置状态实体类
 * 对应协议消息 MSG_ID: 0x0303
 *
 * @param result 配置结果 0-成功 1-失败
 * @param errorCode 错误码 0-无错误 1-GPIO失败 2-AT超时 3-保存失败
 * @author Dreamj
 */
@Serializable
data class RadioConfigStatus(

    /**
     * 配置结果
     * 0-成功 (RADIO_CONFIG_SUCCESS)
     * 1-失败 (RADIO_CONFIG_FAIL)
     */
    val result: Int = 0,

    /**
     * 错误码
     * 0-无错误 (RADIO_ERR_NONE)
     * 1-GPIO失败 (RADIO_ERR_GPIO_FAIL)
     * 2-AT超时 (RADIO_ERR_AT_TIMEOUT)
     * 3-保存失败 (RADIO_ERR_SAVE_FAIL)
     */
    val errorCode: Int = 0
)
