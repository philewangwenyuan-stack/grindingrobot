package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.component.GlobalTopBar

internal fun workflowSideWidth(width: androidx.compose.ui.unit.Dp) = (width * 0.15f).coerceIn(132.dp, 172.dp)

@Composable
internal fun MapWorkflowHeader(step: Int, onEmergencyStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlobalTopBar(onEmergencyClick = onEmergencyStop, title = {
            Text("扫描建图", color = Color(0xFF202937), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("1  扫描地图", "2  选择工作区", "3  划分工作区", "4  保存地图").forEachIndexed { index, text ->
                Text(text, color = if (index + 1 == step) Color(0xFF00A0E9) else Color(0xFF9DA3AF),
                    fontSize = 13.sp, fontWeight = if (index + 1 == step) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun MapWorkflowAction(text: String, primary: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled,
        color = if (primary) Color(0xFF00A0E9) else Color(0xFFE0E6ED),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(160.dp).height(44.dp).testTag("workflow_$text")) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (primary) Color.White else Color(0xFF606C7C),
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun MapWorkflowActions(onCancel: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, save: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        MapWorkflowAction("取消建图", false, onCancel)
        Spacer(Modifier.weight(1f))
        MapWorkflowAction("上一步", false, onPrevious)
        MapWorkflowAction(if (save) "保存地图" else "下一步", true, onNext, enabled)
    }
}

@Composable
internal fun WorkflowGuidePanel(title: String, hint: String, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF6F8FB), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color(0xFF202937), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = Color(0xFF6D737D), fontSize = 13.sp)
    }
}
