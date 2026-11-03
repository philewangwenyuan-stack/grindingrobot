package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.dxf.*
import org.junit.Assert.*
import org.junit.Test

class DrawingAlignmentStateTest {
    private val controls=listOf(PointD(0.0,0.0),PointD(10000.0,0.0),PointD(0.0,10000.0)).map {ControlPair(it,it*.001)}
    @Test fun zeroControlsCannotReportSuccessfulAlignmentOrContinue() {
        val s=DrawingImportUiState(step=1)
        assertFalse(s.registrationSolved);assertFalse(s.canContinue);assertTrue(s.registrationHint!!.contains("3 对"))
    }
    @Test fun selectedDrawingPointExplainsMapSelectionStep() {
        val s=DrawingImportUiState(step=1,pendingDrawing=PointD(0.0,0.0))
        assertTrue(s.registrationHint!!.contains("地图"));assertFalse(s.canContinue)
    }
    @Test fun unconfirmedPairExplainsConfirmationStep() {
        val s=DrawingImportUiState(step=1,pendingDrawing=PointD(0.0,0.0),pendingMap=PointD(0.0,0.0))
        assertTrue(s.registrationHint!!.contains("确认这一对"));assertFalse(s.canContinue)
    }
    @Test fun addingThirdControlDoesNotReuseAnOldFit() {
        val s=DrawingImportUiState(step=1,controls=controls,transform=DrawingTransform())
        assertFalse(s.registrationSolved);assertFalse(s.canContinue)
    }
    @Test fun validSolvedFitUnlocksNextStep() {
        val s=DrawingImportUiState(step=1,controls=controls,transform=Registration.solve(controls))
        assertTrue(s.registrationSolved);assertTrue(s.canContinue);assertNull(s.registrationHint)
    }
    @Test fun excessiveResidualStillBlocksNextStep() {
        val s=DrawingImportUiState(step=1,controls=controls,transform=Registration.solve(controls).copy(rmsM=.21))
        assertTrue(s.registrationSolved);assertFalse(s.canContinue);assertTrue(s.registrationHint!!.contains("超限"))
    }
}
