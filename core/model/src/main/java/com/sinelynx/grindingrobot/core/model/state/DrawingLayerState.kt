package com.sinelynx.grindingrobot.core.model.state

import java.util.concurrent.ConcurrentHashMap

data class DrawingLayerState(val layerId:String,val version:Int,val status:Int,val staleReason:String,
    val sourceFile:String,val sourceSha256:String,val encodedInfo:String="") {
    val stale get()=status==2
}
/** Explicit bypass is keyed by task and layer version, never by map alone. */
object DrawingTaskPolicy {
    data class Choice(val mapId:String,val version:Int)
    private val choices=ConcurrentHashMap<String,Choice>()
    private val layers=ConcurrentHashMap<String,DrawingLayerState>()
    fun update(mapId:String,layer:DrawingLayerState?) { if(layer==null)layers.remove(mapId) else layers[mapId]=layer }
    fun layer(mapId:String)=layers[mapId]
    fun grant(taskId:String,mapId:String,version:Int) { choices[taskId]=Choice(mapId,version) }
    fun allows(taskId:String,mapId:String,version:Int)=choices[taskId]==Choice(mapId,version)
    fun revoke(taskId:String) { choices.remove(taskId) }
    fun clear() { choices.clear();layers.clear() }
}
