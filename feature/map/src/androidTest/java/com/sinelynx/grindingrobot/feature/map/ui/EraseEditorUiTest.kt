package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.feature.map.viewmodel.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EraseEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var confirmed = emptyList<Offset>()
    private var created = emptyList<Offset>()
    private var taps = 0
    private var strokes = emptyList<List<Offset>>()
    private val initial = listOf(Offset(100f, 100f), Offset(200f, 100f), Offset(200f, 200f), Offset(100f, 200f))
    @Before fun begin() { MapBuildSessionStream.begin("erase-test") }

    private fun panel(mode: EditToolMode, active: Boolean = true) {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }.asImageBitmap()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(400.dp).testTag("panel")) {
                    MapEditingPanel(mapId = "erase-test", mapImageBitmap = bitmap, toolMode = mode,
                        alignmentYaw = 0f, mapGeo = null, mapImageSize = 400 to 400, bitmapDecodeSize = 400 to 400,
                        robotPose = null, robotWidth = null, robotLength = null, robotFootprint = emptyList(),
                        eraseAreas = if (active) listOf(EraseRectArea(1, 100f, 100f, 200f, 200f, false, initial)) else emptyList(),
                        previewWorkRegions = emptyList(), previewObstacleRegions = emptyList(), previewEraseRegions = emptyList(),
                        activeAreaId = if (active) 1 else null, showDebugTap = false, onDebugTapBitmap = {},
                        onCreateArea = { taps++ }, onCreateRectangle = { created = it }, onBrushStroke = { strokes = it },
                        onConfirmArea = { confirmed = it }, onRemoveArea = {})
                }
            }
        }
    }

    @Test fun movingThenTappingOutsideKeepsRectangle() {
        panel(EditToolMode.Erase)
        compose.onNodeWithTag("panel").performTouchInput { swipe(Offset(150f, 150f), Offset(190f, 180f), 250) }
        compose.onNodeWithTag("panel").performTouchInput { click(Offset(340f, 330f)) }
        val preview = compose.onNodeWithTag("panel").captureToImage().asAndroidBitmap()
        val directory = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        java.io.File(directory, "erase-refinement.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("✓").performClick()
        assertEquals(0, taps)
        assertEquals(140f, confirmed[0].x, 1f)
        assertEquals(130f, confirmed[0].y, 1f)
    }

    @Test fun twoFingersZoomInEraseModeAndRemainingFingerDoesNotMoveRectangle() {
        panel(EditToolMode.Erase)
        compose.onNodeWithTag("panel").performTouchInput {
            down(0, Offset(140f, 150f)); down(1, Offset(230f, 230f))
            moveTo(0, Offset(80f, 90f)); moveTo(1, Offset(290f, 290f))
            up(1); moveTo(0, Offset(50f, 50f)); up(0)
        }
        compose.onNodeWithText("✓").performClick()
        assertTrue(MapBuildSessionStream.viewportFor("erase-test").zoom > 1.1f)
        initial.zip(confirmed).forEach { (a, b) -> assertEquals(a.x, b.x, 0.01f); assertEquals(a.y, b.y, 0.01f) }
    }

    @Test fun brushStrokeCommitsGeometryAndPinchDoesNotErase() {
        panel(EditToolMode.Brush, active = false)
        compose.onNodeWithTag("panel").performTouchInput { swipe(Offset(70f, 180f), Offset(300f, 180f), 300) }
        assertEquals("straight brush should be one protocol polygon", 1, strokes.size)
        assertTrue(strokes[0].size >= 10)
        strokes = emptyList()
        compose.onNodeWithTag("panel").performTouchInput {
            down(0, Offset(140f, 150f)); down(1, Offset(230f, 230f))
            moveTo(0, Offset(80f, 90f)); moveTo(1, Offset(290f, 290f)); up(1); up(0)
        }
        assertTrue(strokes.isEmpty())
    }

    @Test fun rectangleCanBeDrawnByDraggingFromBlankSpace() {
        panel(EditToolMode.Erase, active = false)
        compose.onNodeWithTag("panel").performTouchInput { swipe(Offset(80f, 100f), Offset(290f, 280f), 300) }
        assertEquals(4, created.size)
        assertEquals(Offset(80f, 100f), created[0])
        assertEquals(Offset(290f, 280f), created[2])
    }

    @Test fun cachedRegionProjectionIsReusedAndInvalidatesForNewGeometry() {
        val cache = MapRegionPathCache()
        val regions = (0..999).map { i -> MapPreviewRegionItem("$i", "", initial.map { MapPreviewPointItem(it.x, it.y) }) }
        var projected = 0
        val mapper: (MapPreviewPointItem) -> Offset? = { projected++; Offset(it.x, it.y) }
        val first = cache.paths(MapRegionOverlayKind.Erase, regions, "projection1", mapper)
        assertEquals("erase fill is batched into one native draw", 1, first.size)
        repeat(120) { assertSame(first, cache.paths(MapRegionOverlayKind.Erase, regions, "projection1", mapper)) }
        assertEquals(4000, projected)
        cache.paths(MapRegionOverlayKind.Erase, regions, "projection2", mapper)
        assertEquals(8000, projected)
        cache.paths(MapRegionOverlayKind.Erase, regions + regions.first(), "projection2", mapper)
        assertEquals(12004, projected)
    }

    @Test fun oppositeWindingOverlapsStayFilledInBatchedErasePath() {
        val cache = MapRegionPathCache()
        val first = listOf(Offset(10f, 10f), Offset(60f, 10f), Offset(60f, 60f), Offset(10f, 60f))
        val second = first.map { it + Offset(20f, 20f) }.asReversed()
        val regions = listOf(first, second).mapIndexed { i, points ->
            MapPreviewRegionItem("$i", "", points.map { MapPreviewPointItem(it.x, it.y) })
        }
        val path = cache.paths(MapRegionOverlayKind.Erase, regions, "map") { Offset(it.x, it.y) }.single().second
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        android.graphics.Canvas(bitmap).drawPath(path.asAndroidPath(), android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(20, 20))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(40, 40))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(70, 70))
        assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(90, 90))
    }

    @Test fun brushSegmentsMergeIntoOneRegion() {
        val polygons = (0..59).map { i -> eraseBrushSegment(Offset(20f + i * 2, 50f),
            Offset(22f + i * 2, 50f), 8f, androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 200f)) }
        val merged = mergeErasePolygons(polygons)
        assertEquals(1, merged.size)
        assertTrue(merged.single().size <= 256)
        assertMergedPixels(polygons, merged)
    }

    @Test fun closedBrushLoopKeepsItsHoleAfterUnion() {
        val corners = listOf(Offset(30f, 30f), Offset(130f, 30f), Offset(130f, 130f), Offset(30f, 130f), Offset(30f, 30f))
        val polygons = corners.zipWithNext().flatMap { (a, b) -> (0..9).map { i ->
            eraseBrushSegment(a + (b-a) * (i / 10f), a + (b-a) * ((i+1) / 10f), 8f,
                androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 200f))
        } }
        val merged = mergeErasePolygons(polygons)
        assertTrue("closed loop should require fewer requests", merged.size < polygons.size)
        assertMergedPixels(polygons, merged)
    }

    private fun assertMergedPixels(original: List<List<Offset>>, merged: List<List<Offset>>) {
        fun render(polygons: List<List<Offset>>): Bitmap {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
            polygons.forEach { points ->
                val path = android.graphics.Path().apply {
                    moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close()
                }
                canvas.drawPath(path, paint)
            }
            return bitmap
        }
        val expected = render(original); val actual = render(merged)
        // Individual overlapping draws and a union rasterize shared AA boundaries differently.
        // Every interior/exterior pixel farther than one pixel from the original edge must match.
        for (y in 1..198) for (x in 1..198) if (expected.getPixel(x,y) != actual.getPixel(x,y)) {
            val edge = (-1..1).any { dy -> (-1..1).any { dx -> expected.getPixel(x+dx,y+dy) != expected.getPixel(x,y) } }
            assertTrue("union changed non-boundary pixel $x,$y", edge)
        }
        assertEquals(expected.getPixel(80,80), actual.getPixel(80,80))
    }
}
