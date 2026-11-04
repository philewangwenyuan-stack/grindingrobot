package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapEditBinding
import com.sinelynx.grindingrobot.core.model.state.MapEditSession
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sl_link.SlLink
import sl_link.SlMessageBuilder

internal object MapDraftReplies {
    val responses = MutableSharedFlow<SlLink.MapDraftResponse>(extraBufferCapacity = 16)
}

internal fun MapEditBinding.toProto(): SlLink.MapDraftBinding = SlLink.MapDraftBinding.newBuilder()
    .setMapId(mapId).setBaseRevision(baseRevision).setEditVersion(editVersion).build()

internal fun acknowledgeMapDraftEdit(result: SlLink.ResultCode, binding: SlLink.MapDraftBinding) {
    if (result == SlLink.ResultCode.RESULT_SUCCESS && binding.mapId.isNotBlank())
        MapEditSession.edited(binding.mapId, binding.baseRevision, binding.editVersion)
}

@Singleton
class MapDraftGateway @Inject constructor(private val tcp: TcpManager) {
    private val mutex = Mutex()
    private var freezeRequestId = UUID.randomUUID().toString()
    private var restartRequestId: String? = null

    private suspend fun exchange(operation: SlLink.MapDraftOperation, mapId: String = "",
                                 id: String = UUID.randomUUID().toString()): SlLink.MapDraftResponse = coroutineScope {
        val request = SlLink.MapDraftRequest.newBuilder().setRequestId(id).setOperation(operation).setMapId(mapId)
        MapEditSession.forMap(mapId)?.let { request.setEditBinding(it.toProto()) }
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(if (operation == SlLink.MapDraftOperation.MAP_DRAFT_FREEZE) 120_000L else 30_000L) {
                MapDraftReplies.responses.first { it.requestId == id }
            }
        }
        try {
            check(tcp.sendData(SlMessageBuilder.buildFrame(0x053Cu, request.build().toByteArray(), 0x10u, 0x08u))) {
                "地图草稿请求发送失败，请检查连接"
            }
            reply.await().also { response ->
                check(response.result == SlLink.ResultCode.RESULT_SUCCESS) { response.message }
                check(response.featureVersion >= 1) { "设备不支持固定地图草稿，请同步升级设备" }
                if (operation != SlLink.MapDraftOperation.MAP_DRAFT_QUERY) {
                    MapEditSession.adopt(response.binding.takeIf { response.exists && it.baseRevision.isNotBlank() }
                        ?.let { MapEditBinding(it.mapId, it.baseRevision, it.editVersion) })
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw java.io.IOException("地图草稿操作超时，请重试；设备已完成的操作不会重复执行", timeout)
        } finally { reply.cancel() }
    }

    suspend fun query() = mutex.withLock { exchange(SlLink.MapDraftOperation.MAP_DRAFT_QUERY) }
    suspend fun resume(mapId: String) = mutex.withLock { exchange(SlLink.MapDraftOperation.MAP_DRAFT_RESUME, mapId) }
    suspend fun freeze() = mutex.withLock {
        exchange(SlLink.MapDraftOperation.MAP_DRAFT_FREEZE, id = freezeRequestId)
    }
    suspend fun restart() = mutex.withLock {
        val id = restartRequestId ?: UUID.randomUUID().toString().also { restartRequestId = it }
        exchange(SlLink.MapDraftOperation.MAP_DRAFT_RESTART, id = id).also {
            freezeRequestId = UUID.randomUUID().toString()
            restartRequestId = null
        }
    }
}
