package com.sinelynx.grindingrobot.core.model.request

import kotlinx.serialization.Serializable

/**
 * 字典数据请求
 *
 * @param types 字典类型列表
 * @author Dreamj
 */
@Serializable
data class DictDataRequest(
    val types: List<String>
)