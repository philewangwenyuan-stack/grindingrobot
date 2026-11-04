package com.sinelynx.grindingrobot.feature.map.viewmodel

import org.junit.Assert.*
import org.junit.Test

class MapEditCheckpointTest {
    @Test fun pendingEraseUndoAndAcknowledgementsSurviveSerialization() {
        val erase = SavedErase(4, 1f, 2f, 3f, 4f, true, listOf(1f to 2f), listOf(listOf(2f to 3f)))
        val checkpoint = Step2Checkpoint("stable-batch", 9, 13f, listOf(erase), listOf(erase), emptyList(), listOf(4L to 0))
        val encoded = editCheckpointJson.encodeToString(Step2Checkpoint.serializer(), checkpoint)
        assertEquals(checkpoint, editCheckpointJson.decodeFromString(Step2Checkpoint.serializer(), encoded))
    }
    @Test fun pendingWorkspacePreservesBoundaryAndProcessSelections() {
        val draft = WorkspaceDraft(boundaryConfigured = true, boundaryDrawMode = BoundaryDrawMode.POLYGON,
            boundaryPolygonPointsM = listOf(1f to 2f, 3f to 4f, 5f to 6f),
            forbiddenCirclesM = listOf((4f to 5f) to 0.8f), scanDirection = "-Y", discSpeedRpm = 1000)
        val checkpoint = Step3Checkpoint(draft, WorkspacePanelMode.FORBIDDEN_EDITOR, "region", "pending", listOf("obstacle"), "-Y", 8, 99)
        val encoded = editCheckpointJson.encodeToString(Step3Checkpoint.serializer(), checkpoint)
        assertEquals(checkpoint, editCheckpointJson.decodeFromString(Step3Checkpoint.serializer(), encoded))
    }
}
