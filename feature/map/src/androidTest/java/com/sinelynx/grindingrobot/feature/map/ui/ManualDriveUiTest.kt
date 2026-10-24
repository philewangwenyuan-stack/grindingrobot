package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.feature.common.component.LocalManualFeedback
import com.sinelynx.grindingrobot.feature.common.component.LocalManualFeedbackAllowed
import com.sinelynx.grindingrobot.feature.common.component.ManualFeedback
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4UiState
import com.sinelynx.grindingrobot.feature.map.viewmodel.EditToolMode
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinelynx.grindingrobot.core.model.state.DevicePosePayload
import com.sinelynx.grindingrobot.feature.common.component.ManualRemoteControlContent
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveController
import com.sinelynx.grindingrobot.feature.common.control.ManualDriveState
import com.sinelynx.grindingrobot.feature.common.control.TravelDirection
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapUiState
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated native Compose screens. No production Application, socket, or robot command is used. */
@RunWith(AndroidJUnit4::class)
class ManualDriveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun landscapeTablet() {
        MapBuildSessionStream.begin()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(15_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }

    @Test fun remoteSlidersAndDirectionLatchThenCapture() {
        lateinit var control: ManualDriveController
        compose.setContent {
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf(ManualDriveState()) }
            control = remember { ManualDriveController(scope, { true }, { true }, { state = it }) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFEBEEF2)).padding(16.dp)) {
                    Text("手动遥控  ·  离线界面预览", fontSize = 22.sp, color = Color(0xFF202937),
                        modifier = Modifier.padding(bottom = 16.dp))
                    ManualRemoteControlContent(state, 300, control::travel, control::steer,
                        control::cancelSteering, control::stop, control::setSpeed, control::setTurnPercent,
                        {}, {}, Modifier.weight(1f))
                }
            }
        }
        compose.runOnIdle { control.refreshAvailability() }
        compose.onNodeWithTag("travel_forward").performClick()
        compose.onNodeWithTag("travel_backward").performClick()
        compose.runOnIdle { assertEquals(TravelDirection.STOPPED, control.state.direction) }
        compose.onNodeWithTag("travel_backward").performClick()
        compose.runOnIdle { assertEquals(TravelDirection.BACKWARD, control.state.direction) }
        compose.onNodeWithTag("manual_stop").performClick()
        compose.onNodeWithTag("slider_行走速度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.12f) }
        compose.runOnIdle { assertEquals(0.12f, control.state.speedMps, 0.001f) }
        compose.onNodeWithTag("slider_行走速度").performSemanticsAction(SemanticsActions.SetProgress) { it(0.05f) }
        compose.onNodeWithTag("travel_forward").performClick()
        compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(0.01f) }
        compose.runOnIdle { assertEquals(0.0495f, control.state.commandedSpeedMps, 0.00001f) }
        compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle { assertEquals(0.025f, control.state.commandedSpeedMps, 0.00001f) }
        capture("remote-control.png")
        compose.onNodeWithTag("manual_stop").performClick()
        compose.runOnIdle { assertFalse(control.state.hasMotion) }
    }

    @Test fun mapKeepsMapAndUsesSameProportionalControlsThenCapture() {
        val state = MapUiState(
            drive = ManualDriveState(direction = TravelDirection.FORWARD, steering = 0.5f, enabled = true),
            mapImageBytes = sampleMap(), mapImageSize = 1400 to 450,
            mapGeo = MapGeo(1400, 450, 0.02f, 0.0, 0.0, 0f, 0),
            robotPose = DevicePosePayload(22f, 5f, 25f), robotWidth = 0.7, robotLength = 1.0,
        )
        compose.setContent {
            MaterialTheme {
                MapScreenContent(state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("雷达图").assertExists()
        compose.onNodeWithTag("slider_行走速度").assertExists()
        compose.onNodeWithTag("slider_转向力度").assertExists()
        compose.onNodeWithTag("manual_stop").assertExists()
        capture("mapping-control.png")
    }

    @Test fun heldJoystickCannotRestartAfterExplicitStop() {
        lateinit var control: ManualDriveController
        compose.setContent {
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf(ManualDriveState()) }
            control = remember { ManualDriveController(scope, { true }, { true }, { state = it }) }
            MaterialTheme {
                ManualRemoteControlContent(state, 300, control::travel, control::steer,
                    control::cancelSteering, control::stop, control::setSpeed, control::setTurnPercent, {}, {})
            }
        }
        compose.runOnIdle { control.refreshAvailability() }
        compose.onNodeWithTag("travel_forward").performClick()
        compose.onNodeWithTag("steering_joystick").performTouchInput {
            down(center)
            moveTo(center + androidx.compose.ui.geometry.Offset(width * 0.2f, 0f))
        }
        compose.runOnIdle { assertTrue(control.state.steering > 0f) }
        // Invoke the second control semantically while pointer 0 remains held on the joystick.
        compose.onNodeWithTag("manual_stop").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("steering_joystick").performTouchInput {
            moveTo(center + androidx.compose.ui.geometry.Offset(width * 0.3f, 0f))
            up()
        }
        compose.runOnIdle { assertFalse(control.state.hasMotion) }
    }

    @Test fun mappingPinchZoomSurvivesLiveFramesAndResetRestoresFit() {
        var state by mutableStateOf(MapUiState(
            mapImageBytes = sampleMap(), mapImageSize = 1400 to 450,
            mapGeo = MapGeo(1400, 450, 0.02f, 0.0, 0.0, 0f, 0),
            robotPose = DevicePosePayload(14f, 4.5f, 25f), robotWidth = 0.7, robotLength = 1.0,
        ))
        var motionEvents = 0
        compose.setContent {
            MaterialTheme {
                MapScreenContent(state, {}, {}, { motionEvents++ }, { _, _ -> motionEvents++ },
                    { motionEvents++ }, { motionEvents++ }, {}, {}, {}, {})
            }
        }
        fun zoom() = compose.onNodeWithTag("mapping_viewport").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        compose.onNodeWithTag("mapping_viewport").performTouchInput {
            val delta = androidx.compose.ui.geometry.Offset(60f, 0f)
            pinch(center - delta, center + delta, center - delta * 2f, center + delta * 2f)
        }
        val pinchZoom = zoom()
        assertTrue("Pinch must enlarge the actual map", pinchZoom > 1.5f)
        compose.runOnIdle { state = state.copy(mapImageBytes = sampleMap()) }
        assertEquals("Live frame must preserve the user's viewport", pinchZoom, zoom(), 0.001f)
        compose.onNodeWithTag("mapping_viewport").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(60f, 0f))
        }
        assertEquals(pinchZoom, zoom(), 0.001f)
        compose.onNodeWithTag("mapping_viewport").performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        assertEquals(8f, zoom(), 0f)
        compose.onNodeWithTag("mapping_viewport").performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
        assertEquals(1f, zoom(), 0f)
        compose.onNodeWithTag("mapping_viewport").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        capture("mapping-zoom.png")
        compose.onNodeWithTag("mapping_zoom_reset").performClick()
        assertEquals(1f, zoom(), 0f)
        compose.runOnIdle { assertEquals("Map gestures must not issue drive input", 0, motionEvents) }
    }

    @Test fun feedbackOnlyComesFromAcceptedInputAndDoesNotRepeatWhileHeld() {
        val events = mutableListOf<ManualFeedback>()
        var allow by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalManualFeedback provides { events.add(it) },
                LocalManualFeedbackAllowed provides { allow }) {
                MaterialTheme {
                    ManualRemoteControlContent(ManualDriveState(enabled = enabled), 300,
                        {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("travel_forward").performClick()
        assertEquals(listOf(ManualFeedback.Tick), events)
        repeat(6) {
            compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        }
        assertEquals(2, events.size)
        compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("manual_stop").performClick()
        assertEquals(ManualFeedback.Stop, events.last())
        val count = events.size
        compose.runOnIdle { allow = false }
        compose.onNodeWithTag("travel_forward").performClick()
        compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(-1f) }
        compose.runOnIdle { enabled = false; allow = true }
        compose.onNodeWithTag("travel_forward").assertIsNotEnabled()
        compose.onNodeWithTag("steering_joystick").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(count, events.size) }
    }

    /** Production header/actions/map/editor/panels with offline data, without network ViewModels. */
    @Test fun workflowNavigationKeepsMapBoundsButtonsAndViewportThenCaptures() {
        val bytes = sampleMap()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        val geo = MapGeo(1400, 450, 0.02f, 0.0, 0.0, 0f, 0)
        val state = MapScreenStep4UiState(mapImageBytes = bytes, mapImageSize = 1400 to 450,
            bitmapDecodeSize = 1400 to 450, mapGeo = geo, mapName = "边角研磨区",
            estimatedAreaM2 = 132f, estimatedDurationHours = 0.45f,
            robotPose = DevicePosePayload(22f, 5f, 25f), robotWidth = 0.7, robotLength = 1.0)
        var step by mutableStateOf(1)
        compose.setContent {
            MaterialTheme {
                if (step == 1) {
                    MapScreenContent(MapUiState(mapImageBytes = bytes, mapImageSize = state.mapImageSize,
                        mapGeo = geo, drive = ManualDriveState(enabled = true)), {}, {}, {}, { _, _ -> },
                        {}, {}, {}, {}, {}, { step = 2 })
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFEBEEF2)).padding(12.dp)) {
                        val sideWidth = workflowSideWidth(maxWidth)
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MapWorkflowHeader(step, {})
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (step == 2) Column(Modifier.width(sideWidth).fillMaxHeight().padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        ModeToolButton(true, com.sinelynx.grindingrobot.feature.map.R.drawable.ic_gesture_selected,
                                            com.sinelynx.grindingrobot.feature.map.R.drawable.ic_gesture_unselcted, "手势模式", {})
                                        ModeToolButton(false, com.sinelynx.grindingrobot.feature.map.R.drawable.ic_eraser_selected,
                                            com.sinelynx.grindingrobot.feature.map.R.drawable.ic_eraser_unselected, "橡皮擦模式", {})
                                    } else WorkflowGuidePanel(if (step == 3) "划分工作区" else "预览与保存",
                                        if (step == 3) "双指缩放地图\n拖动查看细节\n\n在右侧添加或编辑工作区"
                                        else "双指缩放地图\n拖动查看细节\n\n确认工作区后保存", Modifier.width(sideWidth).fillMaxHeight())
                                    Surface(Modifier.weight(1f).fillMaxHeight().testTag("workflow_map"),
                                        color = Color(0xFFF6F8FB), shape = RoundedCornerShape(16.dp)) {
                                        if (step == 2) MapEditingPanel(null, bitmap, EditToolMode.Gesture,
                                            0f, geo, state.mapImageSize, state.bitmapDecodeSize,
                                            state.robotPose, 0.7, 1.0, emptyList(), emptyList(),
                                            emptyList(), emptyList(), emptyList(), null, false,
                                            {}, {}, {}, {})
                                        else WorkflowMapPreview(state, bitmap, null)
                                    }
                                    if (step == 4) WorkflowSavePanel(state, {}, Modifier.width(sideWidth).fillMaxHeight())
                                    else if (step == 3) Surface(Modifier.width(sideWidth).fillMaxHeight(),
                                        color = Color(0xFFFEFEFF), shape = RoundedCornerShape(16.dp)) {
                                        WorkspaceListPanel(emptyList(), {}, {}, {}, {})
                                    } else Box(Modifier.width(sideWidth).fillMaxHeight()) {
                                        UndoRedoDock(false, false, {}, {},
                                            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 20.dp))
                                        MapRotationController(0f, true, {}, {},
                                            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 120.dp))
                                    }
                                }
                            }
                            MapWorkflowActions({}, { step-- }, { if (step < 4) step++ }, save = step == 4)
                        }
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val mapBounds = bounds("workflow_map")
        val cancelBounds = bounds("workflow_取消建图")
        val nextBounds = bounds("workflow_下一步")
        compose.onNodeWithTag("mapping_viewport").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        val initialView = MapBuildSessionStream.viewportFor(null)
        for (page in 2..4) {
            compose.onNodeWithTag("workflow_下一步").performClick()
            assertEquals(mapBounds, bounds("workflow_map"))
            assertEquals(cancelBounds, bounds("workflow_取消建图"))
            assertEquals(nextBounds, bounds(if (page == 4) "workflow_保存地图" else "workflow_下一步"))
            assertEquals(initialView, MapBuildSessionStream.viewportFor(null))
            capture("mapping-step$page.png")
        }
        repeat(3) { compose.onNodeWithTag("workflow_上一步").performClick() }
        assertEquals(mapBounds, bounds("workflow_map"))
        assertEquals(2f, compose.onNodeWithTag("mapping_viewport").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "teleop-ui")
        dir.mkdirs()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Synthetic occupancy frame exercises the existing map renderer; it is not live device data. */
    private fun sampleMap(): ByteArray {
        val bitmap = Bitmap.createBitmap(1400, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(226, 232, 238))
        val paint = Paint().apply { color = AndroidColor.rgb(210, 218, 226); strokeWidth = 1f }
        for (x in 0..1400 step 25) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 450f, paint)
        for (y in 0..450 step 25) canvas.drawLine(0f, y.toFloat(), 1400f, y.toFloat(), paint)
        paint.color = AndroidColor.rgb(69, 76, 83)
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(85f, 55f, 1315f, 395f, paint)
        canvas.drawLine(1110f, 55f, 1110f, 145f, paint)
        canvas.drawLine(1110f, 145f, 1315f, 145f, paint)
        canvas.drawLine(1160f, 395f, 1160f, 290f, paint)
        paint.style = Paint.Style.FILL
        for (x in listOf(380f, 660f, 930f)) canvas.drawRect(x, 220f, x + 18f, 244f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
