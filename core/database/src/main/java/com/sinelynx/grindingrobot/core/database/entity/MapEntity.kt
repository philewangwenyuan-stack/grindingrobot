package com.sinelynx.grindingrobot.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 研磨机地图相关数据
 */
@Entity(tableName = "mapTask")
data class MapEntity (
    /**
     * 主键，自增ID
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * 地图id
     */
    val mapId: String = "",

    /**
     * 地图名称
     */
    val mapName: String,

    /**
     * 地图创建时间
     */
    val mapTime: Long,

    /**
     * 地图面积
     */
    val mapArea: Float,

    /**
     * 地图执行耗时
     */
    val mapTimeConsuming: Float

)