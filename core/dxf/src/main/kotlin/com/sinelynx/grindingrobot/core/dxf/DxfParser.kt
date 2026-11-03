package com.sinelynx.grindingrobot.core.dxf

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.*

enum class DxfUnits(val mm: Double) { UNKNOWN(1.0), MILLIMETRES(1.0), CENTIMETRES(10.0), METRES(1000.0), INCHES(25.4), FEET(304.8) }
data class DxfLayer(val name: String, val color: Int, val visible: Boolean = true)
data class DxfEntity(val id: String, val layer: String, val block: String?, val closed: Boolean,
    val pointsMm: List<PointD>, val cornersMm: List<PointD> = pointsMm,
    val approximationMm: Double = 0.0) {
    val bounds = RectD.of(pointsMm)
}
data class DxfDrawing(val units: DxfUnits, val layers: List<DxfLayer>, val entities: List<DxfEntity>,
    val warnings: List<String>, val sourceEntityCount: Int, val encoding: String) {
    val extentMm = if(entities.isEmpty())RectD(0.0,0.0,0.0,0.0)else RectD(entities.minOf {it.bounds.left},entities.minOf {it.bounds.bottom},entities.maxOf {it.bounds.right},entities.maxOf {it.bounds.top})
    fun withUnits(unit: DxfUnits): DxfDrawing {
        require(unit != DxfUnits.UNKNOWN)
        val factor=unit.mm/units.mm
        return copy(units=unit, entities=entities.map { e -> e.copy(pointsMm=e.pointsMm.map { it*factor },
            cornersMm=e.cornersMm.map { it*factor }, approximationMm=e.approximationMm*factor) })
    }
}
class DxfParseException(message: String): IllegalArgumentException(message)

/** ASCII DXF reader, independent of Android. INSERT expansion never calls application/UI code. */
object DxfParser {
    const val MAX_BYTES = 50 * 1024 * 1024
    private data class Record(val type: String, val groups: List<Pair<Int,String>>) {
        fun text(code: Int, default: String="") = groups.firstOrNull { it.first==code }?.second ?: default
        fun number(code: Int, default: Double=0.0) = text(code).toDoubleOrNull() ?: default
        fun integer(code: Int, default: Int=0) = text(code).toIntOrNull() ?: default
    }
    private data class Block(val base: PointD, val records: List<Record>)
    private data class Matrix(val a: Double=1.0,val b: Double=0.0,val c: Double=0.0,val d: Double=1.0,val x: Double=0.0,val y: Double=0.0) {
        fun point(p: PointD)=PointD(a*p.x+c*p.y+x,b*p.x+d*p.y+y)
        fun then(q: Matrix)=Matrix(a*q.a+c*q.b,b*q.a+d*q.b,a*q.c+c*q.d,b*q.c+d*q.d,a*q.x+c*q.y+x,b*q.x+d*q.y+y)
        val maxScale get() = sqrt(a*a+b*b+c*c+d*d)
    }
    fun parse(input: InputStream, checkCancelled: ()->Unit = {}, unitsOverride: DxfUnits? = null): DxfDrawing {
        val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(32*1024)
        while(true) { checkCancelled(); val n=input.read(buffer); if(n<0)break
            if(output.size()+n>MAX_BYTES)throw DxfParseException("图纸超过50 MB上限")
            output.write(buffer,0,n)
        }
        val bytes=output.toByteArray()
        if(bytes.take(24).toByteArray().toString(Charsets.US_ASCII).startsWith("AutoCAD Binary DXF"))
            throw DxfParseException("不支持二进制DXF，请导出ASCII DXF")
        val header=bytes.take(128*1024).toByteArray().toString(Charsets.ISO_8859_1)
        val cp=Regex("\\\$DWGCODEPAGE\\s+3\\s+([^\\r\\n]+)").find(header)?.groupValues?.get(1)?.trim().orEmpty()
        var encoding="UTF-8"
        val text=try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
        catch(_: java.nio.charset.CharacterCodingException) {
            encoding=when(cp.uppercase()) { "ANSI_936"->"GBK"; "ANSI_950"->"Big5"; "ANSI_1252"->"windows-1252"; else->throw DxfParseException("图纸编码无法识别，请导出UTF-8 DXF") }
            Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
        val records=mutableListOf<Record>(); val groups=mutableListOf<Pair<Int,String>>(); var type=""
        val lines=text.removePrefix("\uFEFF").lineSequence().iterator(); var count=0
        while(lines.hasNext()) {
            if(++count%2048==0)checkCancelled()
            val codeLine=lines.next().trim(); if(!lines.hasNext()) { if(codeLine.isNotBlank())throw DxfParseException("DXF组码不完整"); break }
            val value=lines.next().trim(); val code=codeLine.toIntOrNull() ?: throw DxfParseException("无效DXF组码")
            if(code==0) { if(type.isNotEmpty())records.add(Record(type,groups.toList())); groups.clear(); type=value }
            else groups.add(code to value)
        }
        if(type.isNotEmpty())records.add(Record(type,groups.toList()))
        val unitCode=Regex("\\\$INSUNITS\\s+70\\s+([0-9]+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val units=unitsOverride ?: when(unitCode) { 1->DxfUnits.INCHES;2->DxfUnits.FEET;4->DxfUnits.MILLIMETRES;5->DxfUnits.CENTIMETRES;6->DxfUnits.METRES;else->DxfUnits.UNKNOWN }
        val layers=linkedMapOf<String,DxfLayer>(); val blocks=linkedMapOf<String,Block>(); val model=mutableListOf<Record>()
        var section=""; var i=0
        while(i<records.size) {
            val r=records[i++]
            if(r.type=="SECTION") { section=r.text(2); continue }
            if(r.type=="ENDSEC") { section="";continue }
            if(section=="TABLES"&&r.type=="LAYER") {
                val aci=abs(r.integer(62,7)); val palette=listOf(0x202937,0xD82B2A,0xDDAA00,0x24BE1C,0x00A0E9,0x2466FF,0x8956DD,0x202937)
                layers[r.text(2)]=DxfLayer(r.text(2),palette.getOrElse(aci){0x647080},r.integer(62,7)>0 && r.integer(70)and 1==0)
            }
            if(section=="BLOCKS"&&r.type=="BLOCK") {
                val contents=mutableListOf<Record>()
                while(i<records.size&&records[i].type!="ENDBLK") contents.add(records[i++])
                blocks[r.text(2)]=Block(PointD(r.number(10),r.number(20)),contents)
            }
            if(section=="ENTITIES"&&r.type !in setOf("ENDSEC","EOF"))model.add(r)
        }
        if(records.none { it.type=="SECTION" })throw DxfParseException("不是有效的ASCII DXF文件")
        val entities=mutableListOf<DxfEntity>(); val skipped=linkedMapOf<String,Int>(); val warnings=mutableListOf<String>()
        var expandedPoints=0L
        fun addEntity(entity:DxfEntity) {
            expandedPoints+=entity.pointsMm.size
            if(entities.size>=200000 || expandedPoints>1000000)throw DxfParseException("图元展开超过20万或100万边界点，请精简DXF")
            if(entity.pointsMm.any { !it.finite() })throw DxfParseException("图元变换后的坐标无效")
            entities.add(entity)
        }
        fun skip(typeName: String){ skipped[typeName]=(skipped[typeName]?:0)+1 }
        fun expand(items: List<Record>, transform: Matrix, inheritedLayer: String?, blockName: String?, prefix: String, depth: Int) {
            if(depth>8){skip("图块嵌套超过8层");return}
            var cursor=0
            while(cursor<items.size) {
                checkCancelled();val r=items[cursor++];if(r.integer(67)==1||r.text(410).let { it.isNotEmpty()&&it!="Model" })continue
                val layer=r.text(8,"0").let { if(it=="0")inheritedLayer?:it else it }
                if(layer !in layers)layers[layer]=DxfLayer(layer,0x647080)
                val id="$prefix${r.text(5, (cursor-1).toString())}"
                if(abs(r.number(210))>1e-8||abs(r.number(220))>1e-8){skip("倾斜OCS:${r.type}");continue}
                val mirror=if(r.number(230,1.0)<0)Matrix(a=-1.0)else Matrix()
                if(r.type=="INSERT") {
                    val name=r.text(2); if(name.startsWith("*Paper_Space"))continue
                    val block=blocks[name];if(block==null){skip("缺失图块:$name");continue}
                    val columns=if(r.number(44)==0.0)1 else r.integer(70,1).coerceAtLeast(1)
                    val rows=if(r.number(45)==0.0)1 else r.integer(71,1).coerceAtLeast(1)
                    if(columns.toLong()*rows>10000){skip("图块阵列过大");continue}
                    val angle=Math.toRadians(r.number(50)); val sx=r.number(41,1.0);val sy=r.number(42,1.0)
                    for(row in 0 until rows)for(col in 0 until columns) {
                        // MINSERT grid spacing rotates with the block but is not scaled.
                        val ox=col*r.number(44);val oy=row*r.number(45)
                        val m=Matrix(cos(angle)*sx,sin(angle)*sx,-sin(angle)*sy,cos(angle)*sy,
                            r.number(10)+cos(angle)*ox-sin(angle)*oy,r.number(20)+sin(angle)*ox+cos(angle)*oy)
                            .then(Matrix(x=-block.base.x,y=-block.base.y))
                        expand(block.records,transform.then(mirror).then(m),layer,name,"$id/$row,$col/",depth+1)
                    };continue
                }
                val m=transform.then(mirror);var closed=false;var error=0.0
                val corners=mutableListOf<PointD>();val points=mutableListOf<PointD>()
                when(r.type) {
                    "LINE"->{points.add(PointD(r.number(10),r.number(20)));points.add(PointD(r.number(11),r.number(21)));corners.addAll(points)}
                    "LWPOLYLINE","POLYLINE"->{
                        val vertices=mutableListOf<Pair<PointD,Double>>();closed=r.integer(70)and 1!=0
                        if(r.type=="POLYLINE") {
                            if(r.integer(70)and (8 or 16 or 64)!=0){skip("三维POLYLINE");while(cursor<items.size&&items[cursor].type=="VERTEX")cursor++;continue}
                            while(cursor<items.size&&items[cursor].type=="VERTEX") { val v=items[cursor++];vertices.add(PointD(v.number(10),v.number(20)) to v.number(42)) }
                        } else {
                            var x:Double?=null;var y=0.0;var bulge=0.0
                            for((code,value)in r.groups)when(code) {
                                10->{x?.let { vertices.add(PointD(it,y) to bulge) };x=value.toDoubleOrNull();y=0.0;bulge=0.0}
                                20->y=value.toDoubleOrNull()?:0.0
                                42->bulge=value.toDoubleOrNull()?:0.0
                            };x?.let { vertices.add(PointD(it,y) to bulge) }
                        }
                        corners.addAll(vertices.map { it.first })
                        for(v in vertices.indices) {
                            val (p,bulge)=vertices[v];points.add(p)
                            if((closed||v<vertices.lastIndex)&&abs(bulge)>1e-10) {
                                val q=vertices[(v+1)%vertices.size].first;val delta=q-p
                                val center=(p+q)*0.5+PointD(-delta.y,delta.x)*((1-bulge*bulge)/(4*bulge))
                                val radius=p.distance(center);val sweep=4*atan(bulge)
                                val n=arcCount(radius*m.maxScale*units.mm,abs(sweep));val start=atan2(p.y-center.y,p.x-center.x)
                                for(k in 1 until n) {val a=start+sweep*k/n;points.add(center+PointD(cos(a),sin(a))*radius)};error=5.0
                            }
                        }
                    }
                    "CIRCLE","ARC","ELLIPSE"->{
                        val center=PointD(r.number(10),r.number(20));val ellipse=r.type=="ELLIPSE"
                        val major=if(ellipse)PointD(r.number(11),r.number(21))else PointD(r.number(40),0.0)
                        val minor=PointD(-major.y,major.x)*(if(ellipse)r.number(40,1.0)else 1.0)
                        val start=if(ellipse)r.number(41)else Math.toRadians(r.number(50))
                        var sweep=if(ellipse)r.number(42,2*PI)-start else if(r.type=="CIRCLE")2*PI else Math.toRadians(r.number(51))-start
                        while(sweep<=0)sweep+=2*PI
                        closed=r.type=="CIRCLE"||abs(sweep-2*PI)<1e-8
                        val n=arcCount(max(hypot(major.x,major.y),hypot(minor.x,minor.y))*m.maxScale*units.mm,sweep)
                        for(k in 0..n) {val a=start+sweep*k/n;points.add(center+major*cos(a)+minor*sin(a))}
                        if(closed)points.removeAt(points.lastIndex);error=5.0
                    }
                    "HATCH"->{
                        hatchLoops(r,m.maxScale*units.mm,::skip).forEachIndexed { loop,boundary ->
                            addEntity(DxfEntity("$id/h$loop",layer,blockName,true,boundary.points.map {m.point(it)*units.mm},
                                boundary.corners.map {m.point(it)*units.mm},boundary.errorMm))
                        };continue
                    }
                    "VERTEX","SEQEND","ENDBLK"->continue
                    else->{skip(r.type);continue}
                }
                if(points.size<2||points.any { !it.finite() }){skip("无效${r.type}");continue}
                val result=points.map { m.point(it)*units.mm }
                addEntity(DxfEntity(id,layer,blockName,closed,result,corners.map { m.point(it)*units.mm },error))
            }
        }
        expand(model,Matrix(),null,null,"",0)
        if(units==DxfUnits.UNKNOWN)warnings.add("图纸单位未知，请确认单位后继续")
        if(encoding!="UTF-8")warnings.add("使用${encoding}读取，请核对中文图层名")
        skipped.forEach { (name,n)->warnings.add(if(name in setOf("TEXT","MTEXT","ATTRIB","ATTDEF","DIMENSION"))
            "已忽略标注 $name：$n（不参与地基边界）" else "未导入 $name：$n") }
        if(entities.isEmpty())throw DxfParseException("未找到支持的二维图元；请导出二维模型空间DXF")
        return DxfDrawing(units,layers.values.toList(),entities,warnings,model.size,encoding)
    }
    private data class HatchLoop(val points:List<PointD>,val corners:List<PointD>,val errorMm:Double)
    private fun hatchLoops(record:Record,scaleMm:Double,skip:(String)->Unit):List<HatchLoop> {
        val result=mutableListOf<HatchLoop>();val g=record.groups
        val starts=g.indices.filter {g[it].first==92}
        for((loop,start) in starts.withIndex()) {
            val end=starts.getOrNull(loop+1)?:g.size
            val fields=g.subList(start+1,end);val flags=g[start].second.toIntOrNull()?:0
            val points=mutableListOf<PointD>();val corners=mutableListOf<PointD>();var curved=false
            if(flags and 2!=0) {
                val header=Record("poly",fields)
                if(header.integer(73)!=1){skip("HATCH未闭合边界");continue}
                val count=header.integer(93);val vertices=mutableListOf<Pair<PointD,Double>>()
                var i=fields.indexOfFirst {it.first==93}+1
                while(i<fields.size&&vertices.size<count) {
                    if(fields[i].first!=10){i++;continue}
                    val x=fields[i++].second.toDouble();require(i<fields.size&&fields[i].first==20){"HATCH顶点缺少Y坐标"}
                    val y=fields[i++].second.toDouble();var bulge=0.0
                    if(i<fields.size&&fields[i].first==42)bulge=fields[i++].second.toDouble()
                    vertices.add(PointD(x,y) to bulge)
                }
                if(vertices.size!=count||count<3){skip("HATCH无效顶点");continue}
                corners.addAll(vertices.map {it.first})
                for(i in vertices.indices) {
                    val (a,bulge)=vertices[i];val b=vertices[(i+1)%vertices.size].first;points.add(a)
                    if(abs(bulge)>1e-10) {
                        val delta=b-a;val center=(a+b)*.5+PointD(-delta.y,delta.x)*((1-bulge*bulge)/(4*bulge))
                        val radius=a.distance(center);val sweep=4*atan(bulge);val angle=atan2(a.y-center.y,a.x-center.x)
                        val n=arcCount(radius*scaleMm,abs(sweep))
                        for(k in 1 until n)points.add(center+PointD(cos(angle+sweep*k/n),sin(angle+sweep*k/n))*radius)
                        curved=true
                    }
                }
            } else {
                val edgeStarts=fields.indices.filter {fields[it].first==72}
                val count=Record("edges",fields).integer(93)
                if(edgeStarts.size!=count||count<1){skip("HATCH无效边数");continue}
                var valid=true
                for((n,offset) in edgeStarts.withIndex()) {
                    val edge=Record("edge",fields.subList(offset+1,edgeStarts.getOrNull(n+1)?:fields.size))
                    val segment=when(fields[offset].second.toIntOrNull()) {
                        1->listOf(PointD(edge.number(10),edge.number(20)),PointD(edge.number(11),edge.number(21))).also {corners.addAll(it)}
                        2->{
                            curved=true
                            val ccw=edge.integer(73,1)!=0;val sign=if(ccw)1.0 else -1.0
                            val a=Math.toRadians(edge.number(50))*sign;val b=Math.toRadians(edge.number(51))*sign
                            var sweep=(b-a)%(2*PI)
                            if(ccw&&sweep<=0)sweep+=2*PI
                            if(!ccw&&sweep>=0)sweep-=2*PI
                            val radius=edge.number(40);val center=PointD(edge.number(10),edge.number(20))
                            val steps=arcCount(radius*scaleMm,abs(sweep))
                            (0..steps).map {k->val angle=a+sweep*k/steps;center+PointD(cos(angle),sin(angle))*radius}
                        }
                        else->{skip("HATCH不支持的边型${fields[offset].second}");valid=false;emptyList()}
                    }
                    if(!valid)break
                    val tolerance=.01/scaleMm
                    if(points.isEmpty())points.addAll(segment)
                    else if(points.last().distance(segment.first())<=tolerance)points.addAll(segment.drop(1))
                    else if(points.last().distance(segment.last())<=tolerance)points.addAll(segment.asReversed().drop(1))
                    else {valid=false;break}
                }
                if(!valid||points.size<4||points.last().distance(points.first())>.01/scaleMm){skip("HATCH不连续或未闭合边界");continue}
                points.removeAt(points.lastIndex)
            }
            if(points.size>=3&&points.all {it.finite()})result.add(HatchLoop(points,corners,if(curved)5.0 else 0.0))
            else skip("HATCH无效边界")
        }
        return result
    }
    private fun arcCount(radiusMm:Double,sweep:Double):Int {
        if(radiusMm<=0||!radiusMm.isFinite())throw DxfParseException("无效圆弧半径")
        val step=4*asin(sqrt((5.0/(2*radiusMm)).coerceIn(0.0,1.0)))
        val required=ceil(sweep/step)
        if(!required.isFinite() || required>10000)throw DxfParseException("圆弧离散超过1万段，请精简DXF")
        return required.toInt().coerceAtLeast(4)
    }
}
