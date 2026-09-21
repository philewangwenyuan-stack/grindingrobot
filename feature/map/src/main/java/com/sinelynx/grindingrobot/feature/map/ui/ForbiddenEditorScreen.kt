package com.sinelynx.grindingrobot.feature.map.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.designsystem.theme.ArrowLeftIcon
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import com.sinelynx.grindingrobot.feature.map.viewmodel.ForbiddenDrawMode

@Composable
fun ForbiddenEditorScreen(
    selectedMode: ForbiddenDrawMode,
    onBack: () -> Unit,
    onSelectMode: (ForbiddenDrawMode) -> Unit,
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
                text = "禁区",
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
            ForbiddenOptionCard(
                title = "矩形禁区",
                selected = selectedMode == ForbiddenDrawMode.RECTANGLE,
                onClick = { onSelectMode(ForbiddenDrawMode.RECTANGLE) },
                icon = { selected ->
                    RectangleForbiddenIcon(selected = selected)
                },
                modifier = Modifier.weight(1f)
            )
            ForbiddenOptionCard(
                title = "圆形禁区",
                selected = selectedMode == ForbiddenDrawMode.CIRCLE,
                onClick = { onSelectMode(ForbiddenDrawMode.CIRCLE) },
                icon = { selected ->
                    CircleForbiddenIcon(selected = selected)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ForbiddenOptionCard(
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
private fun RectangleForbiddenIcon(selected: Boolean) {
    val color = if (selected) Color.White else Color(0xFF6D737D)
    Image(
        painter = painterResource(id = CommonR.drawable.ic_forbidden_rectangle),
        contentDescription = "矩形禁区",
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(40.dp)
    )
}

@Composable
private fun CircleForbiddenIcon(selected: Boolean) {
    val color = if (selected) Color.White else Color(0xFF6D737D)
    Image(
        painter = painterResource(id = CommonR.drawable.ic_forbidden_circle),
        contentDescription = "圆形禁区",
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(40.dp)
    )
}

