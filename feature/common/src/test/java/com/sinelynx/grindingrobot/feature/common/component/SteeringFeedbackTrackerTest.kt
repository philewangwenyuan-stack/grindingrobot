package com.sinelynx.grindingrobot.feature.common.component

import org.junit.Assert.*
import org.junit.Test

class SteeringFeedbackTrackerTest {
    @Test fun endpointsAndCenterOnlyFireOncePerCrossing() {
        val tracker = SteeringFeedbackTracker()
        assertFalse(tracker.update(0f))
        assertFalse(tracker.update(0.2f))
        assertTrue(tracker.update(1f))
        repeat(20) { assertFalse(tracker.update(1f)) }
        assertFalse(tracker.update(0.5f))
        assertTrue(tracker.update(0f))
        assertFalse(tracker.update(0f))
        assertFalse(tracker.update(-0.6f))
        assertTrue(tracker.update(-1f))
        assertTrue(tracker.update(0f))
    }
}
