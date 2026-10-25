package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.feature.map.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class EraseSubmissionGeometryTest {
    private val geo = MapGeo(200, 100, 0.1f, 10.0, 20.0, 0f, 1)
    @Test fun rectangleKeepsProtocolCornerOrderWithDifferentBitmapSize() {
        val output = compileEraseOutput(listOf(EraseRectArea(1, 10f, 10f, 30f, 20f, true)),
            100 to 50, 200 to 100, geo, 0f).confirmedAreas.single()
        assertEquals(listOf(Offset(12f, 28f), Offset(16f, 28f), Offset(16f, 26f), Offset(12f, 26f)), output.polygonM)
        assertEquals(output.polygonM[0], output.leftTopM)
        assertEquals(output.polygonM[3], output.leftBottomM)
    }
    @Test fun alignedRectangleUndoesDisplayRotationExactlyOnce() {
        val raw = listOf(Offset(10f, 10f), Offset(30f, 10f), Offset(30f, 20f), Offset(10f, 20f))
        val aligned = raw.map { rotatePoint(it, Offset(50f, 25f), 90f) }
        val area = EraseRectArea(1, 0f, 0f, 0f, 0f, true, aligned)
        val points = compileEraseOutput(listOf(area), 100 to 50, 200 to 100, geo, 90f).confirmedAreas.single().polygonM
        val expected = listOf(Offset(12f, 28f), Offset(16f, 28f), Offset(16f, 26f), Offset(12f, 26f))
        expected.zip(points).forEach { (a, b) -> assertEquals(a.x, b.x, 0.0001f); assertEquals(a.y, b.y, 0.0001f) }
    }
    @Test fun brushTriangleAndMultiplePartsArePreserved() {
        val triangle = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(0f, 10f))
        val area = EraseRectArea(9, 0f, 0f, 0f, 0f, true, bitmapPolygons = listOf(triangle, triangle))
        val parts = compileEraseOutput(listOf(area), 200 to 100, 200 to 100, geo, 60f).confirmedAreas
        assertEquals(listOf(0, 1), parts.map { it.partIndex })
        assertEquals(3, parts[0].polygonM.size)
        assertEquals(Offset(10f, 30f), parts[0].polygonM[0]) // Brush is already in raw bitmap coordinates.
        assertEquals(Offset(11f, 30f), parts[0].polygonM[1])
    }
    @Test fun worldHeadingAndOriginApplyToBrush() {
        val triangle = listOf(Offset(20f, 50f), Offset(30f, 50f), Offset(20f, 60f))
        val area = EraseRectArea(9, 0f, 0f, 0f, 0f, true, bitmapPolygons = listOf(triangle))
        val point = compileEraseOutput(listOf(area), 200 to 100, 200 to 100, geo.copy(headingDeg = 90f), 0f)
            .confirmedAreas.single().polygonM[0]
        assertEquals(5f, point.x, 0.0001f)
        assertEquals(22f, point.y, 0.0001f)
    }
    @Test fun unconfirmedAreasAreNeverSubmitted() {
        assertTrue(compileEraseOutput(listOf(EraseRectArea(1, 0f, 0f, 10f, 10f, false)),
            200 to 100, 200 to 100, geo, 0f).confirmedAreas.isEmpty())
    }
}
