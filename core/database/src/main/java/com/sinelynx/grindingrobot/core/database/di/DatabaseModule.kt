package com.sinelynx.grindingrobot.core.database.di

import android.content.Context
import androidx.room.Room
import com.sinelynx.grindingrobot.core.database.AppDatabase
import com.sinelynx.grindingrobot.core.database.dao.MapDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库模块
 * 负责提供数据库实例及相关DAO的依赖注入
 *
 * @author Dreamj
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供数据库实例
     *
     * @param context 应用上下文
     * @return 应用数据库实例
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(
        )
            .build()
    }

    @Provides
    @Singleton
    fun provideMapDao(database: AppDatabase): MapDao {
        return database.mapDao()
    }
}
