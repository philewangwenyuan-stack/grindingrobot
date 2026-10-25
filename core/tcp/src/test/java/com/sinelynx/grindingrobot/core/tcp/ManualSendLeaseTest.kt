package com.sinelynx.grindingrobot.core.tcp
import org.junit.Test
import org.junit.Assert.*
class ManualSendLeaseTest {
    @Test fun newerInputStopAndDisconnectInvalidateQueuedMovement() {
        val gate = ManualSendLease()
        val old = gate.issue(100, false)
        assertTrue(gate.valid(old, 599))
        assertFalse(gate.valid(old, 601))
        val stop = gate.issue(200, true)
        assertFalse(gate.valid(old, 200))
        assertTrue(gate.valid(stop, 10_000))
        val fresh = gate.issue(300, false)
        assertTrue(gate.valid(stop, 300))
        assertTrue(gate.valid(fresh, 300))
        gate.cancel()
        assertFalse(gate.valid(stop, 10_000))
    }
}
