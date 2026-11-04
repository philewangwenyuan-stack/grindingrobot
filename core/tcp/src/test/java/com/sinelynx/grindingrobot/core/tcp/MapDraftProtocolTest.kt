package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.model.state.MapEditBinding
import org.junit.Assert.*
import org.junit.Test
import sl_link.SlLink
import sl_link.SlMessageBuilder

class MapDraftProtocolTest {
    @Test fun draftWorkflowUsesTheDataChannelAndStableRequestIdentity() {
        val request = SlLink.MapDraftRequest.newBuilder().setRequestId("freeze-once")
            .setOperation(SlLink.MapDraftOperation.MAP_DRAFT_FREEZE).build()
        val frame = SlMessageBuilder.buildFrame(0x053Cu, request.toByteArray(), 0x10u, 0x08u)
        assertEquals(0x053C, ChannelRouting.id(frame))
        assertFalse(ChannelRouting.control(frame))
        assertEquals("freeze-once", SlLink.MapDraftRequest.parseFrom(request.toByteArray()).requestId)
    }
    @Test fun processAcknowledgementAdvancesOnlyTheMatchingDraft() {
        val session = com.sinelynx.grindingrobot.core.model.state.MapEditSession
        session.adopt(MapEditBinding("draft_a", "revision", 7))
        try {
            val reply = SlLink.SettingsWriteResponse.newBuilder().setResult(SlLink.ResultCode.RESULT_SUCCESS)
                .setEditBinding(MapEditBinding("draft_a", "revision", 8).toProto()).build()
            acknowledgeMapDraftEdit(reply.result, reply.editBinding)
            assertEquals(8, session.binding!!.editVersion)
            session.adopt(MapEditBinding("draft_b", "other", 0))
            acknowledgeMapDraftEdit(reply.result, reply.editBinding)
            assertEquals(0, session.binding!!.editVersion)
        } finally { session.adopt(null) }
    }
    @Test fun allEditAndPreviewRequestsCarryTheSameBaseAndVersion() {
        val binding = MapEditBinding("draft_a", "revision", 7).toProto()
        val edit = SlLink.MapEditCommand.newBuilder().setEditBinding(binding).build()
        val plan = SlLink.PathPointPlanRequest.newBuilder().setEditBinding(binding).build()
        val save = SlLink.MapSaveRequest.newBuilder().setEditBinding(binding).build()
        assertEquals(binding, SlLink.MapEditCommand.parseFrom(edit.toByteArray()).editBinding)
        assertEquals(binding, SlLink.PathPointPlanRequest.parseFrom(plan.toByteArray()).editBinding)
        assertEquals(binding, SlLink.MapSaveRequest.parseFrom(save.toByteArray()).editBinding)
    }
}
