package com.sinelynx.grindingrobot.core.tcp.di

import com.sinelynx.grindingrobot.core.data.di.ApplicationScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.tcp.SlLinkManager
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.tcp.TcpService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * TCP模块 - 提供TCP通信和SL-Link协议相关依赖注入
 *
 * 注册的组件:
 * - TcpService (ESP32): ESP32 TCP服务
 * - TcpManager: ESP32 TCP管理器
 * - SlLinkManager: SL-Link协议管理器 (通过@Inject自动注册)
 *
 * @author Dreamj
 */
@Module
@InstallIn(SingletonComponent::class)
object TcpModule {

    /**
     * 提供ESP32 TCP服务实例
     *
     * @return TcpService实例
     * @author Dreamj
     */
    @Singleton
    @Provides
    @Esp32Tcp
    fun provideEsp32TcpService(): TcpService {
        return TcpService()
    }


    /**
     * 提供ESP32 TCP管理器实例
     *
     * @param tcpService ESP32 TCP服务实例
     * @param slLinkManager SL-Link协议管理器实例
     * @param appState 全局应用状态管理器
     * @return TcpManager实例
     * @author Dreamj
     */
    @Singleton
    @Provides
    fun provideTcpManager(
        @Esp32Tcp tcpService: TcpService,
        slLinkManager: SlLinkManager,
        appState: AppState
    ): TcpManager {
        return TcpManager(tcpService, slLinkManager, appState)
    }
}
