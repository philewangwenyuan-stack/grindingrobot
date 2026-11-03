package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.ui.geometry.Offset
import com.sinelynx.grindingrobot.core.dxf.PointD
import com.sinelynx.grindingrobot.feature.map.viewmodel.MapGeo
import kotlin.math.cos
import kotlin.math.sin

/** Inverse of worldPointToBitmapOffset, before viewport/display transforms. */
internal fun bitmapOffsetToMapWorld(point:Offset,geo:MapGeo,bitmapWidth:Int,bitmapHeight:Int):PointD {
    require(bitmapWidth>0&&bitmapHeight>0&&geo.resolution>0)
    val x=point.x.toDouble()*geo.mapWidth/bitmapWidth*geo.resolution
    val y=(geo.mapHeight-point.y.toDouble()*geo.mapHeight/bitmapHeight)*geo.resolution
    val angle=Math.toRadians(geo.headingDeg.toDouble());val c=cos(angle);val s=sin(angle)
    return PointD(geo.originX+c*x-s*y,geo.originY+s*x+c*y)
}
