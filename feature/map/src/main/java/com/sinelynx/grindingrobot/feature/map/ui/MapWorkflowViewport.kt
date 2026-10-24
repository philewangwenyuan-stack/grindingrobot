package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sinelynx.grindingrobot.core.model.state.MapBuildSessionStream
import com.sinelynx.grindingrobot.core.model.state.MapBuildViewport
import java.util.Locale
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapScreenStep4UiState

internal fun MapBuildViewport.toTransform(size: IntSize, image: IntSize, rotation: Float): MapViewportTransform {
    if (size.width <= 0 || size.height <= 0 || image.width <= 0 || image.height <= 0) return MapViewportTransform(zoom)
    val fit = previewImageRect(Size(size.width.toFloat(), size.height.toFloat()), image.width, image.height)
    val rawCenter = Offset(fit.left + centerX * fit.width, fit.top + centerY * fit.height)
    val rotatedCenter = rotatePoint(rawCenter, fit.center, rotation)
    return MapViewportTransform(zoom, Offset(size.width / 2f, size.height / 2f) - rotatedCenter * zoom)
}

internal fun MapViewportTransform.toMapCenter(size: IntSize, image: IntSize, rotation: Float): MapBuildViewport {
    if (size.width <= 0 || size.height <= 0 || image.width <= 0 || image.height <= 0) return MapBuildViewport(scale)
    val fit = previewImageRect(Size(size.width.toFloat(), size.height.toFloat()), image.width, image.height)
    val point = rotatePoint(screenToModel(Offset(size.width / 2f, size.height / 2f)), fit.center, -rotation)
    return MapBuildViewport(scale, (point.x - fit.left) / fit.width, (point.y - fit.top) / fit.height)
}

@Stable
internal class WorkflowViewport(private val mapId: String?, initial: MapBuildViewport) {
    var view by mutableStateOf(initial)
        private set
    fun reset() { update(MapBuildViewport()) }
    private fun update(value: MapBuildViewport) {
        view = value
        MapBuildSessionStream.updateViewport(mapId, value)
    }
    fun gesture(size: IntSize, image: IntSize, rotation: Float, centroid: Offset, pan: Offset, zoom: Float) {
        if (size.width <= 0 || size.height <= 0 || image.width <= 0 || image.height <= 0 ||
            !zoom.isFinite() || zoom <= 0f || !pan.x.isFinite() || !pan.y.isFinite()) return
        val canvas = Size(size.width.toFloat(), size.height.toFloat())
        val fit = previewImageRect(canvas, image.width, image.height)
        val bounds = rotatedRectBounds(fit, fit.center, rotation)
        update(view.toTransform(size, image, rotation).zoomAndPan(centroid, pan, zoom, canvas, bounds)
            .toMapCenter(size, image, rotation))
    }
}

@Composable
internal fun rememberWorkflowViewport(mapId: String?): WorkflowViewport =
    remember(mapId) { WorkflowViewport(mapId, MapBuildSessionStream.viewportFor(mapId)) }

internal fun Modifier.workflowViewportSemantics(viewport: WorkflowViewport, size: IntSize,
    image: IntSize, rotation: Float): Modifier = testTag("mapping_viewport").semantics {
    progressBarRangeInfo = ProgressBarRangeInfo(viewport.view.zoom, 1f..8f)
    stateDescription = String.format(Locale.US, "地图缩放 %.1f 倍", viewport.view.zoom)
    setProgress { value ->
        if (value.isFinite()) {
            viewport.gesture(size, image, rotation, Offset(size.width / 2f, size.height / 2f),
                Offset.Zero, value.coerceIn(1f, 8f) / viewport.view.zoom)
            true
        } else false
    }
}

@Composable
internal fun WorkflowZoomControl(viewport: WorkflowViewport, modifier: Modifier = Modifier) {
    TextButton(onClick = viewport::reset,
        modifier = modifier.background(Color.White, RoundedCornerShape(12.dp)).testTag("mapping_zoom_reset")) {
        Text(String.format(Locale.US, "%.1f× · 复位", viewport.view.zoom), color = Color(0xFF202937))
    }
}

/** Map, paths, regions and robot share the same gesture transform in the save preview. */
@Composable
internal fun WorkflowMapPreview(state: MapScreenStep4UiState, bitmap: ImageBitmap, mapId: String?) {
    val workflow = rememberWorkflowViewport(mapId)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val image = IntSize(bitmap.width, bitmap.height)
    val rotation = (state.mapGeo?.alignmentYawDeg ?: 0f) + (state.mapGeo?.rotationAlignmentDeltaDeg ?: 0f)
    val transform = workflow.view.toTransform(size, image, rotation)
    Box(Modifier.fillMaxSize().background(Color(0xFFDEE5EE)).onSizeChanged { size = it }
        .testTag("mapping_viewport")
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(transform.scale, 1f..8f)
            stateDescription = String.format(Locale.US, "地图缩放 %.1f 倍", transform.scale)
            setProgress { value ->
                if (value.isFinite()) {
                    workflow.gesture(size, image, rotation, Offset(size.width / 2f, size.height / 2f),
                        Offset.Zero, value.coerceIn(1f, 8f) / workflow.view.zoom)
                    true
                } else false
            }
        }
        .pointerInput(image, rotation, size) {
            detectTransformGestures { center, pan, zoom, _ ->
                workflow.gesture(size, image, rotation, center, pan, zoom)
            }
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = transform.scale; scaleY = transform.scale
            translationX = transform.offset.x; translationY = transform.offset.y
        }) {
            Image(bitmap, "地图图片", Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation })
            Step4MapOverlays(state, state.bitmapDecodeSize ?: (bitmap.width to bitmap.height), rotation)
        }
        WorkflowZoomControl(workflow, Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}
