package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.example.excavator.GNSSData
import com.example.excavator.Vector3
import com.sinelynx.grindingrobot.core.model.gnss.NmeaEpochAssembler
import com.sinelynx.grindingrobot.core.model.gnss.NmeaParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelGnssDataEquivalenceTest {

    @Test
    fun `same NMEA produces same GNSSData before and after refactor`() {
        val arrivalMonotonicS = 123.456
        val easting = 431234.567
        val northing = 3543210.123
        val height = 12.345

        val assembler = NmeaEpochAssembler()
        assembler.onSentence(GGA_MAIN, arrivalMonotonicS)
        assembler.onSentence(RMC_MAIN, arrivalMonotonicS)
        val frame = assembler.onSentence(THS, arrivalMonotonicS).single()

        val newGnssData = MainViewModel.buildGnssData(
            frame = frame,
            easting = easting,
            northing = northing,
            height = height
        )

        val legacyGnssData = buildLegacyGnssData(
            gga = NmeaParser.parseGgaData(GGA_MAIN),
            rmc = NmeaParser.parseRmcData(RMC_MAIN),
            ths = NmeaParser.parseThsData(THS),
            easting = easting,
            northing = northing,
            height = height,
            arrivalMonotonicS = arrivalMonotonicS
        )

        assertGnssDataEquals(legacyGnssData, newGnssData)
    }

    private fun buildLegacyGnssData(
        gga: NmeaParser.GgaData,
        rmc: NmeaParser.RmcData,
        ths: NmeaParser.ThsData,
        easting: Double,
        northing: Double,
        height: Double,
        arrivalMonotonicS: Double
    ): GNSSData {
        return GNSSData(
            timestamp = rmc.utcEpochSeconds ?: 0.0,
            gnss_heading = ths.headingDeg ?: 0.0,
            gnss_pos_w = Vector3(easting, northing, height),
            pos_status = gga.fixQuality ?: 0,
            host_observed_monotonic_s = arrivalMonotonicS
        )
    }

    private fun assertGnssDataEquals(expected: GNSSData, actual: GNSSData) {
        assertEquals(expected.timestamp, actual.timestamp, 1e-9)
        assertEquals(expected.gnss_heading, actual.gnss_heading, 1e-9)
        assertEquals(expected.pos_status, actual.pos_status)
        assertEquals(expected.host_observed_monotonic_s, actual.host_observed_monotonic_s, 1e-9)
        assertEquals(expected.gnss_pos_w.x, actual.gnss_pos_w.x, 1e-9)
        assertEquals(expected.gnss_pos_w.y, actual.gnss_pos_w.y, 1e-9)
        assertEquals(expected.gnss_pos_w.z, actual.gnss_pos_w.z, 1e-9)
    }

    companion object {
        private const val GGA_MAIN =
            "\$GNGGA,025843.21,3203.62134444,N,11847.82819750,E,4,28,0.8,6.8270,M,-8.4925,M,0.0,0000*5D"
        private const val RMC_MAIN =
            "\$GPRMC,025843.21,A,3203.62134444,N,11847.82819750,E,0.00,191.34,060226,,,D*59"
        private const val THS = "\$GNTHS,35.00,A*2F"
    }
}
