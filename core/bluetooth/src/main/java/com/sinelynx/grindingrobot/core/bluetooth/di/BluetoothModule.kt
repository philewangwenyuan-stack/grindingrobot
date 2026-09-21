package com.sinelynx.grindingrobot.core.bluetooth.di

import android.content.Context
import com.sinelynx.grindingrobot.core.bluetooth.service.BluetoothService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 蓝牙模块 - 提供蓝牙相关依赖注入
 *
 * @author Dreamj
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    /**
     * 提供蓝牙服务实例
     *
     * @param context 应用上下文
     * @return 蓝牙服务实例
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideBluetoothService(
        @ApplicationContext context: Context
    ): BluetoothService {
        return BluetoothService(context)
    }
}