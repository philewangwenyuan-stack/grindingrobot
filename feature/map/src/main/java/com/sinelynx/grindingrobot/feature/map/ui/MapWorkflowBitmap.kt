package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** One frozen image shared across workflow steps; decode never blocks the UI thread. */
private val WorkflowBitmapCache = SerializedMapDecoder<ImageBitmap> { bytes ->
    runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.apply {
            // Warm drawing caches on the decoding worker before the first UI frame.
            prepareToDraw()
        }?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun rememberWorkflowBitmap(bytes: ByteArray?): ImageBitmap? {
    val bitmap by produceState(WorkflowBitmapCache.peek(bytes), bytes) {
        value = WorkflowBitmapCache.peek(bytes)
        if (bytes != null && value == null) value = WorkflowBitmapCache.decode(bytes)
    }
    return bitmap
}
