package com.sinelynx.grindingrobot.core.network.datasource.ota

import com.sinelynx.grindingrobot.core.model.request.ota.OtaUpgradeCheckRequest
import com.sinelynx.grindingrobot.core.model.request.ota.OtaUpgradeLogRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.ota.OtaUpgradeInfo
import com.sinelynx.grindingrobot.core.network.datasource.base.NetworkDataSource
import okhttp3.ResponseBody
import retrofit2.Response

interface OtaNetworkDataSource : NetworkDataSource {
    suspend fun checkUpgrade(request: OtaUpgradeCheckRequest): UserApiResponse<List<OtaUpgradeInfo>>
    suspend fun reportUpgradeLog(request: OtaUpgradeLogRequest): UserApiResponse<Unit>
    suspend fun downloadFile(fileUrl: String): Response<ResponseBody>
}
