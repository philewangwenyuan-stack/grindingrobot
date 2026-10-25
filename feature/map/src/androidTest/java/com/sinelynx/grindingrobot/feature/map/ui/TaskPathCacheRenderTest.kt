package com.sinelynx.grindingrobot.feature.map.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathSegmentPayload
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Compare actual cached native paths with the previous per-edge scalar renderer under transforms. */
class TaskPathCacheRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val geo = MapGeo(200, 100, 1f, 0.0, 0.0, 0f, 1)
    private val path = TaskPathPayload("task", 1, "map", 0f, listOf(
        TaskPathPointPayload(20f, 20f, index = 0), TaskPathPointPayload(50f, 20f, index = 1),
        TaskPathPointPayload(50f, 40f, index = 2), TaskPathPointPayload(Float.NaN, 0f, index = 3),
        TaskPathPointPayload(80f, 80f, index = 4), TaskPathPointPayload(220f, 80f, index = 5),
        TaskPathPointPayload(120f, 20f, index = 6), TaskPathPointPayload(160f, 40f, index = 7),
        TaskPathPointPayload(180f, 40f, index = 8)
    ), listOf(
        TaskPathSegmentPayload(0, "between_regions", "", "", 0, "", "", 0, 5, 6),
        TaskPathSegmentPayload(1, "within_region", "", "", 0, "", "", 6, 8, 3)
    ))

    @Test fun cachedPolylinesMatchOriginalFitRotationZoomAndBreaks() {
        var rotation by mutableStateOf(0f)
        var zoom by mutableStateOf(1f)
        var pan by mutableStateOf(Offset.Zero)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Row {
                    Box(Modifier.size(240.dp, 180.dp).clipToBounds().background(Color.White).testTag("cached")) {
                        FittedTaskPathOverlay(path, geo, 200 to 100, 200 to 100,
                            rotation, zoom, pan, colorByScope = true)
                    }
                    Box(Modifier.size(240.dp, 180.dp).clipToBounds().background(Color.White).testTag("reference")) {
                        Canvas(Modifier.fillMaxSize()) { drawReference(rotation, zoom, pan) }
                    }
                }
            }
        }
        listOf(0f to 1f, 37f to 1f, -90f to 1f, 180f to 1f, 37f to 2.3f).forEach { (angle, scale) ->
            compose.runOnIdle { rotation = angle; zoom = scale; pan = if (scale > 1f) Offset(-90f, -60f) else Offset.Zero }
            compose.waitForIdle()
            val actual = compose.onNodeWithTag("cached").captureToImage().toPixelMap()
            val expected = compose.onNodeWithTag("reference").captureToImage().toPixelMap()
            var different = 0
            var colored = 0
            for (y in 0 until expected.height) for (x in 0 until expected.width) {
                val a = actual[x, y]
                val b = expected[x, y]
                if (b.red < 0.9f || b.green < 0.9f || b.blue < 0.9f) colored++
                if (abs(a.red - b.red) > 0.25f || abs(a.green - b.green) > 0.25f || abs(a.blue - b.blue) > 0.25f) different++
            }
            assertTrue("test must include visible geometry at $angle/$scale", colored > 30)
            // Native stroke joins differ slightly from overlapping round per-edge caps at AA edges.
            assertTrue("transform $angle/$scale has $different divergent pixels", different < 80)
        }
    }

    private fun DrawScope.drawReference(rotation: Float, zoom: Float, pan: Offset) {
        taskPathRenderSegments(path).forEach { segment ->
            var previous: Offset? = null
            var count = 0
            fun dot() { if (count == 1) drawCircle(segment.color, 1.5f * zoom, previous!!) }
            segment.points.forEach { point ->
                val projected = taskPathPointToBitmapPoint(point, geo, 200 to 100, 200 to 100)
                    ?.let { taskBitmapPointToFittedPoint(it, size, 200 to 100, rotation) }
                    ?.let { it * zoom + pan }
                if (projected == null) { dot(); previous = null; count = 0 }
                else {
                    previous?.let { drawLine(segment.color, it, projected, 1.5f * zoom, StrokeCap.Round) }
                    previous = projected
                    count++
                }
            }
            dot()
        }
    }
}
