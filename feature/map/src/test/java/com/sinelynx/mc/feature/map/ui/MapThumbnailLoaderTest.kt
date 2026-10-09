package com.sinelynx.grindingrobot.feature.map.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapThumbnailLoaderTest {
    @Test
    fun existingSmallThumbnailIsNotDownsampled() {
        assertEquals(1, thumbnailSampleSize(128, 96, 256))
        assertEquals(1, thumbnailSampleSize(256, 256, 256))
    }

    @Test
    fun largeAndNarrowImagesAreBoundedByTheirLongestSide() {
        assertEquals(16, thumbnailSampleSize(4096, 32, 256))
        assertEquals(16, thumbnailSampleSize(32, 4096, 256))
        assertEquals(2, thumbnailSampleSize(257, 128, 256))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroTargetSize() {
        thumbnailSampleSize(128, 128, 0)
    }
}
