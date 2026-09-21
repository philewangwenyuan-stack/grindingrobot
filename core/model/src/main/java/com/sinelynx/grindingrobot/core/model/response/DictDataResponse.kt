package com.sinelynx.grindingrobot.core.model.response

import com.sinelynx.grindingrobot.core.model.entity.DictItem
import kotlinx.serialization.Serializable

/**
 * 字典数据响应
 *
 * @param orderCancelReason 订单取消原因字典
 * @param orderRefundReason 订单退款原因字典
 * @param feedbackType 反馈类型字典
 * @author Dreamj
 */
@Serializable
data class DictDataResponse(
    /**
     * 订单取消原因字典
     */
    val orderCancelReason: List<DictItem>? = null,

    /**
     * 订单退款原因字典
     */
    val orderRefundReason: List<DictItem>? = null,

    /**
     * 反馈类型字典
     */
    val feedbackType: List<DictItem>? = null
)