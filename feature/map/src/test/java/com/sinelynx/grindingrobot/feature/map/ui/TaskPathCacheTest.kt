package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.graphics.Color
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathSegmentPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureNanoTime

class TaskPathCacheTest {
    private val geo = MapGeo(1000, 500, 0.1f, 10.0, 20.0, 0f, 1)
    private fun path(points: List<TaskPathPointPayload>, segments: List<TaskPathSegmentPayload> = emptyList()) =
        TaskPathPayload("task", 1, "map", 0f, points, segments)
    private fun segment(start: Int, end: Int, scope: String = "within_region") =
        TaskPathSegmentPayload(0, scope, "", "", 0, "", "", start, end, 0)

    @Test fun unchangedSnapshotReusesProjectionWithoutReadingAnyPoint() {
        var reads = 0
        val points = object : AbstractList<TaskPathPointPayload>() {
            override val size = 1000
            override fun get(index: Int): TaskPathPointPayload {
                reads++
                return TaskPathPointPayload(10f + index * 0.01f, 25f, index = index)
            }
        }
        val source = path(points, listOf(segment(0, 999)))
        val cache = TaskPathProjectionCache()
        val first = cache.get(source, geo, 1000 to 500, 200 to 100, true)
        val before = reads
        repeat(500) { assertSame(first, cache.get(source, geo.copy(), 1000 to 500, 200 to 100, true)) }
        assertEquals(before, reads)
    }

    @Test fun sameVersionReplacementMetadataDimensionsAndColorPolicyInvalidate() {
        val source = path(listOf(TaskPathPointPayload(12f, 24f), TaskPathPointPayload(13f, 24f)))
        fun verifyChanged(nextSource: TaskPathPayload = source, nextGeo: MapGeo = geo,
                          nextMapSize: Pair<Int, Int>? = null, nextBitmap: Pair<Int, Int> = 200 to 100,
                          nextPolicy: Boolean = true) {
            val cache = TaskPathProjectionCache()
            val first = cache.get(source, geo, null, 200 to 100, true)
            assertNotSame(first, cache.get(nextSource, nextGeo, nextMapSize, nextBitmap, nextPolicy))
        }
        verifyChanged(nextSource = source.copy(points = listOf(TaskPathPointPayload(14f, 24f))))
        verifyChanged(nextGeo = geo.copy(originX = 11.0))
        verifyChanged(nextMapSize = 900 to 500)
        verifyChanged(nextBitmap = 400 to 200)
        verifyChanged(nextPolicy = false)
    }

    @Test fun displayAlignmentChangesDoNotReprojectBitmapGeometry() {
        val cache = TaskPathProjectionCache()
        val source = path(listOf(TaskPathPointPayload(12f, 24f)))
        val first = cache.get(source, geo, null, 200 to 100, true)
        assertSame(first, cache.get(source, geo.copy(alignmentYawDeg = 90f,
            rotationAlignmentDeltaDeg = -30f), null, 200 to 100, true))
    }

    @Test fun cachedProjectionMatchesScalarProjectionAndPreservesOutOfBoundsBreaks() {
        val points = listOf(TaskPathPointPayload(12f, 24f), TaskPathPointPayload(13f, 24f),
            TaskPathPointPayload(1000f, 24f), TaskPathPointPayload(14f, 24f),
            TaskPathPointPayload(Float.NaN, 24f), TaskPathPointPayload(15f, 24f))
        val cache = TaskPathProjectionCache()
        val runs = cache.get(path(points), geo, 1000 to 500, 200 to 100, false)
        assertEquals(listOf(2, 1, 1), runs.map { it.points.size })
        assertEquals(points.mapNotNull { taskPathPointToBitmapPoint(it, geo, 1000 to 500, 200 to 100) },
            runs.flatMap { it.points })
        listOf(-135f, 0f, 90f).forEach { heading ->
            val metadata = geo.copy(headingDeg = heading)
            assertEquals(points.mapNotNull { taskPathPointToBitmapPoint(it, metadata, null, 400 to 100) },
                cache.get(path(points), metadata, null, 400 to 100, false).flatMap { it.points })
        }
    }

    @Test fun indexLookupMatchesPreviousGroupingForSparseOverlappingAndUnsortedSummaries() {
        val random = Random(304)
        repeat(150) {
            val points = List(60) { position ->
                TaskPathPointPayload(if (random.nextInt(8) == 0) Float.NaN else position.toFloat(), 2f,
                    index = random.nextInt(-4, 90),
                    pathScope = listOf("", "within_region", "between_regions")[random.nextInt(3)])
            }
            val segments = List(15) {
                segment(random.nextInt(-5, 90), random.nextInt(-5, 100),
                    listOf("", "within_region", "between_regions")[random.nextInt(3)])
            }
            val source = path(points, segments)
            assertEquals(referenceSegments(source), taskPathRenderSegments(source))
        }
    }

    @Test fun largePathSummaryLookupDoesNotRescanWholePathForEverySegment() {
        var reads = 0
        val points = object : AbstractList<TaskPathPointPayload>() {
            override val size = 50_000
            override fun get(index: Int): TaskPathPointPayload {
                reads++
                return TaskPathPointPayload(index * 0.001f, 2f, index = index)
            }
        }
        val source = path(points, List(500) { segment(it * 100, it * 100 + 99) })
        val elapsed = measureNanoTime {
            val grouped = taskPathRenderSegments(source)
            assertEquals(500, grouped.size)
            assertEquals(50_000, grouped.sumOf { it.points.size })
        }
        assertTrue("point reads=$reads", reads < 1_500_000) // old loop >25 million reads.
        println("50k points/500 summaries: ${elapsed / 1_000_000.0}ms, $reads point reads")
    }

    // Old full-scan algorithm is an independent compatibility oracle, not the optimized lookup.
    private fun referenceSegments(path: TaskPathPayload): List<TaskPathRenderSegment> {
        val result = mutableListOf<TaskPathRenderSegment>()
        val claimed = BooleanArray(path.points.size)
        fun append(scope: String? = null, include: (Int, TaskPathPointPayload) -> Boolean) {
            var run = mutableListOf<TaskPathPointPayload>()
            var runScope: String? = null
            var color = Color(0xFF00FF00)
            fun flush() { if (run.isNotEmpty()) result.add(TaskPathRenderSegment(run.toList(), color)); run = mutableListOf() }
            path.points.forEachIndexed { position, point ->
                if (!include(position, point) || !point.x.isFinite() || !point.y.isFinite()) flush()
                else {
                    val nextScope = scope ?: point.pathScope
                    if (nextScope != runScope) flush()
                    runScope = nextScope
                    color = if (nextScope == "between_regions") Color(0xFF1976D2) else Color(0xFF00FF00)
                    run.add(point)
                }
            }
            flush()
        }
        path.segments.forEach { segment ->
            if (segment.startPointIndex < 0 || segment.endPointIndex < segment.startPointIndex) return@forEach
            fun matches(position: Int, point: TaskPathPointPayload) =
                (if (point.index >= 0) point.index else position) in segment.startPointIndex..segment.endPointIndex
            if (path.points.withIndex().none { matches(it.index, it.value) && it.value.x.isFinite() && it.value.y.isFinite() }) return@forEach
            append(segment.pathScope.takeIf { it == "within_region" || it == "between_regions" }) { position, point ->
                matches(position, point).also { if (it) claimed[position] = true }
            }
        }
        append { position, _ -> !claimed[position] }
        return result
    }
}
