package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.DisposableEffect
import com.sinelynx.grindingrobot.feature.main.viewmodel.datePickerDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinelynx.grindingrobot.core.designsystem.component.AppLazyColumn
import com.sinelynx.grindingrobot.feature.main.R
import com.sinelynx.grindingrobot.feature.main.ui.component.JobStatisticsResultDialog
import com.sinelynx.grindingrobot.feature.main.viewmodel.JobStatisticsUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.JobStatisticsViewModel

@Composable
fun JobStatisticsRoute(
    viewModel: JobStatisticsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.requestMapCatalogOnEnter()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelHistoryQuery() }
    }
    JobStatisticsScreen(
        uiState = uiState,
        onMapToggle = viewModel::toggleMapSelection,
        onClearMaps = viewModel::clearMapSelection,
        onSelectAllMaps = viewModel::selectAllMaps,
        onStartDateSelected = viewModel::setStartDate,
        onEndDateSelected = viewModel::setEndDate,
        onStartDateClick = { viewModel.showDatePickerStart(true) },
        onEndDateClick = { viewModel.showDatePickerEnd(true) },
        onDismissDatePickerStart = { viewModel.showDatePickerStart(false) },
        onDismissDatePickerEnd = { viewModel.showDatePickerEnd(false) },
        onQuery = viewModel::onQuery,
        onDismissResultDialog = viewModel::dismissResultDialog,
        onSelectTask = viewModel::selectTask,
        onExport = { viewModel.exportData(context) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobStatisticsScreen(
    uiState: JobStatisticsUiState,
    onMapToggle: (String) -> Unit,
    onClearMaps: () -> Unit,
    onSelectAllMaps: () -> Unit,
    onStartDateSelected: (Long?) -> Unit,
    onEndDateSelected: (Long?) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onDismissDatePickerStart: () -> Unit,
    onDismissDatePickerEnd: () -> Unit,
    onQuery: () -> Unit,
    onDismissResultDialog: () -> Unit = {},
    onSelectTask: (Int) -> Unit = {},
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMapDropdownExpanded by remember { mutableStateOf(false) }

    val formattedStartDate = remember(uiState.startDate) {
        uiState.startDate?.let {
            datePickerDate(it).toString()
        } ?: "开始时间"
    }

    val formattedEndDate = remember(uiState.endDate) {
        uiState.endDate?.let {
            datePickerDate(it).toString()
        } ?: "结束时间"
    }

    val mapSelectionText = remember(uiState.selectedMapIds, uiState.maps) {
        if (uiState.selectedMapIds.isEmpty()) {
            "地图（支持复选）"
        } else {
            val selectedNames = uiState.maps
                .filter { it.isSelected }
                .map { it.mapName }
            if (selectedNames.size == uiState.maps.size && uiState.maps.isNotEmpty()) {
                "全部地图 (${selectedNames.size})"
            } else if (selectedNames.size <= 2) {
                selectedNames.joinToString(", ")
            } else {
                "已选 ${selectedNames.size} 张地图"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF6F8FB))
            .border(2.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // 大白色圆角区域
        Column(
            modifier = Modifier.width(514.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 顶部的立体 3D 插图 (放大镜 + 文档)
            Image(
                painter = painterResource(id = R.drawable.ic_job_statistics_empty),
                contentDescription = "查询插图",
                modifier = Modifier
                    .size(160.dp,116.dp)
                    .padding(bottom = 12.dp)
            )

            // 2. 地图复选选择框 (整行)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val popupWidth = maxWidth
                val popupGapPx = with(LocalDensity.current) { MapPopupGap.roundToPx() }
                val popupPositionProvider = remember(popupGapPx) {
                    AboveAnchorPopupPositionProvider(popupGapPx)
                }
                SelectorField(
                    text = mapSelectionText,
                    isHint = uiState.selectedMapIds.isEmpty(),
                    onClick = { isMapDropdownExpanded = !isMapDropdownExpanded }
                )

                if (isMapDropdownExpanded) {
                    Popup(
                        popupPositionProvider = popupPositionProvider,
                        onDismissRequest = { isMapDropdownExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(popupWidth)
                                .height(MapPopupHeight)
                                .clip(SelectorFieldShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E7EB), SelectorFieldShape)
                        ) {
                            // 全选和清空的快捷 Row 固定在顶部
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "全选",
                                    fontSize = 14.sp,
                                    color = Color(0xFF00A0E9),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onSelectAllMaps() }
                                )
                                Text(
                                    text = "清空",
                                    fontSize = 14.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onClearMaps() }
                                )
                            }

                            AppLazyColumn(
                                modifier = Modifier.weight(1f),
                                fillMaxSize = true
                            ) {
                                if (uiState.isMapCatalogLoading || uiState.maps.isEmpty()) {
                                    item(key = "map_status") {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (uiState.isMapCatalogLoading) {
                                                        "正在加载地图…"
                                                    } else {
                                                        "暂无可用地图，请先同步或建图"
                                                    },
                                                    color = Color(0xFF9CA3AF),
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            onClick = {}
                                        )
                                    }
                                } else {
                                    items(
                                        items = uiState.maps,
                                        key = { it.mapId }
                                    ) { item ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Checkbox(
                                                        checked = item.isSelected,
                                                        onCheckedChange = { onMapToggle(item.mapId) },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = Color(0xFF00A0E9)
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = item.mapName,
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF374151)
                                                    )
                                                }
                                            },
                                            onClick = { onMapToggle(item.mapId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. 开始时间/结束时间选择器 (并排)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SelectorField(
                    text = formattedStartDate,
                    isHint = uiState.startDate == null,
                    onClick = onStartDateClick,
                    modifier = Modifier.weight(1f)
                )

                SelectorField(
                    text = formattedEndDate,
                    isHint = uiState.endDate == null,
                    onClick = onEndDateClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. 查询主按钮 (居中，限宽)
            Button(
                onClick = onQuery,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .width(280.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A0E9),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (uiState.isLoading) "查询中…" else "查询",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 开始日期弹窗
        if (uiState.showDatePickerStart) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.startDate)
            DatePickerDialog(
                onDismissRequest = onDismissDatePickerStart,
                confirmButton = {
                    TextButton(onClick = {
                        onStartDateSelected(datePickerState.selectedDateMillis)
                    }) {
                        Text("确定", color = Color(0xFF00A0E9), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDatePickerStart) {
                        Text("取消", color = Color(0xFF4B5563))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // 结束日期弹窗
        if (uiState.showDatePickerEnd) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.endDate)
            DatePickerDialog(
                onDismissRequest = onDismissDatePickerEnd,
                confirmButton = {
                    TextButton(onClick = {
                        onEndDateSelected(datePickerState.selectedDateMillis)
                    }) {
                        Text("确定", color = Color(0xFF00A0E9), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDatePickerEnd) {
                        Text("取消", color = Color(0xFF4B5563))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // 查询结果弹窗
        if (uiState.showResultDialog && uiState.statisticsResult != null) {
            JobStatisticsResultDialog(
                result = uiState.statisticsResult,
                onDismiss = onDismissResultDialog,
                onTaskSelected = onSelectTask,
                onExport = onExport
            )
        }
    }
}

private val SelectorFieldShape = RoundedCornerShape(10.dp)
private val MapPopupHeight = 280.dp
private val MapPopupGap = 1.dp

private class AboveAnchorPopupPositionProvider(
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = anchorBounds.left.coerceIn(0, maxX)
        val y = anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(x, y)
    }
}

@Composable
private fun SelectorField(
    text: String,
    isHint: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(SelectorFieldShape)
            .background(Color(0xFFFFFFFF)) // 白色背景，还原设计稿中的输入框填充色
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (isHint) Color(0xFF9CA3AF) else Color(0xFF1F2937),
            fontWeight = FontWeight.Normal
        )
        Icon(
            painter = painterResource(id = com.sinelynx.grindingrobot.core.designsystem.R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
        )
    }
}
