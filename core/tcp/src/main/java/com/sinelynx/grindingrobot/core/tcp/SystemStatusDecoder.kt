package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.SystemStatusPayload
import sl_link.SlLink

internal fun decodeSystemStatus(report: SlLink.DeviceStatusReport, arrival: Double): SystemStatusPayload {
    val h = report.systemHealth
    fun Float.valid(range: ClosedFloatingPointRange<Float>) = takeIf { it.isFinite() && it in range }
    return SystemStatusPayload(
        receivedMonotonicS = arrival,
        leftMotorFault = if (report.hasLeftMotorFaultCode()) report.leftMotorFaultCode.takeIf { it in -32768..32767 } else null,
        rightMotorFault = if (report.hasRightMotorFaultCode()) report.rightMotorFaultCode.takeIf { it in -32768..32767 } else null,
        cpuTemperatureC = if (h.hasCpuTemperatureC()) h.cpuTemperatureC.valid(-40f..150f) else null,
        cpuUsagePercent = if (h.hasCpuUsagePercent()) h.cpuUsagePercent.valid(0f..100f) else null,
        memoryUsagePercent = if (h.hasMemoryUsagePercent()) h.memoryUsagePercent.valid(0f..100f) else null,
        memoryTotalBytes = if (h.hasMemoryTotalBytes()) h.memoryTotalBytes.takeIf { it > 0 } else null,
        memoryAvailableBytes = if (h.hasMemoryAvailableBytes()) h.memoryAvailableBytes.takeIf { it >= 0 } else null,
        diskUsagePercent = if (h.hasDiskUsagePercent()) h.diskUsagePercent.valid(0f..100f) else null,
        diskFreeBytes = if (h.hasDiskFreeBytes()) h.diskFreeBytes.takeIf { it >= 0 } else null,
        uptimeSeconds = if (h.hasUptimeSeconds()) h.uptimeSeconds.takeIf { it >= 0 } else null,
        chassisConnected = if (report.hasChassisConnected()) report.chassisConnected else null,
        chassisEnabled = report.chassisEnabled,
        discEnabled = report.discEnabled,
        emergencyStop = if (report.hasEmergencyStopLatched()) report.emergencyStopLatched else null,
        localizationReady = if (report.hasLocalizationReady()) report.localizationReady else null,
        localizationReason = report.localizationStatusReason,
        radarStatus = report.radarSystemStatus.takeIf { report.radarSystemStatusAvailable && it.isNotBlank() },
        collisionImminent = report.collisionImminent,
        hasResourceTelemetry = report.hasSystemHealth(),
        cellularSignalPercent = if (h.hasCellularSignalPercent()) h.cellularSignalPercent.takeIf { it in 0..100 } else null,
        cellularState = h.cellularState,
        cellularAccessTechnology = h.cellularAccessTechnology,
    )
}
