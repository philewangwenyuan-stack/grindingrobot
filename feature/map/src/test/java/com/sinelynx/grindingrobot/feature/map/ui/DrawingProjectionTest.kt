package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapPreviewPointItem
import org.junit.Assert.*
import org.junit.Test

class DrawingProjectionTest {
    @Test fun projectionRoundTripsWithHeadingResolutionAndDecodedSize(){
        val geo=MapGeo(1200,800,.05f,-10.0,25.0,37f,3,alignmentYawDeg=90f)
        for(p in listOf(Offset(10f,20f),Offset(300f,250f),Offset(599f,399f))) {
            val w=bitmapOffsetToMapWorld(p,geo,600,400)
            val result=worldPointToBitmapOffset(MapPreviewPointItem(w.x.toFloat(),w.y.toFloat()),geo,1200 to 800,600 to 400)!!
            assertEquals(p.x,result.x,0.001f);assertEquals(p.y,result.y,0.001f)
        }
    }
}
