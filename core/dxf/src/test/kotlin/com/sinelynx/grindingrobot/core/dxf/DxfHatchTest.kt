package com.sinelynx.grindingrobot.core.dxf

import org.junit.Assert.*
import org.junit.Test

class DxfHatchTest {
    private fun parse(boundary:String)=DxfParser.parse(("0\nSECTION\n2\nENTITIES\n0\nHATCH\n5\nH\n91\n1\n"+boundary+"0\nENDSEC\n0\nEOF\n").byteInputStream())
    private fun line(a:PointD,b:PointD)="72\n1\n10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n"
    @Test fun lineEdgeLoopRetainsClosedFoundationAndCorners() {
        val v=listOf(PointD(0.0,0.0),PointD(2000.0,0.0),PointD(2000.0,1000.0),PointD(0.0,1000.0))
        val e=parse("92\n1\n93\n4\n"+v.indices.joinToString(""){line(v[it],v[(it+1)%4])}+"97\n0\n").entities.single()
        assertTrue(e.closed);assertEquals(4,e.pointsMm.size);assertEquals(2000000.0,PolygonGeometry.area(e.pointsMm),.001)
        assertEquals(4,e.cornersMm.distinct().size)
    }
    @Test fun clockwiseArcUsesComplementAnglesAndJoinsStraightEdge() {
        val e=parse("92\n1\n93\n2\n72\n2\n10\n0\n20\n0\n40\n1000\n50\n0\n51\n180\n73\n0\n"+line(PointD(-1000.0,0.0),PointD(1000.0,0.0))+"97\n0\n").entities.single()
        assertTrue(e.pointsMm.any {it.y < -990});assertTrue(e.pointsMm.all {it.y<1e-6})
        assertEquals(5.0,e.approximationMm,0.0)
        assertEquals(Math.PI*1000000/2,PolygonGeometry.area(e.pointsMm),15000.0)
    }
    @Test fun polylineBulgeIsTessellatedRatherThanDropped() {
        val e=parse("92\n2\n72\n1\n73\n1\n93\n3\n10\n0\n20\n0\n42\n1\n10\n2000\n20\n0\n42\n0\n10\n1000\n20\n1000\n42\n0\n97\n0\n").entities.single()
        assertTrue(e.pointsMm.size>10);assertEquals(3,e.cornersMm.size);assertEquals(5.0,e.approximationMm,0.0)
    }
    @Test fun gapsAreReportedAndNeverClosedWithInventedEdges() {
        val tail="0\nLINE\n10\n0\n20\n0\n11\n1\n21\n1\n"
        val d=parse("92\n1\n93\n2\n"+line(PointD(0.0,0.0),PointD(1.0,0.0))+line(PointD(4.0,0.0),PointD(5.0,0.0))+"97\n0\n"+tail)
        assertEquals(1,d.entities.size);assertTrue(d.warnings.any {it.contains("不连续")})
    }
}
