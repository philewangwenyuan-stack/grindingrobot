package com.sinelynx.grindingrobot.feature.map.ui

import kotlinx.coroutines.delay

/** Tap once immediately, then repeat at 10 Hz after a short hold. Caller owns cancellation. */
internal suspend fun repeatAdjustment(onAdjust: () -> Unit) {
    onAdjust()
    delay(350L)
    while (true) {
        onAdjust()
        delay(100L)
    }
}
