package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.data.state.DeviceEventLevel

/** Edge detection for reports and transport callbacks, including duplicate failure callbacks. */
internal class DeviceEventTracker(private val emit: (DeviceEventLevel, String) -> Unit) {
    private var connected = false
    private var hasConnected = false
    private var collision: Boolean? = null
    private var chassis: Boolean? = null

    @Synchronized
    fun onConnected() {
        if (connected) return
        connected = true
        emit(DeviceEventLevel.NORMAL, if (hasConnected) "APP 与工控机已重新连接" else "APP 与工控机已连接")
        hasConnected = true
        collision = null
        chassis = null
    }

    @Synchronized
    fun onDisconnected() {
        if (!connected) return // Initial failed attempts are not a lost connection.
        connected = false
        collision = null
        chassis = null
        emit(DeviceEventLevel.ERROR, "APP 与工控机连接已断开")
    }

    @Synchronized
    fun onStatus(collisionImminent: Boolean, chassisConnected: Boolean?) {
        if (!connected) return
        if (collisionImminent && collision != true) {
            emit(DeviceEventLevel.WARNING, "RPP 检测到障碍物，已触发避障停车")
        } else if (!collisionImminent && collision == true) {
            emit(DeviceEventLevel.NORMAL, "RPP 障碍停车已解除")
        }
        collision = collisionImminent
        // Null means unsupported/unknown, never infer a disconnection from an absent field.
        if (chassisConnected != null) {
            if (!chassisConnected && chassis != false) {
                emit(DeviceEventLevel.ERROR, "工控机与底盘连接已断开")
            } else if (chassisConnected && chassis == false) {
                emit(DeviceEventLevel.NORMAL, "工控机与底盘连接已恢复")
            }
            chassis = chassisConnected
        }
    }
}
