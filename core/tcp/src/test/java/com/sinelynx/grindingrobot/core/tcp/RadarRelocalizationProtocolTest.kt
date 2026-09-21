package com.sinelynx.grindingrobot.core.tcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sl_link.SlFrameParser
import sl_link.SlLink
import sl_link.SlMessageBuilder

class RadarRelocalizationProtocolTest {

    @Test
    fun initialPoseRequestUses052AAndPreservesPoseFields() {
        val request = SlLink.RadarRelocalizationRequest.newBuilder()
            .setInitialPoseAvailable(true)
            .setInitialPose(
                SlLink.Pose2D.newBuilder()
                    .setX(1.2f)
                    .setY(-0.5f)
                    .setHeadingDeg(30f)
                    .build()
            )
            .setInitialPoseCovariance(
                SlLink.LocalizationCovariance.newBuilder()
                    .setValid(true)
                    .setXVariance(0.25f)
                    .setYVariance(0.25f)
                    .setYawVariance(0.06853892f)
                    .build()
            )
            .build()

        val frame = SlFrameParser().parse(
            SlMessageBuilder.buildRadarRelocalizationRequestRaw(request.toByteArray(), 0x10u)
        ).single()

        assertEquals(0x052A, frame.msgId.toInt())
        assertEquals(0x10, frame.dstId.toInt())
        assertEquals(0x08, frame.compId.toInt())

        val decoded = SlLink.RadarRelocalizationRequest.parseFrom(frame.payload)
        assertTrue(decoded.initialPoseAvailable)
        assertEquals(1.2f, decoded.initialPose.x, 0.0001f)
        assertEquals(-0.5f, decoded.initialPose.y, 0.0001f)
        assertEquals(30f, decoded.initialPose.headingDeg, 0.0001f)
        assertTrue(decoded.initialPoseCovariance.valid)
        assertEquals(0.25f, decoded.initialPoseCovariance.xVariance, 0.0001f)
    }
}
