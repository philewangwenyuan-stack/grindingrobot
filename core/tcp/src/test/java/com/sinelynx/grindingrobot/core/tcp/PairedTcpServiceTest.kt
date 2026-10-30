package com.sinelynx.grindingrobot.core.tcp

import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sl_link.Crc16
import sl_link.SlFrame
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class PairedTcpServiceTest {
    private class Runtime : ChannelRuntime {
        private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it).apply { isDaemon = true } }
        @Volatile var mainAlive = true
        @Volatile var pulse: (() -> Unit)? = null
        init { timer.scheduleAtFixedRate({ if (mainAlive) pulse?.invoke() }, 0, 50, java.util.concurrent.TimeUnit.MILLISECONDS) }
        override fun now() = System.nanoTime() / 1_000_000
        override fun startMainPulse(pulse: () -> Unit) { this.pulse = pulse }
        override fun stopMainPulse() { pulse = null }
        override fun observeNetwork(available: () -> Unit) {}
        override fun release() { timer.shutdownNow() }
    }
    private class Board : AutoCloseable {
        val data = ServerSocket(0)
        val control = ServerSocket(0)
        private val executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val controlSockets = ConcurrentHashMap.newKeySet<Socket>()
        private val sequence = AtomicInteger()
        val received = CopyOnWriteArrayList<Pair<Boolean, SlFrame>>()
        val movementTimes = CopyOnWriteArrayList<Long>()
        @Volatile var closed = false
        @Volatile var acknowledgeManual = true
        @Volatile var manualAckDelayMs = 0L
        @Volatile var skipNextManualAck = false
        @Volatile var occupied = false
        @Volatile var stallData = false
        @Volatile var token = "fixture-tablet-token"
        init {
            for ((listener, isControl) in listOf(data to false, control to true)) executor.execute {
                while (!closed) try {
                    val socket = listener.accept()
                    sockets.add(socket)
                    if (isControl) controlSockets.add(socket)
                    executor.execute { read(socket, isControl) }
                } catch (_: Exception) { }
            }
        }
        private fun read(socket: Socket, control: Boolean) {
            val parser = SlFrameParser()
            val buffer = ByteArray(8192)
            try {
                while (!closed) {
                    val count = socket.getInputStream().read(buffer)
                    if (count < 0) return
                    for (frame in parser.parse(buffer.copyOf(count))) {
                        if (frame.msgId.toInt() == 0x0403) {
                            val request = SlLink.ChannelSessionRequest.parseFrom(frame.payload)
                            val invalidToken = request.action != SlLink.ChannelSessionAction.CHANNEL_SESSION_OPEN && request.token != token
                            val result = if (occupied || invalidToken) SlLink.ResultCode.RESULT_BUSY else SlLink.ResultCode.RESULT_SUCCESS
                            val response = SlLink.ChannelSessionResponse.newBuilder().setRole(request.role)
                                .setResult(result).setToken(token).setControlReady(control)
                                .setMessage(if (occupied) "Device is occupied" else "Ready").build()
                            reply(socket, frame, 0x0404, response.toByteArray())
                        } else {
                            received.add(control to frame)
                            if (control && frame.msgId.toInt() == 0x0401) {
                                val command = SlLink.ControlCommand.parseFrom(frame.payload)
                                if (command.hasManualDrive() && command.manualDrive.speedRatio > 0) movementTimes.add(System.nanoTime())
                            }
                            if (!control && stallData) Thread.sleep(1000)
                            if (frame.msgId.toInt() == 0x0401 && acknowledgeManual) {
                                if (skipNextManualAck) {
                                    skipNextManualAck = false
                                    continue
                                }
                                val ack = {
                                    reply(socket, frame, 0x0402, SlLink.ControlCommandResponse.newBuilder()
                                        .setResult(SlLink.ResultCode.RESULT_SUCCESS).build().toByteArray())
                                }
                                val delayMs = manualAckDelayMs
                                if (delayMs == 0L) ack() else executor.execute {
                                    Thread.sleep(delayMs)
                                    runCatching { ack() }
                                }
                            }
                            if (frame.msgId.toInt() == 0x0502) reply(socket, frame, 0x0503,
                                SlLink.TaskCommandResponse.newBuilder().setResult(SlLink.ResultCode.RESULT_SUCCESS).build().toByteArray())
                        }
                    }
                }
            } catch (_: Exception) { } finally { socket.close(); sockets.remove(socket); controlSockets.remove(socket) }
        }
        private fun reply(socket: Socket, request: SlFrame, id: Int, payload: ByteArray) {
            val bytes = ByteBuffer.allocate(18 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put(0xFD.toByte()).put(0x55).put(1).put(0)
            bytes.putShort(sequence.incrementAndGet().toShort()).putShort(request.seq.toShort())
            bytes.put(0x10).put(1).put(7).putShort(id.toShort()).putShort(payload.size.toShort()).put(payload)
            bytes.putShort(Crc16.calculate(bytes.array().copyOfRange(0, 15 + payload.size)).toShort()).put(0xFE.toByte())
            synchronized(socket) { socket.getOutputStream().write(bytes.array()) }
        }
        fun movements() = received.filter { it.first && it.second.msgId.toInt() == 0x0401 }
            .map { SlLink.ControlCommand.parseFrom(it.second.payload) }.filter { it.hasManualDrive() }
            .map { it.manualDrive.speedRatio }
        fun cutControl() { controlSockets.toList().forEach { it.close() } }
        override fun close() {
            closed = true
            data.close(); control.close()
            sockets.toList().forEach { it.close() }
            executor.shutdownNow()
        }
    }
    private fun until(timeout: Long = 3500, condition: () -> Boolean) {
        val deadline = System.nanoTime() / 1_000_000 + timeout
        while (!condition() && System.nanoTime() / 1_000_000 < deadline) Thread.sleep(10)
        assertTrue("Condition not reached within ${timeout}ms", condition())
    }
    private fun command(speed: Float) = SlMessageBuilder.buildControlCommandRaw(
        SlLink.ControlCommand.newBuilder().setManualDrive(SlLink.ManualDriveControl.newBuilder()
            .setRemoteY(-0.25f).setSpeedRatio(speed)).build().toByteArray(), 0x10u)

    private fun paired(board: Board, runtime: Runtime): PairedTcpService {
        val service = PairedTcpService(runtime, board.control.localPort)
        service.onForeground()
        service.connect("127.0.0.1", board.data.localPort)
        until { service.isConnected() && service.isControlReady() }
        return service
    }

    @Test fun newerAcknowledgedSpeedRetiresACoalescedOlderSpeed() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                board.skipNextManualAck = true
                assertTrue(service.sendManualData(command(1f), false))
                until(1600) { board.movementTimes.size >= 10 }
                assertEquals(0L, service.manualCancellation.value)
                assertTrue(service.isControlReady())
            } finally { service.release() }
        }
    }

    @Test fun delayedAcknowledgementsDoNotThrottleTheManualRenewal() {
        Board().use { board ->
            board.manualAckDelayMs = 350
            val service = paired(board, Runtime())
            try {
                assertTrue(service.sendManualData(command(1f), false))
                until(timeout = 1600) { board.movementTimes.size >= 8 }
                val intervals = board.movementTimes.zipWithNext { a, b -> (b - a) / 1_000_000 }
                assertTrue("ACK latency must not stretch wire renewal: $intervals",
                    intervals.all { it < 280 })
                assertTrue(service.isControlReady())
            } finally { service.release() }
        }
    }

    @Test fun backgroundSenderRenewsWithoutFurtherUiCallsAndStopsOnBackground() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                assertTrue(service.sendManualData(command(1f), false))
                until { board.movements().count { it > 0 } >= 5 }
                Thread.sleep(5000)
                assertTrue("Wire renewal must continue without UI sends", board.movementTimes.size >= 40)
                val maxGap = board.movementTimes.zipWithNext { a, b -> (b-a)/1_000_000.0 }.maxOrNull()!!
                assertTrue("500ms watchdog gap: $maxGap", maxGap < 500)
                service.onBackground()
                until { board.movements().lastOrNull() == 0f }
                val count = board.movements().size
                Thread.sleep(250)
                assertEquals(count, board.movements().size)
            } finally { service.release() }
        }
    }

    @Test fun mainStallCancelsMotionAndOldTargetCannotResume() {
        Board().use { board ->
            val runtime = Runtime()
            val service = paired(board, runtime)
            try {
                service.sendManualData(command(1f), false)
                until { board.movements().any { it > 0 } }
                runtime.mainAlive = false
                until { board.movements().lastOrNull() == 0f }
                runtime.mainAlive = true
                Thread.sleep(120)
                assertFalse(service.sendManualData(command(1f), false))
                assertTrue(service.sendManualData(command(0f), true))
                assertTrue(service.sendManualData(command(1f), false))
                until { board.movements().lastOrNull() == 1f }
            } finally { service.release() }
        }
    }

    @Test fun dataCongestionCannotBlockManualAndStopRoutesToControl() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                board.stallData = true
                assertTrue(service.sendData(SlMessageBuilder.buildMapRequestRaw(SlLink.MapRequest.getDefaultInstance().toByteArray(), 0x10u)))
                assertTrue(service.sendManualData(command(1f), false))
                until { board.movements().count { it > 0 } >= 3 }
                val stop = SlLink.TaskCommand.newBuilder().setCommand(SlLink.TaskCommandType.TASK_CMD_STOP).build()
                assertTrue(service.sendData(SlMessageBuilder.buildTaskCommandRaw(stop.toByteArray(), 0x10u)))
                until { board.received.any { it.first && it.second.msgId.toInt() == 0x0502 } }
            } finally { service.release() }
        }
    }

    @Test fun foregroundRecoveryDoesNotWaitFiveSecondsOrReplayMotion() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                service.sendManualData(command(1f), false)
                until { board.movements().any { it > 0 } }
                service.onBackground()
                until { board.movements().lastOrNull() == 0f }
                board.cutControl()
                service.onForeground()
                until(3000) { service.isControlReady() }
                val count = board.movements().size
                Thread.sleep(250)
                assertEquals(count, board.movements().size)
            } finally { service.release() }
        }
    }

    @Test fun occupiedDeviceDoesNotReconnectRepeatedly() {
        Board().use { board ->
            board.occupied = true
            val service = PairedTcpService(Runtime(), board.control.localPort)
            try {
                service.connect("127.0.0.1", board.data.localPort)
                until { service.status.value.occupied }
                assertFalse(service.isConnected())
                assertFalse(service.isControlReady())
            } finally { service.release() }
        }
    }

    @Test fun expiredTokenReopensFreeDeviceWithoutFalseOccupiedState() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                board.token = "new-board-generation"
                board.cutControl()
                service.onForeground()
                until(3000) { service.isConnected() && service.isControlReady() && !service.status.value.occupied }
                assertFalse(service.status.value.occupied)
            } finally { service.release() }
        }
    }

    @Test fun lostManualAckCancelsTargetInsteadOfReplayingAfterReconnect() {
        Board().use { board ->
            val service = paired(board, Runtime())
            try {
                board.acknowledgeManual = false
                service.sendManualData(command(1f), false)
                until { service.manualCancellation.value > 0 }
                until { service.isControlReady() }
                val sentBeforeRecovery = board.movements().count { it > 0 }
                assertTrue("Outstanding renewals must stay bounded", sentBeforeRecovery in 1..8)
                Thread.sleep(200)
                assertEquals(sentBeforeRecovery, board.movements().count { it > 0 })
                assertFalse(service.sendManualData(command(1f), false))
            } finally { service.release() }
        }
    }
}
