package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class SystemStatusDecoderTest {
    @Test fun oldFirmwareDoesNotBecomeHealthyZeroValues() {
        val status = decodeSystemStatus(SlLink.DeviceStatusReport.getDefaultInstance(), 10.0)
        assertNull(status.leftMotorFault)
        assertNull(status.rightMotorFault)
        assertNull(status.cpuUsagePercent)
        assertNull(status.chassisConnected)
        assertFalse(status.hasResourceTelemetry)
    }

    @Test fun signedFaultsAndExplicitZerosSurviveWireRoundTrip() {
        val wire = SlLink.DeviceStatusReport.newBuilder().setLeftMotorFaultCode(-32768)
            .setRightMotorFaultCode(0).setChassisConnected(true)
            .setSystemHealth(SlLink.SystemHealth.newBuilder().setCpuUsagePercent(0f)
                .setCpuTemperatureC(62.5f).setMemoryTotalBytes(8589934592L)
                .setMemoryAvailableBytes(4294967296L).setUptimeSeconds(0)
                .setCellularSignalPercent(0).setCellularState("connected").setCellularAccessTechnology("lte")).build().toByteArray()
        val status = decodeSystemStatus(SlLink.DeviceStatusReport.parseFrom(wire), 10.0)
        assertEquals(-32768, status.leftMotorFault)
        assertEquals(0, status.rightMotorFault)
        assertEquals(0f, status.cpuUsagePercent!!, 0f)
        assertEquals(62.5f, status.cpuTemperatureC!!, 0f)
        assertEquals(8589934592L, status.memoryTotalBytes)
        assertEquals(0L, status.uptimeSeconds)
        assertTrue(status.hasResourceTelemetry)
        assertEquals(0, status.cellularSignalPercent)
        assertEquals("lte", status.cellularAccessTechnology)
    }

    @Test fun invalidValuesAreUnknownAndDisconnectClearsSnapshot() {
        val report = SlLink.DeviceStatusReport.newBuilder().setLeftMotorFaultCode(32768)
            .setSystemHealth(SlLink.SystemHealth.newBuilder().setCpuUsagePercent(Float.NaN)
                .setMemoryUsagePercent(101f).setCpuTemperatureC(Float.POSITIVE_INFINITY)).build()
        val status = decodeSystemStatus(report, 10.0)
        assertNull(status.leftMotorFault)
        assertNull(status.cpuUsagePercent)
        assertNull(status.memoryUsagePercent)
        assertNull(status.cpuTemperatureC)
        DeviceStatusStream.publishSystemStatus(status)
        DeviceStatusStream.reset()
        assertNull(DeviceStatusStream.systemStatus.value)
    }
}
