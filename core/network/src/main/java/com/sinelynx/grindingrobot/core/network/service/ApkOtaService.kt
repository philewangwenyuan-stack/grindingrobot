package com.sinelynx.grindingrobot.core.network.service

import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaCheckRequest
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaCheckResponse
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaDownloadUrl
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaEvent
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaEventResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * 206 且起始字节与本地文件长度一致时才能追加；服务器返回 200 表示忽略 Range，需覆盖旧文件。
 * 其他状态不得继续写入，以免将不同版本的字节拼成一个看似完整的 APK。
 */
fun shouldAppendDownload(status: Int, offset: Long, contentRange: String?): Boolean = when (status) {
    200 -> false
    206 -> {
        require(offset > 0 && contentRange?.startsWith("bytes $offset-") == true) { "续传范围不匹配" }
        true
    }
    else -> error("下载响应无效：HTTP $status")
}

/** APK OTA 的 JSON 接口和预签名文件下载均复用项目现有 Retrofit 实例。 */
interface ApkOtaService {
    /** @Url 是 OTA 服务的完整地址，不受业务服务 baseUrl 的路径前缀影响。 */
    @POST
    suspend fun check(@Url url: String, @Body request: ApkOtaCheckRequest): ApkOtaCheckResponse

    /** 过期或续传前刷新预签名地址；请求体与检查更新使用相同设备上下文。 */
    @POST
    suspend fun refreshDownloadUrl(@Url url: String, @Body request: ApkOtaCheckRequest): ApkOtaDownloadUrl

    /** 响应用于确认事件被接收或已去重，本地队列据此决定是否移除事件。 */
    @POST
    suspend fun reportEvent(@Url url: String, @Body event: ApkOtaEvent): Response<ApkOtaEventResponse>

    /** 流式读取 APK，避免将整个安装包载入内存；Range 用于接续本地临时文件。 */
    @Streaming
    @GET
    suspend fun download(@Url url: String, @Header("Range") range: String?): Response<ResponseBody>
}
