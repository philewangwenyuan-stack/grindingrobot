package com.sinelynx.grindingrobot.core.util.storage

/**
 * MMKV 键值管理类
 * 统一管理所有 MMKV 存储的键值，避免硬编码键名导致的错误
 *
 * @author Dreamj
 */
object MMKVKey {
    // GNSS 相关键值
    const val SELECT_GNSS = "SELECT_GNSS"  // 存储选中的 GNSS 数据

    const val CURRENT_SELECT_CAR = "current_select_car"  // 当前选中车辆校准信息

    /** 已应用的任务 ID（任务列表点击应用后缓存） */
    const val APPLIED_TASK_ID = "applied_task_id"

    /** 当前选中项目（缓存 ProjectEntity 的 JSON） */
    const val SELECT_PROJECT = "select_project"

    /** 上次成功的 TCP 连接 IP 地址（蓝牙配网获得） */
    const val TCP_IP_ADDRESS = "tcp_ip_address"

    /** 当前铲尖位置：0=左, 1=中, 2=右 */
    const val BUCKET_TOOTH_POSITION = "bucket_tooth_position"

    /** 是否已完成首次登录成功：false=首次进入，true=非首次 */
    const val FIRST_OPEN_APP = "first_open_app"
}