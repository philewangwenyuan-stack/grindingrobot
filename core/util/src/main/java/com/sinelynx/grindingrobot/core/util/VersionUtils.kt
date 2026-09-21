package com.sinelynx.grindingrobot.core.util

import android.content.Context
import com.sinelynx.grindingrobot.core.util.DeviceUtils.getVersionName
import com.sinelynx.grindingrobot.core.util.`package`.PackageUtils
import kotlin.text.split

/**
 * 应用版本信息工具（供 OTA 等模块使用，避免直接依赖 package 包名）
 */
object VersionUtils {

    fun getAppVersionName(context: Context): String =
        PackageUtils.getCurrentVersionName(context)

    fun getAppVersionCode(context: Context): Int {
        val versionName = getVersionName(context)
        if (versionName.isNullOrBlank()){
            return 0
        }
        val versionArray = versionName.split(".")
        return runCatching { versionArray[0].toInt() * 10000 + versionArray[1].toInt() * 100 + versionArray[2].toInt() }.getOrDefault(0)
    }
}
