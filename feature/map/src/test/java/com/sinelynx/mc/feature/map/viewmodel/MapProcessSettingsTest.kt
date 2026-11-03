package com.sinelynx.grindingrobot.feature.map.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import sl_link.SlLink

class MapProcessSettingsTest {
    @Test fun recipeRoundTripsEveryProcessFieldWithoutManualChassisState() {
        val draft = WorkspaceDraft().copy(
            processType = ProcessType.POLISH,
            grindingParameter = GrindingParameter.GRIT_800,
            concreteStrength = ConcreteStrength.C30,
            processSpeed = .09, processPressure = 262.0, discSpeedRpm = 1500
        )
        val recipe = SlLink.MapProcessSettings.parseFrom(buildMapProcessSettings(draft).toByteArray())
        val restored = WorkspaceDraft().withSavedProcess(recipe)
        assertEquals(draft.processType, restored.processType)
        assertEquals(draft.grindingParameter, restored.grindingParameter)
        assertEquals(draft.concreteStrength, restored.concreteStrength)
        assertEquals(draft.processSpeed, restored.processSpeed, .000001)
        assertEquals(draft.processPressure, restored.processPressure, .000001)
        assertEquals(draft.discSpeedRpm, restored.discSpeedRpm)
    }

    @Test fun explicitZeroProcessSpeedIsPreserved() {
        val recipe = buildMapProcessSettings(WorkspaceDraft().copy(processSpeed = 0.0, discSpeedRpm = 0))
        val restored = WorkspaceDraft().withSavedProcess(recipe)
        assertEquals(0.0, restored.processSpeed, 0.0)
        assertEquals(0, restored.discSpeedRpm)
    }

    @Test fun loadingTwoWorkspaceRecipesDoesNotShareOrOverwriteTheirSettings() {
        val a = com.sinelynx.grindingrobot.core.model.state.WorkRegionProcessSettingsPayload(
            .03f, 925, "ROUGH", "GRIT_30", "C25", 345f)
        val b = a.copy(runSpeed = .07f, discSpeedRpm = 1200, pressure = 262f)
        val first = WorkspaceDraft().withSavedProcess(a)
        val second = WorkspaceDraft().withSavedProcess(b)
        assertEquals(.03, first.processSpeed, .000001)
        assertEquals(925, first.discSpeedRpm)
        assertEquals(.07, second.processSpeed, .000001)
        assertEquals(1200, second.discSpeedRpm)
        assertEquals(345.0, first.processPressure, 0.0)
        assertEquals(262.0, second.processPressure, 0.0)
    }
}
