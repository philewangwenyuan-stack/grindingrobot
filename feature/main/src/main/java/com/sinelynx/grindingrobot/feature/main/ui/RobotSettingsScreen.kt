package com.sinelynx.grindingrobot.feature.main.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import com.sinelynx.grindingrobot.feature.main.R
import com.sinelynx.grindingrobot.feature.main.ui.component.ClearCacheSelectionDialog
import com.sinelynx.grindingrobot.feature.main.ui.component.ConfirmOverlayDialog
import com.sinelynx.grindingrobot.feature.main.ui.component.DownloadProgressDialog
import com.sinelynx.grindingrobot.feature.main.ui.component.UpdateAvailableDialog
import com.sinelynx.grindingrobot.feature.main.viewmodel.ClearCacheCategory
import com.sinelynx.grindingrobot.feature.main.viewmodel.BaseLaserField
import com.sinelynx.grindingrobot.feature.main.viewmodel.FootprintPointUi
import com.sinelynx.grindingrobot.feature.main.viewmodel.PathPlanningSettingsUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.PathPlanningTextField
import com.sinelynx.grindingrobot.feature.main.viewmodel.RppSettingsUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.RppTextField
import com.sinelynx.grindingrobot.feature.main.viewmodel.RppToggleField
import com.sinelynx.grindingrobot.feature.main.viewmodel.RobotSettingsUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.RobotSettingsViewModel
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils

/**
 * 设置菜单项枚举
 */
private enum class SettingsMenuItem(val label: String) {
    SystemUpgrade("系统升级"),
    ClearCache("清除缓存"),
    RobotConfig("机器设置"),
    NavigationConfig("导航参数"),
    PathPlanning("路径规划")
}

@Composable
fun RobotSettingsScreen(
    viewModel: RobotSettingsViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.requestSettingRead()
    }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) ToastUtils.showReplacingError("通知权限未开启，安装后请手动打开 APP")
        viewModel.startDownload()
    }
    val startDownloadWithPermission = {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startDownload()
        }
    }
    RobotSettingsContent(
        uiState = uiState,
        onDecreaseWidth = viewModel::decreaseWidth,
        onIncreaseWidth = viewModel::increaseWidth,
        onDecreaseLength = viewModel::decreaseLength,
        onIncreaseLength = viewModel::increaseLength,
        onStartDownload = startDownloadWithPermission,
        onCheckUpdate = viewModel::checkUpdate,
        onDismissUpdateDialog = viewModel::dismissUpdateDialog,
        onDismissUpdateFailedDialog = viewModel::dismissUpdateFailedDialog,
        onClearCache = viewModel::showClearCacheDialog,
        onConfirmClearCache = viewModel::confirmClearCache,
        onDismissClearCacheDialog = viewModel::dismissClearCacheDialog,
        onFootprintPointChange = viewModel::updateFootprintPoint,
        onBaseLaserChange = viewModel::updateBaseLaser,
        onRppTextChange = viewModel::updateRppText,
        onRppToggleChange = viewModel::updateRppToggle,
        onApplyGeometry = { viewModel.applyGeometrySettings(false) },
        onSaveGeometry = { viewModel.applyGeometrySettings(true) },
        onApplyRpp = { viewModel.applyRppSettings(false) },
        onSaveRpp = { viewModel.applyRppSettings(true) },
        onPathPlanningTextChange = viewModel::updatePathPlanningText,
        onApplyPathPlanning = { viewModel.applyPathPlanningSettings(false) },
        onSavePathPlanning = { viewModel.applyPathPlanningSettings(true) },
        onRestoreSettings = viewModel::restoreSettingsFromBoard,
        modifier = modifier
    )
}

@Composable
private fun RobotSettingsContent(
    uiState: RobotSettingsUiState,
    onDecreaseWidth: () -> Unit,
    onIncreaseWidth: () -> Unit,
    onDecreaseLength: () -> Unit,
    onIncreaseLength: () -> Unit,
    onStartDownload: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onDismissUpdateFailedDialog: () -> Unit,
    onClearCache: () -> Unit,
    onConfirmClearCache: (Set<ClearCacheCategory>) -> Unit,
    onDismissClearCacheDialog: () -> Unit,
    onFootprintPointChange: (Int, String?, String?) -> Unit,
    onBaseLaserChange: (BaseLaserField, String) -> Unit,
    onRppTextChange: (RppTextField, String) -> Unit,
    onRppToggleChange: (RppToggleField, Boolean) -> Unit,
    onApplyGeometry: () -> Unit,
    onSaveGeometry: () -> Unit,
    onApplyRpp: () -> Unit,
    onSaveRpp: () -> Unit,
    onPathPlanningTextChange: (PathPlanningTextField, String) -> Unit,
    onApplyPathPlanning: () -> Unit,
    onSavePathPlanning: () -> Unit,
    onRestoreSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMenuItem by remember { mutableStateOf(SettingsMenuItem.RobotConfig) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧菜单面板
        SettingsMenuPanel(
            selectedItem = selectedMenuItem,
            onItemSelected = { selectedMenuItem = it },
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
        )

        // 右侧内容面板
        when (selectedMenuItem) {
            SettingsMenuItem.RobotConfig -> {
                RobotConfigContent(
                    uiState = uiState,
                    onFootprintPointChange = onFootprintPointChange,
                    onBaseLaserChange = onBaseLaserChange,
                    onApply = onApplyGeometry,
                    onSave = onSaveGeometry,
                    onRestore = onRestoreSettings,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            SettingsMenuItem.NavigationConfig -> {
                RppConfigContent(
                    rpp = uiState.rpp,
                    onTextChange = onRppTextChange,
                    onToggleChange = onRppToggleChange,
                    onApply = onApplyRpp,
                    onSave = onSaveRpp,
                    onRestore = onRestoreSettings,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            SettingsMenuItem.PathPlanning -> {
                PathPlanningConfigContent(
                    settings = uiState.pathPlanning,
                    onTextChange = onPathPlanningTextChange,
                    onApply = onApplyPathPlanning,
                    onSave = onSavePathPlanning,
                    onRestore = onRestoreSettings,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            SettingsMenuItem.SystemUpgrade -> {
                SystemUpgradeContent(
                    uiState = uiState,
                    onStartDownload = onStartDownload,
                    onCheckUpdate = onCheckUpdate,
                    onDismissUpdateDialog = onDismissUpdateDialog,
                    onDismissUpdateFailedDialog = onDismissUpdateFailedDialog,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            SettingsMenuItem.ClearCache -> {
                ClearCacheContent(
                    isClearingCache = uiState.isClearingCache,
                    onClearCache = onClearCache,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }

    if (uiState.showClearCacheConfirmDialog) {
        ClearCacheSelectionDialog(
            onConfirm = onConfirmClearCache,
            onCancel = onDismissClearCacheDialog
        )
    }
}

/**
 * 左侧设置菜单面板
 */
@Composable
private fun SettingsMenuPanel(
    selectedItem: SettingsMenuItem,
    onItemSelected: (SettingsMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.76f))
            .border(2.dp, Color.White.copy(alpha = 0.94f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 26.dp)
    ) {
        // 软件设置分组
        Text(
            text = "软件设置",
            fontSize = 14.sp,
            color = Color(0xFF7885AE),
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(12.dp))

        SettingsMenuButton(
            text = SettingsMenuItem.SystemUpgrade.label,
            selected = selectedItem == SettingsMenuItem.SystemUpgrade,
            onClick = { onItemSelected(SettingsMenuItem.SystemUpgrade) }
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsMenuButton(
            text = SettingsMenuItem.ClearCache.label,
            selected = selectedItem == SettingsMenuItem.ClearCache,
            onClick = { onItemSelected(SettingsMenuItem.ClearCache) }
        )
        Spacer(modifier = Modifier.height(18.dp))

        // 虚线分隔线
        DashedDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 硬件设置分组
        Text(
            text = "硬件设置",
            fontSize = 14.sp,
            color = Color(0xFF7885AE),
            fontWeight = FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(12.dp))

        SettingsMenuButton(
            text = SettingsMenuItem.RobotConfig.label,
            selected = selectedItem == SettingsMenuItem.RobotConfig,
            onClick = { onItemSelected(SettingsMenuItem.RobotConfig) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsMenuButton(
            text = SettingsMenuItem.NavigationConfig.label,
            selected = selectedItem == SettingsMenuItem.NavigationConfig,
            onClick = { onItemSelected(SettingsMenuItem.NavigationConfig) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsMenuButton(
            text = SettingsMenuItem.PathPlanning.label,
            selected = selectedItem == SettingsMenuItem.PathPlanning,
            onClick = { onItemSelected(SettingsMenuItem.PathPlanning) }
        )
    }
}


/**
 * 蓝色按钮菜单项（用于硬件设置分组的「机器设置」）
 */
@Composable
private fun SettingsMenuButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF00A0E9) else Color.Transparent)
            .clickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color(0xFF202937)
        )
    }
}

/**
 * 虚线分隔线
 */
@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = Color(0xFFCDD3DC),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            strokeWidth = 2f
        )
    }
}

/**
 * 系统升级内容面板 - Logo + 检查更新按钮
 */
@Composable
private fun SystemUpgradeContent(
    uiState: RobotSettingsUiState,
    onStartDownload: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onDismissUpdateFailedDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(107.dp))

            // Logo：与设计稿保持 107dp 顶部间距和 68dp 尺寸
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.sinelynx.grindingrobot.feature.common.R.drawable.ic_topabr_logo),
                    contentDescription = "应用 Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(68.dp)
                )
            }

            Spacer(modifier = Modifier.height(117.dp))

            // 检查更新按钮行：顶部距面板 292dp，高度 48dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(enabled = !uiState.isCheckingUpdate && !uiState.isDownloading && !uiState.isAwaitingInstall,
                        onClick = onCheckUpdate)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        uiState.isCheckingUpdate -> "检查中…"
                        uiState.isAwaitingInstall -> "等待系统安装结果…"
                        else -> "检查更新"
                    },
                    fontSize = 16.sp,
                    color = Color(0xFF202937),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    painter = painterResource(id = com.sinelynx.grindingrobot.core.designsystem.R.drawable.ic_arrow_right),
                    contentDescription = "检查更新",
                    tint = Color(0xFF9DA3AF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (uiState.showUpdateDialog && uiState.availableRelease != null) {
        UpdateAvailableDialog(
            versionName = uiState.availableRelease.versionName,
            releaseNotes = uiState.availableRelease.releaseNotes,
            updateRequired = uiState.updateRequired,
            onUpdate = onStartDownload,
            onDismiss = onDismissUpdateDialog
        )
    }

    if (uiState.isDownloading) {
        DownloadProgressDialog()
    }

    if (uiState.showUpdateFailedDialog) {
        ConfirmOverlayDialog(
            title = "更新失败",
            message = uiState.updateError ?: "更新失败，请重试",
            confirmText = if (uiState.availableRelease == null) "重新检查" else "重试",
            cancelText = "取消",
            onConfirm = {
                onDismissUpdateFailedDialog()
                if (uiState.availableRelease == null) onCheckUpdate() else onStartDownload()
            },
            onCancel = onDismissUpdateFailedDialog
        )
    }
}

/**
 * 清除缓存内容面板 - 箱子插图 + 一键清除按钮
 */
@Composable
private fun ClearCacheContent(
    isClearingCache: Boolean,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(78.dp))

            // 纸箱插图：与设计稿保持 78dp 顶部间距和 182dp 尺寸
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(182.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_clear_cache_box),
                    contentDescription = "清除缓存",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(182.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 一键清除按钮行：顶部距面板 292dp，高度 48dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(enabled = !isClearingCache, onClick = onClearCache)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isClearingCache) "清理中…" else "一键清除",
                    fontSize = 16.sp,
                    color = Color(0xFF202937),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    painter = painterResource(id = com.sinelynx.grindingrobot.core.designsystem.R.drawable.ic_arrow_right),
                    contentDescription = if (isClearingCache) "缓存清理中" else "一键清除",
                    tint = Color(0xFF9DA3AF).copy(alpha = if (isClearingCache) 0.5f else 1f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 机器人外形、base_link 到激光坐标系参数。布局按参考图保留“示意图 + 参数卡”层级。 */
@Composable
private fun RobotConfigContent(
    uiState: RobotSettingsUiState,
    onFootprintPointChange: (Int, String?, String?) -> Unit,
    onBaseLaserChange: (BaseLaserField, String) -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .border(2.dp, Color.White.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
            .padding(horizontal = 34.dp, vertical = 26.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("机器人外形与坐标", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                RobotFootprintDiagram(
                    points = uiState.footprint,
                    modifier = Modifier.weight(0.94f).heightIn(min = 430.dp, max = 560.dp)
                )
                Column(
                    modifier = Modifier.weight(1.18f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DesignSectionHeader("Footprint 多边形（相对 base_link）")
                    uiState.footprint.forEachIndexed { index, point ->
                        FootprintPointRow(
                            index = index,
                            point = point,
                            onXChange = { onFootprintPointChange(index, it, null) },
                            onYChange = { onFootprintPointChange(index, null, it) }
                        )
                    }
                    DimensionSummary(
                        width = footprintWidth(uiState.footprint),
                        length = footprintLength(uiState.footprint)
                    )
                    Text(
                        "ⓘ 以上为相对 base_link 的多边形顶点，base_link 是坐标原点，不是机器人尺寸。",
                        fontSize = 12.sp,
                        color = DesignMutedBlue
                    )
                    DesignSectionHeader("base_link → base_laser_link")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GeometryField("x (m)", uiState.baseLaserX, { onBaseLaserChange(BaseLaserField.X, it) }, Modifier.weight(1f))
                        GeometryField("y (m)", uiState.baseLaserY, { onBaseLaserChange(BaseLaserField.Y, it) }, Modifier.weight(1f))
                        GeometryField("z (m)", uiState.baseLaserZ, { onBaseLaserChange(BaseLaserField.Z, it) }, Modifier.weight(1f))
                        GeometryField("yaw (°)", uiState.baseLaserYaw, { onBaseLaserChange(BaseLaserField.YAW, it) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GeometryField("roll (°)", uiState.baseLaserRoll, { onBaseLaserChange(BaseLaserField.ROLL, it) }, Modifier.weight(1f))
                        GeometryField("pitch (°)", uiState.baseLaserPitch, { onBaseLaserChange(BaseLaserField.PITCH, it) }, Modifier.weight(1f))
                        Spacer(Modifier.weight(2f))
                    }
                    SettingsActionBar(onApply, onSave, onRestore)
                    Text(
                        if (uiState.geometryRequiresRestart) {
                            "ⓘ 板端已接受设置；激光外参将在 Super-LIO 重载或重启后生效"
                        } else {
                            "ⓘ 临时应用：立即生效；保存为默认：写入配置文件。"
                        },
                        fontSize = 12.sp,
                        color = DesignMutedBlue
                    )
                }
            }
        }
    }
}

private val DesignNavy = Color(0xFF0B1645)
private val DesignBlue = Color(0xFF109FE8)
private val DesignMutedBlue = Color(0xFF7482A9)
private val DesignLine = Color(0xFFE6ECF4)

@Composable
private fun RobotFootprintDiagram(points: List<FootprintPointUi>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFFDFEFF))) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val coordinates = points.mapNotNull { p ->
                val x = p.x.toFloatOrNull()
                val y = p.y.toFloatOrNull()
                if (x == null || y == null) null else x to y
            }
            if (coordinates.size < 3) return@Canvas
            val extentX = coordinates.maxOf { it.first } - coordinates.minOf { it.first }
            val extentY = coordinates.maxOf { it.second } - coordinates.minOf { it.second }
            val scale = minOf(size.width * 0.62f / extentX.coerceAtLeast(0.5f), size.height * 0.58f / extentY.coerceAtLeast(0.5f))
            val center = Offset(size.width * 0.52f, size.height * 0.56f)
            fun screen(p: Pair<Float, Float>) = Offset(center.x + p.first * scale, center.y - p.second * scale)
            val top = screen(coordinates[0])
            val right = screen(coordinates[1])
            val bottom = screen(coordinates[2])
            val left = screen(coordinates[3])
            drawLine(Color(0xFF1466E8), top, right, strokeWidth = 3f)
            drawLine(Color(0xFF1466E8), right, bottom, strokeWidth = 3f)
            drawLine(Color(0xFF1466E8), bottom, left, strokeWidth = 3f)
            drawLine(Color(0xFF1466E8), left, top, strokeWidth = 3f)
            listOf(top, right, bottom, left).forEach { drawCircle(Color(0xFF1466E8), 8f, it) }

            drawCircle(Color(0xFF18191D), size.minDimension * 0.16f, center + Offset(0f, 22f))
            drawRoundRect(Color(0xFFE83438), center + Offset(-size.minDimension * 0.075f, -size.minDimension * 0.06f),
                size = Size(size.minDimension * 0.15f, size.minDimension * 0.30f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
            drawRoundRect(Color(0xFF111216), center + Offset(-size.minDimension * 0.058f, 0f),
                size = Size(size.minDimension * 0.116f, size.minDimension * 0.17f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            drawCircle(Color(0xFFF2F4F6), 15f, center + Offset(0f, -size.minDimension * 0.11f))
            drawCircle(Color(0xFF111827), 9f, center + Offset(0f, -size.minDimension * 0.11f))
            drawLine(Color(0xFFEF2D2D), center, center + Offset(0f, -size.height * 0.30f), strokeWidth = 4f)
            drawLine(Color(0xFFEF2D2D), center + Offset(0f, -size.height * 0.30f), center + Offset(-10f, -size.height * 0.24f), strokeWidth = 4f)
            drawLine(Color(0xFFEF2D2D), center + Offset(0f, -size.height * 0.30f), center + Offset(10f, -size.height * 0.24f), strokeWidth = 4f)
            drawLine(Color(0xFF2CCB27), center + Offset(0f, 22f), center + Offset(-size.width * 0.19f, 22f), strokeWidth = 4f)
            drawLine(Color(0xFF2CCB27), center + Offset(-size.width * 0.19f, 22f), center + Offset(-size.width * 0.15f, 12f), strokeWidth = 4f)
            drawLine(Color(0xFF2CCB27), center + Offset(-size.width * 0.19f, 22f), center + Offset(-size.width * 0.15f, 32f), strokeWidth = 4f)
        }
        Text("X（前）", Modifier.align(Alignment.TopCenter).padding(top = 22.dp), color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Y（左）", Modifier.align(Alignment.CenterStart).padding(start = 20.dp), color = Color(0xFF25C923), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("base_link\n当前位置", Modifier.align(Alignment.Center).offset(y = 98.dp), color = DesignBlue, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

@Composable
private fun DesignSectionHeader(text: String, accent: Color = DesignBlue, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(36.dp).clip(RoundedCornerShape(6.dp)).background(accent))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
    }
}

@Composable
private fun FootprintPointRow(
    index: Int,
    point: FootprintPointUi,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("顶点 ${index + 1}", Modifier.width(78.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
        Text("x (m)", fontSize = 13.sp, color = DesignNavy)
        CompactValueField(point.x, onXChange, Modifier.weight(1f))
        Text("y (m)", fontSize = 13.sp, color = DesignNavy)
        CompactValueField(point.y, onYChange, Modifier.weight(1f))
    }
}

@Composable
private fun DimensionSummary(width: String, length: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0F8FF)).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("宽度  $width m", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp, color = DesignNavy, fontWeight = FontWeight.Bold)
        Box(Modifier.width(1.dp).height(22.dp).background(Color(0xFFD5E7F8)))
        Text("长度  $length m", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp, color = DesignNavy, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GeometryField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, color = DesignNavy)
        CompactValueField(value, onChange, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CompactValueField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Color(0xFF18213A), textAlign = TextAlign.Center),
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, DesignLine, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingsActionBar(
    onApply: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    saveOutlined: Boolean = false
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        DesignActionButton("临时应用", onApply, Modifier.weight(1f))
        if (saveOutlined) {
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.dp, DesignLine, RoundedCornerShape(10.dp)).clickable(onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                Text("保存为默认", color = DesignNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            DesignActionButton("保存为默认", onSave, Modifier.weight(1f))
        }
        Box(Modifier.width(84.dp).clickable(onClick = onRestore).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
            Text("恢复默认", fontSize = 15.sp, color = DesignBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesignActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(52.dp).clip(RoundedCornerShape(10.dp)).background(DesignBlue).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun footprintWidth(points: List<FootprintPointUi>): String {
    val values = points.mapNotNull { it.y.toFloatOrNull() }
    return values.takeIf { it.size >= 2 }?.let { formatValue((it.max() - it.min()).toDouble()) } ?: "-"
}

private fun footprintLength(points: List<FootprintPointUi>): String {
    val values = points.mapNotNull { it.x.toFloatOrNull() }
    return values.takeIf { it.size >= 2 }?.let { formatValue((it.max() - it.min()).toDouble()) } ?: "-"
}

/** RPP 参数卡片化布局，字段仍然直接写入板端 dynamic_reconfigure 对应协议。 */
@Composable
private fun RppConfigContent(
    rpp: RppSettingsUiState,
    onTextChange: (RppTextField, String) -> Unit,
    onToggleChange: (RppToggleField, Boolean) -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .border(2.dp, Color.White.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RPP导航参数", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
            Spacer(Modifier.width(24.dp))
            Text("仅保留影响导航的核心参数", Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFDCEEFF)).padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 13.sp, color = DesignBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            SettingsActionBar(onApply, onSave, onRestore, Modifier.width(450.dp), saveOutlined = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("控制器：Regulated Pure Pursuit", fontSize = 14.sp, color = DesignMutedBlue)
            StatusPill("●  运行中", Color(0xFF12BF40), Color(0xFFE8FFF0))
            StatusPill("临时调参模式", DesignBlue, Color(0xFFE5F4FF))
            StatusPill("●  有未保存修改", Color(0xFFFFB800), Color.Transparent)
            Spacer(Modifier.weight(1f))
            Text("临时应用立即生效，重启后恢复；保存为默认写入配置文件", fontSize = 12.sp, color = DesignMutedBlue)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignLine))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RppCard("速度", "控制机器人整体运动速度和加速度", Color(0xFF149FE8)) {
                    RppTextRow("目标跟踪速度", "m/s", RppTextField.DESIRED_LINEAR_VEL, rpp.desiredLinearVel, onTextChange)
                    RppTextRow("最大线速度", "m/s", RppTextField.MAX_LINEAR_VEL, rpp.maxLinearVel, onTextChange)
                    RppTextRow("最大角速度", "rad/s", RppTextField.MAX_ANGULAR_VEL, rpp.maxAngularVel, onTextChange)
                    RppTextRow("最大线加速度", "m/s²", RppTextField.MAX_LINEAR_ACCEL, rpp.maxLinearAccel, onTextChange)
                    RppTextRow("最大角加速度", "rad/s²", RppTextField.MAX_ANGULAR_ACCEL, rpp.maxAngularAccel, onTextChange)
                }
                RppCard("避障安全", "保障行驶安全，避免碰撞", Color(0xFFFFA31A), warning = true) {
                    RppTextRow("前方避障距离", "m", RppTextField.FRONT_CLEARANCE, rpp.collisionFrontClearanceM, onTextChange)
                    RppTextRow("侧向避障距离", "m", RppTextField.SIDE_CLEARANCE, rpp.collisionSideClearanceM, onTextChange)
                    RppToggleRow("footprint 膨胀避障", rpp.useFootprintExpansionCollisionDetection, RppToggleField.FOOTPRINT_COLLISION, onToggleChange)
                    RppToggleRow("碰撞检测", rpp.useCollisionDetection, RppToggleField.COLLISION_DETECTION, onToggleChange)
                    RppTextRow("预测碰撞时间", "s", RppTextField.MAX_TIME_TO_COLLISION, rpp.maxAllowedTimeToCollision, onTextChange)
                    RppTextRow("碰撞确认扫描数", "", RppTextField.COLLISION_CONFIRM_SCANS, rpp.collisionConfirmScans, onTextChange)
                    RppTextRow("清除确认扫描数", "", RppTextField.CLEAR_CONFIRM_SCANS, rpp.collisionClearConfirmScans, onTextChange)
                    RppTextRow("激光数据超时", "s", RppTextField.COLLISION_SCAN_TIMEOUT, rpp.collisionScanTimeoutS, onTextChange)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RppCard("直线 / 曲率跟踪", "影响直线跟踪稳定性和转弯平滑性", Color(0xFF149FE8)) {
                    RppTextRow("前视距离", "m", RppTextField.LOOKAHEAD_DIST, rpp.lookaheadDist, onTextChange)
                    RppTextRow("最小前视", "m", RppTextField.MIN_LOOKAHEAD_DIST, rpp.minLookaheadDist, onTextChange)
                    RppTextRow("最大前视", "m", RppTextField.MAX_LOOKAHEAD_DIST, rpp.maxLookaheadDist, onTextChange)
                    RppTextRow("曲率前视距离", "m", RppTextField.CURVATURE_LOOKAHEAD_DIST, rpp.curvatureLookaheadDist, onTextChange)
                    RppTextRow("曲率减速最小半径", "m", RppTextField.SCALING_MIN_RADIUS, rpp.regulatedLinearScalingMinRadius, onTextChange)
                    RppTextRow("曲率最低速度", "m/s", RppTextField.SCALING_MIN_SPEED, rpp.regulatedLinearScalingMinSpeed, onTextChange)
                    RppToggleRow("固定曲率前视", rpp.useFixedCurvatureLookahead, RppToggleField.FIXED_CURVATURE_LOOKAHEAD, onToggleChange)
                    RppToggleRow("曲率限速", rpp.useRegulatedLinearVelocityScaling, RppToggleField.REGULATED_SCALING, onToggleChange)
                }
                RppCard("对正与到达", "控制目标点对正和到达判定", Color(0xFF149FE8)) {
                    RppToggleRow("原地对正", rpp.useRotateToHeading, RppToggleField.ROTATE_TO_HEADING, onToggleChange)
                    RppTextRow("对正角速度", "rad/s", RppTextField.ROTATE_ANGULAR_VEL, rpp.rotateToHeadingAngularVel, onTextChange)
                    RppTextRow("进入对正角度", "rad", RppTextField.ROTATE_MIN_ANGLE, rpp.rotateToHeadingMinAngle, onTextChange)
                    RppTextRow("目标距离容差", "m", RppTextField.GOAL_DIST_TOL, rpp.goalDistTol, onTextChange)
                    RppTextRow("目标角度容差", "rad", RppTextField.ANGLE_TOL, rpp.angleTol, onTextChange)
                    RppToggleRow("允许倒车", rpp.allowReversing, RppToggleField.ALLOW_REVERSING, onToggleChange)
                }
            }
        }
    }
}

@Composable
private fun PathPlanningConfigContent(
    settings: PathPlanningSettingsUiState,
    onTextChange: (PathPlanningTextField, String) -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .border(2.dp, Color.White.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("路径规划参数", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
            Spacer(Modifier.width(24.dp))
            Text(
                "影响扫描路径、安全距离和绕柱策略",
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFDCEEFF))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                fontSize = 13.sp,
                color = DesignBlue,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            SettingsActionBar(onApply, onSave, onRestore, Modifier.width(450.dp), saveOutlined = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("仅保留影响路径规划结果的核心参数", fontSize = 14.sp, color = DesignMutedBlue)
            Spacer(Modifier.weight(1f))
            Text("临时应用立即生效，保存为默认写入板端配置", fontSize = 12.sp, color = DesignMutedBlue)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignLine))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RppCard("路径安全", "控制障碍物边界和作业区域留边", Color(0xFFFFA31A), warning = true) {
                    PathPlanningTextRow(
                        "障碍物膨胀距离", "m", "planner.inflation_radius",
                        PathPlanningTextField.INFLATION_RADIUS, settings.inflationRadius, onTextChange
                    )
                    PathPlanningTextRow(
                        "扫掠线起止点留边", "m", "planner.endpoint_margin",
                        PathPlanningTextField.ENDPOINT_MARGIN, settings.endpointMargin, onTextChange
                    )
                    PathPlanningTextRow(
                        "额外避让距离", "m", "planner.obstacle_avoidance_distance",
                        PathPlanningTextField.OBSTACLE_AVOIDANCE_DISTANCE,
                        settings.obstacleAvoidanceDistance,
                        onTextChange
                    )
                }
                RppCard("输出路径", "控制最终路径的点密度", Color(0xFF149FE8)) {
                    PathPlanningTextRow(
                        "路径补点间距", "m", "planner.output_point_spacing",
                        PathPlanningTextField.OUTPUT_POINT_SPACING, settings.outputPointSpacing, onTextChange
                    )
                    Text(
                        "填 0 可关闭补点，保留规划器原始路径点",
                        Modifier.padding(start = 2.dp, top = 4.dp, bottom = 6.dp),
                        fontSize = 12.sp,
                        color = DesignMutedBlue
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RppCard("柱体绕行", "控制角度对齐后的柱体识别和绕行", Color(0xFF149FE8)) {
                    PathPlanningTextRow(
                        "柱体矩形膨胀距离", "m", "planner.aligned_obstacle_inflation",
                        PathPlanningTextField.ALIGNED_OBSTACLE_INFLATION,
                        settings.alignedObstacleInflation,
                        onTextChange
                    )
                    PathPlanningTextRow(
                        "柱体识别最大边长", "m", "planner.aligned_obstacle_max_extent",
                        PathPlanningTextField.ALIGNED_OBSTACLE_MAX_EXTENT,
                        settings.alignedObstacleMaxExtent,
                        onTextChange
                    )
                    PathPlanningTextRow(
                        "绕柱角度", "deg", "planner.obstacle_corner_angle_deg",
                        PathPlanningTextField.OBSTACLE_CORNER_ANGLE_DEG,
                        settings.obstacleCornerAngleDeg,
                        onTextChange
                    )
                }
                Text(
                    "超过最大边长的障碍物按墙体处理；角度范围为 0～90 度。",
                    Modifier.padding(horizontal = 4.dp),
                    fontSize = 12.sp,
                    color = DesignMutedBlue
                )
            }
        }
    }
}

@Composable
private fun PathPlanningTextRow(
    label: String,
    unit: String,
    protocol: String,
    field: PathPlanningTextField,
    value: String,
    onChange: (PathPlanningTextField, String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 14.sp, color = DesignNavy, fontWeight = FontWeight.Bold)
            Text(protocol, fontSize = 10.sp, color = DesignMutedBlue)
        }
        CompactValueField(value, { onChange(field, it) }, Modifier.width(138.dp))
        Text(unit, Modifier.width(46.dp), fontSize = 13.sp, color = DesignMutedBlue)
    }
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
}

@Composable
private fun RppCard(
    title: String,
    subtitle: String,
    accent: Color,
    warning: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (warning) Color(0xFFFFFCF5) else Color(0xFFF8FBFF)).border(1.dp, if (warning) Color(0xFFFFD48A) else Color(0xFFE1ECF7), RoundedCornerShape(14.dp))
    ) {
        Row(Modifier.fillMaxWidth().background(if (warning) Color(0xFFFFF9ED) else Color(0xFFF1F8FF)).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DesignNavy)
            Spacer(Modifier.width(20.dp))
            Text(subtitle, fontSize = 12.sp, color = DesignMutedBlue)
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp), content = content)
    }
}

@Composable
private fun RppTextRow(
    label: String,
    unit: String,
    field: RppTextField,
    value: String,
    onChange: (RppTextField, String) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 14.sp, color = DesignNavy, fontWeight = FontWeight.Bold)
            Text(protocolName(field), fontSize = 10.sp, color = DesignMutedBlue)
        }
        CompactValueField(value, { onChange(field, it) }, Modifier.width(138.dp))
        Text(unit, Modifier.width(46.dp), fontSize = 13.sp, color = DesignMutedBlue)
    }
}

@Composable
private fun RppToggleRow(
    label: String,
    checked: Boolean,
    field: RppToggleField,
    onChange: (RppToggleField, Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 14.sp, color = DesignNavy, fontWeight = FontWeight.Bold)
            Text(toggleProtocolName(field), fontSize = 10.sp, color = DesignMutedBlue)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSwitch(checked, { onChange(field, it) })
            Text(if (checked) "开启" else "关闭", fontSize = 13.sp, color = DesignMutedBlue)
        }
    }
}

@Composable
private fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.width(48.dp).height(26.dp).clip(RoundedCornerShape(20.dp)).background(if (checked) DesignBlue else Color(0xFFD8DEE8)).clickable { onCheckedChange(!checked) }, contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.padding(3.dp).size(20.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
    }
}

private fun protocolName(field: RppTextField): String = when (field) {
    RppTextField.DESIRED_LINEAR_VEL -> "rpp.desired_linear_vel"
    RppTextField.MAX_LINEAR_VEL -> "rpp.max_linear_vel"
    RppTextField.MAX_ANGULAR_VEL -> "rpp.max_angular_vel"
    RppTextField.MAX_LINEAR_ACCEL -> "rpp.max_linear_acc"
    RppTextField.MAX_ANGULAR_ACCEL -> "rpp.max_angular_acc"
    RppTextField.LOOKAHEAD_DIST -> "rpp.lookahead_dist"
    RppTextField.MIN_LOOKAHEAD_DIST -> "rpp.min_lookahead_dist"
    RppTextField.MAX_LOOKAHEAD_DIST -> "rpp.max_lookahead_dist"
    RppTextField.CURVATURE_LOOKAHEAD_DIST -> "rpp.curvature_lookahead_dist"
    RppTextField.SCALING_MIN_RADIUS -> "rpp.curvature_slowdown_radius"
    RppTextField.SCALING_MIN_SPEED -> "rpp.curvature_min_speed"
    RppTextField.FRONT_CLEARANCE -> "rpp.obstacle_dist_front"
    RppTextField.SIDE_CLEARANCE -> "rpp.obstacle_dist_side"
    RppTextField.MAX_TIME_TO_COLLISION -> "rpp.prediction_time"
    RppTextField.COLLISION_CONFIRM_SCANS -> "rpp.collision_confirm_scans"
    RppTextField.CLEAR_CONFIRM_SCANS -> "rpp.clear_confirm_scans"
    RppTextField.ROTATE_ANGULAR_VEL -> "rpp.in_place_angular_vel"
    RppTextField.ROTATE_MIN_ANGLE -> "rpp.angle_enter"
    RppTextField.GOAL_DIST_TOL -> "rpp.goal_distance_tolerance"
    RppTextField.ANGLE_TOL -> "rpp.goal_angle_tolerance"
    RppTextField.COLLISION_STOP_DISTANCE -> "rpp.collision_stop_distance"
    RppTextField.COLLISION_SCAN_TIMEOUT -> "rpp.scan_timeout"
    RppTextField.COLLISION_MIN_POINTS -> "rpp.min_valid_points"
    RppTextField.ROTATE_EXIT_ANGLE -> "rpp.angle_exit"
    else -> "rpp.core_parameter"
}

private fun toggleProtocolName(field: RppToggleField): String = when (field) {
    RppToggleField.FOOTPRINT_COLLISION -> "rpp.use_footprint_inflation"
    RppToggleField.COLLISION_DETECTION -> "rpp.use_collision_check"
    RppToggleField.ROTATE_TO_HEADING -> "rpp.enable_in_place_rotation"
    RppToggleField.ALLOW_REVERSING -> "rpp.allow_reverse"
    RppToggleField.FIXED_CURVATURE_LOOKAHEAD -> "rpp.use_fixed_curvature_lookahead"
    RppToggleField.REGULATED_SCALING -> "rpp.limit_speed_by_curvature"
    else -> "rpp.runtime_option"
}

private fun formatValue(value: Double): String {
    val text = String.format("%.3f", value)
    return text.trimEnd('0').trimEnd('.')
}
