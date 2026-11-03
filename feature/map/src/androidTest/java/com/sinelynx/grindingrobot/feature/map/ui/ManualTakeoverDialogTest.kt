package com.sinelynx.grindingrobot.feature.map.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinelynx.grindingrobot.core.model.state.ManualTaskGate
import com.sinelynx.grindingrobot.feature.common.component.ManualDriveLifecycle
import com.sinelynx.grindingrobot.feature.common.control.ManualTakeoverCoordinator
import com.sinelynx.grindingrobot.feature.common.control.ManualTakeoverState
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated Dialog/window lifecycle test: no production TCP manager or robot. */
@RunWith(AndroidJUnit4::class)
class ManualTakeoverDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ownDialogFocusLossDoesNotDismissConfirmationOrSendTaskStop() {
        var stoppedTasks=0
        var zeroedMotion=0
        compose.setContent {
            var state by remember { mutableStateOf(ManualTakeoverState()) }
            val scope=rememberCoroutineScope()
            val takeover=remember { ManualTakeoverCoordinator(
                {ManualTaskGate(true,true,"isolated-task")},
                {stoppedTasks++;true}, {zeroedMotion++}, {state=it}) }
            LaunchedEffect(Unit) {takeover.observeTask()}
            ManualDriveLifecycle({}, {takeover.cancel()},
                {takeover.windowBlurred()}, {})
            Column {
                Button(onClick={takeover.request()}) {Text("请求接管")}
                if (state.visible) Dialog(onDismissRequest={takeover.cancel()}) {
                    Column {
                        Text("确认接管自动任务")
                        Button(onClick={scope.launch {takeover.confirm()}}) {Text("确认")}
                        Button(onClick={takeover.cancel()}) {Text("取消")}
                    }
                }
            }
        }
        compose.onNodeWithText("请求接管").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("确认接管自动任务").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,stoppedTasks);assertTrue(zeroedMotion>0)}
        compose.onNodeWithText("确认",useUnmergedTree=true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("确认接管自动任务").assertDoesNotExist()
        compose.runOnIdle {assertEquals(1,stoppedTasks)}
    }
}
