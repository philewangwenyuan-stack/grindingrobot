package com.sinelynx.grindingrobot.feature.main.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinelynx.grindingrobot.feature.common.component.DeviceStatusBar
import com.sinelynx.grindingrobot.feature.main.R
import com.sinelynx.grindingrobot.feature.main.ui.component.ConfirmOverlayDialog
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskRecordItem
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskRecordUiState
import com.sinelynx.grindingrobot.feature.main.viewmodel.TaskRecordViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sinelynx.grindingrobot.feature.map.R as MapR

@Composable
fun TaskRecordRoute(
    viewModel: TaskRecordViewModel, modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.loadRecords()
    }
    TaskRecordScreen(
        uiState = uiState,
        onDeleteRecord = viewModel::deleteRecord,
        onReplayClick = viewModel::openReplay,
        modifier = modifier
    )
}

@Composable
fun TaskRecordScreen(
    uiState: TaskRecordUiState,
    onDeleteRecord: (TaskRecordItem) -> Unit,
    onReplayClick: (TaskRecordItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteRecord by remember { mutableStateOf<TaskRecordItem?>(null) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏标题
            Text(
                text = "任务记录",
                fontSize = 20.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            if (uiState.isLoading && uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00A0E9))
                }
            } else if (uiState.records.isEmpty()) {
                // 空数据展示状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = MapR.drawable.ic_workspace_empty),
                            contentDescription = "暂无数据",
                            modifier = Modifier.size(90.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无数据", color = Color(0xFF9DA3AF), fontSize = 16.sp
                        )
                    }
                }
            } else {
                // 有数据展示列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        uiState.records,
                        key = { "${it.executionId}:${it.taskId}:${it.startTime}" }
                    ) { record ->
                        TaskRecordCard(
                            record = record,
                            deleteEnabled = uiState.deletingExecutionId == null &&
                                record.executionId.isNotBlank(),
                            onDeleteClick = { pendingDeleteRecord = record },
                            onReplayClick = onReplayClick
                        )
                    }
                }
            }
        }
    }

    pendingDeleteRecord?.let { record ->
        ConfirmOverlayDialog(
            title = "删除任务记录？",
            message = "该次任务记录、轨迹及地图快照将被删除，且无法恢复",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                onDeleteRecord(record)
                pendingDeleteRecord = null
            },
            onCancel = {
                pendingDeleteRecord = null
            })
    }
}

@Composable
private fun TaskRecordCard(
    record: TaskRecordItem,
    deleteEnabled: Boolean,
    onDeleteClick: () -> Unit,
    onReplayClick: (TaskRecordItem) -> Unit
) {
    val thumbnailBitmap = remember(record.base64Image) {
        decodeBase64ThumbnailToImageBitmap(record.base64Image)
    }

    val formattedDate = remember(record.startTime) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.startTime))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：地图缩略图圆角方块
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEDEDF0)), contentAlignment = Alignment.Center
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = "地图缩略图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = MapR.drawable.ic_workspace_empty),
                    contentDescription = "地图缩略图",
                    colorFilter = ColorFilter.tint(Color(0xFFE2E7EE)),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 中间：任务名称与属性值
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = record.taskName,
                fontSize = 18.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 地图名称属性
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFD9D9D9))
                    )
                    Text(
                        text = record.mapName, fontSize = 14.sp, color = Color(0xFF9DA3AF)
                    )
                }

                // 时间属性
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = MapR.drawable.ic_home_map_time),
                        contentDescription = "时间",
                        tint = Color(0xFF9DA3AF),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formattedDate, fontSize = 14.sp, color = Color(0xFF9DA3AF)
                    )
                }

                // 研磨面积属性
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = MapR.drawable.ic_home_map_area),
                        contentDescription = "面积",
                        tint = Color(0xFF9DA3AF),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${record.area.toInt()}m²",
                        fontSize = 14.sp,
                        color = Color(0xFF9DA3AF)
                    )
                }
            }
        }

        // 右侧：操作按钮 Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 删除按钮（灰色垃圾箱）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .alpha(if (deleteEnabled) 1f else 0.4f)
                    .clickable(enabled = deleteEnabled, onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = MapR.drawable.ic_workspace_del),
                    contentDescription = "删除任务记录",
                    tint = Color(0xFF9DA3AF),
                    modifier = Modifier.size(36.dp)
                )
            }

            // 任务回放按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00A0E9))
                    .clickable { onReplayClick(record) }, contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_task_record),
                    contentDescription = "任务回放",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun decodeBase64ThumbnailToImageBitmap(base64: String): ImageBitmap? {
    if (base64.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
