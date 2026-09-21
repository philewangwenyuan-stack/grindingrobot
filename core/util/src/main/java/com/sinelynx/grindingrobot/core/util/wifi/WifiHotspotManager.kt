package com.sinelynx.grindingrobot.core.util.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi热点管理器
 * 用于检测和获取WiFi热点信息
 *
 * @param context 应用上下文
 * @author Dreamj
 */
@Singleton
class WifiHotspotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    /**
     * 检查WiFi热点是否开启
     *
     * @return true-热点已开启 false-热点未开启
     * @author Dreamj
     */
    fun isHotspotEnabled(): Boolean {
        return try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            LogUtils.e("检查热点状态失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取WiFi热点配置信息
     *
     * @return HotspotConfig热点配置 包含SSID和密码
     * @author Dreamj
     */
    fun getHotspotConfig(): HotspotConfig? {
        return try {
            // Android 8.0及以上，直接使用反射获取配置
            // 注意：Android 11+由于安全限制，反射可能失败
            // 建议使用默认配置或让用户手动输入
            getHotspotConfigViaReflection()
        } catch (e: Exception) {
            LogUtils.e("获取热点配置失败: ${e.message}")
            // 返回默认配置，用户可以在设置中查看实际的热点配置
            getDefaultHotspotConfig()
        }
    }
    
    /**
     * 通过反射获取热点配置
     *
     * @return HotspotConfig热点配置
     * @author Dreamj
     */
    private fun getHotspotConfigViaReflection(): HotspotConfig? {
        return try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("getWifiApConfiguration")
            method.isAccessible = true
            val config = method.invoke(wifiManager)
            
            if (config != null) {
                // 尝试获取SSID
                val ssid = try {
                    val ssidField = config.javaClass.getDeclaredField("SSID")
                    ssidField.isAccessible = true
                    ssidField.get(config) as? String
                } catch (e: Exception) {
                    // 尝试另一种字段名
                    try {
                        val ssidField = config.javaClass.getDeclaredField("mSsid")
                        ssidField.isAccessible = true
                        ssidField.get(config) as? String
                    } catch (e2: Exception) {
                        null
                    }
                } ?: ""
                
                // 尝试获取密码
                val password = try {
                    val passwordField = config.javaClass.getDeclaredField("preSharedKey")
                    passwordField.isAccessible = true
                    passwordField.get(config) as? String
                } catch (e: Exception) {
                    // 尝试另一种字段名
                    try {
                        val passwordField = config.javaClass.getDeclaredField("mPassphrase")
                        passwordField.isAccessible = true
                        passwordField.get(config) as? String
                    } catch (e2: Exception) {
                        null
                    }
                } ?: ""
                
                LogUtils.d("通过反射获取热点配置: SSID=$ssid")
                HotspotConfig(ssid, password)
            } else {
                LogUtils.w("getWifiApConfiguration返回null")
                getDefaultHotspotConfig()
            }
        } catch (e: Exception) {
            LogUtils.e("反射获取热点配置失败: ${e.message}")
            e.printStackTrace()
            getDefaultHotspotConfig()
        }
    }
    
    /**
     * 获取默认热点配置
     * 当无法通过反射获取配置时使用
     *
     * @return HotspotConfig默认热点配置
     * @author Dreamj
     */
    private fun getDefaultHotspotConfig(): HotspotConfig {
        // 获取设备型号作为默认SSID的一部分
        val deviceModel = Build.MODEL.replace(" ", "_")
        val defaultSsid = "sinelynxmc"
        
        LogUtils.w("无法获取热点配置，使用默认配置: SSID=$defaultSsid")
        LogUtils.w("请在系统设置中查看实际的热点配置")
        
        // 返回默认配置，密码留空表示需要用户确认
        return HotspotConfig(
            ssid = defaultSsid,
            password = "12345678" // 密码未知，可能需要用户手动输入
        )
    }
    
    /**
     * 启用WiFi热点
     * 注意：此功能在Android 8.0以上需要系统权限
     *
     * @param ssid 热点名称
     * @param password 热点密码
     * @return 是否成功启用
     * @author Dreamj
     */
    fun enableHotspot(ssid: String, password: String): Boolean {
        return try {
            // 注意：此功能在Android 8.0以上可能需要系统签名或root权限
            LogUtils.d("尝试启用WiFi热点: ssid=$ssid")
            
            // 这里只是一个占位实现，实际需要根据系统版本使用不同方法
            // Android 8.0以上需要使用LocalOnlyHotspot或OreoWifiManager等方式
            false
        } catch (e: Exception) {
            LogUtils.e("启用热点失败: ${e.message}")
            false
        }
    }
}

/**
 * WiFi热点配置数据类
 *
 * @param ssid 热点名称
 * @param password 热点密码
 * @author Dreamj
 */
data class HotspotConfig(
    val ssid: String,
    val password: String
)
