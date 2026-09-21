package sl_link.examples

import sl_link.*

/**
 * Example usage of SL-Link protocol in Kotlin/Android
 */
class SlLinkExample {
    
    private val parser = SlFrameParser()
    private val monitor = SlLinkMonitor()
    
    init {
        // Set source ID for this device (APP = 0x01)
        SlMessageBuilder.setSourceId(0x01u)
    }
    
    /**
     * Example: Send WiFi configuration to ESP32
     */
    fun sendWifiConfig(ssid: String, password: String): ByteArray {
        val mockPayload = ByteArray(0)
        return SlMessageBuilder.buildWifiConfigRaw(mockPayload, 0x10u)  // DST=ESP32
    }

    /**
     * Example: Parse received data from serial port
     */
    fun handleReceivedData(data: ByteArray) {
        val frames = parser.parse(data)
        
        for (frame in frames) {
            handleFrame(frame)
        }
    }
    
    /**
     * Handle a complete frame
     */
    private fun handleFrame(frame: SlFrame) {
        println("Received frame:")
        println("  MSG_ID: 0x${frame.msgId.toString(16).padStart(4, '0')}")
        println("  SRC_ID: 0x${frame.srcId.toString(16).padStart(2, '0')}")
        println("  SEQ: ${frame.seq}")
        println("  Payload size: ${frame.payload.size}")
        
        // Update link monitor
        val lost = monitor.update(frame.srcId, frame.seq)
        if (lost > 0) {
            println("  !!! Detected $lost lost packets from device 0x${frame.srcId.toString(16)}")
        }
        
        when (frame.msgId.toInt()) {
            0x0001 -> handleFusionSensorDataReport(frame)
            0x0002 -> handleImuDataReport(frame)
            0x0003 -> handleGnssDataReport(frame)
            0x0104 -> handleEsp32StatusResponse(frame)
            0x0105 -> handleGd32StatusResponse(frame)
            else -> println("  Unknown message type")
        }
    }
    
    private fun handleFusionSensorDataReport(frame: SlFrame) {

    }

    private fun handleImuDataReport(frame: SlFrame) {

    }

    private fun handleGnssDataReport(frame: SlFrame) {
        println("  GNSS-only data report received")
    }
    
    private fun handleEsp32StatusResponse(frame: SlFrame) {
        println("  ESP32 status response")
    }
    
    private fun handleGd32StatusResponse(frame: SlFrame) {
        println("  GD32 status response")
    }


    /**
     * Print current link quality statistics
     */
    fun printStats() {
        monitor.logStats()
    }
}
