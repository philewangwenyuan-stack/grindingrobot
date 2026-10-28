package com.sinelynx.grindingrobot.core.tcp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Injectable clocks/lifecycle notifications allow real TCP regression tests on the JVM. */
internal interface ChannelRuntime {
    fun now(): Long
    fun startMainPulse(pulse: () -> Unit)
    fun stopMainPulse()
    fun observeNetwork(available: () -> Unit)
    fun release()
    fun trace(message: String) {}
}

internal class AndroidChannelRuntime(context: Context) : ChannelRuntime {
    override fun trace(message: String) {
        com.sinelynx.grindingrobot.core.util.log.LogUtils.i("ControlChannel", message)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var tick: Runnable? = null
    private var network: ConnectivityManager.NetworkCallback? = null
    override fun now(): Long = SystemClock.elapsedRealtime()
    override fun startMainPulse(pulse: () -> Unit) {
        stopMainPulse()
        tick = object : Runnable {
            override fun run() { pulse(); handler.postDelayed(this, 100) }
        }.also(handler::post)
    }
    override fun stopMainPulse() { tick?.let(handler::removeCallbacks); tick = null }
    override fun observeNetwork(available: () -> Unit) {
        network = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { available() }
        }.also { connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), it) }
    }
    override fun release() {
        stopMainPulse()
        network?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
    }
}
