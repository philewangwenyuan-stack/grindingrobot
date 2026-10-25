package com.sinelynx.grindingrobot.feature.device.viewmodel

/** Board-side Super-LIO watchdog owns thresholds and consecutive-frame confirmation. */
internal fun navigationRadarLabel(ready: Boolean?, reason: String, poseAvailable: Boolean): String {
    if (ready == true) return "正常"
    return when (reason.trim().lowercase()) {
        "registration_quality_failed", "pose_jump_exceeded", "localization_data_invalid",
        "localization_map_identity_mismatch" -> "未收敛"
        "lio_odom_unhealthy", "map_to_base_tf_invalid", "registration_diagnostics_stale",
        "livox_lidar_unhealthy", "livox_imu_unhealthy", "localization_heartbeat_timeout" -> "—"
        // On older firmware a valid reported pose is the only available TF/odometry evidence.
        else -> if (ready == null && poseAvailable) "正常" else "—"
    }
}

/** Never identify a map by list order or the numeric image frame ID. */
internal fun loadedMapLabel(available: Boolean, mapId: String?, names: Map<String, String>): String {
    if (!available) return "暂无地图"
    val id = mapId?.trim().orEmpty()
    return names[id]?.trim()?.takeIf { it.isNotEmpty() }
        ?: id.takeIf { it.isNotEmpty() && it != "LIVE_MAP" }
        ?: "—"
}


internal fun collisionDetailLabel(detail: String): String {
    val values = detail.split(" ").mapNotNull { item ->
        item.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()
    return when (values["reason"]) {
        "scan_timeout", "diagnostic_stale" -> "扫描数据过期"
        "tf_failed" -> "扫描坐标变换失败"
        "scan_invalid" -> "扫描数据无效"
        "side_obstacle" -> "侧方障碍：${values["side_m"] ?: "—"} m"
        "front_obstacle" -> "前方障碍：${values["front_m"] ?: "—"} m"
        "self_filter_invalid", "footprint_missing" -> "避障配置异常"
        "clearing" -> "等待有效扫描清障"
        "clear" -> "正常"
        else -> "避障状态未知"
    }
}
