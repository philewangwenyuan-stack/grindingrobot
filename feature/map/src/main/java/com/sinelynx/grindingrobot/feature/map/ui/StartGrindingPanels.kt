package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapWorkspaceMetricsItem
import com.sinelynx.grindingrobot.feature.map.viewmodel.StartGrindingPlanPreviewUiState

@Composable
private fun PanelShell(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFFFF))
            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF202937),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = 16.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE6E6E6))
        )
        content()
    }
}

@Composable
internal fun WorkspaceSelectionPanel(
    workspaces: List<MapWorkspaceMetricsItem>,
    selectedWorkspaces: SnapshotStateMap<String, Boolean>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelShell(title = "工作区", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            workspaces.forEach { workspace ->
                val id = workspace.regionId
                val selected = selectedWorkspaces[id] == true
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0x3300A0E9) else Color(0xFFF3F4F6))
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) Color(0xFF00A0E9) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggle(id) }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = workspace.regionName.ifBlank { "区域$id" },
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
internal fun ObstaclePlanningPanel(
    selectedMode: TaskObstacleMode,
    onSelectMode: (TaskObstacleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelShell(title = "规划避障区", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ObstacleModeCard(
                title = "矩形禁区",
                selected = selectedMode == TaskObstacleMode.Rectangle,
                isCircle = false,
                onClick = { onSelectMode(TaskObstacleMode.Rectangle) }
            )
            ObstacleModeCard(
                title = "圆形禁区",
                selected = selectedMode == TaskObstacleMode.Circle,
                isCircle = true,
                onClick = { onSelectMode(TaskObstacleMode.Circle) }
            )
        }
    }
}

@Composable
private fun ObstacleModeCard(
    title: String,
    selected: Boolean,
    isCircle: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(136.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF00A0E9) else Color.White)
            .border(1.dp, if (selected) Color(0xFF00A0E9) else Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(
                id = if (isCircle) {
                    CommonR.drawable.ic_forbidden_circle
                } else {
                    CommonR.drawable.ic_forbidden_rectangle
                }
            ),
            contentDescription = title,
            colorFilter = ColorFilter.tint(if (selected) Color.White else Color(0xFF6D737D)),
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFF9DA3AF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun TaskParamsPanel(
    workspaces: List<MapWorkspaceMetricsItem>,
    selectedWorkspaces: SnapshotStateMap<String, Boolean>,
    passCounts: SnapshotStateMap<String, Int>,
    onDecrease: (String) -> Unit,
    onIncrease: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PanelShell(title = "任务参数", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            workspaces
                .filter { selectedWorkspaces[it.regionId] == true }
                .forEach { workspace ->
                    val id = workspace.regionId
                    val regionName = workspace.regionName.ifBlank { "区域$id" }
                    PassCountRow(
                        label = "${regionName}遍数",
                        value = passCounts[id] ?: workspace.repeat.coerceAtLeast(1),
                        onDecrease = { onDecrease(id) },
                        onIncrease = { onIncrease(id) }
                    )
                }
        }
    }
}

@Composable
internal fun TaskPreviewPanel(
    workspaces: List<MapWorkspaceMetricsItem>,
    selectedWorkspaces: SnapshotStateMap<String, Boolean>,
    passCounts: SnapshotStateMap<String, Int>,
    planPreview: StartGrindingPlanPreviewUiState?,
    taskName: String,
    onTaskNameChange: (String) -> Unit,
    canPreviewLegacyPlan: Boolean,
    onPreviewLegacyPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    PanelShell(title = "预览任务", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "任务名称",
                    color = Color(0xFF6D737D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(width = 180.dp, height = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = taskName,
                        onValueChange = onTaskNameChange,
                        textStyle = TextStyle(
                            color = Color(0xFF202937),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = SolidColor(Color(0xFF202937))
                    )
                }
            }

            // 工作区较多时仍能滚动到统计下方的测试按钮。
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val selectedWorkspacesList = workspaces
                    .filter { selectedWorkspaces[it.regionId] == true }
                selectedWorkspacesList.forEach { workspace ->
                    val id = workspace.regionId
                    PreviewMetricRow(
                        label = "${workspace.regionName.ifBlank { "区域$id" }}遍数",
                        value = (passCounts[id] ?: workspace.repeat.coerceAtLeast(1)).toString()
                    )
                }
                PreviewMetricRow(
                    label = "工作区数量",
                    value = selectedWorkspacesList.size.toString()
                )
                // 规划统计缺失时留空，不能拿工作区统计重新估算本轮规划结果。
                PreviewMetricRow(
                    label = "预计面积(m²)",
                    value = formatStartGrindingMetric(planPreview?.estimatedAreaM2)
                )
                PreviewMetricRow(
                    label = "预计耗时(h)",
                    value = formatStartGrindingMetric(planPreview?.estimatedTimeH)
                )
                /*OutlinedButton(onClick = onPreviewLegacyPlan, enabled = canPreviewLegacyPlan,
                modifier = Modifier.fillMaxWidth()) {
                Text("测试：旧版规划图")
            }*/
            }
        }
    }
}

@Composable
private fun PassCountRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF6D737D),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallCounterButton("−", onDecrease)
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.toString(),
                    color = Color(0xFF202937),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            SmallCounterButton("+", onIncrease)
        }
    }
}

@Composable
private fun SmallCounterButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF202937),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PreviewMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF6D737D),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F6))
                .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value,
                color = Color(0xFF202937),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun TaskBottomActions(
    step: StartTaskStep,
    canProceed: Boolean,
    canStart: Boolean,
    onCancel: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskActionButton(
            text = "✕  取消",
            background = Color(0xFFD82B2A),
            textColor = Color.White,
            onClick = onCancel
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step != StartTaskStep.Workspace) {
                TaskActionButton(
                    text = "◀  上一步",
                    background = Color(0xFFF6F8FB),
                    textColor = Color(0xFF202937),
                    onClick = onPrevious
                )
            }
            TaskActionButton(
                text = if (step == StartTaskStep.Preview) "▶  开始研磨" else "▶  下一步",
                background = Color(0xFF00A0E9),
                textColor = Color.White,
                enabled = if (step == StartTaskStep.Preview) canStart else canProceed,
                onClick = if (step == StartTaskStep.Preview) onStart else onNext
            )
        }
    }
}

@Composable
private fun TaskActionButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 56.dp)
            .shadow(4.dp, RoundedCornerShape(1000.dp))
            .clip(RoundedCornerShape(1000.dp))
            .background(if (enabled) background else background.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
