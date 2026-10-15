package com.sinelynx.grindingrobot.feature.common.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/** A scrollbar for a fixed-height list without content padding. */
@Composable
internal fun MessageListScrollbar(
    state: LazyListState,
    itemCount: Int,
    rowHeight: Dp,
    rowSpacing: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.roundToPx().toFloat() }
    val rowSpacingPx = with(density) { rowSpacing.roundToPx().toFloat() }
    val minimumThumbPx = with(density) { 24.dp.toPx() }
    val scope = rememberCoroutineScope()
    var scrollJob by remember(state) { mutableStateOf<Job?>(null) }

    DisposableEffect(state, itemCount) {
        onDispose { scrollJob?.cancel() }
    }

    fun metrics(trackHeightPx: Float): MessageScrollbarMetrics {
        val layout = state.layoutInfo
        return calculateMessageScrollbarMetrics(
            itemCount = minOf(itemCount, layout.totalItemsCount),
            rowHeightPx = rowHeightPx,
            rowSpacingPx = rowSpacingPx,
            viewportHeightPx = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat(),
            trackHeightPx = trackHeightPx,
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
            minimumThumbPx = minimumThumbPx
        )
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "消息列表滚动条" }
            .pointerInput(state, itemCount, rowHeightPx, rowSpacingPx, minimumThumbPx) {
                fun moveTo(pointerY: Float, grabOffsetPx: Float) {
                    val current = metrics(size.height.toFloat())
                    if (!current.canScroll) return
                    val target = current.scrollOffsetForThumb(pointerY - grabOffsetPx)
                    val stride = rowHeightPx + rowSpacingPx
                    val index = floor(target / stride).toInt()
                        .coerceIn(0, minOf(itemCount, state.layoutInfo.totalItemsCount) - 1)
                    val itemOffset = (target - index * stride).roundToInt().coerceAtLeast(0)
                    scrollJob?.cancel()
                    scrollJob = scope.launch { state.scrollToItem(index, itemOffset) }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val current = metrics(size.height.toFloat())
                    if (!current.canScroll) return@awaitEachGesture
                    down.consume()
                    val onThumb = down.position.y in
                        current.thumbTopPx..(current.thumbTopPx + current.thumbHeightPx)
                    val grabOffset = if (onThumb) {
                        down.position.y - current.thumbTopPx
                    } else {
                        current.thumbHeightPx / 2f
                    }
                    if (!onThumb) moveTo(down.position.y, grabOffset)
                    drag(down.id) { change ->
                        change.consume()
                        moveTo(change.position.y, grabOffset)
                    }
                }
            }
    ) {
        if (itemCount <= 0) return@Canvas
        val current = metrics(size.height)
        val trackWidth = 4.dp.toPx()
        val thumbWidth = 6.dp.toPx()
        drawRoundRect(
            color = Color(0xFFE1E6EE),
            topLeft = Offset((size.width - trackWidth) / 2f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f)
        )
        drawRoundRect(
            color = Color(0xFF8A9BB5),
            topLeft = Offset((size.width - thumbWidth) / 2f, current.thumbTopPx),
            size = Size(thumbWidth, current.thumbHeightPx),
            cornerRadius = CornerRadius(thumbWidth / 2f)
        )
    }
}

internal data class MessageScrollbarMetrics(
    val thumbTopPx: Float,
    val thumbHeightPx: Float,
    val thumbTravelPx: Float,
    val maximumScrollOffsetPx: Float
) {
    val canScroll: Boolean get() = thumbTravelPx > 0f && maximumScrollOffsetPx > 0f

    fun scrollOffsetForThumb(thumbTopPx: Float): Float =
        if (canScroll) {
            (thumbTopPx / thumbTravelPx).coerceIn(0f, 1f) * maximumScrollOffsetPx
        } else {
            0f
        }
}

internal fun calculateMessageScrollbarMetrics(
    itemCount: Int,
    rowHeightPx: Float,
    rowSpacingPx: Float,
    viewportHeightPx: Float,
    trackHeightPx: Float,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    minimumThumbPx: Float
): MessageScrollbarMetrics {
    val track = trackHeightPx.coerceAtLeast(0f)
    val contentHeight = itemCount.coerceAtLeast(0) * rowHeightPx +
        (itemCount - 1).coerceAtLeast(0) * rowSpacingPx
    if (itemCount <= 0 || viewportHeightPx <= 0f || contentHeight <= viewportHeightPx || track <= 0f) {
        return MessageScrollbarMetrics(0f, track, 0f, 0f)
    }
    val maximumOffset = contentHeight - viewportHeightPx
    val thumbHeight = (track * viewportHeightPx / contentHeight)
        .coerceIn(minimumThumbPx.coerceIn(0f, track), track)
    val travel = track - thumbHeight
    val offset = (firstVisibleItemIndex * (rowHeightPx + rowSpacingPx) +
        firstVisibleItemScrollOffset).coerceIn(0f, maximumOffset)
    return MessageScrollbarMetrics(
        thumbTopPx = travel * offset / maximumOffset,
        thumbHeightPx = thumbHeight,
        thumbTravelPx = travel,
        maximumScrollOffsetPx = maximumOffset
    )
}
