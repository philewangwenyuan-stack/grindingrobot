package com.sinelynx.grindingrobot.core.tcp

import sl_link.SlLink
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Bounded annotation snapshots; incomplete snapshots never reach map/task UI. */
internal class MapRegionReplyAssembler(private val now:()->Long = System::nanoTime) {
    private data class Pending(val mapId:String,val version:Int,val total:Int,val sha:String,
        val expires:Long,val chunks:MutableMap<Int,ByteArray> = mutableMapOf(),var bytes:Int=0)
    private val pending=linkedMapOf<String,Pending>()
    @Synchronized fun clear() = pending.clear()
    @Synchronized fun accept(reply:SlLink.MapRegionPointResponse):SlLink.MapRegionPointResponse? {
        pending.entries.removeAll {it.value.expires<now()}
        if(reply.transferId.isEmpty()) {
            require(reply.totalChunks==0&&reply.chunkData.isEmpty) {"区域分片缺少传输编号"}
            return reply
        }
        val id=reply.transferId
        try {
            require(id.length<=128&&reply.totalChunks in 1..342&&reply.chunkIndex in 0 until reply.totalChunks&&
                reply.chunkData.size() in 1..49152&&reply.payloadSha256.matches(Regex("[a-f0-9]{64}"))) {"区域分片元数据无效"}
            val item=pending[id]?:run {
                require(pending.size<2) {"并行区域响应过多"}
                Pending(reply.mapId,reply.mapVersion,reply.totalChunks,reply.payloadSha256,now()+15_000_000_000L).also {pending[id]=it}
            }
            require(item.mapId==reply.mapId&&item.version==reply.mapVersion&&item.total==reply.totalChunks&&item.sha==reply.payloadSha256)
            val data=reply.chunkData.toByteArray()
            val old=item.chunks[reply.chunkIndex]
            if(old!=null)require(old.contentEquals(data)) {"重复区域分片内容不同"}
            else {item.chunks[reply.chunkIndex]=data;item.bytes+=data.size}
            require(item.bytes<=16*1024*1024) {"区域快照超过16 MiB"}
            if(item.chunks.size!=item.total)return null
            pending.remove(id)
            val bytes=ByteArrayOutputStream(item.bytes).also { out->(0 until item.total).forEach {out.write(item.chunks.getValue(it))} }.toByteArray()
            val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            require(sha==item.sha) {"区域快照校验失败"}
            return SlLink.MapRegionPointResponse.parseFrom(bytes).also {
                require(it.transferId.isEmpty()&&it.totalChunks==0&&it.chunkData.isEmpty&&it.mapId==item.mapId&&it.mapVersion==item.version)
            }
        }catch(e:Exception) {pending.remove(id);throw e}
    }
}
