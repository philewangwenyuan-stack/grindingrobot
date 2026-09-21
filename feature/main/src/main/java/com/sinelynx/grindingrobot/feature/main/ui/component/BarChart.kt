package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.main.viewmodel.ChartDataPoint
import kotlin.math.ceil

/**
 * 通用蓝色柱状图组件，使用 Compose Canvas 自绘。
 *
 * @param title 图表标题，如 "作业效率趋势图"
 * @param unit  单位文本，如 "m²"、"小时"
 * @param data  数据点列表
 * @param barColor 柱状颜色
 */
@Composable
fun BarChart(
    title: String,
    unit: String,
    data: List<ChartDataPoint>,
    barColor: Color = Color(0xFF00A0E9),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 标题
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
        )
        // 单位
        Text(
            text = "单位：$unit",
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (data.isEmpty() || data.all { it.value == 0f }) {
            // 空态
            Text(
                text = "暂无数据",
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp)
            )
        } else {
            val maxValue = remember(data) {
                val rawMax = data.maxOf { it.value }
                if (rawMax <= 0f) 1f else rawMax
            }
            // 向上取整到合适的刻度
            val niceMax = remember(maxValue) { niceMaxValue(maxValue) }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(start = 4.dp)
            ) {
                drawBarChart(data, niceMax, barColor)
            }
        }
    }
}

/**
 * Canvas 绘制逻辑
 */
private fun DrawScope.drawBarChart(
    data: List<ChartDataPoint>,
    maxValue: Float,
    barColor: Color
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // 布局参数
    val yAxisLabelWidth = 50f     // Y 轴标签保留宽度
    val xAxisLabelHeight = 40f    // X 轴标签保留高度
    val chartLeft = yAxisLabelWidth
    val chartTop = 8f
    val chartRight = canvasWidth - 16f
    val chartBottom = canvasHeight - xAxisLabelHeight
    val chartWidth = chartRight - chartLeft
    val chartHeight = chartBottom - chartTop

    // 刻度数量
    val tickCount = 5
    val tickStep = maxValue / tickCount

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#9CA3AF")
        textSize = 28f
        isAntiAlias = true
    }

    val valuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#1F2937")
        textSize = 24f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // 绘制 Y 轴刻度和横向网格线
    for (i in 0..tickCount) {
        val value = tickStep * i
        val y = chartBottom - (value / maxValue) * chartHeight

        // 刻度标签
        val label = if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
        drawContext.canvas.nativeCanvas.drawText(
            label,
            yAxisLabelWidth - 10f,
            y + 8f,
            textPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
        )

        // 横向网格线（灰色虚线效果用浅色实线代替）
        drawLine(
            color = Color(0xFFE5E7EB),
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 1f
        )
    }

    // 绘制柱子和 X 轴标签
    if (data.isNotEmpty()) {
        val barCount = data.size
        val totalBarSpace = chartWidth
        val barGroupWidth = totalBarSpace / barCount
        val barWidth = (barGroupWidth * 0.5f).coerceAtMost(60f)

        data.forEachIndexed { index, point ->
            val barCenterX = chartLeft + barGroupWidth * index + barGroupWidth / 2
            val barHeight = (point.value / maxValue) * chartHeight
            val barTop = chartBottom - barHeight
            val barLeft = barCenterX - barWidth / 2

            // 柱子
            if (point.value > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )

                // 柱顶数值
                val valueLabel = if (point.value == point.value.toLong().toFloat()) {
                    point.value.toLong().toString()
                } else {
                    String.format("%.1f", point.value)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    valueLabel,
                    barCenterX,
                    barTop - 8f,
                    valuePaint
                )
            }

            // X 轴标签
            drawContext.canvas.nativeCanvas.drawText(
                point.label,
                barCenterX,
                chartBottom + 30f,
                textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER }
            )
        }
    }
}

/**
 * 计算合适的 Y 轴最大值（向上取整到 "好看" 的刻度）
 */
private fun niceMaxValue(rawMax: Float): Float {
    if (rawMax <= 0f) return 1f
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawMax.toDouble()))).toFloat()
    val normalized = rawMax / magnitude
    val niceNormalized = when {
        normalized <= 1.0f -> 1.0f
        normalized <= 2.0f -> 2.0f
        normalized <= 5.0f -> 5.0f
        else -> 10.0f
    }
    val result = ceil((rawMax / (niceNormalized * magnitude)).toDouble()).toFloat() * niceNormalized * magnitude
    return if (result <= rawMax) rawMax * 1.1f else result
}
