package com.sinelynx.grindingrobot.feature.main.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sinelynx.grindingrobot.feature.main.viewmodel.TrajectoryPoint
import com.sinelynx.grindingrobot.feature.map.ui.taskBitmapPointToFittedPoint
import com.sinelynx.grindingrobot.feature.map.ui.worldPointToBitmapOffset
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskReplayGeometryTest {
    private val geo = MapGeo(
        mapWidth = 200,
        mapHeight = 100,
        resolution = 0.1f,
        originX = 10.0,
        originY = 20.0,
        headingDeg = 90f,
        mapVersion = 1,
        alignmentYawDeg = 60f,
        rotationAlignmentDeltaDeg = 30f
    )

    @Test
    fun visibleSegmentsFollowCurrentReplayPoint() {
        assertEquals(0, visibleTrajectorySegmentCount(pointCount = 5, currentIndex = 0))
        assertEquals(2, visibleTrajectorySegmentCount(pointCount = 5, currentIndex = 2))
        assertEquals(4, visibleTrajectorySegmentCount(pointCount = 5, currentIndex = 4))
    }

    @Test
    fun visibleSegmentsClampEmptySinglePointAndOutOfBoundsProgress() {
        assertEquals(0, visibleTrajectorySegmentCount(pointCount = 0, currentIndex = 3))
        assertEquals(0, visibleTrajectorySegmentCount(pointCount = 1, currentIndex = 3))
        assertEquals(0, visibleTrajectorySegmentCount(pointCount = 5, currentIndex = -1))
        assertEquals(4, visibleTrajectorySegmentCount(pointCount = 5, currentIndex = 99))
    }

    @Test
    fun replayPointUsesTheSameBitmapProjectionAsStep2AndStep3() {
        val point = trajectoryPoint(x = 10f, y = 25f)

        val replayOffset = trajectoryPointToBitmapOffset(
            point = point,
            geo = geo,
            mapImageSize = 200 to 100,
            bitmapSize = 400 to 200
        )
        val sharedOffset = worldPointToBitmapOffset(
            point = MapPreviewPointItem(point.x, point.y),
            geo = geo,
            mapImageSize = 200 to 100,
            bitmapSize = 400 to 200
        )

        assertEquals(sharedOffset, replayOffset)
        assertOffsetEquals(Offset(100f, 200f), replayOffset)
    }

    @Test
    fun fittedPointAppliesStep3TotalDisplayRotation() {
        val fitted = taskBitmapPointToFittedPoint(
            point = Offset(100f, 200f),
            canvasSize = Size(800f, 800f),
            bitmapSize = 400 to 200,
            rotationDeg = geo.alignmentYawDeg + geo.rotationAlignmentDeltaDeg
        )

        assertOffsetEquals(Offset(200f, 200f), fitted)
    }

    @Test
    fun vehicleWidthIsMappedThroughResolutionBitmapRatioAndFitScale() {
        val width = trajectoryMetersToScreenPx(
            meters = 1.0,
            geo = geo,
            mapImageSize = 200 to 100,
            bitmapSize = 400 to 200,
            canvasSize = Size(800f, 800f)
        )

        assertEquals(40f, width ?: Float.NaN, 0.001f)
    }

    @Test
    fun invalidWidthAndOutOfBoundsPointAreNotRendered() {
        assertNull(
            trajectoryMetersToScreenPx(
                meters = null,
                geo = geo,
                mapImageSize = 200 to 100,
                bitmapSize = 400 to 200,
                canvasSize = Size(800f, 800f)
            )
        )
        assertNull(
            trajectoryPointToBitmapOffset(
                point = trajectoryPoint(x = 1000f, y = 1000f),
                geo = geo,
                mapImageSize = 200 to 100,
                bitmapSize = 400 to 200
            )
        )
    }

    private fun trajectoryPoint(x: Float, y: Float) = TrajectoryPoint(
        index = 0,
        offsetMs = 0,
        x = x,
        y = y,
        headingDeg = 0f,
        speed = 0f,
        angularSpeedRadps = 0f,
        discSpeedRpm = 0,
        speedAvailable = true,
        discEnabled = false,
        taskStateValue = 0
    )

    private fun assertOffsetEquals(expected: Offset, actual: Offset?) {
        requireNotNull(actual)
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
