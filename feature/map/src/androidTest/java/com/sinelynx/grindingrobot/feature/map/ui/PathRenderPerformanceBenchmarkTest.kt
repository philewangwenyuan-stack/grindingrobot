package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Software offscreen Canvas: this measures CPU/raster work, not device GPU frame time or UI FPS. */
class PathRenderPerformanceBenchmarkTest {
    @Test fun exportColdAndCachedSoftwareRendering() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("robotPathBenchmark") == "true")
        val destination = File(instrumentation.context.getExternalFilesDir(null), "path-performance").apply { mkdirs() }
        val geo = MapGeo(1000, 1000, 0.1f, 0.0, 0.0, 0f, 1)
        val dimensions = 1000 to 1000
        val bitmap = ImageBitmap(1200, 800)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        File(destination, "android-render-raw.csv").bufferedWriter().use { csv ->
            csv.appendLine("environment,stage,points,segments,iteration,variant,batch_ops,duration_ns")
            for (size in listOf(1000, 10000, 50000, 100000)) {
                val source = performancePath(size)
                val warmCache = TaskPathDrawingCache()
                assertEquals(source.segments.size, warmCache.get(source, geo, dimensions, dimensions, true).size)
                for (stage in listOf("render_cold", "render_repeat")) {
                    fun execute(variant: String, sample: Int) {
                        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(1200f, 800f)) {
                            drawRect(Color.White)
                            // Deterministic changing rotation/pan exercises the actual matrix path.
                            val rotation = 30f + sample % 15
                            val zoom = 1f + (sample % 3) * 0.1f
                            val pan = Offset(-(sample % 4) * 3f, -(sample % 5) * 2f)
                            if (variant == "old") drawLegacyFittedTaskPath(source, geo,
                                dimensions, dimensions, rotation, zoom, pan, true)
                            else {
                                val cache = if (stage == "render_cold") TaskPathDrawingCache() else warmCache
                                val drawing = cache.get(source, geo, dimensions, dimensions, true)
                                drawCachedTaskPath(drawing, dimensions, rotation, zoom, pan)
                            }
                        }
                    }
                    repeat(5) { execute("old", it); execute("new", it) }
                    repeat(21) { sample ->
                        for (variant in if (sample % 2 == 0) listOf("old", "new") else listOf("new", "old")) {
                            val start = System.nanoTime()
                            execute(variant, sample)
                            val elapsed = System.nanoTime() - start
                            csv.appendLine("Android12_x86_64_software,$stage,$size,${source.segments.size},$sample,$variant,1,$elapsed")
                        }
                    }
                    csv.flush()
                }
            }
        }
    }
}
