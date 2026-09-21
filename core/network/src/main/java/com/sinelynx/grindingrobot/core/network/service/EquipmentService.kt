package com.sinelynx.grindingrobot.core.network.service

import com.sinelynx.grindingrobot.core.model.request.equipment.EquipmentCreateRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.equipment.EquipmentLogUploadResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface EquipmentService {

    /**
     * 上传设备日志文件（匿名，无需鉴权）
     */
    @Multipart
    @POST("api/0.4/equipment/log/upload")
    suspend fun uploadEquipmentLog(
        @Part("equipmentSN") equipmentSN: RequestBody,
        @Part("softwareVersion") softwareVersion: RequestBody?,
        @Part file: MultipartBody.Part,
    ): UserApiResponse<EquipmentLogUploadResult>

    /**
     * 创建设备信息
     */
    @POST("api/0.4/equipment/device")
    suspend fun createEquipmentDevice(
        @Body request: EquipmentCreateRequest,
    ): UserApiResponse<Unit>
}
