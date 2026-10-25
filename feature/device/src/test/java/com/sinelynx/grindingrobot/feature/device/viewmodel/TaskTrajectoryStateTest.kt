package com.sinelynx.grindingrobot.feature.device.viewmodel

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TaskTrajectoryStateTest {
    private fun pose(x: Float, y: Float = 0f, heading: Float = 0f, color: Int = 1) = TaskPoseUiItem(x, y, heading, color)
    private fun TaskTrajectoryAccumulator.add(pose: TaskPoseUiItem?, task: String = "task", state: DeviceTaskUiState = DeviceTaskUiState.RUNNING) =
        accept(task, state, pose, 0f, 0f)
    private fun runs(scene: TaskTrajectorySnapshot): List<TaskTrajectoryRun> {
        val result = ArrayList<TaskTrajectoryRun>()
        var chunk = scene.completed
        while (chunk != null) { result.add(chunk.run); chunk = chunk.previous }
        result.reverse(); scene.tail?.let(result::add)
        return result
    }

    @Test fun straightHundredThousandSamplesRetainFirstAndLastAsTwoVertices() {
        val accumulator = TaskTrajectoryAccumulator()
        var scene = TaskTrajectorySnapshot.Empty
        repeat(100_000) { scene = accumulator.add(pose(it / 100f)) }
        assertEquals(2, scene.vertexCount)
        assertArrayEquals(floatArrayOf(0f, 0f, 999.99f, 0f), scene.tail!!.xy, 0.0001f)
        assertEquals(pose(999.99f), scene.latestPose)
        assertEquals(100_000L, accumulator.receivedCount)
    }

    @Test fun turnsReversalsAndClosedLoopSurviveWithoutAgeEviction() {
        val accumulator = TaskTrajectoryAccumulator()
        val input = listOf(pose(0f), pose(10f), pose(10f,10f), pose(0f,10f), pose(0f), pose(-10f), pose(0f))
        var scene = TaskTrajectorySnapshot.Empty
        input.forEach { scene = accumulator.add(it) }
        assertEquals(input.size, scene.vertexCount)
        assertEquals(input.last(), scene.latestPose)
        assertArrayEquals(input.flatMap { listOf(it.x,it.y) }.toFloatArray(), scene.tail!!.xy, 0f)
    }

    @Test fun thousandsOfNonCollinearTurnsKeepEveryTurnAcrossChunks() {
        val accumulator=TaskTrajectoryAccumulator()
        var scene=TaskTrajectorySnapshot.Empty
        repeat(5000) { scene=accumulator.add(pose(it*.01f,(it%2)*.2f)) }
        val pieces=runs(scene)
        assertTrue(pieces.size>1)
        assertTrue(pieces.all { it.vertexCount<=128 })
        val xValues=pieces.flatMap { run -> (0 until run.vertexCount).map { run.xy[it*2] } }.distinct()
        assertEquals(5000,xValues.size)
        assertEquals(0f,xValues.first(),0f)
        assertEquals(49.99f,xValues.last(),.00001f)
    }

    @Test fun noisyStraightRunHasBoundedAbsoluteErrorWithoutAccumulatingDrift() {
        val accumulator = TaskTrajectoryAccumulator()
        val samples = (0..20_000).map { pose(it * .002f, (sin(it*.17)*.005).toFloat()) }
        var scene = TaskTrajectorySnapshot.Empty
        samples.forEach { scene = accumulator.add(it) }
        assertTrue(scene.vertexCount < 100)
        assertWithinOrderedSegments(samples, runs(scene).single().xy)
    }

    @Test fun curvedRunRespectsToleranceForEveryAbsorbedSample() {
        val accumulator = TaskTrajectoryAccumulator()
        val samples = (0..10_000).map { pose(it * .001f, (sin(it*.0006)*2).toFloat()) }
        var scene = TaskTrajectorySnapshot.Empty
        samples.forEach { scene = accumulator.add(it) }
        assertWithinOrderedSegments(samples, runs(scene).single().xy)
    }

    @Test fun corridorWorksAcrossNegativePiAngularWrap() {
        val accumulator = TaskTrajectoryAccumulator()
        var scene = TaskTrajectorySnapshot.Empty
        repeat(1000) { scene = accumulator.add(pose(-it*.01f, (sin(it*.2)*.0001).toFloat())) }
        assertTrue(scene.vertexCount < 10)
        assertEquals(-9.99f, scene.latestPose!!.x, .0001f)
    }

    @Test fun completedChunksShareIdentityAndOldSnapshotsNeverMutate() {
        val accumulator = TaskTrajectoryAccumulator(chunkVertices = 3)
        var scene = TaskTrajectorySnapshot.Empty
        repeat(30) { scene = accumulator.add(pose(it.toFloat(), (it%2).toFloat())) }
        val first = scene
        val copy = first.tail!!.xy.copyOf()
        val head = first.completed
        scene = accumulator.add(pose(30f,0f))
        assertArrayEquals(copy, first.tail!!.xy, 0f)
        assertTrue(scene.completed === head || scene.completed?.previous === head)
        assertEquals(0f, runs(scene).first().xy.first(), 0f)
        assertEquals(30f, scene.latestPose!!.x, 0f)
    }

    @Test fun colorChangeKeepsChronologyAndUsesDestinationColorForEdge() {
        val accumulator = TaskTrajectoryAccumulator()
        accumulator.add(pose(0f)); accumulator.add(pose(1f))
        val scene = accumulator.add(pose(2f,color=2))
        val pieces = runs(scene)
        assertEquals(listOf(1,2), pieces.map { it.colorArgb })
        assertArrayEquals(floatArrayOf(1f,0f,2f,0f), pieces.last().xy, 0f)
    }

    @Test fun missingOrInvalidPoseBreaksGeometryRatherThanDrawingThroughGap() {
        val accumulator = TaskTrajectoryAccumulator()
        accumulator.add(pose(0f)); accumulator.add(pose(1f)); accumulator.add(null)
        accumulator.add(pose(Float.NaN))
        val scene = accumulator.add(pose(10f))
        assertEquals(2, runs(scene).size)
        assertArrayEquals(floatArrayOf(10f,0f), scene.tail!!.xy, 0f)
    }

    @Test fun taskChangeIdleBlankAndExplicitStopClearPreviousExecution() {
        val accumulator = TaskTrajectoryAccumulator()
        val old = accumulator.add(pose(1f))
        val next = accumulator.add(pose(9f),task="other")
        assertNotSame(old.session,next.session)
        assertEquals(1,next.vertexCount)
        val idle = accumulator.add(pose(10f),task="other",state=DeviceTaskUiState.IDLE)
        assertEquals(0,idle.vertexCount)
        assertNull(idle.latestPose)
        accumulator.add(pose(1f))
        assertEquals(0,accumulator.add(pose(2f),task="").vertexCount)
        val stopped = DeviceStatusUiState(taskState=DeviceTaskUiState.RUNNING,trajectory=next).afterTaskStopped()
        assertSame(TaskTrajectorySnapshot.Empty,stopped.trajectory)
    }

    @Test fun pauseKeepsGeometryAndReadyOnlyUpdatesRobotMarker() {
        val accumulator = TaskTrajectoryAccumulator()
        val ready = accumulator.add(pose(0f),state=DeviceTaskUiState.READY)
        assertEquals(0,ready.vertexCount)
        val running = accumulator.add(pose(0f))
        assertEquals(1,running.vertexCount)
        val paused = accumulator.add(pose(1f),state=DeviceTaskUiState.PAUSED)
        assertSame(running.session,paused.session)
        assertEquals(2,paused.vertexCount)
    }

    @Test fun duplicatePoseReusesImmutableSnapshotAndMarkerStaysExact() {
        val accumulator = TaskTrajectoryAccumulator()
        val first = accumulator.add(pose(1f))
        assertSame(first,accumulator.add(pose(1f)))
        val current = pose(1.001f,.001f,heading=37f)
        val scene = accumulator.add(current)
        assertEquals(current,scene.latestPose)
    }

    @Test fun discCenterUsesHeadingRatherThanRobotPosition() {
        val accumulator = TaskTrajectoryAccumulator()
        var scene = TaskTrajectorySnapshot.Empty
        listOf(0f,90f,180f).forEach { scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,pose(0f,heading=it),1f,0f) }
        assertArrayEquals(floatArrayOf(1f,0f,0f,1f,-1f,0f),scene.tail!!.xy,.0001f)
        assertEquals(pose(0f,heading=180f),scene.latestPose)
    }

    @Test fun lateSettingsResolveAllPendingSamplesAndPreservePendingGaps() {
        val accumulator = TaskTrajectoryAccumulator()
        fun pending(p: TaskPoseUiItem?) = accumulator.accept("task",DeviceTaskUiState.RUNNING,p,null,null)
        pending(pose(0f)); pending(pose(1f)); pending(null); pending(pose(10f)); pending(pose(11f))
        val scene = accumulator.configure(1f,0f)
        assertEquals(2,runs(scene).size)
        assertArrayEquals(floatArrayOf(1f,0f,2f,0f),runs(scene).first().xy,0f)
        assertArrayEquals(floatArrayOf(11f,0f,12f,0f),scene.tail!!.xy,0f)
        assertEquals(4L,accumulator.receivedCount)
    }

    private fun assertWithinOrderedSegments(samples: List<TaskPoseUiItem>, vertices: FloatArray) {
        var segment=0
        samples.forEach { p ->
            while(segment+2 < vertices.size/2 && p.x > vertices[(segment+1)*2]) segment++
            val ax=vertices[segment*2].toDouble();val ay=vertices[segment*2+1].toDouble()
            val bx=vertices[(segment+1)*2].toDouble();val by=vertices[(segment+1)*2+1].toDouble()
            val dx=bx-ax;val dy=by-ay
            val t=(((p.x-ax)*dx+(p.y-ay)*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
            assertTrue("sample ${p.x},${p.y}",hypot(p.x-ax-t*dx,p.y-ay-t*dy) <= .01001)
        }
    }
}
