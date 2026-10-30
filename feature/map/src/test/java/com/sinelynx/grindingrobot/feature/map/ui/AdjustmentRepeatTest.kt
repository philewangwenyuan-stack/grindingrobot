package com.sinelynx.grindingrobot.feature.map.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentRepeatTest {
    @Test fun shortPressMakesOneStepAndCancellationStopsFurtherSteps() = runBlocking {
        var steps = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) { repeatAdjustment { steps++ } }
        assertEquals(1, steps)
        job.cancelAndJoin()
        delay(400)
        assertEquals(1, steps)
    }

    @Test fun heldPressRepeatsAndReleaseStops() = runBlocking {
        var position = 0f
        val job = launch(start = CoroutineStart.UNDISPATCHED) { repeatAdjustment { position += 0.05f } }
        delay(620)
        assertTrue(position >= 0.15f)
        job.cancelAndJoin()
        val released = position
        delay(150)
        assertEquals(released, position, 0f)
    }
}
