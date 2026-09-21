package com.sinelynx.grindingrobot.core.model.state

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * 建图缓存的模型级测试，不依赖 Android 图片解码。
 * 依次验证冻结时复制字节、迟到实时帧不覆盖、显示旋转不改变原始几何，以及新会话清理。
 * 这些约束保护 Step2/3/4 共用同一坐标基准；其他流程不应借用此建图单例。
 */
class MapBuildSessionStreamTest {
    @After fun clear() { MapBuildSessionStream.clear(); MapImageStream.reset() }

    // 图片字节仅用于验证复制与隔离；图片解码由页面入口负责，不属于会话缓存测试。
    private fun frame() = MapImagePayload(byteArrayOf(1, 2), 100, 50, 0.1f, 12.0, -3.0, 30f,
        alignmentYawDeg = 10f, rotationAlignmentDeltaDeg = 5f, previewScaleX = 0.5f, previewScaleY = 0.5f)

    @Test fun freezesCopyAndIgnoresLateLiveFrames() {
        MapBuildSessionStream.begin()
        val original = frame()
        assertTrue(MapBuildSessionStream.freeze(null, original))
        // 分别模拟源字节被改写和迟到实时帧；两者都不能改变已冻结的编辑坐标基准。
        original.imageBytes[0] = 99
        MapImageStream.publish(frame().copy(originX = 88.0))
        val cached = MapBuildSessionStream.snapshotFor(null)!!.frame
        assertArrayEquals(byteArrayOf(1, 2), cached.imageBytes)
        assertEquals(12.0, cached.originX, 0.0)
        assertEquals(0.5f, cached.previewScaleX, 0f)
    }

    @Test fun rotationDoesNotModifyImageOrOriginalGeometry() {
        MapBuildSessionStream.begin()
        MapBuildSessionStream.freeze("map_a", frame())
        MapBuildSessionStream.updateRotation("map_a", -45f)
        val cached = MapBuildSessionStream.snapshotFor("map_a")!!.frame
        assertArrayEquals(byteArrayOf(1, 2), cached.imageBytes)
        assertEquals(12.0, cached.originX, 0.0)
        assertEquals(30f, cached.headingDeg, 0f)
        assertEquals(-45f, cached.alignmentYawDeg + cached.rotationAlignmentDeltaDeg, 0f)
        assertNull(MapBuildSessionStream.snapshotFor("map_b"))
    }

    @Test fun newSessionAndClearDiscardFrozenFrame() {
        MapBuildSessionStream.begin()
        MapBuildSessionStream.freeze(null, frame())
        val session = MapBuildSessionStream.snapshotFor(null)!!.sessionId
        MapBuildSessionStream.begin()
        assertNull(MapBuildSessionStream.snapshotFor(null))
        MapBuildSessionStream.freeze(null, frame())
        assertNotEquals(session, MapBuildSessionStream.snapshotFor(null)!!.sessionId)
        MapBuildSessionStream.clear()
        assertNull(MapBuildSessionStream.snapshot.value)
    }

    @Test fun invalidFramesCannotBeFrozen() {
        assertFalse(MapBuildSessionStream.freeze(null, frame().copy(imageBytes = byteArrayOf())))
        assertFalse(MapBuildSessionStream.freeze(null, frame().copy(resolution = Float.NaN)))
        assertFalse(MapBuildSessionStream.freeze(null, frame().copy(mapWidth = 0)))
    }
}
