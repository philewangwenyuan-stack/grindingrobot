package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.feature.main.viewmodel.ClearCacheCategory

private data class ClearCacheOption(
    val category: ClearCacheCategory,
    val label: String,
    val description: String
)

private val clearCacheOptions = listOf(
    ClearCacheOption(
        ClearCacheCategory.MemoryCache,
        "内存缓存",
        "清除内存中的预览和规划结果缓存"
    ),
    ClearCacheOption(
        ClearCacheCategory.TemporaryFiles,
        "临时文件",
        "清除预览图、路径/SL-Link 调试文件及超过 60 秒的残留临时文件"
    ),
    ClearCacheOption(
        ClearCacheCategory.Logs,
        "运行日志",
        "清理 ROS 与 catkin 工作空间日志；当前日志文件仅清空内容"
    )
)

@Composable
internal fun ClearCacheSelectionDialog(
    onConfirm: (Set<ClearCacheCategory>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedCategories by remember { mutableStateOf(emptySet<ClearCacheCategory>()) }
    val canConfirm = selectedCategories.isNotEmpty()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(width = 480.dp, height = 570.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(Color.White)
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "清除缓存？",
                    color = Color(0xFF202937),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                clearCacheOptions.forEachIndexed { index, option ->
                    ClearCacheOptionRow(
                        label = option.label,
                        description = option.description,
                        checked = option.category in selectedCategories,
                        onCheckedChange = { checked ->
                            selectedCategories = if (checked) {
                                selectedCategories + option.category
                            } else {
                                selectedCategories - option.category
                            }
                        }
                    )
                    if (index < clearCacheOptions.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "不会清除 LIVE_MAP、当前路径、地图、任务记录、轨迹、配置及设备控制状态。",
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.width(380.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ClearCacheDialogActionButton(
                        text = "取消",
                        background = Color(0xFFE5E7EB),
                        textColor = Color(0x80202937),
                        enabled = true,
                        onClick = onCancel
                    )
                    ClearCacheDialogActionButton(
                        text = "确定",
                        background = Color(0xFFD82B2A),
                        textColor = Color.White,
                        enabled = canConfirm,
                        onClick = { onConfirm(selectedCategories) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ClearCacheOptionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .size(width = 380.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF00A0E9),
                uncheckedColor = Color(0xFFE5E7EB),
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(0xFF202937),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ClearCacheDialogActionButton(
    text: String,
    background: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 64.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(if (enabled) background else background.copy(alpha = 0.5f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
