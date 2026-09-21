package com.sinelynx.grindingrobot.core.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import com.tencent.mmkv.BuildConfig

object DeviceUtils {
    /**
     * 获取设备序列号
     */
    fun getEquipmentSN(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                Build.SERIAL
            }
        } catch (e: Exception) {
            ""
        }
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getIMEICode(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephonyManager.getImei(0) // 卡槽1
        } else {
            telephonyManager.deviceId
        }
    }

    /**
     * 这是标准的写法，但与目前的项目定义不一致，暂不使用
     */
//    fun getVersionCode(context: Context): Int {
//        return try {
//            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
//        } catch (e: Exception) {
//            0
//        }
//    }



    fun getVersionName(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }

        return name ?: "本地文件"
    }

}