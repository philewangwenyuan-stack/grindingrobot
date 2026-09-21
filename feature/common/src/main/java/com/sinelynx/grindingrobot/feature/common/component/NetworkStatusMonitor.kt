package com.sinelynx.grindingrobot.feature.common.component

import android.Manifest
import android.R.attr.type
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统网络状态监听器
 *
 * 负责监听系统网络连接变化，并将当前网络类型回传到 AppState，供 UI 使用。
 */
@Singleton
class NetworkStatusMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appState: AppState
) {

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            LogUtils.d("NetworkStatusMonitor", "network available: $network")
            updateNetworkStatus()
        }

        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun onLost(network: Network) {
            super.onLost(network)
            LogUtils.d("NetworkStatusMonitor", "network lost: $network")
            updateNetworkStatus()
        }

        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            LogUtils.d("NetworkStatusMonitor", "network capabilities changed: $networkCapabilities")
            updateNetworkStatus()
        }
    }

    /**
     * 开始监听系统网络状态
     * 应在应用启动时调用一次，例如在 Application 或 AppState.initialize 中
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun start() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
            // 初始化时也同步一次当前网络状态
            updateNetworkStatus()
        } catch (e: Exception) {
            LogUtils.e("NetworkStatusMonitor", "registerNetworkCallback failed: ${e.message}")
            // 注册失败时，至少保证状态为无网络
            appState.updateNetworkType(AppState.NetworkType.NONE)
            appState.updateNetworkSignalLevel(0)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun updateNetworkStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val type = when {
            capabilities == null -> AppState.NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> AppState.NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> AppState.NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> AppState.NetworkType.ETHERNET
            else -> AppState.NetworkType.NONE
        }
        val signalLevel = when (type) {
            AppState.NetworkType.CELLULAR -> resolveCellularLevel(capabilities)
            AppState.NetworkType.WIFI -> resolveWifiLevel(capabilities)
            else -> 0
        }
        appState.updateNetworkType(type)
        appState.updateNetworkSignalLevel(signalLevel)
    }

    private fun resolveCellularLevel(capabilities: NetworkCapabilities?): Int {
            val signal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                capabilities?.signalStrength ?: Int.MIN_VALUE
            } else {
                // 低版本可在这里返回旧方案信号值，没有就默认 MIN_VALUE
                Int.MIN_VALUE
            }
            return  mapCellularLevel(signal)
    }

    private fun resolveWifiLevel(capabilities: NetworkCapabilities?): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wifiInfo = capabilities?.transportInfo as? WifiInfo
            val rssi = wifiInfo?.rssi ?: Int.MIN_VALUE
            if (rssi != Int.MIN_VALUE) {
                return mapWifiLevel(rssi)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rssi = capabilities?.signalStrength ?: Int.MIN_VALUE
            if (rssi != Int.MIN_VALUE) {
                return mapWifiLevel(rssi)
            }
        }

        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            if (info == null || info.networkId == -1) return 0
            val rssi = info.rssi
            if (rssi == Int.MIN_VALUE) return 0
            mapWifiLevel(rssi)
        } catch (_: Exception) {
            0
        }
    }

    private fun mapCellularLevel(rssi: Int): Int = mapGenericSignalLevel(rssi)

    private fun mapWifiLevel(rssi: Int): Int = mapGenericSignalLevel(rssi)

    private fun mapGenericSignalLevel(rssi: Int): Int {
        if (rssi == Int.MIN_VALUE) return 0
        return when {
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
    }
}
