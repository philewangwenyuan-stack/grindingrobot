package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.sinelynx.grindingrobot.core.tcp.DeviceCommandResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class EmergencyReleaseControllerTest {
    @Test fun waitsForConfirmationAndDeduplicatesRepeatedClicks() = runBlocking {
        val gate = CompletableDeferred<DeviceCommandResult>()
        val states = mutableListOf<EmergencyReleaseUiState>()
        var sends = 0
        val controller = EmergencyReleaseController(this, { sends++; gate.await() }, states::add)
        controller.request()
        controller.request()
        yield()
        assertEquals(1, sends)
        assertTrue(states.last().pending)
        assertNull(states.last().title)
        gate.complete(DeviceCommandResult.Confirmed("released; stopped"))
        yield()
        assertFalse(states.last().pending)
        assertTrue(states.last().message!!.contains("需要重新点击启动"))
        assertTrue(states.last().message!!.contains("released; stopped"))
    }

    @Test fun newEmergencyInvalidatesPendingReleaseAndLateReply() = runBlocking {
        val gate = CompletableDeferred<DeviceCommandResult>()
        val states = mutableListOf<EmergencyReleaseUiState>()
        val controller = EmergencyReleaseController(this, { withContext(NonCancellable) { gate.await() } }, states::add)
        controller.request()
        yield()
        controller.cancelForEmergencyStop()
        gate.complete(DeviceCommandResult.Confirmed("late release"))
        yield()
        assertFalse(states.last().pending)
        assertNull(states.last().title)
        assertNull(states.last().message)
    }

    @Test fun boardRefusalIsShownIncludingTestModeLatchReason() {
        val reason = "Only test indicator clear requested; motion remains latched"
        val state = emergencyReleaseOutcome(DeviceCommandResult.Rejected(reason))
        assertEquals("解除急停被拒绝", state.title)
        assertEquals(reason, state.message)
    }

    @Test fun timeoutAndDisconnectionNeverClaimReleaseSucceeded() {
        val timeout = emergencyReleaseOutcome(DeviceCommandResult.TimedOut)
        val failure = emergencyReleaseOutcome(DeviceCommandResult.SendFailed)
        assertEquals("解除急停未收到确认", timeout.title)
        assertTrue(timeout.message!!.contains("结果未知"))
        assertEquals("解除急停发送失败", failure.title)
    }
}
