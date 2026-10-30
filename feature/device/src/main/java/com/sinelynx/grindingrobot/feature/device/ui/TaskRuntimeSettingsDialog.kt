package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.device.viewmodel.DeviceStatusUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskRuntimeField
import com.sinelynx.grindingrobot.feature.common.component.CommonTipsDialog
import com.sinelynx.grindingrobot.feature.common.component.DriveParameterSlider
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import kotlin.math.roundToInt

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
    val muted = Color(0xFF9DA3AF)
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
        CommonTipsDialog(onDismissRequest = onDismiss, dialogHeight = 520.dp) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(80.dp).background(Color(0xFFFFE2E2), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(CommonR.drawable.ic_device_status), null,
                        tint = Color(0xFFD82B2A), modifier = Modifier.size(32.dp))
                }
                Text("调节${field.label}", color = navy, fontSize = 24.sp, lineHeight = 30.sp,
                    fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("仅本次任务生效，任务结束后恢复原设置。", fontSize = 20.sp,
                        lineHeight = 28.sp, color = muted, textAlign = TextAlign.Center)
                    Text("实际：${if (field == TaskRuntimeField.RUN_SPEED) state.straightSpeedText else state.grindRpmText}")
                    when {
                        state.runtimeLoading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(24.dp))
                            Text("读取设备设定值…")
                        }
                        state.runtimeSettings != null -> {
                            val limit = if (field == TaskRuntimeField.RUN_SPEED) "大于 0～${state.runtimeSettings.maxRunSpeed} m/s" else "0～${state.runtimeSettings.maxDiscSpeedRpm} rpm"
                            Text("允许范围：$limit", fontSize = 12.sp, color = muted)
                            if (field == TaskRuntimeField.DISC_RPM) {
                                val maximum = state.runtimeSettings.maxDiscSpeedRpm.coerceAtLeast(1)
                                val rpm = (state.runtimeInput.toIntOrNull() ?: state.runtimeSettings.discSpeedRpm ?: 0).coerceIn(0, maximum)
                                DriveParameterSlider(
                                    label = "研磨转速", value = rpm.toFloat(), valueText = "$rpm rpm",
                                    range = 0f..maximum.toFloat(), step = 10f,
                                    onValueChange = { onInputChange(((it / 10f).roundToInt() * 10).coerceIn(0, maximum).toString()) },
                                    endpointLabels = "0" to "$maximum rpm", enabled = ready
                                )
                            } else Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(1000.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB), contentColor = Color(0xFF6D737D))) {
                        Text("关闭", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onApply, enabled = ready, modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(1000.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD82B2A), contentColor = Color.White)) {
                        Text("应用", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
