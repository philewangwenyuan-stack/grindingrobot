package com.sinelynx.grindingrobot.feature.map.viewmodel

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** Frozen bitmap -> world projection, shared by rectangle and brush submission. */
internal fun compileEraseOutput(areas: List<EraseRectArea>, bitmapSize: Pair<Int, Int>,
    mapSize: Pair<Int, Int>, geo: MapGeo, alignmentYaw: Float): EditOutput {
    require(bitmapSize.first > 0 && bitmapSize.second > 0 && mapSize.first > 0 && mapSize.second > 0)
    val radians = Math.toRadians(geo.headingDeg.toDouble())
    val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
    val yaw = Math.toRadians(-alignmentYaw.toDouble())
    val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
    val center = Offset(bitmapSize.first / 2f, bitmapSize.second / 2f)
    fun world(p: Offset): Offset {
        val xm = p.x * mapSize.first / bitmapSize.first * geo.resolution
        val ym = (geo.mapHeight - p.y * mapSize.second / bitmapSize.second) * geo.resolution
        return Offset((geo.originX + c * xm - s * ym).toFloat(), (geo.originY + s * xm + c * ym).toFloat())
    }
    return EditOutput(areas.filter { it.confirmed }.flatMap { area ->
        val polygons = area.bitmapPolygons.ifEmpty {
            listOf(if (area.alignedCorners.size == 4) area.alignedCorners.map {
                val p = it - center
                center + Offset(p.x * cy - p.y * sy, p.x * sy + p.y * cy)
            } else listOf(Offset(area.left, area.top), Offset(area.right, area.top),
                Offset(area.right, area.bottom), Offset(area.left, area.bottom)))
        }
        polygons.mapIndexedNotNull { part, polygon ->
            if (polygon.size < 3) return@mapIndexedNotNull null
            val points = polygon.map(::world)
            EraseAreaWorldOutput(area.id, points[3 % points.size], points[2], points[1], points[0], points, part)
        }
    })
}
