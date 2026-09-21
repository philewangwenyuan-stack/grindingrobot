package com.sinelynx.grindingrobot.core.model.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserAndAssemblerTest {

    @Test
    fun `parse GGA and RMC numeric fields correctly`() {
        val gga = NmeaParser.parseGgaData(GGA_MAIN)
        val rmc = NmeaParser.parseRmcData(RMC_MAIN)

        assertEquals(32.0603557406667, gga.latitudeDegrees!!, 1e-12)
        assertEquals(118.797136625, gga.longitudeDegrees!!, 1e-12)
        assertEquals(6.8270, gga.altitude!!, 1e-9)
        assertEquals(-8.4925, gga.geoidSeparation!!, 1e-9)
        assertEquals(28, gga.satelliteCount)
        assertEquals(4, gga.fixQuality)
        assertEquals(0.8, gga.hdop!!, 1e-9)
        assertEquals(0.0, gga.dgpsAgeSeconds!!, 1e-9)

        assertEquals(10723.21, rmc.utcTimeSecondsOfDay!!, 1e-9)
        assertEquals(1770346723.21, rmc.utcEpochSeconds!!, 1e-9)
        assertEquals(true, rmc.valid)
        assertEquals(191.34, rmc.courseDegrees!!, 1e-9)
    }

    @Test
    fun `assembler emits frame when GGA RMC GGAH THS form one epoch`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 1.000).isEmpty())
        assertTrue(assembler.onSentence(RMC_MAIN, 1.005).isEmpty())
        assertTrue(assembler.onSentence(GGA_AUX, 1.010).isEmpty())

        val frames = assembler.onSentence(THS, 1.015)
        assertEquals(1, frames.size)

        val frame = frames.single()
        assertEquals(32.0603557406667, frame.main?.latitudeDeg!!, 1e-12)
        assertEquals(118.797136625, frame.main?.longitudeDeg!!, 1e-12)
        assertEquals(6.8270, frame.main?.altitudeM!!, 1e-9)
        assertEquals(-8.4925, frame.main?.geoidSeparationM!!, 1e-9)
        assertEquals(4, frame.main?.fixQualityCode)
        assertEquals(28, frame.main?.satellitesUsed)
        assertEquals(0.8, frame.main?.hdop!!, 1e-9)
        assertEquals(0.0, frame.main?.ageOfDiffS!!, 1e-9)

        assertEquals(32.0603551488333, frame.aux?.latitudeDeg!!, 1e-12)
        assertEquals(118.797140091667, frame.aux?.longitudeDeg!!, 1e-12)
        assertEquals(5.2576, frame.aux?.altitudeM!!, 1e-9)
        assertEquals(-8.4925, frame.aux?.geoidSeparationM!!, 1e-9)

        assertEquals(35.0, frame.headingDeg!!, 1e-9)
        assertEquals(true, frame.headingValid)
        assertEquals(true, frame.rmcValid)
        assertEquals(10723.21, frame.utcTimeSecondsOfDay!!, 1e-9)
        assertEquals(1770346723.21, frame.utcEpochSeconds!!, 1e-9)
        assertEquals(1.015, frame.hostObservedMonotonicS!!, 1e-12)
    }

    @Test
    fun `GGA and RMC within 5ms are treated as one epoch`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 1.000).isEmpty())
        assertTrue(assembler.onSentence(RMC_WITHIN_4MS, 1.004).isEmpty())

        val frames = assembler.onSentence(THS, 1.010)
        assertEquals(1, frames.size)
        assertEquals(10723.214, frames.single().utcTimeSecondsOfDay!!, 1e-9)
    }

    @Test
    fun `GGA and RMC beyond 5ms are not treated as one epoch`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 1.000).isEmpty())
        assertTrue(assembler.onSentence(RMC_OUTSIDE_6MS, 1.006).isEmpty())

        val frames = assembler.onSentence(THS, 1.010)
        assertTrue(frames.isEmpty())
    }

    @Test
    fun `THS only attaches within 20ms window`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 2.000).isEmpty())
        assertTrue(assembler.onSentence(RMC_MAIN, 2.005).isEmpty())

        val frames = assembler.onSentence(THS, 2.030)
        assertTrue(frames.isEmpty())
    }

    @Test
    fun `newer epoch discards unfinished older bucket`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 3.000).isEmpty())
        assertTrue(assembler.onSentence(GGA_NEXT, 3.100).isEmpty())
        assertTrue(assembler.onSentence(RMC_OLD_THS_TIME, 3.105).isEmpty())

        // 更旧的 THS 不允许挂到已经出现更新主历元的桶
        assertTrue(assembler.onSentence(THS, 3.090).isEmpty())

        val frames = assembler.onSentence(THS_NEXT, 3.115)
        assertEquals(1, frames.size)
        assertEquals(10724.21, frames.single().utcTimeSecondsOfDay!!, 1e-9)
    }

    @Test
    fun `different UTC GGA and RMC do not form epoch`() {
        val assembler = NmeaEpochAssembler()

        assertTrue(assembler.onSentence(GGA_MAIN, 4.000).isEmpty())
        assertTrue(assembler.onSentence(RMC_NEXT, 4.005).isEmpty())
        assertTrue(assembler.onSentence(THS, 4.015).isEmpty())
    }

    @Test
    fun `parseEpochs ignores incomplete trailing chunk`() {
        val frames = NmeaParser.parseEpochs(
            """
            $GGA_MAIN
            $RMC_MAIN
            $THS
            $PARTIAL
            """.trimIndent(),
            arrivalMonotonicS = 5.000
        )

        assertEquals(1, frames.size)
        assertNull(frames.single().aux)
    }

    companion object {
        private const val GGA_MAIN =
            "\$GNGGA,025843.21,3203.62134444,N,11847.82819750,E,4,28,0.8,6.8270,M,-8.4925,M,0.0,0000*5D"
        private const val RMC_MAIN =
            "\$GPRMC,025843.21,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*59"
        private const val GGA_AUX =
            "\$GNGGAH,025843.21,3203.62130893,N,11847.82840550,E,4,28,0.8,5.2576,M,-8.4925,M,0.00,0000*3F"
        private const val THS = "\$GNTHS,35.00,A*2F"
        private const val RMC_WITHIN_4MS =
            "\$GPRMC,025843.214,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*6D"
        private const val RMC_OUTSIDE_6MS =
            "\$GPRMC,025843.216,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*6F"

        private const val GGA_NEXT =
            "\$GNGGA,025844.21,3203.62134444,N,11847.82819750,E,4,28,0.8,6.8270,M,-8.4925,M,0.0,0000*44"
        private const val RMC_NEXT =
            "\$GPRMC,025844.21,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*5E"
        private const val THS_NEXT = "\$GNTHS,36.00,A*2C"
        private const val RMC_OLD_THS_TIME =
            "\$GPRMC,025844.21,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*5E"
        private const val PARTIAL = "\$GNTHS,99.00"
    }
}
