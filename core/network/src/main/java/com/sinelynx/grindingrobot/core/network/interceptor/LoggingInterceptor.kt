package com.sinelynx.grindingrobot.core.network.interceptor

import com.sinelynx.grindingrobot.core.util.log.LogUtils
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志拦截器 - 记录网络请求日志
 *
 * @author Dreamj
 */
@Singleton
class LoggingInterceptor @Inject constructor() {

    /**
     * 初始化日志拦截器
     *
     * @return 自定义网络日志拦截器实例
     * @author Dreamj
     */
    @Inject
    fun init(): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                val response = chain.proceed(request)

                try {
                    val requestBuffer = Buffer()
                    if (request.body != null) {
                        request.body?.writeTo(requestBuffer)
                    }

                    val requestBody = requestBuffer.readUtf8()
                    val responseBody = response.peekBody(1024 * 1024).string()

                    LogUtils.d(
                        "Network Request: ${request.method} ${request.url}\n" +
                        "Headers: ${request.headers}\n" +
                        "Body: $requestBody\n" +
                        "Response Code: ${response.code}\n" +
                        "Response Body: $responseBody"
                    )
                } catch (e: IOException) {
                    LogUtils.d("Error reading request/response body: ${e.message}")
                }

                return response
            }
        }
    }
} 