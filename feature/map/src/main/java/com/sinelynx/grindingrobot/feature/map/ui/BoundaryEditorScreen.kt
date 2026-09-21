package com.sinelynx.grindingrobot.feature.map.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.designsystem.theme.ArrowLeftIcon
import com.sinelynx.grindingrobot.feature.map.viewmodel.BoundaryDrawMode

/**
 * 绘制边界点区域
 */
@Composable
fun BoundaryEditorScreen(
    selectedMode: BoundaryDrawMode,
    onBack: () -> Unit,
    onSelectMode: (BoundaryDrawMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
                text = "区域边界点",
                fontSize = 18.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PanelDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "绘制方式",
            fontSize = 12.sp,
            color = Color(0xFF9DA3AF),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BoundaryOptionCard(
                title = "矩形",
                selected = selectedMode == BoundaryDrawMode.RECTANGLE,
                onClick = { onSelectMode(BoundaryDrawMode.RECTANGLE) },
                icon = { selected ->
                    RectangleBoundaryIcon(selected = selected)
                },
                modifier = Modifier.weight(1f)
            )
            BoundaryOptionCard(
                title = "多边形",
                selected = selectedMode == BoundaryDrawMode.POLYGON,
                onClick = { onSelectMode(BoundaryDrawMode.POLYGON) },
                icon = { selected ->
                    PolygonBoundaryIcon(selected = selected)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BoundaryOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(136.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF00A0E9) else Color.White)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else Color(0xFFE6E6E6),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        icon(selected)
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color(0xFFFEFEFF) else Color(0xFF9DA3AF)
        )
    }
}

@Composable
private fun RectangleBoundaryIcon(selected: Boolean) {
    val color = if (selected) Color.White else Color(0xFF6D737D)
    Box(
        modifier = Modifier
            .size(40.dp)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val squareSize = 28.dp.toPx()
                val left = (size.width - squareSize) / 2f
                val top = (size.height - squareSize) / 2f
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
                    style = Stroke(width = strokeWidth)
                )
                val handleSize = 6.dp.toPx()
                val handles = listOf(
                    Offset(left - handleSize / 2f, top - handleSize / 2f),
                    Offset(left + squareSize - handleSize / 2f, top - handleSize / 2f),
                    Offset(left - handleSize / 2f, top + squareSize - handleSize / 2f),
                    Offset(left + squareSize - handleSize / 2f, top + squareSize - handleSize / 2f)
                )
                handles.forEach { offset ->
                    drawRect(
                        color = color,
                        topLeft = offset,
                        size = androidx.compose.ui.geometry.Size(handleSize, handleSize)
                    )
                }
            }
    )
}

@Composable
private fun PolygonBoundaryIcon(selected: Boolean) {
    val color = if (selected) Color.White else Color(0xFF6D737D)
    Box(
        modifier = Modifier
            .size(40.dp)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val handleRadius = 3.dp.toPx()
                val points = listOf(
                    Offset(size.width * 0.20f, size.height * 0.30f),
                    Offset(size.width * 0.48f, size.height * 0.18f),
                    Offset(size.width * 0.78f, size.height * 0.34f),
                    Offset(size.width * 0.66f, size.height * 0.72f),
                    Offset(size.width * 0.32f, size.height * 0.72f)
                )
                points.zip(points.drop(1) + points.first()).forEach { (start, end) ->
                    drawLine(
                        color = color,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth
                    )
                }
                points.forEach { point ->
                    drawCircle(
                        color = color,
                        radius = handleRadius,
                        center = point
                    )
                }
            }
    )
}

