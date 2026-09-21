package com.sinelynx.grindingrobot.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sinelynx.grindingrobot.core.database.dao.BucketDao
import com.sinelynx.grindingrobot.core.database.dao.CarDao
import com.sinelynx.grindingrobot.core.database.dao.GnssDao
import com.sinelynx.grindingrobot.core.database.dao.ProjectDao
import com.sinelynx.grindingrobot.core.database.entity.BucketEntity
import com.sinelynx.grindingrobot.core.database.entity.CarEntity
import com.sinelynx.grindingrobot.core.database.entity.GnssEntity
import com.sinelynx.grindingrobot.core.database.entity.ProjectEntity

/**
 * 应用数据库
 *
 * @author Dreamj
 */
@Database(
    entities = [
        CarEntity::class,
        BucketEntity::class,
        GnssEntity::class,
        ProjectEntity::class,
        PointEntity::class,
        PointCalibrationEntity::class,
        TaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 获取车辆DAO
     *
     * @return 车辆数据访问对象
     * @author Dreamj
     */
    abstract fun carDao(): CarDao

    /**
     * 获取GNSS数据源接口
     *
     * @return GNSS数据源接口
     * @author Dreamj
     */
    abstract fun gnssDao(): GnssDao

    /**
     * 获取铲斗数据源接口
     *
     * @return 铲斗数据源接口
     * @author Dreamj
     */
    abstract fun bucketDao(): BucketDao

    /**
     * 获取项目数据源接口
     */
    abstract fun projectDao(): ProjectDao

    abstract fun pointDao(): PointDao

    abstract fun pointCalibrationDao(): PointCalibrationDao

    abstract fun taskDao(): TaskDao


    companion object {
        const val DATABASE_NAME = "grindingrobot-database"
    }
}
