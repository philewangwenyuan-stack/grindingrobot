package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.TaskPathPayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import sl_link.SlLink

class TaskPathReceivePerformanceBenchmarkTest {
    private data class Completed(val nanos: Long, val payload: TaskPathPayload)

    @Test fun exportReceiveBlockingAndResultReadyTimes() = runBlocking {
        assumeTrue(System.getenv("ROBOT_PATH_BENCHMARK") == "1")
        val directory = File(requireNotNull(System.getenv("ROBOT_PATH_BENCHMARK_OUTPUT"))).apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val results = Channel<Completed>(1)
        val decoder = TaskPathDecodeQueue(scope, { results.trySend(Completed(System.nanoTime(), it)) })
        try {
            File(directory, "jvm-receive-raw.csv").bufferedWriter().use { csv ->
                csv.appendLine("environment,stage,points,segments,json_bytes,iteration,variant,batch_ops,duration_ns")
                for (size in listOf(10000, 50000, 100000)) {
                    val bytes = jsonPath(size)
                    val envelope = SlLink.PathPointPlanResponse.newBuilder().setTaskId("benchmark")
                        .setRequestId("benchmark-$size").setPathVersion(1).build()
                    suspend fun execute(variant: String): Pair<Long, Long> {
                        val started = System.nanoTime()
                        if (variant == "old") {
                            val parsed = TaskPathJsonParser.parse(bytes, envelope)
                            val completed = System.nanoTime()
                            assertEquals(size, parsed.points.size)
                            return (completed - started) to (completed - started)
                        }
                        assertTrue(decoder.submit(bytes, envelope))
                        val receiverFree = System.nanoTime()
                        val completed = withTimeout(60_000) { results.receive() }
                        assertEquals(size, completed.payload.points.size)
                        return (receiverFree - started) to (completed.nanos - started)
                    }
                    repeat(5) { execute("old"); execute("new") }
                    repeat(21) { sample ->
                        for (variant in if (sample % 2 == 0) listOf("old", "new") else listOf("new", "old")) {
                            val (blocking, ready) = execute(variant)
                            csv.appendLine("JVM,receive_block,$size,${size / 100},${bytes.size},$sample,$variant,1,$blocking")
                            csv.appendLine("JVM,json_result_ready,$size,${size / 100},${bytes.size},$sample,$variant,1,$ready")
                        }
                    }
                    csv.flush()
                }
            }
        } finally { decoder.close(); scope.cancel() }
    }

    private fun jsonPath(size: Int): ByteArray = buildString(size * 120) {
        append("{\"task_id\":\"benchmark\",\"path_version\":1,\"points\":[")
        repeat(size) { index ->
            if (index > 0) append(',')
            val row = index / 100
            val fraction = (index % 100) / 99f
            val x = 10f + 80f * (if (row % 2 == 0) fraction else 1f - fraction)
            val y = 10f + 80f * row / (size / 100 - 1)
            append("{\"index\":$index,\"x\":$x,\"y\":$y,\"path_scope\":\"")
            append(if (row % 10 == 0) "between_regions" else "within_region")
            append("\"}")
        }
        append("],\"segments\":[")
        repeat(size / 100) { row ->
            if (row > 0) append(',')
            append("{\"start_point_index\":${row * 100},\"end_point_index\":${row * 100 + 99},\"path_scope\":\"")
            append(if (row % 10 == 0) "between_regions" else "within_region")
            append("\"}")
        }
        append("]}")
    }.encodeToByteArray()
}
