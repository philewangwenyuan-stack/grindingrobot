package com.sinelynx.grindingrobot.feature.device.viewmodel

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in managed allocation/update benchmark; old cap500 is kept as the real baseline. */
class TaskTrajectoryPerformanceBenchmarkTest {
    @Volatile private var sink=0
    @Test fun exportUpdateAllocations() {
        assumeTrue(System.getenv("ROBOT_TRAJECTORY_BENCHMARK")=="1")
        val destination=File(requireNotNull(System.getenv("ROBOT_TRAJECTORY_BENCHMARK_OUTPUT"))).apply{mkdirs()}
        // Android's compile bootclasspath omits java.management; reflection is used
        // only in this opt-in host JVM test, without adding production dependencies.
        val type=Class.forName("com.sun.management.ThreadMXBean")
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        assumeTrue(type.getMethod("isThreadAllocatedMemorySupported").invoke(bean) as Boolean)
        type.getMethod("setThreadAllocatedMemoryEnabled",java.lang.Boolean.TYPE).invoke(bean,true)
        val thread=Thread.currentThread().id
        val readBytes=type.getMethod("getThreadAllocatedBytes",java.lang.Long.TYPE)
        fun allocated()=readBytes.invoke(bean,thread) as Long
        File(destination,"jvm-update-raw.csv").bufferedWriter().use { csv ->
            csv.appendLine("samples,iteration,variant,duration_ns,allocated_bytes,retained_vertices,coordinate_payload_bytes")
            for(size in listOf(1000,10000,50000)) {
                val input=(0 until size).map { index ->
                    val perRow=size/40;val row=index/perRow;val t=(index%perRow).toFloat()/(perRow-1)
                    TaskPoseUiItem(10f+80f*(if(row%2==0)t else 1f-t),10f+row*1.5f,0f,1)
                }
                fun execute(variant:String):Int {
                    if(variant=="old500") {
                        var recent=emptyList<TaskPoseUiItem>()
                        input.forEach { if(recent.lastOrNull()!=it) recent=(recent+it).takeLast(500) }
                        sink=recent.size;return sink
                    }
                    val accumulator=TaskTrajectoryAccumulator();var scene=TaskTrajectorySnapshot.Empty
                    input.forEach { scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,it,0f,0f) }
                    sink=scene.vertexCount;return sink
                }
                repeat(5){execute("old500");execute("new_full")}
                repeat(21){sample ->
                    for(variant in if(sample%2==0)listOf("old500","new_full") else listOf("new_full","old500")) {
                        val beforeBytes=allocated();val start=System.nanoTime()
                        val retained=execute(variant);val elapsed=System.nanoTime()-start
                        val bytes=allocated()-beforeBytes
                        csv.appendLine("$size,$sample,$variant,$elapsed,$bytes,$retained,${retained*(if(variant=="old500")16 else 8)}")
                    }
                }
                csv.flush()
            }
        }
    }
}
