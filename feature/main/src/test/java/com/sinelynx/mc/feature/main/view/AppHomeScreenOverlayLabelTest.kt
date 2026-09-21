package com.sinelynx.grindingrobot.feature.main.ui

import com.slcad.sdk.SlcadHudTargets
import com.slcad.sdk.SlcadMainViewModes
import com.slcad.sdk.SlcadResolvedViewLayout
import com.slcad.sdk.SlcadViewInteractionProfiles
import com.slcad.sdk.SlcadViewTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class AppHomeScreenOverlayLabelTest {

    @Test
    fun buildOverlayLabels_onlyIncludesVisibleNonZeroLayouts() {
        val labels = buildOverlayLabels(
            listOf(
                layout(
                    viewId = "main",
                    viewType = SlcadViewTypes.PERSPECTIVE,
                    visible = true,
                    widthPx = 100,
                    heightPx = 80,
                ),
                layout(
                    viewId = "hidden",
                    viewType = SlcadViewTypes.ORTHO_FRONT,
                    visible = false,
                    widthPx = 100,
                    heightPx = 80,
                ),
                layout(
                    viewId = "zero",
                    viewType = SlcadViewTypes.ORTHO_TOP,
                    visible = true,
                    widthPx = 0,
                    heightPx = 80,
                ),
            )
        )

        assertEquals(1, labels.size)
        assertEquals("main", labels.first().viewId)
        assertEquals("3D", labels.first().label)
    }

    @Test
    fun overlayTitle_mapsKnownViewTypes() {
        assertEquals("3D", layout("p", SlcadViewTypes.PERSPECTIVE).overlayTitle())
        assertEquals(
            "俯视图",
            layout("main", SlcadViewTypes.PERSPECTIVE, mainViewMode = SlcadMainViewModes.NORTH_UP)
                .overlayTitle()
        )
        assertEquals("俯视图", layout("t", SlcadViewTypes.ORTHO_TOP).overlayTitle())
        assertEquals("正视图", layout("f", SlcadViewTypes.ORTHO_FRONT).overlayTitle())
        assertEquals("侧视图", layout("s", SlcadViewTypes.ORTHO_SIDE).overlayTitle())
        assertEquals("正视图", layout("driver_bucket", SlcadViewTypes.ORTHO_TOP).overlayTitle())
        assertEquals("侧视图", layout("side_bucket", SlcadViewTypes.ORTHO_TOP).overlayTitle())
        assertEquals("视图", layout("u", 99).overlayTitle())
    }

    @Test
    fun hudArrowCycle_advancesAndWraps() {
        assertEquals(SlcadHudTargets.MAIN_NORTH_UP, nextHudTarget(SlcadHudTargets.MAIN_3D))
        assertEquals(SlcadHudTargets.SIDE, nextHudTarget(SlcadHudTargets.MAIN_NORTH_UP))
        assertEquals(SlcadHudTargets.DRIVER, nextHudTarget(SlcadHudTargets.SIDE))
        assertEquals(SlcadHudTargets.MAIN_3D, nextHudTarget(SlcadHudTargets.DRIVER))
    }

    @Test
    fun hudArrowCycle_reversesAndWraps() {
        assertEquals(SlcadHudTargets.DRIVER, previousHudTarget(SlcadHudTargets.MAIN_3D))
        assertEquals(SlcadHudTargets.MAIN_3D, previousHudTarget(SlcadHudTargets.MAIN_NORTH_UP))
        assertEquals(SlcadHudTargets.MAIN_NORTH_UP, previousHudTarget(SlcadHudTargets.SIDE))
        assertEquals(SlcadHudTargets.SIDE, previousHudTarget(SlcadHudTargets.DRIVER))
    }

    private fun layout(
        viewId: String,
        viewType: Int,
        visible: Boolean = true,
        widthPx: Int = 120,
        heightPx: Int = 90,
        mainViewMode: Int = SlcadMainViewModes.FREE,
    ): SlcadResolvedViewLayout {
        return SlcadResolvedViewLayout(
            viewId = viewId,
            xPx = 0,
            yPx = 0,
            widthPx = widthPx,
            heightPx = heightPx,
            visible = visible,
            viewType = viewType,
            mainViewMode = mainViewMode,
            interactionProfile = SlcadViewInteractionProfiles.ORBIT_3D,
        )
    }
}
