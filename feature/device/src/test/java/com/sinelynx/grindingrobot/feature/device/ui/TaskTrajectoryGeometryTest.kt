package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskMapTransformUiState
import com.sinelynx.grindingrobot.feature.device.viewmodel.TaskPoseUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 设备页机器人/轨迹与共享规划路径投影的一致性测试，不依赖截图或真实地图。
 * 使用非零原点、地图朝向、不同宽高缩放比和显示旋转/手势，避免只在默认地图上看似对齐。
 * previewScale 特意设为非 1，验证尺寸比生效后不会重复乘它；越界点丢弃而不是吸附地图边缘。
 */
class TaskTrajectoryGeometryTest {
    @Test fun robotAndPathShareProjectionUnderRotationAndUnequalBitmapScaling() {
        val transform = transform(1000, 500, 0.1f, headingDeg = 90f)
            .copy(alignmentYawDeg = 20f, rotationAlignmentDeltaDeg = 5f, previewScaleX = 0.2f, previewScaleY = 0.1f)
        val geo = com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo(1000, 500, 0.1f,
            10.0, 20.0, 90f, 1)
        val robot = pose(-10f, 30f).toTaskBitmapPoint(transform, 200, 50)!!
        val path = com.sinelynx.grindingrobot.feature.map.ui.taskPathPointToBitmapPoint(
            com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload(-10f, 30f),
            geo, 1000 to 500, 200 to 50)!!
        assertOffsetEquals(robot, path)
        val robotDisplay = robot.toTaskDrawOffset(calculateImageBounds(400f, 300f, 200, 50),
            200, 50, transform.totalRotationDeg).transformBy(2f, Offset(13f, 17f))
        val pathDisplay = com.sinelynx.grindingrobot.feature.map.ui.taskBitmapPointToFittedPoint(
            path, androidx.compose.ui.geometry.Size(400f, 300f), 200 to 50, transform.totalRotationDeg)!!
            .transformBy(2f, Offset(13f, 17f))
        assertOffsetEquals(robotDisplay, pathDisplay)
    }

    @Test fun robotDirectionCancelsMapHeadingThenUsesDisplayRotation() {
        val transform = transform(100, 50, 0.1f, headingDeg = 30f)
            .copy(alignmentYawDeg = 20f, rotationAlignmentDeltaDeg = 5f)
        // 图标朝上基准 90 - (世界航向 90 - 地图朝向 30) + 显示旋转 (20 + 5) = 55。
        assertEquals(55f, taskRobotRotationDeg(90f, transform), EPSILON)
    }

    @Test
    fun worldPointMapsThroughOriginalMapSizeToBitmapPixels() {
        val transform = transform(width = 1_000, height = 500, resolution = 0.1f)
        val pose = pose(x = 60f, y = 45f)

        val result = pose.toTaskBitmapPoint(transform, bitmapWidth = 200, bitmapHeight = 50)

        assertOffsetEquals(Offset(100f, 25f), result)
    }

    @Test
    fun bitmapAspectRatioDefinesFittedDrawBounds() {
        val bounds = calculateImageBounds(
            containerWidth = 300f,
            containerHeight = 300f,
            imageWidth = 200,
            imageHeight = 50
        )
        val center = Offset(100f, 25f).toTaskDrawOffset(
            bounds = bounds,
            bitmapWidth = 200,
            bitmapHeight = 50
        )

        assertEquals(0f, bounds.left, EPSILON)
        assertEquals(112.5f, bounds.top, EPSILON)
        assertEquals(300f, bounds.width, EPSILON)
        assertEquals(75f, bounds.height, EPSILON)
        assertOffsetEquals(Offset(150f, 150f), center)
    }

    @Test
    fun mapHeadingIsAppliedBeforeBitmapScaling() {
        val transform = transform(
            width = 1_000,
            height = 500,
            resolution = 0.1f,
            headingDeg = 90f
        )
        val pose = pose(x = -10f, y = 30f)

        val result = pose.toTaskBitmapPoint(transform, bitmapWidth = 1_000, bitmapHeight = 500)

        assertOffsetEquals(Offset(100f, 300f), result)
    }

    @Test
    fun drawPointUsesSameRotationThenZoomAndPanAsBitmap() {
        val bounds = calculateImageBounds(300f, 300f, imageWidth = 400, imageHeight = 100)

        val result = Offset.Zero
            .toTaskDrawOffset(bounds, bitmapWidth = 400, bitmapHeight = 100, rotationDeg = 90f)
            .transformBy(zoom = 2f, pan = Offset(10f, 20f))

        assertOffsetEquals(Offset(385f, 20f), result)
    }

    @Test
    fun pointOutsideOriginalMapIsRejectedInsteadOfClampedToEdge() {
        val transform = transform(width = 1_000, height = 500, resolution = 0.1f)

        val result = pose(x = 111f, y = 20f)
            .toTaskBitmapPoint(transform, bitmapWidth = 200, bitmapHeight = 100)

        assertNull(result)
    }

    private fun transform(
        width: Int,
        height: Int,
        resolution: Float,
        headingDeg: Float = 0f
    ) = TaskMapTransformUiState(
        width = width,
        height = height,
        resolution = resolution,
        originX = 10.0,
        originY = 20.0,
        headingDeg = headingDeg,
        previewScaleX = 1f,
        previewScaleY = 1f
    )

    private fun pose(x: Float, y: Float) = TaskPoseUiItem(
        x = x,
        y = y,
        headingDeg = 0f,
        lineColorArgb = 0
    )

    private fun assertOffsetEquals(expected: Offset, actual: Offset?) {
        requireNotNull(actual)
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
