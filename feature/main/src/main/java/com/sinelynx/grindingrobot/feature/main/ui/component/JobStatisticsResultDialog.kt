package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.feature.main.viewmodel.JobStatisticsResult

/**
 * 作业统计查询结果全屏弹窗。
 *
 * 展示统计条件复现、汇总指标卡片、任务标签 Tab 和两个柱状图。
 */
@Composable
fun JobStatisticsResultDialog(
    result: JobStatisticsResult,
    onDismiss: () -> Unit,
    onTaskSelected: (Int) -> Unit,
    onExport: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    // 2. 标题行 + 导出按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "作业统计",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF00A0E9))
                                .clickable(onClick = onExport)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "导出",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 统计条件复现行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "统计时间：${result.dateRange}",
                            fontSize = 14.sp,
                            color = Color(0xFF4B5563)
                        )
                        Spacer(modifier = Modifier.width(32.dp))
                        Text(
                            text = "地图：${result.mapNames}",
                            fontSize = 14.sp,
                            color = Color(0xFF4B5563)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. 指标汇总卡片（3 行 × 2 列网格表格）
                    val selectedTask = result.tasks.getOrNull(result.selectedTaskIndex)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        // 第 1 行：任务次数 | 作业面积
                        MetricRow(
                            leftLabel = "任务次数",
                            leftValue = "${selectedTask?.taskCount ?: result.totalTaskCount}",
                            rightLabel = "作业面积",
                            rightValue = "${String.format("%.0f", selectedTask?.totalArea ?: result.totalArea)}m²"
                        )
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFE5E7EB))
                        )
                        // 第 2 行：作业时长 | 作业地图
                        MetricRow(
                            leftLabel = "作业时长",
                            leftValue = "${String.format("%.1f", selectedTask?.totalDurationHours ?: result.totalDurationHours)}",
                            rightLabel = "作业地图",
                            rightValue = selectedTask?.mapNames ?: result.totalMapNames
                        )
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFE5E7EB))
                        )
                        // 第 3 行：稼动率 | (空)
                        MetricRow(
                            leftLabel = "稼动率",
                            leftValue = "${String.format("%.0f", selectedTask?.utilizationRate ?: result.utilizationRate)}%",
                            rightLabel = "",
                            rightValue = ""
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    /*Text(
                        text = "时长为结束减开始，包含暂停；稼动率为累计时长÷查询区间时长。按开始月份统计整次执行。",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                    if ((selectedTask?.invalidDurationCount ?: 0) > 0) {
                        Text(
                            text = "时长不完整：有 ${selectedTask?.invalidDurationCount} 条记录时间异常，时长、稼动率及图表仅汇总有效时间。",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309)
                        )
                    }*/
                    if (result.totalTaskCount == 0) {
                        Text("所选条件下暂无已结束的执行记录", color = Color(0xFF6B7280), fontSize = 14.sp)
                    }

                    // 5. 任务标签 Tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        result.tasks.forEachIndexed { index, task ->
                            val isSelected = index == result.selectedTaskIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(Color(0xFF00A0E9))
                                        } else {
                                            Modifier
                                                .background(Color.White)
                                                .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(20.dp))
                                        }
                                    )
                                    .clickable { onTaskSelected(index) }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = task.taskName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF6B7280)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 6. 作业效率趋势图
                    BarChart(
                        title = "作业趋势图",
                        unit = "m²",
                        data = selectedTask?.areaChartData ?: emptyList()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 7. 作业时长分布图
                    BarChart(
                        title = "作业时长分布图",
                        unit = "小时",
                        data = selectedTask?.durationChartData ?: emptyList()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 指标卡片表格的一行（2 列）
 */
@Composable
private fun MetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        // 左侧标签
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = leftLabel,
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
        }
        // 竖线分隔
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize()
                .background(Color(0xFFE5E7EB))
        )
        // 左侧数值
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = leftValue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F2937)
            )
        }
        // 竖线分隔
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize()
                .background(Color(0xFFE5E7EB))
        )
        // 右侧标签
        if (rightLabel.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color(0xFFF9FAFB))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = rightLabel,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }
            // 竖线分隔
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(Color(0xFFE5E7EB))
            )
            // 右侧数值
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = rightValue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F2937)
                )
            }
        } else {
            // 空白占位（第三行右侧为空）
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxSize()
            )
        }
    }
}
