package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Pointer cancellation (including leaving the button) and disposal cancel the repeat job. */
@Composable
internal fun Modifier.repeatAdjustmentOnHold(onAdjust: () -> Unit): Modifier {
    val latest = rememberUpdatedState(onAdjust)
    return semantics {
        role = Role.Button
        onClick { latest.value(); true }
    }.focusable().pointerInput(Unit) {
        detectTapGestures(onPress = {
            coroutineScope {
                val repeat = launch(start = CoroutineStart.UNDISPATCHED) {
                    repeatAdjustment { latest.value() }
                }
                try {
                    tryAwaitRelease()
                } finally {
                    repeat.cancel()
                }
            }
        })
    }
}
