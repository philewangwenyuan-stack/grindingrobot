package com.sinelynx.grindingrobot.feature.common.control

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ManualDriveControllerTest {
    @Test fun steadyTravelIsRenewedAndExplicitStopCancelsRenewal() = runBlocking {
        val sent = mutableListOf<ManualDriveTarget>()
        val control = ManualDriveController(this, { true }, { sent.add(it); true }, {})
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        delay(150L)
        assertTrue(sent.size >= 2)
        control.stop()
        val afterStop = sent.size
        assertEquals(0f, sent.last().speedRatio, 0f)
        delay(120L)
        assertEquals(afterStop, sent.size)
    }
    @Test fun everyPercentOfEitherSteeringDirectionReducesTranslationLinearly() {
        for (direction in listOf(TravelDirection.FORWARD, TravelDirection.BACKWARD)) {
            for (percent in -100..100) {
                val input = percent / 100f
                val state = ManualDriveState(direction = direction, steering = input)
                val expected = 0.05f * (1f - abs(input))
                assertEquals("steering=$percent", expected, state.commandedSpeedMps, 0.000001f)
                assertEquals(direction.protocolSign * expected / 0.15f, state.target().remoteY, 0.000001f)
                assertEquals(input, state.target().remoteX, 0f)
                assertEquals(0.70f, state.target().maxTurnSpeedRatio, 0f)
            }
        }
    }

    @Test fun proportionalMixFitsBothWheelLimitsWithoutRadialRescaling() {
        for (speed in listOf(0.02f, 0.05f, 0.15f)) {
            for (gain in listOf(1, 70, 100)) {
                for (percent in -100..100) {
                    val target = ManualDriveState(direction = TravelDirection.FORWARD,
                        steering = percent / 100f, speedMps = speed, turnPercent = gain).target()
                    assertTrue(abs(target.remoteY) + abs(target.remoteX) * target.maxTurnSpeedRatio <= 1.000001f)
                }
            }
        }
    }

    @Test fun fullSteeringIsPureRotationAndReleaseRestoresLatchedTravel() = runBlocking {
        val sent = mutableListOf<ManualDriveTarget>()
        val control = ManualDriveController(this, { true }, { sent.add(it); true }, {})
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        yield()
        control.steer(1f, control.state.inputEpoch)
        assertEquals(0f, sent.last().remoteY, 0f)
        assertEquals(1f, sent.last().remoteX, 0f)
        assertEquals(1f, sent.last().speedRatio, 0f)
        control.steer(0.2f, control.state.inputEpoch)
        assertEquals(-0.05f * 0.8f / 0.15f, sent.last().remoteY, 0.000001f)
        control.steer(0f, control.state.inputEpoch)
        assertEquals(-0.05f / 0.15f, sent.last().remoteY, 0.000001f)
        control.stop()
    }

    @Test fun oppositeButtonStopsBothAxesAndRequiresAnotherClick() = runBlocking {
        val control = ManualDriveController(this, { true }, { true }, {})
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        control.steer(0.2f, control.state.inputEpoch)
        val oldEpoch = control.state.inputEpoch
        control.travel(TravelDirection.BACKWARD)
        assertEquals(TravelDirection.STOPPED, control.state.direction)
        assertEquals(0f, control.state.steering, 0f)
        control.steer(0.9f, oldEpoch)
        assertFalse(control.state.hasMotion)
        control.travel(TravelDirection.BACKWARD)
        assertEquals(TravelDirection.BACKWARD, control.state.direction)
        assertTrue(control.state.target().remoteY > 0f)
        control.stop()
    }

    @Test fun stopBypassesPendingPreparationAndLateAckCannotReplayTarget() = runBlocking {
        val ack = CompletableDeferred<Boolean>()
        val sent = mutableListOf<ManualDriveTarget>()
        val control = ManualDriveController(this, { true }, { sent.add(it); true }, {},
            prepare = { withContext(NonCancellable) { ack.await() } })
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        yield()
        control.stop()
        assertEquals(0f, sent.single().speedRatio, 0f)
        ack.complete(true)
        yield()
        assertTrue(sent.all { it.remoteX == 0f && it.remoteY == 0f && it.speedRatio == 0f })
        assertFalse(control.state.hasMotion)
    }

    @Test fun preparationUsesNewestTargetAndFailureClearsLatch() = runBlocking {
        val ack = CompletableDeferred<Boolean>()
        val sent = mutableListOf<ManualDriveTarget>()
        val control = ManualDriveController(this, { true }, { sent.add(it); true }, {}, prepare = { ack.await() })
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        yield()
        control.steer(0.2f, control.state.inputEpoch)
        control.setSpeed(0.10f)
        ack.complete(true)
        yield()
        assertEquals(0.2f, sent.single().remoteX, 0f)
        assertEquals(-0.1f * 0.8f / 0.15f, sent.single().remoteY, 0.000001f)
        control.stop()
        var failed = false
        val rejected = ManualDriveController(this, { true }, { true }, {}, prepare = { false },
            onPreparationFailed = { failed = true })
        rejected.refreshAvailability()
        rejected.travel(TravelDirection.FORWARD)
        yield()
        assertTrue(failed)
        assertFalse(rejected.state.hasMotion)
    }

    @Test fun disconnectAndReconnectNeverRestoreOldMotionOrGesture() = runBlocking {
        var available = true
        val control = ManualDriveController(this, { available }, { true }, {})
        control.refreshAvailability()
        control.travel(TravelDirection.FORWARD)
        control.steer(0.7f, control.state.inputEpoch)
        val epoch = control.state.inputEpoch
        available = false
        control.refreshAvailability()
        available = true
        control.refreshAvailability()
        control.steer(0.9f, epoch)
        assertTrue(control.state.enabled)
        assertFalse(control.state.hasMotion)
    }

    @Test fun pureRotationIsMotionAndAbnormalGestureCancellationStopsLatchedTravel() = runBlocking {
        val control = ManualDriveController(this, { true }, { true }, {})
        control.refreshAvailability()
        control.steer(-0.01f, control.state.inputEpoch)
        assertTrue(control.state.hasMotion)
        assertEquals(0f, control.state.target().remoteY, 0f)
        control.steer(0f, control.state.inputEpoch)
        assertFalse(control.state.hasMotion)
        control.travel(TravelDirection.FORWARD)
        control.cancelSteering(control.state.inputEpoch)
        assertFalse(control.state.hasMotion)
    }
}
