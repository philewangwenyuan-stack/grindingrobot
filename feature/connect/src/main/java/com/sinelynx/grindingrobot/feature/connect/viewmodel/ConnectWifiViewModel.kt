package com.sinelynx.grindingrobot.feature.connect.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.tcp.ConnectionState
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectWifiViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState
) : ViewModel() {

    private val _isTcpConnecting = MutableStateFlow(false)
    val isTcpConnecting: StateFlow<Boolean> = _isTcpConnecting.asStateFlow()

    val isTcpConnected: StateFlow<Boolean> = tcpManager.getConnectionStateFlow()
        .map { state -> state == ConnectionState.CONNECTED }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = tcpManager.isConnected()
        )

    val currentTcpEndpoint: StateFlow<String> = appState.tcpIpAddress
        .map { address -> appState.parseTcpEndpoint(address).address }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = appState.resolveTcpEndpoint().address
        )

    fun switchTcpEndpoint(address: String): Boolean {
        if (_isTcpConnecting.value) return false

        val endpoint = parseStrictTcpEndpoint(address)
        if (endpoint == null) {
            ToastUtils.showWarning("请输入正确的IP:端口")
            return false
        }
        _isTcpConnecting.value = true
        viewModelScope.launch {
            try {
                val success = runCatching {
                    tcpManager.switchTcpEndpoint(endpoint)
                }.getOrElse { error ->
                    LogUtils.w("ConnectWifiViewModel", "TCP IP switch failed: ${error.message}")
                    false
                }
                if (success) {
                    LogUtils.d("ConnectWifiViewModel", "TCP IP switched: ${endpoint.address}")
                } else {
                    ToastUtils.showError("设备IP连接失败")
                    LogUtils.w(
                        "ConnectWifiViewModel",
                        "TCP IP switch connection failed: ${endpoint.address}"
                    )
                }
            } finally {
                _isTcpConnecting.value = false
            }
        }
        return true
    }

    private fun parseStrictTcpEndpoint(address: String): AppState.TcpEndpoint? {
        val trimmed = address.trim()
        val parts = trimmed.split(':')
        if (parts.size != 2) return null

        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val octets = host.split('.')
        if (octets.size != 4) return null

        val validHost = octets.all { octet ->
            octet.isNotBlank() &&
                octet.all { it.isDigit() } &&
                octet.toIntOrNull()?.let { it in 0..255 } == true
        }
        if (!validHost) return null

        return AppState.TcpEndpoint(host, port)
    }
}
