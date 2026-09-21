package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.designsystem.theme.ArrowLeftIcon
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspaceDraft

@Composable
fun AddWorkspaceScreen(
    draft: WorkspaceDraft,
    isEditing: Boolean,
    isSaving: Boolean,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenBoundaryEditor: () -> Unit,
    onOpenForbiddenEditor: () -> Unit,
    onAreaCodeChange: (String) -> Unit,
    onPlaceStartPoint: () -> Unit,
    onPlaceEndPoint: () -> Unit,
    onDecreasePathSpacing: () -> Unit,
    onIncreasePathSpacing: () -> Unit,
    onPreviousScanDirection: () -> Unit,
    onNextScanDirection: () -> Unit,
    onOpenRobotSettings: () -> Unit,
    onOpenProcessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEnter()
    }
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArrowLeftIcon(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(onClick = onBack)
                )
                Text(
                    text = if (isEditing) "编辑工作区" else "新增工作区",
                    fontSize = 18.sp,
                    color = Color(0xFF202937),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            PanelDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "路径规划参数",
                fontSize = 12.sp,
                color = Color(0xFF9DA3AF),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))

            ParameterInputRow(
                label = "区域编号",
                content = {
                    AreaCodeField(
                        value = draft.areaCode,
                        onValueChange = onAreaCodeChange,
                        modifier = Modifier.width(180.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            StatusActionRow(
                label = "区域边界点",
                configured = draft.boundaryConfigured,
                actionIconRes = R.drawable.ic_region_next,
                onActionClick = onOpenBoundaryEditor
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusActionRow(
                label = "禁区",
                configured = draft.forbiddenConfigured,
                actionIconRes = R.drawable.ic_region_next,
                onActionClick = onOpenForbiddenEditor
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusActionRow(
                label = "起点",
                configured = draft.startConfigured,
                actionIconRes = R.drawable.ic_start_point,
                onActionClick = onPlaceStartPoint
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusActionRow(
                label = "终点",
                configured = draft.endConfigured,
                actionIconRes = R.drawable.ic_start_point,
                onActionClick = onPlaceEndPoint
            )
            Spacer(modifier = Modifier.height(8.dp))

            ParameterInputRow(
                label = "扫描方向",
                content = {
                    ValueAdjuster(
                        value = draft.scanDirection,
                        onDecrease = onPreviousScanDirection,
                        onIncrease = onNextScanDirection,
                        decreaseIconRes = R.drawable.ic_change_left,
                        increaseIconRes = R.drawable.ic_change_right
                    )
                }
            )

            /*Spacer(modifier = Modifier.height(8.dp))
            ParameterInputRow(
                label = "路径间距",
                content = {
                    ValueAdjuster(
                        value = draft.pathSpacing.toString(),
                        onDecrease = onDecreasePathSpacing,
                        onIncrease = onIncreasePathSpacing
                    )
                }
            )*/

            Spacer(modifier = Modifier.height(12.dp))
            PanelDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "其他设置",
                fontSize = 12.sp,
                color = Color(0xFF9DA3AF),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))

            SettingsEntryRow(
                text = "工艺设置",
                trailingIconRes = R.drawable.ic_right,
                onClick = onOpenProcessSettings
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsEntryRow(
                text = "机器人设置",
                trailingIconRes = R.drawable.ic_right,
                onClick = onOpenRobotSettings
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(1000.dp))
                    .background(Color(0xFF00A0E9))
                    .clickable(enabled = !isSaving, onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSaving) "保存中..." else "保 存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ParameterInputRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6D737D),
            fontWeight = FontWeight.Medium
        )
        content()
    }
}

@Composable
private fun StatusActionRow(
    label: String,
    configured: Boolean,
    actionIconRes: Int,
    onActionClick: () -> Unit
) {
    ParameterInputRow(
        label = label,
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (configured) "已设置" else "未设置",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (configured) Color(0xFF00A0E9) else Color(0xFF9DA3AF)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00A0E9))
                        .clickable(onClick = onActionClick),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = actionIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun ValueAdjuster(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String = "−",
    increaseLabel: String = "+",
    decreaseIconRes: Int? = null,
    increaseIconRes: Int? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AdjusterButton(text = decreaseLabel, iconRes = decreaseIconRes, onClick = onDecrease)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        AdjusterButton(text = increaseLabel, iconRes = increaseIconRes, onClick = onIncrease)
    }
}

@Composable
private fun AdjusterButton(
    text: String,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = text,
                fontSize = 20.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AreaCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = TextFieldValue(text = value, selection = TextRange(value.length)),
        onValueChange = { onValueChange(it.text) },
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF202937)
        ),
        cursorBrush = SolidColor(Color(0xFF00A0E9)),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
private fun SettingsEntryRow(
    text: String,
    trailingIconRes: Int,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF202937),
            fontWeight = FontWeight.Medium
        )
        Icon(
            painter = painterResource(id = trailingIconRes),
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(16.dp)
        )
    }
}

