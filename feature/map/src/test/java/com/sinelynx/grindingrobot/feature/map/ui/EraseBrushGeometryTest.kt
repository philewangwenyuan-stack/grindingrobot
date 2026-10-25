package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class EraseBrushGeometryTest {
    private val bounds = Rect(0f, 0f, 100f, 100f)
    @Test fun tapCreatesRoundFootprint() {
        val points = eraseBrushSegment(Offset(50f, 50f), Offset(50f, 50f), 10f, bounds)
        assertTrue(points.size >= 10)
        points.forEach { assertEquals(10f, (it - Offset(50f, 50f)).getDistance(), 0.001f) }
    }
    @Test fun brushIsClippedAtEveryMapEdge() {
        val polygon = eraseBrushSegment(Offset(-30f, -30f), Offset(130f, 130f), 20f, bounds)
        assertTrue(polygon.size >= 3)
        polygon.forEach { assertTrue(it.x in -0.001f..100.001f && it.y in -0.001f..100.001f) }
        assertTrue(eraseBrushSegment(Offset(-90f, -90f), Offset(-80f, -80f), 10f, bounds).isEmpty())
    }
    @Test fun straightStrokeCompilesToOneSegmentWithoutTruncation() {
        val points = (0..10000).map { Offset(it / 100f, 40f) }
        assertEquals(listOf(points.first(), points.last()), simplifyEraseStroke(points, 0.5f))
    }
    @Test fun simplificationRetainsReversalAndCorner() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(0f, 0f), Offset(0f, 90f))
        assertEquals(points, simplifyEraseStroke(points, 0.2f))
    }
    @Test fun everyInputPointRemainsWithinTolerance() {
        val points = (0..10000).map { Offset(it / 100f, (kotlin.math.sin(it / 40.0) * 20).toFloat()) }
        val tolerance = 0.25f
        val simplified = simplifyEraseStroke(points, tolerance)
        points.forEach { point ->
            val distance = simplified.zipWithNext().minOf { (a, b) ->
                val ab = b - a; val ap = point - a
                val length2 = ab.x * ab.x + ab.y * ab.y
                val t = if (length2 > 0f) ((ap.x * ab.x + ap.y * ab.y) / length2).coerceIn(0f, 1f) else 0f
                (point - (a + ab * t)).getDistance()
            }
            assertTrue("$distance > $tolerance", distance <= tolerance + 0.001f)
        }
    }
    @Test fun rectangleDrawsInEveryDirectionAndStopsAtBounds() {
        assertEquals(Rect(0f, 10f, 40f, 100f), drawEraseRect(Offset(40f, 10f), Offset(-50f, 200f), bounds))
        assertEquals(Rect(10f, 20f, 80f, 90f), drawEraseRect(Offset(80f, 90f), Offset(10f, 20f), bounds))
    }
}
