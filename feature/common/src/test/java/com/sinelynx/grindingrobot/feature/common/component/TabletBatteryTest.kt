package com.sinelynx.grindingrobot.feature.common.component
import org.junit.Test
import org.junit.Assert.*
class TabletBatteryTest {
    @Test fun unknownInvalidAndRealZeroAreDifferent() {
        assertNull(validTabletBatteryPercent(-1,100))
        assertNull(validTabletBatteryPercent(50,0))
        assertNull(validTabletBatteryPercent(101,100))
        assertEquals(0,validTabletBatteryPercent(0,100))
        assertEquals(50,validTabletBatteryPercent(1,2))
    }
}
