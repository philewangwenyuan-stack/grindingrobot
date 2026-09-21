package com.sinelynx.grindingrobot.core.network.datasource.equipment

import com.sinelynx.grindingrobot.core.model.request.equipment.EquipmentCreateRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.equipment.EquipmentLogUploadResult
import com.sinelynx.grindingrobot.core.network.datasource.base.NetworkDataSource
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface EquipmentNetworkDataSource : NetworkDataSource {
    suspend fun uploadEquipmentLog(
        equipmentSn: RequestBody,
        softwareVersion: RequestBody?,
        filePart: MultipartBody.Part,
    ): UserApiResponse<EquipmentLogUploadResult>

    suspend fun createEquipmentDevice(request: EquipmentCreateRequest): UserApiResponse<Unit>
}
