package com.sinelynx.grindingrobot.feature.map.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.sinelynx.grindingrobot.core.dxf.*
import com.sinelynx.grindingrobot.feature.map.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

private val DrawingBlue=Color(0xFF00A0E9)
private val DrawingOrange=Color(0xFFFF9500)
private val DrawingInk=Color(0xFF202937)

@Composable
fun DrawingImportScreen(viewModel:DrawingImportViewModel,mapId:String?,onPrevious:()->Unit,onContinue:()->Unit,onCancel:()->Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(mapId){viewModel.open(mapId)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri->uri?.let(viewModel::chooseFile) }
    var remove by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFD1DBE8),Color(0xFFEBEEF2)))).padding(12.dp)) {
        val side=workflowSideWidth(maxWidth)
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            MapWorkflowHeader(3,viewModel::emergencyStop,drawing=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                listOf("① 导入图纸","② 对齐地图","③ 选择地基","④ 检查确认").forEachIndexed { n,label ->
                    Surface(color=if(n==state.step)DrawingBlue else if(n<state.step)Color(0xFFE3F6EB)else Color(0xFFE5E7EB),shape=RoundedCornerShape(100.dp),modifier=Modifier.padding(horizontal=4.dp)) {
                        Text(label,Modifier.padding(horizontal=14.dp,vertical=7.dp),color=if(n==state.step)Color.White else DrawingInk,fontSize=13.sp)
                    }
                }
            }
            state.error?.let { error->Row(Modifier.fillMaxWidth().background(Color(0xFFFFF1E5),RoundedCornerShape(12.dp)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(error,Modifier.weight(1f).heightIn(max=76.dp).verticalScroll(rememberScrollState()),color=Color(0xFF9B4D00),fontSize=14.sp)
                TextButton(onClick=viewModel::clearError){Text("关闭")}
            } }
            Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DrawingGuide(state,viewModel,Modifier.width(side).fillMaxHeight())
                Surface(Modifier.weight(1f).fillMaxHeight(),color=Color(0xFFF6F8FB),shape=RoundedCornerShape(16.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        DrawingCanvas(state,viewModel)
                        if(state.busy)Surface(Modifier.align(Alignment.TopCenter).padding(10.dp),color=Color.White,shape=RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp),color=DrawingBlue,strokeWidth=2.dp)
                                Text(if(state.step==3)"正在检查图纸边界…"else "正在准备图纸…",color=DrawingInk,fontSize=14.sp)
                            }
                        }
                    }
                }
                DrawingInspector(state,viewModel,{picker.launch(arrayOf("*/*"))},{remove=true},Modifier.width((side+32.dp).coerceAtMost(240.dp)).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                MapWorkflowAction("取消建图",false,{viewModel.skip();onCancel()})
                TextButton(onClick={viewModel.skip();onContinue()}) {Text(if(state.existingCount>0)"保留现有图纸"else "跳过图纸",color=Color(0xFF647080))}
                Spacer(Modifier.weight(1f))
                MapWorkflowAction("上一步",false,{if(state.step==0)onPrevious()else viewModel.previous()})
                MapWorkflowAction(if(state.step==3)"保存草稿并继续"else if(state.step==1&&state.controls.size<3)"添加控制点"else "下一步",true,
                    {if(state.step==3){if(viewModel.confirm())onContinue()}else viewModel.next()},
                    enabled=state.canContinue||(state.step==1&&!state.busy))
            }
        }
    }
    if(remove)DrawingConfirmDialog("移除图纸障碍层","将在最后保存地图时移除本地图的全部图纸地基。取消编辑会保留原图纸层。","确认移除",
        {remove=false;viewModel.removeLayer()},{remove=false})
}

@Composable
private fun DrawingGuide(s:DrawingImportUiState,vm:DrawingImportViewModel,modifier:Modifier) {
    Column(modifier.background(Color(0xFFF6F8FB),RoundedCornerShape(18.dp)).padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(listOf("导入图纸","对齐地图","选择地基","检查确认")[s.step],color=DrawingInk,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text(when(s.step){0->"选择二维模型空间 DXF\n可稍后通过编辑地图导入";1->"先选图纸角点\n再选地图同一点\n至少三对，分散点选";2->"选择闭合地基轮廓\n隐藏图层不会取消已选地基";else->"检查位置和边界\n划分工作区后预览路径"},fontSize=13.sp,color=Color(0xFF647080))
        if(s.step in 1..2) {
            DrawingToolButton("查看 / 缩放",s.tool==DrawingTool.Pan){vm.tool(DrawingTool.Pan)}
            if(s.step==1) {
                DrawingToolButton("拖动图纸",s.tool==DrawingTool.MoveDrawing){vm.tool(DrawingTool.MoveDrawing)}
                var rotationStep by remember {mutableDoubleStateOf(.005)}
                Text("角度 %.3f°".format(s.transform.thetaDeg),fontSize=13.sp,color=DrawingInk)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    listOf(1.0,.1,.005).forEach {step->Surface(onClick={rotationStep=step},
                        color=if(rotationStep==step)DrawingBlue else Color.White,shape=RoundedCornerShape(8.dp),modifier=Modifier.weight(1f)) {
                        Text("${step}°",Modifier.padding(vertical=8.dp),fontSize=11.sp,color=if(rotationStep==step)Color.White else DrawingInk,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    } }
                }
                Row { TextButton(onClick={vm.coarse(degrees=-rotationStep)},modifier=Modifier.weight(1f)){Text("旋转−")};TextButton(onClick={vm.coarse(degrees=rotationStep)},modifier=Modifier.weight(1f)){Text("旋转＋")} }
                if(s.controls.isEmpty())Text(if(s.pendingDrawing!=null)"围绕已选图纸点旋转"else "围绕图纸中心旋转",fontSize=11.sp,color=Color(0xFF647080))
                Text(if(s.controls.isEmpty())"先旋转、拖动粗对齐，再添加控制点"else "控制点已锁定变换；重新粗调请先清空控制点",fontSize=11.sp,color=Color(0xFF647080))
                DrawingToolButton("添加控制点",s.tool==DrawingTool.Control){vm.tool(DrawingTool.Control)}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.snapEnabled,vm::snap);Text("图纸角点吸附",fontSize=12.sp,color=DrawingInk)}
                if(s.tool==DrawingTool.Control)Text(if(s.pendingDrawing==null)"当前：选图纸点"else "当前：地图手动选点",fontSize=13.sp,color=DrawingBlue)
                if(s.pendingMap!=null) {
                    Text("地图坐标微调",fontSize=12.sp,color=DrawingInk)
                    val step=(s.frame?.resolution?:.05f).toDouble()/2
                    Row {TextButton(onClick={vm.nudge(-step,0.0)}){Text("X−")};TextButton(onClick={vm.nudge(step,0.0)}){Text("X＋")}}
                    Row {TextButton(onClick={vm.nudge(0.0,-step)}){Text("Y−")};TextButton(onClick={vm.nudge(0.0,step)}){Text("Y＋")}}
                    DrawingToolButton("确认这一对",true,vm::confirmPair)
                }
            } else {
                DrawingToolButton("点选",s.tool==DrawingTool.Point){vm.tool(DrawingTool.Point)}
                DrawingToolButton("框选",s.tool==DrawingTool.Box){vm.tool(DrawingTool.Box)}
                DrawingToolButton("同图块批选",false,vm::similar)
                DrawingToolButton("手动描边",s.tool==DrawingTool.Outline){vm.tool(DrawingTool.Outline)}
                if(s.tool==DrawingTool.Outline)DrawingToolButton("闭合描边 (${s.outline.size})",false,vm::finishOutline)
                Row {TextButton(onClick=vm::undo,enabled=s.canUndo){Text("撤销")};TextButton(onClick=vm::redo,enabled=s.canRedo){Text("重做")}}
            }
        }
        if(s.step==3) {
            Text("当前为草稿\n保存地图后正式生效",fontSize=13.sp,color=DrawingInk)
            TextButton(onClick=vm::validate,enabled=!s.busy){Text("重新检查")}
        }
    }
}

@Composable private fun DrawingToolButton(label:String,selected:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,color=if(selected)DrawingBlue else Color.White,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
        Text(label,Modifier.padding(horizontal=10.dp,vertical=10.dp),fontSize=14.sp,color=if(selected)Color.White else DrawingInk)
    }
}

@Composable
private fun DrawingInspector(s:DrawingImportUiState,vm:DrawingImportViewModel,onFile:()->Unit,onRemove:()->Unit,modifier:Modifier) {
    Column(modifier.background(Color.White,RoundedCornerShape(16.dp)).padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        when(s.step) {
            0->{
                Text("图纸文件",color=DrawingInk,fontSize=18.sp,fontWeight=FontWeight.Bold)
                DrawingToolButton(if(s.fileName.isBlank())"选择 DXF 文件"else "更换图纸",false,onFile)
                if(s.existingCount>0)Text("已绑定 ${s.existingCount} 个地基\n可保留或更新图纸",color=Color(0xFF647080),fontSize=13.sp)
                Text(s.fileName,color=DrawingInk,fontSize=14.sp)
                s.drawing?.let { drawing ->
                    Text("${drawing.entities.size} 个图元 · ${drawing.encoding}",fontSize=12.sp,color=Color(0xFF647080))
                    Text("图纸单位：${when(drawing.units){DxfUnits.UNKNOWN->"未知，请选择";DxfUnits.MILLIMETRES->"毫米";DxfUnits.CENTIMETRES->"厘米";DxfUnits.METRES->"米";DxfUnits.INCHES->"英寸";DxfUnits.FEET->"英尺"}}",fontSize=13.sp,color=DrawingInk)
                    Row {listOf("毫米" to DxfUnits.MILLIMETRES,"厘米" to DxfUnits.CENTIMETRES,"米" to DxfUnits.METRES).forEach { (label,unit)->TextButton(onClick={vm.chooseUnits(unit)},enabled=!s.busy){Text(label,fontSize=12.sp)} }}
                    Text("范围 %.2f × %.2f m".format(drawing.extentMm.width/1000,drawing.extentMm.height/1000),fontSize=12.sp,color=DrawingInk)
                    Text("显示图层",color=DrawingInk,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    drawing.layers.forEach { layer->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(layer.name in s.visible,{vm.layer(layer.name,it)});Text(layer.name,color=DrawingInk,fontSize=13.sp)} }
                    drawing.warnings.forEach {Text(it,fontSize=12.sp,color=if(it.startsWith("已忽略标注"))Color(0xFF647080)else Color(0xFF9B4D00))}
                }
                if(s.existingCount>0)TextButton(onClick=onRemove){Text("移除图纸障碍层",color=Color(0xFFD82B2A))}
            }
            1->{
                Text("地图配准",color=DrawingInk,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.transform.scaleLocked,vm::lockScale);Text("锁定比例",fontSize=14.sp,color=DrawingInk)}
                Text("控制点 ${s.controls.size} 对",fontSize=14.sp,color=DrawingInk)
                if(s.pendingMap!=null)DrawingToolButton("确认这一对（P${s.controls.size+1}）",true,vm::confirmPair)
                s.controls.forEachIndexed { n,p->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(p.label,Modifier.weight(1f),color=DrawingInk,fontSize=13.sp)
                        val residual=if(s.registrationSolved)s.transform.residualsM.getOrNull(n)else null
                        Text(residual?.let { "%.1f cm".format(it*100) }?:"待求解",color=if(residual==null)Color(0xFF647080)else if(residual>.2)Color(0xFFD82B2A)else Color(0xFF24BE1C),fontSize=13.sp)
                        TextButton(onClick={vm.removePair(n)}){Text("删除",fontSize=12.sp)}
                    }
                }
                TextButton(onClick=vm::clearPairs){Text("清空控制点")}
                if(s.registrationSolved) {
                    Text("RMS %.1f cm".format(s.transform.rmsM*100),fontSize=15.sp,color=if(s.transform.blocked)Color(0xFFD82B2A)else if(s.transform.warning)DrawingOrange else Color(0xFF24BE1C))
                    Text("自检比例 %.5f".format(s.transform.measuredScale),fontSize=13.sp,color=DrawingInk)
                } else Text("RMS — · 配准未完成",fontSize=15.sp,color=Color(0xFF647080))
                s.registrationHint?.let {Text(it,fontSize=13.sp,color=if(s.controls.size>=3)Color(0xFFD82B2A)else DrawingOrange)}
                Text("图纸透明度 ${(s.opacity*100).toInt()}%",color=DrawingInk,fontSize=13.sp)
                Slider(s.opacity,vm::opacity,valueRange=.1f..1f)
            }
            2->{
                Text("已选地基 ${s.selected.size}",color=DrawingInk,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Text("避开 ${s.selected.count { !it.value.ignored }} / 忽略 ${s.selected.count { it.value.ignored }}",color=DrawingInk,fontSize=13.sp)
                Text("默认额外间距 %.2f m".format(s.defaultMargin),fontSize=13.sp,color=DrawingInk)
                Slider(s.defaultMargin.toFloat(),{vm.defaultMargin(it.toDouble())},valueRange=0f..2f)
                Row {TextButton(onClick={vm.all(false)}){Text("全部避开",fontSize=12.sp)};TextButton(onClick={vm.all(true)}){Text("全部忽略",fontSize=12.sp)}}
                TextButton(onClick={vm.all(clear=true)}){Text("清空本次选择")}
                s.selected.forEach { (id,choice)->
                    TextButton(onClick={vm.focus(id)},modifier=Modifier.fillMaxWidth().background(if(s.activeId==id)Color(0xFFFFF1E5)else Color.Transparent,RoundedCornerShape(8.dp))) {
                        Text("${id.takeLast(16)}  ${if(choice.ignored)"忽略"else "避开"}",color=if(choice.ignored)Color.Gray else DrawingOrange,fontSize=13.sp)
                    }
                }
                s.activeId?.let { id->s.selected[id]?.let { c ->
                    val e=s.drawing?.entities?.firstOrNull { it.id==id }
                    Text("${e?.layer.orEmpty()} / ${e?.block?:"无图块"}",fontSize=12.sp,color=DrawingInk)
                    e?.let {Text("%.2f × %.2f m".format(it.bounds.width/1000,it.bounds.height/1000),fontSize=13.sp,color=DrawingInk)}
                    Row {TextButton(onClick={vm.choice(id,ignored=false)}){Text("避开")};TextButton(onClick={vm.choice(id,ignored=true)}){Text("忽略")}}
                    Text("额外间距 %.2f m".format(c.marginM),fontSize=13.sp,color=DrawingInk)
                    Slider(c.marginM.toFloat(),{vm.choice(id,margin=it.toDouble())},valueRange=0f..2f)
                } }
            }
            3->{
                Text("检查结果",color=DrawingInk,fontSize=18.sp,fontWeight=FontWeight.Bold)
                if(s.removeLayer)Text("本次将移除图纸障碍层",fontSize=14.sp,color=Color(0xFFD82B2A))
                s.checks.forEach { check->
                    Text(check.message,color=if(check.level=="error")Color(0xFFD82B2A)else Color(0xFF9B4D00),fontSize=13.sp)
                    if(check.hasPosition())TextButton(onClick={vm.focusPosition(PointD(check.position.x.toDouble(),check.position.y.toDouble()))}){Text("定位")}
                }
                if(!s.busy&&s.draftVersion>0&&s.checks.none { it.level=="error" })Text("配准和边界检查通过",color=Color(0xFF24BE1C),fontSize=14.sp)
                Text("不代表任务路径可达，请在划分工作区后查看路径预览。",color=Color(0xFF647080),fontSize=13.sp)
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.checked,vm::checked,enabled=s.draftVersion>0&&!s.busy);Text("我已核对位置",fontSize=14.sp,color=DrawingInk)}
                Text("当前为草稿\n保存地图后生效",color=DrawingInk,fontSize=13.sp)
            }
        }
    }
}

private data class DrawingPath(val entity:DxfEntity,val path:Path,val bounds:Rect)

@Composable
private fun DrawingCanvas(s:DrawingImportUiState,vm:DrawingImportViewModel) {
    val bitmap=rememberWorkflowBitmap(s.frame?.imageBytes)
    val frame=s.frame
    val geo=remember(frame){frame?.toMapGeo()}
    val latest by rememberUpdatedState(s)
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(s.sessionId){mutableFloatStateOf(1f)}
    var pan by remember(s.sessionId){mutableStateOf(Offset.Zero)}
    var boxStart by remember {mutableStateOf<Offset?>(null)};var boxEnd by remember {mutableStateOf<Offset?>(null)}
    val density=LocalDensity.current
    val preview=remember(s.step,s.drawing){if(s.step==0)s.drawing?.let {DrawingPreviewProjection(it.extentMm)}else null}
    val contentWidth=preview?.width?.toFloat()?:bitmap?.width?.toFloat()?:1f
    val contentHeight=preview?.height?.toFloat()?:bitmap?.height?.toFloat()?:1f
    val angle=if(s.step==0)0f else frame?.let {it.alignmentYawDeg+it.rotationAlignmentDeltaDeg}?:0f
    val scale=if(panelSize.width>0)min(panelSize.width/contentWidth,panelSize.height/contentHeight)*.94f*zoom else 1f
    LaunchedEffect(s.step,s.sha){zoom=1f;pan=Offset.Zero}
    fun rotate(p:Offset,degrees:Float):Offset {val a=Math.toRadians(degrees.toDouble());return Offset((cos(a)*p.x-sin(a)*p.y).toFloat(),(sin(a)*p.x+cos(a)*p.y).toFloat())}
    fun base(p:PointD):Offset? = if(bitmap!=null&&geo!=null)worldPointToBitmapOffset(MapPreviewPointItem(p.x.toFloat(),p.y.toFloat()),geo,
        geo.mapWidth to geo.mapHeight,bitmap.width to bitmap.height)else null
    fun screen(p:PointD):Offset {val b=base(p)?:return Offset.Zero;return rotate(b-Offset(bitmap!!.width/2f,bitmap.height/2f),angle)*scale+Offset(panelSize.width/2f,panelSize.height/2f)+pan}
    fun world(p:Offset):PointD? {
        if(bitmap==null||geo==null)return null
        val b=rotate((p-Offset(panelSize.width/2f,panelSize.height/2f)-pan)/scale,-angle)+Offset(bitmap.width/2f,bitmap.height/2f)
        return bitmapOffsetToMapWorld(b,geo,bitmap.width,bitmap.height)
    }
    val radiusMm=if(frame!=null)with(density){16.dp.toPx()}/scale*frame.resolution*1000/s.transform.scale else 1.0
    val currentWorld by rememberUpdatedState<(Offset)->PointD?>({world(it)})
    val currentRadius by rememberUpdatedState(radiusMm)
    val paths by produceState<List<DrawingPath>>(emptyList(),s.drawing,s.transform,bitmap,geo,preview) {
        value=withContext(Dispatchers.Default) {
            val image=bitmap;val map=geo
            if(image==null||map==null)emptyList() else s.drawing?.entities.orEmpty().map { e->
                val path=Path();e.pointsMm.forEachIndexed { n,p->val w=s.transform.forward(p)
                    val b=preview?.project(p)?.let {Offset(it.x.toFloat(),it.y.toFloat())}?:
                        worldPointToBitmapOffset(MapPreviewPointItem(w.x.toFloat(),w.y.toFloat()),map,map.mapWidth to map.mapHeight,image.width to image.height)
                    if(b!=null){if(n==0)path.moveTo(b.x,b.y)else path.lineTo(b.x,b.y)} }
                if(e.closed)path.close();DrawingPath(e,path,path.getBounds())
            }
        }
    }
    val margins by produceState<Map<String,Path>>(emptyMap(),paths,s.selected,bitmap,frame) {
        value=withContext(Dispatchers.Default) {
            val image=bitmap;val map=frame
            if(image==null||map==null)emptyMap() else {
                val byId=paths.associateBy {it.entity.id}
                s.selected.mapNotNull { (id,choice)->
                    val source=byId[id]
                    if(source==null||choice.ignored||choice.marginM<=0)null
                    else id to expandedFoundationPath(source.path,(choice.marginM/map.resolution*image.width/map.mapWidth).toFloat())
                }.toMap()
            }
        }
    }
    LaunchedEffect(s.focusRevision){s.focus?.let {pan+=Offset(panelSize.width/2f,panelSize.height/2f)-screen(it)}}
    Box(Modifier.fillMaxSize().onSizeChanged{panelSize=it}) {
        Canvas(Modifier.fillMaxSize().testTag("drawing_map").pointerInput(s.tool,s.step,s.pendingDrawing!=null) {
            if(s.tool==DrawingTool.Box)detectDragGestures(onDragStart={boxStart=it;boxEnd=it},onDragEnd={
                val a=boxStart;val b=boxEnd;if(a!=null&&b!=null)vm.box(listOf(Offset(a.x,a.y),Offset(b.x,a.y),Offset(b.x,b.y),Offset(a.x,b.y)).mapNotNull(currentWorld));boxStart=null;boxEnd=null
            },onDragCancel={boxStart=null;boxEnd=null}){change,_->change.consume();boxEnd=change.position}
            else detectTapGestures { tap->currentWorld(tap)?.let { p->when(latest.tool) {
                DrawingTool.Control->if(latest.pendingDrawing==null)vm.drawingControl(p,currentRadius)else vm.mapControl(p)
                DrawingTool.Point->vm.select(p,currentRadius)
                DrawingTool.Outline->vm.outlinePoint(p,currentRadius)
                else->Unit
            } } }
        }.pointerInput(s.tool) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                var multiple=false
                do {
                    val event=awaitPointerEvent()
                    val pressed=event.changes.filter {it.pressed}
                    if(pressed.size>=2) {
                        multiple=true;zoom=(zoom*event.calculateZoom()).coerceIn(.25f,12f);pan+=event.calculatePan()
                        event.changes.forEach {it.consume()}
                    } else if(!multiple&&pressed.size==1&&(latest.tool==DrawingTool.Pan||latest.tool==DrawingTool.MoveDrawing)) {
                        val change=pressed.first();val delta=change.position-change.previousPosition
                        if(delta!=Offset.Zero) {
                            if(latest.tool==DrawingTool.MoveDrawing){val a=currentWorld(change.previousPosition);val b=currentWorld(change.position);if(a!=null&&b!=null)vm.coarse(b-a)}
                            else pan+=delta
                            change.consume()
                        }
                    }
                }while(event.changes.any {it.pressed})
            }
        }) {
            drawRect(Color(0xFFE5EBF2))
            val image=bitmap
            if(image!=null) {
                withTransform({translate(panelSize.width/2f+pan.x,panelSize.height/2f+pan.y);scale(scale,scale,pivot=Offset.Zero);rotate(angle,pivot=Offset.Zero);translate(-contentWidth/2f,-contentHeight/2f)}) {
                    if(s.step>0)drawImage(image)
                    else drawRect(Color.White,size=androidx.compose.ui.geometry.Size(contentWidth,contentHeight))
                    val corners=listOf(Offset.Zero,Offset(panelSize.width.toFloat(),0f),Offset(0f,panelSize.height.toFloat()),Offset(panelSize.width.toFloat(),panelSize.height.toFloat()))
                        .map {rotate((it-Offset(panelSize.width/2f,panelSize.height/2f)-pan)/scale,-angle)+Offset(contentWidth/2f,contentHeight/2f)}
                    val viewport=Rect(corners.minOf {it.x},corners.minOf {it.y},corners.maxOf {it.x},corners.maxOf {it.y})
                    paths.forEach { item->
                        val c=s.selected[item.entity.id];val show=if(s.step==3)c!=null else item.entity.layer in s.visible||c!=null
                        if(show&&item.bounds.inflate(2f/scale).overlaps(viewport)) {
                            val color=if(c?.ignored==true)Color.Gray else if(c!=null||item.entity.layer.contains("基础")||item.entity.layer.contains("FOUND",true))DrawingOrange else Color(0xFF2466FF)
                            if(c!=null&&c.ignored.not())drawPath(item.path,color.copy(alpha=.28f))
                            if(s.step==3)margins[item.entity.id]?.let { margin->drawPath(margin,DrawingOrange,style=Stroke(1f/scale,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5f/scale,4f/scale)))) }
                            val border=if(item.entity.id==s.activeId&&s.step==2)DrawingBlue else color
                            drawPath(item.path,border.copy(alpha=if(s.step==1)s.opacity else 1f),style=Stroke((if(item.entity.id==s.activeId)2.5f else 1.2f)/scale,
                                pathEffect=if(c?.ignored==true)PathEffect.dashPathEffect(floatArrayOf(5f/scale,4f/scale))else null))
                        }
                    }
                }
            }
            if(s.step>0)s.controls.forEachIndexed { n,p->val pt=screen(p.mapM);drawCircle(Color(0xFF24BE1C),7.dp.toPx(),pt);drawContext.canvas.nativeCanvas.drawText("${n+1}",pt.x-3.dp.toPx(),pt.y+3.dp.toPx(),android.graphics.Paint().apply {color=android.graphics.Color.WHITE;textSize=10.dp.toPx();isAntiAlias=true}) }
            s.pendingDrawing?.let {drawCircle(DrawingBlue,6.dp.toPx(),screen(s.transform.forward(it)),style=Stroke(2.dp.toPx()))}
            s.pendingMap?.let {val p=screen(it);drawLine(Color.Red,p-Offset(12f,0f),p+Offset(12f,0f),2f);drawLine(Color.Red,p-Offset(0f,12f),p+Offset(0f,12f),2f)}
            if(s.outline.isNotEmpty()){val path=Path();s.outline.forEachIndexed { n,p->val pt=screen(s.transform.forward(p));if(n==0)path.moveTo(pt.x,pt.y)else path.lineTo(pt.x,pt.y) };drawPath(path,DrawingOrange,style=Stroke(2.dp.toPx()))}
            val a=boxStart;val b=boxEnd;if(a!=null&&b!=null)drawRect(DrawingBlue,topLeft=Offset(min(a.x,b.x),min(a.y,b.y)),size=androidx.compose.ui.geometry.Size(abs(a.x-b.x),abs(a.y-b.y)),style=Stroke(2.dp.toPx()))
        }
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.White,RoundedCornerShape(10.dp)),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={zoom=1f;pan=Offset.Zero}){Text("%.1f× · 复位".format(zoom),color=DrawingInk)}
            TextButton(onClick={zoom=(zoom*1.4f).coerceAtMost(12f)}){Text("＋")};TextButton(onClick={zoom=(zoom/1.4f).coerceAtLeast(.25f)}){Text("−")}
        }
        if(s.snapped!=null&&s.step==1)Text("已吸附图纸角点",Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.White,RoundedCornerShape(8.dp)).padding(8.dp),color=DrawingBlue,fontSize=13.sp)
        if(s.pendingMap!=null&&bitmap!=null&&geo!=null) {
            val p=base(s.pendingMap)?:Offset.Zero
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp).width(140.dp).background(Color.White,RoundedCornerShape(12.dp)).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Canvas(Modifier.size(128.dp)) {
                    val crop=minOf((this.size.width/(scale*4)).toInt(),bitmap.width,bitmap.height).coerceAtLeast(1)
                    val x=(p.x-crop/2).toInt().coerceIn(0,(bitmap.width-crop).coerceAtLeast(0));val y=(p.y-crop/2).toInt().coerceIn(0,(bitmap.height-crop).coerceAtLeast(0))
                    drawImage(bitmap,srcOffset=IntOffset(x,y),srcSize=IntSize(crop,crop),dstSize=IntSize(this.size.width.toInt(),this.size.height.toInt()),filterQuality=FilterQuality.None)
                    val q=Offset((p.x-x)/crop*this.size.width,(p.y-y)/crop*this.size.height)
                    drawLine(Color.Red,q-Offset(8f,0f),q+Offset(8f,0f),2f);drawLine(Color.Red,q-Offset(0f,8f),q+Offset(0f,8f),2f)
                }
                Text("地图手动选点 · 4倍",fontSize=11.sp,color=DrawingInk)
            }
        }
        if(bitmap==null&&!s.busy)Text("地图尚未准备好",Modifier.align(Alignment.Center),color=DrawingInk)
    }
}
