package com.sinelynx.grindingrobot.core.network.di

import android.content.Context
import com.sinelynx.grindingrobot.core.network.service.AuthService
import com.sinelynx.grindingrobot.core.network.service.CommonService
import com.sinelynx.grindingrobot.core.network.service.EquipmentService
import com.sinelynx.grindingrobot.core.network.service.FileUploadService
import com.sinelynx.grindingrobot.core.network.service.OtaService
import com.sinelynx.grindingrobot.core.network.service.ApkOtaService
import com.sinelynx.grindingrobot.core.network.service.impl.FileUploadServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * 服务模块，提供所有网络服务接口的依赖注入
 * 为Hilt提供各种网络服务接口的实例
 *
 * @author Dreamj
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    /**
     * 提供通用基础服务接口
     *
     * @param retrofit Retrofit实例
     * @return 通用基础服务接口实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideCommonService(retrofit: Retrofit): CommonService {
        return retrofit.create(CommonService::class.java)
    }

    /**
     * 提供文件上传服务接口
     *
     * @param okHttpClient OkHttpClient实例
     * @param context 应用上下文
     * @return 文件上传服务接口实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideFileUploadService(
        @FileUploadQualifier okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): FileUploadService {
        return FileUploadServiceImpl(
            okHttpClient = okHttpClient,
            context = context
        )
    }

    /**
     * 提供认证服务接口
     *
     * @param retrofit Retrofit实例
     * @return 认证服务接口实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }

    /**
     * 提供OTA服务接口
     *
     * @param retrofit Retrofit实例
     * @return OTA服务接口实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideOtaService(retrofit: Retrofit): OtaService {
        return retrofit.create(OtaService::class.java)
    }

    /** 沿用现有 Retrofit；APK OTA 方法通过 @Url 传入完整地址，不另建客户端。 */
    @Provides
    @Singleton
    fun provideApkOtaService(retrofit: Retrofit): ApkOtaService = retrofit.create(ApkOtaService::class.java)

    /**
     * 提供设备服务接口
     *
     * @param retrofit Retrofit实例
     * @return 设备服务接口实现
     * @author Dreamj
     */
    @Provides
    @Singleton
    fun provideEquipmentService(retrofit: Retrofit): EquipmentService {
        return retrofit.create(EquipmentService::class.java)
    }
}
