package com.sinelynx.grindingrobot.core.network.service

import com.sinelynx.grindingrobot.core.model.request.ota.OtaUpgradeLogRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.ota.OtaUpgradeInfo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface OtaService {

    @GET("api/0.4/ota/upgrade/check")
    suspend fun checkUpgrade(
        @Query("equipmentSN") equipmentSN: String,
        @Query("firmwareTypeCode") firmwareTypeCode: String,
        @Query("currentVersionCode") currentVersionCode: Int? = null,
    ): UserApiResponse<List<OtaUpgradeInfo>>

    @POST("api/0.4/ota/upgrade/log")
    suspend fun reportUpgradeLog(@Body request: OtaUpgradeLogRequest): UserApiResponse<Unit>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}
