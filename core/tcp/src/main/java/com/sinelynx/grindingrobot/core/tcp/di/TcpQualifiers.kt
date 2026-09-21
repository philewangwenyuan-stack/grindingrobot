package com.sinelynx.grindingrobot.core.tcp.di

import javax.inject.Qualifier

/**
 * ESP32 TCP服务限定符
 * 用于区分ESP32和GD32的TCP服务实例
 *
 * @author Dreamj
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Esp32Tcp

/**
 * GD32 TCP服务限定符
 * 用于区分ESP32和GD32的TCP服务实例
 *
 * @author Dreamj
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Gd32Tcp
