package com.sinelynx.grindingrobot.core.model.response.equipment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EquipmentLogUploadResult(
    @SerialName("filePath") val filePath: String? = null,
    @SerialName("fileSize") val fileSize: String? = null,
    @SerialName("fileName") val fileName: String? = null,
)
