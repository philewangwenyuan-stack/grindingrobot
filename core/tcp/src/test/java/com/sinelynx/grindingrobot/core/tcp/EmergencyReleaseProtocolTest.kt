package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class EmergencyReleaseProtocolTest {
    @Test fun latchReportDistinguishesUnknownNormalAndEmergency() {
        assertFalse(SlLink.DeviceStatusReport.getDefaultInstance().hasEmergencyStopLatched())
        listOf(false, true).forEach { latched ->
            val report = SlLink.DeviceStatusReport.parseFrom(
                SlLink.DeviceStatusReport.newBuilder().setEmergencyStopLatched(latched).build().toByteArray()
            )
            assertTrue(report.hasEmergencyStopLatched())
            assertEquals(latched, report.emergencyStopLatched)
        }
    }
    @Test fun falseRemainsAnExplicitEmergencyCommandAfterWireRoundTrip() {
        val command = SlLink.ControlCommand.parseFrom(buildEmergencyStopCommand(false).toByteArray())
        assertTrue(command.hasEmergencyStop())
        assertFalse(command.emergencyStop.enabled)
        assertEquals(1, command.allFields.size)
    }

    @Test fun stopStillSendsEnabledTrueWithoutOtherActions() {
        val command = SlLink.ControlCommand.parseFrom(buildEmergencyStopCommand(true).toByteArray())
        assertTrue(command.hasEmergencyStop())
        assertTrue(command.emergencyStop.enabled)
        assertEquals(1, command.allFields.size)
    }

    @Test fun releaseCannotBeConfirmedByUnsequencedOrWrongSourceReply() {
        val tracker = CommandReplyTracker()
        val frame = ByteArray(10).apply { this[4] = 42; this[9] = 16 }
        val ticket = tracker.register(frame, "")
        val reply = CommandReply(0, "released")
        assertNull(tracker.complete(0, 16, null, reply, requireExactAck = true))
        assertNull(tracker.complete(42, 17, null, reply, requireExactAck = true))
        assertFalse(ticket.reply.isCompleted)
        assertNotNull(tracker.complete(42, 16, null, reply, requireExactAck = true))
        assertTrue(ticket.reply.isCompleted)
    }
}
