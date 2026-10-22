package com.sinelynx.grindingrobot.feature.common.control

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TravelDirection(val protocolSign: Float) {
    STOPPED(0f), FORWARD(-1f), BACKWARD(1f)
}

data class ManualDriveState(
    val direction: TravelDirection = TravelDirection.STOPPED,
    val steering: Float = 0f,
    val speedMps: Float = 0.05f,
    val turnPercent: Int = 70,
    val enabled: Boolean = false,
    val inputEpoch: Int = 0,
) {
    val travelFactor: Float get() = 1f - abs(steering)
    val commandedSpeedMps: Float
        get() = if (direction == TravelDirection.STOPPED) 0f else speedMps * travelFactor
    val hasMotion: Boolean get() = direction != TravelDirection.STOPPED || steering != 0f

    fun target(): ManualDriveTarget = ManualDriveTarget(
        remoteX = steering,
        remoteY = direction.protocolSign * commandedSpeedMps / MAX_SPEED_MPS,
        speedRatio = if (hasMotion) 1f else 0f,
        maxSpeedMps = MAX_SPEED_MPS,
        maxTurnSpeedRatio = turnPercent / 100f,
    )

    companion object {
        const val MIN_SPEED_MPS = 0.02f
        const val MAX_SPEED_MPS = 0.15f
    }
}

data class ManualDriveTarget(
    val remoteX: Float,
    val remoteY: Float,
    val speedRatio: Float,
    val maxSpeedMps: Float,
    val maxTurnSpeedRatio: Float,
)

/** Main-thread owner of the complete target. A stopped generation can never replay old input. */
class ManualDriveController(
    private val scope: CoroutineScope,
    private val canControl: () -> Boolean,
    private val send: (ManualDriveTarget) -> Boolean,
    private val onStateChanged: (ManualDriveState) -> Unit,
    private val prepare: suspend () -> Boolean = { true },
    private val onPreparationFailed: () -> Unit = {},
) {
    var state = ManualDriveState()
        private set
    private var sender: Job? = null
    private var generation = 0
    private var prepared = false

    private fun publish(value: ManualDriveState) {
        state = value
        onStateChanged(value)
    }

    fun refreshAvailability() {
        val available = canControl()
        if (!available && state.hasMotion) stop()
        if (state.enabled != available) publish(state.copy(enabled = available))
    }

    fun travel(direction: TravelDirection) {
        if (direction == TravelDirection.STOPPED) { stop(); return }
        if (!state.enabled || !canControl()) return
        if (state.direction != TravelDirection.STOPPED && state.direction != direction) {
            stop()
            return
        }
        publish(state.copy(direction = direction))
        transmit()
    }

    fun steer(value: Float, epoch: Int) {
        if (epoch != state.inputEpoch || !state.enabled || !canControl()) return
        publish(state.copy(steering = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f))
        transmit()
    }

    fun cancelSteering(epoch: Int) {
        if (epoch == state.inputEpoch) stop()
    }

    fun setSpeed(value: Float) {
        if (!value.isFinite()) return
        val speed = ((value * 100).roundToInt() / 100f)
            .coerceIn(ManualDriveState.MIN_SPEED_MPS, ManualDriveState.MAX_SPEED_MPS)
        publish(state.copy(speedMps = speed))
        if (state.hasMotion) transmit()
    }

    fun setTurnPercent(value: Float) {
        if (!value.isFinite()) return
        publish(state.copy(turnPercent = value.roundToInt().coerceIn(1, 100)))
        if (state.hasMotion) transmit()
    }

    fun stop() = reset(invalidateTouch = true)

    private fun reset(invalidateTouch: Boolean) {
        val needsZero = state.hasMotion || sender != null || prepared
        generation++
        sender?.cancel()
        sender = null
        prepared = false
        publish(state.copy(direction = TravelDirection.STOPPED, steering = 0f,
            inputEpoch = state.inputEpoch + if (invalidateTouch) 1 else 0))
        // Zero bypasses task-stop preparation and invalidates its pending target immediately.
        if (needsZero) send(state.target())
    }

    private fun sendLatest(): Boolean {
        if (!canControl()) { refreshAvailability(); return false }
        if (!send(state.target())) {
            reset(invalidateTouch = true)
            publish(state.copy(enabled = false))
            return false
        }
        return true
    }

    private fun transmit() {
        if (!state.hasMotion) { reset(invalidateTouch = false); return }
        if (!canControl()) { refreshAvailability(); return }
        if (prepared) { sendLatest(); return }
        if (sender?.isActive == true) return
        val requestedGeneration = generation
        sender = scope.launch {
            val accepted = try {
                prepare()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (requestedGeneration != generation || !state.hasMotion || !canControl()) return@launch
            if (!accepted) {
                reset(invalidateTouch = true)
                onPreparationFailed()
                return@launch
            }
            prepared = true
            if (!sendLatest()) return@launch
            while (isActive && requestedGeneration == generation) {
                delay(100L)
                if (!state.hasMotion || !sendLatest()) break
            }
        }
    }
}
