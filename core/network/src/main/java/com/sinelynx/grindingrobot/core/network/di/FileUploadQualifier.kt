package com.sinelynx.grindingrobot.core.network.di

import javax.inject.Qualifier

/**
 * 文件上传专用的 OkHttpClient 限定符
 *
 * @author Dreamj
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class FileUploadQualifier