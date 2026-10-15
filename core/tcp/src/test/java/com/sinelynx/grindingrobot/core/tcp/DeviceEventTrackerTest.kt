package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.data.state.DeviceEventLevel
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class DeviceEventTrackerTest {
    private val events = mutableListOf<Pair<DeviceEventLevel, String>>()
    private val tracker = DeviceEventTracker { level, title -> events += level to title }

    @Test fun failedAttemptsAndDuplicateCallbacksDoNotFloodHistory() {
        tracker.onDisconnected()
        assertTrue(events.isEmpty())
        tracker.onConnected()
        tracker.onConnected()
        tracker.onDisconnected()
        tracker.onDisconnected()
        tracker.onConnected()
        assertEquals(listOf("APP 与工控机已连接", "APP 与工控机连接已断开", "APP 与工控机已重新连接"), events.map { it.second })
    }

    @Test fun collisionIsRecordedOnEdgesIncludingFirstActiveSnapshot() {
        tracker.onConnected()
        tracker.onStatus(true, null)
        tracker.onStatus(true, null)
        tracker.onStatus(false, null)
        tracker.onStatus(false, null)
        tracker.onStatus(true, null)
        assertEquals(4, events.size)
        assertEquals(DeviceEventLevel.WARNING, events[1].first)
        assertEquals("RPP 障碍停车已解除", events[2].second)
    }

    @Test fun absentChassisFieldNeverBecomesAnAlarm() {
        tracker.onConnected()
        tracker.onStatus(false, null)
        tracker.onStatus(false, null)
        tracker.onStatus(false, true)
        assertEquals(1, events.size)
        tracker.onStatus(false, false)
        tracker.onStatus(false, false)
        tracker.onStatus(false, true)
        tracker.onStatus(false, true)
        assertEquals(listOf("工控机与底盘连接已断开", "工控机与底盘连接已恢复"), events.drop(1).map { it.second })
    }

    @Test fun firstDisconnectedChassisSnapshotIsVisibleAndTransportLossIsNotChassisLoss() {
        tracker.onConnected()
        tracker.onStatus(false, false)
        tracker.onDisconnected()
        tracker.onStatus(true, false)
        assertEquals(3, events.size)
        assertEquals("APP 与工控机连接已断开", events.last().second)
    }

    @Test fun chassisPresenceSurvivesWireEncodingIncludingFalse() {
        val old = SlLink.DeviceStatusReport.parseFrom(SlLink.DeviceStatusReport.newBuilder().build().toByteArray())
        assertFalse(old.hasChassisConnected())
        for (connected in listOf(false, true)) {
            val report = SlLink.DeviceStatusReport.parseFrom(
                SlLink.DeviceStatusReport.newBuilder().setChassisConnected(connected).build().toByteArray()
            )
            assertTrue(report.hasChassisConnected())
            assertEquals(connected, report.chassisConnected)
        }
    }
}
