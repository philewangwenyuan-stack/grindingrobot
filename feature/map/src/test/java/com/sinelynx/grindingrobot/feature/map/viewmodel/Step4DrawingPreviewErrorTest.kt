package com.sinelynx.grindingrobot.feature.map.viewmodel

import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Step4DrawingPreviewErrorTest {
    private fun path() = TaskPathPayload(taskId = "", pathVersion = 1, frameId = "map",
        alignmentYawDeg = 0f, points = listOf(TaskPathPointPayload(1f, 2f)), drawingDraftVersion = 7)

    @Test fun planningFailureTakesPrecedenceOverDefaultVersionZero() {
        val reason = "全局路径或车体包络与图纸地基相交，无法生成安全路径"
        val failed = path().copy(points = emptyList(), planned = false, result = "RESULT_FAILED",
            message = reason, drawingDraftVersion = 0)
        assertEquals(reason, step4PathError(true, failed, 7))
    }

    @Test fun successfulPreviewStillRequiresMatchingDraftVersion() {
        assertNull(step4PathError(true, path(), 7))
        assertEquals("图纸草稿与规划版本不一致，请重新预览", step4PathError(true, path(), 8))
    }

    @Test fun sendFailureAndMissingResponseKeepTheirOwnErrors() {
        assertEquals("路径规划请求发送失败", step4PathError(false, null, 7))
        assertEquals("路径规划未返回数据", step4PathError(true, null, 7))
    }
}
