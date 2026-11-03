package com.sinelynx.grindingrobot.core.dxf

import org.junit.Assert.*
import org.junit.Test

class DrawingPreviewProjectionTest {
    @Test fun fullCadExtentsFitWithoutUsingRadarMapDimensionsOrWorldOffset() {
        val b=RectD(-357285.0,134155.0,579468.0,432622.0)
        val projection=DrawingPreviewProjection(b);val scale=projection.fitScale(800.0,600.0)
        listOf(PointD(b.left,b.bottom),PointD(b.right,b.top),PointD(b.right,b.bottom),PointD(b.left,b.top)).forEach {
            val p=projection.project(it)
            val x=(p.x-projection.width/2)*scale+400;val y=(p.y-projection.height/2)*scale+300
            assertTrue(x in 0.0..800.0);assertTrue(y in 0.0..600.0)
        }
        assertEquals(PointD(0.0,0.0),projection.project(PointD(b.left,b.top)))
    }
    @Test fun zeroWidthDrawingStillHasFiniteScale() {
        val p=DrawingPreviewProjection(RectD(4.0,10.0,4.0,20.0))
        assertTrue(p.fitScale(800.0,600.0).isFinite())
    }
}
