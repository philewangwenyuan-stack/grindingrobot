package com.sinelynx.grindingrobot.feature.device.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sinelynx.grindingrobot.feature.device.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class TaskTrajectoryRenderTest {
    private val map = TaskMapTransformUiState(1000,800,.1f,0.0,0.0,0f,1f,1f)
    private fun pose(x: Float,y: Float,color: Int=0xff2aaf67.toInt()) = TaskPoseUiItem(x,y,0f,color)
    private fun render(scene: TaskTrajectorySnapshot, transform: TaskMapTransformUiState=map,
                       zoom: Float=1f, pan: Offset=Offset.Zero, cache: TaskTrajectoryDrawingCache=TaskTrajectoryDrawingCache()): android.graphics.Bitmap {
        val bitmap=android.graphics.Bitmap.createBitmap(800,640,android.graphics.Bitmap.Config.ARGB_8888)
        cache.update(scene,transform,1000,800)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(bitmap.asImageBitmap()),Size(800f,640f)) {
            drawRect(Color.White);cache.draw(this,transform,1000,800,zoom,pan)
        }
        return bitmap
    }

    @Test fun fullTraceKeepsOriginAfterTenThousandSamplesAndGestureReusesProjection() {
        val accumulator=TaskTrajectoryAccumulator()
        var scene=TaskTrajectorySnapshot.Empty
        repeat(10_001) { scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,pose(10f+it*.008f,40f),0f,0f) }
        val cache=TaskTrajectoryDrawingCache()
        val bitmap=render(scene,cache=cache)
        assertEquals(0xff2aaf67.toInt(),bitmap.getPixel(80,320))
        assertEquals(0xff2aaf67.toInt(),bitmap.getPixel(720,320))
        val projected=cache.projectedVertexCount
        render(scene,map.copy(alignmentYawDeg=30f),1.5f,Offset(-20f,15f),cache).recycle()
        assertEquals(projected,cache.projectedVertexCount)
        assertEquals(1,cache.drawRunCount)
        bitmap.recycle()
    }

    @Test fun invalidMapPointCreatesGapAndNeverJoinsAcrossIt() {
        val accumulator=TaskTrajectoryAccumulator(toleranceM=0.0)
        var scene=TaskTrajectorySnapshot.Empty
        listOf(pose(10f,40f),pose(20f,40f),pose(-10f,40f),pose(70f,40f),pose(80f,40f)).forEach {
            scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,it,0f,0f)
        }
        val bitmap=render(scene)
        assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(400,320))
        assertEquals(0xff2aaf67.toInt(),bitmap.getPixel(120,320))
        bitmap.recycle()
    }

    @Test fun latestPassGeometryCoversEarlierPassAtCrossing() {
        val accumulator=TaskTrajectoryAccumulator(toleranceM=0.0)
        listOf(pose(10f,40f),pose(90f,40f)).forEach { accumulator.accept("task",DeviceTaskUiState.RUNNING,it,0f,0f) }
        accumulator.breakContinuity()
        accumulator.accept("task",DeviceTaskUiState.RUNNING,pose(50f,10f,0xff4881f4.toInt()),0f,0f)
        val scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,pose(50f,70f,0xff4881f4.toInt()),0f,0f)
        val bitmap=render(scene)
        assertEquals(0xff4881f4.toInt(),bitmap.getPixel(400,320))
        bitmap.recycle()
    }

    @Test fun nativeBandMatchesOriginalScalarGeometryUnderMapAndViewportTransforms() {
        val input=listOf(pose(10f,20f),pose(30f,20f),pose(30f,40f),pose(60f,40f),pose(60f,60f))
        val accumulator=TaskTrajectoryAccumulator(toleranceM=0.0)
        var scene=TaskTrajectorySnapshot.Empty
        input.forEach { scene=accumulator.accept("task",DeviceTaskUiState.RUNNING,it,0f,0f) }
        for ((angle,zoom,pan) in listOf(Triple(0f,1f,Offset.Zero),Triple(30f,1.2f,Offset(-20f,-15f)),Triple(-45f,1.1f,Offset(10f,5f)))) {
            val transform=map.copy(alignmentYawDeg=angle)
            val actual=render(scene,transform,zoom,pan)
            val reference=android.graphics.Bitmap.createBitmap(800,640,android.graphics.Bitmap.Config.ARGB_8888)
            val bounds=calculateImageBounds(800f,640f,1000,800)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(reference.asImageBitmap()),Size(800f,640f)) {
                drawRect(Color.White)
                val points=input.map { it.toTaskBitmapPoint(transform,1000,800)!!.toTaskDrawOffset(bounds,1000,800,angle)*zoom+pan }
                val width=.9f*bounds.width/transform.width/transform.resolution*zoom
                for(i in 1 until points.size) drawLine(Color(input[i].lineColorArgb),points[i-1],points[i],width,StrokeCap.Round)
                points.forEach { drawCircle(Color(input[0].lineColorArgb),width/2,it) }
            }
            var mismatch=0;var painted=0
            for(y in 0 until 640) for(x in 0 until 800) {
                val a=actual.getPixel(x,y);val b=reference.getPixel(x,y)
                if(a!=android.graphics.Color.WHITE || b!=android.graphics.Color.WHITE) painted++
                if(listOf(16,8,0).any { kotlin.math.abs(((a shr it) and 255)-((b shr it) and 255))>48 }) mismatch++
            }
            assertTrue("angle=$angle mismatch=$mismatch painted=$painted",mismatch.toDouble()/painted.coerceAtLeast(1)<.025)
            actual.recycle();reference.recycle()
        }
    }
}
