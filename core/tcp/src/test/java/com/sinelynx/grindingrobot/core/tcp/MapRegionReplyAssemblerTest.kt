package com.sinelynx.grindingrobot.core.tcp

import com.google.protobuf.ByteString
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink
import java.security.MessageDigest

class MapRegionReplyAssemblerTest {
    private fun frames():Pair<SlLink.MapRegionPointResponse,List<SlLink.MapRegionPointResponse>> {
        val response=SlLink.MapRegionPointResponse.newBuilder().setMapId("map").setMapVersion(7).setMessage("x".repeat(140000))
            .addObstacleRegions(SlLink.PolygonRegion.newBuilder().setRegionId("drawing:one").setSource("drawing")).build()
        val bytes=response.toByteArray();val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val chunks=bytes.asList().chunked(49152)
        return response to chunks.mapIndexed { n,b->SlLink.MapRegionPointResponse.newBuilder().setMapId("map").setMapVersion(7)
            .setTransferId("transfer").setChunkIndex(n).setTotalChunks(chunks.size).setPayloadSha256(sha).setChunkData(ByteString.copyFrom(b.toByteArray())).build() }
    }
    @Test fun legacySmallResponseIsUnchanged() {
        val reply=SlLink.MapRegionPointResponse.newBuilder().setMapId("map").build()
        assertSame(reply,MapRegionReplyAssembler().accept(reply))
    }
    @Test fun completeOutOfOrderSnapshotPublishesOnceWithAllDrawingData() {
        val (expected,chunks)=frames();val assembler=MapRegionReplyAssembler()
        assertNull(assembler.accept(chunks.last()));assertNull(assembler.accept(chunks.last()))
        assertNull(assembler.accept(chunks.first()))
        assertEquals(expected,assembler.accept(chunks[1]))
        assertTrue(chunks.all {it.serializedSize<65535})
    }
    @Test(expected=IllegalArgumentException::class) fun corruptSnapshotIsNeverPublished() {
        val (_,chunks)=frames();val assembler=MapRegionReplyAssembler()
        chunks.dropLast(1).forEach {assertNull(assembler.accept(it))}
        assembler.accept(chunks.last().toBuilder().setChunkData(ByteString.copyFromUtf8("wrong")).build())
    }
    @Test(expected=IllegalArgumentException::class) fun conflictingDuplicateIsRejected() {
        val chunk=frames().second.first();val assembler=MapRegionReplyAssembler();assembler.accept(chunk)
        assembler.accept(chunk.toBuilder().setChunkData(ByteString.copyFromUtf8("other")).build())
    }
    @Test fun expiredAndDisconnectedSnapshotsCannotComplete() {
        var clock=0L;val (_,chunks)=frames();val assembler=MapRegionReplyAssembler {clock}
        assembler.accept(chunks.first());clock=16_000_000_000L
        chunks.drop(1).forEach {assertNull(assembler.accept(it))}
        assembler.clear();assertNull(assembler.accept(chunks.first()))
    }
    @Test(expected=IllegalArgumentException::class) fun unboundedChunkCountIsRejected() {
        MapRegionReplyAssembler().accept(frames().second.first().toBuilder().setTotalChunks(343).build())
    }
}
