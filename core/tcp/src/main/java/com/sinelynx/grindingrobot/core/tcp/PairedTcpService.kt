package com.sinelynx.grindingrobot.core.tcp

import android.content.Context
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sl_link.SlFrame
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

/** One tablet, two independently recoverable streams. All blocking I/O stays off Main. */
class PairedTcpService internal constructor(private val runtime: ChannelRuntime, private val controlPort: Int = 8003) {
    interface Callback {
        fun onFrameReceived(frame: SlFrame, arrivalMonotonicS: Double, controlChannel: Boolean)
        fun onControlConnectionChanged(ready: Boolean)
        fun onConnected()
        fun onDisconnected(reason: String)
    }

    constructor(context: Context) : this(AndroidChannelRuntime(context))
    data class Status(val dataReady: Boolean = false, val controlReady: Boolean = false,
                      val occupied: Boolean = false, val message: String = "未连接")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryLock = Mutex()
    private val targetLock = Any()
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()
    private val _status = MutableStateFlow(Status())
    val status = _status.asStateFlow()
    private val _manualCancellation = MutableStateFlow(0L)
    val manualCancellation = _manualCancellation.asStateFlow()
    @Volatile private var dataPeer: Peer? = null
    @Volatile private var controlPeer: Peer? = null
    @Volatile private var host: String? = null
    @Volatile private var dataPort = 8002
    @Volatile private var token = ""
    @Volatile private var enabled = false
    @Volatile private var foreground = false
    @Volatile private var mainPulseAt = 0L
    private var callback: Callback? = null
    private var recovery: Job? = null
    private val probePending = AtomicBoolean()
    private val recoveryWake = Channel<Unit>(Channel.CONFLATED)
    private val epoch = AtomicLong()
    private val revision = AtomicLong()
    private var target: SlLink.ControlCommand? = null
    private var requireNeutral = false
    private var lastManualSent = 0L
    @Volatile private var lastAckTrace = 0L
    @Volatile private var foregroundAt = 0L
    init {
        runtime.observeNetwork { recoverNow(probe = true) }
        scope.launch {
            while (isActive) {
                delay(25)
                val current = now()
                for (peer in listOfNotNull(dataPeer, controlPeer)) {
                    if ((peer.writingAt > 0 && current - peer.writingAt >= 500) ||
                        (peer.ready && current - peer.lastSessionAck >= 3000)) {
                        drop(peer, "通道超时")
                    } else if (peer.ready && current - peer.lastHeartbeat >= 1000) {
                        peer.lastHeartbeat = current
                        session(peer, SlLink.ChannelSessionAction.CHANNEL_SESSION_HEARTBEAT)
                    }
                }
                val peer = controlPeer
                if (peer != null && peer.ready) {
                    if (!foreground || current - mainPulseAt >= 300) {
                        cancelManual("界面已暂停或无响应")
                    }
                    val pending = peer.manualAck
                    if (pending != null && current - pending.second >= 500) {
                        drop(peer, "遥控确认超时")
                    } else if (pending == null && current - lastManualSent >= 100) {
                        synchronized(targetLock) {
                            target?.let { command ->
                                val bytes = SlMessageBuilder.buildControlCommandRaw(command.toByteArray(), 0x10u)
                                if (peer.enqueue(Outbound(bytes, manualEpoch = epoch.get(), manualRevision = revision.get()))) {
                                    lastManualSent = current
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun now() = runtime.now()
    private data class Outbound(val bytes: ByteArray, val written: (() -> Unit)? = null,
                                val manualEpoch: Long? = null, val stop: Boolean = false,
                                val manualRevision: Long? = null, val emergency: Boolean = false)
    private inner class Peer(val control: Boolean) {
        val socket = Socket()
        val parser = SlFrameParser()
        val ordinary = LinkedBlockingDeque<Outbound>(16)
        val urgent = LinkedBlockingDeque<Outbound>(8)
        val emergencies = LinkedBlockingDeque<Outbound>(8)
        val pendingManual = AtomicReference<Outbound?>()
        val replies = ConcurrentHashMap<Int, CompletableDeferred<SlLink.ChannelSessionResponse>>()
        @Volatile var ready = false
        @Volatile var closed = false
        @Volatile var writingAt = 0L
        @Volatile var lastSessionAck = now()
        @Volatile var lastHeartbeat = now()
        @Volatile var manualAck: Pair<Int, Long>? = null
        fun current() = !closed && if (control) controlPeer === this else dataPeer === this
        fun enqueue(item: Outbound): Boolean {
            if (!current()) return false
            if (item.manualEpoch != null && !item.stop) {
                pendingManual.set(item)
                return true
            }
            return (if (item.emergency) emergencies else if (item.stop) urgent else ordinary).offer(item)
        }
        fun close() {
            closed = true
            ready = false
            // Close the socket first: closing a buffered output stream can block flushing.
            runCatching { socket.close() }
            ordinary.clear(); urgent.clear()
            emergencies.clear(); pendingManual.set(null)
            replies.values.forEach { it.cancel() }
            replies.clear()
        }
        fun start() {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.sendBufferSize = if (control) 16384 else 65536
            socket.connect(InetSocketAddress(host, if (control) controlPort else dataPort), 1000)
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            scope.launch {
                try {
                    val buffer = ByteArray(8192)
                    while (current()) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val arrival = now() / 1e3
                        for (frame in parser.parse(buffer.copyOf(count))) {
                            if (!current()) break
                            if (frame.srcId != 0x10u.toUByte() || frame.dstId != 0x01u.toUByte()) continue
                            if (frame.msgId.toInt() == 0x0404) {
                                val reply = SlLink.ChannelSessionResponse.parseFrom(frame.payload)
                                val expectedRole = if (control) SlLink.ChannelRole.CHANNEL_ROLE_CONTROL else SlLink.ChannelRole.CHANNEL_ROLE_DATA
                                if (reply.role != expectedRole) continue
                                val waiting = replies.remove(frame.ackSeq.toInt()) ?: continue
                                lastSessionAck = now()
                                waiting.complete(reply)
                                if (reply.result != SlLink.ResultCode.RESULT_SUCCESS && ready) {
                                    drop(this@Peer, reply.message)
                                }
                            } else if (ready) {
                                if (control && frame.msgId.toInt() == 0x0402 && manualAck?.first == frame.ackSeq.toInt()) {
                                    val current = now()
                                    val sentAt = manualAck?.second ?: current
                                    if (current - lastAckTrace >= 1000) {
                                        lastAckTrace = current
                                        runtime.trace("manual_ack seq=${frame.ackSeq} rtt_ms=${current - sentAt}")
                                    }
                                    manualAck = null
                                    if (SlLink.ControlCommandResponse.parseFrom(frame.payload).result != SlLink.ResultCode.RESULT_SUCCESS) {
                                        cancelManual("遥控被设备拒绝")
                                    }
                                }
                                callback?.onFrameReceived(frame, arrival, control)
                            }
                        }
                    }
                } catch (_: Exception) { } finally { drop(this@Peer, "连接已断开") }
            }
            scope.launch {
                try {
                    while (current()) {
                        val item = emergencies.poll() ?: urgent.poll() ?: ordinary.poll() ?: pendingManual.getAndSet(null)
                        if (item == null) { delay(10); continue }
                        if (item.manualEpoch != null && item.manualEpoch != epoch.get()) continue
                        if (item.manualRevision != null && item.manualRevision != revision.get()) continue
                        if (item.manualEpoch != null && !item.stop && manualAck != null) continue
                        writingAt = now()
                        if (item.manualEpoch != null && !item.stop) manualAck = ChannelRouting.seq(item.bytes) to writingAt
                        output.write(item.bytes)
                        output.flush()
                        writingAt = 0
                        item.written?.invoke()
                    }
                } catch (_: Exception) { } finally { drop(this@Peer, "发送失败") }
            }
        }
    }

    private fun publish(message: String = _status.value.message, occupied: Boolean = _status.value.occupied) {
        val previous = _status.value.controlReady
        _status.value = Status(dataPeer?.ready == true, controlPeer?.ready == true, occupied, message)
        _connectionState.value = if (_status.value.dataReady) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        if (previous != _status.value.controlReady) {
            runtime.trace("control_ready=${_status.value.controlReady} foreground_recovery_ms=${now() - foregroundAt}")
            callback?.onControlConnectionChanged(_status.value.controlReady)
        }
        if (_status.value.occupied) runtime.trace("device_occupied")
    }
    private fun drop(peer: Peer, reason: String) {
        synchronized(this) {
            if (!peer.current()) { peer.close(); return }
            peer.close()
            if (peer.control) {
                controlPeer = null
                cancelManual(reason)
            } else {
                dataPeer = null
                callback?.onDisconnected(reason)
            }
            publish(reason)
        }
        recoverNow()
    }
    private fun session(peer: Peer, action: SlLink.ChannelSessionAction): CompletableDeferred<SlLink.ChannelSessionResponse> {
        val request = SlLink.ChannelSessionRequest.newBuilder().setAction(action)
            .setRole(if (peer.control) SlLink.ChannelRole.CHANNEL_ROLE_CONTROL else SlLink.ChannelRole.CHANNEL_ROLE_DATA)
            .setToken(if (action == SlLink.ChannelSessionAction.CHANNEL_SESSION_OPEN) "" else token).build()
        val bytes = SlMessageBuilder.buildChannelSessionRequestRaw(request.toByteArray(), 0x10u)
        val reply = CompletableDeferred<SlLink.ChannelSessionResponse>()
        peer.replies[ChannelRouting.seq(bytes)] = reply
        if (!peer.enqueue(Outbound(bytes, stop = true))) reply.cancel()
        return reply
    }
    private suspend fun establish(control: Boolean): Boolean {
        val peer = Peer(control)
        if (control) controlPeer = peer else dataPeer = peer
        try {
            peer.start()
            val action = if (control) SlLink.ChannelSessionAction.CHANNEL_SESSION_BIND
                else if (token.isEmpty()) SlLink.ChannelSessionAction.CHANNEL_SESSION_OPEN else SlLink.ChannelSessionAction.CHANNEL_SESSION_RECOVER
            val response = withTimeout(1000) { session(peer, action).await() }
            if (!peer.current()) return false
            if (response.result != SlLink.ResultCode.RESULT_SUCCESS) {
                // A server restart invalidates our token. Try fresh admission once;
                // an actual foreign owner will reject OPEN and stop further retries.
                if (token.isNotEmpty()) {
                    token = ""
                    dataPeer?.close(); controlPeer?.close()
                    dataPeer = null; controlPeer = null
                    cancelManual("原连接组已失效")
                    callback?.onDisconnected("原连接组已失效")
                    publish("重新建立连接组", occupied = false)
                } else publish(response.message, occupied = response.result == SlLink.ResultCode.RESULT_BUSY)
                peer.close()
                if (control) controlPeer = null else dataPeer = null
                return false
            }
            if (!control) token = response.token
            peer.ready = true
            publish("已连接", occupied = false)
            if (!control) callback?.onConnected()
            return true
        } catch (_: Exception) {
            if (peer.current()) {
                peer.close()
                if (control) controlPeer = null else dataPeer = null
            }
            publish("正在重连")
            return false
        }
    }
    fun recoverNow(probe: Boolean = false) {
        if (!enabled || host == null) return
        synchronized(this) {
            if (probe) probePending.set(true)
            if (recovery?.isActive == true) {
                recoveryWake.trySend(Unit)
                return
            }
            recovery = scope.launch {
              try {
                recoveryLock.withLock {
                    var attempt = 0
                    while (isActive && enabled && !_status.value.occupied) {
                      if (probePending.getAndSet(false)) {
                        for (peer in listOfNotNull(controlPeer, dataPeer).filter { it.ready }) {
                            val okay = runCatching {
                                withTimeout(300) { session(peer, SlLink.ChannelSessionAction.CHANNEL_SESSION_HEARTBEAT).await() }
                            }.getOrNull()?.result == SlLink.ResultCode.RESULT_SUCCESS
                            if (!okay) {
                                peer.close()
                                if (peer.control) { controlPeer = null; cancelManual("控制通道需恢复") }
                                else { dataPeer = null; callback?.onDisconnected("数据通道需恢复") }
                                publish("正在恢复")
                            }
                        }
                      }
                        // A valid paired token can restore control before data.
                        if (token.isNotEmpty() && controlPeer?.ready != true) establish(true)
                        if (dataPeer?.ready != true) establish(false)
                        if (token.isNotEmpty() && controlPeer?.ready != true) establish(true)
                        if (dataPeer?.ready == true && controlPeer?.ready == true && !probePending.get()) return@withLock
                        withTimeoutOrNull(longArrayOf(500, 1000, 2000, 3000)[attempt.coerceAtMost(3)]) {
                            recoveryWake.receive()
                        }
                        attempt++
                    }
                }
              } finally {
                val ownJob = coroutineContext[Job]
                val completedCurrent = synchronized(this@PairedTcpService) {
                    if (recovery === ownJob) { recovery = null; true } else false
                }
                if (completedCurrent && enabled && !_status.value.occupied &&
                    (probePending.get() || dataPeer?.ready != true || controlPeer?.ready != true)) recoverNow()
              }
            }
        }
    }
    fun setCallback(value: Callback) { callback = value }
    fun connect(host: String, port: Int) {
        if (this.host != host || dataPort != port) {
            disconnect()
            token = ""
        }
        this.host = host
        dataPort = port
        enabled = true
        publish("正在连接", occupied = false)
        recoverNow()
    }
    suspend fun connectAsync(host: String, port: Int): Boolean {
        connect(host, port)
        return withTimeoutOrNull(3500) {
            while (enabled && !_status.value.occupied && !isConnected()) delay(20)
            isConnected()
        } ?: false
    }
    fun sendData(bytes: ByteArray, onWritten: (() -> Unit)? = null): Boolean {
        if (bytes.size < 18) return false
        val control = ChannelRouting.control(bytes)
        val peer = if (control) controlPeer else dataPeer
        if (peer?.ready != true) return false
        if (!control && ChannelRouting.id(bytes) == 0x0502 && controlPeer?.ready != true) return false
        val emergency = control && ChannelRouting.id(bytes) == 0x0401 && runCatching {
            val command = SlLink.ControlCommand.parseFrom(bytes.copyOfRange(15, bytes.size - 3))
            command.hasEmergencyStop() && command.emergencyStop.enabled
        }.getOrDefault(false)
        val stop = control && (ChannelRouting.id(bytes) == 0x0502 || emergency)
        if (stop) cancelManual("停车指令")
        val sent = peer.enqueue(Outbound(bytes.copyOf(), onWritten, stop = stop, emergency = emergency))
        if (!sent && control) drop(peer, "控制发送队列已满")
        return sent
    }
    fun sendManualData(bytes: ByteArray, stop: Boolean): Boolean = synchronized(targetLock) {
        if (bytes.size < 18) return@synchronized false
        val peer = controlPeer
        if (stop) {
            target = null
            epoch.incrementAndGet()
            requireNeutral = false
            return@synchronized peer?.takeIf { it.ready }?.enqueue(
                Outbound(bytes.copyOf(), manualEpoch = epoch.get(), stop = true)) ?: false
        }
        if (peer?.ready != true) return@synchronized false
        if (!foreground || now() - mainPulseAt >= 300 || requireNeutral) return@synchronized false
        target = SlLink.ControlCommand.parseFrom(bytes.copyOfRange(15, bytes.size - 3))
        revision.incrementAndGet()
        true
    }
    private fun cancelManual(reason: String) = synchronized(targetLock) {
        if (target == null) return@synchronized
        target = null
        requireNeutral = true
        runtime.trace("manual_cancel reason=$reason")
        epoch.incrementAndGet()
        _manualCancellation.value += 1
        controlPeer?.takeIf { it.ready }?.let { peer ->
            val zero = SlLink.ControlCommand.newBuilder().setManualDrive(SlLink.ManualDriveControl.newBuilder().setSpeedRatio(0f)).build()
            peer.enqueue(Outbound(SlMessageBuilder.buildControlCommandRaw(zero.toByteArray(), 0x10u), manualEpoch = epoch.get(), stop = true))
        }
    }
    fun onForeground() {
        foreground = true
        foregroundAt = now()
        mainPulseAt = now()
        runtime.startMainPulse { mainPulseAt = now() }
        recoverNow(probe = true)
    }
    fun onBackground() {
        foreground = false
        runtime.stopMainPulse()
        cancelManual("APP进入后台")
    }
    fun isConnected() = dataPeer?.ready == true
    fun isControlReady() = controlPeer?.ready == true
    fun disconnect() {
        enabled = false
        recovery?.cancel()
        cancelManual("主动断开")
        dataPeer?.close(); controlPeer?.close()
        dataPeer = null; controlPeer = null
        token = ""
        publish("未连接", occupied = false)
        callback?.onDisconnected("主动断开")
    }
    fun release() {
        disconnect()
        runtime.release()
        scope.cancel()
    }
}
