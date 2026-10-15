package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceStatusUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskRuntimeField

@Composable
internal fun TaskRuntimeSettingsDialog(
    state: DeviceStatusUiState,
    field: TaskRuntimeField,
    onInputChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val ready = state.runtimeSettings != null && !state.runtimeLoading && !state.runtimeApplying
    val blue = Color(0xFF00A0E9)
    val navy = Color(0xFF202937)
    val muted = Color(0xFF7585A5)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = blue,
            onPrimary = Color.White,
            surface = Color.White,
            onSurface = navy,
            onSurfaceVariant = muted,
            surfaceVariant = Color(0xFFF1F8FF),
            outline = Color(0xFFDCE8F5),
            error = Color(0xFFD82B2A)
        )
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color.White,
            titleContentColor = navy,
            textContentColor = navy,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = { Text("调节${field.label}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("仅本次任务生效，任务结束后恢复原设置。", fontSize = 13.sp, color = muted)
                    Text("实际：${if (field == TaskRuntimeField.RUN_SPEED) state.straightSpeedText else state.grindRpmText}")
                    when {
                        state.runtimeLoading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp))
                            Text("读取设备设定值…")
                        }
                        state.runtimeSettings != null -> {
                            val limit = if (field == TaskRuntimeField.RUN_SPEED) "大于 0～${state.runtimeSettings.maxRunSpeed} m/s" else "0～${state.runtimeSettings.maxDiscSpeedRpm} rpm"
                            Text("允许范围：$limit", fontSize = 12.sp, color = muted)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onStep(-1) }, enabled = ready) { Text("－") }
                                OutlinedTextField(
                                    value = state.runtimeInput, onValueChange = onInputChange,
                                    modifier = Modifier.weight(1f), enabled = ready, singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text("设定值 (${field.unit})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (field == TaskRuntimeField.RUN_SPEED) KeyboardType.Decimal else KeyboardType.Number)
                                )
                                TextButton(onClick = { onStep(1) }, enabled = ready) { Text("＋") }
                            }
                            if (state.runtimeApplying) Text("等待设备确认…", color = Color(0xFF0077BB))
                        }
                    }
                    state.runtimeError?.let { Text(it, color = Color(0xFFD82B2A), fontSize = 12.sp) }
                    if (!state.runtimeLoading && state.runtimeSettings == null) TextButton(onClick = onRetry) { Text("重新读取") }
                }
            },
            confirmButton = {
                Button(onClick = onApply, enabled = ready, shape = RoundedCornerShape(10.dp)) {
                    Text("应用", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFF1F8FF))
                ) { Text("关闭") }
            }
        )
    }
}
