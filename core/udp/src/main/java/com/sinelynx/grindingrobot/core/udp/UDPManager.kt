package com.sinelynx.grindingrobot.core.udp

import com.sinelynx.grindingrobot.core.util.log.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UDP 通信管理：绑定本地端口接收数据，并向指定主机/端口发送数据。
 * 通过 Hilt 注入单例，上层调用 [initialize] 后开始监听，[release] 释放资源。
 */
@Singleton
class UDPManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null

    /** 当前绑定的本地端口；未启动时为 null */
    val boundLocalPort: Int?
        get() = synchronized(lock) { socket?.localPort?.takeIf { it > 0 } }

    /**
     * 绑定本地 [localPort] 并开始接收 UDP 报文。
     * 重复调用会先 [release] 再重新绑定。
     *
     * @param localPort 本地监听端口 1..65535
     * @param onReceived 收到数据时回调：负载、对端 IP、对端端口
     */
    fun initialize(localPort: Int, onReceived: (ByteArray, String, Int) -> Unit) {
        if (localPort !in 1..65535) {
            LogUtils.e("UDPManager: invalid localPort $localPort")
            return
        }
        release()
        receiveJob = scope.launch {
            val s = try {
                DatagramSocket(localPort)
            } catch (e: Exception) {
                LogUtils.e("UDPManager: bind failed on port $localPort: ${e.message}")
                return@launch
            }
            synchronized(lock) {
                socket = s
            }
            LogUtils.d("UDPManager: listening on UDP :$localPort")
            val buf = ByteArray(MAX_UDP_PAYLOAD)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    s.receive(packet)
                    val n = packet.length
                    if (n <= 0) continue
                    val data = buf.copyOf(n)
                    val host = packet.address.hostAddress ?: ""
                    val remotePort = packet.port
                    onReceived(data, host, remotePort)
                } catch (e: SocketException) {
                    if (!isActive || s.isClosed) break
                    LogUtils.e("UDPManager: receive SocketException: ${e.message}")
                } catch (e: Exception) {
                    LogUtils.e("UDPManager: receive error: ${e.message}")
                }
            }
        }
    }

    /**
     * 在内部 IO 协程中发送 UDP 数据（非阻塞调用方线程）。
     */
    fun send(host: String, port: Int, data: ByteArray) {
        if (data.isEmpty()) return
        scope.launch {
            doSend(host, port, data)
        }
    }

    /**
     * 挂起发送，便于在协程中组合或处理返回值。
     *
     * @return 是否发送成功
     */
    suspend fun sendSuspend(host: String, port: Int, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            doSend(host, port, data)
        }

    private fun doSend(host: String, port: Int, data: ByteArray): Boolean {
        val s = synchronized(lock) { socket }
        if (s == null || s.isClosed) {
            LogUtils.w("UDPManager: send ignored, call initialize() first")
            return false
        }
        return try {
            val packet = DatagramPacket(data, data.size, InetAddress.getByName(host), port)
            s.send(packet)
            true
        } catch (e: Exception) {
            LogUtils.e("UDPManager: send failed: ${e.message}")
            false
        }
    }

    /**
     * 关闭套接字并停止接收协程。
     */
    fun release() {
        synchronized(lock) {
            socket?.close()
            socket = null
        }
        receiveJob?.cancel()
        receiveJob = null
        LogUtils.d("UDPManager: released")
    }

    companion object {
        private const val MAX_UDP_PAYLOAD = 1024
    }
}
