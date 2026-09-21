package com.sinelynx.grindingrobot.feature.common.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraRtspOverlayPositionTest {

    @Test
    fun initialOffset_usesTopAndEndPadding() {
        val offset = calculateInitialCameraOverlayOffset(
            containerSize = IntSize(1280, 800),
            overlaySize = IntSize(326, 233),
            topPaddingPx = 16f,
            endPaddingPx = 70f
        )

        assertEquals(884f, offset.x, 0f)
        assertEquals(16f, offset.y, 0f)
    }

    @Test
    fun clampOffset_keepsWindowInsideEveryEdge() {
        val container = IntSize(1000, 700)
        val overlay = IntSize(326, 233)

        assertEquals(
            Offset.Zero,
            clampCameraOverlayOffset(Offset(-20f, -30f), container, overlay)
        )
        assertEquals(
            Offset(674f, 467f),
            clampCameraOverlayOffset(Offset(900f, 600f), container, overlay)
        )
    }

    @Test
    fun clampOffset_preservesPositionAlreadyInsideBounds() {
        val requested = Offset(240f, 180f)

        assertEquals(
            requested,
            clampCameraOverlayOffset(
                requested = requested,
                containerSize = IntSize(1000, 700),
                overlaySize = IntSize(326, 233)
            )
        )
    }

    @Test
    fun clampOffset_handlesContainerSmallerThanWindow() {
        assertEquals(
            Offset.Zero,
            clampCameraOverlayOffset(
                requested = Offset(100f, 100f),
                containerSize = IntSize(200, 150),
                overlaySize = IntSize(326, 233)
            )
        )
    }
}
