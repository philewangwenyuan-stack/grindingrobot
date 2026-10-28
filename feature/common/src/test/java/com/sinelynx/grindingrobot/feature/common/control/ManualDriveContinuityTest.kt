package com.sinelynx.grindingrobot.feature.common.control

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real JVM scheduling; no TCP, Android UI, or physical robot. */
class ManualDriveContinuityTest {
    @Test fun heldTravelRetainsIntentUntilExplicitRelease() = runBlocking {
        val sent = mutableListOf<Pair<Long, ManualDriveTarget>>()
        val controller = ManualDriveController(this, { true }, {
            sent.add(System.nanoTime() to it); true
        }, {})
        controller.refreshAvailability()
        controller.travel(TravelDirection.FORWARD)
        try {
            delay(500L)
            assertTrue(controller.state.hasMotion)
            assertEquals("The background transport owns wire renewal", 1, sent.size)
            assertTrue(sent.all { it.second.speedRatio > 0f })
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
