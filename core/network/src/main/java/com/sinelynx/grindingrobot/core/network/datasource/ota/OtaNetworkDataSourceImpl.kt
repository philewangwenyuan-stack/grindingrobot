package com.sinelynx.grindingrobot.core.network.datasource.ota

import com.sinelynx.grindingrobot.core.model.request.ota.OtaUpgradeCheckRequest
import com.sinelynx.grindingrobot.core.model.request.ota.OtaUpgradeLogRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.ota.OtaUpgradeInfo
import com.sinelynx.grindingrobot.core.network.base.BaseNetworkDataSource
import com.sinelynx.grindingrobot.core.network.service.OtaService
import jakarta.inject.Inject
import okhttp3.ResponseBody
import retrofit2.Response

class OtaNetworkDataSourceImpl @Inject constructor(
    private val otaService: OtaService,
) : BaseNetworkDataSource(), OtaNetworkDataSource {

    override suspend fun checkUpgrade(request: OtaUpgradeCheckRequest): UserApiResponse<List<OtaUpgradeInfo>> {
        return otaService.checkUpgrade(
            equipmentSN = request.equipmentSN,
            firmwareTypeCode = request.firmwareTypeCode,
            currentVersionCode = request.currentVersionCode
        )
    }

    override suspend fun reportUpgradeLog(request: OtaUpgradeLogRequest): UserApiResponse<Unit> {
        return otaService.reportUpgradeLog(request)
    }

    override suspend fun downloadFile(fileUrl: String): Response<ResponseBody> {
        return otaService.downloadFile(fileUrl)
    }
}
