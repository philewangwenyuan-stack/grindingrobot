package com.sinelynx.grindingrobot.feature.main.viewmodel

import android.os.SystemClock
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import com.sinelynx.grindingrobot.core.model.state.DeviceStatusStream
import com.sinelynx.grindingrobot.core.model.state.SystemStatusPayload
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemStatusUiState(
    val connected: Boolean = false,
    val fresh: Boolean = false,
    val status: SystemStatusPayload? = null,
    val wifiName: String = "未知",
    val wifiRssi: Int? = null,
    val dataRttMs: Long? = null,
    val controlRttMs: Long? = null,
)

@HiltViewModel
class SystemStatusViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemStatusUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh() {
        val connected = tcpManager.channelStatus.value.dataReady
        val status = DeviceStatusStream.systemStatus.value
        val age = status?.let { SystemClock.elapsedRealtimeNanos() / 1e9 - it.receivedMonotonicS }
        val fresh = connected && age != null && age in 0.0..3.0
        val wifi = readWifi()
        val latency = tcpManager.channelLatencySnapshot()
        _uiState.value = SystemStatusUiState(connected, fresh, status.takeIf { fresh },
            wifi.first, wifi.second, latency.dataRttMs, latency.controlRttMs)
    }

    @Suppress("DEPRECATION")
    private fun readWifi(): Pair<String, Int?> = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.allNetworks.mapNotNull { manager.getNetworkCapabilities(it) }
            .firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?: return@runCatching "未连接" to null
        val wifi = (if (Build.VERSION.SDK_INT >= 29) capabilities.transportInfo as? WifiInfo else null)
            ?: (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        val name = wifi?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" } ?: "名称不可用"
        name to wifi?.rssi?.takeIf { it in -126..-1 }
    }.getOrDefault("读取受限" to null)
}
