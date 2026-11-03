package com.sinelynx.grindingrobot.feature.map.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import sl_link.SlLink

/** 固定工艺组合对应的施工参数，并覆盖切换、手动调整和底盘设置输出的兼容规则。 */
class ProcessPresetTest {

    @Test
    fun allGrindingParameterAndStrengthCombinations_matchConstructionTable() {
        val expectedByParameter = mapOf(
            GrindingParameter.GRIT_30 to listOf(
                expected(ConcreteStrength.C25, 0.03, 345.0, 925),
                expected(ConcreteStrength.C30, 0.03, 345.0, 1000),
                expected(ConcreteStrength.C35, 0.02, 428.0, 1050)
            ),
            GrindingParameter.GRIT_50 to listOf(
                expected(ConcreteStrength.C25, 0.03, 345.0, 925),
                expected(ConcreteStrength.C30, 0.03, 345.0, 1000),
                expected(ConcreteStrength.C35, 0.02, 428.0, 1050)
            ),
            GrindingParameter.GRIT_100 to listOf(
                expected(ConcreteStrength.C25, 0.07, 345.0, 1250),
                expected(ConcreteStrength.C30, 0.05, 345.0, 1300),
                expected(ConcreteStrength.C35, 0.05, 345.0, 1350)
            ),
            GrindingParameter.GRIT_200 to listOf(
                expected(ConcreteStrength.C25, 0.07, 262.0, 1400),
                expected(ConcreteStrength.C30, 0.07, 345.0, 1450),
                expected(ConcreteStrength.C35, 0.05, 345.0, 1475)
            ),
            GrindingParameter.GRIT_400 to listOf(
                expected(ConcreteStrength.C25, 0.09, 262.0, 1500),
                expected(ConcreteStrength.C30, 0.07, 262.0, 1500),
                expected(ConcreteStrength.C35, 0.07, 345.0, 1500)
            ),
            GrindingParameter.GRIT_800 to listOf(
                expected(ConcreteStrength.C25, 0.09, 262.0, 1500),
                expected(ConcreteStrength.C30, 0.09, 262.0, 1500),
                expected(ConcreteStrength.C35, 0.07, 262.0, 1500)
            ),
            GrindingParameter.GRIT_1500 to listOf(
                expected(ConcreteStrength.C25, 0.09, 262.0, 1500),
                expected(ConcreteStrength.C30, 0.09, 262.0, 1500),
                expected(ConcreteStrength.C35, 0.07, 262.0, 1500)
            ),
            GrindingParameter.GRIT_2000 to listOf(
                expected(ConcreteStrength.C25, 0.12, 262.0, 1500),
                expected(ConcreteStrength.C30, 0.12, 262.0, 1500),
                expected(ConcreteStrength.C35, 0.10, 262.0, 1500)
            ),
            GrindingParameter.GRIT_3000 to listOf(
                expected(ConcreteStrength.C25, 0.12, 262.0, 1500),
                expected(ConcreteStrength.C30, 0.12, 262.0, 1500),
                expected(ConcreteStrength.C35, 0.10, 262.0, 1500)
            )
        )

        assertEquals(27, expectedByParameter.values.sumOf { it.size })
        expectedByParameter.forEach { (parameter, expectations) ->
            expectations.forEach { expected ->
                assertPresetEquals(expected, processPreset(parameter, expected.strength))
            }
        }
    }

    @Test
    fun processTypeOptions_coverThirtySelectableCombinations() {
        val combinations = ProcessType.entries.sumOf { type ->
            GrindingParameter.optionsFor(type).size * ConcreteStrength.entries.size
        }

        assertEquals(27, combinations)
        assertEquals(
            listOf(GrindingParameter.GRIT_30, GrindingParameter.GRIT_50, GrindingParameter.GRIT_100),
            GrindingParameter.optionsFor(ProcessType.ROUGH)
        )
        assertEquals(
            listOf(GrindingParameter.GRIT_200, GrindingParameter.GRIT_400),
            GrindingParameter.optionsFor(ProcessType.FINE)
        )
        assertEquals(
            listOf(
                GrindingParameter.GRIT_800,
                GrindingParameter.GRIT_1500,
                GrindingParameter.GRIT_2000,
                GrindingParameter.GRIT_3000
            ),
            GrindingParameter.optionsFor(ProcessType.POLISH)
        )
    }

    @Test
    fun changingType_resetsParameterAndKeepsStrengthThenAppliesPreset() {
        val original = WorkspaceDraft().withProcessSelection(
            parameter = GrindingParameter.GRIT_100,
            strength = ConcreteStrength.C35
        )

        val result = original.withProcessSelection(type = ProcessType.FINE)

        assertEquals(ProcessType.FINE, result.processType)
        assertEquals(GrindingParameter.GRIT_200, result.grindingParameter)
        assertEquals(ConcreteStrength.C35, result.concreteStrength)
        assertEquals(0.05, result.processSpeed, 0.0001)
        assertEquals(345.0, result.processPressure, 0.0001)
        assertEquals(1475, result.discSpeedRpm)
    }

    @Test
    fun changingParameterOrStrength_updatesAllDerivedValues() {
        val parameterChanged = WorkspaceDraft().withProcessSelection(
            type = ProcessType.POLISH,
            parameter = GrindingParameter.GRIT_2000
        )
        assertEquals(0.12, parameterChanged.processSpeed, 0.0001)
        assertEquals(262.0, parameterChanged.processPressure, 0.0001)
        assertEquals(1500, parameterChanged.discSpeedRpm)

        val strengthChanged = parameterChanged.withProcessSelection(strength = ConcreteStrength.C35)
        assertEquals(0.10, strengthChanged.processSpeed, 0.0001)
        assertEquals(262.0, strengthChanged.processPressure, 0.0001)
        assertEquals(1500, strengthChanged.discSpeedRpm)
    }

    @Test
    fun manualAdjustments_followStepsAndClampToSupportedRanges() {
        val draft = WorkspaceDraft()

        assertEquals(0.02, draft.withProcessSpeed(draft.processSpeed - 0.01).processSpeed, 0.0001)
        assertEquals(0.04, draft.withProcessSpeed(draft.processSpeed + 0.01).processSpeed, 0.0001)
        assertEquals(0.0, draft.withProcessSpeed(-1.0).processSpeed, 0.0001)
        assertEquals(0.2, draft.withProcessSpeed(1.0).processSpeed, 0.0001)

        assertEquals(344.0, draft.withProcessPressure(draft.processPressure - 1.0).processPressure, 0.0001)
        assertEquals(346.0, draft.withProcessPressure(draft.processPressure + 1.0).processPressure, 0.0001)
        assertEquals(1.0, draft.withProcessPressure(-1.0).processPressure, 0.0001)
        assertEquals(477.0, draft.withProcessPressure(999.0).processPressure, 0.0001)

        assertEquals(825, draft.withDiscSpeed(draft.discSpeedRpm - 100).discSpeedRpm)
        assertEquals(1025, draft.withDiscSpeed(draft.discSpeedRpm + 100).discSpeedRpm)
        assertEquals(0, draft.withDiscSpeed(-100).discSpeedRpm)
        assertEquals(1500, draft.withDiscSpeed(2000).discSpeedRpm)
    }

    @Test
    fun directInput_clampsValidValuesAndIgnoresInvalidValues() {
        val draft = WorkspaceDraft()

        assertEquals(0.2, draft.withProcessSpeedInput("0.25").processSpeed, 0.0001)
        assertEquals(477.0, draft.withProcessPressureInput("500").processPressure, 0.0001)
        assertEquals(1500, draft.withDiscSpeedInput("1600").discSpeedRpm)
        assertEquals(draft, draft.withProcessSpeedInput("invalid"))
        assertEquals(draft, draft.withProcessPressureInput(""))
        assertEquals(draft, draft.withDiscSpeedInput("12.5"))
    }

    @Test
    fun mapRecipe_containsProcessMetadataWithoutChassisControl() {
        val draft = WorkspaceDraft().withProcessSelection(
            type = ProcessType.ROUGH,
            parameter = GrindingParameter.GRIT_50,
            strength = ConcreteStrength.C30
        )

        val manuallyAdjusted = draft
            .withProcessSpeed(0.08)
            .withProcessPressure(400.0)
            .withDiscSpeed(1200)
        val decoded = SlLink.MapProcessSettings.parseFrom(
            buildMapProcessSettings(manuallyAdjusted).toByteArray()
        )

        assertEquals(0.08f, decoded.runSpeed, 0.0001f)
        assertEquals(1200, decoded.discSpeedRpm)
        assertEquals(400f, decoded.pressure, 0f)
        assertEquals("ROUGH", decoded.processType)
        assertEquals("GRIT_50", decoded.grindingParameter)
        assertEquals("C30", decoded.concreteStrength)
    }

    private fun expected(
        strength: ConcreteStrength,
        speed: Double,
        pressure: Double,
        rpm: Int
    ) = ExpectedPreset(strength, speed, pressure, rpm)

    private fun assertPresetEquals(expected: ExpectedPreset, actual: ProcessPreset) {
        assertEquals(expected.speed, actual.speedMetersPerSecond, 0.0001)
        assertEquals(expected.pressure, actual.pressureKg, 0.0001)
        assertEquals(expected.rpm, actual.discSpeedRpm)
    }

    private data class ExpectedPreset(
        val strength: ConcreteStrength,
        val speed: Double,
        val pressure: Double,
        val rpm: Int
    )
}
