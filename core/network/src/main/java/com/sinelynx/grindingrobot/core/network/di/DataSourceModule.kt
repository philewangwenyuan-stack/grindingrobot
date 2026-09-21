package com.sinelynx.grindingrobot.core.network.di

import com.sinelynx.grindingrobot.core.network.datasource.auth.AuthNetworkDataSource
import com.sinelynx.grindingrobot.core.network.datasource.auth.AuthNetworkDataSourceImpl
import com.sinelynx.grindingrobot.core.network.datasource.common.CommonNetworkDataSource
import com.sinelynx.grindingrobot.core.network.datasource.common.CommonNetworkDataSourceImpl
import com.sinelynx.grindingrobot.core.network.datasource.equipment.EquipmentNetworkDataSource
import com.sinelynx.grindingrobot.core.network.datasource.equipment.EquipmentNetworkDataSourceImpl
import com.sinelynx.grindingrobot.core.network.datasource.fileupload.FileUploadNetworkDataSource
import com.sinelynx.grindingrobot.core.network.datasource.fileupload.FileUploadNetworkDataSourceImpl
import com.sinelynx.grindingrobot.core.network.datasource.ota.OtaNetworkDataSource
import com.sinelynx.grindingrobot.core.network.datasource.ota.OtaNetworkDataSourceImpl
import com.sinelynx.grindingrobot.core.network.service.AuthService
import com.sinelynx.grindingrobot.core.network.service.CommonService
import com.sinelynx.grindingrobot.core.network.service.EquipmentService
import com.sinelynx.grindingrobot.core.network.service.FileUploadService
import com.sinelynx.grindingrobot.core.network.service.OtaService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据源模块，提供所有网络数据源的依赖注入
 * 为Hilt提供各种网络数据源的实例
 *
 * @author Dreamj
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    /**
     * 提供通用基础网络数据源
     *
     * @param commonService 通用基础服务接口
     * @return 通用基础网络数据源实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideCommonNetworkDataSource(commonService: CommonService): CommonNetworkDataSource {
        return CommonNetworkDataSourceImpl(commonService)
    }

    /**
     * 提供文件上传网络数据源
     *
     * @param commonNetworkDataSource 通用网络数据源，用于获取上传配置
     * @param fileUploadService 文件上传服务接口
     * @return 文件上传网络数据源实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideFileUploadNetworkDataSource(
        commonNetworkDataSource: CommonNetworkDataSource,
        fileUploadService: FileUploadService
    ): FileUploadNetworkDataSource {
        return FileUploadNetworkDataSourceImpl(
            commonNetworkDataSource,
            fileUploadService
        )
    }

    /**
     * 提供认证网络数据源
     *
     * @param authService 认证服务接口
     * @return 认证网络数据源实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideAuthNetworkDataSource(authService: AuthService): AuthNetworkDataSource {
        return AuthNetworkDataSourceImpl(authService)
    }

    /**
     * 提供OTA网络数据源
     *
     * @param otaService OTA服务接口
     * @return OTA网络数据源实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideOtaNetworkDataSource(otaService: OtaService): OtaNetworkDataSource {
        return OtaNetworkDataSourceImpl(otaService)
    }

    /**
     * 提供设备网络数据源
     *
     * @param equipmentService 设备服务接口
     * @return 设备网络数据源实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideEquipmentNetworkDataSource(equipmentService: EquipmentService): EquipmentNetworkDataSource {
        return EquipmentNetworkDataSourceImpl(equipmentService)
    }
}