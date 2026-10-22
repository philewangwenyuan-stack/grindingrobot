package com.sinelynx.grindingrobot.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.sinelynx.grindingrobot.feature.map.ui.MapViewportTransform
import org.junit.Assert.*
import org.junit.Test

class MappingViewportTransformTest {
    private val canvas = Size(1000f, 500f)
    private val bounds = Rect(0f, 0f, 1000f, 500f)

    @Test fun zoomUsesExistingPreviewLimits() {
        val enlarged = MapViewportTransform().zoomAndPan(Offset(500f, 250f), Offset.Zero, 100f, canvas, bounds)
        assertEquals(8f, enlarged.scale, 0f)
        val reduced = enlarged.zoomAndPan(Offset(500f, 250f), Offset.Zero, 0.001f, canvas, bounds)
        assertEquals(1f, reduced.scale, 0f)
    }

    @Test fun zoomRemainsAnchoredToTheFingerCentroid() {
        val centroid = Offset(620f, 190f)
        val original = MapViewportTransform()
        val next = original.zoomAndPan(centroid, Offset.Zero, 2f, canvas, bounds)
        val anchorBefore = original.screenToModel(centroid)
        val anchorAfter = next.screenToModel(centroid)
        assertEquals(anchorBefore.x, anchorAfter.x, 0.001f)
        assertEquals(anchorBefore.y, anchorAfter.y, 0.001f)
    }

    @Test fun mapAndMarkerUseTheSameScreenTransform() {
        val viewport = MapViewportTransform().zoomAndPan(Offset(500f, 250f), Offset(30f, -20f), 2f, canvas, bounds)
        val markerBasePoint = Offset(600f, 280f)
        val screen = viewport.modelToScreen(markerBasePoint)
        assertEquals(markerBasePoint.x * viewport.scale + viewport.offset.x, screen.x, 0f)
        assertEquals(markerBasePoint.y * viewport.scale + viewport.offset.y, screen.y, 0f)
        assertEquals(markerBasePoint, viewport.screenToModel(screen))
    }

    @Test fun panningRetainsVisibleMapAndResetRestoresFit() {
        val viewport = MapViewportTransform().zoomAndPan(Offset(500f, 250f), Offset(100_000f, -100_000f), 2f, canvas, bounds)
        assertTrue(bounds.left * viewport.scale + viewport.offset.x <= canvas.width - 64f)
        assertTrue(bounds.bottom * viewport.scale + viewport.offset.y >= 64f)
        val reset = MapViewportTransform()
        assertEquals(1f, reset.scale, 0f)
        assertEquals(Offset.Zero, reset.offset)
    }
}
