package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sinelynx.grindingrobot.core.designsystem.theme.LogoIcon
import com.sinelynx.grindingrobot.core.util.VersionUtils
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.device.ui.DeviceStatusScreen
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceStatusViewModel
import com.sinelynx.grindingrobot.feature.main.R
import com.sinelynx.grindingrobot.feature.main.ui.component.ConfirmOverlayDialog
import com.sinelynx.grindingrobot.feature.main.viewmodel.HomeModuleTab
import com.sinelynx.grindingrobot.feature.main.viewmodel.HomeViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.JobStatisticsViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.RemoteSettingsViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.RobotSettingsViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskRecordViewModel
import com.sinelynx.grindingrobot.feature.map.ui.MapHomeScreen
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapHomeViewModel
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapModeStartEvent
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import kotlinx.coroutines.flow.collect

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddMap: () -> Unit,
    onEditMap: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.uiState.collectAsState()
    val mapHomeViewModel: MapHomeViewModel = hiltViewModel()
    val mapState by mapHomeViewModel.uiState.collectAsState()
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var showEmergencyConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mapHomeViewModel) {
        mapHomeViewModel.mapModeEvents.collect { event ->
            if (event == MapModeStartEvent.Ready) {
                onAddMap()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        HomeTopBar(
            onCloseClick = { showDisconnectConfirmDialog = true },
            onEmergencyClick = { showEmergencyConfirmDialog = true }
        )

        LeftRail(
            selectedTab = homeState.selectedTab,
            onTabSelected = viewModel::onTabSelected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 62.dp)
        )

        when (homeState.selectedTab) {
            HomeModuleTab.Map -> {
                MapHomeScreen(
                    maps = mapState.maps,
                    metricsByMapId = mapState.metricsByMapId,
                    previewByMapId = mapState.previewByMapId,
                    isPreviewLoading = mapState.isPreviewLoading,
                    grindingSession = mapState.grindingSession,
                    grindingLegacyPreview = mapState.grindingLegacyPreview,
                    isMapCatalogLoading = mapState.isMapCatalogLoading,
                    isStartingMapping = mapState.isStartingMapping,
                    robotPose = mapState.robotPose,
                    existingTaskNames = mapState.existingTaskNames,
                    robotWidth = mapState.robotWidth,
                    robotLength = mapState.robotLength,
                    robotFootprint = mapState.robotFootprint,
                    isRelocalizationDialogVisible = mapState.isRelocalizationDialogVisible,
                    isRelocalizationSuccessful = mapState.isRelocalizationSuccessful,
                    relocalizationRawStatus = mapState.relocalizationRawStatus,
                    relocalizationRunSpeed = mapState.relocalizationRunSpeed,
                    relocalizationTurnSpeed = mapState.relocalizationTurnSpeed,
                    relocalizationTurnCount = mapState.relocalizationTurnCount,
                    relocalizationSettingsLoadState = mapState.relocalizationSettingsLoadState,
                    relocalizationSettingsError = mapState.relocalizationSettingsError,
                    onEnter = mapHomeViewModel::requestMapCatalogOnEnter,
                    onAddMap = mapHomeViewModel::startMappingMode,
                    onEditMap = onEditMap,
                    onDeleteMap = mapHomeViewModel::deleteMapByMapId,
                    onRequestMapMetrics = mapHomeViewModel::requestMapMetrics,
                    onOpenStartGrinding = mapHomeViewModel::openStartGrinding,
                    onCloseStartGrinding = mapHomeViewModel::closeStartGrinding,
                    onRetryStartGrindingMap = mapHomeViewModel::retryStartGrindingMap,
                    onLeaveStartGrindingPreview = mapHomeViewModel::leaveStartGrindingPreview,
                    onOpenStartGrindingLegacyPreview = mapHomeViewModel::openStartGrindingLegacyPreview,
                    onCloseStartGrindingLegacyPreview = mapHomeViewModel::closeStartGrindingLegacyPreview,
                    onRequestTaskResultPreview = mapHomeViewModel::requestTaskResultPreview,
                    onDismissMapPreview = mapHomeViewModel::dismissMapPreview,
                    onRequestPlanPreview = { mapId, taskId, taskName, regionRepeats, obstacles ->
                        mapHomeViewModel.requestStartGrindingPlanPreview(
                            mapId,
                            taskId,
                            taskName,
                            regionRepeats,
                            obstacles
                        )
                    },
                    onStartTaskConfig = { mapId, taskId, taskName, regionRepeats, obstacles ->
                        val started = mapHomeViewModel.startGrindingTask(
                            mapId,
                            taskId,
                            taskName,
                            regionRepeats,
                            obstacles
                        )
                        if (started) {
                            viewModel.onTabSelected(HomeModuleTab.DeviceHome)
                        }
                        started
                    },
                    onRadarMapSync = mapHomeViewModel::requestRelocalizationRadarMapSync,
                    onRadarRelocalization = mapHomeViewModel::requestRadarRelocalization,
                    onStartRelocalization = mapHomeViewModel::startRelocalization,
                    onDismissRelocalization = mapHomeViewModel::dismissRelocalization,
                    onRetryRelocalizationSettings = mapHomeViewModel::retryRelocalizationSettingsRead,
                    onRelocalizationRunSpeedDecrease = mapHomeViewModel::decreaseRelocalizationRunSpeed,
                    onRelocalizationRunSpeedIncrease = mapHomeViewModel::increaseRelocalizationRunSpeed,
                    onRelocalizationTurnSpeedDecrease = mapHomeViewModel::decreaseRelocalizationTurnSpeed,
                    onRelocalizationTurnSpeedIncrease = mapHomeViewModel::increaseRelocalizationTurnSpeed,
                    onRelocalizationTurnCountDecrease = mapHomeViewModel::decreaseRelocalizationTurnCount,
                    onRelocalizationTurnCountIncrease = mapHomeViewModel::increaseRelocalizationTurnCount,
                    onRelocalizationTurnCountChange = mapHomeViewModel::updateRelocalizationTurnCountDraft,
                    onRelocalizationTurnCountChangeFinished = mapHomeViewModel::commitRelocalizationTurnCount,
                    onRelocalizationCommandStart = mapHomeViewModel::onRelocalizationCommandStart,
                    onRelocalizationCommandEnd = mapHomeViewModel::onRelocalizationCommandEnd,
                    onRelocalizationPositionChanged = mapHomeViewModel::onRelocalizationPositionChanged,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            HomeModuleTab.DeviceHome -> {
                val deviceStatusViewModel: DeviceStatusViewModel = hiltViewModel()
                DeviceStatusScreen(
                    viewModel = deviceStatusViewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            HomeModuleTab.Remote -> {
                val remoteViewModel: RemoteSettingsViewModel = hiltViewModel()
                RemoteSettingsScreen(
                    viewModel = remoteViewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            HomeModuleTab.TaskRecord -> {
                val taskRecordViewModel: TaskRecordViewModel = hiltViewModel()
                TaskRecordRoute(
                    viewModel = taskRecordViewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            HomeModuleTab.JobStatistics -> {
                val jobStatisticsViewModel: JobStatisticsViewModel = hiltViewModel()
                JobStatisticsRoute(
                    viewModel = jobStatisticsViewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            HomeModuleTab.Robot -> {
                val robotSettingsViewModel: RobotSettingsViewModel = hiltViewModel()
                RobotSettingsScreen(
                    viewModel = robotSettingsViewModel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(start = 104.dp, top = 86.dp, end = 22.dp)
                )
            }

            else -> {
                ModulePlaceholder(
                    title = homeState.selectedTab.label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 40.dp)
                )
            }
        }

        if (showDisconnectConfirmDialog) {
            ConfirmOverlayDialog(
                title = stringResource(R.string.home_disconnect_confirm_title),
                message = stringResource(R.string.home_disconnect_confirm_message),
                confirmText = stringResource(R.string.common_confirm),
                cancelText = stringResource(R.string.common_cancel),
                onConfirm = {
                    showDisconnectConfirmDialog = false
                    viewModel.disconnectTcpAfterStoppingTaskIfNeeded(onDisconnected = onClose)
                },
                onCancel = { showDisconnectConfirmDialog = false }
            )
        }

        if (showEmergencyConfirmDialog) {
            ConfirmOverlayDialog(
                title = stringResource(R.string.home_emergency_confirm_title),
                message = stringResource(R.string.home_emergency_confirm_message),
                confirmText = stringResource(R.string.common_confirm),
                cancelText = stringResource(R.string.common_cancel),
                onConfirm = {
                    showEmergencyConfirmDialog = false
                    //发送紧急制动命令
                    viewModel.setEmergencyStop()
                },
                onCancel = { showEmergencyConfirmDialog = false }
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    onCloseClick: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val context = LocalContext.current
    val appVersionName = remember { VersionUtils.getAppVersionName(context) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                LogoIcon(size = 24.dp, res = CommonR.drawable.ic_topabr_logo)
            }
            Text(
                text = stringResource(R.string.home_app_title),
                fontSize = 20.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
            Text(
                text = "v$appVersionName",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        EmergencyPill(
            onClick = onEmergencyClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DeviceStatusBar()
            Box(
                modifier = Modifier.clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_home_close),
                    contentDescription = stringResource(R.string.home_close_content_description),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onCloseClick)
                )
            }
        }
    }
}

@Composable
private fun EmergencyPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(1000.dp))
            .background(Color(0x1AD82B2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFFD82B2A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
        Text(
            text = stringResource(R.string.home_emergency_stop),
            color = Color(0xFFD82B2A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LeftRail(
    selectedTab: HomeModuleTab,
    onTabSelected: (HomeModuleTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeModuleTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Color(0xFF00A0E9) else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = tab.iconRes()),
                    contentDescription = tab.label,
                    colorFilter = if (selected) {
                        ColorFilter.tint(Color.White)
                    } else {
                        ColorFilter.tint(Color(0xFF9DA3AF))
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun HomeModuleTab.iconRes(): Int = when (this) {
    HomeModuleTab.DeviceHome -> R.drawable.ic_device_checked
    HomeModuleTab.Map -> R.drawable.ic_map_unchecked
    HomeModuleTab.TaskRecord -> R.drawable.ic_task_record_unchecked
    HomeModuleTab.JobStatistics -> R.drawable.ic_job_statistics_unchecked
    HomeModuleTab.Remote -> R.drawable.ic_remote_unchecked
    HomeModuleTab.Robot -> R.drawable.ic_setting_unchecked
}

@Composable
private fun ModulePlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            color = Color(0xFF202937),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_module_placeholder),
            fontSize = 14.sp,
            color = Color(0xFF9DA3AF)
        )
    }
}
