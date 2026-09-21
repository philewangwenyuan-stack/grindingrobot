package com.sinelynx.grindingrobot.feature.main.viewmodel

import com.slcad.sdk.SlcadTaskKinds
import com.slcad.sdk.SlcadTaskQueryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppHomeViewModelGuidanceStateTest {

    @Test
    fun `guidanceSnapshotFromQuery maps valid surface guidance to home state`() {
        val snapshot = guidanceSnapshotFromQuery(
            query = taskQueryResult(
                surfaceMetricsValid = true,
                designElevationM = 12.34,
                elevationOffsetM = -0.56,
                normalDiffM = -0.12,
            ),
        )

        requireNotNull(snapshot)
        assertEquals(-0.56, snapshot.elevationOffsetM, 1e-9)
        assertEquals(12.34, snapshot.designElevationM, 1e-9)
        assertEquals(-0.56, snapshot.bucketTipNormalDiffM, 1e-9)
    }

    @Test
    fun `guidanceSnapshotFromQuery returns null for invalid surface metrics`() {
        val snapshot = guidanceSnapshotFromQuery(
            query = taskQueryResult(
                surfaceMetricsValid = false,
                designElevationM = 12.34,
                elevationOffsetM = -0.56,
                normalDiffM = -0.56,
            ),
        )

        assertNull(snapshot)
    }

    private fun taskQueryResult(
        surfaceMetricsValid: Boolean,
        designElevationM: Double,
        elevationOffsetM: Double,
        normalDiffM: Double,
    ) = SlcadTaskQueryResult(
        taskId = "task-1",
        taskKind = SlcadTaskKinds.DRAWING_SURFACE,
        queryXM = 0.0,
        queryYM = 0.0,
        queryZM = designElevationM + elevationOffsetM,
        surfaceMetricsValid = surfaceMetricsValid,
        referenceMetricsValid = false,
        designElevationM = designElevationM,
        normalDiffM = normalDiffM,
        targetSlopeDeg = 0.0,
        chainageM = 0.0,
        lateralOffsetM = 0.0,
        closestXM = 0.0,
        closestYM = 0.0,
        closestZM = 0.0,
        footXM = 0.0,
        footYM = 0.0,
        footZM = 0.0,
        blockingReason = "",
    )
}
