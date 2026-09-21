package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathPointPayload
import com.sinelynx.grindingrobot.core.model.state.TaskPathSegmentPayload
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.*
import sl_link.SlLink

/**
 * 宽松读取完整路径 JSON：辅助元数据不作为丢弃实际路径的依据。
 * 坐标、统计和摘要分别读取；无法解码时也返回空结果，让本轮等待立即结束。
 */
internal object TaskPathJsonParser {
    fun parse(
        bytes: ByteArray,
        response: SlLink.PathPointPlanResponse,
        onDecodeFailure: ((Exception) -> Unit)? = null
    ): TaskPathPayload {
        // 外层元数据用于 JSON 缺字段或解码失败时的回退；统计 float 没有 presence，不能拿默认 0 填空。
        val envelope = TaskPathPayload(
            taskId = response.taskId, pathVersion = response.pathVersion,
            frameId = response.frameId.ifEmpty { "map" }, alignmentYawDeg = 0f, points = emptyList(),
            planned = response.planned, result = response.result.name, message = response.message,
            pathPointCount = response.pathPointCount,
            pathLengthM = response.pathLengthM.takeIf { it.isFinite() } ?: 0f,
            requestId = response.requestId, mapId = response.mapId, mapVersion = response.mapVersion
        )
        val root = try {
            val json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            Json.parseToJsonElement(json) as? JsonObject ?: error("路径JSON不是对象")
        } catch (error: Exception) {
            onDecodeFailure?.invoke(error)
            return envelope.copy(message = envelope.message.ifBlank { "路径规划未返回数据" })
        }
        // 保持数组顺序及无效坐标占位；删掉占位会把原本断开的两侧点误连。
        val points = root.array("points").mapIndexed { index, item ->
            val point = item as? JsonObject ?: JsonObject(emptyMap())
            TaskPathPointPayload(
                x = point.number("x") ?: Float.NaN, y = point.number("y") ?: Float.NaN,
                index = point.int("index") ?: index, pathScope = point.string("path_scope").orEmpty(),
                pathCategory = point.string("path_category").orEmpty(),
                regionId = point.string("region_id").orEmpty(), lapIndex = point.int("lap_index") ?: 0
            )
        }
        // 摘要缺失不影响 points；缺少区间的段用 -1 表示，交给绘制层回退到点的 scope 分组。
        val segments = root.array("segments").mapIndexedNotNull { index, item ->
            val segment = item as? JsonObject ?: return@mapIndexedNotNull null
            TaskPathSegmentPayload(
                segmentIndex = segment.int("segment_index") ?: index,
                pathScope = segment.string("path_scope").orEmpty(),
                pathCategory = segment.string("path_category").orEmpty(),
                regionId = segment.string("region_id").orEmpty(), lapIndex = segment.int("lap_index") ?: 0,
                fromRegionId = segment.string("from_region_id").orEmpty(),
                toRegionId = segment.string("to_region_id").orEmpty(),
                startPointIndex = segment.int("start_point_index") ?: -1,
                endPointIndex = segment.int("end_point_index") ?: -1,
                pointCount = segment.int("point_count") ?: 0
            )
        }
        // 业务元数据宽松回退；关联标识始终保留包络 request_id，不校验内外层字段一致性。
        return envelope.copy(
            taskId = root.string("task_id") ?: envelope.taskId,
            pathVersion = root.int("path_version") ?: envelope.pathVersion,
            frameId = root.string("frame_id") ?: envelope.frameId,
            mapId = root.string("map_id") ?: envelope.mapId,
            mapVersion = root.int("map_version") ?: envelope.mapVersion,
            alignmentYawDeg = root.number("alignment_yaw") ?: 0f,
            points = points, segments = segments,
            planned = root.primitive("planned")?.booleanOrNull ?: envelope.planned,
            result = root.string("result") ?: envelope.result,
            message = root.string("message") ?: envelope.message,
            pathPointCount = root.int("path_point_count") ?: envelope.pathPointCount,
            pathLengthM = root.number("path_length_m") ?: envelope.pathLengthM,
            totalWorkAreaM2 = root.number("total_work_area_m2"),
            estimatedTimeS = root.number("estimated_time_s")
        )
    }

    private fun JsonObject.primitive(key: String) = this[key] as? JsonPrimitive
    private fun JsonObject.string(key: String) = primitive(key)?.contentOrNull
    private fun JsonObject.int(key: String) = string(key)?.toIntOrNull()
    // 缺字段、null、空串、非数值及无穷值均视为缺失；0 和负数仍保留给展示层处理。
    private fun JsonObject.number(key: String) = string(key)?.toFloatOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
}
