package com.sinelynx.grindingrobot.core.model.response.ota

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OTA 升级信息
 */
@Serializable
data class OtaUpgradeInfo(
    @SerialName("releaseCode") val releaseCode: String? = null,
    @SerialName("releaseName") val releaseName: String? = null,
    @SerialName("releaseType") val releaseType: String? = null,
    @SerialName("releaseScope") val releaseScope: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("publishTime") val publishTime: String? = null,
    @SerialName("firmwareCode") val firmwareCode: String? = null,
    @SerialName("firmwareTypeCode") val firmwareTypeCode: String? = null,
    @SerialName("firmwareTypeName") val firmwareTypeName: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("versionCode") val versionCode: Int? = null,
    @SerialName("fileUrl") val fileUrl: String? = null,
    @SerialName("fileMD5") val fileMD5: String? = null,
    @SerialName("fileSize") val fileSize: Long? = null,
)
