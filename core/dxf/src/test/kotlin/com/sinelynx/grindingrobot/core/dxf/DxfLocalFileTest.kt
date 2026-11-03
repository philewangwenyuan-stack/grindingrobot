package com.sinelynx.grindingrobot.core.dxf

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional real-file reproduction; the private factory drawing is not checked into Git. */
class DxfLocalFileTest {
    @Test fun parseLocalDrawingWithoutDroppingSupportedHatchBoundaries() {
        val path=System.getenv("DXF_REAL_FILE").orEmpty();assumeTrue(path.isNotBlank())
        val d=File(path).inputStream().use {DxfParser.parse(it)}
        println("units=${d.units}, entities=${d.entities.size}, hatchLoops=${d.entities.count {it.id.contains("/h")}}, extent=${d.extentMm}, warnings=${d.warnings}")
        assertTrue(d.entities.size>10000)
        assertEquals(889,d.entities.count {it.id.contains("/h")})
        assertFalse(d.warnings.any {it.contains("HATCH")})
        val out=System.getenv("DXF_GEOMETRY_DUMP").orEmpty()
        if(out.isNotBlank())File(out).bufferedWriter().use {writer->d.entities.forEach {e->
            writer.append(e.id).append('\t').append(e.layer).append('\t').append(e.closed.toString()).append('\t')
                .append(e.pointsMm.joinToString(";"){"${it.x},${it.y}"}).append('\n')
        }}
    }
}
