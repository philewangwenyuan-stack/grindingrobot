package com.sinelynx.grindingrobot.feature.device.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusPresentationTest {
    @Test fun absentLocalizationDoesNotPretendToBeHealthy() {
        assertEquals("—", navigationRadarLabel(null, "", false))
        assertEquals("—", navigationRadarLabel(false, "", true))
        assertEquals("—", DeviceStatusUiState().navRadarStatus)
    }

    @Test fun readyWatchdogAndLegacyValidPoseAreHealthy() {
        assertEquals("正常", navigationRadarLabel(true, "", true))
        assertEquals("正常", navigationRadarLabel(null, "", true))
        // A recovered watchdog is healthy even when an earlier task pause remains latched.
        assertEquals("正常", navigationRadarLabel(true, "registration_quality_failed", true))
    }

    @Test fun confirmedQualityFailureIsUnconverged() {
        for (reason in listOf("registration_quality_failed", "pose_jump_exceeded", "localization_data_invalid")) {
            assertEquals("未收敛", navigationRadarLabel(false, reason, true))
        }
    }

    @Test fun missingTfOrOdomOverridesAnOldPose() {
        for (reason in listOf("lio_odom_unhealthy", "map_to_base_tf_invalid", "registration_diagnostics_stale")) {
            assertEquals("—", navigationRadarLabel(false, reason, true))
        }
    }

    @Test fun mapNameBelongsToLoadedMapNotFirstCatalogEntry() {
        val names = mapOf("map-a" to "B1", "map-b" to "B2")
        assertEquals("B2", loadedMapLabel(true, "map-b", names))
        assertEquals("暂无地图", loadedMapLabel(false, "map-a", names))
        assertEquals("map-c", loadedMapLabel(true, "map-c", names))
        assertEquals("—", loadedMapLabel(true, "LIVE_MAP", names))
        assertEquals("—", loadedMapLabel(true, null, names))
    }
}
