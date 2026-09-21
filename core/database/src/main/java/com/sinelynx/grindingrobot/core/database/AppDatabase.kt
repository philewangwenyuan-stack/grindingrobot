package com.sinelynx.grindingrobot.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sinelynx.grindingrobot.core.database.dao.MapDao
import com.sinelynx.grindingrobot.core.database.entity.MapEntity

/**
 * 应用数据库
 * 管理车辆校准信息，铲斗、GNSS相关配置等本地数据
 *
 * @author Dreamj
 */
@Database(
    entities = [
        MapEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 获取地图DAO
     */
    abstract fun mapDao(): MapDao

    companion object {
        const val DATABASE_NAME = "sinel-grindingrobot-database"
    }
}
