package com.sinelynx.grindingrobot.feature.device.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.sinelynx.grindingrobot.feature.device.viewmodel.*
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Paired software Canvas timings, never claimed as tablet GPU frame rate. */
class TaskTrajectoryRenderBenchmarkTest {
    @Test fun exportPairedSoftwareRenderingAndPreview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("robotTrajectoryBenchmark")=="true")
        val directory=File(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),"trajectory-performance").apply{mkdirs()}
        val map=TaskMapTransformUiState(1000,800,.1f,0.0,0.0,0f,1f,1f)
        val bitmap=Bitmap.createBitmap(800,640,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap.asImageBitmap());val scope=CanvasDrawScope()
        File(directory,"android-render-raw.csv").bufferedWriter().use { csv ->
            csv.appendLine("samples,stage,iteration,variant,duration_ns,vertices,draw_calls")
            for(size in listOf(1000,10000,50000)) {
                val input=(0 until size).map { index ->
                    val count=size/40;val row=index/count;val t=(index%count).toFloat()/(count-1)
                    TaskPoseUiItem(10f+80f*(if(row%2==0)t else 1f-t),10f+row*1.5f,0f,0xff2aaf67.toInt())
                }
                val accumulator=TaskTrajectoryAccumulator();var scene=TaskTrajectorySnapshot.Empty
                input.forEach { scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,it,0f,0f) }
                val old500=input.takeLast(500).map { it.toTaskBitmapPoint(map,1000,800)!! }
                val oldFull=input.map { it.toTaskBitmapPoint(map,1000,800)!! }
                val warm=TaskTrajectoryDrawingCache().apply { update(scene,map,1000,800) }
                for(stage in listOf("render_rebuild","render_pan")) {
                    fun execute(variant:String, sample:Int) {
                        scope.draw(Density(1f),LayoutDirection.Ltr,canvas,Size(800f,640f)) {
                            drawRect(Color.White)
                            val angle=(sample%5)*3f;val zoom=1f+(sample%3)*.05f;val pan=Offset(-(sample%4)*2f,0f)
                            if(variant=="new_full") {
                                val cache=if(stage=="render_pan")warm else TaskTrajectoryDrawingCache()
                                val transform=map.copy(alignmentYawDeg=angle)
                                cache.update(scene,transform,1000,800)
                                cache.draw(this,transform,1000,800,zoom,pan)
                            } else {
                                val points=if(stage=="render_pan") {
                                    if(variant=="old500")old500 else oldFull
                                } else {
                                    (if(variant=="old500")input.takeLast(500) else input).map { it.toTaskBitmapPoint(map,1000,800)!! }
                                }
                                legacy(points,angle,zoom,pan)
                            }
                        }
                    }
                    val variants=listOf("old500","new_full","old_full")
                    repeat(5){sample->variants.forEach { execute(it,sample) }}
                    repeat(21){sample->
                        for(variant in if(sample%2==0)variants else variants.reversed()) {
                            val start=System.nanoTime();execute(variant,sample);val duration=System.nanoTime()-start
                            val vertices=when(variant){"old500"->500;"old_full"->size;else->scene.vertexCount}
                            val draws=if(variant=="new_full")warm.drawRunCount else vertices*2-1
                            csv.appendLine("$size,$stage,$sample,$variant,$duration,$vertices,$draws")
                        }
                    }
                    csv.flush()
                }
                if(size==50000) {
                    for(variant in listOf("old500","new_full")) {
                        scope.draw(Density(1f),LayoutDirection.Ltr,canvas,Size(800f,640f)) {
                            drawRect(Color.White)
                            // Reference grid only; this is synthetic route evidence, not a robot screenshot.
                            for(x in 0..800 step 40)drawLine(Color(0xffe7ebf0),Offset(x.toFloat(),0f),Offset(x.toFloat(),640f))
                            for(y in 0..640 step 40)drawLine(Color(0xffe7ebf0),Offset(0f,y.toFloat()),Offset(800f,y.toFloat()))
                            if(variant=="new_full")warm.draw(this,map,1000,800) else legacy(old500,0f,1f,Offset.Zero)
                        }
                        File(directory,"$variant.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    }
                }
            }
        }
        bitmap.recycle()
    }

    private fun DrawScope.legacy(points:List<Offset>,angle:Float,zoom:Float,pan:Offset) {
        val bounds=calculateImageBounds(size.width,size.height,1000,800)
        val width=.9f*bounds.width/1000f/.1f*zoom
        fun project(point:Offset)=point.toTaskDrawOffset(bounds,1000,800,angle)*zoom+pan
        for(i in 1 until points.size)drawLine(Color(0xff2aaf67),project(points[i-1]),project(points[i]),width,StrokeCap.Round)
        points.forEach { drawCircle(Color(0xff2aaf67),width/2f,project(it)) }
    }
}
