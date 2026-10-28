package com.sinelynx.grindingrobot.feature.map

import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapHomeUiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.RelocalizationSettingsLoadState
import com.sinelynx.grindingrobot.feature.map.viewmodel.consumeRelocalizationSettingsReadResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sl_link.SlLink

class RelocalizationSettingsReadTest {

    @Test
    fun successfulReadAppliesAllSettingsIncludingZeroValues() {
        val current = MapHomeUiState(
            isRelocalizationDialogVisible = true,
            relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Loading,
            relocalizationRunSpeed = 0.15f,
            relocalizationTurnSpeed = 50,
            relocalizationTurnCount = 800
        )
        val settings = SlLink.ChassisSettings.newBuilder()
            .setRunSpeed(0f)
            .setMaxTurnSpeedRatio(0.7f)
            .setDiscSpeedRpm(0)
            .build()

        val updated = consumeRelocalizationSettingsReadResponse(
            current,
            successfulResponse(settings)
        )

        assertEquals(0f, updated.relocalizationRunSpeed, 0f)
        assertEquals(70, updated.relocalizationTurnSpeed)
        assertEquals(0, updated.relocalizationTurnCount)
        assertEquals(RelocalizationSettingsLoadState.Ready, updated.relocalizationSettingsLoadState)
    }

    @Test
    fun responseAfterInitializationDoesNotOverwritePageValues() {
        val ready = MapHomeUiState(
            isRelocalizationDialogVisible = true,
            relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Ready,
            relocalizationRunSpeed = 0.1f,
            relocalizationTurnSpeed = 80,
            relocalizationTurnCount = 900
        )
        val laterSettings = SlLink.ChassisSettings.newBuilder()
            .setRunSpeed(0f)
            .setMaxTurnSpeedRatio(0.2f)
            .setDiscSpeedRpm(0)
            .build()

        val unchanged = consumeRelocalizationSettingsReadResponse(
            ready,
            successfulResponse(laterSettings)
        )

        assertSame(ready, unchanged)
    }

    @Test
    fun failedOrIncompleteReadKeepsControlsDisabledAndAllowsRetryState() {
        val loading = MapHomeUiState(
            isRelocalizationDialogVisible = true,
            relocalizationSettingsLoadState = RelocalizationSettingsLoadState.Loading
        )

        val failed = consumeRelocalizationSettingsReadResponse(
            loading,
            AppState.SettingsReadResponseState(false, "读取失败", null)
        )
        val incomplete = consumeRelocalizationSettingsReadResponse(
            loading,
            AppState.SettingsReadResponseState(true, "", null)
        )

        assertEquals(RelocalizationSettingsLoadState.Error, failed.relocalizationSettingsLoadState)
        assertEquals("读取失败", failed.relocalizationSettingsError)
        assertEquals(RelocalizationSettingsLoadState.Error, incomplete.relocalizationSettingsLoadState)
        assertEquals("底盘参数响应不完整，请重试", incomplete.relocalizationSettingsError)
    }

    private fun successfulResponse(
        settings: SlLink.ChassisSettings
    ): AppState.SettingsReadResponseState = AppState.SettingsReadResponseState(
        isSuccess = true,
        message = "",
        chassisSettings = settings
    )
}
