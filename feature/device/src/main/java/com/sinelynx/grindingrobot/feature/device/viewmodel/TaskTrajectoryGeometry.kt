package com.sinelynx.grindingrobot.feature.device.viewmodel

import kotlin.math.*

/** Immutable chunks share completed geometry; only the small live tail is copied. */
class TaskTrajectoryRun internal constructor(internal val xy: FloatArray, val colorArgb: Int) {
    val vertexCount: Int get() = xy.size / 2
}
class TaskTrajectoryChunk internal constructor(val previous: TaskTrajectoryChunk?, val run: TaskTrajectoryRun)
data class TaskTrajectorySnapshot internal constructor(
    val session: Any,
    val completed: TaskTrajectoryChunk? = null,
    val tail: TaskTrajectoryRun? = null,
    val latestPose: TaskPoseUiItem? = null,
    val vertexCount: Int = 0
) {
    companion object { val Empty = TaskTrajectorySnapshot(Any()) }
}

/**
 * Full received execution, with no time/point eviction. A merged segment's angular
 * corridor includes EVERY absorbed sample, not just the last three points. The
 * monotonically increasing anchor radius also preserves reversals and bounds the
 * segment-end error. Thus straight/noisy runs compact within tolerance, bends stay.
 */
internal class TaskTrajectoryAccumulator(private val toleranceM: Double = 0.01, private val chunkVertices: Int = 128) {
    init { require(toleranceM >= 0 && toleranceM.isFinite() && chunkVertices >= 3) }
    private var taskId = ""
    private var session = Any()
    private var completed: TaskTrajectoryChunk? = null
    private var sealedVertices = 0
    private var xy = FloatArray(chunkVertices * 2)
    private var size = 0
    private var color = 0
    private var revision = 0L
    private var snapshotRevision = -1L
    private var cached = TaskTrajectorySnapshot.Empty
    private var latestPose: TaskPoseUiItem? = null
    private var recording = false
    private var pending = ArrayList<TaskPoseUiItem?>()
    private var localX: Float? = null
    private var localY: Float? = null
    private var baseAngle = 0.0
    private var hasAngle = false
    private var low = -PI
    private var high = PI
    private var maxRadius = 0.0
    var receivedCount = 0L; private set

    fun reset(): TaskTrajectorySnapshot {
        taskId = ""; session = Any(); completed = null; sealedVertices = 0
        size = 0; revision = 0; snapshotRevision = -1
        latestPose = null; recording = false; pending = ArrayList(); localX = null; localY = null
        receivedCount = 0
        cached = TaskTrajectorySnapshot(session)
        return cached
    }

    fun breakContinuity(): TaskTrajectorySnapshot {
        seal()
        // Unknown-position intervals must not reconnect across buffered samples.
        if (pending.isNotEmpty() && pending.last() != null) pending.add(null)
        latestPose = null; recording = false
        return snapshot()
    }

    fun accept(reportTaskId: String, state: DeviceTaskUiState, pose: TaskPoseUiItem?,
               discLocalX: Float?, discLocalY: Float?): TaskTrajectorySnapshot {
        if (state == DeviceTaskUiState.IDLE || reportTaskId.isBlank()) {
            return if (taskId.isBlank()) cached else reset()
        }
        if (taskId != reportTaskId) { reset(); taskId = reportTaskId }
        val validLocal = discLocalX != null && discLocalY != null && discLocalX.isFinite() && discLocalY.isFinite()
        val changedLocal = validLocal && (localX != discLocalX || localY != discLocalY)
        if (changedLocal) {
            seal(); localX = discLocalX; localY = discLocalY
        }
        val resolvedPending = validLocal && pending.isNotEmpty()
        if (resolvedPending) {
            pending.forEach { if (it == null) seal() else addPose(it) }
            pending = ArrayList()
        }
        if (pose == null || !pose.x.isFinite() || !pose.y.isFinite() || !pose.headingDeg.isFinite()) {
            return breakContinuity()
        }
        if (state == DeviceTaskUiState.READY) {
            latestPose = pose; recording = false
            return snapshot()
        }
        receivedCount++
        if (latestPose == pose && recording && !changedLocal && !resolvedPending) return cached
        latestPose = pose; recording = true
        if (!validLocal) {
            if (pending.isEmpty()) seal()
            pending.add(pose)
        } else addPose(pose)
        return snapshot()
    }

    /** Settings arriving after initial poses can resolve the pending geometry once. */
    fun configure(discLocalX: Float?, discLocalY: Float?): TaskTrajectorySnapshot {
        if (taskId.isBlank() || pending.isEmpty() || discLocalX == null || discLocalY == null ||
            !discLocalX.isFinite() || !discLocalY.isFinite()) return cached
        if (localX != discLocalX || localY != discLocalY) seal()
        localX = discLocalX; localY = discLocalY
        pending.forEach { if (it == null) seal() else addPose(it) }
        pending = ArrayList()
        return snapshot()
    }

    private fun addPose(pose: TaskPoseUiItem) {
        val lx = localX ?: return
        val ly = localY ?: return
        val theta = Math.toRadians(pose.headingDeg.toDouble())
        val c = cos(theta).toFloat(); val s = sin(theta).toFloat()
        add(pose.x + lx * c - ly * s, pose.y + lx * s + ly * c, pose.lineColorArgb)
    }

    private fun add(x: Float, y: Float, nextColor: Int) {
        if (!x.isFinite() || !y.isFinite()) { seal(); return }
        if (size > 0 && nextColor != color) {
            val px = xy[(size - 1) * 2]; val py = xy[(size - 1) * 2 + 1]
            seal(); color = nextColor; append(px, py)
        }
        if (size == 0) { color = nextColor; append(x, y); return }
        if (xy[(size - 1) * 2] == x && xy[(size - 1) * 2 + 1] == y) return
        if (size >= 2 && canExtend(x, y)) {
            xy[(size - 1) * 2] = x; xy[(size - 1) * 2 + 1] = y
            revision++; return
        }
        if (size == chunkVertices) {
            val px = xy[(size - 1) * 2]; val py = xy[(size - 1) * 2 + 1]
            seal(); append(px, py)
        }
        append(x, y)
        if (size >= 2) resetCorridor()
    }

    private fun append(x: Float, y: Float) {
        xy[size * 2] = x; xy[size * 2 + 1] = y; size++; revision++
    }

    private fun resetCorridor() {
        hasAngle = false; low = -PI; high = PI; maxRadius = 0.0
        include(xy[(size - 1) * 2], xy[(size - 1) * 2 + 1], check = false)
    }

    private fun canExtend(x: Float, y: Float): Boolean = include(x, y, check = true)

    private fun include(x: Float, y: Float, check: Boolean): Boolean {
        val dx = x.toDouble() - xy[(size - 2) * 2]
        val dy = y.toDouble() - xy[(size - 2) * 2 + 1]
        val radius = hypot(dx, dy)
        if (radius <= toleranceM && maxRadius <= toleranceM) {
            maxRadius = max(maxRadius, radius); return true
        }
        val absolute = atan2(dy, dx)
        if (!hasAngle) { baseAngle = absolute; hasAngle = true }
        val angle = atan2(sin(absolute - baseAngle), cos(absolute - baseAngle))
        if (check && (radius < maxRadius || angle < low || angle > high)) return false
        val half = asin((toleranceM / radius).coerceIn(0.0, 1.0))
        low = max(low, angle - half); high = min(high, angle + half)
        maxRadius = max(maxRadius, radius)
        return true
    }

    private fun seal() {
        if (size == 0) return
        val run = TaskTrajectoryRun(xy.copyOf(size * 2), color)
        completed = TaskTrajectoryChunk(completed, run); sealedVertices += size
        size = 0; revision++
    }

    private fun snapshot(): TaskTrajectorySnapshot {
        val tail = if (snapshotRevision == revision) cached.tail
            else if (size == 0) null else TaskTrajectoryRun(xy.copyOf(size * 2), color)
        if (cached.completed !== completed || cached.tail !== tail || cached.latestPose != latestPose || cached.session !== session) {
            cached = TaskTrajectorySnapshot(session, completed, tail, latestPose, sealedVertices + size)
        }
        snapshotRevision = revision
        return cached
    }
}
