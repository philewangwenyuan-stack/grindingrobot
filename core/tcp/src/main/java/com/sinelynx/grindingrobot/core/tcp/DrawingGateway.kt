package com.sinelynx.grindingrobot.core.tcp

import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sl_link.SlLink
import sl_link.SlMessageBuilder
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal object DrawingReplies {
    val responses=MutableSharedFlow<SlLink.DrawingDraftResponse>(extraBufferCapacity=32)
}

@Singleton
class DrawingGateway @Inject constructor(private val tcp: TcpManager) {
    private val mutex=Mutex()
    private suspend fun exchange(request: SlLink.DrawingDraftRequest, timeout:Long=15000):SlLink.DrawingDraftResponse = coroutineScope {
        val wait=async(start=CoroutineStart.UNDISPATCHED) {
            withTimeout(timeout) { DrawingReplies.responses.first { it.requestId==request.requestId } }
        }
        try {
            check(tcp.sendData(SlMessageBuilder.buildDrawingDraftRequestRaw(request.toByteArray(),0x10u))) { "图纸请求发送失败，请检查连接" }
            wait.await().also { check(it.result==SlLink.ResultCode.RESULT_SUCCESS) {
                it.checksList.filter { c->c.level=="error" }.joinToString("；") { c->c.message }.ifBlank { it.message.ifBlank { "设备拒绝图纸请求" } }
            } }
        } finally { wait.cancel() }
    }
    suspend fun capabilities(mapId:String):SlLink.DrawingDraftResponse = mutex.withLock {
        exchange(SlLink.DrawingDraftRequest.newBuilder().setMapId(mapId).setRequestId(UUID.randomUUID().toString())
            .setOperation(SlLink.DrawingDraftOperation.DRAWING_CAPABILITIES).build(),4000)
    }
    suspend fun upload(mapId:String, draftId:String, payload:SlLink.DrawingLayerPayload, expected:Int):SlLink.DrawingDraftResponse = mutex.withLock {
        withContext(Dispatchers.Default) {
            val bytes=payload.toByteArray();require(bytes.size<=8*1024*1024) { "地基边界超过8 MiB，请精简" }
            val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val count=(bytes.size+49151)/49152
            var response:SlLink.DrawingDraftResponse?=null
            for(i in 0 until count) {
                ensureActive()
                val request=SlLink.DrawingDraftRequest.newBuilder().setRequestId(UUID.randomUUID().toString())
                    .setMapId(mapId).setDraftId(draftId).setOperation(SlLink.DrawingDraftOperation.DRAWING_UPLOAD)
                    .setChunkIndex(i).setTotalChunks(count).setExpectedLayerVersion(expected).setPayloadSha256(sha)
                    .setData(ByteString.copyFrom(bytes,i*49152,minOf(49152,bytes.size-i*49152))).build()
                response=exchange(request)
            }
            check(response?.complete==true) { "图纸草稿未完整接收" };response!!
        }
    }
    suspend fun validate(mapId:String,draftId:String,version:Int)=mutex.withLock {
        exchange(SlLink.DrawingDraftRequest.newBuilder().setRequestId(UUID.randomUUID().toString()).setMapId(mapId)
            .setDraftId(draftId).setDraftVersion(version).setOperation(SlLink.DrawingDraftOperation.DRAWING_VALIDATE).build())
    }
    suspend fun discard(mapId:String,draftId:String)=mutex.withLock {
        exchange(SlLink.DrawingDraftRequest.newBuilder().setRequestId(UUID.randomUUID().toString()).setMapId(mapId)
            .setDraftId(draftId).setOperation(SlLink.DrawingDraftOperation.DRAWING_DISCARD).build())
    }
}
