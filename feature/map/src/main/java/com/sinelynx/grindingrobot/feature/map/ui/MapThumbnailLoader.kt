package com.sinelynx.grindingrobot.feature.map.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.LruCache
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 按图片内容缓存；同图刷新复用，图片变化自动失效，内存最多 8 MiB。 */
internal object MapThumbnailLoader {
    private const val TARGET_SIZE_PX = 256
    private val decodeMutex = Mutex()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(base64: String): Bitmap? = withContext(Dispatchers.Default) {
        if (base64.isBlank()) return@withContext null
        decodeMutex.withLock {
            cache.get(base64)?.let { return@withLock it }
            val startedAt = SystemClock.elapsedRealtime()
            val bitmap = runCatching {
                val trimmed = base64.trim()
                val bytes = Base64.decode(trimmed.substringAfter(",", trimmed), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = thumbnailSampleSize(bounds.outWidth, bounds.outHeight, TARGET_SIZE_PX)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull()
            if (bitmap != null) cache.put(base64, bitmap)
            LogUtils.d("MapCatalogPerf", "thumbnail decodeMs=${SystemClock.elapsedRealtime() - startedAt}, success=${bitmap != null}")
            bitmap
        }
    }
}

/** 保证图片较长的一边缩到目标大小附近，避免将大图完整展开为缩略图。 */
internal fun thumbnailSampleSize(width: Int, height: Int, target: Int): Int {
    require(target > 0)
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / sample > target && sample <= Int.MAX_VALUE / 2) sample *= 2
    return sample
}
