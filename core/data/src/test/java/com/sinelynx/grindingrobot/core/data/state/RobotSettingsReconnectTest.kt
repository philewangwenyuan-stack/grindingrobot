package com.sinelynx.grindingrobot.core.data.state

import com.sinelynx.grindingrobot.core.data.repository.UserInfoStoreRepository
import com.sinelynx.grindingrobot.core.datastore.datasource.userinfo.UserInfoStoreDataSource
import com.sinelynx.grindingrobot.core.model.entity.User
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.util.storage.MMKVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink

class RobotSettingsReconnectTest {
    private val scope = CoroutineScope(SupervisorJob())
    private val state = AppState(UserInfoStoreRepository(UnusedUserStore), MMKVUtils, scope)
    private val endpoint = AppState.TcpEndpoint("192.168.1.2", 5000)
    private val footprint = listOf(
        AppState.FootprintPoint(-0.2f, 0.3f), AppState.FootprintPoint(0.8f, 0.3f),
        AppState.FootprintPoint(0.8f, -0.3f), AppState.FootprintPoint(-0.2f, -0.3f)
    )
    private val map = SlLink.MapSettings.newBuilder().setVehicleWidth(0.6f).setVehicleLength(1f)
        .addAllFootprint(footprint.map { SlLink.PolygonPoint.newBuilder().setX(it.x).setY(it.y).build() }).build()

    private fun confirmedSettings() {
        state.prepareDeviceSettings(endpoint)
        state.updateRobotSettings(0.6, 1.0, footprint)
        state.updateMapSetting(map)
        state.updateClassicSettings(SlLink.ChassisSettings.newBuilder().setRunSpeed(0.12f).build())
        state.updateRppSettings(SlLink.RppSettings.newBuilder().setDesiredLinearVel(0.08f).build())
    }

    @After fun cleanup() { scope.cancel(); DeviceStatusStream.reset() }

    @Test fun disconnectClearsLivePoseAndVideoButKeepsConfirmedFootprint() {
        confirmedSettings()
        DeviceStatusStream.publishPose(DevicePosePayload(2f, 3f, 45f))
        state.updateVideoStreamInfo(AppState.VideoStreamInfo(0, "", "rtsp://device/live", true))
        state.clearDeviceStatus()
        assertEquals(footprint, state.robotSettings.value?.footprint)
        assertEquals(map, state.mapSettings.value)
        assertNull(DeviceStatusStream.pose.value)
        assertNull(state.videoStreamInfo.value)
    }

    @Test fun automaticReconnectToSameDevicePreservesTemporaryGeometry() {
        confirmedSettings()
        state.clearDeviceStatus()
        state.prepareDeviceSettings(endpoint.copy(host = " 192.168.1.2 "))
        assertEquals(footprint, state.robotSettings.value?.footprint)
        assertEquals(map, state.mapSettings.value)
    }

    @Test fun changingHostOrPortCannotReusePreviousDeviceSettings() {
        listOf(endpoint.copy(host = "192.168.1.3"), endpoint.copy(port = 6000)).forEach { next ->
            confirmedSettings()
            state.prepareDeviceSettings(next)
            assertNull(state.robotSettings.value)
            assertNull(state.mapSettings.value)
            assertNull(state.chassisSettings.value)
            assertNull(state.rppSettings.value)
        }
    }

    @Test fun freshDeviceReplyReplacesRetainedGeometry() {
        confirmedSettings()
        state.clearDeviceStatus()
        state.prepareDeviceSettings(endpoint)
        val fresh = footprint.map { it.copy(x = it.x + 0.1f) }
        state.updateRobotSettings(0.6, 1.0, fresh)
        assertEquals(fresh, state.robotSettings.value?.footprint)
    }

    @Test fun replyWithoutPolygonDoesNotEraseConfirmedFootprint() {
        confirmedSettings()
        state.clearDeviceStatus()
        state.prepareDeviceSettings(endpoint)
        state.updateRobotSettings(0.6, 1.0)
        assertEquals(footprint, state.robotSettings.value?.footprint)
    }

    private object UnusedUserStore : UserInfoStoreDataSource {
        override suspend fun saveUserInfo(user: User) = error("unused")
        override suspend fun getUserInfo(): User? = error("unused")
        override suspend fun updateUserInfo(updates: Map<String, Any?>) = error("unused")
        override suspend fun clearUserInfo() = error("unused")
        override suspend fun getUserId(): Long = error("unused")
        override suspend fun getNickName(): String? = error("unused")
        override suspend fun getAvatarUrl(): String? = error("unused")
    }
}
