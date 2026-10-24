package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import com.sinelynx.grindingrobot.feature.common.component.LocalManualFeedbackAllowed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveLifecycle
import com.sinelynx.grindingrobot.feature.common.component.ManualRemoteControlContent
import com.sinelynx.grindingrobot.feature.main.ui.component.ConfirmOverlayDialog
import com.sinelynx.grindingrobot.feature.main.viewmodel.RemoteSettingsViewModel

@Composable
fun RemoteSettingsScreen(viewModel: RemoteSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    ManualDriveLifecycle(viewModel::onScreenEnter, viewModel::onScreenExit)
    CompositionLocalProvider(LocalManualFeedbackAllowed provides viewModel::acceptsManualFeedback) {
    ManualRemoteControlContent(
        state = uiState.drive, discRpm = uiState.turnCount,
        onTravel = viewModel::onTravelClick,
        onSteering = viewModel::onSteeringChanged,
        onSteeringCancel = viewModel::onSteeringCancelled,
        onStop = viewModel::stopControl,
        onSpeedChange = viewModel::updateRunSpeed,
        onTurnChange = viewModel::updateTurnSpeed,
        onDiscChange = viewModel::updateTurnCountDraft,
        onDiscFinished = viewModel::commitTurnCount,
        modifier = modifier.fillMaxSize(),
    )
    }
    if (uiState.showAutoModeJoystickDialog) {
        ConfirmOverlayDialog(
            title = "确定退出自动驾驶?",
            message = "机器人将停止自动研磨，切换为手动操作。",
            confirmText = "确 定", cancelText = "取 消",
            onConfirm = viewModel::confirmAutoModeJoystickDialog,
            onCancel = viewModel::cancelAutoModeJoystickDialog,
        )
    }
}
