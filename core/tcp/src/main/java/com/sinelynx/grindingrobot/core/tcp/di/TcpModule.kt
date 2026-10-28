package com.sinelynx.grindingrobot.core.tcp.di

import android.content.Context
import com.sinelynx.grindingrobot.core.tcp.PairedTcpService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TcpModule {
    @Singleton
    @Provides
    fun providePairedTcpService(@ApplicationContext context: Context): PairedTcpService =
        PairedTcpService(context)
}
