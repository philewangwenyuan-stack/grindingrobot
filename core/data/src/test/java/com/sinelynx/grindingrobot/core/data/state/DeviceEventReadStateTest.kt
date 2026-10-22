package com.sinelynx.grindingrobot.core.data.state

import org.junit.Assert.*
import org.junit.Test

class DeviceEventReadStateTest {
    @Test fun onlyEventsAfterReadPositionAreUnread() {
        val old = DeviceEventRecord(100L, "old", DeviceEventLevel.NORMAL, 101L)
        val fresh = DeviceEventRecord(100L, "new", DeviceEventLevel.WARNING, 102L)
        assertFalse(hasUnreadDeviceEvents(emptyList(), 0L))
        assertTrue(hasUnreadDeviceEvents(listOf(old), 0L))
        assertFalse(hasUnreadDeviceEvents(listOf(old), 101L))
        assertTrue(hasUnreadDeviceEvents(listOf(fresh, old), 101L))
        assertFalse(hasUnreadDeviceEvents(listOf(fresh, old), 102L))
    }

    @Test fun simultaneousMessagesAndClockRollbackStillGetNewIds() {
        assertEquals(101L, nextDeviceEventId(100L, 100L, 99L))
        assertEquals(201L, nextDeviceEventId(50L, 100L, 200L))
        assertEquals(301L, nextDeviceEventId(50L, 0L, 300L))
    }
}
