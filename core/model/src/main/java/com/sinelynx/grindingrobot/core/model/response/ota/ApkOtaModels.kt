package com.sinelynx.grindingrobot.core.model.response.ota

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * APK OTA 检查和刷新下载地址共用的设备上下文。
 * installId 必须在同一设备的多次请求中保持一致，服务端据此判断测试授权和灰度资格。
 */
@Serializable
data class ApkOtaCheckRequest(
    val packageName: String,
    val channel: String,
    val versionCode: Long,
    val installId: String,
    val androidApiLevel: Int,
    val supportedAbis: List<String>
)

/** release 仅在 hasUpdate 为 true 时有值；updateRequired 只用于当前升级页提示。 */
@Serializable
data class ApkOtaCheckResponse(
    val hasUpdate: Boolean,
    val updateRequired: Boolean,
    val release: ApkOtaRelease? = null
)

/**
 * 服务端推荐的 APK 版本。fileUrl 是有时效的预签名地址，不能长期缓存；
 * fileSize 和 fileSHA256 则是下载完成后必须核对的文件身份信息。
 */
@Serializable
data class ApkOtaRelease(
    val releaseId: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val fileUrl: String,
    val urlExpiresAt: String,
    val fileMD5: String? = null,
    val fileSHA256: String,
    val fileSize: Long,
    val releaseNotes: String? = null
)

/** 续传或安装前重新签发的下载地址，同时复核版本仍可供当前设备获取。 */
@Serializable
data class ApkOtaDownloadUrl(
    val releaseId: String,
    val fileUrl: String,
    val urlExpiresAt: String,
    val fileSHA256: String,
    val fileSize: Long
)

/**
 * 客户端升级阶段事件。同一事件重试时应复用 eventId，detail 承载失败原因等补充信息。
 */
@Serializable
data class ApkOtaEvent(
    val eventId: String,
    val releaseId: String,
    val packageName: String,
    val installId: String,
    val eventType: String,
    val success: Boolean,
    val detail: JsonObject,
    val clientTime: String
)

/** duplicate=true 表示服务端已处理过该 eventId，可从本地待发队列移除。 */
@Serializable
data class ApkOtaEventResponse(val accepted: Boolean, val duplicate: Boolean = false)
