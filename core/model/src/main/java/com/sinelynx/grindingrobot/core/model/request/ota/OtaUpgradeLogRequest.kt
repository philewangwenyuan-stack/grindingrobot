package com.sinelynx.grindingrobot.core.model.request.ota

import kotlinx.serialization.Serializable

/**
 * OTA 升级日志上报请求
 */
@Serializable
data class OtaUpgradeLogRequest(
    val equipmentSN: String,
    val firmwareTypeCode: String,
    val upgradeResult: String,
    val releaseCode: String? = null,
    val firmwareCode: String? = null,
    val fromVersion: String? = null,
    val fromVersionCode: Int? = null,
    val toVersion: String? = null,
    val toVersionCode: Int? = null,
    val errorMsg: String? = null,
    val startTime: String? = null,
    val completeTime: String? = null,
)
