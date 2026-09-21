package com.sinelynx.grindingrobot.core.tcp

import java.io.ByteArrayOutputStream

/** 有 requestId 的响应仅按请求分组；其余响应沿用任务与版本。 */
internal data class TaskPathChunkKey(val requestId: String, val taskId: String, val pathVersion: Int)

/** 只负责收齐并拼接字节，不检查业务结果或过滤历史版本，JSON 交由独立解析器处理。 */
internal class TaskPathChunkAssembler {
    private val assemblies = mutableMapOf<TaskPathChunkKey, Assembly>()

    fun append(
        taskId: String,
        pathVersion: Int,
        chunkIndex: Int,
        totalChunks: Int,
        data: ByteArray,
        requestId: String = ""
    ): ByteArray? {
        if (totalChunks <= 0 || chunkIndex !in 0 until totalChunks) return null
        // 旧 PathPlanRequest 仍可能附带无 request_id 的分片，仅此类响应沿用任务/版本分组。
        val key = if (requestId.isNotEmpty()) TaskPathChunkKey(requestId, "", 0)
            else TaskPathChunkKey("", taskId, pathVersion)
        // 同键的总片数发生变化时重新开始，避免把两种分片布局混在一起。
        val current = assemblies[key]?.takeIf { it.totalChunks == totalChunks }
            ?: Assembly(totalChunks).also { assemblies[key] = it }
        // 按序号覆盖重复片，并复制输入，避免接收缓冲复用修改已经收集的数据。
        current.chunks[chunkIndex] = data.copyOf()
        if (current.chunks.size < totalChunks) return null

        // UTF-8 字符可能跨片，必须先按序号拼完整字节，不能逐片转字符串。
        val output = ByteArrayOutputStream()
        for (index in 0 until totalChunks) {
            output.write(current.chunks[index] ?: return null)
        }
        assemblies.remove(key)
        return output.toByteArray()
    }

    private data class Assembly(
        val totalChunks: Int,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    )
}
