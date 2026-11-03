package com.sinelynx.grindingrobot.core.dxf

import kotlin.math.*

data class PointD(val x: Double, val y: Double) {
    operator fun plus(p: PointD) = PointD(x + p.x, y + p.y)
    operator fun minus(p: PointD) = PointD(x - p.x, y - p.y)
    operator fun times(s: Double) = PointD(x * s, y * s)
    fun distance(p: PointD) = hypot(x - p.x, y - p.y)
    fun finite() = x.isFinite() && y.isFinite()
}
data class RectD(val left: Double, val bottom: Double, val right: Double, val top: Double) {
    val width get() = right - left
    val height get() = top - bottom
    fun contains(p: PointD) = p.x in left..right && p.y in bottom..top
    fun intersects(r: RectD) = left <= r.right && right >= r.left && bottom <= r.top && top >= r.bottom
    companion object {
        fun of(points: List<PointD>): RectD = if (points.isEmpty()) RectD(0.0, 0.0, 0.0, 0.0)
        else RectD(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}

/** Import preview uses CAD extents, independent of the radar map and registration. */
data class DrawingPreviewProjection(val bounds:RectD) {
    val width=max(1.0,bounds.width)
    val height=max(1.0,bounds.height)
    fun project(p:PointD)=PointD(p.x-bounds.left,bounds.top-p.y)
    fun fitScale(viewWidth:Double,viewHeight:Double)=min(viewWidth/width,viewHeight/height)*.94
}
data class ControlPair(val drawingMm: PointD, val mapM: PointD, val label: String = "")
data class DrawingTransform(
    val thetaDeg: Double = 0.0, val txM: Double = 0.0, val tyM: Double = 0.0,
    val scale: Double = 1.0, val rmsM: Double = 0.0, val measuredScale: Double = 1.0,
    val residualsM: List<Double> = emptyList(), val scaleLocked: Boolean = true
) {
    fun forward(mm: PointD): PointD {
        val a = Math.toRadians(thetaDeg); val p = mm * (scale / 1000.0)
        return PointD(cos(a) * p.x - sin(a) * p.y + txM, sin(a) * p.x + cos(a) * p.y + tyM)
    }
    fun inverse(m: PointD): PointD {
        require(scale.isFinite() && scale > 0)
        val a = Math.toRadians(thetaDeg); val p = m - PointD(txM, tyM)
        return PointD(cos(a) * p.x + sin(a) * p.y, -sin(a) * p.x + cos(a) * p.y) * (1000.0 / scale)
    }
    val blocked get() = !rmsM.isFinite() || rmsM > 0.20 || measuredScale !in 0.995..1.005
    val warning get() = !blocked && rmsM > 0.10
}
object Registration {
    /** Centering in Double also handles large survey coordinates without a Float round-trip. */
    fun solve(pairs: List<ControlPair>, lockScale: Boolean = true): DrawingTransform {
        require(pairs.size >= 3) { "至少添加三对控制点" }
        require(pairs.all { it.drawingMm.finite() && it.mapM.finite() }) { "控制点坐标无效" }
        val x = pairs.map { it.drawingMm * 0.001 }; val y = pairs.map { it.mapM }
        val cx = x.reduce(PointD::plus) * (1.0 / x.size); val cy = y.reduce(PointD::plus) * (1.0 / y.size)
        var a = 0.0; var b = 0.0; var variance = 0.0
        x.indices.forEach { i ->
            val p = x[i] - cx; val q = y[i] - cy
            a += p.x * q.x + p.y * q.y; b += p.x * q.y - p.y * q.x
            variance += p.x * p.x + p.y * p.y
        }
        require(variance > 1e-6 && hypot(a, b) > 1e-12) { "控制点重复或有效跨度不足" }
        val angle = atan2(b, a); val measured = hypot(a, b) / variance
        val scale = if (lockScale) 1.0 else measured
        val t = cy - PointD(cos(angle) * cx.x - sin(angle) * cx.y, sin(angle) * cx.x + cos(angle) * cx.y) * scale
        val fit = DrawingTransform(Math.toDegrees(angle), t.x, t.y, scale, measuredScale = measured, scaleLocked = lockScale)
        val errors = pairs.map { fit.forward(it.drawingMm).distance(it.mapM) }
        return fit.copy(rmsM = sqrt(errors.sumOf { it * it } / errors.size), residualsM = errors)
    }
}
object PolygonGeometry {
    fun area(p: List<PointD>): Double = abs(p.indices.sumOf { i -> val q = p[(i + 1) % p.size]; p[i].x * q.y - q.x * p[i].y }) * 0.5
    fun contains(p: List<PointD>, q: PointD): Boolean {
        var inside = false
        p.indices.forEach { i ->
            val a = p[i]; val b = p[(i + 1) % p.size]
            if ((a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
    fun distanceToSegment(p: PointD, a: PointD, b: PointD): Double {
        val d = b - a; val den = d.x * d.x + d.y * d.y
        if (den == 0.0) return p.distance(a)
        val t = (((p.x-a.x)*d.x + (p.y-a.y)*d.y)/den).coerceIn(0.0, 1.0)
        return p.distance(a + d * t)
    }
}
/** Immutable spatial index; construct on a background dispatcher. Radius is in drawing mm. */
class DrawingIndex(private val entities: List<DxfEntity>) {
    private val bins = HashMap<Pair<Int, Int>, MutableList<Int>>()
    private val large = mutableListOf<Int>()
    private val bounds = if(entities.isEmpty())RectD(0.0,0.0,0.0,0.0)else RectD(entities.minOf {it.bounds.left},entities.minOf {it.bounds.bottom},entities.maxOf {it.bounds.right},entities.maxOf {it.bounds.top})
    private val cell = max(1.0, max(bounds.width,bounds.height) / 64.0)
    init {
        entities.forEachIndexed { i, e ->
            val x0=floor(e.bounds.left/cell).toInt(); val x1=floor(e.bounds.right/cell).toInt()
            val y0=floor(e.bounds.bottom/cell).toInt(); val y1=floor(e.bounds.top/cell).toInt()
            if ((x1.toLong()-x0+1)*(y1.toLong()-y0+1)>4096) large.add(i)
            else for(x in x0..x1) for(y in y0..y1) bins.getOrPut(x to y){ mutableListOf() }.add(i)
        }
    }
    fun candidates(rect: RectD): List<DxfEntity> {
        val ids = large.toMutableSet()
        val x0=floor(rect.left/cell).toInt(); val x1=floor(rect.right/cell).toInt()
        val y0=floor(rect.bottom/cell).toInt(); val y1=floor(rect.top/cell).toInt()
        if ((x1.toLong()-x0+1)*(y1.toLong()-y0+1)>4096) return entities.filter { it.bounds.intersects(rect) }
        for(x in x0..x1) for(y in y0..y1) bins[x to y]?.let(ids::addAll)
        return ids.map(entities::get).filter { it.bounds.intersects(rect) }
    }
    fun snap(p: PointD, radiusMm: Double, visibleLayers: Set<String>): PointD? {
        val near=candidates(RectD(p.x-radiusMm,p.y-radiusMm,p.x+radiusMm,p.y+radiusMm)).filter {it.layer in visibleLayers}
        val targets=near.flatMap {it.cornersMm}.filter {it.distance(p)<=radiusMm}.toMutableList()
        // Real finite-segment intersections, never bounding-box corners or curve samples.
        val segments=near.asSequence().filter {it.approximationMm==0.0}.flatMap { e->
            (e.pointsMm+if(e.closed)e.pointsMm.take(1)else emptyList()).zipWithNext().asSequence()
        }.filter {(a,b)->PolygonGeometry.distanceToSegment(p,a,b)<=radiusMm}.take(128).toList()
        for(i in segments.indices)for(j in i+1 until segments.size) {
            val(a,b)=segments[i];val(c,d)=segments[j];val u=b-a;val v=d-c
            val den=u.x*v.y-u.y*v.x
            if(abs(den)<1e-12)continue
            val w=c-a;val t=(w.x*v.y-w.y*v.x)/den;val q=(w.x*u.y-w.y*u.x)/den
            if(t in 0.0..1.0&&q in 0.0..1.0){val at=a+u*t;if(at.distance(p)<=radiusMm)targets.add(at)}
        }
        return targets.minByOrNull {it.distance(p)}
    }
    fun hit(p: PointD, radiusMm: Double, visibleLayers: Set<String>): DxfEntity? =
        candidates(RectD(p.x-radiusMm,p.y-radiusMm,p.x+radiusMm,p.y+radiusMm)).asReversed()
            .firstOrNull { e -> e.layer in visibleLayers && (e.closed && PolygonGeometry.contains(e.pointsMm,p) ||
                (if(e.closed)e.pointsMm+e.pointsMm.first()else e.pointsMm).zipWithNext().any { (a,b) -> PolygonGeometry.distanceToSegment(p,a,b)<=radiusMm }) }
}
