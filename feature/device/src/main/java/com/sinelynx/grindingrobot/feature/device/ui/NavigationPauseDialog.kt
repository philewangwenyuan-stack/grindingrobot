package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.device.viewmodel.NavigationPauseNotice

@Composable
internal fun NavigationPauseDialog(notice: NavigationPauseNotice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text(notice.title, color = Color(0xFF202937), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(notice.message, color = Color(0xFF7585A5), fontSize = 14.sp, lineHeight = 22.sp) },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A0E9), contentColor = Color.White)) {
                Text("知道了", fontWeight = FontWeight.Medium)
            }
        }
    )
}
