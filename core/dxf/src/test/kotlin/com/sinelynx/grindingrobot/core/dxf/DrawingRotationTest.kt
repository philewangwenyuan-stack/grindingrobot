package com.sinelynx.grindingrobot.core.dxf

import org.junit.Assert.*
import org.junit.Test

class DrawingRotationTest {
    @Test fun fineRotationPreservesPickedAnchorAtLargeCadCoordinates() {
        val t=DrawingTransform(12.0,-5000.0,6000.0)
        val pivot=PointD(539000000.0,3361000000.0);val next=t.rotatedAround(pivot,.005)
        assertEquals(.005,next.thetaDeg-t.thetaDeg,1e-12)
        assertTrue(next.forward(pivot).distance(t.forward(pivot))<1e-8)
    }
    @Test fun repeatedFineAdjustmentAccumulatesWithoutRoundingToZero() {
        val pivot=PointD(200000.0,300000.0);var t=DrawingTransform()
        repeat(200){t=t.rotatedAround(pivot,.005)}
        assertEquals(1.0,t.thetaDeg,1e-12)
        assertTrue(t.forward(pivot).distance(pivot*.001)<1e-8)
    }
    @Test fun OppositeAdjustmentsRestorePointLocations() {
        val pivot=PointD(1000.0,2000.0);val t=DrawingTransform(28.0,10.0,30.0)
        val next=t.rotatedAround(pivot,.005).rotatedAround(pivot,-.005)
        assertTrue(next.forward(PointD(5000.0,8000.0)).distance(t.forward(PointD(5000.0,8000.0)))<1e-10)
    }
}
