package com.sinelynx.grindingrobot.feature.map.ui

import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Native decodes cannot be interrupted. Keep their allocations serialized across screens. */
internal class SerializedMapDecoder<T : Any>(private val decodeImage: (ByteArray) -> T?) {
    private val decodeLock = Mutex()
    private var source = WeakReference<ByteArray>(null)
    private var image: T? = null

    @Synchronized fun peek(bytes: ByteArray?): T? =
        if (bytes != null && source.get() === bytes) image else null

    suspend fun decode(bytes: ByteArray): T? = decodeLock.withLock {
        currentCoroutineContext().ensureActive()
        peek(bytes)?.let { return@withLock it }
        // Cancellation waits for native work to finish before releasing the mutex.
        val decoded = withContext(Dispatchers.Default) { decodeImage(bytes) }
        synchronized(this) {
            source = WeakReference(bytes)
            image = decoded
        }
        decoded
    }
}
