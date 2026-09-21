package com.sinelynx.grindingrobot.core.model.gnss

/**
 * 规范化后的 GNSS 历元数据。
 *
 * - `main` / `aux` 表示主副天线在同一历元上的解
 * - 时间字段统一使用数值表示，展示字符串由 UI 侧负责格式化
 */
data class GnssFrame(
    val main: GnssFix? = null,
    val aux: GnssFix? = null,
    val headingDeg: Double? = null,
    val headingValid: Boolean? = null,
    val speedKnots: Double? = null,
    val courseOverGroundDeg: Double? = null,
    val rmcValid: Boolean? = null,
    val utcTimeSecondsOfDay: Double? = null,
    val utcEpochSeconds: Double? = null,
    val hostObservedMonotonicS: Double? = null,
)

data class GnssFix(
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val altitudeM: Double? = null,
    val geoidSeparationM: Double? = null,
    val satellitesUsed: Int? = null,
    val fixQualityCode: Int? = null,
    val hdop: Double? = null,
    val ageOfDiffS: Double? = null,
)
