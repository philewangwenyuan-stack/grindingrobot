package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class ChassisSettingsUpdateTest {
    private val current = SlLink.ChassisSettings.newBuilder()
        .setRunSpeed(0.03f).setDiscSpeedRpm(800).setDiscEnabled(false)
        .setWorkMode(SlLink.WorkMode.WORK_MODE_MANUAL).setMaxTurnSpeedRatio(0.7f).build()

    private fun wire(settings: SlLink.ChassisSettings) = SlLink.SettingsWriteRequest.parseFrom(
        SlLink.SettingsWriteRequest.newBuilder().setChassis(settings).build().toByteArray()).chassis

    @Test fun oldPartialDiscWriteDecodesMissingTaskSpeedAsZero() {
        val partial = SlLink.ChassisSettings.newBuilder().setDiscEnabled(true).setDiscSpeedRpm(300).build()
        assertEquals(0f, wire(partial).runSpeed, 0f)
    }

    @Test fun discWritePreservesTaskSpeedTurnGainAndModeOnTheWire() {
        val result = wire(buildDiscSettingsUpdate(current, 300, true)!!)
        assertEquals(0.03f, result.runSpeed, 0f)
        assertEquals(0.7f, result.maxTurnSpeedRatio, 0f)
        assertEquals(current.workMode, result.workMode)
        assertEquals(300, result.discSpeedRpm)
        assertTrue(result.discEnabled)
        assertEquals(800, current.discSpeedRpm)
        assertFalse(current.discEnabled)
    }

    @Test fun zeroDiscRpmDoesNotZeroTaskSpeedOrChangeExistingEnableRule() {
        val result = wire(buildDiscSettingsUpdate(current, 0, true)!!)
        assertEquals(0.03f, result.runSpeed, 0f)
        assertEquals(0, result.discSpeedRpm)
        assertTrue(result.discEnabled)
    }

    @Test fun turningEditPreservesSpeedDiscSetpointEnableAndMode() {
        val result = wire(buildTurnRatioSettingsUpdate(current, 0.5f)!!)
        assertEquals(current.toBuilder().setMaxTurnSpeedRatio(0.5f).build(), result)
    }

    @Test fun unknownSnapshotNeverCreatesAZeroFilledWrite() {
        assertNull(buildDiscSettingsUpdate(null, 300, true))
        assertNull(buildTurnRatioSettingsUpdate(null, 0.5f))
    }

    @Test fun existingIntentionalZeroIsNotSilentlyReplacedWithAMovingSpeed() {
        val stopped = current.toBuilder().setRunSpeed(0f).build()
        assertEquals(0f, wire(buildDiscSettingsUpdate(stopped, 300, false)!!).runSpeed, 0f)
        assertEquals(0f, wire(buildTurnRatioSettingsUpdate(stopped, 0.5f)!!).runSpeed, 0f)
    }

    @Test fun invalidParametersDoNotProduceSettingsWrites() {
        assertNull(buildDiscSettingsUpdate(current, -1, true))
        for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 1.1f)) {
            assertNull(buildTurnRatioSettingsUpdate(current, ratio))
        }
    }
}
