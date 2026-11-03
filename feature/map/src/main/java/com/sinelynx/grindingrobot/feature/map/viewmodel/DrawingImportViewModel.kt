package com.sinelynx.grindingrobot.feature.map.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.dxf.*
import com.sinelynx.grindingrobot.core.model.state.*
import com.sinelynx.grindingrobot.core.tcp.DrawingGateway
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import sl_link.SlLink
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

data class FoundationChoice(val ignored:Boolean=false,val marginM:Double=.30)
enum class DrawingTool { Pan, Control, MoveDrawing, Point, Box, Outline }
data class DrawingImportUiState(
    val sessionId:Long=0,val mapId:String="",val frame:MapImagePayload?=null,
    val step:Int=0,val busy:Boolean=false,val supported:Boolean=false,val error:String?=null,
    val fileName:String="",val sha:String="",val cachedFile:String="",val drawing:DxfDrawing?=null,
    val index:DrawingIndex?=null,val visible:Set<String> = emptySet(),
    val transform:DrawingTransform=DrawingTransform(),val controls:List<ControlPair> = emptyList(),
    val pendingDrawing:PointD?=null,val pendingMap:PointD?=null,val snapped:PointD?=null,
    val snapEnabled:Boolean=true,val tool:DrawingTool=DrawingTool.Pan,val opacity:Float=.65f,
    val selected:Map<String,FoundationChoice> = emptyMap(),val activeId:String?=null,
    val outline:List<PointD> = emptyList(),val defaultMargin:Double=.30,
    val checks:List<SlLink.DrawingCheckItem> = emptyList(),val checked:Boolean=false,
    val draftId:String="",val draftVersion:Int=0,val sourceVersion:Int=0,
    val focus:PointD?=null,val focusRevision:Int=0,val revision:Int=0,
    val canUndo:Boolean=false,val canRedo:Boolean=false,val existingCount:Int=0,
    val removeLayer:Boolean=false
) {
    val registrationReady get()=controls.size>=3&&!transform.blocked
    val canContinue get()=!busy && when(step) {
        0->supported&&drawing!=null&&drawing.units!=DxfUnits.UNKNOWN
        1->registrationReady
        2->selected.isNotEmpty()
        else->draftVersion>0&&checked&&checks.none { it.level=="error" }
    }
}
data class ConfirmedDrawingDraft(val sessionId:Long,val sourceMapId:String,val draftId:String,val version:Int,
    val payload:SlLink.DrawingLayerPayload,val targetMapId:String,val saveRequestId:String)
/** References are session-owned; a later map session cannot inherit an earlier draft. */
object DrawingWorkflowDrafts {
    private val values=java.util.concurrent.ConcurrentHashMap<Long,ConfirmedDrawingDraft>()
    fun get(mapId:String?):ConfirmedDrawingDraft? = MapBuildSessionStream.snapshotFor(mapId)?.let { values[it.sessionId] }
    fun put(value:ConfirmedDrawingDraft) { values[value.sessionId]=value;values.keys.filter { it!=value.sessionId }.forEach(values::remove) }
    fun remove(sessionId:Long) { values.remove(sessionId) }
}

@HiltViewModel
class DrawingImportViewModel @Inject constructor(
    @ApplicationContext private val context:Context,private val tcp:TcpManager,private val gateway:DrawingGateway
):ViewModel() {
    private val _state=MutableStateFlow(DrawingImportUiState());val state=_state.asStateFlow()
    private var fileJob:Job?=null;private var workJob:Job?=null;private var saveJob:Job?=null
    private var capability:SlLink.DrawingDraftResponse?=null
    private var frameFingerprint=""
    private var published:List<MapPreviewRegionPayload> = emptyList()
    private var stagedPayload:SlLink.DrawingLayerPayload?=null
    private data class Edit(val drawing:DxfDrawing?,val selected:Map<String,FoundationChoice>)
    private val undo=ArrayDeque<Edit>();private val redo=ArrayDeque<Edit>()
    private fun draftFile(mapId:String)=File(context.filesDir,"drawing-drafts/${sha(mapId.toByteArray())}.json")
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun open(mapId:String?) {
        val snapshot=MapBuildSessionStream.snapshotFor(mapId)
        if(snapshot==null){_state.update { it.copy(error="地图快照不存在，请返回扫描步骤") };return}
        if(_state.value.sessionId==snapshot.sessionId){_state.update {it.copy(frame=snapshot.frame)};return}
        val id=mapId?.takeIf { it.isNotBlank() }?:"LIVE_MAP"
        _state.value=DrawingImportUiState(sessionId=snapshot.sessionId,mapId=id,frame=snapshot.frame,busy=true)
        viewModelScope.launch {
            try {
                frameFingerprint=withContext(Dispatchers.Default){sha(snapshot.frame.imageBytes)}
                val cap=gateway.capabilities(id);check(cap.featureVersion>=1){"设备固件不支持图纸障碍"};capability=cap
                val regions=tcp.loadMapRegions(id);published=regions?.obstacleRegions.orEmpty().filter { it.source=="drawing" }
                _state.update { it.copy(busy=false,supported=true,sourceVersion=cap.layer.version,existingCount=published.size) }
                val saved=withContext(Dispatchers.IO){draftFile(id).takeIf(File::exists)?.readText(Charsets.UTF_8)?.let(::JSONObject)}
                val path=saved?.optString("file").orEmpty()
                if(path.isNotEmpty()&&File(path).exists()) loadCached(File(path),saved!!.optString("name"),saved.optString("sha"),saved)
                else if(cap.layer.sourceSha256.isNotEmpty()) {
                    val original=File(context.filesDir,"drawing-sources/${cap.layer.sourceSha256}.dxf")
                    if(original.exists())loadCached(original,cap.layer.sourceFile,cap.layer.sourceSha256,null)
                }
            }catch(c:CancellationException){throw c}
            catch(e:Exception){_state.update { it.copy(busy=false,error="${e.message?:"图纸功能查询超时"}；可跳过图纸继续") }}
        }
    }
    fun emergencyStop() { tcp.sendEmergencyStop() }
    fun clearError(){_state.update { it.copy(error=null) }}
    fun chooseFile(uri:Uri) {
        fileJob?.cancel();workJob?.cancel();fileJob=viewModelScope.launch {
            _state.update { it.copy(busy=true,error=null) }
            try {
                val fileInfo=withContext(Dispatchers.IO) {
                    val name=context.contentResolver.query(uri,null,null,null,null)?.use { c ->
                        if(c.moveToFirst())c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it>=0 }?.let(c::getString) else null
                    }?:"图纸.dxf"
                    require(name.endsWith(".dxf",true)){"仅支持DXF，请先将DWG导出为ASCII DXF"}
                    val dir=File(context.filesDir,"drawing-sources").apply { mkdirs() }
                    val temporary=File.createTempFile("import-",".dxf",dir)
                    try {
                        val digest=MessageDigest.getInstance("SHA-256");var size=0
                        context.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { out ->
                            val buffer=ByteArray(32768)
                            while(true){ensureActive();val n=input.read(buffer);if(n<0)break
                                size+=n;require(size<=DxfParser.MAX_BYTES){"图纸超过50 MB"};digest.update(buffer,0,n);out.write(buffer,0,n)}
                        } }
                        val hash=digest.digest().joinToString(""){"%02x".format(it)};val target=File(dir,"$hash.dxf")
                        if(!target.exists())check(temporary.renameTo(target)){"无法缓存图纸"}
                        Triple(target,name,hash)
                    }finally { temporary.delete() }
                }
                loadCached(fileInfo.first,fileInfo.second,fileInfo.third,null)
            }catch(c:CancellationException){throw c}
            catch(e:Exception){_state.update { it.copy(busy=false,error=e.message?:"图纸读取失败") }}
        }
    }
    private suspend fun loadCached(file:File,name:String,hash:String,saved:JSONObject?,unit:DxfUnits?=null) {
        val previous=_state.value
        withContext(Dispatchers.IO) {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input->val buffer=ByteArray(32768);while(true){ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)} }
            check(digest.digest().joinToString(""){"%02x".format(it)}==hash){"缓存图纸校验失败，请重新选择原图"}
        }
        val storedUnit=saved?.optString("units")?.takeIf { it.isNotBlank() }?.let {runCatching { DxfUnits.valueOf(it) }.getOrNull()}
        var parsed=withContext(Dispatchers.Default) { val job=currentCoroutineContext()[Job]
            file.inputStream().use { DxfParser.parse(it,{job?.ensureActive()},unit?:storedUnit) } }
        val frame=previous.frame?:return
        val sameMap=saved==null || capability?.layer?.status!=SlLink.DrawingLayerStatus.DRAWING_STALE&&saved.optInt("width",frame.mapWidth)==frame.mapWidth&&saved.optInt("height",frame.mapHeight)==frame.mapHeight&&
            kotlin.math.abs(saved.optDouble("resolution",frame.resolution.toDouble())-frame.resolution)<1e-6&&
            kotlin.math.abs(saved.optDouble("originX",frame.originX)-frame.originX)<1e-6&&kotlin.math.abs(saved.optDouble("originY",frame.originY)-frame.originY)<1e-6&&
            kotlin.math.abs(saved.optDouble("heading",frame.headingDeg.toDouble())-frame.headingDeg)<1e-6&&saved.optString("frameId",frame.frameId)==frame.frameId&&
            saved.optString("mapRevision",capability?.layer?.binding?.mapRevision.orEmpty())==capability?.layer?.binding?.mapRevision.orEmpty()
            &&(previous.mapId!="LIVE_MAP"||saved.optString("frameSha")==frameFingerprint)
        val center=PointD(frame.originX+frame.mapWidth*frame.resolution/2,frame.originY+frame.mapHeight*frame.resolution/2)
        val drawingCenter=PointD((parsed.extentMm.left+parsed.extentMm.right)/2,(parsed.extentMm.bottom+parsed.extentMm.top)/2)
        var fit=DrawingTransform(txM=center.x-drawingCenter.x/1000,tyM=center.y-drawingCenter.y/1000)
        var pairs=emptyList<ControlPair>();var selections=emptyMap<String,FoundationChoice>()
        val info=capability?.layer
        if(unit==null && info!=null && info.sourceSha256==hash && info.status!=SlLink.DrawingLayerStatus.DRAWING_STALE) {
            pairs=info.transform.controlPointsList.map { ControlPair(PointD(it.drawingXMm,it.drawingYMm),PointD(it.mapXM,it.mapYM),it.label) }
            if(pairs.size>=3)fit=Registration.solve(pairs,info.transform.scaleLocked)
            val existingIds=parsed.entities.map { it.id }.toSet()
            val manual=published.filter { it.drawingRef !in existingIds&&it.drawingRef.isNotBlank() }.map { r->
                DxfEntity(r.drawingRef,"手动描边",null,true,r.points.map { fit.inverse(PointD(it.x.toDouble(),it.y.toDouble())) }) }
            parsed=parsed.copy(entities=parsed.entities+manual,layers=parsed.layers+if(manual.isEmpty())emptyList()else listOf(DxfLayer("手动描边",0xFF9500)))
            selections=published.associate { it.drawingRef to FoundationChoice(it.drawingAction==1,it.inflationM.toDouble()) }
        }
        if(saved!=null && sameMap && saved.optInt("sourceVersion",-1)==previous.sourceVersion) {
            val manual=saved.optJSONArray("manual")?:JSONArray()
            val ids=parsed.entities.map {it.id}.toSet()
            val restored=(0 until manual.length()).map { n->val e=manual.getJSONObject(n);val points=e.getJSONArray("points")
                DxfEntity(e.getString("id"),"手动描边",null,true,(0 until points.length()).map { i->val p=points.getJSONArray(i);PointD(p.getDouble(0),p.getDouble(1)) }) }.filter { it.id !in ids }
            if(restored.isNotEmpty())parsed=parsed.copy(entities=parsed.entities+restored,layers=parsed.layers+if(parsed.layers.any {it.name=="手动描边"})emptyList()else listOf(DxfLayer("手动描边",0xFF9500)))
            val a=saved.optJSONArray("controls")?:JSONArray()
            pairs=(0 until a.length()).map { i->val p=a.getJSONArray(i);ControlPair(PointD(p.getDouble(0),p.getDouble(1)),PointD(p.getDouble(2),p.getDouble(3)),"P${i+1}") }
            if(pairs.size>=3)fit=Registration.solve(pairs,saved.optBoolean("locked",true))
            else fit=fit.copy(thetaDeg=saved.optDouble("theta",fit.thetaDeg),txM=saved.optDouble("tx",fit.txM),tyM=saved.optDouble("ty",fit.tyM))
            val chosen=saved.optJSONObject("selected")?:JSONObject()
            selections=chosen.keys().asSequence().filter { id->parsed.entities.any { it.id==id } }.associateWith { id ->
                val c=chosen.getJSONObject(id);FoundationChoice(c.optBoolean("ignored"),c.optDouble("margin",.30)) }
        }
        val index=withContext(Dispatchers.Default){DrawingIndex(parsed.entities)}
        undo.clear();redo.clear()
        _state.update { it.copy(fileName=name,sha=hash,cachedFile=file.absolutePath,drawing=parsed,index=index,
            visible=parsed.layers.filter { l->l.visible }.map { l->l.name }.toSet(),transform=fit,controls=pairs,selected=selections,
            busy=false,error=if(sameMap)null else "地图几何已变化，已恢复原图，请重新对齐",step=0,checks=emptyList(),draftVersion=0,checked=false,canUndo=false,canRedo=false) }
        invalidate()
    }
    fun chooseUnits(units:DxfUnits) {
        val s=_state.value;if(s.cachedFile.isBlank())return
        fileJob?.cancel();fileJob=viewModelScope.launch { _state.update { it.copy(busy=true) }
            try {loadCached(File(s.cachedFile),s.fileName,s.sha,null,units)}
            catch(c:CancellationException){throw c}catch(e:Exception){_state.update { it.copy(busy=false,error=e.message) }} }
    }
    private fun invalidate() {
        val s=_state.value;DrawingWorkflowDrafts.remove(s.sessionId);stagedPayload=null
        _state.update { it.copy(revision=it.revision+1,draftId="",draftVersion=0,checked=false,checks=emptyList()) };persist()
    }
    fun layer(name:String,show:Boolean){_state.update { it.copy(visible=if(show)it.visible+name else it.visible-name) };persist()}
    fun tool(tool:DrawingTool){_state.update { it.copy(tool=tool,pendingDrawing=null,pendingMap=null,snapped=null,outline=emptyList()) }}
    fun snap(enabled:Boolean){_state.update { it.copy(snapEnabled=enabled) }}
    fun opacity(value:Float){_state.update { it.copy(opacity=value) }}
    fun coarse(delta:PointD=PointD(0.0,0.0),degrees:Double=0.0){
        if(_state.value.controls.isNotEmpty()){_state.update { it.copy(error="已有控制点，请清空后再粗对齐") };return}
        _state.update { it.copy(transform=it.transform.copy(txM=it.transform.txM+delta.x,tyM=it.transform.tyM+delta.y,thetaDeg=it.transform.thetaDeg+degrees)) };invalidate()
    }
    fun drawingControl(world:PointD,radiusMm:Double) {
        val s=_state.value;val raw=s.transform.inverse(world)
        val target=if(s.snapEnabled)s.index?.snap(raw,radiusMm,s.visible)else null
        _state.update { it.copy(pendingDrawing=target?:raw,pendingMap=null,snapped=target) }
    }
    fun mapControl(world:PointD){_state.update { it.copy(pendingMap=world) }}
    fun nudge(dx:Double,dy:Double){_state.update { it.copy(pendingMap=it.pendingMap?.plus(PointD(dx,dy))) }}
    fun confirmPair(){val s=_state.value;val d=s.pendingDrawing?:return;val m=s.pendingMap?:return
        _state.update { it.copy(controls=it.controls+ControlPair(d,m,"P${it.controls.size+1}"),pendingDrawing=null,pendingMap=null,snapped=null) };solve() }
    fun removePair(index:Int){_state.update { it.copy(controls=it.controls.filterIndexed { n,_->n!=index }) };solve()}
    fun clearPairs(){_state.update { it.copy(controls=emptyList(),pendingDrawing=null,pendingMap=null,transform=it.transform.copy(rmsM=0.0,residualsM=emptyList())) };invalidate()}
    fun lockScale(value:Boolean){_state.update { it.copy(transform=it.transform.copy(scaleLocked=value)) };solve()}
    private fun solve(){workJob?.cancel();invalidate();val s=_state.value;if(s.controls.size<3)return
        _state.update {it.copy(transform=it.transform.copy(rmsM=Double.POSITIVE_INFINITY))}
        workJob=viewModelScope.launch {try {val fit=withContext(Dispatchers.Default){Registration.solve(s.controls,s.transform.scaleLocked)}
            _state.update { it.copy(transform=fit,error=null) };persist()
        }catch(c:CancellationException){throw c}catch(e:Exception){_state.update { it.copy(error=e.message) }}} }
    private fun history(){val s=_state.value;undo.addLast(Edit(s.drawing,s.selected));while(undo.size>50)undo.removeFirst();redo.clear()}
    private fun historyFlags(){_state.update { it.copy(canUndo=undo.isNotEmpty(),canRedo=redo.isNotEmpty()) };invalidate()}
    fun select(world:PointD,radiusMm:Double) {
        val s=_state.value;val entity=s.index?.hit(s.transform.inverse(world),radiusMm,s.visible)?:return
        if(!entity.closed){_state.update { it.copy(error="此图元未闭合，请使用手动描边") };return}
        history();_state.update { it.copy(selected=if(entity.id in it.selected)it.selected-entity.id else it.selected+(entity.id to FoundationChoice(marginM=it.defaultMargin)),activeId=entity.id) };historyFlags()
    }
    fun box(corners:List<PointD>){val s=_state.value;val rect=RectD.of(corners.map(s.transform::inverse))
        val found=s.index?.candidates(rect).orEmpty().filter { it.closed&&it.layer in s.visible&&rect.contains(PointD(it.bounds.left,it.bounds.bottom))&&rect.contains(PointD(it.bounds.right,it.bounds.top)) }
        history();_state.update { it.copy(selected=it.selected+found.associate { e->e.id to FoundationChoice(marginM=it.defaultMargin) }) };historyFlags() }
    fun similar(){val s=_state.value;val e=s.drawing?.entities?.firstOrNull { it.id==s.activeId }?:return
        if(e.block==null){_state.update { it.copy(error="该图元没有图块信息，请使用框选") };return}
        history();_state.update { it.copy(selected=it.selected+s.drawing.entities.filter { x->x.closed&&x.block==e.block }.associate { x->x.id to FoundationChoice(marginM=it.defaultMargin) }) };historyFlags() }
    fun focus(id:String){val s=_state.value;val e=s.drawing?.entities?.firstOrNull { it.id==id }?:return
        _state.update { it.copy(activeId=id,focus=s.transform.forward(PointD((e.bounds.left+e.bounds.right)/2,(e.bounds.bottom+e.bounds.top)/2)),focusRevision=it.focusRevision+1) } }
    fun focusPosition(point:PointD){_state.update { it.copy(focus=point,focusRevision=it.focusRevision+1) }}
    fun choice(id:String,ignored:Boolean?=null,margin:Double?=null){val c=_state.value.selected[id]?:return;history()
        _state.update { it.copy(selected=it.selected+(id to c.copy(ignored=ignored?:c.ignored,marginM=(margin?:c.marginM).coerceIn(0.0,2.0)))) };historyFlags() }
    fun all(ignored:Boolean?=null,clear:Boolean=false){history();_state.update { it.copy(selected=if(clear)emptyMap()else it.selected.mapValues { (_,v)->v.copy(ignored=ignored?:v.ignored) }) };historyFlags()}
    fun defaultMargin(value:Double){_state.update { it.copy(defaultMargin=value.coerceIn(0.0,2.0)) };persist()}
    fun outlinePoint(world:PointD,radiusMm:Double){val s=_state.value;val p=s.transform.inverse(world);val target=if(s.snapEnabled)s.index?.snap(p,radiusMm,s.visible)else null
        _state.update { it.copy(outline=it.outline+(target?:p),snapped=target) } }
    fun finishOutline(){val s=_state.value;if(s.outline.size<3)return;val drawing=s.drawing?:return;history()
        val id="manual:${UUID.randomUUID()}";val e=DxfEntity(id,"手动描边",null,true,s.outline)
        val next=drawing.copy(entities=drawing.entities+e,layers=if(drawing.layers.any { it.name==e.layer })drawing.layers else drawing.layers+DxfLayer(e.layer,0xFF9500))
        _state.update { it.copy(drawing=next,outline=emptyList(),selected=it.selected+(id to FoundationChoice(marginM=it.defaultMargin)),visible=it.visible+e.layer,activeId=id) }
        rebuildIndex();historyFlags()
    }
    fun undo(){if(undo.isEmpty())return;val s=_state.value;redo.addLast(Edit(s.drawing,s.selected));val old=undo.removeLast()
        _state.update { it.copy(drawing=old.drawing,selected=old.selected) };rebuildIndex();historyFlags()}
    fun redo(){if(redo.isEmpty())return;val s=_state.value;undo.addLast(Edit(s.drawing,s.selected));val next=redo.removeLast()
        _state.update { it.copy(drawing=next.drawing,selected=next.selected) };rebuildIndex();historyFlags()}
    private fun rebuildIndex(){val drawing=_state.value.drawing?:return;viewModelScope.launch {val index=withContext(Dispatchers.Default){DrawingIndex(drawing.entities)}
        if(_state.value.drawing===drawing)_state.update { it.copy(index=index) }} }
    fun previous(){workJob?.cancel();_state.update { it.copy(step=(it.step-1).coerceAtLeast(0),busy=false,checked=false,tool=DrawingTool.Pan) }}
    fun next(){val s=_state.value;if(!s.canContinue)return
        if(s.step==2){_state.update { it.copy(step=3,tool=DrawingTool.Pan) };validate()}
        else if(s.step<2)_state.update { it.copy(step=it.step+1,tool=if(it.step==0)DrawingTool.Pan else DrawingTool.Point) }
        persist()
    }
    fun checked(value:Boolean){_state.update { it.copy(checked=value) }}
    private fun payload(s:DrawingImportUiState):SlLink.DrawingLayerPayload {
        val frame=s.frame?:error("地图未加载");val fit=s.transform
        val transform=SlLink.DrawingTransform.newBuilder().setThetaDeg(fit.thetaDeg).setTxM(fit.txM).setTyM(fit.tyM)
            .setScale(fit.scale).setMeasuredScale(fit.measuredScale).setRmsM(fit.rmsM).setScaleLocked(fit.scaleLocked)
        s.controls.forEachIndexed { n,p->transform.addControlPoints(SlLink.DrawingControlPoint.newBuilder().setDrawingXMm(p.drawingMm.x).setDrawingYMm(p.drawingMm.y)
            .setMapXM(p.mapM.x).setMapYM(p.mapM.y).setResidualM(fit.residualsM.getOrElse(n){0.0}).setLabel(p.label)) }
        val binding=SlLink.DrawingMapBinding.newBuilder().setWidth(frame.mapWidth).setHeight(frame.mapHeight).setResolution(frame.resolution.toDouble())
            .setOriginX(frame.originX).setOriginY(frame.originY).setHeadingDeg(frame.headingDeg.toDouble()).setFrameId(frame.frameId)
            .setMapRevision(capability?.layer?.binding?.mapRevision.orEmpty())
        val info=SlLink.DrawingObstacleLayerInfo.newBuilder().setLayerId(capability?.layer?.layerId?.takeIf { it.isNotEmpty() }?:UUID.randomUUID().toString())
            .setSourceFile(s.fileName).setSourceSha256(s.sha).setTransform(transform).setBinding(binding)
        val result=SlLink.DrawingLayerPayload.newBuilder().setInfo(info).setRemoveLayer(s.removeLayer)
        if(!s.removeLayer)s.drawing?.entities?.filter { it.id in s.selected }?.forEach { e->val c=s.selected.getValue(e.id)
            val polygon=SlLink.PolygonRegion.newBuilder().setRegionId("drawing:${s.sha.take(12)}:${e.id}").setName(e.id.takeLast(20))
                .setSource("drawing").setSubtype("foundation").setDrawingRef(e.id).setClosed(true).setRegionType(SlLink.RegionType.REGION_TYPE_OBSTACLE)
                .setEnabled(!c.ignored).setDrawingAction(if(c.ignored)SlLink.DrawingObstacleAction.DRAWING_IGNORE else SlLink.DrawingObstacleAction.DRAWING_AVOID)
                .setInflationM(c.marginM.toFloat()).setGeometryErrorM((e.approximationMm/1000).toFloat())
            e.pointsMm.forEach { p->val w=fit.forward(p);polygon.addPoints(SlLink.PolygonPoint.newBuilder().setX(w.x.toFloat()).setY(w.y.toFloat())) }
            result.addFoundations(polygon)
        }
        return result.build()
    }
    fun removeLayer(){_state.update { it.copy(removeLayer=true,step=3,checked=false) };invalidate();validate()}
    fun validate(){workJob?.cancel();val s=_state.value;_state.update { it.copy(busy=true,error=null,checked=false) }
        workJob=viewModelScope.launch {
            try {
                val body=withContext(Dispatchers.Default){payload(s)};val id=UUID.randomUUID().toString()
                val response=gateway.upload(s.mapId,id,body,s.sourceVersion)
                if(_state.value.revision!=s.revision)return@launch
                stagedPayload=body;_state.update { it.copy(busy=false,draftId=id,draftVersion=response.draftVersion,checks=response.checksList) }
            }catch(c:CancellationException){throw c}catch(e:Exception){_state.update { it.copy(busy=false,error=e.message,draftVersion=0) }}
        }
    }
    fun confirm():Boolean {val s=_state.value;if(!s.canContinue||s.step!=3)return false;val body=stagedPayload?:return false
        val target=if(s.mapId=="LIVE_MAP")"map_${UUID.randomUUID()}"else s.mapId
        DrawingWorkflowDrafts.put(ConfirmedDrawingDraft(s.sessionId,s.mapId,s.draftId,s.draftVersion,body,target,UUID.randomUUID().toString()))
        persist();return true
    }
    fun skip(){DrawingWorkflowDrafts.remove(_state.value.sessionId)}
    private fun persist(){saveJob?.cancel();val s=_state.value;if(s.cachedFile.isBlank())return
        saveJob=viewModelScope.launch(Dispatchers.IO) { delay(300)
            try {
                val controls=JSONArray();s.controls.forEach { controls.put(JSONArray(listOf(it.drawingMm.x,it.drawingMm.y,it.mapM.x,it.mapM.y))) }
                val selected=JSONObject();s.selected.forEach { (id,c)->selected.put(id,JSONObject().put("ignored",c.ignored).put("margin",c.marginM)) }
                val manual=JSONArray();s.drawing?.entities?.filter {it.id.startsWith("manual:")}?.forEach { e->
                    manual.put(JSONObject().put("id",e.id).put("points",JSONArray(e.pointsMm.map {JSONArray(listOf(it.x,it.y))}))) }
                val json=JSONObject().put("file",s.cachedFile).put("name",s.fileName).put("sha",s.sha).put("sourceVersion",s.sourceVersion)
                    .put("frameSha",frameFingerprint)
                    .put("controls",controls).put("locked",s.transform.scaleLocked).put("selected",selected)
                    .put("manual",manual).put("units",s.drawing?.units?.name).put("theta",s.transform.thetaDeg).put("tx",s.transform.txM).put("ty",s.transform.tyM)
                    .put("width",s.frame?.mapWidth).put("height",s.frame?.mapHeight).put("resolution",s.frame?.resolution)
                    .put("originX",s.frame?.originX).put("originY",s.frame?.originY)
                    .put("heading",s.frame?.headingDeg).put("frameId",s.frame?.frameId).put("mapRevision",capability?.layer?.binding?.mapRevision.orEmpty())
                val path=draftFile(s.mapId);path.parentFile?.mkdirs();val atomic=android.util.AtomicFile(path);val out=atomic.startWrite()
                try {out.write(json.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}
            }catch(c:CancellationException){throw c}catch(_:Exception){ _state.update {it.copy(error="本地图纸草稿保存失败，请检查存储空间")} }
        }
    }
}
