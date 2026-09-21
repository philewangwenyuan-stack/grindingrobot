package com.sinelynx.grindingrobot.core.model.request.ota

import kotlinx.serialization.Serializable

/**
 * OTA 升级检查查询参数
 */
@Serializable
data class OtaUpgradeCheckRequest(
    val equipmentSN: String,
    val firmwareTypeCode: String,
    val currentVersionCode: Int? = null,
)
