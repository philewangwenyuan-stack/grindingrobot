package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sinelynx.grindingrobot.feature.main.viewmodel.SystemStatusViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val StatusInk = Color(0xFF202937)
private val StatusMuted = Color(0xFF7885AE)
private val StatusDanger = Color(0xFFD82B2A)
private val StatusGood = Color(0xFF29984B)
private val LocalStatusCompact = compositionLocalOf { false }

@Composable
internal fun SystemStatusScreen(modifier: Modifier = Modifier, viewModel: SystemStatusViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        while (isActive) { viewModel.refresh(); delay(2000) }
    }
    val s = ui.status
    val memoryAvailable = s?.memoryAvailableBytes
    val memoryTotal = s?.memoryTotalBytes
    BoxWithConstraints(modifier.background(Color(0xFFF6F8FB), RoundedCornerShape(24.dp))
        .border(2.dp, Color.White, RoundedCornerShape(24.dp))
        .padding(16.dp)) {
    CompositionLocalProvider(LocalStatusCompact provides (maxHeight < 540.dp || maxWidth < 800.dp)) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("系统状态", color = StatusInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(when {
            !ui.connected -> "设备未连接"
            !ui.fresh -> "状态未收到或已过期"
            s?.hasResourceTelemetry != true -> "已连接 · 资源数据暂未提供"
            else -> "板端实时状态 · 每 2 秒刷新"
        }, color = StatusMuted, fontSize = 14.sp)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusSection("伺服电机与底盘", Modifier.weight(1f)) {
            MotorStatusRow("左伺服电机", s?.leftMotorFault.takeIf { s?.chassisConnected == true })
            MotorStatusRow("右伺服电机", s?.rightMotorFault.takeIf { s?.chassisConnected == true })
            StatusValue("底盘连接", booleanStatus(s?.chassisConnected, "已连接", "未连接"))
            StatusValue("底盘使能", booleanStatus(s?.chassisEnabled.takeIf { s?.chassisConnected == true }, "已开启", "已关闭"))
            StatusValue("磨盘使能", booleanStatus(s?.discEnabled.takeIf { s?.chassisConnected == true }, "已开启", "已关闭"))
            StatusValue("急停状态", booleanStatus(s?.emergencyStop, "已急停", "未触发"), if (s?.emergencyStop == true) StatusDanger else StatusInk)
        }
        StatusSection("计算资源（机器人板端）", Modifier.weight(1f)) {
            StatusValue("CPU 温度", s?.cpuTemperatureC?.let { String.format(Locale.US, "%.1f ℃", it) } ?: "—")
            StatusValue("CPU 占用率", percent(s?.cpuUsagePercent))
            StatusValue("内存占用率", percent(s?.memoryUsagePercent))
            StatusValue("可用内存 / 总内存", if (memoryTotal != null && memoryAvailable != null) "${gib(memoryAvailable)} / ${gib(memoryTotal)}" else "—")
            StatusValue("存储占用率", percent(s?.diskUsagePercent))
            StatusValue("存储剩余空间", s?.diskFreeBytes?.let(::gib) ?: "—")
            StatusValue("本次开机时长", s?.uptimeSeconds?.let { "${it / 86400}天 ${it / 3600 % 24}小时 ${it / 60 % 60}分" } ?: "—")
        }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusSection("通信与网络", Modifier.weight(1f)) {
            StatusValue("平板 Wi-Fi", ui.wifiName)
            StatusValue("Wi-Fi 信号", ui.wifiRssi?.let { "$it dBm" } ?: "未提供")
            StatusValue("板端数据 RTT", ui.dataRttMs?.let { "$it ms" } ?: "未测得")
            StatusValue("板端控制 RTT", ui.controlRttMs?.let { "$it ms" } ?: "未测得")
            StatusValue("板端 4G 信号", s?.cellularSignalPercent?.let { "$it%" } ?: "未提供")
            StatusValue("蜂窝连接 / 制式", cellularState(s?.cellularState) + " / " + s?.cellularAccessTechnology.orEmpty().ifBlank { "未知" })
        }
        StatusSection("雷达与定位", Modifier.weight(1f)) {
            StatusValue("导航雷达最近上报", s?.radarStatus ?: "未提供")
            StatusValue("定位就绪", booleanStatus(s?.localizationReady, "已就绪", "未就绪"))
            if (!s?.localizationReason.isNullOrBlank()) StatusValue("定位状态说明", s?.localizationReason.orEmpty())
            StatusValue("碰撞保护", s?.let { if (it.collisionImminent) "已触发" else "未触发" } ?: "未知",
                if (s?.collisionImminent == true) StatusDanger else StatusInk)
            StatusValue("避障雷达健康", "暂未提供")
        }
        }
        Text("— / 未知表示未提供或已过期；RTT 为通信往返耗时。", color = StatusMuted, fontSize = 11.sp)
    }
    }
    }
}

@Composable
private fun StatusSection(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    val compact = LocalStatusCompact.current
    Column(modifier.fillMaxSize().background(Color.White, RoundedCornerShape(18.dp)).padding(if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp)) {
        Text(title, color = StatusInk, fontSize = if (compact) 15.sp else 18.sp,
            lineHeight = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun StatusValue(label: String, value: String, color: Color = StatusInk) {
    val compact = LocalStatusCompact.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), color = StatusMuted, fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 16.sp else 20.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, Modifier.weight(1.3f), color = color, fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 16.sp else 20.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MotorStatusRow(label: String, code: Int?) {
    StatusValue(label, when (code) {
        null -> "未知"
        0 -> "正常 · 0"
        else -> "故障 · $code (0x${String.format(Locale.US, "%04X", code and 0xFFFF)})"
    }, when (code) { null -> StatusMuted; 0 -> StatusGood; else -> StatusDanger })
}

private fun booleanStatus(value: Boolean?, yes: String, no: String) = when (value) { null -> "未知"; true -> yes; false -> no }
private fun percent(value: Float?) = value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
private fun gib(value: Long) = String.format(Locale.US, "%.2f GiB", value / 1073741824.0)
private fun cellularState(state: String?) = when (state) {
    "connected" -> "已连接"; "registered" -> "已注册"; "connecting" -> "连接中"
    "searching" -> "搜索中"; "disabled" -> "未启用"; "failed" -> "异常"
    null, "" -> "未提供"; else -> state
}
