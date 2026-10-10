package com.sinelynx.grindingrobot.feature.device.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskRuntimeSettingsPayload
import org.junit.Assert.*
import org.junit.Test

class TaskRuntimeEditorTest {
    private val settings = TaskRuntimeSettingsPayload("task", 0.06f, 1200, 0.15f, 1500, true)

    @Test fun missingOrPersistentOrDifferentTaskReplyCannotEnableEditing() {
        assertTrue(settings.isUsableFor("task"))
        assertFalse(settings.isUsableFor("other"))
        assertFalse(settings.copy(temporary = false).isUsableFor("task"))
        assertFalse(settings.copy(runSpeed = null).isUsableFor("task"))
        assertFalse(settings.copy(discSpeedRpm = null).isUsableFor("task"))
    }

    @Test fun invalidLimitsAndReportedValuesAreRejected() {
        assertFalse(settings.copy(maxRunSpeed = Float.NaN).isUsableFor("task"))
        assertFalse(settings.copy(runSpeed = Float.POSITIVE_INFINITY).isUsableFor("task"))
        assertFalse(settings.copy(discSpeedRpm = -1).isUsableFor("task"))
    }

    @Test fun speedCannotBypassLimitOrActAsPause() {
        assertNull(runtimeInputError(TaskRuntimeField.RUN_SPEED, "0.10", settings))
        for (input in listOf("0", "-1", "0.16", "NaN", "Infinity", "")) {
            assertNotNull(runtimeInputError(TaskRuntimeField.RUN_SPEED, input, settings))
        }
    }

    @Test fun rpmMustBeIntegralWithinLimitButMayBeZero() {
        assertNull(runtimeInputError(TaskRuntimeField.DISC_RPM, "0", settings))
        for (input in listOf("-1", "1501", "1200.5", "")) {
            assertNotNull(runtimeInputError(TaskRuntimeField.DISC_RPM, input, settings))
        }
    }

    @Test fun stepsRespectUpperLowerBoundsAndDoNotOverflow() {
        assertEquals("0.150", stepRuntimeInput(TaskRuntimeField.RUN_SPEED, "0.15", 1, settings))
        assertEquals("0.010", stepRuntimeInput(TaskRuntimeField.RUN_SPEED, "0.01", -1, settings))
        assertEquals("0", stepRuntimeInput(TaskRuntimeField.DISC_RPM, "0", -1, settings))
        assertEquals("1500", stepRuntimeInput(TaskRuntimeField.DISC_RPM, Long.MAX_VALUE.toString(), 1, settings))
    }
}
