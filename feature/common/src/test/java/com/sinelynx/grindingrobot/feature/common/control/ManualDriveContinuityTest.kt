package com.sinelynx.grindingrobot.feature.common.control

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real JVM scheduling; no TCP, Android UI, or physical robot. */
class ManualDriveContinuityTest {
    @Test fun heldTravelHasNoDurationLimitAndReleaseEndsRenewal() = runBlocking {
        val sent = mutableListOf<Pair<Long, ManualDriveTarget>>()
        val controller = ManualDriveController(this, { true }, {
            sent.add(System.nanoTime() to it); true
        }, {})
        controller.refreshAvailability()
        controller.travel(TravelDirection.FORWARD)
        try {
            delay(5_000L)
            assertTrue(controller.state.hasMotion)
            assertTrue("Continuous renewal missing", sent.size >= 40)
            assertTrue(sent.all { it.second.speedRatio > 0f })
            val maxGapMs = sent.zipWithNext { a, b -> (b.first - a.first) / 1_000_000.0 }.maxOrNull()!!
            assertTrue("500 ms watchdog gap: $maxGapMs", maxGapMs < 500.0)
            controller.stop()
            val count = sent.size
            assertEquals(0f, sent.last().second.speedRatio, 0f)
            delay(250L)
            assertEquals(count, sent.size)
        } finally {
            controller.stop()
        }
    }
}
