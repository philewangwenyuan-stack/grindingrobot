package com.sinelynx.grindingrobot.feature.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared global positions: title left, emergency in the center, device status at the right. */
@Composable
fun GlobalTopBar(
    onEmergencyClick: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    emergencyLabel: String = "紧急制动",
    emergencyEnabled: Boolean = true,
    emergencyAccent: Color = Color(0xFFD82B2A),
    emergencyFeedback: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            title()
        }
        GlobalEmergencyPill(onEmergencyClick, emergencyLabel, emergencyEnabled, emergencyAccent,
            emergencyFeedback, Modifier.align(Alignment.TopCenter))
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DeviceStatusBar()
            // Reserve the global close-button slot even on pages that exit through their footer.
            if (trailingContent != null) trailingContent() else Spacer(Modifier.size(36.dp))
        }
    }
}

@Composable
private fun GlobalEmergencyPill(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean,
    accent: Color,
    feedback: String?,
    modifier: Modifier,
) {
    Row(modifier.clip(RoundedCornerShape(1000.dp)).background(accent.copy(alpha = 0.1f))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Text("!", color = Color.White, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold,
                lineHeight = 10.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)))
        }
        Column {
            Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (feedback != null) Text(feedback, color = accent, fontSize = 11.sp)
        }
    }
}
