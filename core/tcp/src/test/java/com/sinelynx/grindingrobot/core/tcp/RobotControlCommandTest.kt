package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Test
import sl_link.SlLink

class RobotControlCommandTest {

    @Test
    fun manualDrive_includesMaximumStraightLineSpeed() {
        val command = SlLink.ControlCommand.parseFrom(
            buildRobotControlCommand(
                remoteX = 0.25f,
                remoteY = -0.75f,
                speed = 0.8f,
                maxSpeedMps = 0.15f
            ).toByteArray()
        )

        assertEquals(0.25f, command.manualDrive.remoteX, 0f)
        assertEquals(-0.75f, command.manualDrive.remoteY, 0f)
        assertEquals(0.8f, command.manualDrive.speedRatio, 0f)
        assertEquals(0.15f, command.manualDrive.maxSpeedMps, 0f)
    }

    @Test
    fun manualDrive_keepsLegacyMaximumSpeedByDefault() {
        val command = buildRobotControlCommand(
            remoteX = 0f,
            remoteY = 1f,
            speed = 1f
        )

        assertEquals(0f, command.manualDrive.maxSpeedMps, 0f)
    }
}
