package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** One frozen image shared across workflow steps; decode never blocks the UI thread. */
private object WorkflowBitmapCache {
    private var source = WeakReference<ByteArray>(null)
    private var bitmap: ImageBitmap? = null
    @Synchronized fun peek(bytes: ByteArray?): ImageBitmap? =
        if (bytes != null && source.get() === bytes) bitmap else null
    fun decode(bytes: ByteArray): ImageBitmap? {
        peek(bytes)?.let { return it }
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        synchronized(this) {
            source = WeakReference(bytes)
            bitmap = decoded
        }
        return decoded
    }
}

@Composable
fun rememberWorkflowBitmap(bytes: ByteArray?): ImageBitmap? {
    val bitmap by produceState(WorkflowBitmapCache.peek(bytes), bytes) {
        value = WorkflowBitmapCache.peek(bytes)
        if (bytes != null && value == null) value = withContext(Dispatchers.Default) { WorkflowBitmapCache.decode(bytes) }
    }
    return bitmap
}
