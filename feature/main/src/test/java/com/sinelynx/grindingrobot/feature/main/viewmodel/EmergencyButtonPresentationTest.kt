package com.sinelynx.grindingrobot.feature.main.viewmodel

import org.junit.Assert.*
import org.junit.Test

class EmergencyButtonPresentationTest {
    @Test fun normalStateOffersStop() {
        assertEquals("紧急制动", emergencyButtonPresentation(false, false, false).label)
        assertFalse(emergencyButtonPresentation(false, false, false).releaseAction)
    }
    @Test fun actualLatchOffersRelease() {
        val button = emergencyButtonPresentation(true, false, false)
        assertEquals("解除急停", button.label)
        assertTrue(button.releaseAction)
    }
    @Test fun missingOrDisconnectedStateDoesNotGuessRelease() {
        assertFalse(emergencyButtonPresentation(null, false, false).releaseAction)
    }
    @Test fun pendingStopCannotSendRelease() {
        val button = emergencyButtonPresentation(true, true, false)
        assertFalse(button.enabled)
        assertFalse(button.releaseAction)
    }
    @Test fun waitingReleaseKeepsEmergencyStopAvailable() {
        val button = emergencyButtonPresentation(true, false, true)
        assertTrue(button.enabled)
        assertFalse(button.releaseAction)
        assertEquals("紧急制动", button.label)
    }
}
