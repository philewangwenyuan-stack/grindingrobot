package com.sinelynx.grindingrobot.core.dxf

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class DxfCoreTest {
    private fun drawing(entities: String, units: Int=4, blocks: String="", codepage: String="UTF-8") =
        "0\nSECTION\n2\nHEADER\n9\n\$INSUNITS\n70\n$units\n9\n\$DWGCODEPAGE\n3\n$codepage\n0\nENDSEC\n"+
            "0\nSECTION\n2\nBLOCKS\n$blocks"+"0\nENDSEC\n0\nSECTION\n2\nENTITIES\n$entities"+"0\nENDSEC\n0\nEOF\n"
    private val rectangle="0\nLWPOLYLINE\n5\nABC\n8\n基础\n70\n1\n10\n0\n20\n0\n10\n2000\n20\n0\n10\n2000\n20\n1000\n10\n0\n20\n1000\n"
    private fun parse(s: String) = DxfParser.parse(s.byteInputStream())
    @Test fun closedRectangleRetainsCornersAndStableHandle() {
        val d=parse(drawing(rectangle)); val e=d.entities.single()
        assertEquals("ABC",e.id);assertEquals("基础",e.layer);assertTrue(e.closed)
        assertEquals(2000000.0,PolygonGeometry.area(e.pointsMm),1e-6)
        assertEquals(4,e.cornersMm.size)
    }
    @Test fun metresAreConvertedOnce() { val e=parse(drawing(rectangle,6)).entities.single();assertEquals(2000000.0,e.bounds.width,0.01) }
    @Test fun unknownUnitsRequireExplicitChoice() {
        val d=parse(drawing(rectangle,0));assertEquals(DxfUnits.UNKNOWN,d.units)
        assertEquals(2000000.0,d.withUnits(DxfUnits.METRES).entities.single().bounds.width,0.01)
    }
    @Test fun legacyGbkDoesNotCorruptChinese() {
        val d=DxfParser.parse(drawing(rectangle,codepage="ANSI_936").toByteArray(Charset.forName("GBK")).inputStream())
        assertEquals("基础",d.entities.single().layer);assertEquals("GBK",d.encoding)
    }
    @Test(expected=DxfParseException::class) fun rejectsBinary() {parse("AutoCAD Binary DXF\r\n\u001a\u0000")}
    @Test(expected=DxfParseException::class) fun rejectsNonDxf() {parse("not a dxf")}
    @Test fun paperSpaceIsExcluded() {
        val paper=rectangle.replace("5\nABC","67\n1\n5\nPAPER")
        assertEquals(1,parse(drawing(rectangle+paper)).entities.size)
    }
    @Test fun nestedBlockRotationScaleAndArray() {
        val b="0\nBLOCK\n2\nJC\n10\n0\n20\n0\n"+rectangle+"0\nENDBLK\n"
        val insert="0\nINSERT\n5\nI\n2\nJC\n10\n100\n20\n200\n41\n2\n42\n2\n50\n90\n70\n2\n44\n3000\n"
        val d=parse(drawing(insert,blocks=b));assertEquals(2,d.entities.size)
        assertEquals(2000.0,d.entities[0].bounds.width,0.001);assertEquals(4000.0,d.entities[0].bounds.height,0.001)
        assertEquals(3000.0,d.entities[1].bounds.bottom-d.entities[0].bounds.bottom,0.001)
        assertNotEquals(d.entities[0].id,d.entities[1].id)
    }
    @Test fun curvedVerticesAreNotSnapCorners() {
        val d=parse(drawing("0\nCIRCLE\n10\n0\n20\n0\n40\n2000\n"));assertTrue(d.entities.single().pointsMm.size>20)
        assertNull(DrawingIndex(d.entities).snap(PointD(2000.0,0.0),20.0,setOf("0")))
    }
    @Test fun hiddenLayersDoNotSnap() {
        val d=parse(drawing(rectangle));val index=DrawingIndex(d.entities)
        assertEquals(PointD(0.0,0.0),index.snap(PointD(5.0,6.0),16.0,setOf("基础")))
        assertNull(index.snap(PointD(5.0,6.0),16.0,emptySet()))
    }
    @Test fun snapsRealLineIntersectionButNotBoundingBoxCorner() {
        val a=DxfEntity("a","墙",null,false,listOf(PointD(-100.0,0.0),PointD(100.0,0.0)))
        val b=DxfEntity("b","墙",null,false,listOf(PointD(0.0,-100.0),PointD(0.0,100.0)))
        assertEquals(PointD(0.0,0.0),DrawingIndex(listOf(a,b)).snap(PointD(3.0,4.0),10.0,setOf("墙")))
        val diagonal=DxfEntity("c","墙",null,false,listOf(PointD(0.0,0.0),PointD(100.0,100.0)))
        assertNull(DrawingIndex(listOf(diagonal)).snap(PointD(0.0,100.0),10.0,setOf("墙")))
    }
    @Test fun explicitUnitsReparseCurvesAtPhysicalTolerance() {
        val source=drawing("0\nCIRCLE\n10\n0\n20\n0\n40\n10\n",0)
        val d=DxfParser.parse(source.byteInputStream(),unitsOverride=DxfUnits.METRES)
        assertEquals(10000.0,d.entities.single().bounds.width/2,10.0)
        assertEquals(5.0,d.entities.single().approximationMm,0.0)
        assertTrue(d.entities.single().pointsMm.size>50)
    }
    @Test fun registrationRoundTripAtSurveyCoordinates() {
        val expected=DrawingTransform(17.0,-5000.0,7000.0)
        val points=listOf(PointD(538990000.0,3360970000.0),PointD(539090000.0,3360970000.0),PointD(539090000.0,3361070000.0),PointD(538990000.0,3361070000.0))
        val fit=Registration.solve(points.map { ControlPair(it,expected.forward(it)) })
        assertFalse(fit.blocked);assertEquals(17.0,fit.thetaDeg,1e-6)
        points.forEach { assertTrue(fit.forward(it).distance(expected.forward(it))<1e-6);assertTrue(fit.inverse(fit.forward(it)).distance(it)<1e-5) }
    }
    @Test fun lockingScaleCannotHideIncorrectUnits() {
        val points=listOf(PointD(0.0,0.0),PointD(10000.0,0.0),PointD(0.0,10000.0))
        val fit=Registration.solve(points.map { ControlPair(it,it*0.01) },true)
        assertTrue(fit.blocked);assertEquals(1.0,fit.scale,0.0);assertEquals(10.0,fit.measuredScale,1e-8)
    }
    @Test(expected=IllegalArgumentException::class) fun duplicatePointsRejected() {Registration.solve(List(3){ControlPair(PointD(0.0,0.0),PointD(1.0,1.0))})}
    @Test(expected=DxfParseException::class) fun hugeArcCannotSilentlyExceedPhysicalTolerance() {
        parse(drawing("0\nCIRCLE\n10\n0\n20\n0\n40\n1000000000000\n"))
    }
    @Test fun closingEdgeIsSelectableFromOutside() {
        val d=parse(drawing(rectangle))
        assertEquals("ABC",DrawingIndex(d.entities).hit(PointD(-3.0,500.0),5.0,setOf("基础"))?.id)
    }
}
