package com.sinelynx.grindingrobot.core.tcp

import com.sinelynx.grindingrobot.core.data.state.DeviceEventLevel

/** Edge detection for reports and transport callbacks, including duplicate failure callbacks. */
internal class DeviceEventTracker(private val emit: (DeviceEventLevel, String) -> Unit) {
    private var connected = false
    private var hasConnected = false
    private var collision: Boolean? = null
    private var chassis: Boolean? = null
    private var localizationPaused: Boolean? = null
    private var localizationRecoveryNotified = false
    private var localizationNeedsRecovery = false

    private fun localizationReason(code: String): String = when (code) {
        "registration_quality_failed" -> "配准质量连续不合格"
        "pose_jump_exceeded" -> "定位位姿突跳"
        "livox_lidar_unhealthy" -> "雷达点云失效"
        "livox_imu_unhealthy" -> "雷达惯导失效"
        "lio_odom_unhealthy" -> "定位里程计失效"
        "registration_diagnostics_stale" -> "配准诊断中断"
        "map_to_base_tf_invalid" -> "坐标变换失效"
        "localization_data_invalid" -> "定位数据无效"
        "localization_heartbeat_timeout" -> "定位管理器心跳中断"
        "localization_map_identity_mismatch" -> "地图身份不一致"
        else -> "定位状态异常"
    }

    @Synchronized
    fun onConnected() {
        if (connected) return
        connected = true
        emit(DeviceEventLevel.NORMAL, if (hasConnected) "APP 与工控机已重新连接" else "APP 与工控机已连接")
        hasConnected = true
        collision = null
        chassis = null
        localizationPaused = null
        localizationRecoveryNotified = false
        localizationNeedsRecovery = false
    }

    @Synchronized
    fun onDisconnected() {
        if (!connected) return // Initial failed attempts are not a lost connection.
        connected = false
        collision = null
        chassis = null
        localizationPaused = null
        localizationRecoveryNotified = false
        localizationNeedsRecovery = false
        emit(DeviceEventLevel.ERROR, "APP 与工控机连接已断开")
    }

    @Synchronized
    fun onStatus(
        collisionImminent: Boolean,
        chassisConnected: Boolean?,
        localizationReady: Boolean? = null,
        localizationPauseLatched: Boolean? = null,
        localizationStatusReason: String = ""
    ) {
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
        if (localizationPauseLatched != null) {
            if (localizationPauseLatched && localizationPaused != true) {
                emit(DeviceEventLevel.ERROR,
                    "定位异常，任务已暂停，行走和磨盘已下发停止指令：${localizationReason(localizationStatusReason)}")
                localizationRecoveryNotified = false
            }
            if (localizationPauseLatched && localizationReady == false) localizationNeedsRecovery = true
            if (localizationPauseLatched && localizationReady == true && localizationNeedsRecovery && !localizationRecoveryNotified) {
                emit(DeviceEventLevel.NORMAL, "定位已恢复，任务仍暂停，请确认后点击继续。")
                localizationRecoveryNotified = true
            }
            if (!localizationPauseLatched) {
                localizationRecoveryNotified = false
                localizationNeedsRecovery = false
            }
            localizationPaused = localizationPauseLatched
        }
    }
}
