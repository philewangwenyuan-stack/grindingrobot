package com.sinelynx.grindingrobot.core.network.interceptor

import com.sinelynx.grindingrobot.core.datastore.datasource.auth.AuthStoreDataSource
import com.sinelynx.grindingrobot.core.network.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** 协议、主机和端口都相同才视作业务服务，避免将业务 Token 发给 OTA 或 APK 域名。 */
internal fun isBusinessOrigin(url: HttpUrl, businessOrigin: HttpUrl): Boolean =
    url.scheme == businessOrigin.scheme && url.host == businessOrigin.host && url.port == businessOrigin.port

/**
 * 认证拦截器 - 添加授权头信息
 *
 * @param authStoreDataSource 认证数据存储源
 * @author Dreamj
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authStoreDataSource: AuthStoreDataSource
) : Interceptor {
    // 只解析一次业务服务源站；OTA 方法使用完整 URL，但仍经过同一个 OkHttpClient。
    private val businessOrigin = BuildConfig.BASE_URL.toHttpUrl()

    /**
     * 拦截请求并添加认证信息
     *
     * @param chain 拦截器链
     * @return 响应结果
     * @author Dreamj
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val url = originalRequest.url
        // 非业务源站直接放行，也避免读取本地 Token 时不必要的阻塞。
        if (!isBusinessOrigin(url, businessOrigin)) {
            return chain.proceed(originalRequest)
        }

        // 从 DataStore 获取 token，使用 runBlocking 调用挂起函数
        val token = runBlocking {
            authStoreDataSource.getToken() ?: ""
        }

        // 如果有Token，添加到请求头
        val request = if (token.isNotBlank()) {
            val bearer = if (token.startsWith("Bearer ")) token else "Bearer $token"
            originalRequest.newBuilder()
                .header("Authorization", bearer)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
