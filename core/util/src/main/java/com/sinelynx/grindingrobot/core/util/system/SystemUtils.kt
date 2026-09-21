package com.sinelynx.grindingrobot.core.util.system

import android.content.Context
import android.content.Intent
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统相关工具类
 * 当前主要用于控制系统蓝牙开关
 *
 * 设计对齐 WifiHotspotManager：
 * - 使用 @Singleton + @Inject 构造函数
 * - 通过 @ApplicationContext 注入全局 Context
 * - 通过实例方法对外提供功能（由 Hilt 注入使用）
 */
@Singleton
class SystemUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 直接通过系统 UI 广播控制蓝牙开关
     *
     * @param open true 为打开，false 为关闭
     */
    fun openBlueTooth(open: Boolean) {
        LogUtils.d("SystemUtils openBlueTooth: $open")
        val actionSetting = Intent("com.intent.systemui.bluebooth_control").apply {
            putExtra("bluebooth_status", open) // true 为打开，false 为关闭
        }
        context.sendBroadcast(actionSetting)
    }

    /**
     * 通过系统 UI 广播控制热点开关
     *
     * @param open true 为打开，false 为关闭
     * @param ssid 可选热点名称，如果不为空则一并设置
     * @param password 可选热点密码，如果不为空则一并设置
     */
    fun openHotspot(open: Boolean, ssid: String? = null, password: String? = null) {
        LogUtils.d("SystemUtils openHotspot: $open, ssid=$ssid")
        val actionSetting = Intent("com.intent.systemui.hotspot_control").apply {
            putExtra("hotspot_status", open)
            if (!ssid.isNullOrEmpty()) {
                putExtra("account", ssid)
            }
            if (!password.isNullOrEmpty()) {
                putExtra("password", password)
            }
        }
        context.sendBroadcast(actionSetting)
    }

    /**
     * 通过系统 UI 广播控制底部状态栏显示/隐藏
     *
     * @param hide true 为隐藏，false 为显示
     */
    fun setBottomStatusBarHidden(hide: Boolean) {
        LogUtils.d("SystemUtils setBottomStatusBarHidden: $hide")
        val actionSetting = Intent("com.intent.systemui.show_hide_bar").apply {
            putExtra("show_hide_bar", hide) // true 为隐藏，false 为显示
        }
        context.sendBroadcast(actionSetting)
    }

    /**
     * 通过系统 UI 广播修改系统时间
     *
     * 注意：是否真正生效取决于设备系统是否支持该自定义广播以及权限控制。
     *
     * @param year 年，例如 2026
     * @param month 月，1-12
     * @param day 日，1-31
     * @param hour 小时，0-23
     * @param minute 分钟，0-59
     */
    fun modifySystemTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        LogUtils.d("SystemUtils modifySystemTime: $year-$month-$day $hour:$minute")
        val actionSetting = Intent("com.intent.modify_time").apply {
            putExtra("year", year)
            putExtra("month", month)
            putExtra("day", day)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        context.sendBroadcast(actionSetting)
    }

    /**
     * 通过反射调用隐藏的 SystemProperties.set(String, String)
     */
    private fun setSystemProperty(key: String, value: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("set", String::class.java, String::class.java)
            method.invoke(null, key, value)
            LogUtils.d("SystemUtils setSystemProperty: $key=$value")
        } catch (e: Throwable) {
            LogUtils.e("SystemUtils setSystemProperty error: ${e.message}")
        }
    }

    /**
     * 切换以太网为 DHCP（动态 IP）模式
     * 等价于 SystemProperties.set("persist.xxxxx.ethernet_static", "0")
     */
    fun setEthernetDhcpMode() {
        LogUtils.d("SystemUtils setEthernetDhcpMode")
        setSystemProperty("persist.xxxxx.ethernet_static", "0")
    }

    /**
     * 配置以太网为静态 IP 模式并写入相关网络参数。
     */
    fun setEthernetStaticConfig(
        ip: String,
        mask: String,
        gateway: String,
        dns1: String,
        dns2: String
    ) {
        LogUtils.d("SystemUtils setEthernetStaticConfig ip=$ip mask=$mask gateway=$gateway dns1=$dns1 dns2=$dns2")
        setSystemProperty("persist.xxxxx.ethernet_static", "1")
        setSystemProperty("persist.xxxxx.ethernet_static_ip", ip)
        setSystemProperty("persist.xxxxx.ethernet_static_mask", mask)
        setSystemProperty("persist.xxxxx.ethernet_static_gateway", gateway)
        setSystemProperty("persist.xxxxx.ethernet_static_dns1", dns1)
        setSystemProperty("persist.xxxxx.ethernet_static_dns2", dns2)

        //设置完后发送广播使以太网参数生效
        val actionSetting = Intent("com.intent.ethernet_change")
        context.sendBroadcast(actionSetting)
    }
}