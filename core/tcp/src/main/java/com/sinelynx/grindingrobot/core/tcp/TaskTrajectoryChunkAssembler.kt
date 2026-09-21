package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryMapPayload
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPagePayload
import com.sinelynx.grindingrobot.core.model.state.TaskTrajectoryPointPayload
import sl_link.SlLink
import java.io.ByteArrayOutputStream
import java.util.TreeMap

internal sealed interface TaskTrajectoryChunkResult {
    data object Pending : TaskTrajectoryChunkResult
    data class Complete(val payload: TaskTrajectoryPagePayload) : TaskTrajectoryChunkResult
    data class Invalid(val message: String) : TaskTrajectoryChunkResult
}

/** 组装一次 0x0532 请求对应的全部 0x0533 协议分块。 */
internal class TaskTrajectoryChunkAssembler {
    private data class Assembly(
        val executionId: String,
        val startIndex: Int,
        val totalChunks: Int,
        val chunks: TreeMap<Int, SlLink.TaskTrajectoryChunk> = TreeMap()
    )

    private var assembly: Assembly? = null

    fun append(chunk: SlLink.TaskTrajectoryChunk): TaskTrajectoryChunkResult {
        if (chunk.totalChunks <= 0) {
            clear()
            return TaskTrajectoryChunkResult.Invalid("任务轨迹分块总数无效")
        }
        if (chunk.chunkIndex !in 0 until chunk.totalChunks) {
            clear()
            return TaskTrajectoryChunkResult.Invalid("任务轨迹分块序号无效")
        }

        val current = assembly
        val target = if (current == null) {
            Assembly(chunk.executionId, chunk.startIndex, chunk.totalChunks).also { assembly = it }
        } else {
            if (current.executionId != chunk.executionId ||
                current.startIndex != chunk.startIndex ||
                current.totalChunks != chunk.totalChunks
            ) {
                clear()
                return TaskTrajectoryChunkResult.Invalid("任务轨迹分块元数据不一致")
            }
            current
        }

        target.chunks[chunk.chunkIndex] = chunk
        if (target.chunks.size < target.totalChunks) return TaskTrajectoryChunkResult.Pending
        if ((0 until target.totalChunks).any { it !in target.chunks }) {
            return TaskTrajectoryChunkResult.Pending
        }

        val orderedChunks = target.chunks.values.toList()
        val metadata = orderedChunks.first()
        val mapMetadata = orderedChunks.firstOrNull {
            it.mapAvailable || it.mapImageData.size() > 0
        } ?: metadata
        val imageResult = assembleMapImage(orderedChunks)
        if (imageResult is MapImageResult.Invalid) {
            clear()
            return TaskTrajectoryChunkResult.Invalid(imageResult.message)
        }
        val imageBytes = (imageResult as MapImageResult.Complete).bytes
        val points = orderedChunks
            .flatMap { it.pointsList }
            .associateBy { it.index }
            .toSortedMap()
            .values
            .map { point ->
                TaskTrajectoryPointPayload(
                    index = point.index,
                    offsetMs = point.offsetMs,
                    xMeters = point.xMm / 1000f,
                    yMeters = point.yMm / 1000f,
                    headingDeg = point.headingMdeg / 1000f,
                    linearSpeedMps = point.linearSpeedMmps / 1000f,
                    angularSpeedRadps = point.angularSpeedMradps / 1000f,
                    discSpeedRpm = point.discSpeedRpm,
                    speedAvailable = point.speedAvailable,
                    discEnabled = point.discEnabled,
                    taskStateValue = point.taskStateValue
                )
            }
        val payload = TaskTrajectoryPagePayload(
            isSuccess = true,
            message = metadata.message,
            executionId = metadata.executionId,
            taskId = metadata.taskId,
            mapId = metadata.mapId,
            totalPointCount = orderedChunks.maxOf { it.totalPointCount },
            returnedPointCount = points.size,
            startIndex = metadata.startIndex,
            nextIndex = orderedChunks.maxOf { it.nextIndex },
            hasMore = orderedChunks.any { it.hasMore },
            startedAtMs = metadata.startedAtMs,
            startTime = metadata.startTime,
            endTime = metadata.endTime,
            sampleStep = metadata.sampleStep.coerceAtLeast(1),
            points = points,
            map = TaskTrajectoryMapPayload(
                available = mapMetadata.mapAvailable,
                message = mapMetadata.mapMessage,
                version = mapMetadata.mapVersion,
                sourceWidth = mapMetadata.mapSourceWidth,
                sourceHeight = mapMetadata.mapSourceHeight,
                resolution = mapMetadata.mapResolution,
                originX = mapMetadata.mapOrigin.x,
                originY = mapMetadata.mapOrigin.y,
                originHeadingDeg = mapMetadata.mapOrigin.headingDeg,
                frameId = mapMetadata.mapFrameId,
                imageFormat = mapMetadata.mapImageFormat,
                imageWidth = mapMetadata.mapImageWidth,
                imageHeight = mapMetadata.mapImageHeight,
                previewScaleX = mapMetadata.mapPreviewScaleX,
                previewScaleY = mapMetadata.mapPreviewScaleY,
                imageBytes = imageBytes,
                alignmentYawDeg = mapMetadata.alignmentYawDeg,
                appRotationDeg = mapMetadata.appRotationDeg,
                rotationAlignmentDeltaDeg = mapMetadata.rotationAlignmentDeltaDeg
            )
        )
        clear()
        return TaskTrajectoryChunkResult.Complete(payload)
    }

    fun clear() {
        assembly = null
    }

    private sealed interface MapImageResult {
        data class Complete(val bytes: ByteArray) : MapImageResult
        data class Invalid(val message: String) : MapImageResult
    }

    private fun assembleMapImage(chunks: List<SlLink.TaskTrajectoryChunk>): MapImageResult {
        val totalImageChunks = chunks.maxOf { it.mapImageTotalChunks }
        if (totalImageChunks <= 0) return MapImageResult.Complete(byteArrayOf())

        val imageChunks = buildMap<Int, SlLink.TaskTrajectoryChunk> {
            chunks.forEach { chunk ->
                val existing = get(chunk.mapImageChunkIndex)
                if (existing == null ||
                    (existing.mapImageData.isEmpty && !chunk.mapImageData.isEmpty)
                ) {
                    put(chunk.mapImageChunkIndex, chunk)
                }
            }
        }
        if ((0 until totalImageChunks).any { it !in imageChunks }) {
            return MapImageResult.Invalid("任务轨迹地图图片分块缺失")
        }
        val output = ByteArrayOutputStream()
        repeat(totalImageChunks) { index ->
            output.write(imageChunks.getValue(index).mapImageData.toByteArray())
        }
        return MapImageResult.Complete(output.toByteArray())
    }
}
