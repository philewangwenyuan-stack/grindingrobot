package com.sinelynx.grindingrobot.feature.common.component

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.sinelynx.grindingrobot.feature.common.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 通用设备状态栏
 */

@Composable
fun DeviceStatusBar(
    modifier: Modifier = Modifier,
    wifiName: String? = null,
    batteryPercent: Int? = null,
    currentTime: String? = null
) {
    val systemWifiName by rememberConnectedWifiName()
    val systemBatteryPercent by rememberBatteryPercent()
    val timeText = rememberCurrentTimeText(currentTime)
    val wifiText = wifiName ?: systemWifiName
    val resolvedBatteryPercent = batteryPercent ?: systemBatteryPercent
    val batteryValue = resolvedBatteryPercent.coerceIn(0, 100)
    val messages = buildDeviceMessages(
        batteryPercent = resolvedBatteryPercent,
        time = timeText
    )
    var showMessageDialog by remember { mutableStateOf(false) }
    var messageButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BellDotIcon(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    messageButtonBounds = coordinates.boundsInWindow()
                }
                .clickable { showMessageDialog = true }
        )

        Row(
            modifier = Modifier
                .width(180.dp)
                .height(32.dp)
                .background(Color(0x0D3968EB), RoundedCornerShape(1000.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WifiSignalIcon(modifier = Modifier.size(20.dp))
            Text(
                text = wifiText.uppercase(Locale.getDefault()),
                fontSize = 14.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VerticalBatteryView(modifier = Modifier.size(12.dp, height = 24.dp), level = batteryValue)
            Text(
                text = "$batteryValue%",
                fontSize = 14.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = timeText,
            fontSize = 14.sp,
            color = Color(0xFF202937),
            fontWeight = FontWeight.Bold
        )
    }

    if (showMessageDialog) {
        MessageCenterDialog(
            anchorBounds = messageButtonBounds,
            messages = messages,
            onDismissRequest = { showMessageDialog = false }
        )
    }
}

private enum class DeviceMessageType(val dotColor: Color) {
    NORMAL(Color(0xFF00A0E9)),
    WARNING(Color(0xFFFFA000)),
    ERROR(Color(0xFFD82B2A))
}

private data class DeviceMessage(
    val type: DeviceMessageType,
    val title: String,
    val time: String
)

private fun buildDeviceMessages(
    embeddedMessages: List<DeviceMessage> = emptyList(),
    batteryPercent: Int? = null,
    time: String
): List<DeviceMessage> {
    val batteryValue = batteryPercent?.coerceIn(0, 100)
    val batteryMessage = when {
        batteryValue == null -> null
        batteryValue < 10 -> DeviceMessage(
            type = DeviceMessageType.ERROR,
            title = "设备电量严重不足($batteryValue%)",
            time = time
        )
        batteryValue < 20 -> DeviceMessage(
            type = DeviceMessageType.WARNING,
            title = "设备电量过低($batteryValue%)",
            time = time
        )
        else -> null
    }

    return embeddedMessages + listOfNotNull(batteryMessage)
}

@Composable
private fun MessageCenterDialog(
    anchorBounds: androidx.compose.ui.geometry.Rect?,
    messages: List<DeviceMessage>,
    onDismissRequest: () -> Unit
) {
    val density = LocalDensity.current
    val horizontalOffsetPx = with(density) { 170.dp.toPx() }
    val verticalOffsetPx = with(density) { 12.dp.toPx() }
    val fallbackTopPx = with(density) { 81.dp.toPx() }
    val fallbackStartPx = with(density) { 490.dp.toPx() }
    val popupOffset = anchorBounds?.let { bounds ->
        IntOffset(
            x = (bounds.center.x - horizontalOffsetPx).roundToInt(),
            y = (bounds.bottom + verticalOffsetPx).roundToInt()
        )
    } ?: IntOffset(
        x = fallbackStartPx.roundToInt(),
        y = fallbackTopPx.roundToInt()
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { popupOffset },
            contentAlignment = Alignment.TopStart
        ) {
            Surface(
                modifier = Modifier.size(width = 360.dp, height = 254.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF6F8FB),
                border = BorderStroke(2.dp, Color.White),
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 28.dp, end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "消息中心",
                            color = Color(0xFF777C89),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "×",
                            color = Color(0xFF9DA3AF),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = onDismissRequest)
                        )
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无消息",
                                color = Color(0xFF777C89),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            messages.forEach { message ->
                                MessageCenterItem(
                                    dotColor = message.type.dotColor,
                                    title = message.title,
                                    time = message.time
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCenterItem(
    dotColor: Color,
    title: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = title,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            color = Color(0xFF202937),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = time,
            color = Color(0xFF777C89),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun rememberCurrentTimeText(externalTime: String? = null): String {
    var nowText by remember { mutableStateOf("") }
    LaunchedEffect(externalTime) {
        if (externalTime != null) return@LaunchedEffect
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            nowText = formatter.format(Date())
            delay(1000L)
        }
    }
    return externalTime ?: nowText
}

@Composable
private fun rememberBatteryPercent(): androidx.compose.runtime.State<Int> {
    val context = LocalContext.current
    val batteryPercent = remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent.intValue = (level * 100 / scale).coerceIn(0, 100)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)?.let { sticky ->
            receiver.onReceive(context, sticky)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return batteryPercent
}

@Composable
private fun rememberConnectedWifiName(): androidx.compose.runtime.State<String> {
    val context = LocalContext.current
    val wifiName = remember { mutableStateOf("未连接WiFi") }

    fun resolveSsid(): String {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // 获取 WiFi 名称前置条件：必须具备定位权限
        if (!hasFineLocation && !hasCoarseLocation) return "请授予定位权限"

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return "WiFi不可用"
        val info = wifiManager.connectionInfo ?: return "未连接WiFi"
        val rawSsid = info.ssid ?: return "未连接WiFi"
        val ssid = rawSsid.trim('"')
        return if (ssid.isBlank() || ssid.equals("<unknown ssid>", ignoreCase = true)) {
            "未连接WiFi"
        } else {
            ssid
        }
    }
    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiName.value = resolveSsid()
            }

            override fun onLost(network: Network) {
                wifiName.value = resolveSsid()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                wifiName.value = resolveSsid()
            }
        }

        wifiName.value = resolveSsid()
        if (connectivityManager != null) {
            runCatching {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder().build(),
                    callback
                )
            }
        }

        onDispose {
            if (connectivityManager != null) {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }

    return wifiName
}

@Composable
private fun BellDotIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_topbar_message),
        contentDescription = "消息",
        modifier = modifier.size(20.dp)
    )
}

@Composable
private fun WifiSignalIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_topbar_wifi),
        contentDescription = "WiFi信号",
        modifier = modifier
    )
}

@Composable
private fun BatteryIcon(
    modifier: Modifier = Modifier,
    percent: Int
) {
    val levelColor = when {
        percent <= 15 -> Color(0xFFD82B2A)
        percent <= 30 -> Color(0xFFFFA000)
        else -> Color(0xFF202937)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .border(1.4.dp, levelColor, RoundedCornerShape(2.dp))
                .padding(1.4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((12f * percent.coerceIn(0, 100) / 100f).dp)
                    .background(levelColor, RoundedCornerShape(1.dp))
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 1.dp)
                .width(2.dp)
                .height(6.dp)
                .background(levelColor, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun VerticalBatteryView(
    level: Int, // 0~100
    modifier: Modifier = Modifier,
    width: Dp = 24.dp,
    height: Dp = 60.dp
) {
    val percent = (level.coerceIn(0, 100)) / 100f

    val color = when {
        level < 10 -> Color.Red
        level < 20 -> Color.Yellow
        else -> Color(0xFF4CAF50)
    }

    Canvas(
        modifier = modifier.size(width, height)
    ) {
        val strokeWidth = 2.dp.toPx()

        // 电池头（顶部）
        val capHeight = size.height * 0.15f
        val capWidth = size.width * 0.5f

        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(size.width / 2 - capWidth / 2, 0f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // 电池主体区域（去掉头部）
        val bodyTop = capHeight
        val bodyHeight = size.height - capHeight

        // 外框
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val padding = 1.dp.toPx()

        // 填充高度（从底部向上）
        val fillHeight = (bodyHeight - padding * 2) * percent

        drawRoundRect(
            color = color,
            topLeft = Offset(
                padding,
                bodyTop + bodyHeight - padding - fillHeight // 从底部开始
            ),
            size = Size(
                size.width - padding * 2,
                fillHeight
            ),
//            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}
