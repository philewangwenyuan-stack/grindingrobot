package sl_link

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SL-LinkA message builder for creating protocol frames.
 */
object SlMessageBuilder {

    private var sequenceNumber: UShort = 0u
    private var sourceId: UByte = 0x01u

    fun setSourceId(srcId: UByte) {
        sourceId = srcId
    }

    private fun getNextSeq(): UShort {
        val current = sequenceNumber
        sequenceNumber = ((sequenceNumber.toInt() + 1) and 0xFFFF).toUShort()
        return current
    }

    fun buildFrame(
        msgId: UShort,
        payloadData: ByteArray,
        dstId: UByte,
        compId: UByte,
        flags: UByte = 0u,
        ackSeq: UShort = 0u
    ): ByteArray {
        val payloadLen = payloadData.size
        val frameSize = SlFrame.HEADER_SIZE + payloadLen + 2 + 1

        val buffer = ByteBuffer.allocate(frameSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(SlFrame.STX1.toByte())
        buffer.put(SlFrame.STX2.toByte())
        buffer.put(0x01.toByte())
        buffer.put(flags.toByte())
        buffer.putShort(getNextSeq().toShort())
        buffer.putShort(ackSeq.toShort())
        buffer.put(sourceId.toByte())
        buffer.put(dstId.toByte())
        buffer.put(compId.toByte())
        buffer.putShort(msgId.toShort())
        buffer.putShort(payloadLen.toShort())
        buffer.put(payloadData)

        val crcData = buffer.array().copyOfRange(0, SlFrame.HEADER_SIZE + payloadLen)
        val crc = Crc16.calculate(crcData)
        buffer.putShort(crc.toShort())
        buffer.put(SlFrame.TAIL.toByte())
        return buffer.array()
    }

    fun buildWifiConfigRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0201u, serializedData, dstId, 0x04u)
    }

    fun buildSettingsReadRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0203u, serializedData, dstId, 0x05u)
    }

    fun buildSettingsWriteRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0205u, serializedData, dstId, 0x05u)
    }

    fun buildCameraFrameRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0302u, serializedData, dstId, 0x06u)
    }

    fun buildMapRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0304u, serializedData, dstId, 0x06u)
    }

    /** 切换地图模式（0x0512），例如进入 LIVE_MAP 建图模式。 */
    fun buildMapModeRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0512u, serializedData, dstId, 0x08u)
    }

    fun buildControlCommandRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0401u, serializedData, dstId, 0x07u)
    }

    fun buildTaskConfigRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0500u, serializedData, dstId, 0x08u)
    }

    fun buildTaskCommandRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0502u, serializedData, dstId, 0x08u)
    }

    fun buildPathPointPlanRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0505u, serializedData, dstId, 0x08u)
    }

    fun buildMapPreviewRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0507u, serializedData, dstId, 0x08u)
    }

    /** 0x052E 只查询区域与工作区起终点，对应 0x052F；不替换 0x0305 返回的底图。 */
    fun buildMapRegionPointRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x052Eu, serializedData, dstId, 0x08u)
    }

    fun buildMapEditCommandRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0509u, serializedData, dstId, 0x08u)
    }

    fun buildVideoStreamInfoRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x050Cu, serializedData, dstId, 0x08u)
    }

    fun buildPathPlanRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x050Eu, serializedData, dstId, 0x08u)
    }

    fun buildMapSaveRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0518u, serializedData, dstId, 0x08u)
    }

    fun buildMapDeleteRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0516u, serializedData, dstId, 0x08u)
    }

    fun buildMapCatalogRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0514u, serializedData, dstId, 0x08u)
    }

    fun buildMapMetricsRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x051Au, serializedData, dstId, 0x08u)
    }

    fun buildTaskResultRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x051Cu, serializedData, dstId, 0x08u)
    }

    fun buildTaskExecutionHistoryRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0530u, serializedData, dstId, 0x08u)
    }

    fun buildTaskExecutionDeleteRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0534u, serializedData, dstId, 0x08u)
    }

    fun buildSystemCacheClearRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0536u, serializedData, dstId, 0x08u)
    }

    fun buildTaskTrajectoryRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0532u, serializedData, dstId, 0x08u)
    }

    fun buildLiveMapCacheClearRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x051Eu, serializedData, dstId, 0x08u)
    }

    fun buildRadarMapCacheClearRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0520u, serializedData, dstId, 0x08u)
    }

    fun buildMapImportToRadarRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0522u, serializedData, dstId, 0x08u)
    }

    /** 地图对齐专用帧：0x0524，payload 为 MapAlignmentRequest 的 protobuf 字节。 */
    fun buildMapAlignmentRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0524u, serializedData, dstId, 0x08u)
    }

    fun buildRadarSystemStatusRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0526u, serializedData, dstId, 0x08u)
    }

    fun buildRadarMapSyncRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x0528u, serializedData, dstId, 0x08u)
    }

    fun buildRadarRelocalizationRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x052Au, serializedData, dstId, 0x08u)
    }

    fun buildRadarRelocalizationStatusRequestRaw(serializedData: ByteArray, dstId: UByte): ByteArray {
        return buildFrame(0x052Cu, serializedData, dstId, 0x08u)
    }

    fun buildAck(receivedFrame: SlFrame, payloadData: ByteArray = ByteArray(0)): ByteArray {
        return buildFrame(
            msgId = receivedFrame.msgId,
            payloadData = payloadData,
            dstId = receivedFrame.srcId,
            compId = receivedFrame.compId,
            flags = 0x00u,
            ackSeq = receivedFrame.seq
        )
    }
}
