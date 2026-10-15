package com.sinelynx.grindingrobot.feature.common.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageScrollbarMetricsTest {

    @Test
    fun threeVisibleRows_reachTrackTopMiddleAndBottom() {
        // Six 56 px rows with five 4 px gaps occupy 356 px; the viewport is 176 px.
        val top = metrics(count = 6)
        val middle = metrics(count = 6, index = 1, offset = 30)
        val bottom = metrics(count = 6, index = 3)

        assertTrue(top.canScroll)
        assertEquals(180f, top.maximumScrollOffsetPx, 0.001f)
        assertEquals(0f, top.thumbTopPx, 0.001f)
        assertEquals(middle.thumbTravelPx / 2f, middle.thumbTopPx, 0.001f)
        assertEquals(bottom.thumbTravelPx, bottom.thumbTopPx, 0.001f)
        assertEquals(180f, bottom.scrollOffsetForThumb(bottom.thumbTopPx), 0.001f)
    }

    @Test
    fun longHistory_keepsThumbTouchableWithoutLosingEndOfList() {
        val many = metrics(count = 500)

        assertEquals(24f, many.thumbHeightPx, 0.001f)
        assertEquals(152f, many.thumbTravelPx, 0.001f)
        assertEquals(many.maximumScrollOffsetPx / 2f, many.scrollOffsetForThumb(76f), 0.001f)
        assertEquals(0f, many.scrollOffsetForThumb(-100f), 0f)
        assertEquals(many.maximumScrollOffsetPx, many.scrollOffsetForThumb(500f), 0f)
    }

    @Test
    fun emptyAndSingleScreenLists_haveNoScrollRange() {
        listOf(0, 1, 3).forEach { count ->
            val result = metrics(count)
            assertFalse(result.canScroll)
            assertEquals(176f, result.thumbHeightPx, 0f)
            assertEquals(0f, result.scrollOffsetForThumb(100f), 0f)
        }
    }

    @Test
    fun clearingOrShrinkingHistory_clampsPreviousScrollPosition() {
        val cleared = metrics(count = 0, index = 490, offset = 40)
        val shortened = metrics(count = 6, index = 490, offset = 40)

        assertEquals(0f, cleared.thumbTopPx, 0f)
        assertFalse(cleared.canScroll)
        assertEquals(shortened.thumbTravelPx, shortened.thumbTopPx, 0.001f)
    }

    @Test
    fun unmeasuredViewportAndShortTrack_doNotDivideByZero() {
        val unmeasured = metrics(count = 6, viewport = 0f)
        val noTrack = metrics(count = 6, track = 0f)
        val shortTrack = metrics(count = 500, track = 12f)

        assertFalse(unmeasured.canScroll)
        assertFalse(noTrack.canScroll)
        assertFalse(shortTrack.canScroll)
        assertEquals(12f, shortTrack.thumbHeightPx, 0f)
        assertEquals(0f, shortTrack.scrollOffsetForThumb(8f), 0f)
    }

    private fun metrics(
        count: Int,
        index: Int = 0,
        offset: Int = 0,
        viewport: Float = 176f,
        track: Float = 176f
    ) = calculateMessageScrollbarMetrics(
        itemCount = count,
        rowHeightPx = 56f,
        rowSpacingPx = 4f,
        viewportHeightPx = viewport,
        trackHeightPx = track,
        firstVisibleItemIndex = index,
        firstVisibleItemScrollOffset = offset,
        minimumThumbPx = 24f
    )
}
