package com.sinelynx.grindingrobot.core.model.state

/** Null means unsupported/unavailable, never implicitly healthy. All resources refer to the robot board. */
data class SystemStatusPayload(
    val receivedMonotonicS: Double,
    val leftMotorFault: Int? = null,
    val rightMotorFault: Int? = null,
    val cpuTemperatureC: Float? = null,
    val cpuUsagePercent: Float? = null,
    val memoryUsagePercent: Float? = null,
    val memoryTotalBytes: Long? = null,
    val memoryAvailableBytes: Long? = null,
    val diskUsagePercent: Float? = null,
    val diskFreeBytes: Long? = null,
    val uptimeSeconds: Long? = null,
    val chassisConnected: Boolean? = null,
    val chassisEnabled: Boolean = false,
    val discEnabled: Boolean = false,
    val emergencyStop: Boolean? = null,
    val localizationReady: Boolean? = null,
    val localizationReason: String = "",
    val radarStatus: String? = null,
    val collisionImminent: Boolean = false,
    val hasResourceTelemetry: Boolean = false,
    val cellularSignalPercent: Int? = null,
    val cellularState: String = "",
    val cellularAccessTechnology: String = "",
)
