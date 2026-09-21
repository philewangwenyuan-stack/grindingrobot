package com.sinelynx.grindingrobot.core.model.request.equipment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EquipmentCreateRequest(
    @SerialName("equipmentSN") val equipmentSn: String,
    @SerialName("modelCode") val modelCode: String,
    @SerialName("equipmentName") val equipmentName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("tabletSN") val tabletSn: String,
    @SerialName("softwareVersion") val softwareVersion: String,
    @SerialName("machineBrand") val machineBrand: String? = null,
    @SerialName("machineModel") val machineModel: String? = null,
)
