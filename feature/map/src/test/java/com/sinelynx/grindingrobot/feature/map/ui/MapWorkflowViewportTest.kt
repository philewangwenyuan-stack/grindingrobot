package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.sinelynx.grindingrobot.core.model.state.MapBuildViewport
import org.junit.Assert.*
import org.junit.Test

class MapWorkflowViewportTest {
    @Test fun sameRawMapPointStaysAtCenterAcrossScreenSizesAndDisplayRotations() {
        val view = MapBuildViewport(3.2f, 0.62f, 0.41f)
        for (size in listOf(IntSize(960, 540), IntSize(1200, 720))) {
            for (angle in listOf(0f, 35f, -90f, 180f)) {
                val restored = view.toTransform(size, IntSize(1400, 450), angle)
                    .toMapCenter(size, IntSize(1400, 450), angle)
                assertEquals(view.zoom, restored.zoom, 0.0001f)
                assertEquals(view.centerX, restored.centerX, 0.0001f)
                assertEquals(view.centerY, restored.centerY, 0.0001f)
            }
        }
    }

    @Test fun handoffPreservesPanAndGestureAnchorAndClampsZoom() {
        val size = IntSize(960, 540)
        val image = IntSize(1400, 450)
        val viewport = WorkflowViewport(null, MapBuildViewport())
        val anchor = Offset(480f, 270f)
        viewport.gesture(size, image, 0f, anchor, Offset(40f, -20f), 2f)
        val first = viewport.view.toTransform(size, image, 0f)
        val handoff = WorkflowViewport(null, viewport.view).view.toTransform(size, image, 0f)
        assertEquals(first, handoff)
        assertEquals(Offset(-440f, -290f), first.offset)
        viewport.gesture(size, image, 0f, anchor, Offset.Zero, 100f)
        assertEquals(8f, viewport.view.zoom, 0f)
        viewport.gesture(size, image, 0f, anchor, Offset.Zero, 0.001f)
        assertEquals(1f, viewport.view.zoom, 0f)
        viewport.reset()
        assertEquals(MapBuildViewport(), viewport.view)
    }
}
