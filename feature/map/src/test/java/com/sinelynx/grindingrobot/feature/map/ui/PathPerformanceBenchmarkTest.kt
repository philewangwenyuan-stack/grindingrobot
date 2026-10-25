package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in microbenchmark; no wall-time thresholds and no production code mutation. */
class PathPerformanceBenchmarkTest {
    private val geo = MapGeo(1000, 1000, 0.1f, 0.0, 0.0, 0f, 1)
    private val dimensions = 1000 to 1000
    @Volatile private var sink = 0.0

    @Test fun exportPairedBenchmarks() {
        assumeTrue(System.getenv("ROBOT_PATH_BENCHMARK") == "1")
        val directory = File(requireNotNull(System.getenv("ROBOT_PATH_BENCHMARK_OUTPUT"))).apply { mkdirs() }
        File(directory, "jvm-geometry-raw.csv").bufferedWriter().use { csv ->
            csv.appendLine("environment,stage,points,segments,iteration,variant,batch_ops,duration_ns")
            File(directory, "operation-counts.csv").bufferedWriter().use { counts ->
                counts.appendLine("points,segments,legacy_group_point_reads,new_group_point_reads,cache_hit_point_reads,legacy_edges,new_paths")
                for (size in listOf(1000, 10000, 50000, 100000)) {
                    val source = performancePath(size)
                    assertEquals(legacyTaskPathRenderSegments(source), taskPathRenderSegments(source))
                    var reads = 0L
                    val counted = source.copy(points = object : AbstractList<TaskPathPointPayload>() {
                        override val size = source.points.size
                        override fun get(index: Int): TaskPathPointPayload { reads++; return source.points[index] }
                    })
                    legacyTaskPathRenderSegments(counted)
                    val before = reads
                    taskPathRenderSegments(counted)
                    val after = reads
                    val cache = TaskPathProjectionCache()
                    cache.get(counted, geo, dimensions, dimensions, true)
                    val hitStart = reads
                    repeat(500) { cache.get(counted, geo, dimensions, dimensions, true) }
                    counts.appendLine("$size,${source.segments.size},$before,${after - before},${reads - hitStart},${size - source.segments.size},${source.segments.size}")

                    val warmCache = TaskPathProjectionCache().also { it.get(source, geo, dimensions, dimensions, true) }
                    for (stage in listOf("group_cold", "geometry_cold", "geometry_repeat")) {
                        fun execute(variant: String): Int {
                            val batch = if (variant == "new" && stage == "geometry_repeat") 2000 else 1
                            repeat(batch) {
                                when {
                                    stage == "group_cold" -> sink = (if (variant == "old")
                                        legacyTaskPathRenderSegments(source) else taskPathRenderSegments(source)).size.toDouble()
                                    variant == "old" -> legacyGeometry(source)
                                    else -> sink = (if (stage == "geometry_repeat") warmCache else TaskPathProjectionCache())
                                        .get(source, geo, dimensions, dimensions, true).size.toDouble()
                                }
                            }
                            return batch
                        }
                        repeat(5) { execute("old"); execute("new") }
                        repeat(21) { sample ->
                            val order = if (sample % 2 == 0) listOf("old", "new") else listOf("new", "old")
                            for (variant in order) {
                                val start = System.nanoTime()
                                val batch = execute(variant)
                                val elapsed = System.nanoTime() - start
                                csv.appendLine("JVM,$stage,$size,${source.segments.size},$sample,$variant,$batch,$elapsed")
                            }
                        }
                        csv.flush()
                    }
                }
            }
        }
    }

    private fun legacyGeometry(path: TaskPathPayload) {
        var checksum = 0.0
        legacyTaskPathRenderSegments(path).forEach { segment ->
            segment.points.forEach { point ->
                val pixel = taskPathPointToBitmapPoint(point, geo, dimensions, dimensions)
                    ?.let { taskBitmapPointToFittedPoint(it, Size(1200f, 800f), dimensions, 37f) }
                    ?.let { Offset(it.x * 1.5f - 80f, it.y * 1.5f - 40f) }
                if (pixel != null) checksum += pixel.x + pixel.y
            }
        }
        sink = checksum
    }
}
