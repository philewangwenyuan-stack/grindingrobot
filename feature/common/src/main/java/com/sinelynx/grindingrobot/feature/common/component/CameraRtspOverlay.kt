package com.sinelynx.grindingrobot.feature.common.component

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

private val CameraOverlayWidth = 326.dp
private val CameraOverlayHeight = 233.dp
private const val RtspMinBufferMs = 300
private const val RtspMaxBufferMs = 1_000
private const val RtspBufferForPlaybackMs = 100
private const val RtspBufferForPlaybackAfterRebufferMs = 300

/**
 * 可拖动的 RTSP 实时画面浮窗。
 *
 * [modifier] 用于指定浮窗可活动的父容器范围。浮窗关闭并重新进入组合后，会回到由
 * [initialTopPadding] 和 [initialEndPadding] 指定的右上角初始位置。
 */
@Composable
fun CameraRtspOverlay(
    streamUrl: String?,
    initialTopPadding: Dp = 16.dp,
    initialEndPadding: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }

    val topPaddingPx = with(density) { initialTopPadding.toPx() }
    val endPaddingPx = with(density) { initialEndPadding.toPx() }
    val initialOffset = calculateInitialCameraOverlayOffset(
        containerSize = containerSize,
        overlaySize = overlaySize,
        topPaddingPx = topPaddingPx,
        endPaddingPx = endPaddingPx
    )
    val displayedOffset = clampCameraOverlayOffset(
        requested = dragOffset ?: initialOffset,
        containerSize = containerSize,
        overlaySize = overlaySize
    )

    LaunchedEffect(containerSize, overlaySize) {
        if (containerSize != IntSize.Zero && overlaySize != IntSize.Zero) {
            dragOffset = clampCameraOverlayOffset(
                requested = dragOffset ?: initialOffset,
                containerSize = containerSize,
                overlaySize = overlaySize
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = displayedOffset.x.roundToInt(),
                        y = displayedOffset.y.roundToInt()
                    )
                }
                .size(width = CameraOverlayWidth, height = CameraOverlayHeight)
                .onSizeChanged { overlaySize = it },
            shape = RoundedCornerShape(16.dp),
            color = Color.Black,
            shadowElevation = 8.dp,
            border = BorderStroke(4.dp, Color.White)
        ) {
            Box(Modifier.fillMaxSize()) {
                RtspVideoView(
                    streamUrl = streamUrl,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 13.dp)
                        .height(24.dp)
                        .widthIn(min = 80.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD82B2A))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                    )
                    Text(
                        text = "实时视角",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 覆盖在 PlayerView 上方，确保从视频、标签或边框内侧都可以发起拖动。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerSize, overlaySize, initialTopPadding, initialEndPadding) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val currentOffset = dragOffset ?: initialOffset
                                dragOffset = clampCameraOverlayOffset(
                                    requested = currentOffset + dragAmount,
                                    containerSize = containerSize,
                                    overlaySize = overlaySize
                                )
                            }
                        }
                )
            }
        }
    }
}

internal fun calculateInitialCameraOverlayOffset(
    containerSize: IntSize,
    overlaySize: IntSize,
    topPaddingPx: Float,
    endPaddingPx: Float
): Offset = clampCameraOverlayOffset(
    requested = Offset(
        x = containerSize.width - overlaySize.width - endPaddingPx,
        y = topPaddingPx
    ),
    containerSize = containerSize,
    overlaySize = overlaySize
)

internal fun clampCameraOverlayOffset(
    requested: Offset,
    containerSize: IntSize,
    overlaySize: IntSize
): Offset {
    val maxX = (containerSize.width - overlaySize.width).coerceAtLeast(0).toFloat()
    val maxY = (containerSize.height - overlaySize.height).coerceAtLeast(0).toFloat()
    return Offset(
        x = requested.x.coerceIn(0f, maxX),
        y = requested.y.coerceIn(0f, maxY)
    )
}

@Composable
private fun RtspVideoView(
    streamUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember {
        val lowLatencyLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                RtspMinBufferMs,
                RtspMaxBufferMs,
                RtspBufferForPlaybackMs,
                RtspBufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(lowLatencyLoadControl)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(player, playerView, mainHandler) {
        onDispose {
            playerView.player = null
            val playerToRelease = player
            mainHandler.post {
                runCatching {
                    playerToRelease.stop()
                    playerToRelease.clearMediaItems()
                    playerToRelease.release()
                }
            }
        }
    }

    LaunchedEffect(streamUrl) {
        playerView.player = player
        player.stop()
        player.clearMediaItems()
        if (!streamUrl.isNullOrBlank()) {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier,
        update = { it.player = player }
    )
}
